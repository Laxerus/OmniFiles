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
        bindActionCards()
        bindActions()
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderStorageUsage()
        renderTrashCount()
    }

    private fun bindActionCards() {
        binding.storageAccessCard.bind(
            R.string.settings_storage_access_title,
            R.string.settings_storage_access_summary,
            R.drawable.ic_security_24,
        )
        binding.developerSettingsCard.bind(
            R.string.settings_developer_title,
            R.string.settings_developer_summary,
            R.drawable.ic_code_24,
        )
        binding.wifiSettingsCard.bind(
            R.string.settings_wifi_title,
            R.string.settings_wifi_summary,
            R.drawable.ic_wifi_24,
        )
        binding.appDetailsCard.bind(
            R.string.settings_app_details_title,
            R.string.settings_app_details_summary,
            R.drawable.ic_info_24,
        )
        binding.junkCleanerButton.bind(
            R.string.junk_cleaner_title,
            R.string.settings_junk_cleaner_summary,
            R.drawable.ic_delete_24,
        )
        binding.storageAnalyzerButton.bind(
            R.string.storage_analyzer,
            R.string.settings_analyzer_summary,
            R.drawable.ic_storage_24,
        )
        binding.duplicateFinderButton.bind(
            R.string.duplicate_finder_title,
            R.string.settings_duplicate_finder_summary,
            R.drawable.ic_content_copy_24,
        )
        binding.trashButton.bind(
            R.string.trash_bin,
            R.string.settings_trash_summary,
            R.drawable.ic_delete_24,
        )
        binding.checksumButton.bind(
            R.string.checksum_tool,
            R.string.settings_checksum_summary,
            R.drawable.ic_hash_24,
        )
        binding.adbCard.bind(
            R.string.wireless_debugging,
            R.string.settings_adb_setup_summary,
            R.drawable.ic_wifi_24,
        )
        binding.adbFilesCard.bind(
            R.string.adb_browse,
            R.string.settings_adb_browser_summary,
            R.drawable.ic_storage_24,
        )
        binding.saveScoutCard.bind(
            R.string.save_scout,
            R.string.settings_save_scout_summary,
            R.drawable.ic_search_24,
        )
        binding.sqliteStudioCard.bind(
            R.string.sqlite_studio,
            R.string.settings_sqlite_summary,
            R.drawable.ic_database_24,
        )
    }

    private fun bindActions() {
        binding.storageAccessCard.setOnClickListener { openStorageAccessSettings() }
        binding.developerSettingsCard.setOnClickListener {
            openSystemSettings(SystemSettingsNavigator.Destination.DEVELOPER_OPTIONS)
        }
        binding.wifiSettingsCard.setOnClickListener {
            openSystemSettings(SystemSettingsNavigator.Destination.WIFI)
        }
        binding.appDetailsCard.setOnClickListener {
            openSystemSettings(SystemSettingsNavigator.Destination.APP_DETAILS)
        }

        binding.junkCleanerButton.setOnClickListener {
            openStorageTool(JunkCleanerActivity::class.java)
        }
        binding.storageAnalyzerButton.setOnClickListener {
            openStorageTool(StorageAnalyzerActivity::class.java)
        }
        binding.duplicateFinderButton.setOnClickListener {
            openStorageTool(DuplicateFinderActivity::class.java)
        }
        binding.trashButton.setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }
        binding.checksumButton.setOnClickListener {
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

    private fun openStorageTool(target: Class<*>) {
        if (StorageAccessController.hasSharedStorageAccess(this)) {
            startActivity(Intent(this, target))
        } else {
            Toast.makeText(this, R.string.settings_requires_storage, Toast.LENGTH_SHORT).show()
            openStorageAccessSettings()
        }
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
        binding.storageStatusChip.contentDescription = getString(
            if (state.sharedStorage) R.string.storage_access_ready
            else R.string.settings_status_attention
        )
        binding.rootStatusChip.setText(
            if (state.rootBinaryPresent) R.string.settings_status_root_detected
            else R.string.settings_status_root_unknown
        )
        binding.storageAccessCard.setSummary(
            if (state.sharedStorage) R.string.settings_storage_access_ready_summary
            else R.string.settings_storage_access_required_summary
        )
        binding.junkCleanerButton.setSummary(
            if (state.sharedStorage) R.string.settings_junk_cleaner_summary
            else R.string.settings_junk_cleaner_needs_access
        )
        binding.storageAnalyzerButton.setSummary(
            if (state.sharedStorage) R.string.settings_analyzer_summary
            else R.string.settings_analyzer_needs_access
        )
        binding.duplicateFinderButton.setSummary(
            if (state.sharedStorage) R.string.settings_duplicate_finder_summary
            else R.string.settings_duplicate_finder_needs_access
        )
        binding.adbFilesCard.setSummary(
            if (state.adbEndpointConfigured) R.string.settings_adb_browser_summary
            else R.string.settings_adb_browser_needs_setup
        )
        binding.saveScoutCard.setSummary(
            if (state.adbEndpointConfigured) R.string.settings_save_scout_summary
            else R.string.settings_save_scout_needs_setup
        )

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
            binding.trashButton.setSummary(getString(R.string.settings_trash_count_summary, count))
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
        val percent: Int,
    )
}
