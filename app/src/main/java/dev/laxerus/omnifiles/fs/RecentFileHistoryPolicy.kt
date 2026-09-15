package dev.laxerus.omnifiles.fs

import java.io.File

object RecentFileHistoryPolicy {
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
            val file = runCatching {
                FilePathPolicy.requireDirectEntry(File(rawPath), root)
            }.getOrNull() ?: continue
            if (!file.exists() || !file.isFile) continue
            val canonicalPath = file.canonicalPath
            if (!seen.add(canonicalPath)) continue
            valid += file.canonicalFile
        }
        return valid
    }

    fun push(
        existingPaths: Iterable<String>,
        file: File,
        sharedRoot: File,
        maxEntries: Int = DEFAULT_MAX_ENTRIES
    ): List<File> {
        require(maxEntries > 0) { "Geçmiş sınırı pozitif olmalı" }
        val root = FilePathPolicy.canonical(sharedRoot)
        val safe = FilePathPolicy.requireDirectEntry(file, root)
        require(safe.exists() && safe.isFile) { "Yalnız mevcut dosyalar geçmişe eklenebilir" }

        val paths = sequenceOf(safe.canonicalPath) + existingPaths.asSequence()
        return normalize(paths.asIterable(), root, maxEntries)
    }
}
