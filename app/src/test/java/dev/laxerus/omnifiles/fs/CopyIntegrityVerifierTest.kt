package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CopyIntegrityVerifierTest {
    @Test
    fun acceptsIdenticalFiles() {
        val root = Files.createTempDirectory("omnifiles-copy-integrity-file").toFile()
        try {
            val source = root.resolve("source.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
            val destination = root.resolve("destination.bin").apply { writeBytes(source.readBytes()) }

            assertTrue(CopyIntegrityVerifier.matches(source, destination))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSameSizeDifferentFileContent() {
        val root = Files.createTempDirectory("omnifiles-copy-integrity-different").toFile()
        try {
            val source = root.resolve("source.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
            val destination = root.resolve("destination.bin").apply { writeBytes(byteArrayOf(4, 3, 2, 1)) }

            assertFalse(CopyIntegrityVerifier.matches(source, destination))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun acceptsIdenticalDirectoryTrees() {
        val root = Files.createTempDirectory("omnifiles-copy-integrity-tree").toFile()
        try {
            val source = root.resolve("source").apply { mkdirs() }
            source.resolve("nested").mkdirs()
            source.resolve("a.txt").writeText("alpha")
            source.resolve("nested/b.bin").writeBytes(byteArrayOf(9, 8, 7, 6))

            val destination = root.resolve("destination")
            assertTrue(source.copyRecursively(destination, overwrite = false))
            assertTrue(CopyIntegrityVerifier.matches(source, destination))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsDirectoryTreeWithSameSizeMutation() {
        val root = Files.createTempDirectory("omnifiles-copy-integrity-tree-mutation").toFile()
        try {
            val source = root.resolve("source").apply { mkdirs() }
            source.resolve("nested").mkdirs()
            source.resolve("nested/data.bin").writeBytes(byteArrayOf(1, 1, 1, 1))

            val destination = root.resolve("destination")
            assertTrue(source.copyRecursively(destination, overwrite = false))
            destination.resolve("nested/data.bin").writeBytes(byteArrayOf(2, 2, 2, 2))

            assertFalse(CopyIntegrityVerifier.matches(source, destination))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkedTreeEntry() {
        val root = Files.createTempDirectory("omnifiles-copy-integrity-symlink").toFile()
        try {
            val outside = root.resolve("outside.txt").apply { writeText("outside") }
            val source = root.resolve("source").apply { mkdirs() }
            val destination = root.resolve("destination").apply { mkdirs() }
            val sourceLink = source.resolve("link.txt").toPath()
            val destinationLink = destination.resolve("link.txt").toPath()

            val linked = runCatching {
                Files.createSymbolicLink(sourceLink, outside.toPath())
                Files.createSymbolicLink(destinationLink, outside.toPath())
            }.isSuccess
            if (!linked) return

            assertFalse(CopyIntegrityVerifier.matches(source, destination))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkedRootFile() {
        val root = Files.createTempDirectory("omnifiles-copy-integrity-root-file-link").toFile()
        try {
            val realSource = root.resolve("real-source.bin").apply { writeBytes(byteArrayOf(5, 4, 3, 2, 1)) }
            val destination = root.resolve("destination.bin").apply { writeBytes(realSource.readBytes()) }
            val sourceLink = root.resolve("source-link.bin").toPath()
            val linked = runCatching { Files.createSymbolicLink(sourceLink, realSource.toPath()) }.isSuccess
            if (!linked) return

            assertFalse(CopyIntegrityVerifier.matches(sourceLink.toFile(), destination))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkedRootDirectory() {
        val root = Files.createTempDirectory("omnifiles-copy-integrity-root-dir-link").toFile()
        try {
            val realSource = root.resolve("real-source").apply { mkdirs() }
            realSource.resolve("a.txt").writeText("alpha")
            val destination = root.resolve("destination")
            assertTrue(realSource.copyRecursively(destination, overwrite = false))
            val sourceLink = root.resolve("source-link").toPath()
            val linked = runCatching { Files.createSymbolicLink(sourceLink, realSource.toPath()) }.isSuccess
            if (!linked) return

            assertFalse(CopyIntegrityVerifier.matches(sourceLink.toFile(), destination))
        } finally {
            root.deleteRecursively()
        }
    }
}
