package dev.laxerus.omnifiles.maintenance

import java.io.File
import java.util.ArrayDeque

object StartupMaintenance {
    private const val DAY_MS = 24L * 60L * 60L * 1000L
    const val DEFAULT_MAX_ENTRIES = 5_000

    private data class Rule(
        val directoryName: String,
        val retentionMs: Long,
    )

    data class Report(
        val scannedEntries: Int,
        val deletedFiles: Int,
        val deletedDirectories: Int,
        val reclaimedBytes: Long,
        val truncated: Boolean,
    )

    private val rules = listOf(
        Rule("adb-preview", 1L * DAY_MS),
        Rule("adb-checksum", 1L * DAY_MS),
        Rule("sqlite-studio", 7L * DAY_MS),
    )

    fun prune(
        cacheDirectory: File,
        nowMs: Long = System.currentTimeMillis(),
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
    ): Report {
        require(nowMs >= 0L) { "Geçerli saat negatif olamaz" }
        require(maxEntries > 0) { "Bakım öğe sınırı pozitif olmalı" }

        val cacheRoot = cacheDirectory.canonicalFile
        if (!cacheRoot.exists() || !cacheRoot.isDirectory) {
            return Report(0, 0, 0, 0L, false)
        }

        var scanned = 0
        var deletedFiles = 0
        var deletedDirectories = 0
        var reclaimedBytes = 0L
        var truncated = false

        for (rule in rules) {
            if (scanned >= maxEntries) {
                truncated = true
                break
            }
            val managedRoot = safeManagedRoot(cacheRoot, rule.directoryName) ?: continue
            if (!managedRoot.exists() || !managedRoot.isDirectory) continue
            val cutoff = if (nowMs >= rule.retentionMs) nowMs - rule.retentionMs else Long.MIN_VALUE
            val pending = ArrayDeque<File>()
            val visitedDirectories = mutableSetOf<String>()
            val directories = mutableListOf<File>()
            pending.addLast(managedRoot)

            while (pending.isNotEmpty()) {
                if (scanned >= maxEntries) {
                    truncated = true
                    break
                }
                val raw = pending.removeFirst()
                val safe = safeEntry(raw, managedRoot) ?: continue
                if (!safe.exists()) continue
                scanned++

                if (safe.isDirectory) {
                    if (!visitedDirectories.add(safe.canonicalPath)) continue
                    directories += safe
                    safe.listFiles()?.forEach(pending::addLast)
                    continue
                }
                if (!safe.isFile || !isStale(safe, cutoff)) continue

                val bytes = safe.length().coerceAtLeast(0L)
                if (safe.delete() || !safe.exists()) {
                    deletedFiles++
                    reclaimedBytes = saturatingAdd(reclaimedBytes, bytes)
                }
            }

            directories.asReversed().forEach { directory ->
                if (directory.canonicalPath == managedRoot.canonicalPath) return@forEach
                val safe = safeEntry(directory, managedRoot) ?: return@forEach
                val children = safe.listFiles() ?: return@forEach
                if (children.isEmpty() && (safe.delete() || !safe.exists())) deletedDirectories++
            }

            if (truncated) break
        }

        return Report(
            scannedEntries = scanned,
            deletedFiles = deletedFiles,
            deletedDirectories = deletedDirectories,
            reclaimedBytes = reclaimedBytes,
            truncated = truncated,
        )
    }

    private fun safeManagedRoot(cacheRoot: File, childName: String): File? = runCatching {
        val raw = File(cacheRoot, childName).absoluteFile
        val canonical = raw.canonicalFile
        require(canonical.path.startsWith(cacheRoot.path + File.separator))
        if (raw.exists()) require(raw.path == canonical.path)
        canonical
    }.getOrNull()

    private fun safeEntry(candidate: File, managedRoot: File): File? = runCatching {
        val absolute = candidate.absoluteFile
        val canonical = absolute.canonicalFile
        require(canonical.path == managedRoot.path || canonical.path.startsWith(managedRoot.path + File.separator))
        if (absolute.exists()) require(absolute.path == canonical.path)
        canonical
    }.getOrNull()

    private fun isStale(file: File, cutoff: Long): Boolean {
        val modified = file.lastModified()
        return modified > 0L && modified <= cutoff
    }

    private fun saturatingAdd(left: Long, right: Long): Long {
        if (right <= 0L) return left
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }
}
