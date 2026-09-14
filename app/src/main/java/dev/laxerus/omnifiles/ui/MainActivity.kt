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
        binding.versionText.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)

        binding.grantAccessButton.setOnClickListener {
            if (!StorageAccessController.requestSharedStorageAccess(this)) {
                Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
            }
        }
        binding.developerSettingsButton.setOnClickListener {
            openSystemSettings(SystemSettingsNavigator.Destination.DEVELOPER_OPTIONS)
        }
        binding.wifiSettingsButton.setOnClickListener {
            openSystemSettings(SystemSettingsNavigator.Destination.WIFI)
        }
        binding.appDetailsButton.setOnClickListener {
            openSystemSettings(SystemSettingsNavigator.Destination.APP_DETAILS)
        }

        binding.storageAnalyzerButton.setOnClickListener {
            startActivity(Intent(this, StorageAnalyzerActivity::class.java))
        }
        binding.trashButton.setOnClickListener {
            startActivity(Intent(this, TrashActivity::class.java))
        }
        binding.checksumButton.setOnClickListener {
            startActivity(Intent(this, ChecksumActivity::class.java))
        }
        binding.adbButton.setOnClickListener {
            startActivity(Intent(this, AdbPairingActivity::class.java))
        }
        binding.adbFilesButton.setOnClickListener {
            startActivity(Intent(this, AdbBrowserActivity::class.java))
        }
        binding.saveScoutButton.setOnClickListener {
            startActivity(Intent(this, SaveScoutActivity::class.java))
        }
        binding.sqliteStudioButton.setOnClickListener {
            startActivity(Intent(this, SqliteStudioActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderStorageUsage()
        renderTrashCount()
    }

    private fun openSystemSettings(destination: SystemSettingsNavigator.Destination) {
        if (!SystemSettingsNavigator.open(this, destination)) {
            Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun renderStatus() {
        val state = AccessSnapshot.read(this)
        val storageState = getString(
            if (state.sharedStorage) R.string.settings_status_ready else R.string.settings_status_attention
        )
        val rootState = getString(
            if (state.rootBinaryPresent) R.string.settings_status_detected else R.string.settings_status_unknown
        )
        binding.storageStatus.text = "${getString(R.string.direct_storage)}: $storageState"
        binding.rootStatus.text = "${getString(R.string.root_status)}: $rootState"
        binding.grantAccessButton.setText(R.string.settings_storage_access_open)
        binding.grantAccessButton.contentDescription = if (state.sharedStorage) {
            "${getString(R.string.settings_storage_access_open)} • ${getString(R.string.storage_access_ready)}"
        } else {
            "${getString(R.string.settings_storage_access_open)} • ${getString(R.string.settings_status_attention)}"
        }
        binding.storageAnalyzerButton.isEnabled = state.sharedStorage
        binding.adbFilesButton.isEnabled = state.adbEndpointConfigured
        binding.saveScoutButton.isEnabled = state.adbEndpointConfigured

        if (state.adbEndpointConfigured) {
            binding.adbStatus.text = "${getString(R.string.adb_shell)}: ${getString(R.string.adb_status_checking)}"
            verifyAdbHealth()
        } else {
            adbHealthGeneration++
            binding.adbStatus.text = "${getString(R.string.adb_shell)}: ${getString(R.string.adb_status_not_configured)}"
        }
    }

    private fun verifyAdbHealth() {
        val generation = ++adbHealthGeneration
        lifecycleScope.launch {
            val healthy = runCatching { AdbSessionManager.get(this@MainActivity).healthCheck() }.isSuccess
            if (generation != adbHealthGeneration) return@launch
            binding.adbStatus.text = "${getString(R.string.adb_shell)}: ${getString(
                if (healthy) R.string.adb_status_connected else R.string.adb_status_unreachable
            )}"
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
            binding.trashButton.text = getString(R.string.trash_bin_count, count)
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
