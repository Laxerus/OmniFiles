package dev.laxerus.omnifiles.ui

import android.content.Intent
import android.os.Bundle
import android.os.StatFs
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import dev.laxerus.omnifiles.BuildConfig
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.AccessSnapshot
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.access.SystemSettingsNavigator
import dev.laxerus.omnifiles.adb.AdbSessionManager
import dev.laxerus.omnifiles.databinding.ActivityMainBinding
import dev.laxerus.omnifiles.fs.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : OmniActivity() {
    private lateinit var binding: ActivityMainBinding
    private var trashCountGeneration = 0
    private var adbHealthGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.versionText.text = BuildConfig.VERSION_NAME

        binding.storageAccessCard.setOnClickListener {
            openStorageAccessSettings()
        }
        binding.developerSettingsCard.setOnClickListener {
            openSystemSettings(SystemSettingsNavigator.Destination.DEVELOPER_OPTIONS)
        }
        binding.wifiSettingsCard.setOnClickListener {
            openSystemSettings(SystemSettingsNavigator.Destination.WIFI)
        }
        binding.appDetailsCard.setOnClickListener {
            openSystemSettings(SystemSettingsNavigator.Destination.APP_DETAILS)
        }

        binding.storageAnalyzerCard.setOnClickListener {
            if (StorageAccessController.hasSharedStorageAccess(this)) {
                startActivity(Intent(this, StorageAnalyzerActivity::class.java))
            } else {
                Toast.makeText(this, R.string.settings_requires_storage, Toast.LENGTH_SHORT).show()
                openStorageAccessSettings()
            }
        }
        binding.trashCard.setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }
        binding.checksumCard.setOnClickListener {
            startActivity(Intent(this, ChecksumActivity::class.java))
        }
        binding.adbCard.setOnClickListener {
            startActivity(Intent(this, AdbPairingActivity::class.java))
        }
        binding.adbFilesCard.setOnClickListener {
            openAdbTool(AdbBrowserActivity::class.java)
        }
        binding.saveScoutCard.setOnClickListener {
            openAdbTool(SaveScoutActivity::class.java)
        }
        binding.sqliteStudioCard.setOnClickListener {
            startActivity(Intent(this, SqliteStudioActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderStorageUsage()
        renderTrashCount()
    }

    private fun openStorageAccessSettings() {
        if (!StorageAccessController.requestSharedStorageAccess(this)) {
            Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openSystemSettings(destination: SystemSettingsNavigator.Destination) {
        if (!SystemSettingsNavigator.open(this, destination)) {
            Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun openAdbTool(target: Class<*>) {
        if (AccessSnapshot.read(this).adbEndpointConfigured) {
            startActivity(Intent(this, target))
        } else {
            Toast.makeText(this, R.string.settings_requires_adb, Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, AdbPairingActivity::class.java))
        }
    }

    private fun renderStatus() {
        val state = AccessSnapshot.read(this)

        binding.storageStatusChip.setText(
            if (state.sharedStorage) R.string.settings_status_storage_ready
            else R.string.settings_status_storage_required
        )
        binding.rootStatusChip.setText(
            if (state.rootBinaryPresent) R.string.settings_status_root_detected
            else R.string.settings_status_root_unknown
        )
        binding.storageAccessSubtitle.setText(
            if (state.sharedStorage) R.string.settings_storage_access_ready_summary
            else R.string.settings_storage_access_required_summary
        )
        binding.storageAnalyzerSubtitle.setText(
            if (state.sharedStorage) R.string.settings_analyzer_summary
            else R.string.settings_analyzer_needs_access
        )
        binding.adbFilesSubtitle.setText(
            if (state.adbEndpointConfigured) R.string.settings_adb_browser_summary
            else R.string.settings_adb_browser_needs_setup
        )
        binding.saveScoutSubtitle.setText(
            if (state.adbEndpointConfigured) R.string.settings_save_scout_summary
            else R.string.settings_save_scout_needs_setup
        )

        binding.storageAccessCard.contentDescription =
            "${getString(R.string.settings_storage_access_title)}. ${binding.storageAccessSubtitle.text}"
        binding.storageAnalyzerCard.contentDescription =
            "${getString(R.string.storage_analyzer)}. ${binding.storageAnalyzerSubtitle.text}"
        binding.adbFilesCard.contentDescription =
            "${getString(R.string.adb_browse)}. ${binding.adbFilesSubtitle.text}"
        binding.saveScoutCard.contentDescription =
            "${getString(R.string.save_scout)}. ${binding.saveScoutSubtitle.text}"

        if (state.adbEndpointConfigured) {
            binding.adbStatusChip.setText(R.string.settings_status_adb_checking)
            verifyAdbHealth()
        } else {
            adbHealthGeneration++
            binding.adbStatusChip.setText(R.string.settings_status_adb_not_configured)
        }
    }

    private fun verifyAdbHealth() {
        val generation = ++adbHealthGeneration
        lifecycleScope.launch {
            val healthy = runCatching { AdbSessionManager.get(this@MainActivity).healthCheck() }.isSuccess
            if (generation != adbHealthGeneration) return@launch
            binding.adbStatusChip.setText(
                if (healthy) R.string.settings_status_adb_connected
                else R.string.settings_status_adb_unreachable
            )
        }
    }

    private fun renderStorageUsage() {
        val result = runCatching {
            val stat = StatFs(StorageAccessController.sharedRoot().absolutePath)
            val total = stat.totalBytes.coerceAtLeast(0L)
            val free = stat.availableBytes.coerceIn(0L, total)
            val used = (total - free).coerceAtLeast(0L)
            val percent = if (total > 0L) {
                ((used.toDouble() / total.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
            } else {
                0
            }
            StorageUsage(total = total, used = used, free = free, percent = percent)
        }

        result.onSuccess { usage ->
            binding.storageUsageText.text = getString(
                R.string.storage_usage,
                formatBytes(usage.used),
                formatBytes(usage.total),
                formatBytes(usage.free),
                usage.percent
            )
            binding.storageUsageProgress.isIndeterminate = false
            binding.storageUsageProgress.progress = usage.percent
            binding.storageUsageProgress.contentDescription = "${usage.percent}%"
        }.onFailure {
            binding.storageUsageText.setText(R.string.storage_usage_unavailable)
            binding.storageUsageProgress.isIndeterminate = false
            binding.storageUsageProgress.progress = 0
        }
    }

    private fun renderTrashCount() {
        val generation = ++trashCountGeneration
        lifecycleScope.launch {
            val count = runCatching {
                withContext(Dispatchers.IO) { TrashManager(this@MainActivity).count() }
            }.getOrNull() ?: return@launch
            if (generation != trashCountGeneration) return@launch
            binding.trashSubtitle.text = getString(R.string.settings_trash_count_summary, count)
            binding.trashCard.contentDescription =
                "${getString(R.string.trash_bin)}. ${binding.trashSubtitle.text}"
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

    private data class StorageUsage(
        val total: Long,
        val used: Long,
        val free: Long,
        val percent: Int
    )
}
