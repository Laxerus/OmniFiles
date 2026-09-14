package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferBatchRunnerTest {
    @Test fun cancellationStopsBeforeNextItemAndKeepsUnprocessedItems() {
        finishAnyActiveBatch()
        val executed = mutableListOf<String>()
        val result = TransferBatchRunner.run(
            operation = TransferBatchRuntime.Operation.MOVE,
            items = listOf("first", "second", "third"),
            labelOf = { it },
            estimateBytes = { _, _ -> 100L },
            execute = { item, onProgress, isCancelled ->
                executed += item
                when (item) {
                    "first" -> onProgress(100L, 100L)
                    "second" -> {
                        onProgress(25L, 100L)
                        val id = TransferBatchRuntime.currentSnapshot()?.id ?: error("missing batch")
                        assertTrue(TransferBatchRuntime.requestCancel(id))
                        if (isCancelled()) throw TransferCancelledException()
                    }
                    else -> error("third item must never start")
                }
            }
        )

        assertTrue(result.cancelled)
        assertEquals(listOf("first"), result.succeeded)
        assertTrue(result.failed.isEmpty())
        assertEquals(listOf("second", "third"), result.remaining)
        assertEquals(listOf("first", "second"), executed)
        assertNull(TransferBatchRuntime.currentSnapshot())
    }

    @Test fun aggregateProgressCombinesFinishedAndCurrentItemBytes() {
        finishAnyActiveBatch()
        val events = mutableListOf<TransferBatchRuntime.Snapshot>()
        val listener: (TransferBatchRuntime.Snapshot) -> Unit = { events += it }
        TransferBatchRuntime.setListener(listener)
        try {
            val result = TransferBatchRunner.run(
                operation = TransferBatchRuntime.Operation.COPY,
                items = listOf("a", "b"),
                labelOf = { it },
                estimateBytes = { item, _ -> if (item == "a") 100L else 300L },
                execute = { item, onProgress, _ ->
                    if (item == "a") {
                        onProgress(50L, 100L)
                        onProgress(100L, 100L)
                    } else {
                        onProgress(150L, 300L)
                        onProgress(300L, 300L)
                    }
                }
            )

            assertFalse(result.cancelled)
            assertTrue(result.failed.isEmpty())
            assertEquals(listOf("a", "b"), result.succeeded)
            assertTrue(events.any { it.sourceName == "a" && it.copiedBytes == 50L && it.totalBytes == 400L })
            assertTrue(events.any { it.sourceName == "b" && it.copiedBytes == 250L && it.totalBytes == 400L })
            val finished = events.last()
            assertEquals(TransferBatchRuntime.Phase.FINISHED, finished.phase)
            assertEquals(400L, finished.copiedBytes)
            assertEquals(2, finished.succeededItems)
            assertEquals(0, finished.failedItems)
            assertEquals(true, finished.succeeded)
        } finally {
            TransferBatchRuntime.clearListener(listener)
            finishAnyActiveBatch()
        }
    }

    @Test fun failedItemDoesNotStopFollowingItems() {
        finishAnyActiveBatch()
        val executed = mutableListOf<String>()
        val result = TransferBatchRunner.run(
            operation = TransferBatchRuntime.Operation.COPY,
            items = listOf("bad", "good"),
            labelOf = { it },
            estimateBytes = { _, _ -> 10L },
            execute = { item, _, _ ->
                executed += item
                if (item == "bad") error("boom")
            }
        )

        assertFalse(result.cancelled)
        assertEquals(listOf("good"), result.succeeded)
        assertEquals(listOf("bad"), result.failed)
        assertTrue(result.remaining.isEmpty())
        assertEquals(listOf("bad", "good"), executed)
    }

    @Test fun staleCancelRequestCannotCancelNewBatch() {
        finishAnyActiveBatch()
        val first = TransferBatchRuntime.begin(TransferBatchRuntime.Operation.COPY, 1)
        TransferBatchRuntime.finish(first, succeeded = true, cancelled = false)
        val second = TransferBatchRuntime.begin(TransferBatchRuntime.Operation.COPY, 1)
        try {
            assertFalse(TransferBatchRuntime.requestCancel(first))
            assertFalse(TransferBatchRuntime.isCancelled(second))
        } finally {
            TransferBatchRuntime.finish(second, succeeded = false, cancelled = false)
        }
    }

    private fun finishAnyActiveBatch() {
        TransferBatchRuntime.currentSnapshot()?.let { active ->
            TransferBatchRuntime.finish(active.id, succeeded = false, cancelled = false)
        }
    }
}
