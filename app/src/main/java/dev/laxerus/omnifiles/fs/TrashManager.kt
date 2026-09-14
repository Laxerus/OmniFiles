package dev.laxerus.omnifiles.fs

import android.content.Context
import dev.laxerus.omnifiles.access.StorageAccessController
import java.io.File
import java.util.UUID

class TrashManager(private val context: Context) {
    private val trashRoot: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "trash").apply {
            check(exists() || mkdirs()) { "Çöp klasörü oluşturulamadı" }
        }
    }

    fun moveToTrash(target: File): File {
        val safeTarget = FilePathPolicy.requireSafeTrashTarget(target, StorageAccessController.sharedRoot())
        val destination = File(trashRoot, "${System.currentTimeMillis()}-${UUID.randomUUID()}-${safeTarget.name}")
        require(!destination.exists()) { "Çöp hedefi zaten var" }

        if (safeTarget.renameTo(destination)) return destination

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
        return destination
    }
}
