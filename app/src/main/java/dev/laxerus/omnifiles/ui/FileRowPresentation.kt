package dev.laxerus.omnifiles.ui

import java.io.File
import java.util.Locale

enum class FileVisualKind {
    DIRECTORY,
    IMAGE,
    VIDEO,
    AUDIO,
    ARCHIVE,
    ANDROID_PACKAGE,
    DATABASE,
    CONFIG,
    CODE,
    PDF,
    DOCUMENT,
    SPREADSHEET,
    PRESENTATION,
    EBOOK,
    FONT,
    TEXT,
    OTHER,
}

data class FileRowPresentation(
    val kind: FileVisualKind,
    val extensionLabel: String?,
    val icon: String,
)

object FileRowPresenter {
    fun describe(file: File): FileRowPresentation = describe(file.name, file.isDirectory)

    fun describe(name: String, isDirectory: Boolean): FileRowPresentation {
        val kind = classify(name, isDirectory)
        return FileRowPresentation(
            kind = kind,
            extensionLabel = extensionLabel(name, isDirectory),
            icon = iconFor(kind),
        )
    }

    fun classify(name: String, isDirectory: Boolean): FileVisualKind {
        if (isDirectory) return FileVisualKind.DIRECTORY
        return when (extension(name)) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "svg" -> FileVisualKind.IMAGE
            "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "mpeg", "mpg" -> FileVisualKind.VIDEO
            "mp3", "wav", "ogg", "m4a", "flac", "aac", "opus", "amr" -> FileVisualKind.AUDIO
            "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst" -> FileVisualKind.ARCHIVE
            "apk", "apks", "xapk", "apkm", "aab" -> FileVisualKind.ANDROID_PACKAGE
            "db", "sqlite", "sqlite3", "realm" -> FileVisualKind.DATABASE
            "json", "xml", "yaml", "yml", "toml", "ini", "properties", "conf", "cfg" -> FileVisualKind.CONFIG
            "kt", "kts", "java", "js", "jsx", "ts", "tsx", "py", "sh", "bash", "zsh", "html", "htm", "css", "scss", "c", "cpp", "cc", "h", "hpp", "rs", "go", "php", "rb", "swift", "gradle" -> FileVisualKind.CODE
            "pdf" -> FileVisualKind.PDF
            "doc", "docx", "odt", "rtf", "pages" -> FileVisualKind.DOCUMENT
            "xls", "xlsx", "ods", "numbers" -> FileVisualKind.SPREADSHEET
            "ppt", "pptx", "odp", "key" -> FileVisualKind.PRESENTATION
            "epub", "mobi", "azw", "azw3", "fb2" -> FileVisualKind.EBOOK
            "ttf", "otf", "woff", "woff2" -> FileVisualKind.FONT
            "txt", "md", "markdown", "log", "csv", "tsv", "srt", "vtt" -> FileVisualKind.TEXT
            else -> FileVisualKind.OTHER
        }
    }

    fun extensionLabel(name: String, isDirectory: Boolean): String? {
        if (isDirectory) return null
        val ext = extension(name)
        if (ext.isEmpty() || ext.length > 12) return null
        return ext.uppercase(Locale.ROOT)
    }

    private fun extension(name: String): String {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.startsWith('.') && trimmed.indexOf('.', 1) < 0) return ""
        val dot = trimmed.lastIndexOf('.')
        if (dot <= 0 || dot == trimmed.lastIndex) return ""
        return trimmed.substring(dot + 1).lowercase(Locale.ROOT)
    }

    private fun iconFor(kind: FileVisualKind): String = when (kind) {
        FileVisualKind.DIRECTORY -> "📁"
        FileVisualKind.IMAGE -> "🖼️"
        FileVisualKind.VIDEO -> "🎞️"
        FileVisualKind.AUDIO -> "🎵"
        FileVisualKind.ARCHIVE -> "🗜️"
        FileVisualKind.ANDROID_PACKAGE -> "📦"
        FileVisualKind.DATABASE -> "🗃️"
        FileVisualKind.CONFIG -> "⚙️"
        FileVisualKind.CODE -> "💻"
        FileVisualKind.PDF -> "📕"
        FileVisualKind.DOCUMENT -> "📘"
        FileVisualKind.SPREADSHEET -> "📊"
        FileVisualKind.PRESENTATION -> "📽️"
        FileVisualKind.EBOOK -> "📚"
        FileVisualKind.FONT -> "🔤"
        FileVisualKind.TEXT -> "📝"
        FileVisualKind.OTHER -> "📄"
    }
}
