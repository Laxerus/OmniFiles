package dev.laxerus.omnifiles.adb

object RemotePathPolicy {
    fun normalizeAbsolute(path: String): String {
        require(path.startsWith('/')) { "ADB yolu mutlak olmalı" }
        require('\u0000' !in path) { "ADB yolunda NUL olamaz" }
        val parts = path.split('/').filter { it.isNotEmpty() }
        require(parts.none { it == "." || it == ".." }) { "ADB yolunda göreli bileşen kullanılamaz" }
        return if (parts.isEmpty()) "/" else "/" + parts.joinToString("/")
    }

    fun child(parent: String, name: String): String {
        val base = normalizeAbsolute(parent)
        require(name.isNotEmpty() && name != "." && name != "..") { "Geçersiz dosya adı" }
        require('/' !in name && '\u0000' !in name) { "Geçersiz dosya adı" }
        return if (base == "/") "/$name" else "$base/$name"
    }

    fun parent(path: String): String? {
        val normalized = normalizeAbsolute(path)
        if (normalized == "/") return null
        val index = normalized.lastIndexOf('/')
        return if (index <= 0) "/" else normalized.substring(0, index)
    }
}
