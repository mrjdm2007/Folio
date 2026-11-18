import SwiftUI
import Combine
import UniformTypeIdentifiers
import AppKit
import Security
import CryptoKit

@main
struct FolioApp: App {
    @StateObject private var store = NoteStore()
    var body: some Scene {
        WindowGroup { FolioRootView().environmentObject(store).frame(minWidth: 980, minHeight: 650) }
            .commands {
                CommandGroup(replacing: .newItem) {
                    Button("New Note") { store.createNote() }.keyboardShortcut("n", modifiers: .command)
                }
                CommandGroup(after: .textEditing) {
                    Button("Toggle Pin") { store.togglePinSelected() }.keyboardShortcut("p", modifiers: [.command, .shift])
                }
            }
        Settings { SettingsView().environmentObject(store) }
        MenuBarExtra("Folio", systemImage: "note.text") {
            QuickCaptureView().environmentObject(store)
        }
        .menuBarExtraStyle(.window)
    }
}

struct QuickCaptureView: View {
    @EnvironmentObject private var store: NoteStore
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var bodyText = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Quick Capture").font(.headline)
            TextField("Title (optional)", text: $title).textFieldStyle(.roundedBorder)
            TextEditor(text: $bodyText).font(.system(size: 13)).frame(minHeight: 90, maxHeight: 180)
            HStack {
                Text("Saved locally to Inbox").font(.caption).foregroundStyle(.secondary)
                Spacer()
                Button("Save") {
                    store.createQuickCapture(title: title, body: bodyText)
                    title = ""; bodyText = ""; dismiss()
                }.keyboardShortcut(.return, modifiers: .command)
            }
        }.padding(14).frame(width: 320)
    }
}

struct PortableFolioNote: Codable {
    var id: String
    var title: String
    var body: String
    var folder: String
    var tags: [String]
    var isPinned: Bool
    var isArchived: Bool
    var isDeleted: Bool
    var createdAtMs: Int64
    var updatedAtMs: Int64

    init(_ note: FolioNote) {
        id = note.id.uuidString.lowercased(); title = note.title; body = note.body
        folder = note.folder; tags = note.tags
        isPinned = note.isPinned; isArchived = note.isArchived; isDeleted = note.isDeleted
        createdAtMs = Int64(note.createdAt.timeIntervalSince1970 * 1000)
        updatedAtMs = Int64(note.updatedAt.timeIntervalSince1970 * 1000)
    }

    init(id: String, title: String, body: String, folder: String, tags: [String],
         isPinned: Bool, isArchived: Bool, isDeleted: Bool,
         createdAtMs: Int64, updatedAtMs: Int64) {
        self.id = id; self.title = title; self.body = body; self.folder = folder
        self.tags = tags; self.isPinned = isPinned; self.isArchived = isArchived
        self.isDeleted = isDeleted; self.createdAtMs = createdAtMs; self.updatedAtMs = updatedAtMs
    }
    var note: FolioNote {
        FolioNote(id: UUID(uuidString: id) ?? UUID(), title: title, body: body,
                  folder: folder, tags: tags, isPinned: isPinned, isArchived: isArchived,
                  isDeleted: isDeleted,
                  createdAt: Date(timeIntervalSince1970: Double(createdAtMs) / 1000),
                  updatedAt: Date(timeIntervalSince1970: Double(updatedAtMs) / 1000))
    }
}
struct FolioBackupEnvelope: Codable {
    var schemaVersion: Int = 1
    var exportedAtMs: Int64 = Int64(Date.now.timeIntervalSince1970 * 1000)
    var notes: [PortableFolioNote]
}
struct FolioBackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data
    init(data: Data = Data()) { self.data = data }
    init(configuration: ReadConfiguration) throws { data = configuration.file.regularFileContents ?? Data() }
    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
struct FolioAttachment: Identifiable, Codable, Equatable {
    var id: UUID
    var noteID: UUID
    var name: String
    var storedName: String
    var contentType: String
}

struct FolioNote: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var title: String
    var body: String
    var folder: String = "Notes"
    var tags: [String] = []
    var isPinned = false
    var isArchived = false
    var isDeleted = false
    var createdAt: Date = .now
    var updatedAt: Date = .now

    init(id: UUID = UUID(), title: String, body: String, folder: String = "Notes",
         tags: [String] = [], isPinned: Bool = false, isArchived: Bool = false,
         isDeleted: Bool = false, createdAt: Date = .now, updatedAt: Date = .now) {
        self.id = id; self.title = title; self.body = body; self.folder = folder
        self.tags = tags; self.isPinned = isPinned; self.isArchived = isArchived
        self.isDeleted = isDeleted; self.createdAt = createdAt; self.updatedAt = updatedAt
    }

    private enum CodingKeys: String, CodingKey {
        case id, title, body, folder, tags, isPinned, isArchived, isDeleted, createdAt, updatedAt
    }

    init(from decoder: Decoder) throws {
        let values = try decoder.container(keyedBy: CodingKeys.self)
        id = try values.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        title = try values.decodeIfPresent(String.self, forKey: .title) ?? "Untitled Note"
        body = try values.decodeIfPresent(String.self, forKey: .body) ?? ""
        folder = try values.decodeIfPresent(String.self, forKey: .folder) ?? "Notes"
        tags = try values.decodeIfPresent([String].self, forKey: .tags) ?? []
        isPinned = try values.decodeIfPresent(Bool.self, forKey: .isPinned) ?? false
        isArchived = try values.decodeIfPresent(Bool.self, forKey: .isArchived) ?? false
        isDeleted = try values.decodeIfPresent(Bool.self, forKey: .isDeleted) ?? false
        createdAt = try values.decodeIfPresent(Date.self, forKey: .createdAt) ?? .now
        updatedAt = try values.decodeIfPresent(Date.self, forKey: .updatedAt) ?? .now
    }
}

