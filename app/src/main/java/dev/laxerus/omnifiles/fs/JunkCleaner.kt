package dev.laxerus.omnifiles.fs

import java.io.File
import java.util.ArrayDeque
import java.util.Locale

enum class JunkKind {
    TEMP_FILE,
    PARTIAL_DOWNLOAD,
    METADATA,
    EMPTY_CACHE_DIRECTORY,
    EMPTY_LOG,
}

data class JunkCandidate(
    val path: String,
    val bytes: Long,
    val modifiedAt: Long,
    val kind: JunkKind,
)

data class JunkScanResult(
    val candidates: List<JunkCandidate>,
    val totalBytes: Long,
    val scannedEntries: Int,
    val truncated: Boolean,
)

data class JunkCleanupResult(
    val deleted: Int,
    val failed: Int,
    val reclaimedBytes: Long,
)

/**
 * Conservative shared-storage cleanup engine.
 *
 * It intentionally avoids Android/, does not infer that user media/documents are junk,
 * rejects indirect/symlink paths through FilePathPolicy, and only proposes patterns that
 * are either well-known metadata leftovers or sufficiently old temporary artifacts.
 */
object JunkCleaner {
    private const val DEFAULT_MAX_ENTRIES = 30_000
    private const val DEFAULT_MAX_CANDIDATES = 1_500
    private const val STALE_TEMP_AGE_MS = 7L * 24L * 60L * 60L * 1000L
    private const val STALE_LOG_AGE_MS = 30L * 24L * 60L * 60L * 1000L

    private val skippedTopLevelDirectories = setOf("android", "lost.dir")
    private val exactMetadataNames = setOf(".ds_store", "thumbs.db", "desktop.ini")
    private val partialSuffixes = listOf(".crdownload", ".download", ".part")
    private val tempSuffixes = listOf(".tmp", ".temp")
    private val emptyCacheNames = setOf("cache", ".cache", "temp", "tmp")

    fun scan(
        sharedRoot: File,
        now: Long = System.currentTimeMillis(),
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        maxCandidates: Int = DEFAULT_MAX_CANDIDATES,
    ): JunkScanResult {
        require(maxEntries > 0) { "Tarama sınırı pozitif olmalı" }
        require(maxCandidates > 0) { "Aday sınırı pozitif olmalı" }

        val root = FilePathPolicy.canonical(sharedRoot)
        require(root.exists() && root.isDirectory) { "Ortak depolama kökü okunamadı" }

        val queue = ArrayDeque<File>()
        root.listFiles()?.forEach(queue::addLast)
        val candidates = ArrayList<JunkCandidate>(minOf(maxCandidates, 128))
        var scanned = 0
        var truncated = false

        while (queue.isNotEmpty()) {
            if (scanned >= maxEntries || candidates.size >= maxCandidates) {
                truncated = true
                break
            }

            val raw = queue.removeFirst()
            val safe = runCatching { FilePathPolicy.requireDirectEntry(raw, root) }.getOrNull() ?: continue
            scanned++

            if (isInsideSkippedTopLevel(safe, root)) continue

            if (safe.isDirectory) {
                val children = safe.listFiles()
                val candidate = classifyDirectory(safe, children, now)
                if (candidate != null) candidates += candidate
                if (children != null && children.isNotEmpty()) children.forEach(queue::addLast)
                continue
            }

            if (!safe.isFile) continue
            classifyFile(safe, now)?.let(candidates::add)
        }

        return JunkScanResult(
            candidates = candidates.toList(),
            totalBytes = candidates.sumOf { it.bytes.coerceAtLeast(0L) },
            scannedEntries = scanned,
            truncated = truncated,
        )
    }

    fun clean(
        sharedRoot: File,
        candidates: List<JunkCandidate>,
        now: Long = System.currentTimeMillis(),
    ): JunkCleanupResult {
        val root = FilePathPolicy.canonical(sharedRoot)
        var deleted = 0
        var failed = 0
        var reclaimed = 0L

        candidates.distinctBy { it.path }.forEach { scannedCandidate ->
            val outcome = runCatching {
                val safe = FilePathPolicy.requireDirectEntry(File(scannedCandidate.path), root)
                require(!isInsideSkippedTopLevel(safe, root)) { "Korunan klasör" }
                require(safe.exists()) { "Öğe artık mevcut değil" }

                val refreshed = if (safe.isDirectory) {
                    classifyDirectory(safe, safe.listFiles(), now)
                } else if (safe.isFile) {
                    classifyFile(safe, now)
                } else {
                    null
                }
                require(refreshed != null && refreshed.kind == scannedCandidate.kind) {
                    "Öğe artık güvenli temizlik adayı değil"
                }
                require(
                    refreshed.bytes == scannedCandidate.bytes &&
                        refreshed.modifiedAt == scannedCandidate.modifiedAt
                ) { "Öğe taramadan sonra değişti; silme atlandı" }

                val bytes = refreshed.bytes.coerceAtLeast(0L)
                val removed = safe.delete()
                check(removed || !safe.exists()) { "Öğe silinemedi" }
                bytes
            }

            outcome.onSuccess { bytes ->
                deleted++
                reclaimed += bytes
            }.onFailure {
                failed++
            }
        }

        return JunkCleanupResult(
            deleted = deleted,
            failed = failed,
            reclaimedBytes = reclaimed,
        )
    }

    private fun classifyFile(file: File, now: Long): JunkCandidate? {
        val name = file.name.lowercase(Locale.ROOT)
        val age = ageMs(file, now)
        val kind = when {
            name in exactMetadataNames -> JunkKind.METADATA
            partialSuffixes.any(name::endsWith) && age >= STALE_TEMP_AGE_MS -> JunkKind.PARTIAL_DOWNLOAD
            tempSuffixes.any(name::endsWith) && age >= STALE_TEMP_AGE_MS -> JunkKind.TEMP_FILE
            name.endsWith(".log") && file.length() == 0L && age >= STALE_LOG_AGE_MS -> JunkKind.EMPTY_LOG
            else -> return null
        }
        return JunkCandidate(
            path = file.canonicalPath,
            bytes = file.length().coerceAtLeast(0L),
            modifiedAt = file.lastModified(),
            kind = kind,
        )
    }

    private fun classifyDirectory(file: File, children: Array<File>?, now: Long): JunkCandidate? {
        if (children == null || children.isNotEmpty()) return null
        val name = file.name.lowercase(Locale.ROOT)
        if (name !in emptyCacheNames || ageMs(file, now) < STALE_TEMP_AGE_MS) return null
        return JunkCandidate(
            path = file.canonicalPath,
            bytes = 0L,
            modifiedAt = file.lastModified(),
            kind = JunkKind.EMPTY_CACHE_DIRECTORY,
        )
    }

    private fun isInsideSkippedTopLevel(file: File, root: File): Boolean {
        val relative = file.path.removePrefix(root.path).trimStart(File.separatorChar)
        val first = relative.substringBefore(File.separatorChar).lowercase(Locale.ROOT)
        return first in skippedTopLevelDirectories
    }

    private fun ageMs(file: File, now: Long): Long {
        val modified = file.lastModified()
        if (modified <= 0L || modified > now) return 0L
        return now - modified
    }
}
