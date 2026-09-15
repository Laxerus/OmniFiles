package dev.laxerus.omnifiles.fs

import java.io.File
import java.io.FileOutputStream

/** Small durable writer for critical app-owned metadata. */
object DurableFileWriter {
    fun writeNewUtf8(temp: File, destination: File, content: String) {
        val parent = destination.parentFile?.canonicalFile ?: error("Metadata üst klasörü bulunamadı")
        require(parent.exists() && parent.isDirectory) { "Metadata üst klasörü geçersiz" }
        require(!destination.exists()) { "Metadata hedefi zaten var" }
        require(temp.parentFile?.canonicalPath == parent.path) { "Geçici metadata dosyası aynı klasörde olmalı" }
        require(!temp.exists()) { "Geçici metadata dosyası zaten var" }

        val bytes = content.toByteArray(Charsets.UTF_8)
        try {
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.flush()
                output.fd.sync()
            }
            check(temp.isFile && temp.length() == bytes.size.toLong()) {
                "Geçici metadata dosyası doğrulanamadı"
            }
            check(temp.readBytes().contentEquals(bytes)) {
                "Geçici metadata içeriği doğrulanamadı"
            }
            check(temp.renameTo(destination)) { "Metadata kaydı atomik olarak tamamlanamadı" }
            if (!destination.isFile || destination.length() != bytes.size.toLong() ||
                !destination.readBytes().contentEquals(bytes)
            ) {
                destination.delete()
                error("Kalıcı metadata içeriği doğrulanamadı")
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }
}
