package dev.laxerus.omnifiles.fs

import java.util.concurrent.atomic.AtomicLong

object TransferBatchRuntime {
    enum class Operation { COPY, MOVE }
    enum class Phase { PREPARING, ACTIVE, FINISHED }

    data class Snapshot(
        val id: Long,
        val operation: Operation,
        val itemCount: Int,
        val preparedItems: Int,
        val preparedBytes: Long,
        val processedItems: Int,
        val succeededItems: Int,
        val failedItems: Int,
        val currentIndex: Int,
        val sourceName: String,
        val settledBytes: Long,
        val currentBytes: Long,
        val totalBytes: Long,
        val cancelRequested: Boolean,
        val phase: Phase,
        val succeeded: Boolean? = null,
        val cancelled: Boolean = false
    ) {
        val copiedBytes: Long
            get() {
                val sum = if (Long.MAX_VALUE - settledBytes < currentBytes) Long.MAX_VALUE
                else settledBytes + currentBytes
                return if (totalBytes > 0L) sum.coerceAtMost(totalBytes) else sum
            }
    }

    private val ids = AtomicLong(0L)
    private val lock = Any()

    @Volatile private var listener: ((Snapshot) -> Unit)? = null
    @Volatile private var current: Snapshot? = null
    @Volatile private var cancelledId: Long = NO_ID

    fun begin(operation: Operation, itemCount: Int): Long {
        require(itemCount > 0) { "Batch transfer requires at least one item" }
        val id = ids.incrementAndGet().takeIf { it > 0L } ?: 1L.also { ids.set(it) }
        val snapshot = Snapshot(
            id = id,
            operation = operation,
            itemCount = itemCount,
            preparedItems = 0,
            preparedBytes = 0L,
            processedItems = 0,
            succeededItems = 0,
            failedItems = 0,
            currentIndex = -1,
            sourceName = "",
            settledBytes = 0L,
            currentBytes = 0L,
            totalBytes = 0L,
            cancelRequested = false,
            phase = Phase.PREPARING
        )
        synchronized(lock) {
            cancelledId = NO_ID
            current = snapshot
        }
        dispatch(snapshot)
        return id
    }

    fun reportPreparation(id: Long, preparedItems: Int, preparedBytes: Long) {
        val snapshot = synchronized(lock) {
            val active = current ?: return
            if (active.id != id || active.phase != Phase.PREPARING) return
            active.copy(
                preparedItems = preparedItems.coerceIn(active.preparedItems, active.itemCount),
                preparedBytes = preparedBytes.coerceAtLeast(active.preparedBytes),
                cancelRequested = cancelledId == id
            ).also { current = it }
        }
        dispatch(snapshot)
    }

    fun setTotalBytes(id: Long, totalBytes: Long) {
        val snapshot = synchronized(lock) {
            val active = current ?: return
            if (active.id != id || active.phase == Phase.FINISHED) return
            active.copy(
                totalBytes = totalBytes.coerceAtLeast(0L),
                cancelRequested = cancelledId == id
            ).also { current = it }
        }
        dispatch(snapshot)
    }

    fun startItem(id: Long, index: Int, sourceName: String) {
        val snapshot = synchronized(lock) {
            val active = current ?: return
            if (active.id != id || active.phase == Phase.FINISHED) return
            require(index in 0 until active.itemCount) { "Batch item index is out of range" }
            active.copy(
                currentIndex = index,
                sourceName = sourceName.ifBlank { "Adsız öğe" },
                currentBytes = 0L,
                cancelRequested = cancelledId == id,
                phase = Phase.ACTIVE
            ).also { current = it }
        }
        dispatch(snapshot)
    }

    fun updateCurrent(id: Long, currentBytes: Long) {
        val snapshot = synchronized(lock) {
            val active = current ?: return
            if (active.id != id || active.phase != Phase.ACTIVE) return
            val remaining = if (active.totalBytes > 0L) {
                (active.totalBytes - active.settledBytes).coerceAtLeast(0L)
            } else {
                Long.MAX_VALUE
            }
            active.copy(
                currentBytes = currentBytes.coerceAtLeast(0L).coerceAtMost(remaining),
                cancelRequested = cancelledId == id
            ).also { current = it }
        }
        dispatch(snapshot)
    }

    fun finishItem(id: Long, itemBytes: Long, succeeded: Boolean) {
        val snapshot = synchronized(lock) {
            val active = current ?: return
            if (active.id != id || active.phase != Phase.ACTIVE) return
            val safeItemBytes = itemBytes.coerceAtLeast(0L)
            val settled = saturatingAdd(active.settledBytes, safeItemBytes).let { value ->
                if (active.totalBytes > 0L) value.coerceAtMost(active.totalBytes) else value
            }
            active.copy(
                processedItems = (active.processedItems + 1).coerceAtMost(active.itemCount),
                succeededItems = active.succeededItems + if (succeeded) 1 else 0,
                failedItems = active.failedItems + if (succeeded) 0 else 1,
                settledBytes = settled,
                currentBytes = 0L,
                cancelRequested = cancelledId == id
            ).also { current = it }
        }
        dispatch(snapshot)
    }

    fun requestCancel(id: Long): Boolean {
        val snapshot = synchronized(lock) {
            val active = current ?: return false
            if (active.id != id || active.phase == Phase.FINISHED) return false
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
                settledBytes = if (succeeded && active.totalBytes > 0L) active.totalBytes else active.settledBytes,
                currentBytes = if (succeeded) 0L else active.currentBytes,
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

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    private const val NO_ID = Long.MIN_VALUE
}
