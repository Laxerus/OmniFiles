package dev.laxerus.omnifiles.fs

import java.io.File
import java.util.ArrayDeque

data class FileInspection(
    val totalBytes: Long,
    val fileCount: Long,
    val directoryCount: Long,
    val skippedCount: Long,
    val truncated: Boolean,
    val readable: Boolean,
    val writable: Boolean,
    val lastModified: Long
)

object FileInspector {
    private const val DEFAULT_MAX_ENTRIES = 100_000

    fun inspect(target: File, sharedRoot: File, maxEntries: Int = DEFAULT_MAX_ENTRIES): FileInspection {
        require(maxEntries > 0) { "Tarama sınırı pozitif olmalı" }
        val root = FilePathPolicy.canonical(sharedRoot)
        val safeTarget = FilePathPolicy.requireInside(target, root)
        require(safeTarget.exists()) { "Öğe artık mevcut değil" }

        if (safeTarget.isFile) {
            return FileInspection(
                totalBytes = safeTarget.length().coerceAtLeast(0L),
                fileCount = 1,
                directoryCount = 0,
                skippedCount = 0,
                truncated = false,
                readable = safeTarget.canRead(),
                writable = safeTarget.canWrite(),
                lastModified = safeTarget.lastModified()
            )
        }

        var totalBytes = 0L
        var fileCount = 0L
        var directoryCount = 0L
        var skippedCount = 0L
        var inspectedEntries = 0
        var truncated = false
        val pending = ArrayDeque<File>()
        val visitedDirectories = mutableSetOf<String>()
        pending.add(safeTarget)

        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            val safeCurrent = runCatching { FilePathPolicy.requireInside(current, root) }.getOrNull()
            if (safeCurrent == null || !safeCurrent.exists()) {
                skippedCount++
                continue
            }

            if (safeCurrent.isFile) {
                totalBytes = saturatingAdd(totalBytes, safeCurrent.length().coerceAtLeast(0L))
                fileCount++
                continue
            }

            val canonicalPath = safeCurrent.canonicalPath
            if (!visitedDirectories.add(canonicalPath)) continue
            if (safeCurrent.canonicalPath != safeTarget.canonicalPath) directoryCount++

            val children = safeCurrent.listFiles()
            if (children == null) {
                skippedCount++
                continue
            }

            for (child in children) {
                if (inspectedEntries >= maxEntries) {
                    truncated = true
                    break
                }
                inspectedEntries++
                val safeChild = runCatching { FilePathPolicy.requireInside(child, root) }.getOrNull()
                if (safeChild == null) {
                    skippedCount++
                    continue
                }
                pending.addLast(safeChild)
            }
            if (truncated) break
        }

        return FileInspection(
            totalBytes = totalBytes,
            fileCount = fileCount,
            directoryCount = directoryCount,
            skippedCount = skippedCount,
            truncated = truncated,
            readable = safeTarget.canRead(),
            writable = safeTarget.canWrite(),
            lastModified = safeTarget.lastModified()
        )
    }

    private fun saturatingAdd(left: Long, right: Long): Long {
        if (right <= 0L) return left
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }
}
