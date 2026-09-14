package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileOperationsTest {
    @Test fun createsDirectoryInsideRoot() {
        val root = createTempDir(prefix = "omnifiles-ops-")
        val created = FileOperations.createDirectory(root, "Yeni Klasor", root)
        assertTrue(created.isDirectory)
        assertEquals(File(root, "Yeni Klasor").canonicalPath, created.canonicalPath)
        root.deleteRecursively()
    }

    @Test fun renamesFileWithoutLeavingParent() {
        val root = createTempDir(prefix = "omnifiles-ops-")
        val source = File(root, "old.txt").apply { writeText("data") }
        val renamed = FileOperations.rename(source, "new.txt", root)
        assertTrue(renamed.isFile)
        assertEquals("data", renamed.readText())
        assertFalse(source.exists())
        root.deleteRecursively()
    }

    @Test fun rejectsTraversalAndCollisions() {
        val root = createTempDir(prefix = "omnifiles-ops-")
        File(root, "exists").mkdir()
        assertThrows(IllegalArgumentException::class.java) {
            FileOperations.createDirectory(root, "../escape", root)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FileOperations.createDirectory(root, "exists", root)
        }
        root.deleteRecursively()
    }
}
