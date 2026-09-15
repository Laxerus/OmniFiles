package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files

class TrashRestorePolicyTest {
    @Test
    fun unknownOriginalIsUnavailable() {
        assertEquals(
            TrashRestoreAvailability.ORIGINAL_UNKNOWN,
            TrashRestorePolicy.availability(null)
        )
    }

    @Test
    fun missingDestinationIsAvailable() {
        val root = Files.createTempDirectory("omnifiles-trash-restore-available").toFile()
        try {
            val destination = root.resolve("restored.txt")
            assertEquals(
                TrashRestoreAvailability.AVAILABLE,
                TrashRestorePolicy.availability(destination)
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun existingDestinationBlocksRestore() {
        val root = Files.createTempDirectory("omnifiles-trash-restore-conflict").toFile()
        try {
            val destination = root.resolve("restored.txt").apply { writeText("newer file") }
            assertEquals(
                TrashRestoreAvailability.DESTINATION_OCCUPIED,
                TrashRestorePolicy.availability(destination)
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
