package dev.laxerus.omnifiles.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.AccessSnapshot
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.databinding.ActivityStorageAnalyzerBinding
import dev.laxerus.omnifiles.fs.FilePathPolicy
import dev.laxerus.omnifiles.fs.StorageAnalyzer
import dev.laxerus.omnifiles.fs.TrashManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class StorageAnalyzerActivity : OmniActivity() {
    private data class TrashOutcome(
        val moved: Boolean,
        val stale: Boolean,
    )

    private lateinit var binding: ActivityStorageAnalyzerBinding
    private val sharedRoot: File by lazy { StorageAccessController.sharedRoot().canonicalFile }
    private val trashManager: TrashManager by lazy { TrashManager(this) }
    private val cancelRequested = AtomicBoolean(false)
    private var scanJob: Job? = null
    private var hasResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStorageAnalyzerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.startButton.setOnClickListener { startScan() }
        binding.cancelButton.setOnClickListener {
            cancelRequested.set(true)
            binding.cancelButton.isEnabled = false
            binding.summaryText.setText(R.string.storage_analyzer_cancelling)
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
            binding.summaryText.setText(R.string.storage_analyzer_access_required)
        } else if (!hasResult) {
            binding.summaryText.setText(R.string.storage_analyzer_idle)
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
        binding.summaryText.setText(R.string.storage_analyzer_scanning)
        binding.categoriesContainer.removeAllViews()
        binding.filesContainer.removeAllViews()
        binding.foldersContainer.removeAllViews()

        scanJob = lifecycleScope.launch {
            val ownerJob = coroutineContext[Job]
            try {
                val result = withContext(Dispatchers.IO) {
                    StorageAnalyzer.scan(
                        root = sharedRoot,
                        isCancelled = { cancelRequested.get() || ownerJob?.isActive == false }
                    )
                }
                if (!isActive) return@launch
                hasResult = true
                renderResult(result)
            } catch (_: CancellationException) {
                return@launch
            } catch (_: Throwable) {
                if (isActive) binding.summaryText.setText(R.string.storage_analyzer_failed)
            } finally {
                if (isActive) {
                    binding.progress.visibility = View.GONE
                    binding.cancelButton.isEnabled = false
                    binding.startButton.isEnabled = AccessSnapshot.read(this@StorageAnalyzerActivity).sharedStorage
                    binding.startButton.setText(if (hasResult) R.string.storage_analyzer_rescan else R.string.storage_analyzer_start)
                }
            }
        }
    }

    private fun renderResult(result: StorageAnalyzer.Result) {
        val summary = mutableListOf(
            getString(
                R.string.storage_analyzer_summary,
                result.visitedEntries,
                result.fileCount,
                result.directoryCount,
                formatBytes(result.scannedBytes)
            )
        )
        if (result.skippedEntries > 0) {
            summary += getString(R.string.storage_analyzer_skipped, result.skippedEntries)
        }
        when {
            result.cancelled -> summary += getString(R.string.storage_analyzer_partial_cancelled)
            result.truncated -> summary += getString(R.string.storage_analyzer_partial_limit)
        }
        binding.summaryText.text = summary.joinToString("\n")

        renderCategories(result.categories, result.scannedBytes)
        renderEntries(binding.filesContainer, result.largestFiles)
        renderEntries(binding.foldersContainer, result.largestDirectories)
    }

    private fun renderCategories(categories: List<StorageAnalyzer.CategoryUsage>, totalBytes: Long) {
        val container = binding.categoriesContainer
        container.removeAllViews()
        if (categories.isEmpty()) {
            container.addView(TextView(this).apply {
                setText(R.string.storage_analyzer_none)
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }

        categories.forEach { usage ->
            val row = layoutInflater.inflate(R.layout.item_storage_category, container, false)
            val ratio = if (totalBytes > 0L) {
                (usage.sizeBytes.toDouble() / totalBytes.toDouble()).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val percentage = String.format(Locale.getDefault(), "%.1f%%", ratio * 100.0)
            val label = getString(categoryLabel(usage.category))
            row.findViewById<TextView>(R.id.categoryName).text = label
            row.findViewById<TextView>(R.id.categoryMeta).text = getString(
                R.string.storage_analyzer_category_meta,
                usage.fileCount,
                formatBytes(usage.sizeBytes),
                percentage
            )
            row.findViewById<TextView>(R.id.categoryHint).text = getString(
                R.string.storage_analyzer_category_hint,
                usage.largestFiles.size
            )
            row.findViewById<LinearProgressIndicator>(R.id.categoryProgress).apply {
                max = 1000
                progress = (ratio * 1000.0).toInt().coerceIn(0, 1000)
            }
            row.contentDescription = getString(
                R.string.storage_analyzer_category_action_hint,
                label,
                usage.fileCount,
                formatBytes(usage.sizeBytes)
            )
            row.setOnClickListener { showCategoryFiles(usage) }
            container.addView(row)
        }
    }

    private fun showCategoryFiles(usage: StorageAnalyzer.CategoryUsage) {
        val entries = usage.largestFiles
        if (entries.isEmpty()) return
        val labels = entries.map { entry ->
            getString(
                R.string.storage_analyzer_category_file_item,
                entry.name,
                formatBytes(entry.sizeBytes),
                relativePath(entry.path)
            )
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(
                getString(
                    R.string.storage_analyzer_category_top_title,
                    getString(categoryLabel(usage.category)),
                    entries.size
                )
            )
            .setItems(labels) { _, which -> showEntryActions(entries[which]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun categoryLabel(category: StorageAnalyzer.FileCategory): Int = when (category) {
        StorageAnalyzer.FileCategory.IMAGE -> R.string.storage_analyzer_category_image
        StorageAnalyzer.FileCategory.VIDEO -> R.string.storage_analyzer_category_video
        StorageAnalyzer.FileCategory.AUDIO -> R.string.storage_analyzer_category_audio
        StorageAnalyzer.FileCategory.APK -> R.string.storage_analyzer_category_apk
        StorageAnalyzer.FileCategory.ARCHIVE -> R.string.storage_analyzer_category_archive
        StorageAnalyzer.FileCategory.DOCUMENT -> R.string.storage_analyzer_category_document
        StorageAnalyzer.FileCategory.DATABASE -> R.string.storage_analyzer_category_database
        StorageAnalyzer.FileCategory.OTHER -> R.string.storage_analyzer_category_other
    }

    private fun renderEntries(container: LinearLayout, entries: List<StorageAnalyzer.Entry>) {
        container.removeAllViews()
        if (entries.isEmpty()) {
            container.addView(TextView(this).apply {
                setText(R.string.storage_analyzer_none)
                setPadding(0, dp(8), 0, dp(8))
            })
            return
        }

        entries.forEach { entry ->
            val row = layoutInflater.inflate(R.layout.item_storage_analysis, container, false)
            row.findViewById<TextView>(R.id.analysisName).text = entry.name
            row.findViewById<TextView>(R.id.analysisMeta).text = getString(
                R.string.storage_analyzer_entry_meta,
                formatBytes(entry.sizeBytes),
                relativePath(entry.path)
            )
            row.isClickable = true
            row.isFocusable = true
            row.contentDescription = getString(
                R.string.storage_analyzer_entry_action_hint,
                entry.name,
                formatBytes(entry.sizeBytes)
            )
            row.setOnClickListener { showEntryActions(entry) }
            row.setOnLongClickListener {
                copyEntryPath(entry)
                true
            }
            container.addView(row)
        }
    }

    private fun showEntryActions(entry: StorageAnalyzer.Entry) {
        val safe = resolveEntry(entry) ?: return
        val actions = buildList {
            add(R.string.details)
            if (safe.isDirectory) {
                add(R.string.storage_analyzer_open_folder)
            } else {
                add(R.string.storage_analyzer_show_in_folder)
                add(R.string.storage_analyzer_open_file)
                add(R.string.share)
                add(R.string.storage_analyzer_sha256)
                add(R.string.storage_analyzer_move_to_trash)
            }
            add(R.string.copy_path)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(safe.name.ifBlank { relativePath(safe.path) })
            .setItems(actions.map(::getString).toTypedArray()) { _, which ->
                when (actions[which]) {
                    R.string.details -> showEntryDetails(safe, entry)
                    R.string.storage_analyzer_open_folder -> openInBrowser(safe)
                    R.string.storage_analyzer_show_in_folder -> openInBrowser(safe)
                    R.string.storage_analyzer_open_file -> openFile(safe)
                    R.string.share -> shareFile(safe)
                    R.string.storage_analyzer_sha256 -> openChecksum(safe)
                    R.string.storage_analyzer_move_to_trash -> confirmMoveToTrash(entry)
                    R.string.copy_path -> copyEntryPath(entry)
                }
            }
            .show()
    }

    private fun confirmMoveToTrash(entry: StorageAnalyzer.Entry) {
        val safe = StorageAnalyzer.verifyUnchangedFile(entry, sharedRoot)
        if (safe == null) {
            Toast.makeText(this, R.string.storage_analyzer_entry_stale, Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.storage_analyzer_trash_confirm_title)
            .setMessage(
                getString(
                    R.string.storage_analyzer_trash_confirm,
                    safe.name,
                    formatBytes(entry.sizeBytes),
                )
            )
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.storage_analyzer_move_to_trash) { _, _ ->
                moveToTrash(entry)
            }
            .show()
    }

    private fun moveToTrash(entry: StorageAnalyzer.Entry) {
        if (scanJob?.isActive == true) return
        binding.startButton.isEnabled = false
        binding.cancelButton.isEnabled = false
        binding.progress.visibility = View.VISIBLE
        binding.summaryText.setText(R.string.storage_analyzer_trash_moving)

        lifecycleScope.launch {
            val outcome = withContext(Dispatchers.IO) {
                val safe = StorageAnalyzer.verifyUnchangedFile(entry, sharedRoot)
                    ?: return@withContext TrashOutcome(moved = false, stale = true)
                runCatching { trashManager.moveToTrash(safe) }
                    .fold(
                        onSuccess = { TrashOutcome(moved = true, stale = false) },
                        onFailure = { TrashOutcome(moved = false, stale = false) },
                    )
            }
            if (!isActive) return@launch

            binding.progress.visibility = View.GONE
            when {
                outcome.moved -> {
                    Toast.makeText(
                        this@StorageAnalyzerActivity,
                        R.string.storage_analyzer_trashed,
                        Toast.LENGTH_SHORT,
                    ).show()
                    hasResult = false
                    startScan()
                }

                outcome.stale -> {
                    Toast.makeText(
                        this@StorageAnalyzerActivity,
                        R.string.storage_analyzer_entry_stale,
                        Toast.LENGTH_LONG,
                    ).show()
                    hasResult = false
                    startScan()
                }

                else -> {
                    binding.startButton.isEnabled = AccessSnapshot.read(this@StorageAnalyzerActivity).sharedStorage
                    binding.cancelButton.isEnabled = false
                    Toast.makeText(
                        this@StorageAnalyzerActivity,
                        R.string.storage_analyzer_trash_failed,
                        Toast.LENGTH_LONG,
                    ).show()
                    renderAccessState()
                }
            }
        }
    }

    private fun resolveEntry(entry: StorageAnalyzer.Entry): File? {
        val safe = runCatching { FilePathPolicy.requireDirectEntry(File(entry.path), sharedRoot) }
            .getOrElse {
                Toast.makeText(this, R.string.storage_analyzer_entry_stale, Toast.LENGTH_LONG).show()
                return null
            }
        if (!safe.exists() || safe.isDirectory != entry.isDirectory) {
            Toast.makeText(this, R.string.storage_analyzer_entry_stale, Toast.LENGTH_LONG).show()
            return null
        }
        return safe
    }

    private fun openInBrowser(file: File) {
        val target = if (file.isDirectory) file else file.parentFile ?: sharedRoot
        val safeTarget = runCatching { FilePathPolicy.requireDirectEntry(target, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, R.string.storage_analyzer_entry_stale, Toast.LENGTH_LONG).show()
                return
            }
        if (!safeTarget.exists() || !safeTarget.isDirectory) {
            Toast.makeText(this, R.string.storage_analyzer_entry_stale, Toast.LENGTH_LONG).show()
            return
        }
        val intent = Intent(this, FileBrowserActivity::class.java)
            .putExtra(FileBrowserActivity.EXTRA_START_PATH, safeTarget.canonicalPath)
        if (file.isFile) {
            intent.putExtra(BrowserLaunchExtras.EXTRA_HIGHLIGHT_PATH, file.canonicalPath)
        }
        startActivity(intent)
    }

    private fun showEntryDetails(file: File, entry: StorageAnalyzer.Entry) {
        val modified = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(file.lastModified().coerceAtLeast(entry.modifiedAt)))
        val type = if (file.isDirectory) {
            getString(R.string.storage_analyzer_type_folder)
        } else {
            LocalFileIntents.mimeFor(file)
        }
        val message = listOf(
            getString(R.string.detail_type, type),
            getString(R.string.detail_size, formatBytes(entry.sizeBytes)),
            getString(R.string.detail_modified, modified),
            getString(R.string.detail_path, file.path)
        ).joinToString("\n")
        MaterialAlertDialogBuilder(this)
            .setTitle(file.name.ifBlank { file.path })
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun openFile(file: File) {
        val intent = runCatching { LocalFileIntents.viewIntent(this, file) }
            .getOrElse {
                Toast.makeText(this, R.string.storage_analyzer_entry_stale, Toast.LENGTH_LONG).show()
                return
            }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.storage_analyzer_no_viewer, Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(file: File) {
        val intent = runCatching { LocalFileIntents.shareIntent(this, file) }
            .getOrElse {
                Toast.makeText(this, R.string.storage_analyzer_entry_stale, Toast.LENGTH_LONG).show()
                return
            }
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.share))) }
            .onFailure {
                Toast.makeText(this, R.string.storage_analyzer_share_failed, Toast.LENGTH_SHORT).show()
            }
    }

    private fun openChecksum(file: File) {
        val intent = runCatching { LocalFileIntents.checksumIntent(this, file) }
            .getOrElse {
                Toast.makeText(this, R.string.storage_analyzer_entry_stale, Toast.LENGTH_LONG).show()
                return
            }
        runCatching { startActivity(intent) }
            .onFailure {
                Toast.makeText(this, R.string.storage_analyzer_entry_stale, Toast.LENGTH_SHORT).show()
            }
    }

    private fun copyEntryPath(entry: StorageAnalyzer.Entry) {
        val safe = resolveEntry(entry) ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OmniFiles path", safe.path))
        Toast.makeText(this, R.string.path_copied, Toast.LENGTH_SHORT).show()
    }

    private fun relativePath(path: String): String {
        val rootPath = sharedRoot.path
        return if (path.startsWith(rootPath + File.separator)) {
            path.substring(rootPath.length + 1)
        } else {
            path
        }
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
