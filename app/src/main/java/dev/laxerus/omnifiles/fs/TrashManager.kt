package dev.laxerus.omnifiles.fs

import android.content.Context
import dev.laxerus.omnifiles.access.StorageAccessController
import java.io.File
import java.util.UUID

data class TrashTicket(
    val trashedFile: File,
    val originalFile: File
)

class TrashManager(private val context: Context) {
    private val trashRoot: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "trash").apply {
            check(exists() || mkdirs()) { "Çöp klasörü oluşturulamadı" }
        }.canonicalFile
    }

    fun moveToTrash(target: File): TrashTicket {
        val sharedRoot = StorageAccessController.sharedRoot()
        val safeTarget = FilePathPolicy.requireSafeTrashTarget(target, sharedRoot)
        val originalFile = safeTarget.canonicalFile
        val destination = File(trashRoot, "${System.currentTimeMillis()}-${UUID.randomUUID()}-${safeTarget.name}")
        require(!destination.exists()) { "Çöp hedefi zaten var" }

        if (safeTarget.renameTo(destination)) {
            return TrashTicket(destination.canonicalFile, originalFile)
        }

        if (safeTarget.isDirectory) {
            val copied = safeTarget.copyRecursively(destination, overwrite = false)
            check(copied && destination.exists()) { "Klasör güvenli biçimde kopyalanamadı; kaynak korunuyor" }
            check(safeTarget.deleteRecursively()) {
                "Kopya oluşturuldu ancak kaynak temizlenemedi; iki kopya da korunuyor"
            }
        } else {
            safeTarget.copyTo(destination, overwrite = false)
            check(destination.exists() && destination.length() == safeTarget.length()) {
                "Dosya doğrulanamadı; kaynak korunuyor"
            }
            check(safeTarget.delete()) {
                "Kopya oluşturuldu ancak kaynak temizlenemedi; iki kopya da korunuyor"
            }
        }
        return TrashTicket(destination.canonicalFile, originalFile)
    }

    fun restore(ticket: TrashTicket): File {
        val source = requireTrashEntry(ticket.trashedFile)
        val sharedRoot = StorageAccessController.sharedRoot()
        val destination = FilePathPolicy.requireMutableTarget(ticket.originalFile, sharedRoot)
        val parent = destination.parentFile?.canonicalFile ?: error("Eski üst klasör bulunamadı")
        FilePathPolicy.requireInside(parent, sharedRoot)
        require(parent.exists() && parent.isDirectory) { "Eski üst klasör artık mevcut değil" }
        require(!destination.exists()) { "Eski konumda aynı adda başka bir öğe var; geri alma iptal edildi" }

        if (source.renameTo(destination)) return destination.canonicalFile

        if (source.isDirectory) {
            val copied = source.copyRecursively(destination, overwrite = false)
            if (!copied || !destination.exists()) {
                if (destination.exists()) destination.deleteRecursively()
                error("Klasör eski konumuna geri yüklenemedi; çöp kopyası korundu")
            }
            if (!source.deleteRecursively()) {
                error("Öğe geri yüklendi ancak çöp kopyası temizlenemedi")
            }
        } else {
            source.copyTo(destination, overwrite = false)
            if (!destination.exists() || destination.length() != source.length()) {
                destination.delete()
                error("Dosya geri yükleme doğrulamasından geçmedi; çöp kopyası korundu")
            }
            if (!source.delete()) {
                error("Öğe geri yüklendi ancak çöp kopyası temizlenemedi")
            }
        }
        return destination.canonicalFile
    }

    private fun requireTrashEntry(candidate: File): File {
        val safe = candidate.canonicalFile
        require(safe.parentFile?.canonicalPath == trashRoot.path) { "Geçersiz çöp kaydı" }
        require(safe.exists()) { "Çöp kaydı artık mevcut değil" }
        return safe
    }
}
