package dev.laxerus.omnifiles.fs

import java.io.File

object QuickFolderPolicy {
    enum class Kind(val relativePaths: List<String>) {
        DOWNLOADS(listOf("Download")),
        DOCUMENTS(listOf("Documents")),
        PICTURES(listOf("Pictures")),
        CAMERA(listOf("DCIM")),
        SCREENSHOTS(listOf("DCIM/Screenshots", "Pictures/Screenshots")),
        VIDEOS(listOf("Movies")),
        RECORDINGS(
            listOf(
                "DCIM/Screen recordings",
                "Movies/Screen recordings",
                "Recordings",
                "Music/Recordings",
            )
        ),
        MUSIC(listOf("Music")),
        ANDROID_MEDIA(listOf("Android/media")),
    }

    data class Entry(
        val kind: Kind,
        val directory: File,
    )

    fun available(sharedRoot: File): List<Entry> {
        val root = FilePathPolicy.canonical(sharedRoot)
        val seen = linkedSetOf<String>()
        return Kind.entries.mapNotNull { kind ->
            val safe = kind.relativePaths.asSequence()
                .mapNotNull { relativePath ->
                    val candidate = File(root, relativePath)
                    runCatching { FilePathPolicy.requireDirectEntry(candidate, root) }.getOrNull()
                        ?.takeIf { it.exists() && it.isDirectory && it.canRead() }
                }
                .firstOrNull()
                ?: return@mapNotNull null
            if (!seen.add(safe.canonicalPath)) return@mapNotNull null
            Entry(kind, safe)
        }
    }
}
