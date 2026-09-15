package dev.laxerus.omnifiles.fs

import java.io.File
import java.util.ArrayDeque

object StorageAnalyzer {
    const val DEFAULT_MAX_ENTRIES = 40_000
    const val DEFAULT_TOP_LIMIT = 20
    const val DEFAULT_CATEGORY_TOP_LIMIT = 8
    const val MAX_CATEGORY_TOP_LIMIT = 20

    enum class FileCategory {
        IMAGE,
        VIDEO,
        AUDIO,
        APK,
        ARCHIVE,
        DOCUMENT,
        DATABASE,
        OTHER
    }

    data class Entry(
        val path: String,
        val name: String,
        val sizeBytes: Long,
        val modifiedAt: Long,
        val isDirectory: Boolean
    )

    data class CategoryUsage(
        val category: FileCategory,
        val fileCount: Int,
        val sizeBytes: Long,
        val largestFiles: List<Entry>
    )

    data class Result(
        val visitedEntries: Int,
        val fileCount: Int,
        val directoryCount: Int,
        val skippedEntries: Int,
        val scannedBytes: Long,
        val truncated: Boolean,
        val cancelled: Boolean,
        val categories: List<CategoryUsage>,
        val largestFiles: List<Entry>,
        val largestDirectories: List<Entry>
    )

    fun scan(
        root: File,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        topLimit: Int = DEFAULT_TOP_LIMIT,
        categoryTopLimit: Int = DEFAULT_CATEGORY_TOP_LIMIT,
        isCancelled: () -> Boolean = { false }
    ): Result {
        require(maxEntries > 0) { "Tarama öğe sınırı pozitif olmalı" }
        require(topLimit > 0) { "Sonuç sınırı pozitif olmalı" }
        require(categoryTopLimit in 1..MAX_CATEGORY_TOP_LIMIT) {
            "Kategori sonuç sınırı 1-$MAX_CATEGORY_TOP_LIMIT arasında olmalı"
        }

        val safeRoot = FilePathPolicy.canonical(root)
        require(safeRoot.exists() && safeRoot.isDirectory) { "Tarama kökü geçerli bir klasör değil" }

        val pending = ArrayDeque<Frame>()
        val directorySizes = mutableMapOf<String, Long>()
        val visitedDirectories = mutableSetOf<String>()
        val largestFiles = mutableListOf<Entry>()
        val largestDirectories = mutableListOf<Entry>()
        val ranking = entryComparator()
        val categoryCounts = IntArray(FileCategory.entries.size)
        val categoryBytes = LongArray(FileCategory.entries.size)
        val categoryLargestFiles = Array(FileCategory.entries.size) { mutableListOf<Entry>() }

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
                        val entry = Entry(
                            path = safe.path,
                            name = safe.name,
                            sizeBytes = size,
                            modifiedAt = safe.lastModified().coerceAtLeast(0L),
                            isDirectory = false
                        )
                        scannedBytes = saturatingAdd(scannedBytes, size)
                        fileCount++
                        val category = classifyFileName(safe.name)
                        val categoryIndex = category.ordinal
                        categoryCounts[categoryIndex] = saturatingIncrement(categoryCounts[categoryIndex])
                        categoryBytes[categoryIndex] = saturatingAdd(categoryBytes[categoryIndex], size)
                        retainTop(
                            entries = largestFiles,
                            entry = entry,
                            limit = topLimit,
                            ranking = ranking
                        )
                        retainTop(
                            entries = categoryLargestFiles[categoryIndex],
                            entry = entry,
                            limit = categoryTopLimit,
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

        val categories = FileCategory.entries
            .map { category ->
                val index = category.ordinal
                CategoryUsage(
                    category = category,
                    fileCount = categoryCounts[index],
                    sizeBytes = categoryBytes[index],
                    largestFiles = categoryLargestFiles[index].sortedWith(ranking)
                )
            }
            .filter { it.fileCount > 0 }
            .sortedWith(
                compareByDescending<CategoryUsage> { it.sizeBytes }
                    .thenByDescending { it.fileCount }
                    .thenBy { it.category.ordinal }
            )

        return Result(
            visitedEntries = visitedEntries,
            fileCount = fileCount,
            directoryCount = directoryCount,
            skippedEntries = skippedEntries,
            scannedBytes = scannedBytes,
            truncated = truncated,
            cancelled = cancelled,
            categories = categories,
            largestFiles = largestFiles.sortedWith(ranking),
            largestDirectories = largestDirectories.sortedWith(ranking)
        )
    }

    fun verifyUnchangedFile(entry: Entry, root: File): File? {
        if (entry.isDirectory) return null
        val safeRoot = runCatching { FilePathPolicy.canonical(root) }.getOrNull() ?: return null
        val safe = runCatching {
            FilePathPolicy.requireDirectEntry(File(entry.path), safeRoot)
        }.getOrNull() ?: return null
        if (!safe.exists() || !safe.isFile) return null
        if (safe.length().coerceAtLeast(0L) != entry.sizeBytes) return null
        if (safe.lastModified().coerceAtLeast(0L) != entry.modifiedAt) return null
        return safe
    }

    internal fun classifyFileName(name: String): FileCategory {
        val extension = name.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(java.util.Locale.ROOT)
        if (extension.isEmpty()) return FileCategory.OTHER

        return when (extension) {
            in IMAGE_EXTENSIONS -> FileCategory.IMAGE
            in VIDEO_EXTENSIONS -> FileCategory.VIDEO
            in AUDIO_EXTENSIONS -> FileCategory.AUDIO
            in APK_EXTENSIONS -> FileCategory.APK
            in ARCHIVE_EXTENSIONS -> FileCategory.ARCHIVE
            in DOCUMENT_EXTENSIONS -> FileCategory.DOCUMENT
            in DATABASE_EXTENSIONS -> FileCategory.DATABASE
            else -> FileCategory.OTHER
        }
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

    private fun saturatingIncrement(value: Int): Int =
        if (value == Int.MAX_VALUE) Int.MAX_VALUE else value + 1

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

    private val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "dng", "tif", "tiff", "svg"
    )
    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "mkv", "webm", "avi", "mov", "m4v", "3gp", "3gpp", "mpg", "mpeg", "ts", "m2ts", "flv"
    )
    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "amr", "wma", "mid", "midi"
    )
    private val APK_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm", "aab")
    private val ARCHIVE_EXTENSIONS = setOf(
        "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "zst", "lz4", "jar"
    )
    private val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "txt", "md", "rtf", "doc", "docx", "odt", "xls", "xlsx", "ods", "ppt", "pptx", "odp",
        "csv", "json", "xml", "yaml", "yml", "epub", "mobi"
    )
    private val DATABASE_EXTENSIONS = setOf("db", "sqlite", "sqlite3", "realm")
}
