package dev.laxerus.omnifiles.ui

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.AccessSnapshot
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.databinding.ActivityStorageAnalyzerBinding
import dev.laxerus.omnifiles.fs.StorageAnalyzer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class StorageAnalyzerActivity : OmniActivity() {
    private lateinit var binding: ActivityStorageAnalyzerBinding
    private val sharedRoot: File by lazy { StorageAccessController.sharedRoot().canonicalFile }
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

        renderEntries(binding.filesContainer, result.largestFiles)
        renderEntries(binding.foldersContainer, result.largestDirectories)
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
            container.addView(row)
        }
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
