package dev.laxerus.omnifiles.fs

import java.io.File

object FileOperations {
    fun createDirectory(parent: File, rawName: String, sharedRoot: File): File {
        val destination = FilePathPolicy.resolveChild(parent, rawName, sharedRoot)
        require(!destination.exists()) { "Bu adda bir öğe zaten var" }
        check(destination.mkdir()) { "Klasör oluşturulamadı" }
        return destination.canonicalFile
    }

    fun rename(target: File, rawName: String, sharedRoot: File): File {
        val safeTarget = FilePathPolicy.requireMutableTarget(target, sharedRoot)
        require(safeTarget.exists()) { "Öğe artık mevcut değil" }
        val parent = safeTarget.parentFile ?: error("Üst klasör bulunamadı")
        val destination = FilePathPolicy.resolveChild(parent, rawName, sharedRoot)
        require(destination.canonicalPath != safeTarget.canonicalPath) { "Yeni ad mevcut adla aynı" }
        require(!destination.exists()) { "Bu adda bir öğe zaten var" }
        check(safeTarget.renameTo(destination)) { "Yeniden adlandırma başarısız" }
        return destination.canonicalFile
    }
}
