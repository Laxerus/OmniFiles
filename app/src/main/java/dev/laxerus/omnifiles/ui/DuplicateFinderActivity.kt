package dev.laxerus.omnifiles.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.AccessSnapshot
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.databinding.ActivityDuplicateFinderBinding
import dev.laxerus.omnifiles.fs.DuplicateFinder
import dev.laxerus.omnifiles.fs.FilePathPolicy
import dev.laxerus.omnifiles.fs.TrashManager
import dev.laxerus.omnifiles.fs.TrashTicket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class DuplicateFinderActivity : OmniActivity() {
    private data class CleanupOutcome(
        val tickets: List<TrashTicket>,
        val failed: Int,
        val keeperInvalidated: Boolean,
    ) {
        val moved: Int get() = tickets.size
    }

    private enum class ManualTrashCheck {
        READY,
        ENTRY_STALE,
        CONTENT_CHANGED,
        NO_VERIFIED_PEER,
    }

    private sealed interface ManualTrashOutcome {
        data class Moved(val ticket: TrashTicket) : ManualTrashOutcome
        data class Rejected(val check: ManualTrashCheck) : ManualTrashOutcome
        data object Failed : ManualTrashOutcome
    }

    private lateinit var binding: ActivityDuplicateFinderBinding
    private val sharedRoot: File by lazy { StorageAccessController.sharedRoot().canonicalFile }
    private val trashManager: TrashManager by lazy { TrashManager(this) }
    private val cancelRequested = AtomicBoolean(false)
    private var scanJob: Job? = null
    private var hasResult = false
    private var manualTrashUndoPending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDuplicateFinderBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.startButton.setOnClickListener { startScan() }
        binding.cancelButton.setOnClickListener {
            cancelRequested.set(true)
            binding.cancelButton.isEnabled = false
            binding.summaryText.setText(R.string.duplicate_finder_cancelling)
        }
        renderAccessState()
    }

    override fun onResume() {
        super.onResume()
        if (scanJob?.isActive != true) renderAccessState()
    }

    override fun onDestroy() {
        cancelRequested.set(true)
        super.onDestroy()
    }

    private fun renderAccessState() {
        val ready = AccessSnapshot.read(this).sharedStorage
        binding.startButton.isEnabled = ready && !manualTrashUndoPending
        if (manualTrashUndoPending) {
            binding.cancelButton.isEnabled = false
            binding.summaryText.setText(R.string.duplicate_finder_trashed)
            return
        }
        if (!ready) {
            binding.summaryText.setText(R.string.duplicate_finder_access_required)
        } else if (!hasResult) {
            binding.summaryText.setText(R.string.duplicate_finder_idle)
        }
    }

    private fun startScan() {
        if (manualTrashUndoPending || scanJob?.isActive == true) return
        if (!AccessSnapshot.read(this).sharedStorage) {
            renderAccessState()
            return
        }

        cancelRequested.set(false)
        binding.startButton.isEnabled = false
        binding.cancelButton.isEnabled = true
        binding.progress.visibility = View.VISIBLE
        binding.summaryText.setText(R.string.duplicate_finder_scanning)
        binding.groupsContainer.removeAllViews()

        scanJob = lifecycleScope.launch {
            val ownerJob = coroutineContext[Job]
            try {
                val result = withContext(Dispatchers.IO) {
                    DuplicateFinder.scan(
                        root = sharedRoot,
                        isCancelled = { cancelRequested.get() || ownerJob?.isActive == false },
                    )
                }
                if (!isActive) return@launch
                hasResult = true
                renderResult(result)
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Throwable) {
                if (isActive) binding.summaryText.setText(R.string.duplicate_finder_failed)
            } finally {
                if (isActive) {
                    binding.progress.visibility = View.GONE
                    binding.cancelButton.isEnabled = false
                    binding.startButton.isEnabled = AccessSnapshot.read(this@DuplicateFinderActivity).sharedStorage &&
                        !manualTrashUndoPending
                    binding.startButton.setText(
                        if (hasResult) R.string.duplicate_finder_rescan else R.string.duplicate_finder_start
                    )
                }
            }
        }
    }

    private fun renderResult(result: DuplicateFinder.Result) {
        val summary = mutableListOf(
            getString(
                R.string.duplicate_finder_summary,
                result.fileCount,
                result.groups.size,
                formatBytes(result.reclaimableBytes),
            ),
            getString(
                R.string.duplicate_finder_pipeline_summary,
                result.scannedEntries,
                result.candidateFiles,
                result.fingerprintedFiles,
                result.hashedFiles,
                formatBytes(result.hashedBytes),
            )
        )
        if (result.skippedEntries > 0) {
            summary += getString(R.string.duplicate_finder_skipped, result.skippedEntries)
        }
        when {
            result.cancelled -> summary += getString(R.string.duplicate_finder_partial_cancelled)
            result.truncated -> summary += getString(R.string.duplicate_finder_partial_limit)
        }
        binding.summaryText.text = summary.joinToString("\n")
        renderGroups(result.groups)
    }

    private fun renderGroups(groups: List<DuplicateFinder.DuplicateGroup>) {
        val container = binding.groupsContainer
        container.removeAllViews()
        if (groups.isEmpty()) {
            container.addView(TextView(this).apply {
                setText(R.string.duplicate_finder_none)
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }

        groups.forEach { group ->
            val row = layoutInflater.inflate(R.layout.item_storage_analysis, container, false)
            row.findViewById<TextView>(R.id.analysisName).text = getString(
                R.string.duplicate_finder_group_title,
                group.files.size,
                formatBytes(group.sizeBytes),
            )
            row.findViewById<TextView>(R.id.analysisMeta).text = getString(
                R.string.duplicate_finder_group_meta,
                formatBytes(group.reclaimableBytes),
                group.sha256.take(12),
            )
            row.isClickable = true
            row.isFocusable = true
            row.setOnClickListener { showGroup(group) }
            row.setOnLongClickListener {
                copyGroupPaths(group)
                true
            }
            container.addView(row)
        }
    }

    private fun showGroup(group: DuplicateFinder.DuplicateGroup) {
        val labels = group.files.map { duplicate -> relativePath(duplicate.path) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.duplicate_finder_group_dialog, group.files.size))
            .setItems(labels) { _, which -> showFileActions(group, group.files[which]) }
            .setNeutralButton(R.string.duplicate_finder_clean_group) { _, _ -> confirmCleanGroup(group) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun chooseKeeper(group: DuplicateFinder.DuplicateGroup): DuplicateFinder.DuplicateFile =
        group.files.sortedWith(
            compareByDescending<DuplicateFinder.DuplicateFile> { it.modifiedAt }
                .thenBy { it.path.length }
                .thenBy { it.path.lowercase(Locale.ROOT) }
        ).first()

    private fun confirmCleanGroup(group: DuplicateFinder.DuplicateGroup) {
        if (manualTrashUndoPending || group.files.size < 2 || scanJob?.isActive == true) return
        val keeper = chooseKeeper(group)
        binding.startButton.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val keeperValid = withContext(Dispatchers.IO) {
                verifyDuplicate(keeper, group.sha256)
            }
            if (!isActive) return@launch
            binding.progress.visibility = View.GONE
            binding.startButton.isEnabled = AccessSnapshot.read(this@DuplicateFinderActivity).sharedStorage &&
                !manualTrashUndoPending
            if (!keeperValid) {
                Toast.makeText(this@DuplicateFinderActivity, R.string.duplicate_finder_keeper_stale, Toast.LENGTH_LONG).show()
                return@launch
            }
            MaterialAlertDialogBuilder(this@DuplicateFinderActivity)
                .setTitle(R.string.duplicate_finder_clean_group_title)
                .setMessage(
                    getString(
                        R.string.duplicate_finder_clean_group_message,
                        group.files.size - 1,
                        relativePath(keeper.path),
                        formatBytes(group.reclaimableBytes),
                    )
                )
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.duplicate_finder_clean_group_confirm) { _, _ -> cleanGroup(group, keeper) }
                .show()
        }
    }

    private fun cleanGroup(
        group: DuplicateFinder.DuplicateGroup,
        keeper: DuplicateFinder.DuplicateFile,
    ) {
        if (manualTrashUndoPending || scanJob?.isActive == true) return
        val targets = group.files.filterNot { it.path == keeper.path }
        if (targets.isEmpty()) return

        binding.startButton.isEnabled = false
        binding.cancelButton.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        binding.groupsContainer.alpha = 0.55f
        binding.summaryText.setText(R.string.duplicate_finder_group_cleaning)

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val tickets = mutableListOf<TrashTicket>()
                var failed = 0
                var keeperInvalidated = false

                for (index in targets.indices) {
                    if (!verifyDuplicate(keeper, group.sha256)) {
                        keeperInvalidated = true
                        failed += targets.size - index
                        break
                    }

                    val duplicate = targets[index]
                    val safe = resolveFileSilently(duplicate)
                    if (safe == null || !verifyDuplicate(duplicate, group.sha256)) {
                        failed++
                        continue
                    }

                    runCatching { trashManager.moveToTrash(safe) }
                        .onSuccess { tickets += it }
                        .onFailure { failed++ }
                }
                CleanupOutcome(tickets.toList(), failed, keeperInvalidated)
            }
            if (!isActive) return@launch

            binding.progress.visibility = View.GONE
            binding.groupsContainer.alpha = 1f
            when {
                outcome.tickets.isNotEmpty() -> showGroupTrashUndo(outcome)

                outcome.keeperInvalidated -> {
                    Toast.makeText(
                        this@DuplicateFinderActivity,
                        R.string.duplicate_finder_keeper_changed_during_cleanup,
                        Toast.LENGTH_LONG,
                    ).show()
                    hasResult = false
                    startScan()
                }

                else -> {
                    Toast.makeText(
                        this@DuplicateFinderActivity,
                        getString(R.string.duplicate_finder_clean_group_result, outcome.moved, outcome.failed),
                        Toast.LENGTH_LONG,
                    ).show()
                    hasResult = false
                    startScan()
                }
            }
        }
    }

    private fun showGroupTrashUndo(outcome: CleanupOutcome) {
        manualTrashUndoPending = true
        binding.startButton.isEnabled = false
        binding.cancelButton.isEnabled = false
        val message = getString(R.string.duplicate_finder_clean_group_result, outcome.moved, outcome.failed)
        binding.summaryText.text = message
        if (outcome.keeperInvalidated) {
            Toast.makeText(
                this,
                R.string.duplicate_finder_keeper_changed_during_cleanup,
                Toast.LENGTH_LONG,
            ).show()
        }

        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { restoreTrashTickets(outcome.tickets) }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event == Snackbar.Callback.DISMISS_EVENT_ACTION) return
                    manualTrashUndoPending = false
                    refreshAfterManualTrash()
                }
            })
            .show()
    }

    private fun showFileActions(
        group: DuplicateFinder.DuplicateGroup,
        duplicate: DuplicateFinder.DuplicateFile,
    ) {
        val safe = resolveFile(duplicate) ?: return
        val actions = arrayOf(
            getString(R.string.duplicate_finder_action_open),
            getString(R.string.duplicate_finder_action_copy_path),
            getString(R.string.duplicate_finder_action_trash),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.duplicate_finder_file_actions_title)
            .setMessage(relativePath(safe.canonicalPath))
            .setItems(actions) { _, which ->
                when (which) {
                    0 -> openLocation(duplicate)
                    1 -> copyPath(safe.canonicalPath)
                    2 -> confirmMoveToTrash(group, duplicate)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmMoveToTrash(
        group: DuplicateFinder.DuplicateGroup,
        duplicate: DuplicateFinder.DuplicateFile,
    ) {
        if (manualTrashUndoPending || scanJob?.isActive == true) return
        binding.startButton.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val check = withContext(Dispatchers.IO) {
                verifyManualTrashState(group, duplicate)
            }
            if (!isActive) return@launch
            binding.progress.visibility = View.GONE
            binding.startButton.isEnabled = AccessSnapshot.read(this@DuplicateFinderActivity).sharedStorage &&
                !manualTrashUndoPending
            if (check != ManualTrashCheck.READY) {
                showManualTrashCheckFailure(check)
                return@launch
            }
            val safe = resolveFile(duplicate) ?: return@launch
            MaterialAlertDialogBuilder(this@DuplicateFinderActivity)
                .setTitle(R.string.duplicate_finder_trash_confirm_title)
                .setMessage(getString(R.string.duplicate_finder_trash_confirm, relativePath(safe.canonicalPath)))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.duplicate_finder_action_trash) { _, _ -> moveToTrash(group, duplicate) }
                .show()
        }
    }

    private fun moveToTrash(
        group: DuplicateFinder.DuplicateGroup,
        duplicate: DuplicateFinder.DuplicateFile,
    ) {
        if (manualTrashUndoPending || scanJob?.isActive == true) return
        binding.startButton.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val check = verifyManualTrashState(group, duplicate)
                if (check != ManualTrashCheck.READY) {
                    return@withContext ManualTrashOutcome.Rejected(check)
                }
                val safe = resolveFileSilently(duplicate)
                    ?: return@withContext ManualTrashOutcome.Rejected(ManualTrashCheck.ENTRY_STALE)
                runCatching { trashManager.moveToTrash(safe) }
                    .fold(
                        onSuccess = { ticket -> ManualTrashOutcome.Moved(ticket) },
                        onFailure = { ManualTrashOutcome.Failed },
                    )
            }
            if (!isActive) return@launch
            binding.progress.visibility = View.GONE
            when (outcome) {
                is ManualTrashOutcome.Moved -> showTrashUndo(outcome.ticket)
                is ManualTrashOutcome.Rejected -> {
                    binding.startButton.isEnabled = AccessSnapshot.read(this@DuplicateFinderActivity).sharedStorage
                    showManualTrashCheckFailure(outcome.check)
                }
                ManualTrashOutcome.Failed -> {
                    binding.startButton.isEnabled = AccessSnapshot.read(this@DuplicateFinderActivity).sharedStorage
                    Toast.makeText(
                        this@DuplicateFinderActivity,
                        R.string.duplicate_finder_trash_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun verifyManualTrashState(
        group: DuplicateFinder.DuplicateGroup,
        duplicate: DuplicateFinder.DuplicateFile,
    ): ManualTrashCheck {
        if (resolveFileSilently(duplicate) == null) return ManualTrashCheck.ENTRY_STALE
        if (!verifyDuplicate(duplicate, group.sha256)) return ManualTrashCheck.CONTENT_CHANGED

        val hasVerifiedPeer = group.files.asSequence()
            .filterNot { peer -> peer.path == duplicate.path }
            .any { peer ->
                resolveFileSilently(peer) != null && verifyDuplicate(peer, group.sha256)
            }
        if (!hasVerifiedPeer) return ManualTrashCheck.NO_VERIFIED_PEER

        if (resolveFileSilently(duplicate) == null) return ManualTrashCheck.ENTRY_STALE
        if (!verifyDuplicate(duplicate, group.sha256)) return ManualTrashCheck.CONTENT_CHANGED
        return ManualTrashCheck.READY
    }

    private fun showManualTrashCheckFailure(check: ManualTrashCheck) {
        val message = when (check) {
            ManualTrashCheck.ENTRY_STALE -> R.string.duplicate_finder_entry_stale
            ManualTrashCheck.CONTENT_CHANGED -> R.string.duplicate_finder_entry_content_changed
            ManualTrashCheck.NO_VERIFIED_PEER -> R.string.duplicate_finder_no_verified_peer
            ManualTrashCheck.READY -> return
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showTrashUndo(ticket: TrashTicket) {
        manualTrashUndoPending = true
        binding.startButton.isEnabled = false
        binding.cancelButton.isEnabled = false
        binding.summaryText.setText(R.string.duplicate_finder_trashed)

        Snackbar.make(binding.root, R.string.duplicate_finder_trashed, Snackbar.LENGTH_LONG)
            .setAction(R.string.undo) { restoreTrash(ticket) }
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    if (event == Snackbar.Callback.DISMISS_EVENT_ACTION) return
                    manualTrashUndoPending = false
                    refreshAfterManualTrash()
                }
            })
            .show()
    }

    private fun restoreTrash(ticket: TrashTicket) {
        binding.startButton.isEnabled = false
        binding.cancelButton.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { trashManager.restore(ticket) }
            }
            if (!isActive) return@launch
            manualTrashUndoPending = false
            result.onSuccess {
                Snackbar.make(binding.root, R.string.trash_restored, Snackbar.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@DuplicateFinderActivity, R.string.trash_restore_failed, Toast.LENGTH_LONG).show()
            }
            refreshAfterManualTrash()
        }
    }

    private fun restoreTrashTickets(tickets: List<TrashTicket>) {
        if (tickets.isEmpty()) return
        binding.startButton.isEnabled = false
        binding.cancelButton.isEnabled = false
        lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) {
                var restored = 0
                var failed = 0
                tickets.forEach { ticket ->
                    runCatching { trashManager.restore(ticket) }
                        .onSuccess { restored++ }
                        .onFailure { failed++ }
                }
                restored to failed
            }
            if (!isActive) return@launch
            manualTrashUndoPending = false
            val message = if (counts.second == 0) {
                getString(R.string.batch_restore_done, counts.first)
            } else {
                getString(R.string.batch_restore_partial, counts.first, counts.second)
            }
            Toast.makeText(
                this@DuplicateFinderActivity,
                message,
                if (counts.second == 0) Toast.LENGTH_SHORT else Toast.LENGTH_LONG,
            ).show()
            refreshAfterManualTrash()
        }
    }

    private fun refreshAfterManualTrash() {
        hasResult = false
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) && !isFinishing && !isDestroyed) {
            startScan()
        } else {
            renderAccessState()
        }
    }

    private fun openLocation(duplicate: DuplicateFinder.DuplicateFile) {
        val safe = resolveFile(duplicate) ?: return
        val parent = safe.parentFile ?: sharedRoot
        val safeParent = runCatching { FilePathPolicy.requireDirectEntry(parent, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, R.string.duplicate_finder_entry_stale, Toast.LENGTH_LONG).show()
                return
            }
        val intent = Intent(this, FileBrowserActivity::class.java)
            .putExtra(FileBrowserActivity.EXTRA_START_PATH, safeParent.canonicalPath)
            .putExtra(BrowserLaunchExtras.EXTRA_HIGHLIGHT_PATH, safe.canonicalPath)
        startActivity(intent)
    }

    private fun resolveFile(duplicate: DuplicateFinder.DuplicateFile): File? {
        val safe = resolveFileSilently(duplicate)
        if (safe == null) {
            Toast.makeText(this, R.string.duplicate_finder_entry_stale, Toast.LENGTH_LONG).show()
        }
        return safe
    }

    private fun resolveFileSilently(duplicate: DuplicateFinder.DuplicateFile): File? {
        val safe = runCatching { FilePathPolicy.requireDirectEntry(File(duplicate.path), sharedRoot) }.getOrNull()
            ?: return null
        val unchanged = safe.exists() && safe.isFile &&
            safe.length() == duplicate.sizeBytes &&
            safe.lastModified().coerceAtLeast(0L) == duplicate.modifiedAt
        return safe.takeIf { unchanged }
    }

    private fun verifyDuplicate(duplicate: DuplicateFinder.DuplicateFile, expectedSha256: String): Boolean =
        DuplicateFinder.verifyDuplicate(
            file = File(duplicate.path),
            root = sharedRoot,
            expectedSizeBytes = duplicate.sizeBytes,
            expectedModifiedAt = duplicate.modifiedAt,
            expectedSha256 = expectedSha256,
        )

    private fun copyPath(path: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OmniFiles path", path))
        Toast.makeText(this, R.string.duplicate_finder_path_copied, Toast.LENGTH_SHORT).show()
    }

    private fun copyGroupPaths(group: DuplicateFinder.DuplicateGroup) {
        val validPaths = group.files.mapNotNull { duplicate ->
            resolveFileSilently(duplicate)?.canonicalPath
        }
        if (validPaths.isEmpty()) {
            Toast.makeText(this, R.string.duplicate_finder_entry_stale, Toast.LENGTH_SHORT).show()
            return
        }
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OmniFiles duplicate paths", validPaths.joinToString("\n")))
        Toast.makeText(this, R.string.duplicate_finder_paths_copied, Toast.LENGTH_SHORT).show()
    }

    private fun relativePath(path: String): String {
        val rootPath = sharedRoot.path
        return if (path.startsWith(rootPath + File.separator)) path.substring(rootPath.length + 1) else path
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB")
        var value = bytes.toDouble()
        var index = -1
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return "%.1f %s".format(Locale.ROOT, value, units[index])
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
