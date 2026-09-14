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
import dev.laxerus.omnifiles.fs.TransferRuntime
import java.util.Locale

abstract class OmniActivity : AppCompatActivity() {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var transferDialog: AlertDialog? = null
    private var transferDialogId: Long? = null
    private var transferStatusText: TextView? = null
    private var transferDetailText: TextView? = null
    private var transferProgressIndicator: LinearProgressIndicator? = null
    private var pendingTransferDismiss: Runnable? = null

    private val transferListener: (TransferRuntime.Snapshot) -> Unit = { snapshot ->
        runOnUiThread { renderTransferSnapshot(snapshot) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        TransferRuntime.setListener(transferListener)
    }

    override fun onPause() {
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
        ensureTransferDialog(snapshot)
        transferDialogId = snapshot.id

        val status = when {
            snapshot.cancelRequested -> getString(R.string.transfer_progress_canceling)
            snapshot.operation == TransferRuntime.Operation.COPY -> getString(R.string.transfer_progress_copy)
            else -> getString(R.string.transfer_progress_move)
        }
        transferStatusText?.text = status

        val progress = transferProgressIndicator
        if (snapshot.totalBytes > 0L) {
            val fraction = ((snapshot.copiedBytes.coerceAtMost(snapshot.totalBytes).toDouble() /
                snapshot.totalBytes.toDouble()) * PROGRESS_MAX)
                .toInt()
                .coerceIn(0, PROGRESS_MAX)
            val percent = ((fraction.toLong() * 100L) / PROGRESS_MAX).toInt()
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

    private fun renderFinishedTransfer(snapshot: TransferRuntime.Snapshot) {
        if (transferDialogId != snapshot.id || transferDialog?.isShowing != true) return
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

        val finishedId = snapshot.id
        pendingTransferDismiss = Runnable {
            pendingTransferDismiss = null
            if (transferDialogId == finishedId && TransferRuntime.currentSnapshot()?.id != finishedId) {
                dismissTransferDialog()
            }
        }.also { mainHandler.postDelayed(it, FINISH_VISIBILITY_MS) }
    }

    private fun ensureTransferDialog(snapshot: TransferRuntime.Snapshot) {
        val existing = transferDialog
        if (existing?.isShowing == true) {
            transferDialogId = snapshot.id
            existing.getButton(DialogInterface.BUTTON_NEGATIVE)?.isEnabled = true
            return
        }

        val content = layoutInflater.inflate(R.layout.dialog_transfer_progress, null, false)
        transferStatusText = content.findViewById(R.id.transferStatusText)
        transferDetailText = content.findViewById(R.id.transferDetailText)
        transferProgressIndicator = content.findViewById(R.id.transferProgressIndicator)
        transferDialogId = snapshot.id

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.transfer_progress_title)
            .setView(content)
            .setNegativeButton(R.string.transfer_cancel_current, null)
            .create()
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener {
            dialog.getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener {
                val activeId = transferDialogId ?: return@setOnClickListener
                if (TransferRuntime.requestCancel(activeId)) {
                    dialog.getButton(DialogInterface.BUTTON_NEGATIVE).isEnabled = false
                    transferStatusText?.setText(R.string.transfer_progress_canceling)
                }
            }
        }
        dialog.setOnDismissListener {
            if (transferDialog === dialog) {
                transferDialog = null
                transferDialogId = null
                transferStatusText = null
                transferDetailText = null
                transferProgressIndicator = null
            }
        }
        transferDialog = dialog
        dialog.show()
    }

    private fun cancelPendingTransferDismiss() {
        pendingTransferDismiss?.let(mainHandler::removeCallbacks)
        pendingTransferDismiss = null
    }

    private fun dismissTransferDialog() {
        transferDialog?.dismiss()
        transferDialog = null
        transferDialogId = null
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
