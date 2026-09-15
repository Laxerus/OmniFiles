package dev.laxerus.omnifiles.fs

import java.io.File

object QuickFolderPolicy {
    enum class Kind(val relativePath: String) {
        DOWNLOADS("Download"),
        DOCUMENTS("Documents"),
        PICTURES("Pictures"),
        CAMERA("DCIM"),
        VIDEOS("Movies"),
        MUSIC("Music"),
        ANDROID_MEDIA("Android/media"),
    }

    data class Entry(
        val kind: Kind,
        val directory: File,
    )

    fun available(sharedRoot: File): List<Entry> {
        val root = FilePathPolicy.canonical(sharedRoot)
        val seen = linkedSetOf<String>()
        return Kind.entries.mapNotNull { kind ->
            val candidate = File(root, kind.relativePath)
            val safe = runCatching { FilePathPolicy.requireDirectEntry(candidate, root) }.getOrNull()
                ?.takeIf { it.exists() && it.isDirectory && it.canRead() }
                ?: return@mapNotNull null
            if (!seen.add(safe.canonicalPath)) return@mapNotNull null
            Entry(kind, safe)
        }
    }
}
