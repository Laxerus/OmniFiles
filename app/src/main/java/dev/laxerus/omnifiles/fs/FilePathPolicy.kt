package dev.laxerus.omnifiles.fs

import java.io.File

object FilePathPolicy {
    private const val MAX_CHILD_NAME_LENGTH = 255

    fun canonical(file: File): File = file.canonicalFile

    fun requireInside(target: File, allowedRoot: File): File {
        val root = canonical(allowedRoot)
        val absolute = target.absoluteFile
        val item = canonical(absolute)
        require(item.path == root.path || item.path.startsWith(root.path + File.separator)) {
            "İşlem izin verilen alanın dışında"
        }
        if (item.path != root.path && absolute.exists()) {
            require(absolute.path == item.path) {
                "Sembolik bağlantı veya dolaylı dosya yolu güvenli depolama işlemlerinde kullanılamaz"
            }
        }
        return item
    }

    fun requireDirectEntry(target: File, allowedRoot: File): File {
        val root = canonical(allowedRoot)
        val absolute = target.absoluteFile
        val item = requireInside(absolute, root)
        if (item.path != root.path) {
            require(absolute.path == item.path) {
                "Sembolik bağlantı veya dolaylı dosya yolu bu işlem için kullanılamaz"
            }
        }
        return item
    }

    fun sanitizeChildName(rawName: String): String {
        val name = rawName.trim()
        require(name.isNotEmpty()) { "Ad boş olamaz" }
        require(name.length <= MAX_CHILD_NAME_LENGTH) { "Ad çok uzun" }
        require(name != "." && name != "..") { "Bu ad kullanılamaz" }
        require('/' !in name && '\\' !in name && name.none { it.code == 0 }) {
            "Ad klasör ayırıcı veya geçersiz karakter içeremez"
        }
        require(name.none { Character.isISOControl(it) }) {
            "Ad satır sonu veya kontrol karakteri içeremez"
        }
        return name
    }

    fun resolveChild(parent: File, rawName: String, allowedRoot: File): File {
        val safeParent = requireDirectEntry(parent, allowedRoot)
        require(safeParent.isDirectory) { "Hedef üst klasör geçerli değil" }
        val child = File(safeParent, sanitizeChildName(rawName))
        return requireInside(child, allowedRoot)
    }

    fun requireMutableTarget(target: File, sharedRoot: File): File {
        val root = canonical(sharedRoot)
        val item = requireDirectEntry(target, root)
        val protected = setOf(
            root.path,
            File(root, "Android").canonicalPath,
            File(root, "Android/data").canonicalPath,
            File(root, "Android/obb").canonicalPath,
            File(root, "Android/media").canonicalPath
        )
        require(item.path !in protected) { "Bu sistem klasörünün kendisi değiştirilemez" }
        return item
    }

    fun requireSafeTrashTarget(target: File, sharedRoot: File): File {
        val item = requireMutableTarget(target, sharedRoot)
        require(item.exists()) { "Dosya artık mevcut değil" }
        return item
    }
}
