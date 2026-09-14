package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class TransferPreflightTest {
    @Test fun cachedPreflightRejectsNestedMutationAndRollsBack() {
        val root = createTempDirectory("omnifiles-preflight-stale-").toFile()
        try {
            val source = File(root, "World").apply { mkdir() }
            val region = File(source, "region").apply { mkdir() }
            val payload = File(region, "r.0.0.mca").apply { writeBytes(ByteArray(64) { 7 }) }
            val destination = File(root, "Backups").apply { mkdir() }

            assertEquals(64L, FileOperations.estimateTransferBytes(source, root))
            payload.writeBytes(ByteArray(96) { 9 })

            assertThrows(IllegalStateException::class.java) {
                FileOperations.copy(source, destination, root)
            }

            assertTrue(source.isDirectory)
            assertEquals(96L, payload.length())
            assertFalse(File(destination, "World").exists())
            assertNoTransferStaging(destination)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun snapshotLimitFallsBackToFreshScan() {
        val root = createTempDirectory("omnifiles-preflight-limit-").toFile()
        try {
            val source = File(root, "Save").apply { mkdir() }
            val payload = File(source, "slot.dat").apply { writeText("old") }
            val destination = File(root, "Backups").apply { mkdir() }

            val preflight = FileOperations.prepareTransfer(
                source = source,
                sharedRoot = root,
                snapshotLimit = 1
            )
            assertFalse(preflight.reusable)
            assertEquals(0, preflight.snapshotCount)

            payload.writeText("new-and-longer")
            val copied = FileOperations.copy(
                source = source,
                destinationDirectory = destination,
                sharedRoot = root,
                preflight = preflight
            )

            assertEquals("new-and-longer", File(copied, "slot.dat").readText())
            assertNoTransferStaging(destination)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun preflightFromDifferentSourceCannotOverrideActualSource() {
        val root = createTempDirectory("omnifiles-preflight-source-").toFile()
        try {
            val sourceA = File(root, "a.bin").apply { writeText("AAAA") }
            val sourceB = File(root, "b.bin").apply { writeText("BBBBBBBB") }
            val destination = File(root, "Backups").apply { mkdir() }

            val preflightA = FileOperations.prepareTransfer(sourceA, root)
            assertTrue(preflightA.reusable)

            val copied = FileOperations.copy(
                source = sourceB,
                destinationDirectory = destination,
                sharedRoot = root,
                preflight = preflightA
            )

            assertEquals("BBBBBBBB", copied.readText())
            assertEquals(8L, copied.length())
            assertNoTransferStaging(destination)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun reusablePreflightCopiesUnchangedTree() {
        val root = createTempDirectory("omnifiles-preflight-valid-").toFile()
        try {
            val source = File(root, "Pack").apply { mkdir() }
            File(source, "a.txt").writeText("alpha")
            File(source, "nested").apply { mkdir() }
            File(source, "nested/b.txt").writeText("beta")
            val destination = File(root, "Backups").apply { mkdir() }

            val preflight = FileOperations.prepareTransfer(source, root)
            assertTrue(preflight.reusable)
            assertEquals(4, preflight.snapshotCount)

            val copied = FileOperations.copy(
                source = source,
                destinationDirectory = destination,
                sharedRoot = root,
                preflight = preflight
            )

            assertEquals("alpha", File(copied, "a.txt").readText())
            assertEquals("beta", File(copied, "nested/b.txt").readText())
            assertNoTransferStaging(destination)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertNoTransferStaging(directory: File) {
        assertTrue(
            directory.listFiles().orEmpty().none { it.name.startsWith(".omnifiles-transfer-") }
        )
    }
}
