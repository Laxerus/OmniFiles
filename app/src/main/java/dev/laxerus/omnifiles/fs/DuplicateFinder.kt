package dev.laxerus.omnifiles.fs

import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.Locale

object DuplicateFinder {
    const val DEFAULT_MAX_ENTRIES = 30_000
    const val DEFAULT_MAX_FINGERPRINTED_FILES = 12_000
    const val DEFAULT_MAX_HASHED_FILES = 4_000
    const val DEFAULT_MAX_GROUPS = 100
    const val DEFAULT_MIN_FILE_SIZE_BYTES = 64L * 1024L
    const val DEFAULT_MAX_HASHED_BYTES = 16L * 1024L * 1024L * 1024L
    private const val BUFFER_BYTES = 64 * 1024
    private const val SAMPLE_BYTES = 64 * 1024

    data class DuplicateFile(
        val path: String,
        val sizeBytes: Long,
        val modifiedAt: Long,
    )

    data class DuplicateGroup(
        val sha256: String,
        val sizeBytes: Long,
        val files: List<DuplicateFile>,
    ) {
        val reclaimableBytes: Long
            get() = saturatingMultiply(sizeBytes, (files.size - 1).coerceAtLeast(0))
    }

    data class Result(
        val scannedEntries: Int,
        val fileCount: Int,
        val candidateFiles: Int,
        val fingerprintedFiles: Int,
        val hashedFiles: Int,
        val hashedBytes: Long,
        val skippedEntries: Int,
        val groups: List<DuplicateGroup>,
        val reclaimableBytes: Long,
        val truncated: Boolean,
        val cancelled: Boolean,
    )

    private data class Snapshot(
        val file: File,
        val sizeBytes: Long,
        val modifiedAt: Long,
    )

