package dev.laxerus.omnifiles.adb

import android.content.Context
import com.flyfishxu.kadb.Kadb
import dev.laxerus.omnifiles.fs.DigestUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AdbSessionManager private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val store = AdbEndpointStore(appContext)
    private val mutex = Mutex()
    private var session: Kadb? = null

    suspend fun pair(host: String, pairingPort: Int, code: String) {
        requireValidHost(host)
        require(pairingPort in 1..65535) { "Geçersiz eşleştirme portu" }
        require(code.matches(Regex("\\d{6}"))) { "Eşleştirme kodu 6 haneli olmalı" }
        Kadb.pair(host.trim(), pairingPort, code)
    }

    suspend fun connect(host: String, port: Int): String = mutex.withLock {
        requireValidHost(host)
        require(port in 1..65535) { "Geçersiz bağlantı portu" }
        withContext(Dispatchers.IO) {
            closeSessionBlocking()
            val next = newSession(host.trim(), port)
            try {
                val response = next.shell("id")
                check(response.exitCode == 0) { response.errorOutput.ifBlank { "ADB shell bağlantısı doğrulanamadı" } }
                session = next
                store.save(AdbEndpoint(host.trim(), port))
                response.output.trim()
            } catch (error: Throwable) {
                runCatching { next.close() }
                throw error
            }
        }
    }

    suspend fun reconnectSaved(): String {
        val endpoint = store.load() ?: error("Kayıtlı ADB bağlantısı yok")
        return connect(endpoint.host, endpoint.port)
    }

    suspend fun healthCheck(): Unit = mutex.withLock {
        withContext(Dispatchers.IO) {
            withReconnectOnce { active ->
                val response = active.shell("echo omnifiles-health")
                check(response.exitCode == 0 && response.output.trim() == "omnifiles-health") {
                    response.errorOutput.ifBlank { "ADB sağlık kontrolü başarısız" }
                }
            }
        }
    }

    suspend fun shell(command: String): String = mutex.withLock {
        require(command.isNotBlank()) { "Komut boş olamaz" }
        withContext(Dispatchers.IO) {
            withReconnectOnce { active ->
                val response = active.shell(command)
                check(response.exitCode == 0) { response.errorOutput.ifBlank { "ADB komutu başarısız" } }
                response.output
            }
        }
    }

    suspend fun listDirectory(path: String): List<AdbRemoteEntry> = mutex.withLock {
        val safePath = RemotePathPolicy.normalizeAbsolute(path)
        withContext(Dispatchers.IO) {
            withReconnectOnce { active ->
                active.openSync().use { sync ->
                    sync.list(safePath).mapNotNull { entry ->
                        if (!RemotePathPolicy.isSafeChildName(entry.name)) return@mapNotNull null
                        AdbRemoteEntry(
                            parentPath = safePath,
                            name = entry.name,
                            mode = entry.mode,
                            size = entry.size,
                            modifiedAtMillis = entry.mtimeSec * 1000L,
                            errorCode = entry.errorCode
                        )
                    }
                }
            }
        }
    }

    suspend fun pull(remotePath: String, destination: File): File = mutex.withLock {
        val safePath = RemotePathPolicy.normalizeAbsolute(remotePath)
        require(!destination.exists()) { "Yerel hedef zaten var" }
        destination.parentFile?.let { parent ->
            check(parent.exists() || parent.mkdirs()) { "Önizleme klasörü oluşturulamadı" }
        }
        withContext(Dispatchers.IO) {
            try {
                withReconnectOnce { active ->
                    val before = snapshotRemote(active, safePath)
                    active.pull(destination, safePath)
                    check(destination.isFile) { "ADB indirme sonucu dosya oluşmadı" }

                    if (before != null) {
                        val after = snapshotRemote(active, safePath)
                            ?: error("Uzak dosya indirme sırasında kayboldu")
                        check(before == after) {
                            "Uzak dosya indirme sırasında değişti; tutarsız kopya kullanılmadı"
                        }
                        check(after.size < 0L || destination.length() == after.size) {
                            "ADB indirme boyut doğrulamasından geçmedi"
                        }
                    }
                    destination
                }
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }
        }
    }

    suspend fun sha256(remotePath: String): String {
        val safePath = RemotePathPolicy.normalizeAbsolute(remotePath)
        val scratch = newChecksumScratch()
        val pulled = pull(safePath, scratch)
        return try {
            withContext(Dispatchers.IO) { DigestUtils.sha256Hex(pulled) }
        } finally {
            pulled.delete()
        }
    }

    suspend fun disconnect(forgetEndpoint: Boolean = false) = mutex.withLock {
        withContext(Dispatchers.IO) { closeSessionBlocking() }
        if (forgetEndpoint) store.clear()
    }

    fun endpoint(): AdbEndpoint? = store.load()

    private fun newChecksumScratch(): File {
        val root = File(appContext.cacheDir, "adb-checksum").apply {
            check(exists() || mkdirs()) { "ADB checksum cache klasörü oluşturulamadı" }
        }
        val cutoff = System.currentTimeMillis() - CHECKSUM_CACHE_RETENTION_MS
        root.listFiles()?.forEach { stale ->
            if (stale.isFile && stale.lastModified() < cutoff) stale.delete()
        }
        return File(root, "${UUID.randomUUID()}.bin")
    }

    private fun snapshotRemote(active: Kadb, safePath: String): RemoteSnapshot? {
        if (safePath == "/") return null
        val parent = RemotePathPolicy.parent(safePath) ?: return null
        val name = safePath.substringAfterLast('/')
        return active.openSync().use { sync ->
            sync.list(parent)
                .firstOrNull {
                    entry -> entry.name == name &&
                        RemotePathPolicy.isSafeChildName(entry.name) &&
                        (entry.errorCode == null || entry.errorCode == 0)
                }
                ?.let { entry ->
                    RemoteSnapshot(
                        size = entry.size,
                        modifiedAtMillis = entry.mtimeSec * 1000L,
                        mode = entry.mode
                    )
                }
        }
    }

    private fun <T> withReconnectOnce(operation: (Kadb) -> T): T {
        val first = ensureSessionBlocking()
        return try {
            operation(first)
        } catch (error: Throwable) {
            if (first.connectionCheck()) throw error
            closeSessionBlocking()
            val retry = ensureSessionBlocking()
            operation(retry)
        }
    }

    private fun ensureSessionBlocking(): Kadb {
        session?.let { active ->
            if (active.connectionCheck()) return active
            closeSessionBlocking()
        }
        val endpoint = store.load() ?: error("ADB bağlı değil")
        val candidate = newSession(endpoint.host, endpoint.port)
        try {
            val probe = candidate.shell("echo omnifiles-ready")
            check(probe.exitCode == 0 && probe.output.trim() == "omnifiles-ready") {
                "Kayıtlı ADB oturumu yeniden açılamadı"
            }
            session = candidate
            return candidate
        } catch (error: Throwable) {
            runCatching { candidate.close() }
            throw error
        }
    }

    private fun newSession(host: String, port: Int): Kadb =
        Kadb.create(host, port, connectTimeout = CONNECT_TIMEOUT_MS, socketTimeout = SOCKET_TIMEOUT_MS)

    private fun closeSessionBlocking() {
        val old = session
        session = null
        runCatching { old?.close() }
    }

    private fun requireValidHost(host: String) {
        val value = host.trim()
        require(value.isNotEmpty() && value.length <= 253) { "Geçersiz host" }
        require(!value.any { it.isWhitespace() || it == '/' || it == '\\' }) { "Geçersiz host" }
    }

    private data class RemoteSnapshot(
        val size: Long,
        val modifiedAtMillis: Long,
        val mode: Int
    )

    companion object {
        private const val CONNECT_TIMEOUT_MS = 8_000
        private const val SOCKET_TIMEOUT_MS = 15_000
        private const val CHECKSUM_CACHE_RETENTION_MS = 6L * 60L * 60L * 1000L
        @Volatile private var instance: AdbSessionManager? = null

        fun get(context: Context): AdbSessionManager = instance ?: synchronized(this) {
            instance ?: AdbSessionManager(context.applicationContext).also { instance = it }
        }
    }
}