@MainActor
final class NoteStore: ObservableObject {
    @Published var notes: [FolioNote] = []
    @Published var selection: UUID?
    @Published var query = ""
    @Published var selectedFolder = "All Notes"
    @Published var selectedTag: String? = nil
    var allTags: [String] { Array(Set(notes.filter { !$0.isArchived && !$0.isDeleted }.flatMap(\.tags))).sorted() }
    @Published var sortMode = "Recently updated"
    private let fileURL: URL
    private let attachmentsURL: URL
    private let attachmentIndexURL: URL
    private let historyURL: URL
    @Published var revisionHistory: [String: [FolioNote]] = [:]
    @Published var attachments: [FolioAttachment] = []
    @Published var peerAddress = UserDefaults.standard.string(forKey: "FolioPeerAddress") ?? "" {
        didSet { UserDefaults.standard.set(peerAddress, forKey: "FolioPeerAddress") }
    }
    @Published var peerPairingKey = ""
    @Published var syncStatus = "Not paired"
    private var peerService: FolioPeerSyncService?
    private var recoveredCorruptStore = false

    init() {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let directory = base.appendingPathComponent("Folio", isDirectory: true)
        try? FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        fileURL = directory.appendingPathComponent("notes.json")
        attachmentsURL = directory.appendingPathComponent("Attachments", isDirectory: true)
        attachmentIndexURL = directory.appendingPathComponent("attachments.json")
        historyURL = directory.appendingPathComponent("history.json")
        if let data = try? Data(contentsOf: historyURL), let decoded = try? JSONDecoder().decode([String: [FolioNote]].self, from: data) { revisionHistory = decoded }
        try? FileManager.default.createDirectory(at: attachmentsURL, withIntermediateDirectories: true)
        loadAttachments()
        load()
        if notes.isEmpty && !recoveredCorruptStore { createNote(title: "Welcome to Folio") }
        peerPairingKey = FolioPeerSyncService.loadPairingSecret()
        peerService = FolioPeerSyncService(secret: { FolioPeerSyncService.loadPairingSecret() },
            exchange: { [weak self] remote in
                var result = remote
                DispatchQueue.main.sync { if let self { result = self.mergePeerNotes(remote) } }
                return result
            })
        if peerPairingKey.count >= 32 {
            do { try peerService?.start(); syncStatus = "Ready for paired device" }
            catch { syncStatus = error.localizedDescription }
            if !peerAddress.isEmpty { DispatchQueue.main.asyncAfter(deadline: .now() + 1) { self.syncWithPeer() } }
        }
    }

    func configurePeerSync(key: String) {
        guard key.count >= 32 else { syncStatus = "Pairing key must be at least 32 characters."; return }
        peerPairingKey = key
        do {
            try FolioPeerSyncService.savePairingSecret(key)
            try peerService?.start()
            syncStatus = "Pairing key saved in Keychain · listening on port 47831"
        } catch { syncStatus = error.localizedDescription }
    }

    func createPeerPairingKey() {
        var bytes = [UInt8](repeating: 0, count: 24)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let key = Data(bytes).base64EncodedString()
            .replacingOccurrences(of: "+", with: "-").replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
        configurePeerSync(key: key)
    }

