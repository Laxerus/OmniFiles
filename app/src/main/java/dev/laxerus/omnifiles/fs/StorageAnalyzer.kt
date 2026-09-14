package dev.laxerus.omnifiles.fs

import java.io.File
import java.util.ArrayDeque

object StorageAnalyzer {
    const val DEFAULT_MAX_ENTRIES = 40_000
    const val DEFAULT_TOP_LIMIT = 20

    data class Entry(
        val path: String,
        val name: String,
        val sizeBytes: Long,
        val modifiedAt: Long,
        val isDirectory: Boolean
    )

    data class Result(
        val visitedEntries: Int,
        val fileCount: Int,
        val directoryCount: Int,
        val skippedEntries: Int,
        val scannedBytes: Long,
        val truncated: Boolean,
        val cancelled: Boolean,
        val largestFiles: List<Entry>,
        val largestDirectories: List<Entry>
    )

    fun scan(
        root: File,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        topLimit: Int = DEFAULT_TOP_LIMIT,
        isCancelled: () -> Boolean = { false }
    ): Result {
        require(maxEntries > 0) { "Tarama öğe sınırı pozitif olmalı" }
        require(topLimit > 0) { "Sonuç sınırı pozitif olmalı" }

        val safeRoot = FilePathPolicy.canonical(root)
        require(safeRoot.exists() && safeRoot.isDirectory) { "Tarama kökü geçerli bir klasör değil" }

        val pending = ArrayDeque<Frame>()
        val directorySizes = mutableMapOf<String, Long>()
        val visitedDirectories = mutableSetOf<String>()
        val largestFiles = mutableListOf<Entry>()
        val largestDirectories = mutableListOf<Entry>()
        val ranking = entryComparator()

        var visitedEntries = 0
        var fileCount = 0
        var directoryCount = 0
        var skippedEntries = 0
        var scannedBytes = 0L
        var truncated = false
        var cancelled = false

        pending.addLast(Frame.Enter(safeRoot, parentPath = null, rankDirectory = false))

        scanLoop@ while (pending.isNotEmpty()) {
            if (isCancelled()) {
                cancelled = true
                break
            }

            when (val frame = pending.removeLast()) {
                is Frame.Exit -> {
                    val size = directorySizes.remove(frame.path) ?: 0L
                    if (frame.rankDirectory) {
                        retainTop(
                            entries = largestDirectories,
                            entry = Entry(
                                path = frame.path,
                                name = frame.name,
                                sizeBytes = size,
                                modifiedAt = frame.modifiedAt,
                                isDirectory = true
                            ),
                            limit = topLimit,
                            ranking = ranking
                        )
                    }
                    frame.parentPath?.let { parent ->
                        directorySizes[parent] = saturatingAdd(directorySizes[parent] ?: 0L, size)
                    }
                }

                is Frame.Enter -> {
                    if (visitedEntries >= maxEntries) {
                        truncated = true
                        break@scanLoop
                    }

                    val safe = try {
                        FilePathPolicy.requireDirectEntry(frame.file, safeRoot)
                    } catch (_: Throwable) {
                        skippedEntries++
                        continue@scanLoop
                    }
                    if (!safe.exists()) {
                        skippedEntries++
                        continue@scanLoop
                    }
                    visitedEntries++

                    if (safe.isFile) {
                        val size = safe.length().coerceAtLeast(0L)
                        scannedBytes = saturatingAdd(scannedBytes, size)
                        fileCount++
                        retainTop(
                            entries = largestFiles,
                            entry = Entry(
                                path = safe.path,
                                name = safe.name,
                                sizeBytes = size,
                                modifiedAt = safe.lastModified().coerceAtLeast(0L),
                                isDirectory = false
                            ),
                            limit = topLimit,
                            ranking = ranking
                        )
                        frame.parentPath?.let { parent ->
                            directorySizes[parent] = saturatingAdd(directorySizes[parent] ?: 0L, size)
                        }
                        continue@scanLoop
                    }

                    if (!safe.isDirectory) {
                        skippedEntries++
                        continue@scanLoop
                    }

                    val canonicalPath = safe.canonicalPath
                    if (!visitedDirectories.add(canonicalPath)) {
                        skippedEntries++
                        continue@scanLoop
                    }
                    directoryCount++
                    directorySizes[canonicalPath] = 0L
                    pending.addLast(
                        Frame.Exit(
                            path = canonicalPath,
                            name = safe.name.ifEmpty { canonicalPath },
                            modifiedAt = safe.lastModified().coerceAtLeast(0L),
                            parentPath = frame.parentPath,
                            rankDirectory = frame.rankDirectory
                        )
                    )

                    val children = safe.listFiles()
                    if (children == null) {
                        skippedEntries++
                        continue@scanLoop
                    }
                    for (index in children.indices.reversed()) {
                        pending.addLast(
                            Frame.Enter(
                                file = children[index],
                                parentPath = canonicalPath,
                                rankDirectory = true
                            )
                        )
                    }
                }
            }
        }

        return Result(
            visitedEntries = visitedEntries,
            fileCount = fileCount,
            directoryCount = directoryCount,
            skippedEntries = skippedEntries,
            scannedBytes = scannedBytes,
            truncated = truncated,
            cancelled = cancelled,
            largestFiles = largestFiles.sortedWith(ranking),
            largestDirectories = largestDirectories.sortedWith(ranking)
        )
    }

    private fun retainTop(
        entries: MutableList<Entry>,
        entry: Entry,
        limit: Int,
        ranking: Comparator<Entry>
    ) {
        if (entries.size < limit) {
            entries += entry
            return
        }

        var worstIndex = 0
        for (index in 1 until entries.size) {
            if (ranking.compare(entries[worstIndex], entries[index]) < 0) {
                worstIndex = index
            }
        }
        if (ranking.compare(entry, entries[worstIndex]) < 0) {
            entries[worstIndex] = entry
        }
    }

    private fun entryComparator(): Comparator<Entry> =
        compareByDescending<Entry> { it.sizeBytes }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.path }

    private fun saturatingAdd(left: Long, right: Long): Long {
        if (right <= 0L) return left
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }

    private sealed interface Frame {
        data class Enter(
            val file: File,
            val parentPath: String?,
            val rankDirectory: Boolean
        ) : Frame

        data class Exit(
            val path: String,
            val name: String,
            val modifiedAt: Long,
            val parentPath: String?,
            val rankDirectory: Boolean
        ) : Frame
    }
}
