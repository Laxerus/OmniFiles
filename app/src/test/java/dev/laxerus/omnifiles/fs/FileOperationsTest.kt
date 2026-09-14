package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.createTempDirectory

class FileOperationsTest {
    @Test fun createsDirectoryInsideRoot() {
        val root = createTempDirectory("omnifiles-ops-").toFile()
        try {
            val created = FileOperations.createDirectory(root, "Yeni Klasor", root)
            assertTrue(created.isDirectory)
            assertEquals(File(root, "Yeni Klasor").canonicalPath, created.canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun renamesFileWithoutLeavingParent() {
        val root = createTempDirectory("omnifiles-ops-").toFile()
        try {
            val source = File(root, "old.txt").apply { writeText("data") }
            val renamed = FileOperations.rename(source, "new.txt", root)
            assertTrue(renamed.isFile)
            assertEquals("data", renamed.readText())
            assertFalse(source.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsTraversalAndCollisions() {
        val root = createTempDirectory("omnifiles-ops-").toFile()
        try {
            File(root, "exists").mkdir()
            assertThrows(IllegalArgumentException::class.java) {
                FileOperations.createDirectory(root, "../escape", root)
            }
            assertThrows(IllegalArgumentException::class.java) {
                FileOperations.createDirectory(root, "exists", root)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun estimatesNestedTransferBytesExactly() {
        val root = createTempDirectory("omnifiles-estimate-").toFile()
        try {
            val source = File(root, "World").apply { mkdir() }
            File(source, "level.dat").writeBytes(ByteArray(17))
            File(source, "region").apply { mkdir() }
            File(source, "region/r.0.0.mca").writeBytes(ByteArray(33))

            assertEquals(50L, FileOperations.estimateTransferBytes(source, root))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun refusesEstimatingEntireSharedRoot() {
        val root = createTempDirectory("omnifiles-estimate-root-").toFile()
        try {
            File(root, "sample.bin").writeBytes(ByteArray(4))
            assertThrows(IllegalArgumentException::class.java) {
                FileOperations.estimateTransferBytes(root, root)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun copiesFilesWithoutReplacingExistingNames() {
        val root = createTempDirectory("omnifiles-copy-").toFile()
        try {
            val source = File(root, "save.dat").apply { writeText("slot-a") }
            val destination = File(root, "Backup").apply { mkdir() }
            File(destination, "save.dat").writeText("older")

            val copied = FileOperations.copy(source, destination, root)

            assertTrue(source.exists())
            assertEquals("slot-a", source.readText())
            assertEquals("slot-a", copied.readText())
            assertEquals("save (1).dat", copied.name)
            assertEquals("older", File(destination, "save.dat").readText())
            assertNoTransferStaging(destination)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun copiesBinaryPayloadByteForByteAndCommitsStaging() {
        val root = createTempDirectory("omnifiles-copy-binary-").toFile()
        try {
            val payload = ByteArray(256 * 1024) { index -> ((index * 31) xor (index ushr 3)).toByte() }
            val source = File(root, "payload.bin").apply { writeBytes(payload) }
            val destination = File(root, "Backup").apply { mkdir() }

            val copied = FileOperations.copy(source, destination, root)

            assertArrayEquals(payload, copied.readBytes())
            assertEquals("payload.bin", copied.name)
            assertEquals(source.lastModified(), copied.lastModified())
            assertNoTransferStaging(destination)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun reportsMonotonicByteProgressAndFinishesAtTotal() {
        val root = createTempDirectory("omnifiles-copy-progress-").toFile()
        try {
            val payload = ByteArray(320 * 1024) { index -> (index * 13).toByte() }
            val source = File(root, "progress.bin").apply { writeBytes(payload) }
            val destination = File(root, "Backup").apply { mkdir() }
            val updates = mutableListOf<Pair<Long, Long>>()

            val copied = FileOperations.copy(
                source,
                destination,
                root,
                onProgress = { copiedBytes, totalBytes -> updates += copiedBytes to totalBytes }
            )

            assertArrayEquals(payload, copied.readBytes())
            assertTrue(updates.isNotEmpty())
            assertEquals(0L to payload.size.toLong(), updates.first())
            assertEquals(payload.size.toLong() to payload.size.toLong(), updates.last())
            assertTrue(updates.zipWithNext().all { (left, right) -> right.first >= left.first })
            assertTrue(updates.all { (_, total) -> total == payload.size.toLong() })
            assertNoTransferStaging(destination)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun cancellationRollsBackPartialStagingWithoutCommittingDestination() {
        val root = createTempDirectory("omnifiles-copy-cancel-").toFile()
        try {
            val payload = ByteArray(768 * 1024) { index -> (index * 17).toByte() }
            val source = File(root, "cancel.bin").apply { writeBytes(payload) }
            val destination = File(root, "Backup").apply { mkdir() }
            val copiedBytes = AtomicLong(0L)

            assertThrows(TransferCancelledException::class.java) {
                FileOperations.copy(
                    source,
                    destination,
                    root,
                    onProgress = { copied, _ -> copiedBytes.set(copied) },
                    isCancelled = { copiedBytes.get() >= 128L * 1024L }
                )
            }

            assertTrue(source.isFile)
            assertArrayEquals(payload, source.readBytes())
            assertFalse(File(destination, "cancel.bin").exists())
            assertTrue(copiedBytes.get() >= 128L * 1024L)
            assertNoTransferStaging(destination)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun copiesExtensionlessFilesWithSafeCollisionName() {
        val root = createTempDirectory("omnifiles-copy-name-").toFile()
        try {
            val source = File(root, "LICENSE").apply { writeText("new") }
            val destination = File(root, "Backup").apply { mkdir() }
            File(destination, "LICENSE").writeText("old")

            val copied = FileOperations.copy(source, destination, root)

            assertEquals("LICENSE (1)", copied.name)
            assertEquals("new", copied.readText())
            assertEquals("old", File(destination, "LICENSE").readText())
            assertNoTransferStaging(destination)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun copiesDirectoryTreesAndKeepsSource() {
        val root = createTempDirectory("omnifiles-copy-tree-").toFile()
        try {
            val source = File(root, "World").apply { mkdir() }
            File(source, "level.dat").writeText("world-data")
            File(source, "region").apply { mkdir() }
            File(source, "region/r.0.0.mca").writeText("region-data")
            val destination = File(root, "Backups").apply { mkdir() }

            val copied = FileOperations.copy(source, destination, root)

            assertTrue(source.exists())
            assertEquals("world-data", File(copied, "level.dat").readText())
            assertEquals("region-data", File(copied, "region/r.0.0.mca").readText())
            assertNoTransferStaging(destination)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun copiesVeryDeepDirectoryTreeWithoutRecursiveTraversal() {
        val root = createTempDirectory("omnifiles-deep-copy-").toFile()
        try {
            val source = File(root, "Deep").apply { mkdir() }
            var cursor = source
            repeat(1200) {
                cursor = File(cursor, "d").apply { check(mkdir()) }
            }
            File(cursor, "leaf.txt").writeText("deep-payload")
            val destination = File(root, "Backups").apply { mkdir() }

            val copied = FileOperations.copy(source, destination, root)
            var copiedCursor = copied
            repeat(1200) { copiedCursor = File(copiedCursor, "d") }

            assertEquals("deep-payload", File(copiedCursor, "leaf.txt").readText())
            assertNoTransferStaging(destination)
        } finally {
            deleteTreeIterative(root)
        }
    }

    @Test fun rejectsDirectoryTransferIntoItself() {
        val root = createTempDirectory("omnifiles-cycle-").toFile()
        try {
            val source = File(root, "Folder").apply { mkdir() }
            val child = File(source, "Child").apply { mkdir() }

            assertThrows(IllegalArgumentException::class.java) {
                FileOperations.copy(source, child, root)
            }
            assertThrows(IllegalArgumentException::class.java) {
                FileOperations.move(source, child, root)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun movesFileToAnotherDirectory() {
        val root = createTempDirectory("omnifiles-move-").toFile()
        try {
            val source = File(root, "move.txt").apply { writeText("payload") }
            val destination = File(root, "Target").apply { mkdir() }

            val moved = FileOperations.move(source, destination, root)

            assertFalse(source.exists())
            assertTrue(moved.isFile)
            assertEquals("payload", moved.readText())
            assertEquals(File(destination, "move.txt").canonicalPath, moved.canonicalPath)
            assertNoTransferStaging(destination)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun removesOnlyOldStructuredTransferStaging() {
        val root = createTempDirectory("omnifiles-staging-clean-").toFile()
        try {
            val destination = File(root, "Target").apply { mkdir() }
            val now = 2_000_000_000_000L
            val staleAfter = 6L * 60L * 60L * 1000L
            val oldTime = now - staleAfter - 10_000L
            val freshTime = now - 30_000L

            val stale = File(destination, ".omnifiles-transfer-v2-$oldTime-deadbeef").apply {
                mkdir()
                File(this, "partial.bin").writeText("partial")
                setLastModified(oldTime)
            }
            File(stale, "partial.bin").setLastModified(oldTime)
            val fresh = File(destination, ".omnifiles-transfer-v2-$freshTime-cafebabe").apply {
                writeText("active")
                setLastModified(freshTime)
            }
            val malformed = File(destination, ".omnifiles-transfer-v2-not-a-time-user-file").apply {
                writeText("keep")
                setLastModified(oldTime)
            }
            val legacy = File(destination, ".omnifiles-transfer-legacy").apply {
                writeText("keep")
                setLastModified(oldTime)
            }

            val removed = FileOperations.cleanupStaleStaging(destination, root, now, staleAfter)

            assertEquals(1, removed)
            assertFalse(stale.exists())
            assertTrue(fresh.exists())
            assertTrue(malformed.exists())
            assertTrue(legacy.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun stagingCleanupRequiresBothEncodedAgeAndFilesystemAge() {
        val root = createTempDirectory("omnifiles-staging-age-").toFile()
        try {
            val destination = File(root, "Target").apply { mkdir() }
            val now = 2_000_000_000_000L
            val staleAfter = 6L * 60L * 60L * 1000L
            val oldTime = now - staleAfter - 10_000L
            val recentFilesystemTime = now - 5_000L
            val candidate = File(destination, ".omnifiles-transfer-v2-$oldTime-feedface").apply {
                writeText("still-active")
                setLastModified(recentFilesystemTime)
            }

            assertEquals(0, FileOperations.cleanupStaleStaging(destination, root, now, staleAfter))
            assertTrue(candidate.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertNoTransferStaging(directory: File) {
        assertTrue(
            directory.listFiles().orEmpty().none { it.name.startsWith(".omnifiles-transfer-") }
        )
    }

    private fun deleteTreeIterative(root: File) {
        if (!root.exists()) return
        val pending = ArrayDeque<File>()
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
