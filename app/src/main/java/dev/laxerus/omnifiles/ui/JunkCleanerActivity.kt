package dev.laxerus.omnifiles.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.databinding.ActivityJunkCleanerBinding
import dev.laxerus.omnifiles.fs.JunkCandidate
import dev.laxerus.omnifiles.fs.JunkCleaner
import dev.laxerus.omnifiles.fs.JunkKind
import dev.laxerus.omnifiles.fs.JunkScanResult
import dev.laxerus.omnifiles.maintenance.AutoCleanupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class JunkCleanerActivity : OmniActivity() {
    private lateinit var binding: ActivityJunkCleanerBinding
    private var currentScan: JunkScanResult? = null
    private var busy = false
    private var suppressAutoToggle = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityJunkCleanerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { if (!busy) finish() }
        binding.scanButton.setOnClickListener { scan() }
        binding.cleanButton.setOnClickListener { confirmClean() }
        binding.openTrashButton.setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }
        binding.autoCleanupSwitch.setOnCheckedChangeListener { _, checked ->
            if (!suppressAutoToggle) handleAutoCleanupToggle(checked)
        }
        renderIdle()
        renderAutoCleanupStatus()
    }

    override fun onResume() {
        super.onResume()
        if (!StorageAccessController.hasSharedStorageAccess(this)) {
            currentScan = null
            renderIdle()
        }
        renderAutoCleanupStatus()
    }

    private fun handleAutoCleanupToggle(enabled: Boolean) {
        if (enabled && !StorageAccessController.hasSharedStorageAccess(this)) {
            AutoCleanupManager.setEnabled(this, false)
            setAutoSwitchChecked(false)
            renderAutoCleanupStatus()
            Toast.makeText(this, R.string.junk_cleaner_auto_access_required, Toast.LENGTH_LONG).show()
            StorageAccessController.requestSharedStorageAccess(this)
            return
        }

        AutoCleanupManager.setEnabled(this, enabled)
        if (!enabled) {
            Toast.makeText(this, R.string.junk_cleaner_auto_disabled_toast, Toast.LENGTH_SHORT).show()
            renderAutoCleanupStatus()
            return
        }

        Toast.makeText(this, R.string.junk_cleaner_auto_enabled, Toast.LENGTH_SHORT).show()
        runAutomaticCleanupNow()
    }

    private fun runAutomaticCleanupNow() {
        if (busy) {
            renderAutoCleanupStatus()
            return
        }
        setBusy(true, R.string.junk_cleaner_auto_running)
        binding.autoCleanupSwitch.isEnabled = false
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    AutoCleanupManager.runIfDue(
                        context = this@JunkCleanerActivity,
                        force = true,
                    )
                }
            }
            if (!isFinishing && !isDestroyed) {
                setBusy(false)
                binding.autoCleanupSwitch.isEnabled = true
                currentScan = null
                renderIdle()
                renderAutoCleanupStatus()
                result.onFailure {
                    Toast.makeText(
                        this@JunkCleanerActivity,
                        R.string.junk_cleaner_auto_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun renderAutoCleanupStatus() {
        val state = AutoCleanupManager.state(this)
        setAutoSwitchChecked(state.enabled)
        binding.autoCleanupSwitch.isEnabled = !busy
        binding.autoCleanupStatus.text = when {
            !state.enabled -> getString(R.string.junk_cleaner_auto_disabled)
            !StorageAccessController.hasSharedStorageAccess(this) ->
                getString(R.string.junk_cleaner_auto_access_required)
            state.lastSuccessAt <= 0L -> getString(R.string.junk_cleaner_auto_waiting)
            else -> buildString {
                append(
                    getString(
                        R.string.junk_cleaner_auto_last_run,
                        formatTimestamp(state.lastSuccessAt),
                        state.lastDeleted,
                        state.lastFailed,
                        formatBytes(state.lastReclaimedBytes),
                    )
                )
                if (state.lastScanTruncated) {
                    append("\n")
                    append(getString(R.string.junk_cleaner_auto_partial))
                }
            }
        }
    }

    private fun setAutoSwitchChecked(checked: Boolean) {
        suppressAutoToggle = true
        binding.autoCleanupSwitch.isChecked = checked
        suppressAutoToggle = false
    }

    private fun scan() {
        if (busy) return
        if (!StorageAccessController.hasSharedStorageAccess(this)) {
            Toast.makeText(this, R.string.junk_cleaner_storage_required, Toast.LENGTH_LONG).show()
            StorageAccessController.requestSharedStorageAccess(this)
            return
        }

        setBusy(true, R.string.junk_cleaner_scanning)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    JunkCleaner.scan(StorageAccessController.sharedRoot())
                }
            }
            setBusy(false)
            renderAutoCleanupStatus()
            result.onSuccess {
                currentScan = it
                renderScan(it)
            }.onFailure {
                currentScan = null
                binding.summaryText.text = it.message ?: getString(R.string.junk_cleaner_scan_failed)
                binding.detailsText.setText(R.string.junk_cleaner_scan_failed_hint)
                binding.cleanButton.isEnabled = false
            }
        }
    }

    private fun confirmClean() {
        val scan = currentScan ?: return
        if (busy || scan.candidates.isEmpty()) return

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.junk_cleaner_confirm_title)
            .setMessage(
                getString(
                    R.string.junk_cleaner_confirm_message,
                    scan.candidates.size,
                    formatBytes(scan.totalBytes),
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.junk_cleaner_clean_now) { _, _ -> clean(scan.candidates) }
            .show()
    }

    private fun clean(candidates: List<JunkCandidate>) {
        if (busy) return
        setBusy(true, R.string.junk_cleaner_cleaning)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    JunkCleaner.clean(
                        sharedRoot = StorageAccessController.sharedRoot(),
                        candidates = candidates,
                    )
                }
            }
            setBusy(false)
            renderAutoCleanupStatus()
            result.onSuccess { cleanup ->
                Toast.makeText(
                    this@JunkCleanerActivity,
                    getString(
                        R.string.junk_cleaner_done,
                        cleanup.deleted,
                        cleanup.failed,
                        formatBytes(cleanup.reclaimedBytes),
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                currentScan = null
                scan()
            }.onFailure {
                Toast.makeText(
                    this@JunkCleanerActivity,
                    it.message ?: getString(R.string.junk_cleaner_clean_failed),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    private fun renderIdle() {
        binding.summaryText.setText(R.string.junk_cleaner_idle)
        binding.detailsText.setText(R.string.junk_cleaner_idle_hint)
        binding.cleanButton.isEnabled = false
        binding.progress.visibility = View.GONE
    }

    private fun renderScan(result: JunkScanResult) {
        binding.summaryText.text = getString(
            R.string.junk_cleaner_scan_summary,
            result.candidates.size,
            formatBytes(result.totalBytes),
            result.scannedEntries,
        )

        if (result.candidates.isEmpty()) {
            binding.detailsText.setText(R.string.junk_cleaner_clean_state)
            binding.cleanButton.isEnabled = false
            return
        }

        val counts = result.candidates.groupingBy { it.kind }.eachCount()
        val categoryLines = JunkKind.entries.mapNotNull { kind ->
            val count = counts[kind] ?: return@mapNotNull null
            "• ${kindLabel(kind)}: $count"
        }
        val root = runCatching { StorageAccessController.sharedRoot().canonicalFile }.getOrNull()
        val preview = result.candidates
            .sortedWith(compareByDescending<JunkCandidate> { it.bytes }.thenBy { it.path })
            .take(10)
            .map { candidate ->
                val path = root?.let { relativePath(candidate.path, it) } ?: candidate.path
                "  $path"
            }

        val limitNote = if (result.truncated) {
            "\n\n${getString(R.string.junk_cleaner_scan_limited)}"
        } else {
            ""
        }
        binding.detailsText.text = buildString {
            append(categoryLines.joinToString("\n"))
            append("\n\n")
            append(getString(R.string.junk_cleaner_preview_title))
            append("\n")
            append(preview.joinToString("\n"))
            if (result.candidates.size > preview.size) {
                append("\n")
                append(getString(R.string.junk_cleaner_more_items, result.candidates.size - preview.size))
            }
            append(limitNote)
        }
        binding.cleanButton.isEnabled = !busy
    }

    private fun setBusy(value: Boolean, statusRes: Int? = null) {
        busy = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        binding.scanButton.isEnabled = !value
        binding.openTrashButton.isEnabled = !value
        binding.autoCleanupSwitch.isEnabled = !value
        binding.cleanButton.isEnabled = !value && currentScan?.candidates?.isNotEmpty() == true
        if (statusRes != null) binding.summaryText.setText(statusRes)
    }

    private fun kindLabel(kind: JunkKind): String = getString(
        when (kind) {
            JunkKind.TEMP_FILE -> R.string.junk_kind_temp
            JunkKind.PARTIAL_DOWNLOAD -> R.string.junk_kind_partial
            JunkKind.METADATA -> R.string.junk_kind_metadata
            JunkKind.EMPTY_CACHE_DIRECTORY -> R.string.junk_kind_empty_cache
            JunkKind.EMPTY_LOG -> R.string.junk_kind_empty_log
        }
    )

    private fun relativePath(path: String, root: File): String {
        return path.removePrefix(root.path).trimStart(File.separatorChar).ifBlank { root.path }
    }

    private fun formatTimestamp(timestamp: Long): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
            .format(Date(timestamp))

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = -1
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return "%.1f %s".format(Locale.ROOT, value, units[index])
    }
}
