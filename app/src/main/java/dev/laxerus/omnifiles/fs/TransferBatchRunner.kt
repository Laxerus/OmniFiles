package dev.laxerus.omnifiles.fs

object TransferBatchRunner {
    data class Result<T>(
        val succeeded: List<T>,
        val failed: List<T>,
        val remaining: List<T>,
        val cancelled: Boolean
    )

    fun <T> run(
        operation: TransferBatchRuntime.Operation,
        items: List<T>,
        labelOf: (T) -> String,
        estimateBytes: (T, () -> Boolean) -> Long,
        execute: (T, (Long, Long) -> Unit, () -> Boolean) -> Unit
    ): Result<T> {
        if (items.isEmpty()) return Result(emptyList(), emptyList(), emptyList(), cancelled = false)

        val runtimeId = TransferBatchRuntime.begin(operation, items.size)
        val cancellation = { TransferBatchRuntime.isCancelled(runtimeId) }
        val estimates = LongArray(items.size)
        val succeeded = mutableListOf<T>()
        val failed = mutableListOf<T>()
        val remaining = mutableListOf<T>()
        var cancelled = false
        var totalBytes = 0L

        try {
            for (index in items.indices) {
                checkCancelled(cancellation)
                estimates[index] = try {
                    estimateBytes(items[index], cancellation).coerceAtLeast(0L)
                } catch (error: TransferCancelledException) {
                    throw error
                } catch (_: Throwable) {
                    0L
                }
                totalBytes = saturatingAdd(totalBytes, estimates[index])
                TransferBatchRuntime.reportPreparation(runtimeId, index + 1)
            }
            TransferBatchRuntime.setTotalBytes(runtimeId, totalBytes)

            var index = 0
            while (index < items.size) {
                if (cancellation()) {
                    cancelled = true
                    remaining += items.drop(index)
                    break
                }

                val item = items[index]
                val itemBytes = estimates[index]
                TransferBatchRuntime.startItem(runtimeId, index, labelOf(item))
                try {
                    execute(
                        item,
                        { copied, reportedTotal ->
                            TransferBatchRuntime.updateCurrent(
                                runtimeId,
                                scaleProgress(copied, reportedTotal, itemBytes)
                            )
                        },
                        cancellation
                    )
                    TransferBatchRuntime.finishItem(runtimeId, itemBytes, succeeded = true)
                    succeeded += item
                    index++
                } catch (error: TransferCancelledException) {
                    cancelled = true
                    remaining += items.drop(index)
                    break
                } catch (_: Throwable) {
                    TransferBatchRuntime.finishItem(runtimeId, itemBytes, succeeded = false)
                    failed += item
                    index++
                }
            }

            if (!cancelled && cancellation()) {
                cancelled = true
                val processed = succeeded.size + failed.size
                if (processed < items.size) remaining += items.drop(processed)
            }

            return Result(
                succeeded = succeeded.toList(),
                failed = failed.toList(),
                remaining = remaining.toList(),
                cancelled = cancelled
            )
        } catch (_: TransferCancelledException) {
            cancelled = true
            remaining += items
            return Result(
                succeeded = emptyList(),
                failed = emptyList(),
                remaining = remaining.toList(),
                cancelled = true
            )
        } finally {
            TransferBatchRuntime.finish(
                runtimeId,
                succeeded = !cancelled && failed.isEmpty() && succeeded.size == items.size,
                cancelled = cancelled
            )
        }
    }

    private fun scaleProgress(copied: Long, reportedTotal: Long, itemBytes: Long): Long {
        if (itemBytes <= 0L || copied <= 0L || reportedTotal <= 0L) return 0L
        if (copied >= reportedTotal) return itemBytes
        val ratio = copied.toDouble() / reportedTotal.toDouble()
        return (ratio * itemBytes.toDouble()).toLong().coerceIn(0L, itemBytes)
    }

    private fun checkCancelled(isCancelled: () -> Boolean) {
        if (isCancelled()) throw TransferCancelledException()
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
}
