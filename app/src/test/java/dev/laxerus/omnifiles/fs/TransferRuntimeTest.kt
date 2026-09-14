package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferRuntimeTest {
    @Test fun publishesProgressCancellationAndFinishedState() {
        finishAnyActiveTransfer()
        val events = mutableListOf<TransferRuntime.Snapshot>()
        val listener: (TransferRuntime.Snapshot) -> Unit = { events += it }
        TransferRuntime.setListener(listener)
        try {
            val id = TransferRuntime.begin(TransferRuntime.Operation.COPY, "world.zip")
            TransferRuntime.update(id, 64L, 256L)

            assertTrue(TransferRuntime.requestCancel(id))
            assertTrue(TransferRuntime.isCancelled(id))

            TransferRuntime.finish(id, succeeded = false, cancelled = true)

            assertNull(TransferRuntime.currentSnapshot())
            assertTrue(events.isNotEmpty())
            assertEquals(TransferRuntime.Phase.ACTIVE, events.first().phase)
            assertEquals(64L, events.first { it.copiedBytes == 64L }.copiedBytes)
            assertTrue(events.any { it.cancelRequested })
            val finished = events.last()
            assertEquals(TransferRuntime.Phase.FINISHED, finished.phase)
            assertTrue(finished.cancelled)
            assertEquals(false, finished.succeeded)
        } finally {
            TransferRuntime.clearListener(listener)
            finishAnyActiveTransfer()
        }
    }

    @Test fun successfulFinishPinsProgressToKnownTotal() {
        finishAnyActiveTransfer()
        val events = mutableListOf<TransferRuntime.Snapshot>()
        val listener: (TransferRuntime.Snapshot) -> Unit = { events += it }
        TransferRuntime.setListener(listener)
        try {
            val id = TransferRuntime.begin(TransferRuntime.Operation.MOVE, "save.dat")
            TransferRuntime.update(id, 25L, 100L)
            assertFalse(TransferRuntime.isCancelled(id))

            TransferRuntime.finish(id, succeeded = true, cancelled = false)

            val finished = events.last()
            assertEquals(TransferRuntime.Phase.FINISHED, finished.phase)
            assertEquals(100L, finished.copiedBytes)
            assertEquals(100L, finished.totalBytes)
            assertEquals(true, finished.succeeded)
            assertFalse(finished.cancelled)
        } finally {
            TransferRuntime.clearListener(listener)
            finishAnyActiveTransfer()
        }
    }

    @Test fun staleCancellationRequestCannotCancelDifferentTransfer() {
        finishAnyActiveTransfer()
        val first = TransferRuntime.begin(TransferRuntime.Operation.COPY, "first.bin")
        TransferRuntime.finish(first, succeeded = true, cancelled = false)
        val second = TransferRuntime.begin(TransferRuntime.Operation.COPY, "second.bin")
        try {
            assertFalse(TransferRuntime.requestCancel(first))
            assertFalse(TransferRuntime.isCancelled(second))
        } finally {
            TransferRuntime.finish(second, succeeded = false, cancelled = false)
        }
    }

    private fun finishAnyActiveTransfer() {
        TransferRuntime.currentSnapshot()?.let { active ->
            TransferRuntime.finish(active.id, succeeded = false, cancelled = false)
        }
    }
}
