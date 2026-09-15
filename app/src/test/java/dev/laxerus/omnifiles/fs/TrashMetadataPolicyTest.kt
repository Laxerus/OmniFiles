package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashMetadataPolicyTest {
    @Test
    fun freshMetadataIsInsideGrace() {
        val now = 1_000_000L
        assertFalse(
            TrashMetadataPolicy.isPastGrace(
                modifiedAt = now - TrashMetadataPolicy.ORPHAN_GRACE_MS + 1,
                now = now,
            )
        )
    }

    @Test
    fun staleMetadataIsPastGrace() {
        val now = 1_000_000L
        assertTrue(
            TrashMetadataPolicy.isPastGrace(
                modifiedAt = now - TrashMetadataPolicy.ORPHAN_GRACE_MS,
                now = now,
            )
        )
    }

    @Test
    fun futureTimestampIsKeptConservatively() {
        val now = 1_000_000L
        assertFalse(TrashMetadataPolicy.isPastGrace(modifiedAt = now + 1, now = now))
    }

    @Test
    fun deletesOnlyStaleTempFiles() {
        val now = 1_000_000L
        assertTrue(
            TrashMetadataPolicy.shouldDeleteStaleTemp(
                name = "entry.json.random.tmp",
                modifiedAt = now - TrashMetadataPolicy.ORPHAN_GRACE_MS,
                now = now,
            )
        )
        assertFalse(
            TrashMetadataPolicy.shouldDeleteStaleTemp(
                name = "entry.json",
                modifiedAt = now - TrashMetadataPolicy.ORPHAN_GRACE_MS * 2,
                now = now,
            )
        )
        assertFalse(
            TrashMetadataPolicy.shouldDeleteStaleTemp(
                name = "entry.json.random.tmp",
                modifiedAt = now - TrashMetadataPolicy.ORPHAN_GRACE_MS + 1,
                now = now,
            )
        )
    }

    @Test
    fun ignoresUnknownFilesConservatively() {
        val now = 1_000_000L
        assertFalse(
            TrashMetadataPolicy.shouldDeleteStaleTemp(
                name = "notes.txt",
                modifiedAt = 1L,
                now = now,
            )
        )
    }
}
