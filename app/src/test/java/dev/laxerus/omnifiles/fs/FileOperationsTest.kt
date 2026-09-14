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
        try {
            val created = FileOperations.createDirectory(root, "Yeni Klasor", root)
            assertTrue(created.isDirectory)
            assertEquals(File(root, "Yeni Klasor").canonicalPath, created.canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun renamesFileWithoutLeavingParent() {
        val root = createTempDir(prefix = "omnifiles-ops-")
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
        val root = createTempDir(prefix = "omnifiles-ops-")
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

    @Test fun copiesFilesWithoutReplacingExistingNames() {
        val root = createTempDir(prefix = "omnifiles-copy-")
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
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun copiesDirectoryTreesAndKeepsSource() {
        val root = createTempDir(prefix = "omnifiles-copy-tree-")
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
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsDirectoryTransferIntoItself() {
        val root = createTempDir(prefix = "omnifiles-cycle-")
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
        val root = createTempDir(prefix = "omnifiles-move-")
        try {
            val source = File(root, "move.txt").apply { writeText("payload") }
            val destination = File(root, "Target").apply { mkdir() }

            val moved = FileOperations.move(source, destination, root)

            assertFalse(source.exists())
            assertTrue(moved.isFile)
            assertEquals("payload", moved.readText())
            assertEquals(File(destination, "move.txt").canonicalPath, moved.canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }
}
