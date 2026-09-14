package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class FileInspectorTest {
    @Test fun inspectsSingleFile() {
        val root = createTempDirectory("omnifiles-inspect-").toFile()
        try {
            val file = File(root, "save.dat").apply { writeBytes(ByteArray(128)) }
            val result = FileInspector.inspect(file, root)

            assertEquals(128L, result.totalBytes)
            assertEquals(1L, result.fileCount)
            assertEquals(0L, result.directoryCount)
            assertFalse(result.truncated)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun inspectsDirectoryTree() {
        val root = createTempDirectory("omnifiles-inspect-tree-").toFile()
        try {
            val folder = File(root, "World").apply { mkdir() }
            File(folder, "level.dat").writeBytes(ByteArray(10))
            File(folder, "region").apply { mkdir() }
            File(folder, "region/r.0.0.mca").writeBytes(ByteArray(30))

            val result = FileInspector.inspect(folder, root)

            assertEquals(40L, result.totalBytes)
            assertEquals(2L, result.fileCount)
            assertEquals(1L, result.directoryCount)
            assertEquals(0L, result.skippedCount)
            assertFalse(result.truncated)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun enforcesEntryLimit() {
        val root = createTempDirectory("omnifiles-inspect-limit-").toFile()
        try {
            val folder = File(root, "Large").apply { mkdir() }
            repeat(5) { index -> File(folder, "$index.bin").writeBytes(ByteArray(4)) }

            val result = FileInspector.inspect(folder, root, maxEntries = 2)

            assertTrue(result.truncated)
            assertTrue(result.fileCount <= 2L)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsTargetsOutsideRoot() {
        val root = createTempDirectory("omnifiles-inspect-root-").toFile()
        val outside = createTempDirectory("omnifiles-inspect-outside-").toFile()
        try {
            val file = File(outside, "secret.txt").apply { writeText("secret") }
            assertThrows(IllegalArgumentException::class.java) {
                FileInspector.inspect(file, root)
            }
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }
}
