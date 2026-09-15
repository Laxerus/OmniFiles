package dev.laxerus.omnifiles.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class LocalFileIntentsTest {
    @Test
    fun requireDirectFileAcceptsRegularFile() {
        val root = createTempDirectory("omnifiles-local-intent-").toFile()
        try {
            val file = root.resolve("note.txt").apply { writeText("hello") }

            assertEquals(file.canonicalFile, LocalFileIntents.requireDirectFile(file))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun requireDirectFileRejectsDirectory() {
        val root = createTempDirectory("omnifiles-local-intent-dir-").toFile()
        try {
            val directory = root.resolve("folder").apply { mkdirs() }

            assertThrows(IllegalArgumentException::class.java) {
                LocalFileIntents.requireDirectFile(directory)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun requireDirectFileRejectsSymlink() {
        val root = createTempDirectory("omnifiles-local-intent-link-").toFile()
        try {
            val target = root.resolve("target.txt").apply { writeText("target") }
            val link = root.resolve("link.txt")
            if (runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }.isFailure) return

            assertThrows(IllegalArgumentException::class.java) {
                LocalFileIntents.requireDirectFile(link)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
