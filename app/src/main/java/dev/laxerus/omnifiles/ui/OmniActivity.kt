package dev.laxerus.omnifiles.ui

import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.fs.TransferBatchRuntime
import dev.laxerus.omnifiles.fs.TransferRuntime
import java.util.Locale

abstract class OmniActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var transferDialog: AlertDialog? = null
    private var transferDialogId: Long? = null
    private var transferDialogIsBatch = false
    private var transferStatusText: TextView? = null
    private var transferDetailText: TextView? = null
    private var transferProgressIndicator: LinearProgressIndicator? = null
    private var pendingTransferDismiss: Runnable? = null

    private val transferListener: (TransferRuntime.Snapshot) -> Unit = { snapshot ->
        runOnUiThread {
            if (TransferBatchRuntime.currentSnapshot() == null) renderTransferSnapshot(snapshot)
        }
    }

    private val batchTransferListener: (TransferBatchRuntime.Snapshot) -> Unit = { snapshot ->
        runOnUiThread { renderBatchTransferSnapshot(snapshot) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        TransferBatchRuntime.setListener(batchTransferListener)
        TransferRuntime.setListener(transferListener)
    }

    override fun onPause() {
        TransferBatchRuntime.clearListener(batchTransferListener)
        TransferRuntime.clearListener(transferListener)
        cancelPendingTransferDismiss()
        dismissTransferDialog()
        super.onPause()
    }

    protected fun applySystemBarInsets(root: View) {
        val initialLeft = root.paddingLeft
        val initialTop = root.paddingTop
        val initialRight = root.paddingRight
        val initialBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialLeft + bars.left,
                initialTop + bars.top,
                initialRight + bars.right,
                initialBottom + bars.bottom
            )
            insets
        }
    }

    private fun renderTransferSnapshot(snapshot: TransferRuntime.Snapshot) {
        if (isFinishing || isDestroyed) return
        if (snapshot.phase == TransferRuntime.Phase.FINISHED) {
            renderFinishedTransfer(snapshot)
            return
        }

        cancelPendingTransferDismiss()
        ensureTransferDialog(snapshot.id, isBatch = false)

        val status = when {
            snapshot.cancelRequested -> getString(R.string.transfer_progress_canceling)
            snapshot.operation == TransferRuntime.Operation.COPY -> getString(R.string.transfer_progress_copy)
            else -> getString(R.string.transfer_progress_move)
        }
        transferStatusText?.text = status

        val progress = transferProgressIndicator
        if (snapshot.totalBytes > 0L) {
            val fraction = progressFraction(snapshot.copiedBytes, snapshot.totalBytes)
            val percent = progressPercent(fraction)
            transferDetailText?.text = getString(
                R.string.transfer_progress_detail,
                snapshot.sourceName,
                formatTransferBytes(snapshot.copiedBytes),
                formatTransferBytes(snapshot.totalBytes),
                percent
            )
            progress?.apply {
                max = PROGRESS_MAX
                setProgressCompat(fraction, true)
            }
        } else {
            transferDetailText?.text = getString(R.string.transfer_progress_preparing, snapshot.sourceName)
            progress?.apply {
                max = PROGRESS_MAX
                setProgressCompat(0, false)
            }
        }

        transferDialog?.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
            isEnabled = !snapshot.cancelRequested
            setText(R.string.transfer_cancel_current)
        }
    }

    private fun renderBatchTransferSnapshot(snapshot: TransferBatchRuntime.Snapshot) {
        if (isFinishing || isDestroyed) return
        if (snapshot.phase == TransferBatchRuntime.Phase.FINISHED) {
            renderFinishedBatchTransfer(snapshot)
            return
        }

        cancelPendingTransferDismiss()
        ensureTransferDialog(snapshot.id, isBatch = true)

        val status = when {
            snapshot.cancelRequested -> getString(R.string.transfer_progress_canceling)
            snapshot.operation == TransferBatchRuntime.Operation.COPY -> getString(R.string.transfer_progress_copy)
            else -> getString(R.string.transfer_progress_move)
        }
        transferStatusText?.text = status

        val progress = transferProgressIndicator
        if (snapshot.phase == TransferBatchRuntime.Phase.PREPARING) {
            transferDetailText?.text = getString(
                R.string.transfer_batch_preparing,
                snapshot.preparedItems,
                snapshot.itemCount
            )
            progress?.apply {
                max = PROGRESS_MAX
                setProgressCompat(0, false)
            }
        } else if (snapshot.totalBytes > 0L) {
            val fraction = progressFraction(snapshot.copiedBytes, snapshot.totalBytes)
            val percent = progressPercent(fraction)
            transferDetailText?.text = getString(
                R.string.transfer_batch_detail,
                (snapshot.currentIndex + 1).coerceIn(1, snapshot.itemCount),
                snapshot.itemCount,
                snapshot.sourceName,
                formatTransferBytes(snapshot.copiedBytes),
                formatTransferBytes(snapshot.totalBytes),
                percent
            )
            progress?.apply {
                max = PROGRESS_MAX
                setProgressCompat(fraction, true)
            }
        } else {
            transferDetailText?.text = getString(
                R.string.transfer_batch_detail_no_bytes,
                (snapshot.currentIndex + 1).coerceIn(1, snapshot.itemCount),
                snapshot.itemCount,
                snapshot.sourceName
            )
            progress?.apply {
                max = PROGRESS_MAX
                setProgressCompat(0, false)
            }
        }

        transferDialog?.getButton(DialogInterface.BUTTON_NEGATIVE)?.apply {
            isEnabled = !snapshot.cancelRequested
            setText(R.string.transfer_cancel_batch)
        }
    }

    private fun renderFinishedTransfer(snapshot: TransferRuntime.Snapshot) {
        if (transferDialogIsBatch || transferDialogId != snapshot.id || transferDialog?.isShowing != true) return
        val status = when {
            snapshot.cancelled -> getString(R.string.transfer_progress_cancelled)
            snapshot.succeeded == true -> getString(R.string.transfer_progress_done)
            else -> getString(R.string.transfer_progress_failed)
        }
        transferStatusText?.text = status
        if (snapshot.succeeded == true && snapshot.totalBytes > 0L) {
            transferProgressIndicator?.apply {
                max = PROGRESS_MAX
                setProgressCompat(PROGRESS_MAX, true)
            }
            transferDetailText?.text = getString(
                R.string.transfer_progress_detail,
                snapshot.sourceName,
                formatTransferBytes(snapshot.totalBytes),
                formatTransferBytes(snapshot.totalBytes),
                100
            )
        } else if (snapshot.cancelled) {
            transferDetailText?.text = snapshot.sourceName
        }
        transferDialog?.getButton(DialogInterface.BUTTON_NEGATIVE)?.isEnabled = false
        scheduleTransferDismiss(snapshot.id, isBatch = false)
    }

    private fun renderFinishedBatchTransfer(snapshot: TransferBatchRuntime.Snapshot) {
        if (!transferDialogIsBatch || transferDialogId != snapshot.id || transferDialog?.isShowing != true) return
        val status = when {
            snapshot.cancelled -> getString(R.string.transfer_progress_cancelled)
            snapshot.succeeded == true -> getString(R.string.transfer_progress_done)
            else -> getString(R.string.transfer_progress_failed)
        }
        transferStatusText?.text = status

        if (snapshot.succeeded == true && snapshot.totalBytes > 0L) {
            transferProgressIndicator?.apply {
                max = PROGRESS_MAX
                setProgressCompat(PROGRESS_MAX, true)
            }
        }

        val waiting = (snapshot.itemCount - snapshot.processedItems).coerceAtLeast(0)
        transferDetailText?.text = if (snapshot.cancelled) {
            getString(
                R.string.transfer_batch_cancelled_detail,
                snapshot.succeededItems,
                snapshot.itemCount,
                snapshot.failedItems,
                waiting
            )
        } else {
            getString(
                R.string.transfer_batch_done_detail,
                snapshot.succeededItems,
                snapshot.itemCount,
                snapshot.failedItems
            )
        }
        transferDialog?.getButton(DialogInterface.BUTTON_NEGATIVE)?.isEnabled = false
        scheduleTransferDismiss(snapshot.id, isBatch = true)
    }

    private fun ensureTransferDialog(id: Long, isBatch: Boolean) {
        val existing = transferDialog
        if (existing?.isShowing == true) {
            transferDialogId = id
            transferDialogIsBatch = isBatch
            existing.getButton(DialogInterface.BUTTON_NEGATIVE)?.isEnabled = true
            return
        }

        val content = layoutInflater.inflate(R.layout.dialog_transfer_progress, null, false)
        transferStatusText = content.findViewById(R.id.transferStatusText)
        transferDetailText = content.findViewById(R.id.transferDetailText)
        transferProgressIndicator = content.findViewById(R.id.transferProgressIndicator)
        transferDialogId = id
        transferDialogIsBatch = isBatch

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.transfer_progress_title)
            .setView(content)
            .setNegativeButton(if (isBatch) R.string.transfer_cancel_batch else R.string.transfer_cancel_current, null)
            .create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                val activeId = transferDialogId ?: return@setOnClickListener
                val requested = if (transferDialogIsBatch) {
                    TransferBatchRuntime.requestCancel(activeId)
                } else {
                    TransferRuntime.requestCancel(activeId)
                }
                if (requested) {
                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = false
                    transferStatusText?.setText(R.string.transfer_progress_canceling)
                }
            }
        }
        dialog.setOnDismissListener {
            if (transferDialog === dialog) {
                transferDialog = null
                transferDialogId = null
                transferDialogIsBatch = false
                transferStatusText = null
                transferDetailText = null
                transferProgressIndicator = null
            }
        }
        transferDialog = dialog
        dialog.show()
    }

    private fun scheduleTransferDismiss(id: Long, isBatch: Boolean) {
        pendingTransferDismiss = Runnable {
            pendingTransferDismiss = null
            val stillCurrent = if (isBatch) {
                TransferBatchRuntime.currentSnapshot()?.id == id
            } else {
                TransferRuntime.currentSnapshot()?.id == id
            }
            if (transferDialogId == id && transferDialogIsBatch == isBatch && !stillCurrent) {
                dismissTransferDialog()
            }
        }.also { mainHandler.postDelayed(it, FINISH_VISIBILITY_MS) }
    }

    private fun progressFraction(copiedBytes: Long, totalBytes: Long): Int {
        if (totalBytes <= 0L) return 0
        return ((copiedBytes.coerceIn(0L, totalBytes).toDouble() / totalBytes.toDouble()) * PROGRESS_MAX)
            .toInt()
            .coerceIn(0, PROGRESS_MAX)
    }

    private fun progressPercent(fraction: Int): Int =
        ((fraction.toLong() * 100L) / PROGRESS_MAX).toInt()

    private fun cancelPendingTransferDismiss() {
        pendingTransferDismiss?.let(mainHandler::removeCallbacks)
        pendingTransferDismiss = null
    }

    private fun dismissTransferDialog() {
        transferDialog?.dismiss()
        transferDialog = null
        transferDialogId = null
        transferDialogIsBatch = false
        transferStatusText = null
        transferDetailText = null
        transferProgressIndicator = null
    }

    private fun formatTransferBytes(bytes: Long): String {
        val safeBytes = bytes.coerceAtLeast(0L)
        if (safeBytes < 1024L) return "$safeBytes B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB", "EB")
        var value = safeBytes.toDouble()
        var index = -1
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return "%.1f %s".format(Locale.ROOT, value, units[index])
    }

    companion object {
        private const val PROGRESS_MAX = 1_000
        private const val FINISH_VISIBILITY_MS = 650L
    }
}