    fun scan(
        root: File,
        maxEntries: Int = DEFAULT_MAX_ENTRIES,
        maxFingerprintedFiles: Int = DEFAULT_MAX_FINGERPRINTED_FILES,
        maxHashedFiles: Int = DEFAULT_MAX_HASHED_FILES,
        maxHashedBytes: Long = DEFAULT_MAX_HASHED_BYTES,
        maxGroups: Int = DEFAULT_MAX_GROUPS,
        minFileSizeBytes: Long = DEFAULT_MIN_FILE_SIZE_BYTES,
        isCancelled: () -> Boolean = { false },
    ): Result {
        require(maxEntries > 0) { "Tarama öğe sınırı pozitif olmalı" }
        require(maxFingerprintedFiles > 0) { "Örnek parmak izi sınırı pozitif olmalı" }
        require(maxHashedFiles > 0) { "Hash dosya sınırı pozitif olmalı" }
        require(maxHashedBytes > 0L) { "Hash bayt sınırı pozitif olmalı" }
        require(maxGroups > 0) { "Grup sınırı pozitif olmalı" }
        require(minFileSizeBytes >= 0L) { "Minimum dosya boyutu negatif olamaz" }

        val safeRoot = FilePathPolicy.canonical(root)
        require(safeRoot.exists() && safeRoot.isDirectory) { "Tarama kökü geçerli bir klasör değil" }

        val pending = ArrayDeque<File>()
        val visitedDirectories = mutableSetOf<String>()
        val sizeBuckets = linkedMapOf<Long, MutableList<Snapshot>>()
        pending.addLast(safeRoot)

        var scannedEntries = 0
        var fileCount = 0
        var skippedEntries = 0
        var truncated = false
        var cancelled = false

        scanLoop@ while (pending.isNotEmpty()) {
            if (isCancelled()) {
                cancelled = true
                break
            }
            if (scannedEntries >= maxEntries) {
                truncated = true
                break
            }

            val raw = pending.removeFirst()
            val safe = try {
                FilePathPolicy.requireDirectEntry(raw, safeRoot)
            } catch (_: Throwable) {
                skippedEntries++
                continue
            }
            if (!safe.exists()) {
                skippedEntries++
                continue
            }
            scannedEntries++

            when {
                safe.isDirectory -> {
                    if (!visitedDirectories.add(safe.canonicalPath)) {
                        skippedEntries++
                        continue@scanLoop
                    }
                    val children = safe.listFiles()
                    if (children == null) {
                        skippedEntries++
                    } else {
                        children.forEach(pending::addLast)
                    }
                }

                safe.isFile -> {
                    fileCount++
                    val size = safe.length().coerceAtLeast(0L)
                    if (size >= minFileSizeBytes) {
                        sizeBuckets.getOrPut(size) { mutableListOf() } += Snapshot(
                            file = safe,
                            sizeBytes = size,
                            modifiedAt = safe.lastModified().coerceAtLeast(0L),
                        )
                    }
                }

                else -> skippedEntries++
            }
        }

        if (cancelled) {
            return Result(
                scannedEntries = scannedEntries,
                fileCount = fileCount,
                candidateFiles = 0,
                fingerprintedFiles = 0,
                hashedFiles = 0,
                hashedBytes = 0L,
                skippedEntries = skippedEntries,
                groups = emptyList(),
                reclaimableBytes = 0L,
                truncated = truncated,
                cancelled = true,
            )
        }

        val candidateBuckets = sizeBuckets.values.filter { it.size > 1 }
        val candidateFiles = candidateBuckets.sumOf { it.size }
        val sampledBuckets = linkedMapOf<Pair<Long, String>, MutableList<Snapshot>>()
        var fingerprintedFiles = 0

        fingerprintLoop@ for (bucket in candidateBuckets) {
            for (snapshot in bucket) {
                if (isCancelled()) {
                    cancelled = true
                    break@fingerprintLoop
                }
                if (fingerprintedFiles >= maxFingerprintedFiles) {
                    truncated = true
                    break@fingerprintLoop
                }

                val safe = revalidate(snapshot, safeRoot)
                if (safe == null) {
                    skippedEntries++
                    continue
                }

                val fingerprint = try {
                    sampledFingerprint(safe, snapshot.sizeBytes, isCancelled)
                } catch (_: ScanCancelledException) {
                    cancelled = true
                    break@fingerprintLoop
                } catch (_: Throwable) {
                    skippedEntries++
                    continue
                }

                if (!matchesSnapshot(safe, snapshot)) {
                    skippedEntries++
                    continue
                }

                fingerprintedFiles++
                sampledBuckets.getOrPut(snapshot.sizeBytes to fingerprint) { mutableListOf() } += snapshot
            }
        }

        val fullHashBuckets = sampledBuckets.values.filter { it.size > 1 }
        val verifiedBuckets = linkedMapOf<Pair<Long, String>, MutableList<DuplicateFile>>()
        var hashedFiles = 0
        var hashedBytes = 0L

        hashLoop@ for (bucket in fullHashBuckets) {
            for (snapshot in bucket) {
                if (isCancelled()) {
                    cancelled = true
                    break@hashLoop
                }
                if (hashedFiles >= maxHashedFiles) {
                    truncated = true
                    break@hashLoop
                }
                if (snapshot.sizeBytes > maxHashedBytes - hashedBytes) {
                    truncated = true
                    continue
                }

                val safe = revalidate(snapshot, safeRoot)
                if (safe == null) {
                    skippedEntries++
                    continue
                }

                val digest = try {
                    sha256(safe, isCancelled)
                } catch (_: ScanCancelledException) {
                    cancelled = true
                    break@hashLoop
                } catch (_: Throwable) {
                    skippedEntries++
                    continue
                }

                if (!matchesSnapshot(safe, snapshot)) {
                    skippedEntries++
                    continue
                }

                hashedFiles++
                hashedBytes = saturatingAdd(hashedBytes, snapshot.sizeBytes)
                val key = snapshot.sizeBytes to digest
                verifiedBuckets.getOrPut(key) { mutableListOf() } += DuplicateFile(
                    path = safe.canonicalPath,
                    sizeBytes = snapshot.sizeBytes,
                    modifiedAt = snapshot.modifiedAt,
                )
            }
        }

        val allGroups = verifiedBuckets.entries.asSequence()
            .filter { it.value.size > 1 }
            .map { (key, files) ->
                DuplicateGroup(
                    sha256 = key.second,
                    sizeBytes = key.first,
                    files = files.sortedBy { it.path.lowercase(Locale.ROOT) },
                )
            }
            .sortedWith(
                compareByDescending<DuplicateGroup> { it.reclaimableBytes }
                    .thenByDescending { it.sizeBytes }
                    .thenByDescending { it.files.size }
                    .thenBy { it.sha256 }
            )
            .toList()

        if (allGroups.size > maxGroups) truncated = true
        val groups = allGroups.take(maxGroups)
        val reclaimableBytes = groups.fold(0L) { total, group -> saturatingAdd(total, group.reclaimableBytes) }

        return Result(
            scannedEntries = scannedEntries,
            fileCount = fileCount,
            candidateFiles = candidateFiles,
            fingerprintedFiles = fingerprintedFiles,
            hashedFiles = hashedFiles,
            hashedBytes = hashedBytes,
            skippedEntries = skippedEntries,
            groups = groups,
            reclaimableBytes = reclaimableBytes,
            truncated = truncated,
            cancelled = cancelled,
        )
    }

