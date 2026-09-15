package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DuplicateFinderTest {
    @Test
    fun groupsOnlyFilesWithIdenticalContent() {
        val root = Files.createTempDirectory("omnifiles-duplicates").toFile()
        try {
            val payload = "same-content".repeat(8_000)
            root.resolve("one.bin").writeText(payload)
            root.resolve("nested").mkdirs()
            root.resolve("nested/two.bin").writeText(payload)
            root.resolve("other.bin").writeText("x".repeat(payload.length))

            val result = DuplicateFinder.scan(root, minFileSizeBytes = 1L)

            assertFalse(result.cancelled)
            assertEquals(1, result.groups.size)
            assertEquals(2, result.groups.single().files.size)
            assertEquals(payload.toByteArray().size.toLong(), result.groups.single().sizeBytes)
            assertEquals(result.groups.single().sizeBytes, result.reclaimableBytes)
            assertTrue(result.groups.single().files.any { it.path.endsWith("one.bin") })
            assertTrue(result.groups.single().files.any { it.path.endsWith("two.bin") })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresFilesBelowMinimumSize() {
        val root = Files.createTempDirectory("omnifiles-duplicates-small").toFile()
        try {
            root.resolve("a.txt").writeText("tiny")
            root.resolve("b.txt").writeText("tiny")

            val result = DuplicateFinder.scan(root, minFileSizeBytes = 64L)

            assertTrue(result.groups.isEmpty())
            assertEquals(0, result.candidateFiles)
            assertEquals(0, result.fingerprintedFiles)
            assertEquals(0, result.hashedFiles)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun sampledFingerprintFiltersDifferentContentBeforeFullHash() {
        val root = Files.createTempDirectory("omnifiles-duplicates-sample").toFile()
        try {
            val length = 256 * 1024
            root.resolve("a.bin").writeBytes(ByteArray(length) { 1 })
            root.resolve("b.bin").writeBytes(ByteArray(length) { 2 })
            root.resolve("c.bin").writeBytes(ByteArray(length) { 3 })

            val result = DuplicateFinder.scan(root, minFileSizeBytes = 1L)

            assertEquals(3, result.candidateFiles)
            assertEquals(3, result.fingerprintedFiles)
            assertEquals(0, result.hashedFiles)
            assertEquals(0L, result.hashedBytes)
            assertTrue(result.groups.isEmpty())
            assertFalse(result.truncated)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun respectsFingerprintLimitAndMarksResultTruncated() {
        val root = Files.createTempDirectory("omnifiles-duplicates-fingerprint-limit").toFile()
        try {
            val payload = "candidate".repeat(2_000)
            repeat(4) { index -> root.resolve("$index.bin").writeText(payload) }

            val result = DuplicateFinder.scan(
                root = root,
                minFileSizeBytes = 1L,
                maxFingerprintedFiles = 2,
            )

            assertTrue(result.truncated)
            assertEquals(2, result.fingerprintedFiles)
            assertEquals(2, result.hashedFiles)
            assertEquals(1, result.groups.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun respectsHashLimitAndMarksResultTruncated() {
        val root = Files.createTempDirectory("omnifiles-duplicates-limit").toFile()
        try {
            val payload = "duplicate".repeat(1_000)
            repeat(4) { index -> root.resolve("$index.bin").writeText(payload) }

            val result = DuplicateFinder.scan(
                root = root,
                minFileSizeBytes = 1L,
                maxHashedFiles = 2,
            )

            assertTrue(result.truncated)
            assertEquals(4, result.fingerprintedFiles)
            assertEquals(2, result.hashedFiles)
            assertEquals(1, result.groups.size)
            assertEquals(2, result.groups.single().files.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun respectsTotalHashedByteBudget() {
        val root = Files.createTempDirectory("omnifiles-duplicates-byte-limit").toFile()
        try {
            val payload = "duplicate-budget".repeat(1_000)
            root.resolve("a.bin").writeText(payload)
            root.resolve("b.bin").writeText(payload)
            val fileBytes = root.resolve("a.bin").length()

            val result = DuplicateFinder.scan(
                root = root,
                minFileSizeBytes = 1L,
                maxHashedBytes = fileBytes,
            )

            assertTrue(result.truncated)
            assertEquals(2, result.fingerprintedFiles)
            assertEquals(1, result.hashedFiles)
            assertEquals(fileBytes, result.hashedBytes)
            assertTrue(result.groups.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun cancellationStopsHashing() {
        val root = Files.createTempDirectory("omnifiles-duplicates-cancel").toFile()
        try {
            val payload = "duplicate".repeat(10_000)
            root.resolve("a.bin").writeText(payload)
            root.resolve("b.bin").writeText(payload)
            var checks = 0

            val result = DuplicateFinder.scan(
                root = root,
                minFileSizeBytes = 1L,
                isCancelled = { ++checks > 5 },
            )

            assertTrue(result.cancelled)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun verifyDuplicateAcceptsUnchangedVerifiedFile() {
        val root = Files.createTempDirectory("omnifiles-duplicates-verify").toFile()
        try {
            val payload = ByteArray(128 * 1024) { index -> (index % 251).toByte() }
            root.resolve("a.bin").writeBytes(payload)
            root.resolve("b.bin").writeBytes(payload)

            val group = DuplicateFinder.scan(root, minFileSizeBytes = 1L).groups.single()
            val duplicate = group.files.first()

            assertTrue(
                DuplicateFinder.verifyDuplicate(
                    file = java.io.File(duplicate.path),
                    root = root,
                    expectedSizeBytes = duplicate.sizeBytes,
                    expectedModifiedAt = duplicate.modifiedAt,
                    expectedSha256 = group.sha256,
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun verifyDuplicateRejectsContentChangedWithSameSizeAndTimestamp() {
        val root = Files.createTempDirectory("omnifiles-duplicates-verify-change").toFile()
        try {
            val payload = ByteArray(128 * 1024) { 7 }
            val first = root.resolve("a.bin").apply { writeBytes(payload) }
            root.resolve("b.bin").writeBytes(payload)

            val group = DuplicateFinder.scan(root, minFileSizeBytes = 1L).groups.single()
            val duplicate = group.files.first { it.path == first.canonicalPath }

            first.writeBytes(ByteArray(payload.size) { 9 })
            assertTrue(first.setLastModified(duplicate.modifiedAt))
            assertEquals(duplicate.sizeBytes, first.length())
            assertEquals(duplicate.modifiedAt, first.lastModified().coerceAtLeast(0L))

            assertFalse(
                DuplicateFinder.verifyDuplicate(
                    file = first,
                    root = root,
                    expectedSizeBytes = duplicate.sizeBytes,
                    expectedModifiedAt = duplicate.modifiedAt,
                    expectedSha256 = group.sha256,
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun verifyDuplicateRejectsInvalidDigestFormat() {
        val root = Files.createTempDirectory("omnifiles-duplicates-invalid-digest").toFile()
        try {
            val file = root.resolve("a.bin").apply { writeText("payload") }

            assertFalse(
                DuplicateFinder.verifyDuplicate(
                    file = file,
                    root = root,
                    expectedSizeBytes = file.length(),
                    expectedModifiedAt = file.lastModified().coerceAtLeast(0L),
                    expectedSha256 = "not-a-sha256",
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun verifyDuplicateRejectsDeletedFile() {
        val root = Files.createTempDirectory("omnifiles-duplicates-deleted").toFile()
        try {
            val file = root.resolve("a.bin").apply { writeText("payload") }
            val expectedSize = file.length()
            val expectedModified = file.lastModified().coerceAtLeast(0L)
            assertTrue(file.delete())

            assertFalse(
                DuplicateFinder.verifyDuplicate(
                    file = file,
                    root = root,
                    expectedSizeBytes = expectedSize,
                    expectedModifiedAt = expectedModified,
                    expectedSha256 = "0".repeat(64),
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun verifyDuplicateRejectsChangedSize() {
        val root = Files.createTempDirectory("omnifiles-duplicates-size-change").toFile()
        try {
            val file = root.resolve("a.bin").apply { writeText("payload") }
            val expectedSize = file.length()
            val expectedModified = file.lastModified().coerceAtLeast(0L)
            file.appendText("-changed")

            assertFalse(
                DuplicateFinder.verifyDuplicate(
                    file = file,
                    root = root,
                    expectedSizeBytes = expectedSize,
                    expectedModifiedAt = expectedModified,
                    expectedSha256 = "0".repeat(64),
                )
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun verifyDuplicateRejectsSymlinkEscapingRoot() {
        val root = Files.createTempDirectory("omnifiles-duplicates-link-root").toFile()
        val outside = Files.createTempDirectory("omnifiles-duplicates-link-outside").toFile()
        try {
            val target = outside.resolve("target.bin").apply { writeText("outside-payload") }
            val link = root.resolve("linked.bin")
            if (runCatching { Files.createSymbolicLink(link.toPath(), target.toPath()) }.isFailure) return

            assertFalse(
                DuplicateFinder.verifyDuplicate(
                    file = link,
                    root = root,
                    expectedSizeBytes = target.length(),
                    expectedModifiedAt = target.lastModified().coerceAtLeast(0L),
                    expectedSha256 = "0".repeat(64),
                )
            )
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }
}