    func syncWithPeer() {
        guard !peerAddress.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            syncStatus = "Enter the other device's IP address."; return
        }
        guard let peerService else { syncStatus = "Save a pairing key first."; return }
        syncStatus = "Connecting…"
        peerService.sync(host: peerAddress, notes: notes.map(PortableFolioNote.init)) { [weak self] result in
            DispatchQueue.main.async {
                guard let self else { return }
                switch result {
                case .success(let remote):
                    let merged = self.mergePeerNotes(remote)
                    self.syncStatus = "Synced · \(merged.count) notes"
                case .failure(let error): self.syncStatus = "Sync failed · \(error.localizedDescription)"
                }
            }
        }
    }

    private func mergePeerNotes(_ remote: [PortableFolioNote]) -> [PortableFolioNote] {
        var byID = Dictionary(uniqueKeysWithValues: notes.map { ($0.id, $0) })
        for item in remote {
            guard let id = UUID(uuidString: item.id) else { continue }
            let candidate = item.note
            guard let current = byID[id] else { byID[id] = candidate; continue }
            if !peerContentEqual(candidate, current) {
                let losing = candidate.updatedAt > current.updatedAt ? current : candidate
                var conflict = losing
                let tagsJSON = (try? JSONSerialization.data(withJSONObject: losing.tags))
                    .flatMap { String(data: $0, encoding: .utf8) } ?? "[]"
                let identity = "\(id.uuidString.lowercased())|\(Int64(losing.updatedAt.timeIntervalSince1970 * 1000))|\(losing.title)|\(losing.body)|\(losing.folder)|\(tagsJSON)|\(losing.isPinned)|\(losing.isArchived)|\(losing.isDeleted)"
                var bytes = Array(Insecure.MD5.hash(data: Data(identity.utf8)))
                bytes[6] = (bytes[6] & 0x0f) | 0x30
                bytes[8] = (bytes[8] & 0x3f) | 0x80
                conflict.id = UUID(uuid: (bytes[0], bytes[1], bytes[2], bytes[3], bytes[4], bytes[5],
                    bytes[6], bytes[7], bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]))
                conflict.title += " (Sync conflict)"
                conflict.folder = "Sync Conflicts"
                conflict.isPinned = false
                byID[conflict.id] = conflict
                if candidate.updatedAt > current.updatedAt { byID[id] = candidate }
            } else if candidate.updatedAt > current.updatedAt { byID[id] = candidate }
        }
        notes = Array(byID.values).sorted { $0.updatedAt > $1.updatedAt }
        save()
        return notes.map(PortableFolioNote.init)
    }
    var folders: [String] { Array(Set(notes.filter { !$0.isDeleted }.map(\.folder))).sorted() }
    var visibleNotes: [FolioNote] {
        notes.filter { note in
            let location: Bool
            switch selectedFolder {
            case "Pinned": location = note.isPinned && !note.isArchived && !note.isDeleted
            case "Tasks": location = !note.isArchived && !note.isDeleted && note.body.range(of: #"(?m)^\s*- \[ \]"#, options: .regularExpression) != nil
            case "Archive": location = note.isArchived && !note.isDeleted
            case "Recently Deleted": location = note.isDeleted
            case "All Notes": location = !note.isArchived && !note.isDeleted
            default: location = note.folder == selectedFolder && !note.isArchived && !note.isDeleted
            }
            let match = query.isEmpty || note.title.localizedCaseInsensitiveContains(query) ||
                note.body.localizedCaseInsensitiveContains(query) ||
                note.tags.contains(where: { $0.localizedCaseInsensitiveContains(query) })
            let tagMatch = selectedTag == nil || note.tags.contains(where: { $0.localizedCaseInsensitiveCompare(selectedTag!) == .orderedSame })
            return location && match && tagMatch
        }.sorted { left, right in
            if left.isPinned != right.isPinned { return left.isPinned }
            switch sortMode {
            case "Recently created": return left.createdAt > right.createdAt
            case "Oldest first": return left.updatedAt < right.updatedAt
            case "Title A–Z": return left.title.localizedStandardCompare(right.title) == .orderedAscending
            case "Title Z–A": return left.title.localizedStandardCompare(right.title) == .orderedDescending
            default: return left.updatedAt > right.updatedAt
            }
        }
    }
    var selectedNote: FolioNote? { notes.first { $0.id == selection } }
    func createNote(title: String = "Untitled Note") {
        let note = FolioNote(title: title, body: "")
        notes.insert(note, at: 0); selection = note.id; selectedFolder = "All Notes"; save()
    }
    func createQuickCapture(title: String, body: String) {
        let cleanTitle = title.trimmingCharacters(in: .whitespacesAndNewlines)
        let note = FolioNote(title: cleanTitle.isEmpty ? "Quick Capture" : cleanTitle,
                             body: body, folder: "Inbox")
        notes.insert(note, at: 0); selection = note.id; selectedFolder = "All Notes"; save()
    }
    func update(_ id: UUID, title: String, body: String, folder: String, tags: String) {
        guard let i = notes.firstIndex(where: { $0.id == id }) else { return }
        if notes[i].title != title || notes[i].body != body {
            let key = id.uuidString
            revisionHistory[key, default: []].insert(notes[i], at: 0)
            if revisionHistory[key, default: []].count > 20 { revisionHistory[key] = Array(revisionHistory[key, default: []].prefix(20)) }
            if let historyData = try? JSONEncoder().encode(revisionHistory) { try? historyData.write(to: historyURL, options: .atomic) }
        }
        notes[i].title = title; notes[i].body = body
        notes[i].folder = folder.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Notes" : folder
        notes[i].tags = tags.split(separator: ",").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        notes[i].updatedAt = .now; save()
    }
    func restoreRevision(_ revision: FolioNote, for id: UUID) {
        guard let index = notes.firstIndex(where: { $0.id == id }) else { return }
        notes[index].title = revision.title
        notes[index].body = revision.body
        notes[index].folder = revision.folder
        notes[index].tags = revision.tags
        notes[index].updatedAt = .now
        save()
    }
    func togglePin(_ note: FolioNote) {
        guard let i = notes.firstIndex(where: { $0.id == note.id }) else { return }
        notes[i].isPinned.toggle(); notes[i].updatedAt = .now; save()
    }
    func togglePinSelected() { if let note = selectedNote { togglePin(note) } }
    func duplicate(_ note: FolioNote) {
        var copy = note
        copy.id = UUID()
        copy.title += " (Copy)"
        copy.isPinned = false
        copy.isArchived = false
        copy.isDeleted = false
        copy.createdAt = .now
        copy.updatedAt = .now
        notes.insert(copy, at: 0); selection = copy.id; selectedFolder = "All Notes"; save()
    }
    func createDailyNote() {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        let date = formatter.string(from: .now)
        let title = "Daily · \(date)"
        if let existing = notes.first(where: {
            $0.title == title && $0.folder == "Daily Notes" && !$0.isDeleted && !$0.isArchived
        }) {
            selection = existing.id
            selectedFolder = "Daily Notes"
            return
        }
        let body = "# \(title)\n\n## Priorities\n- [ ] \n\n## Notes\n\n## Tasks\n- [ ] "
        let note = FolioNote(title: title, body: body, folder: "Daily Notes")
        notes.insert(note, at: 0); selection = note.id; selectedFolder = "Daily Notes"; save()
    }

    func createTemplate(_ kind: String) {
        let title: String
        let content: String
        switch kind {
        case "Class Notes":
            title = "Class Notes"
            content = "# Topic\n\n**Date:** \(Date.now.formatted(date: .abbreviated, time: .omitted))\n**Course:** \n\n## Key concepts\n- \n\n## Examples\n\n## Questions\n- [ ] "
        case "Meeting":
            title = "Meeting Notes"
            content = "# Meeting\n\n**Date:** \(Date.now.formatted(date: .abbreviated, time: .omitted))\n**Attendees:** \n\n## Agenda\n1. \n\n## Decisions\n- \n\n## Action items\n- [ ] Owner — Task — Due date"
        case "Daily Journal":
            title = "Daily Journal"
            content = "# Daily Journal\n\n**Date:** \(Date.now.formatted(date: .abbreviated, time: .omitted))\n\n## Today\n\n## Highlights\n- \n\n## To remember\n- "
        case "Research Notes":
            title = "Research Notes"
            content = "# Research question\n\n**Source / URL:**\n**Date accessed:** \(Date.now.formatted(date: .abbreviated, time: .omitted))\n\n## Summary\n\n## Evidence\n- \n\n## Open questions\n- [ ] \n\n## References\n- "
        case "Reading List":
            title = "Reading List"
            content = "# Reading List\n\n## To read\n- [ ] Title — Author — Link\n\n## Reading notes\n\n## Finished\n- [x] "
        case "Weekly Planner":
            title = "Weekly Planner"
            content = "# Weekly Plan · \(Date.now.formatted(date: .abbreviated, time: .omitted))\n\n## Priorities\n1. \n2. \n3. \n\n## Monday\n- [ ] \n\n## Tuesday\n- [ ] \n\n## Wednesday\n- [ ] \n\n## Thursday\n- [ ] \n\n## Friday\n- [ ] \n\n## Weekend\n- [ ] \n\n## Notes / review"
        case "Lecture Revision":
            title = "Lecture Revision"
            content = "# Lecture Revision\n\n**Subject:** \n**Topic:** \n**Exam date:** \n\n## Core concepts\n- \n\n## Formulas / definitions\n- \n\n## Worked examples\n\n## Questions to practise\n- [ ] \n\n## Confidence check\n- [ ] Revisit weak areas"
        default:
            title = "Project Brief"
            content = "# Project Brief\n\n## Objective\n\n## Scope\n\n## Milestones\n- [ ] \n\n## Risks\n\n## Next steps"
        }
        let note = FolioNote(title: title, body: content)
        notes.insert(note, at: 0); selection = note.id; selectedFolder = "All Notes"; save()
    }
    func archive(_ note: FolioNote) {
        guard let i = notes.firstIndex(where: { $0.id == note.id }) else { return }
        notes[i].isArchived = true; notes[i].updatedAt = .now
        if selection == note.id { selection = nil }; save()
    }
    func moveToTrash(_ note: FolioNote) {
        guard let i = notes.firstIndex(where: { $0.id == note.id }) else { return }
        notes[i].isDeleted = true; notes[i].isArchived = false
        notes[i].updatedAt = .now
        if selection == note.id { selection = nil }; save()
    }
    func restore(_ note: FolioNote) {
        guard let i = notes.firstIndex(where: { $0.id == note.id }) else { return }
        notes[i].isDeleted = false; notes[i].isArchived = false; notes[i].updatedAt = .now; save()
    }
    func permanentlyDelete(_ note: FolioNote) {
        notes.removeAll { $0.id == note.id }
        let removed = attachments.filter { $0.noteID == note.id }
        attachments.removeAll { $0.noteID == note.id }
        for item in removed {
            try? FileManager.default.removeItem(at: attachmentsURL.appendingPathComponent(item.storedName))
        }
        saveAttachments()
        revisionHistory.removeValue(forKey: note.id.uuidString)
        if let data = try? JSONEncoder().encode(revisionHistory) {
            try? data.write(to: historyURL, options: .atomic)
        }
        if selection == note.id { selection = nil }
        save()
    }
    func addAttachment(_ source: URL, to noteID: UUID) throws {
        let access = source.startAccessingSecurityScopedResource()
        defer { if access { source.stopAccessingSecurityScopedResource() } }
        let id = UUID()
        let ext = source.pathExtension
        let storedName = id.uuidString + (ext.isEmpty ? "" : "." + ext)
        let destination = attachmentsURL.appendingPathComponent(storedName)
        let attributes = try FileManager.default.attributesOfItem(atPath: source.path)
        let fileSize = (attributes[.size] as? NSNumber)?.int64Value ?? 0
        guard fileSize <= 100 * 1024 * 1024 else {
            throw NSError(domain: "FolioAttachments", code: 1,
                userInfo: [NSLocalizedDescriptionKey: "Attachments must be 100 MB or smaller."])
        }
        try FileManager.default.copyItem(at: source, to: destination)
        let item = FolioAttachment(id: id, noteID: noteID, name: source.lastPathComponent,
                                   storedName: storedName, contentType: UTType(filenameExtension: ext)?.preferredMIMEType ?? "application/octet-stream")
        attachments.append(item)
        saveAttachments()
    }
    func removeAttachment(_ item: FolioAttachment) {
        attachments.removeAll { $0.id == item.id }
        try? FileManager.default.removeItem(at: attachmentsURL.appendingPathComponent(item.storedName))
        saveAttachments()
    }
    func attachmentURL(_ item: FolioAttachment) -> URL {
        attachmentsURL.appendingPathComponent(item.storedName)
    }
    private func loadAttachments() {
        guard let data = try? Data(contentsOf: attachmentIndexURL),
              let values = try? JSONDecoder().decode([FolioAttachment].self, from: data) else { return }
        attachments = values.filter { FileManager.default.fileExists(atPath: attachmentsURL.appendingPathComponent($0.storedName).path) }
    }
    private func saveAttachments() {
        guard let data = try? JSONEncoder().encode(attachments) else { return }
        try? data.write(to: attachmentIndexURL, options: .atomic)
    }
    private func peerContentEqual(_ lhs: FolioNote, _ rhs: FolioNote) -> Bool {
        lhs.title == rhs.title && lhs.body == rhs.body && lhs.folder == rhs.folder &&
        lhs.tags == rhs.tags && lhs.isPinned == rhs.isPinned &&
        lhs.isArchived == rhs.isArchived && lhs.isDeleted == rhs.isDeleted
    }

    func makeBackup() -> FolioBackupDocument {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        return FolioBackupDocument(data: (try? encoder.encode(FolioBackupEnvelope(notes: notes.map(PortableFolioNote.init)))) ?? Data())
    }
    func restoreBackup(_ data: Data) throws {
        let decoder = JSONDecoder()
        let imported: [FolioNote]
        if let envelope = try? decoder.decode(FolioBackupEnvelope.self, from: data) {
            guard envelope.schemaVersion == 1 else { throw BackupError.unsupportedVersion }
            // Reject malformed identifiers rather than silently assigning new IDs and duplicating notes.
            guard envelope.notes.allSatisfy({ UUID(uuidString: $0.id) != nil }) else {
                throw BackupError.invalidFile
            }
            imported = envelope.notes.map(\.note)
        } else if let legacy = try? decoder.decode([FolioNote].self, from: data) {
            imported = legacy
        } else { throw BackupError.invalidFile }
        // Avoid trapping on malformed backups containing duplicate IDs; keep the newest local copy.
        var byID: [UUID: FolioNote] = [:]
        for note in notes {
            if let current = byID[note.id], current.updatedAt > note.updatedAt { continue }
            byID[note.id] = note
        }
        for note in imported {
            if let existing = byID[note.id], existing.updatedAt > note.updatedAt { continue }
            byID[note.id] = note
        }
        notes = Array(byID.values).sorted { $0.updatedAt > $1.updatedAt }
        selection = notes.first(where: { !$0.isDeleted && !$0.isArchived })?.id
        save()
    }
    private func load() {
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return }
        guard let data = try? Data(contentsOf: fileURL) else {
            // Do not create a fresh welcome note over a file that exists but cannot be read.
            recoveredCorruptStore = true
            return
        }
        do {
            let decoded = try JSONDecoder().decode([FolioNote].self, from: data)
            // Stable SwiftUI row identity requires unique IDs; retain the newest duplicate.
            var unique: [UUID: FolioNote] = [:]
            for note in decoded {
                if let current = unique[note.id], current.updatedAt > note.updatedAt { continue }
                unique[note.id] = note
            }
            notes = Array(unique.values).sorted { $0.updatedAt > $1.updatedAt }
            selection = notes.first(where: { !$0.isDeleted && !$0.isArchived })?.id
        } catch {
            // Never silently replace an unreadable library with a new empty one.
            let stamp = ISO8601DateFormatter().string(from: .now).replacingOccurrences(of: ":", with: "-")
            let recoveryURL = fileURL.deletingLastPathComponent()
                .appendingPathComponent("notes-corrupt-\(stamp).json")
            do {
                try FileManager.default.moveItem(at: fileURL, to: recoveryURL)
                recoveredCorruptStore = true
            } catch {
                // Keep the original file in place if recovery cannot be moved.
                recoveredCorruptStore = true
            }
        }
    }
    private func save() {
        guard let data = try? JSONEncoder().encode(notes) else { return }
        do {
            try data.write(to: fileURL, options: .atomic)
        } catch {
            // Keep the in-memory notes; a later edit or relaunch must not imply a successful save.
            assertionFailure("Folio could not save its local note store: \(error.localizedDescription)")
        }
    }
}

