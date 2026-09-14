package dev.laxerus.omnifiles.fs

import java.util.concurrent.atomic.AtomicLong

object TransferRuntime {
    enum class Operation { COPY, MOVE }
    enum class Phase { ACTIVE, FINISHED }

    data class Snapshot(
        val id: Long,
        val operation: Operation,
        val sourceName: String,
        val copiedBytes: Long,
        val totalBytes: Long,
        val cancelRequested: Boolean,
        val phase: Phase,
        val succeeded: Boolean? = null,
        val cancelled: Boolean = false
    )

    private val ids = AtomicLong(0L)
    private val lock = Any()

    @Volatile private var listener: ((Snapshot) -> Unit)? = null
    @Volatile private var current: Snapshot? = null
    @Volatile private var cancelledId: Long = NO_ID

    fun begin(operation: Operation, sourceName: String): Long {
        val id = ids.incrementAndGet().takeIf { it > 0L } ?: 1L.also { ids.set(it) }
        val snapshot = Snapshot(
            id = id,
            operation = operation,
            sourceName = sourceName.ifBlank { "Adsız öğe" },
            copiedBytes = 0L,
            totalBytes = 0L,
            cancelRequested = false,
            phase = Phase.ACTIVE
        )
        synchronized(lock) {
            cancelledId = NO_ID
            current = snapshot
        }
        dispatch(snapshot)
        return id
    }

    fun update(id: Long, copiedBytes: Long, totalBytes: Long) {
        val snapshot = synchronized(lock) {
            val active = current ?: return
            if (active.id != id || active.phase != Phase.ACTIVE) return
            val total = totalBytes.coerceAtLeast(0L)
            val copied = copiedBytes.coerceAtLeast(0L).let { value ->
                if (total > 0L) value.coerceAtMost(total) else value
            }
            active.copy(
                copiedBytes = copied,
                totalBytes = total,
                cancelRequested = cancelledId == id
            ).also { current = it }
        }
        dispatch(snapshot)
    }

    fun requestCancel(id: Long): Boolean {
        val snapshot = synchronized(lock) {
            val active = current ?: return false
            if (active.id != id || active.phase != Phase.ACTIVE) return false
            cancelledId = id
            active.copy(cancelRequested = true).also { current = it }
        }
        dispatch(snapshot)
        return true
    }

    fun isCancelled(id: Long): Boolean = cancelledId == id

    fun finish(id: Long, succeeded: Boolean, cancelled: Boolean) {
        val snapshot = synchronized(lock) {
            val active = current ?: return
            if (active.id != id) return
            val finished = active.copy(
                copiedBytes = if (succeeded && active.totalBytes > 0L) active.totalBytes else active.copiedBytes,
                cancelRequested = cancelledId == id,
                phase = Phase.FINISHED,
                succeeded = succeeded,
                cancelled = cancelled
            )
            current = null
            if (cancelledId == id) cancelledId = NO_ID
            finished
        }
        dispatch(snapshot)
    }

    fun setListener(next: (Snapshot) -> Unit) {
        listener = next
        current?.let(::dispatch)
    }

    fun clearListener(expected: (Snapshot) -> Unit) {
        if (listener === expected) listener = null
    }

    fun currentSnapshot(): Snapshot? = current

    private fun dispatch(snapshot: Snapshot) {
        val target = listener ?: return
        runCatching { target(snapshot) }
    }

    private const val NO_ID = Long.MIN_VALUE
}
