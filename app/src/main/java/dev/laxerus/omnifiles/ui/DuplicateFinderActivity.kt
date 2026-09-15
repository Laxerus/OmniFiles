package dev.laxerus.omnifiles.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.AccessSnapshot
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.databinding.ActivityDuplicateFinderBinding
import dev.laxerus.omnifiles.fs.DuplicateFinder
import dev.laxerus.omnifiles.fs.FilePathPolicy
import dev.laxerus.omnifiles.fs.TrashManager
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
    private lateinit var binding: ActivityDuplicateFinderBinding
    private val sharedRoot: File by lazy { StorageAccessController.sharedRoot().canonicalFile }
    private val trashManager: TrashManager by lazy { TrashManager(this) }
    private val cancelRequested = AtomicBoolean(false)
    private var scanJob: Job? = null
    private var hasResult = false

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
        binding.startButton.isEnabled = ready
        if (!ready) {
            binding.summaryText.setText(R.string.duplicate_finder_access_required)
        } else if (!hasResult) {
            binding.summaryText.setText(R.string.duplicate_finder_idle)
        }
    }

    private fun startScan() {
        if (scanJob?.isActive == true) return
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
                    binding.startButton.isEnabled = AccessSnapshot.read(this@DuplicateFinderActivity).sharedStorage
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
                result.hashedFiles,
                result.groups.size,
                formatBytes(result.reclaimableBytes),
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
            .setItems(labels) { _, which -> showFileActions(group.files[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showFileActions(duplicate: DuplicateFinder.DuplicateFile) {
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
                    2 -> confirmMoveToTrash(duplicate)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmMoveToTrash(duplicate: DuplicateFinder.DuplicateFile) {
        val safe = resolveFile(duplicate) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.duplicate_finder_trash_confirm_title)
            .setMessage(getString(R.string.duplicate_finder_trash_confirm, relativePath(safe.canonicalPath)))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.duplicate_finder_action_trash) { _, _ -> moveToTrash(duplicate) }
            .show()
    }

    private fun moveToTrash(duplicate: DuplicateFinder.DuplicateFile) {
        binding.startButton.isEnabled = false
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val safe = resolveFileSilently(duplicate)
                        ?: error("Dosya taramadan sonra değişmiş veya kaldırılmış")
                    trashManager.moveToTrash(safe)
                }
            }
            if (!isActive) return@launch
            result.onSuccess {
                Toast.makeText(this@DuplicateFinderActivity, R.string.duplicate_finder_trashed, Toast.LENGTH_SHORT).show()
                startScan()
            }.onFailure {
                binding.startButton.isEnabled = AccessSnapshot.read(this@DuplicateFinderActivity).sharedStorage
                Toast.makeText(this@DuplicateFinderActivity, R.string.duplicate_finder_trash_failed, Toast.LENGTH_LONG).show()
            }
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
