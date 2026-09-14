package dev.laxerus.omnifiles.adb

import android.content.Context
import com.flyfishxu.kadb.Kadb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class AdbSessionManager private constructor(context: Context) {
    private val store = AdbEndpointStore(context)
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
            val next = Kadb.create(host.trim(), port, connectTimeout = 8_000, socketTimeout = 15_000)
            try {
                val response = next.shell("id")
                check(response.exitCode == 0) { response.errorOutput.ifBlank { "ADB shell bağlantısı doğrulanamadı" } }
                session?.close()
                session = next
                store.save(AdbEndpoint(host.trim(), port))
                response.output.trim()
            } catch (error: Throwable) {
                next.close()
                throw error
            }
        }
    }

    suspend fun reconnectSaved(): String {
        val endpoint = store.load() ?: error("Kayıtlı ADB bağlantısı yok")
        return connect(endpoint.host, endpoint.port)
    }

    suspend fun shell(command: String): String = mutex.withLock {
        require(command.isNotBlank()) { "Komut boş olamaz" }
        withContext(Dispatchers.IO) {
            val active = ensureSessionBlocking()
            val response = active.shell(command)
            check(response.exitCode == 0) { response.errorOutput.ifBlank { "ADB komutu başarısız" } }
            response.output
        }
    }

    suspend fun listDirectory(path: String): List<AdbRemoteEntry> = mutex.withLock {
        val safePath = RemotePathPolicy.normalizeAbsolute(path)
        withContext(Dispatchers.IO) {
            ensureSessionBlocking().openSync().use { sync ->
                sync.list(safePath).map { entry ->
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

    suspend fun pull(remotePath: String, destination: File): File = mutex.withLock {
        val safePath = RemotePathPolicy.normalizeAbsolute(remotePath)
        require(!destination.exists()) { "Yerel hedef zaten var" }
        destination.parentFile?.let { parent -> check(parent.exists() || parent.mkdirs()) { "Önizleme klasörü oluşturulamadı" } }
        withContext(Dispatchers.IO) {
            try {
                ensureSessionBlocking().pull(destination, safePath)
                check(destination.isFile) { "ADB indirme sonucu dosya oluşmadı" }
                destination
            } catch (error: Throwable) {
                destination.delete()
                throw error
            }
        }
    }

    suspend fun disconnect() = mutex.withLock {
        withContext(Dispatchers.IO) {
            session?.close()
            session = null
        }
    }

    fun endpoint(): AdbEndpoint? = store.load()

    private fun ensureSessionBlocking(): Kadb {
        session?.let { return it }
        val endpoint = store.load() ?: error("ADB bağlı değil")
        val candidate = Kadb.create(endpoint.host, endpoint.port, connectTimeout = 8_000, socketTimeout = 15_000)
        try {
            val probe = candidate.shell("echo omnifiles-ready")
            check(probe.exitCode == 0 && probe.output.trim() == "omnifiles-ready") {
                "Kayıtlı ADB oturumu yeniden açılamadı"
            }
            session = candidate
            return candidate
        } catch (error: Throwable) {
            candidate.close()
            throw error
        }
    }

    private fun requireValidHost(host: String) {
        val value = host.trim()
        require(value.isNotEmpty() && value.length <= 253) { "Geçersiz host" }
        require(!value.any { it.isWhitespace() || it == '/' || it == '\\' }) { "Geçersiz host" }
    }

    companion object {
        @Volatile private var instance: AdbSessionManager? = null

        fun get(context: Context): AdbSessionManager = instance ?: synchronized(this) {
            instance ?: AdbSessionManager(context.applicationContext).also { instance = it }
        }
    }
}
