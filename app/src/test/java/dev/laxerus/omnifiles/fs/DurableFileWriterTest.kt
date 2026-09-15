package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DurableFileWriterTest {
    @Test
    fun writesVerifiedUtf8AndRemovesTemp() {
        val root = Files.createTempDirectory("omnifiles-durable-writer").toFile()
        try {
            val temp = root.resolve("record.tmp")
            val destination = root.resolve("record.json")
            val content = "{\"path\":\"/storage/emulated/0/Test\",\"name\":\"çöp\"}"

            DurableFileWriter.writeNewUtf8(temp, destination, content)

            assertTrue(destination.isFile)
            assertEquals(content, destination.readText(Charsets.UTF_8))
            assertFalse(temp.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesToOverwriteExistingDestination() {
        val root = Files.createTempDirectory("omnifiles-durable-writer-existing").toFile()
        try {
            val temp = root.resolve("record.tmp")
            val destination = root.resolve("record.json").apply { writeText("original") }

            val failed = runCatching {
                DurableFileWriter.writeNewUtf8(temp, destination, "replacement")
            }.isFailure

            assertTrue(failed)
            assertEquals("original", destination.readText())
            assertFalse(temp.exists())
        } finally {
            root.deleteRecursively()
        }
    }
}