struct FolioRootView: View {
    @EnvironmentObject private var store: NoteStore
    private var sidebarItems: [String] { ["All Notes", "Pinned", "Tasks"] + store.folders + ["Archive", "Recently Deleted"] }
    var body: some View {
        NavigationSplitView {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("FOLIO").font(.system(size: 21, weight: .bold, design: .rounded))
                    Spacer()
                    Button { store.createDailyNote() } label: { Image(systemName: "calendar") }
                        .help("Open or create today's Daily Note")
                    Menu {
                        Button("Daily Note · Today") { store.createDailyNote() }
                        Button("Blank Note") { store.createNote() }
                        Divider()
                        ForEach(["Class Notes", "Meeting", "Daily Journal", "Project Brief", "Research Notes", "Reading List", "Weekly Planner", "Lecture Revision"], id: \.self) { kind in
                            Button(kind) { store.createTemplate(kind) }
                        }
                    } label: { Image(systemName: "square.and.pencil") }.help("New note or template")
                }.padding(.bottom, 10)
                ForEach(sidebarItems, id: \.self) { item in
                    Button { store.selectedFolder = item } label: {
                        Label(item, systemImage: icon(for: item)).frame(maxWidth: .infinity, alignment: .leading)
                            .padding(8).background(store.selectedFolder == item ? Color.accentColor.opacity(0.14) : .clear, in: RoundedRectangle(cornerRadius: 8))
                    }.buttonStyle(.plain)
                }
                Spacer()
                Label("Stored on this Mac", systemImage: "internaldrive").font(.caption).foregroundStyle(.secondary)
            }.padding(14).navigationSplitViewColumnWidth(min: 175, ideal: 205)
        } content: {
            VStack(spacing: 10) {
                TextField("Search titles, text and tags", text: $store.query).textFieldStyle(.roundedBorder)
                HStack {
                    Text("\(store.visibleNotes.count) notes").font(.caption).foregroundStyle(.secondary)
                    Spacer()
                    Menu {
                        Button("All tags") { store.selectedTag = nil }
                        Divider()
                        ForEach(store.allTags, id: \.self) { tag in
                            Button {
                                store.selectedTag = store.selectedTag == tag ? nil : tag
                            } label: {
                                if store.selectedTag == tag { Label("#" + tag, systemImage: "checkmark") }
                                else { Text("#" + tag) }
                            }
                        }
                    } label: {
                        Label(store.selectedTag.map { "#" + $0 } ?? "Tags", systemImage: "tag")
                            .font(.caption)
                    }.help("Filter notes by tag")
                    Menu {
                        ForEach(["Recently updated", "Recently created", "Oldest first", "Title A–Z", "Title Z–A"], id: \.self) { mode in
                            Button { store.sortMode = mode } label: {
                                if store.sortMode == mode { Label(mode, systemImage: "checkmark") } else { Text(mode) }
                            }
                        }
                    } label: { Label("Sort", systemImage: "arrow.up.arrow.down").font(.caption) }
                }
                List(selection: $store.selection) {
                    ForEach(store.visibleNotes) { note in
                        VStack(alignment: .leading, spacing: 5) {
                            HStack {
                                Text(note.title.isEmpty ? "Untitled Note" : note.title).font(.headline).lineLimit(1)
                                if note.isPinned { Image(systemName: "pin.fill").foregroundStyle(.orange) }
                            }
                            Text(note.body.isEmpty ? "No additional text" : note.body).font(.subheadline).foregroundStyle(.secondary).lineLimit(2)
                            HStack {
                                Text(note.folder)
                                if !note.tags.isEmpty { Text("· " + note.tags.joined(separator: ", ")) }
                                Spacer(); Text(note.updatedAt, style: .date)
                            }.font(.caption2).foregroundStyle(.tertiary)
                        }.tag(note.id).contextMenu {
                            Button(note.isPinned ? "Unpin" : "Pin") { store.togglePin(note) }
                            Button("Duplicate") { store.duplicate(note) }
                            if note.isDeleted {
                                Button("Restore") { store.restore(note) }
                                Button("Delete Permanently", role: .destructive) { store.permanentlyDelete(note) }
                            } else {
                                Button("Archive") { store.archive(note) }
                                Button("Move to Recently Deleted", role: .destructive) { store.moveToTrash(note) }
                            }
                        }
                    }
                }.listStyle(.plain)
            }.padding(12).navigationSplitViewColumnWidth(min: 250, ideal: 300)
        } detail: {
            if let note = store.selectedNote {
                NoteEditor(note: note)
            } else {
                ContentUnavailableView("Select a note", systemImage: "note.text", description: Text("Create a note or choose one from the list."))
            }
        }.preferredColorScheme(.dark)
            .tint(Color(red: 0.725, green: 0.953, blue: 0.416))
    }
    private func icon(for item: String) -> String {
        switch item { case "All Notes": return "note.text"; case "Tasks": return "checklist"; case "Archive": return "archivebox"; case "Recently Deleted": return "trash"; default: return "folder" }
    }
}

