package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashRecoveryPolicyTest {
    private val now = 1_000_000L
    private val stale = now - TrashMetadataPolicy.ORPHAN_GRACE_MS
    private val fresh = now - TrashMetadataPolicy.ORPHAN_GRACE_MS + 1

    @Test
    fun keepsFreshPendingTransaction() {
        assertEquals(
            TrashRecoveryAction.KEEP,
            TrashRecoveryPolicy.decide(
                metadataModifiedAt = fresh,
                now = now,
                trashExists = false,
                originalExists = true,
            )
        )
    }

    @Test
    fun deletesStaleMetadataWhenMoveNeverCommitted() {
        assertEquals(
            TrashRecoveryAction.DELETE_METADATA,
            TrashRecoveryPolicy.decide(
                metadataModifiedAt = stale,
                now = now,
                trashExists = false,
                originalExists = true,
            )
        )
    }

    @Test
    fun keepsMetadataWhenTrashEntryExists() {
        assertEquals(
            TrashRecoveryAction.KEEP,
            TrashRecoveryPolicy.decide(
                metadataModifiedAt = stale,
                now = now,
                trashExists = true,
                originalExists = true,
            )
        )
    }

    @Test
    fun keepsMetadataWhenOriginalIsMissing() {
        assertEquals(
            TrashRecoveryAction.KEEP,
            TrashRecoveryPolicy.decide(
                metadataModifiedAt = stale,
                now = now,
                trashExists = false,
                originalExists = false,
            )
        )
    }
}
