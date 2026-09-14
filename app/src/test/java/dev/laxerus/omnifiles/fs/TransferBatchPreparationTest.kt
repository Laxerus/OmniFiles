package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferBatchPreparationTest {
    @Test fun preparationPublishesMonotonicDiscoveredBytes() {
        finishAnyActiveBatch()
        val events = mutableListOf<TransferBatchRuntime.Snapshot>()
        val listener: (TransferBatchRuntime.Snapshot) -> Unit = { events += it }
        TransferBatchRuntime.setListener(listener)
        try {
            val result = TransferBatchRunner.run(
                operation = TransferBatchRuntime.Operation.COPY,
                items = listOf(10L, 20L, 30L),
                labelOf = { "$it.bin" },
                estimateBytes = { bytes, _ -> bytes },
                execute = { _, onProgress, _ -> onProgress(1L, 1L) }
            )

            assertFalse(result.cancelled)
            val preparing = events.filter { it.phase == TransferBatchRuntime.Phase.PREPARING }
            assertTrue(preparing.any { it.preparedItems == 1 && it.preparedBytes == 10L })
            assertTrue(preparing.any { it.preparedItems == 2 && it.preparedBytes == 30L })
            assertTrue(preparing.any { it.preparedItems == 3 && it.preparedBytes == 60L })
            assertTrue(preparing.zipWithNext().all { (left, right) -> right.preparedBytes >= left.preparedBytes })
            assertEquals(60L, events.last().totalBytes)
        } finally {
            TransferBatchRuntime.clearListener(listener)
            finishAnyActiveBatch()
        }
    }

    @Test fun preparationByteCounterSaturatesInsteadOfOverflowing() {
        finishAnyActiveBatch()
        val events = mutableListOf<TransferBatchRuntime.Snapshot>()
        val listener: (TransferBatchRuntime.Snapshot) -> Unit = { events += it }
        TransferBatchRuntime.setListener(listener)
        try {
            TransferBatchRunner.run(
                operation = TransferBatchRuntime.Operation.COPY,
                items = listOf(Long.MAX_VALUE - 5L, 100L),
                labelOf = { "item" },
                estimateBytes = { bytes, _ -> bytes },
                execute = { _, _, _ -> }
            )

            assertTrue(events.any {
                it.phase == TransferBatchRuntime.Phase.PREPARING &&
                    it.preparedItems == 2 &&
                    it.preparedBytes == Long.MAX_VALUE
            })
            assertEquals(Long.MAX_VALUE, events.last().totalBytes)
        } finally {
            TransferBatchRuntime.clearListener(listener)
            finishAnyActiveBatch()
        }
    }

    private fun finishAnyActiveBatch() {
        TransferBatchRuntime.currentSnapshot()?.let { active ->
            TransferBatchRuntime.finish(active.id, succeeded = false, cancelled = false)
        }
    }
}
