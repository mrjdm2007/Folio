package app.folio.notes

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/**
 * Local peer transport. Payloads are AES-256-GCM encrypted with a key derived
 * from a high-entropy pairing secret. The secret must be shared out-of-band.
 * No listener is opened until a secret has been configured.
 */
object FolioPeerSync {
    private const val PORT = 47831
    private const val PREFS = "folio_local"
    private const val SECRET_KEY = "p2p_pairing_secret"
    private const val MAX_FRAME = 8 * 1024 * 1024
    @Volatile private var listener: ServerSocket? = null
    @Volatile var onRemoteNotes: ((String) -> Unit)? = null

    private fun secretPrefs(context: Context): SharedPreferences {
        val master = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        return EncryptedSharedPreferences.create(context, "folio_peer_keys", master,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    }

    fun pairingSecret(context: Context): String =
        secretPrefs(context).getString(SECRET_KEY, "") ?: ""

    fun createPairingSecret(context: Context): String {
        val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
        val secret = Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE)
        secretPrefs(context).edit().putString(SECRET_KEY, secret).apply()
        startListener(context)
        return secret
    }

    fun setPairingSecret(context: Context, secret: String) {
        require(secret.length >= 32) { "Pairing key must contain at least 32 characters." }
        secretPrefs(context).edit().putString(SECRET_KEY, secret).apply()
        startListener(context)
    }

    @Synchronized
    fun startListener(context: Context) {
        if (pairingSecret(context).length < 32 || listener != null) return

        // Bind before starting the worker so repeated app/resume calls cannot
        // race and create multiple accept loops for the same port.
        val server = try {
            ServerSocket(PORT).apply { soTimeout = 0 }
        } catch (_: Exception) {
            return
        }
        listener = server
        thread(name = "Folio-P2P-listener", isDaemon = true) {
            try {
                while (!server.isClosed) {
                    val socket = server.accept()
                    thread(name = "Folio-P2P-peer", isDaemon = true) { handlePeer(context, socket) }
                }
            } catch (_: Exception) {
                // Listener shutdown or a bind/accept failure leaves local notes usable.
            } finally {
                synchronized(this) {
                    if (listener === server) listener = null
                }
                runCatching { server.close() }
            }
        }
    }

    fun sync(context: Context, host: String, callback: (Result<Int>) -> Unit) {
        thread(name = "Folio-P2P-sync") {
            try {
                val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                val secret = pairingSecret(context).ifBlank { error("Pair this device first.") }
                require(secret.length >= 32) { "Pair this device first." }
                val socket = Socket()
                socket.connect(InetSocketAddress(host, PORT), 5000)
                socket.soTimeout = 10000
                socket.use {
                    val local = JSONArray(prefs.getString("notes", "[]") ?: "[]")
                    val payload = JSONObject().put("protocol", 1).put("notes", local)
                    writeFrame(socket, encrypt(payload.toString(), secret))
                    val response = JSONObject(decrypt(readFrame(socket), secret))
                    require(response.optInt("protocol") == 1) { "Peer protocol mismatch." }
                    val remote = response.optJSONArray("notes") ?: JSONArray()
                    val merged = merge(JSONArray(prefs.getString("notes", "[]") ?: "[]"), remote)
                    prefs.edit().putString("notes", merged.toString()).apply()
                    callback(Result.success(merged.length()))
                }
            } catch (e: Exception) { callback(Result.failure(e)) }
        }
    }

    private fun handlePeer(context: Context, socket: Socket) {
        socket.use {
            it.soTimeout = 10000
            val secret = pairingSecret(context)
            require(secret.length >= 32) { "Pairing is not configured." }
            val request = JSONObject(decrypt(readFrame(it), secret))
            require(request.optInt("protocol") == 1) { "Unsupported peer protocol." }
            val remote = request.optJSONArray("notes") ?: JSONArray()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val local = JSONArray(prefs.getString("notes", "[]") ?: "[]")
            val merged = merge(local, remote)
            prefs.edit().putString("notes", merged.toString()).commit()
            FolioWidgetProvider.refresh(context)
            onRemoteNotes?.invoke(merged.toString())
            val response = JSONObject().put("protocol", 1).put("notes", merged)
            writeFrame(it, encrypt(response.toString(), secret))
        }
    }

