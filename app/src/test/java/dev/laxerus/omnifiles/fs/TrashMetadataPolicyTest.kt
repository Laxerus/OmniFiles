package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashMetadataPolicyTest {
    @Test
    fun keepsLiveMetadataEvenWhenOld() {
        val now = 1_000_000L
        assertFalse(
            TrashMetadataPolicy.shouldDeleteOrphan(
                name = "entry.json",
                modifiedAt = now - TrashMetadataPolicy.ORPHAN_GRACE_MS * 2,
                liveMetadataNames = setOf("entry.json"),
                now = now,
            )
        )
    }

    @Test
    fun keepsFreshOrphanMetadata() {
        val now = 1_000_000L
        assertFalse(
            TrashMetadataPolicy.shouldDeleteOrphan(
                name = "entry.json",
                modifiedAt = now - TrashMetadataPolicy.ORPHAN_GRACE_MS + 1,
                liveMetadataNames = emptySet(),
                now = now,
            )
        )
    }

    @Test
    fun deletesStaleOrphanJson() {
        val now = 1_000_000L
        assertTrue(
            TrashMetadataPolicy.shouldDeleteOrphan(
                name = "entry.json",
                modifiedAt = now - TrashMetadataPolicy.ORPHAN_GRACE_MS,
                liveMetadataNames = emptySet(),
                now = now,
            )
        )
    }

    @Test
    fun deletesStaleTempFile() {
        val now = 1_000_000L
        assertTrue(
            TrashMetadataPolicy.shouldDeleteOrphan(
                name = "entry.json.random.tmp",
                modifiedAt = now - TrashMetadataPolicy.ORPHAN_GRACE_MS - 1,
                liveMetadataNames = emptySet(),
                now = now,
            )
        )
    }

    @Test
    fun ignoresUnknownMetadataFilesConservatively() {
        val now = 1_000_000L
        assertFalse(
            TrashMetadataPolicy.shouldDeleteOrphan(
                name = "notes.txt",
                modifiedAt = 1L,
                liveMetadataNames = emptySet(),
                now = now,
            )
        )
    }
}
