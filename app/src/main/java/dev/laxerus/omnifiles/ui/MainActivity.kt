package dev.laxerus.omnifiles.ui

import android.content.Intent
import android.os.Bundle
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.AccessSnapshot
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.databinding.ActivityMainBinding

class MainActivity : OmniActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.openFilesButton.setOnClickListener {
            startActivity(Intent(this, FileBrowserActivity::class.java))
        }
        binding.grantAccessButton.setOnClickListener {
            StorageAccessController.requestSharedStorageAccess(this)
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
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
    }

    private fun renderStatus() {
        val state = AccessSnapshot.read(this)
        binding.storageStatus.text = "${getString(R.string.direct_storage)}: ${if (state.sharedStorage) "Hazır" else "İzin gerekli"}"
        binding.adbStatus.text = "${getString(R.string.adb_shell)}: ${if (state.adbEndpointConfigured) "Yapılandırıldı" else "Bağlı değil"}"
        binding.rootStatus.text = "${getString(R.string.root_status)}: ${if (state.rootBinaryPresent) "Algılandı" else "Yok / bilinmiyor"}"
        binding.adbFilesButton.isEnabled = state.adbEndpointConfigured
        binding.saveScoutButton.isEnabled = state.adbEndpointConfigured
    }
}