    private fun merge(local: JSONArray, remote: JSONArray): JSONArray {
        val byId = linkedMapOf<String, JSONObject>()
        fun ingest(array: JSONArray) {
            for (i in 0 until array.length()) {
                val note = array.optJSONObject(i) ?: continue
                val normalized = JSONObject(note.toString())
                val rawId = normalized.optString("id")
                val id = runCatching { UUID.fromString(rawId).toString() }.getOrNull() ?: continue
                normalized.put("id", id)
                val previous = byId[id]
                if (previous == null) byId[id] = normalized
                else {
                    val oldTime = previous.optLong("updatedAt")
                    val newTime = normalized.optLong("updatedAt")
                    if (!sameContent(normalized, previous)) {
                        // Preserve the losing branch as a deterministic conflict copy; never silently discard edits.
                        if (newTime > oldTime) {
                            val preserved = conflictCopy(previous, id)
                            byId[preserved.getString("id")] = preserved
                            byId[id] = normalized
                        } else {
                            val preserved = conflictCopy(normalized, id)
                            byId[preserved.getString("id")] = preserved
                        }
                    } else if (newTime > oldTime) byId[id] = normalized
                }
            }
        }
        ingest(local); ingest(remote)
        return JSONArray().also { result -> byId.values.forEach(result::put) }
    }

    private fun conflictCopy(source: JSONObject, originalId: String): JSONObject {
        val fingerprint = listOf(originalId, source.optLong("updatedAt").toString(),
            source.optString("title"), source.optString("body"), source.optString("folder"),
            source.optString("tags"), source.optBoolean("pinned").toString(),
            source.optBoolean("archived").toString(), source.optBoolean("deleted").toString(),
            source.optString("priority", "Normal")).joinToString("|")
        val copy = JSONObject(source.toString())
        copy.put("id", UUID.nameUUIDFromBytes(fingerprint.toByteArray(Charsets.UTF_8)).toString())
        copy.put("title", copy.optString("title") + " (Sync conflict)")
        copy.put("folder", "Sync Conflicts")
        copy.put("pinned", false)
        return copy
    }

    private fun sameContent(a: JSONObject, b: JSONObject): Boolean {
        val fields = listOf("title", "body", "folder", "tags", "pinned", "archived", "deleted", "priority")
        return fields.all { a.optString(it) == b.optString(it) }
    }

    private fun encrypt(plain: String, secret: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val key = derive(secret, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return JSONObject().put("salt", b64(salt)).put("iv", b64(iv)).put("data", b64(ciphertext)).toString()
    }

    private fun decrypt(frame: String, secret: String): String {
        val envelope = JSONObject(frame)
        val salt = un64(envelope.getString("salt"))
        val iv = un64(envelope.getString("iv"))
        val data = un64(envelope.getString("data"))
        require(data.size <= MAX_FRAME) { "Peer frame too large." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(secret, salt), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(data), Charsets.UTF_8)
    }

    private fun derive(secret: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(secret.toCharArray(), salt, 210000, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }
    private fun b64(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun un64(value: String) = Base64.decode(value, Base64.DEFAULT)
    private fun writeFrame(socket: Socket, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_FRAME) { "Sync payload exceeds 8 MB." }
        val out = socket.getOutputStream()
        out.write(byteArrayOf((bytes.size shr 24).toByte(), (bytes.size shr 16).toByte(), (bytes.size shr 8).toByte(), bytes.size.toByte()))
        out.write(bytes); out.flush()
    }
    private fun readFrame(socket: Socket): String {
        val input = socket.getInputStream()
        val header = ByteArray(4); var offset = 0
        while (offset < 4) { val n = input.read(header, offset, 4 - offset); require(n > 0) { "Peer disconnected." }; offset += n }
        val size = ((header[0].toInt() and 255) shl 24) or ((header[1].toInt() and 255) shl 16) or ((header[2].toInt() and 255) shl 8) or (header[3].toInt() and 255)
        require(size in 1..MAX_FRAME) { "Invalid peer frame size." }
        val bytes = ByteArray(size); offset = 0
        while (offset < size) { val n = input.read(bytes, offset, size - offset); require(n > 0) { "Incomplete peer frame." }; offset += n }
        return String(bytes, Charsets.UTF_8)
    }
}
