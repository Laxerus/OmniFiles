package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class StorageAnalyzerTest {
    @Test fun ranksLargestFilesAndAggregatedDirectories() {
        val root = createTempDirectory("omnifiles-analyzer-").toFile()
        try {
            val media = File(root, "Media").apply { mkdir() }
            File(media, "movie.bin").writeBytes(ByteArray(300))
            File(media, "cover.bin").writeBytes(ByteArray(20))
            val saves = File(root, "Saves").apply { mkdir() }
            File(saves, "slot.dat").writeBytes(ByteArray(120))

            val result = StorageAnalyzer.scan(root, maxEntries = 100, topLimit = 10)

            assertFalse(result.truncated)
            assertFalse(result.cancelled)
            assertEquals(3, result.fileCount)
            assertEquals(3, result.directoryCount)
            assertEquals(440L, result.scannedBytes)
            assertEquals("movie.bin", result.largestFiles.first().name)
            assertEquals(300L, result.largestFiles.first().sizeBytes)
            assertEquals("Media", result.largestDirectories.first().name)
            assertEquals(320L, result.largestDirectories.first().sizeBytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun topLimitKeepsOnlyLargestCandidates() {
        val root = createTempDirectory("omnifiles-analyzer-top-").toFile()
        try {
            repeat(60) { index ->
                File(root, "file-${index.toString().padStart(2, '0')}.bin")
                    .writeBytes(ByteArray(index + 1))
            }

            val result = StorageAnalyzer.scan(root, maxEntries = 100, topLimit = 4)

            assertEquals(60, result.fileCount)
            assertEquals(4, result.largestFiles.size)
            assertEquals(listOf(60L, 59L, 58L, 57L), result.largestFiles.map { it.sizeBytes })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun stopsAtConfiguredEntryLimit() {
        val root = createTempDirectory("omnifiles-analyzer-limit-").toFile()
        try {
            repeat(20) { index -> File(root, "file-$index.bin").writeBytes(ByteArray(index + 1)) }

            val result = StorageAnalyzer.scan(root, maxEntries = 5, topLimit = 3)

            assertTrue(result.truncated)
            assertTrue(result.visitedEntries <= 5)
            assertTrue(result.largestFiles.size <= 3)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun cancellationReturnsPartialResultWithoutThrowing() {
        val root = createTempDirectory("omnifiles-analyzer-cancel-").toFile()
        try {
            repeat(50) { index -> File(root, "file-$index.bin").writeBytes(ByteArray(8)) }
            var checks = 0

            val result = StorageAnalyzer.scan(
                root = root,
                maxEntries = 100,
                topLimit = 5,
                isCancelled = { ++checks > 8 }
            )

            assertTrue(result.cancelled)
            assertFalse(result.truncated)
            assertTrue(result.visitedEntries < 51)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun scansDeepDirectoryTreeWithoutRecursiveCallStack() {
        val root = createTempDirectory("omnifiles-analyzer-deep-").toFile()
        try {
            var current = root
            repeat(800) {
                current = File(current, "d").apply { mkdir() }
            }
            File(current, "payload.bin").writeBytes(ByteArray(32))

            val result = StorageAnalyzer.scan(root, maxEntries = 2_000, topLimit = 5)

            assertFalse(result.truncated)
            assertFalse(result.cancelled)
            assertEquals(1, result.fileCount)
            assertEquals(801, result.directoryCount)
            assertEquals(32L, result.scannedBytes)
            assertEquals(32L, result.largestDirectories.first().sizeBytes)
        } finally {
            deleteTreeIterative(root)
        }
    }

    private fun deleteTreeIterative(root: File) {
        val pending = java.util.ArrayDeque<File>()
        val visited = mutableListOf<File>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            visited += current
            if (current.isDirectory) current.listFiles().orEmpty().forEach(pending::addLast)
        }
        visited.asReversed().forEach { it.delete() }
    }
}
