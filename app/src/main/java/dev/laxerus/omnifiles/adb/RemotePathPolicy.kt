package dev.laxerus.omnifiles.adb

object RemotePathPolicy {
    private const val MAX_CHILD_NAME_LENGTH = 255

    fun normalizeAbsolute(path: String): String {
        require(path.startsWith('/')) { "ADB yolu mutlak olmalı" }
        require('\u0000' !in path) { "ADB yolunda NUL olamaz" }
        require(path.none { Character.isISOControl(it) }) {
            "ADB yolunda kontrol karakteri kullanılamaz"
        }
        val parts = path.split('/').filter { it.isNotEmpty() }
        require(parts.none { it == "." || it == ".." }) { "ADB yolunda göreli bileşen kullanılamaz" }
        require(parts.all(::isSafeChildName)) { "ADB yolu güvenli olmayan dosya adı içeriyor" }
        return if (parts.isEmpty()) "/" else "/" + parts.joinToString("/")
    }

    fun isSafeChildName(name: String): Boolean =
        name.isNotEmpty() &&
            name.length <= MAX_CHILD_NAME_LENGTH &&
            name != "." &&
            name != ".." &&
            '/' !in name &&
            '\u0000' !in name &&
            name.none { Character.isISOControl(it) }

    fun child(parent: String, name: String): String {
        val base = normalizeAbsolute(parent)
        require(isSafeChildName(name)) { "Geçersiz dosya adı" }
        return if (base == "/") "/$name" else "$base/$name"
    }

    fun parent(path: String): String? {
        val normalized = normalizeAbsolute(path)
        if (normalized == "/") return null
        val index = normalized.lastIndexOf('/')
        return if (index <= 0) "/" else normalized.substring(0, index)
    }
}
