package dev.laxerus.omnifiles.ui

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import dev.laxerus.omnifiles.BuildConfig
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.AccessSnapshot
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.databinding.ActivityMainBinding
import dev.laxerus.omnifiles.fs.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : OmniActivity() {
    private lateinit var binding: ActivityMainBinding
    private var trashCountGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.versionText.text = getString(R.string.version_label, BuildConfig.VERSION_NAME)
        binding.openFilesButton.setOnClickListener { startActivity(Intent(this, FileBrowserActivity::class.java)) }
        binding.trashButton.setOnClickListener { startActivity(Intent(this, TrashActivity::class.java)) }
        binding.grantAccessButton.setOnClickListener { StorageAccessController.requestSharedStorageAccess(this) }
        binding.adbButton.setOnClickListener { startActivity(Intent(this, AdbPairingActivity::class.java)) }
        binding.adbFilesButton.setOnClickListener { startActivity(Intent(this, AdbBrowserActivity::class.java)) }
        binding.saveScoutButton.setOnClickListener { startActivity(Intent(this, SaveScoutActivity::class.java)) }
        binding.sqliteStudioButton.setOnClickListener { startActivity(Intent(this, SqliteStudioActivity::class.java)) }
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderTrashCount()
    }

    private fun renderStatus() {
        val state = AccessSnapshot.read(this)
        binding.storageStatus.text = "${getString(R.string.direct_storage)}: ${if (state.sharedStorage) "Hazır" else "İzin gerekli"}"
        binding.adbStatus.text = "${getString(R.string.adb_shell)}: ${if (state.adbEndpointConfigured) "Yapılandırıldı" else "Bağlı değil"}"
        binding.rootStatus.text = "${getString(R.string.root_status)}: ${if (state.rootBinaryPresent) "Algılandı" else "Yok / bilinmiyor"}"
        binding.adbFilesButton.isEnabled = state.adbEndpointConfigured
        binding.saveScoutButton.isEnabled = state.adbEndpointConfigured
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
}