    private fun revalidate(snapshot: Snapshot, safeRoot: File): File? {
        val safe = try {
            FilePathPolicy.requireDirectEntry(snapshot.file, safeRoot)
        } catch (_: Throwable) {
            return null
        }
        return safe.takeIf { matchesSnapshot(it, snapshot) }
    }

    private fun matchesSnapshot(file: File, snapshot: Snapshot): Boolean =
        file.isFile &&
            file.length() == snapshot.sizeBytes &&
            file.lastModified().coerceAtLeast(0L) == snapshot.modifiedAt

    private fun sampledFingerprint(
        file: File,
        sizeBytes: Long,
        isCancelled: () -> Boolean,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("size:$sizeBytes|".toByteArray(Charsets.UTF_8))
        val sampleLength = minOf(SAMPLE_BYTES.toLong(), sizeBytes).toInt()
        val maxOffset = (sizeBytes - sampleLength.toLong()).coerceAtLeast(0L)
        val offsets = linkedSetOf(
            0L,
            (maxOffset / 2L).coerceAtLeast(0L),
            maxOffset,
        )
        val buffer = ByteArray(sampleLength.coerceAtLeast(1))

        RandomAccessFile(file, "r").use { input ->
            offsets.forEach { offset ->
                if (isCancelled()) throw ScanCancelledException()
                digest.update("offset:$offset|".toByteArray(Charsets.UTF_8))
                input.seek(offset)
                var remaining = sampleLength
                var cursor = 0
                while (remaining > 0) {
                    if (isCancelled()) throw ScanCancelledException()
                    val read = input.read(buffer, cursor, remaining)
                    if (read < 0) break
                    if (read == 0) continue
                    cursor += read
                    remaining -= read
                }
                if (cursor != sampleLength) error("Dosya örneği tam okunamadı")
                digest.update(buffer, 0, cursor)
            }
        }
        return digest.digest().toHex()
    }

    private fun sha256(file: File, isCancelled: () -> Boolean): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        file.inputStream().buffered().use { input ->
            while (true) {
                if (isCancelled()) throw ScanCancelledException()
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun saturatingAdd(left: Long, right: Long): Long {
        if (right <= 0L) return left
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }

    private fun saturatingMultiply(value: Long, multiplier: Int): Long {
        if (value <= 0L || multiplier <= 0) return 0L
        return if (value > Long.MAX_VALUE / multiplier.toLong()) Long.MAX_VALUE else value * multiplier.toLong()
    }

    private class ScanCancelledException : RuntimeException()
}
