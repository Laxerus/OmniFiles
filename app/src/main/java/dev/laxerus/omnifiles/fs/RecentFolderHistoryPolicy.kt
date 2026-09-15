package dev.laxerus.omnifiles.fs

import java.io.File

object RecentFolderHistoryPolicy {
    const val DEFAULT_MAX_ENTRIES = 12

    fun normalize(
        rawPaths: Iterable<String>,
        sharedRoot: File,
        maxEntries: Int = DEFAULT_MAX_ENTRIES
    ): List<File> {
        require(maxEntries > 0) { "Geçmiş sınırı pozitif olmalı" }
        val root = FilePathPolicy.canonical(sharedRoot)
        val seen = linkedSetOf<String>()
        val valid = mutableListOf<File>()

        for (rawPath in rawPaths) {
            if (valid.size >= maxEntries) break
            val directory = runCatching {
                FilePathPolicy.requireInside(File(rawPath), root)
            }.getOrNull() ?: continue
            if (!directory.exists() || !directory.isDirectory) continue
            if (directory.canonicalPath == root.canonicalPath) continue
            if (!seen.add(directory.canonicalPath)) continue
            valid += directory.canonicalFile
        }
        return valid
    }

    fun push(
        existingPaths: Iterable<String>,
        directory: File,
        sharedRoot: File,
        maxEntries: Int = DEFAULT_MAX_ENTRIES
    ): List<File> {
        require(maxEntries > 0) { "Geçmiş sınırı pozitif olmalı" }
        val root = FilePathPolicy.canonical(sharedRoot)
        val safe = FilePathPolicy.requireInside(directory, root)
        require(safe.exists() && safe.isDirectory) { "Yalnız mevcut klasörler geçmişe eklenebilir" }
        if (safe.canonicalPath == root.canonicalPath) {
            return normalize(existingPaths, root, maxEntries)
        }

        val paths = sequenceOf(safe.canonicalPath) + existingPaths.asSequence()
        return normalize(paths.asIterable(), root, maxEntries)
    }
}
