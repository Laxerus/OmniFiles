package dev.laxerus.omnifiles.fs

import android.content.Context
import dev.laxerus.omnifiles.access.StorageAccessController
import java.io.File
import java.util.UUID

class TrashManager(private val context: Context) {
    private val trashRoot: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "trash").apply { mkdirs() }
    }

    fun moveToTrash(target: File): File {
        val safeTarget = FilePathPolicy.requireSafeTrashTarget(target, StorageAccessController.sharedRoot())
        val destination = File(trashRoot, "${System.currentTimeMillis()}-${UUID.randomUUID()}-${safeTarget.name}")
        require(!destination.exists()) { "Çöp hedefi zaten var" }
        if (safeTarget.renameTo(destination)) return destination

        if (safeTarget.isDirectory) {
            safeTarget.copyRecursively(destination, overwrite = false)
            check(safeTarget.deleteRecursively()) { "Kaynak klasör temizlenemedi" }
        } else {
            safeTarget.copyTo(destination, overwrite = false)
            check(safeTarget.delete()) { "Kaynak dosya silinemedi" }
        }
        return destination
    }
}