struct NoteEditor: View {
    @EnvironmentObject private var store: NoteStore
    let note: FolioNote
    @State private var title = ""
    @State private var bodyText = ""
    @State private var folder = "Notes"
    @State private var tags = ""
    @State private var showRenderedPreview = false
    @State private var showingAttachmentPicker = false
    @State private var attachmentMessage: String?
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                TextField("Title", text: $title).font(.system(size: 28, weight: .bold)).textFieldStyle(.plain)
                Button { store.togglePin(note) } label: { Image(systemName: note.isPinned ? "pin.fill" : "pin") }.help("Pin note")
                ShareLink(item: "# \(title)\n\n\(bodyText)") { Image(systemName: "square.and.arrow.up") }.help("Share or export as Markdown")
                Menu {
                    Button("Archive") { store.archive(note) }
                    Button("Move to Recently Deleted", role: .destructive) { store.moveToTrash(note) }
                    if let versions = store.revisionHistory[note.id.uuidString], !versions.isEmpty {
                        Divider()
                        Menu("Version History") {
                            ForEach(versions) { version in
                                Button(version.updatedAt.formatted(date: .abbreviated, time: .shortened)) {
                                    store.restoreRevision(version, for: note.id)
                                    title = version.title; bodyText = version.body
                                    folder = version.folder; tags = version.tags.joined(separator: ", ")
                                }
                            }
                        }
                    }
                } label: { Image(systemName: "ellipsis.circle") }
            }
            HStack {
                TextField("Folder", text: $folder).textFieldStyle(.roundedBorder).frame(maxWidth: 180)
                TextField("Tags, separated by commas", text: $tags).textFieldStyle(.roundedBorder)
                Menu {
                    ForEach(store.notes.filter { $0.id != note.id && !$0.isDeleted && !$0.isArchived }.prefix(40)) { target in
                        Button(target.title.isEmpty ? "Untitled Note" : target.title) {
                            let label = target.title.isEmpty ? "Untitled Note" : target.title
                            bodyText += (bodyText.isEmpty || bodyText.hasSuffix("\n") ? "" : "\n") + "[[\(label)]]"
                            persist()
                        }
                    }
                } label: { Image(systemName: "link.badge.plus") }
                    .help("Link another note")
            }
            let linkPattern = try? NSRegularExpression(pattern: #"[[([^]]+)]]"#)
            let linkMatches = linkPattern?.matches(in: bodyText, range: NSRange(bodyText.startIndex..., in: bodyText)) ?? []
            let linkTitles = linkMatches.compactMap { match -> String? in
                guard let range = Range(match.range(at: 1), in: bodyText) else { return nil }
                return String(bodyText[range]).trimmingCharacters(in: .whitespacesAndNewlines)
            }
            let linkedNotes = store.notes.filter { candidate in
                linkTitles.contains(where: { $0.localizedCaseInsensitiveCompare(candidate.title) == .orderedSame })
            }
            let backlinks = store.notes.filter { candidate in
                candidate.id != note.id && candidate.body.localizedCaseInsensitiveContains("[[\(title)]]")
            }
            if !linkedNotes.isEmpty || !backlinks.isEmpty {
                VStack(alignment: .leading, spacing: 5) {
                    if !linkedNotes.isEmpty {
                        Text("LINKED NOTES").font(.caption2.weight(.semibold)).foregroundStyle(.green)
                        HStack { ForEach(linkedNotes) { item in
                            Button(item.title.isEmpty ? "Untitled Note" : item.title) { store.selection = item.id }
                                .buttonStyle(.link)
                        } }
                    }
                    if !backlinks.isEmpty {
                        Text("BACKLINKS").font(.caption2.weight(.semibold)).foregroundStyle(.orange)
                        HStack { ForEach(backlinks) { item in
                            Button(item.title.isEmpty ? "Untitled Note" : item.title) { store.selection = item.id }
                                .buttonStyle(.link)
                        } }
                    }
                }.font(.caption)
            }
            HStack(spacing: 8) {
                Button("H1") { bodyText += "\n# Heading\n" }
                Button("H2") { bodyText += "\n## Heading\n" }
                Button("Bullet") { bodyText += "\n- List item\n" }
                Button("Task") { bodyText += "\n- [ ] Task\n" }
                Button("Quote") { bodyText += "\n> Quote\n" }
                Button("Bold") { bodyText += " **bold** " }
                Button("Italic") { bodyText += " _italic_ " }
                Button("Code") { bodyText += " `code` " }
                Button("Link") { bodyText += " [text](https://) " }
                Spacer()
                Button("Export .md") { exportMarkdown() }
                Button(showRenderedPreview ? "Edit" : "Preview") { showRenderedPreview.toggle() }
                Text("Markdown").font(.caption).foregroundStyle(.secondary)
            }.buttonStyle(.borderless)
            HStack {
                Label("Documents · \(store.attachments.filter { $0.noteID == note.id }.count)", systemImage: "paperclip")
                    .font(.caption).foregroundStyle(.secondary)
                Spacer()
                Button { showingAttachmentPicker = true } label: { Label("Attach file", systemImage: "plus") }
            }
            ForEach(store.attachments.filter { $0.noteID == note.id }) { item in
                HStack {
                    Button(item.name) { NSWorkspace.shared.open(store.attachmentURL(item)) }
                        .buttonStyle(.link).lineLimit(1)
                    Spacer()
                    Button(role: .destructive) { store.removeAttachment(item) } label: {
                        Image(systemName: "trash")
                    }.buttonStyle(.borderless).help("Remove attachment")
                }
            }
            if let attachmentMessage {
                Text(attachmentMessage).font(.caption).foregroundStyle(.secondary)
            }
            Divider()
            if showRenderedPreview {
                ScrollView {
                    Group {
                        if let rendered = try? AttributedString(markdown: bodyText, options: .init(interpretedSyntax: .full)) {
                            Text(rendered).frame(maxWidth: .infinity, alignment: .leading)
                        } else {
                            Text(bodyText).frame(maxWidth: .infinity, alignment: .leading)
                        }
                    }.padding(.vertical, 8)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .background(Color(nsColor: .textBackgroundColor).opacity(0.35), in: RoundedRectangle(cornerRadius: 10))
            } else {
                TextEditor(text: $bodyText).font(.system(size: 15)).scrollContentBackground(.hidden)
            }
            let tasks = bodyText.components(separatedBy: .newlines).filter {
                $0.range(of: #"^\s*- \[([ xX])\]"#, options: .regularExpression) != nil
            }
            let completedTasks = tasks.filter {
                $0.range(of: #"^\s*- \[xX\]"#, options: .regularExpression) != nil
            }.count
            HStack {
                Text("Autosaved locally"); Spacer()
                if !tasks.isEmpty { Text("\(completedTasks)/\(tasks.count) tasks · ") }
                let wordCount = bodyText.split(whereSeparator: \.isWhitespace).count
                Text("\(wordCount) words · \(wordCount == 0 ? 0 : (wordCount + 199) / 200) min read")
            }.font(.caption).foregroundStyle(.secondary)
            if !tasks.isEmpty {
                ProgressView(value: Double(completedTasks), total: Double(tasks.count))
                    .tint(Color.accentColor)
            }
        }.padding(24)
            .fileImporter(isPresented: $showingAttachmentPicker, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
                do { for url in try result.get() { try store.addAttachment(url, to: note.id) }; attachmentMessage = "Documents attached." }
                catch { attachmentMessage = error.localizedDescription }
            }
            .onAppear { loadDraft() }
            .onChange(of: note.id) { _ in loadDraft() }
            .onChange(of: title) { _ in persist() }
            .onChange(of: bodyText) { _ in persist() }
            .onChange(of: folder) { _ in persist() }
            .onChange(of: tags) { _ in persist() }
    }
    private func exportMarkdown() {
        let panel = NSSavePanel()
        panel.allowedContentTypes = [.plainText]
        panel.nameFieldStringValue = (title.isEmpty ? "Folio-Note" : title)
            .replacingOccurrences(of: "/", with: "-") + ".md"
        panel.canCreateDirectories = true
        if panel.runModal() == .OK, let url = panel.url {
            let markdown = "# \\(title)\\n\\n" + bodyText
            do { try Data(markdown.utf8).write(to: url, options: .atomic) }
            catch { attachmentMessage = "Markdown export failed: \\(error.localizedDescription)" }
        }
    }
    private func loadDraft() { title = note.title; bodyText = note.body; folder = note.folder; tags = note.tags.joined(separator: ", ") }
    private func persist() { store.update(note.id, title: title, body: bodyText, folder: folder, tags: tags) }
}

enum BackupError: LocalizedError {
    case invalidFile, unsupportedVersion
    var errorDescription: String? {
        switch self {
        case .invalidFile: return "This file is not a valid Folio backup."
        case .unsupportedVersion: return "This backup was created by a newer Folio version."
        }
    }
}
struct SettingsView: View {
    @EnvironmentObject private var store: NoteStore
    @State private var backupDocument = FolioBackupDocument()
    @State private var showingExporter = false
    @State private var showingImporter = false
    @State private var backupMessage: String?
    var body: some View {
        Form {
            Section("Folio") {
                Text("Folio for macOS")
                Text("Local notes are stored in Application Support/Folio.").foregroundStyle(.secondary)
                Text("\(store.notes.count) notes stored locally")
            }
            Section("Peer-to-peer sync") {
                TextField("Shared pairing key (32+ characters)", text: $store.peerPairingKey)
                HStack {
                    Button("Create pairing key") { store.createPeerPairingKey() }
                    Button("Copy key") {
                        NSPasteboard.general.clearContents()
                        NSPasteboard.general.setString(store.peerPairingKey, forType: .string)
                    }.disabled(store.peerPairingKey.isEmpty)
                    Button("Save pairing key") { store.configurePeerSync(key: store.peerPairingKey) }
                }
                TextField("Other device IP address", text: $store.peerAddress)
                Button("Sync Now") { store.syncWithPeer() }
                    .disabled(store.peerAddress.isEmpty || store.peerPairingKey.count < 32)
                Text(store.syncStatus).font(.caption).foregroundStyle(.secondary)
                Text("Use the same key on both devices. Connect over the same trusted Wi-Fi or a private VPN. The note payload is encrypted with AES-256-GCM; never share the key publicly.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Section("Backup and restore") {
                HStack {
                    Button("Export Folio Backup") { backupDocument = store.makeBackup(); showingExporter = true }
                    Button("Restore from Backup") { showingImporter = true }
                }
                Text("Backups include all notes and merge by stable note ID, keeping the newer copy.")
                    .font(.caption).foregroundStyle(.secondary)
                if let backupMessage { Text(backupMessage).font(.caption) }
            }
            Section("Available in this build") {
                Text("Folders, tags, full-text search, pinning, archive, recently deleted and autosave.")
            }
            Section("Sync notes") {
                Text("Peer sync uses an encrypted direct connection on port 47831. Both devices must be running Folio, use the same pairing key, and be reachable by IP. The first release syncs note text and metadata; attachments and automatic background discovery are not included yet.")
                    .font(.caption).foregroundStyle(.secondary)
            }
        }.padding(20).frame(width: 460)
            .fileExporter(isPresented: $showingExporter, document: backupDocument,
                          contentType: .json, defaultFilename: "Folio-Backup.json") { result in
                if case .success = result { backupMessage = "Backup exported." }
                else { backupMessage = "Backup export failed." }
            }
            .fileImporter(isPresented: $showingImporter, allowedContentTypes: [.json]) { result in
                do {
                    let url = try result.get()
                    let access = url.startAccessingSecurityScopedResource()
                    defer { if access { url.stopAccessingSecurityScopedResource() } }
                    try store.restoreBackup(Data(contentsOf: url))
                    backupMessage = "Backup restored and merged."
                } catch { backupMessage = error.localizedDescription }
            }
    }
}
