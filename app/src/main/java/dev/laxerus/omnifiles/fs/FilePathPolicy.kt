package dev.laxerus.omnifiles.fs

import java.io.File

object FilePathPolicy {
    fun canonical(file: File): File = file.canonicalFile

    fun requireInside(target: File, allowedRoot: File): File {
        val root = canonical(allowedRoot)
        val item = canonical(target)
        require(item.path == root.path || item.path.startsWith(root.path + File.separator)) {
            "İşlem izin verilen alanın dışında"
        }
        return item
    }

    fun requireSafeTrashTarget(target: File, sharedRoot: File): File {
        val root = canonical(sharedRoot)
        val item = requireInside(target, root)
        val protected = setOf(
            root.path,
            File(root, "Android").canonicalPath,
            File(root, "Android/data").canonicalPath,
            File(root, "Android/obb").canonicalPath
        )
        require(item.path !in protected) { "Bu sistem klasörünün kendisi çöpe taşınamaz" }
        require(item.exists()) { "Dosya artık mevcut değil" }
        return item
    }
}
