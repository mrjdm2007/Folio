import Foundation
import Network
import CryptoKit
import CommonCrypto
import Security

/// Encrypted peer transport shared with Android's FolioPeerSync wire format.
/// Pairing uses a high-entropy secret copied between devices out of band.
final class FolioPeerSyncService {
    static let port: NWEndpoint.Port = 47831
    private let queue = DispatchQueue(label: "app.folio.peer-sync")
    private var listener: NWListener?
    private let secret: () -> String
    private let exchange: ([PortableFolioNote]) throws -> [PortableFolioNote]

    static func loadPairingSecret() -> String {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "app.folio.notes.peer-sync",
            kSecAttrAccount as String: "pairing-secret", kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }

    static func savePairingSecret(_ secret: String) throws {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: "app.folio.notes.peer-sync",
            kSecAttrAccount as String: "pairing-secret"]
        SecItemDelete(query as CFDictionary)
        var item = query
        item[kSecValueData as String] = Data(secret.utf8)
        let status = SecItemAdd(item as CFDictionary, nil)
        guard status == errSecSuccess else { throw NSError(domain: NSOSStatusErrorDomain, code: Int(status)) }
    }

    init(secret: @escaping () -> String,
         exchange: @escaping ([PortableFolioNote]) throws -> [PortableFolioNote]) {
        self.secret = secret
        self.exchange = exchange
    }

    func start() throws {
        guard secret().count >= 32 else { throw SyncError.notPaired }
        guard listener == nil else { return }
        let listener = try NWListener(using: .tcp, on: Self.port)
        listener.newConnectionHandler = { [weak self] connection in
            guard let self else { connection.cancel(); return }
            connection.start(queue: self.queue)
            self.receiveRequest(on: connection)
        }
        listener.stateUpdateHandler = { state in
            if case .failed(let error) = state { NSLog("Folio peer listener failed: %@", error.localizedDescription) }
        }
        self.listener = listener
        listener.start(queue: queue)
    }

    func sync(host: String, notes: [PortableFolioNote], completion: @escaping (Result<[PortableFolioNote], Error>) -> Void) {
        do {
            guard secret().count >= 32 else { throw SyncError.notPaired }
            let connection = NWConnection(host: NWEndpoint.Host(host), port: Self.port, using: .tcp)
            let completionLock = NSLock()
            var completed = false
            var timeout: DispatchWorkItem?
            let finish: (Result<[PortableFolioNote], Error>) -> Void = { result in
                completionLock.lock()
                let shouldFinish = !completed
                completed = true
                completionLock.unlock()
                guard shouldFinish else { return }
                timeout?.cancel()
                connection.cancel()
                completion(result)
            }
            timeout = DispatchWorkItem { finish(.failure(SyncError.timedOut)) }
            if let timeout { queue.asyncAfter(deadline: .now() + 12, execute: timeout) }
            connection.stateUpdateHandler = { [weak self] state in
                guard let self else { finish(.failure(SyncError.disconnected)); return }
                switch state {
                case .ready:
                    self.sendExchange(notes, on: connection) { result in finish(result) }
                case .failed(let error):
                    finish(.failure(error))
                case .cancelled:
                    break
                default:
                    break
                }
            }
            connection.start(queue: queue)
        } catch { completion(.failure(error)) }
    }

    private func receiveRequest(on connection: NWConnection) {
        receiveFrame(on: connection) { [weak self] result in
            guard let self else { connection.cancel(); return }
            do {
                let encrypted = try result.get()
                let plaintext = try self.decrypt(encrypted)
                let object = try JSONSerialization.jsonObject(with: plaintext) as? [String: Any]
                guard (object?["protocol"] as? Int) == 1,
                      let raw = object?["notes"] as? [[String: Any]] else { throw SyncError.invalidMessage }
                let remote = try raw.map { try Self.portable(fromWire: $0) }
                let merged = try self.exchange(remote)
                let response = try JSONSerialization.data(withJSONObject: [
                    "protocol": 1, "notes": merged.map(Self.wireDictionary)
                ])
                self.sendFrame(try self.encrypt(response), on: connection) { _ in connection.cancel() }
            } catch { connection.cancel(); NSLog("Folio peer request rejected: %@", error.localizedDescription) }
        }
    }

    private func sendExchange(_ notes: [PortableFolioNote], on connection: NWConnection,
                              completion: @escaping (Result<[PortableFolioNote], Error>) -> Void) {
        do {
            let payload = try JSONSerialization.data(withJSONObject: [
                "protocol": 1, "notes": notes.map(Self.wireDictionary)
            ])
            let frame = try encrypt(payload)
            sendFrame(frame, on: connection) { [weak self] result in
                guard let self else { return }
                if case .failure(let error) = result { completion(.failure(error)); return }
                self.receiveFrame(on: connection) { response in
                    do {
                        let clear = try self.decrypt(response.get())
                        let object = try JSONSerialization.jsonObject(with: clear) as? [String: Any]
                        guard (object?["protocol"] as? Int) == 1,
                              let raw = object?["notes"] as? [[String: Any]] else { throw SyncError.invalidMessage }
                        completion(.success(try raw.map { try Self.portable(fromWire: $0) }))
                    } catch { completion(.failure(error)) }
                }
            }
        } catch { completion(.failure(error)) }
    }

    private func sendFrame(_ data: Data, on connection: NWConnection, completion: @escaping (Result<Void, Error>) -> Void) {
        guard data.count <= 8 * 1024 * 1024 else { completion(.failure(SyncError.tooLarge)); return }
        var length = UInt32(data.count).bigEndian
        let header = Data(bytes: &length, count: 4)
        connection.send(content: header + data, completion: .contentProcessed { error in
            if let error { completion(.failure(error)) } else { completion(.success(())) }
        })
    }

    private func receiveFrame(on connection: NWConnection, completion: @escaping (Result<Data, Error>) -> Void) {
        receiveExact(4, on: connection) { [weak self] headerResult in
            guard let self else { return }
            do {
                let header = try headerResult.get()
                let size = header.reduce(UInt32(0)) { ($0 << 8) | UInt32($1) }
                guard size > 0, size <= 8 * 1024 * 1024 else { throw SyncError.tooLarge }
                self.receiveExact(Int(size), on: connection, completion: completion)
            } catch { completion(.failure(error)) }
        }
    }

    private func receiveExact(_ count: Int, on connection: NWConnection, completion: @escaping (Result<Data, Error>) -> Void) {
        func collect(_ accumulated: Data) {
            if accumulated.count == count { completion(.success(accumulated)); return }
            connection.receive(minimumIncompleteLength: 1, maximumLength: count - accumulated.count) { data, _, complete, error in
                if let error { completion(.failure(error)); return }
                let next = accumulated + (data ?? Data())
                if complete && next.count < count { completion(.failure(SyncError.disconnected)) }
                else { collect(next) }
            }
        }
        collect(Data())
    }


    private static func wireDictionary(_ note: PortableFolioNote) -> [String: Any] {
        ["id": note.id, "title": note.title, "body": note.body, "folder": note.folder,
         "tags": note.tags, "pinned": note.isPinned, "archived": note.isArchived,
         "deleted": note.isDeleted, "createdAt": note.createdAtMs, "updatedAt": note.updatedAtMs]
    }

    private static func portable(fromWire value: [String: Any]) throws -> PortableFolioNote {
        guard let id = value["id"] as? String, UUID(uuidString: id) != nil else { throw SyncError.invalidMessage }
        return PortableFolioNote(
            id: id, title: value["title"] as? String ?? "Untitled Note",
            body: value["body"] as? String ?? "", folder: value["folder"] as? String ?? "Notes",
            tags: value["tags"] as? [String] ?? [], isPinned: value["pinned"] as? Bool ?? false,
            isArchived: value["archived"] as? Bool ?? false, isDeleted: value["deleted"] as? Bool ?? false,
            createdAtMs: (value["createdAt"] as? NSNumber)?.int64Value ?? 0,
            updatedAtMs: (value["updatedAt"] as? NSNumber)?.int64Value ?? 0)
    }

    private func encrypt(_ clear: Data) throws -> Data {
        let salt = Self.random(16), nonce = AES.GCM.Nonce()
        let key = try Self.derive(secret(), salt: salt)
        let box = try AES.GCM.seal(clear, using: key, nonce: nonce)
        let envelope: [String: String] = [
            "salt": salt.base64EncodedString(),
            "iv": Data(nonce).base64EncodedString(),
            "data": (box.ciphertext + box.tag).base64EncodedString()
        ]
        return try JSONSerialization.data(withJSONObject: envelope)
    }

    private func decrypt(_ data: Data) throws -> Data {
        guard let envelope = try JSONSerialization.jsonObject(with: data) as? [String: String],
              let saltText = envelope["salt"], let ivText = envelope["iv"], let dataText = envelope["data"],
              let salt = Data(base64Encoded: saltText), let iv = Data(base64Encoded: ivText),
              let sealed = Data(base64Encoded: dataText), sealed.count >= 16 else { throw SyncError.invalidMessage }
        let key = try Self.derive(secret(), salt: salt)
        let nonce = try AES.GCM.Nonce(data: iv)
        let box = try AES.GCM.SealedBox(nonce: nonce, ciphertext: sealed.dropLast(16), tag: sealed.suffix(16))
        return try AES.GCM.open(box, using: key)
    }

    private static func derive(_ secret: String, salt: Data) throws -> SymmetricKey {
        var output = [UInt8](repeating: 0, count: 32)
        let status = secret.withCString { password in
            salt.withUnsafeBytes { saltBytes in
                CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), password, secret.utf8.count,
                    saltBytes.bindMemory(to: UInt8.self).baseAddress, salt.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 210000, &output, output.count)
            }
        }
        guard status == kCCSuccess else { throw SyncError.keyDerivation }
        return SymmetricKey(data: output)
    }
    private static func random(_ count: Int) -> Data {
        var bytes = [UInt8](repeating: 0, count: count); _ = SecRandomCopyBytes(kSecRandomDefault, count, &bytes)
        return Data(bytes)
    }
    enum SyncError: LocalizedError {
        case notPaired, invalidMessage, tooLarge, disconnected, keyDerivation, timedOut
        var errorDescription: String? {
            switch self {
            case .notPaired: return "Create or enter the same pairing key on both devices."
            case .invalidMessage: return "The peer sent an invalid sync message."
            case .tooLarge: return "The peer sync message exceeds the 8 MB limit."
            case .disconnected: return "The peer disconnected during sync."
            case .keyDerivation: return "Could not derive the sync encryption key."
            case .timedOut: return "The peer did not respond within 12 seconds."
            }
        }
    }
}
