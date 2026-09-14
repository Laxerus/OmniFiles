package dev.laxerus.omnifiles.ui

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.adb.AdbSessionManager
import dev.laxerus.omnifiles.databinding.ActivitySaveScoutBinding
import dev.laxerus.omnifiles.scout.SaveLocation
import dev.laxerus.omnifiles.scout.SaveScoutService
import kotlinx.coroutines.launch

class SaveScoutActivity : OmniActivity() {
    private lateinit var binding: ActivitySaveScoutBinding
    private val service by lazy { SaveScoutService(this) }
    private val adb by lazy { AdbSessionManager.get(this) }
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySaveScoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.scanButton.setOnClickListener { scan() }
        binding.refreshAppsButton.setOnClickListener { loadPackages() }

        if (adb.endpoint() == null) {
            Toast.makeText(this, R.string.adb_not_ready, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        loadPackages()
    }

    private fun loadPackages() {
        if (busy) return
        setBusy(true, getString(R.string.save_scout_loading_apps))
        lifecycleScope.launch {
            runCatching { service.listThirdPartyPackages() }
                .onSuccess { packages ->
                    binding.packageInput.setAdapter(
                        ArrayAdapter(this@SaveScoutActivity, android.R.layout.simple_dropdown_item_1line, packages)
                    )
                    binding.statusText.text = "${packages.size} kullanıcı uygulaması bulundu."
                }
                .onFailure { binding.statusText.text = it.message ?: "Uygulama listesi alınamadı." }
            setBusy(false)
        }
    }

    private fun scan() {
        if (busy) return
        val packageName = binding.packageInput.text?.toString()?.trim().orEmpty()
        binding.packageLayout.error = null
        if (!SaveScoutService.isValidPackageName(packageName)) {
            binding.packageLayout.error = "Geçerli bir paket adı gir."
            return
        }

        setBusy(true, getString(R.string.save_scout_scanning))
        binding.resultsContainer.removeAllViews()
        lifecycleScope.launch {
            runCatching { service.scan(packageName) }
                .onSuccess { locations -> renderLocations(locations) }
                .onFailure { binding.statusText.text = it.message ?: "Tarama başarısız." }
            setBusy(false)
        }
    }

    private fun renderLocations(locations: List<SaveLocation>) {
        binding.resultsContainer.removeAllViews()
        if (locations.isEmpty()) {
            binding.statusText.text = getString(R.string.save_scout_none)
            return
        }
        binding.statusText.text = "${locations.size} erişilebilir konum bulundu."
        locations.forEach { location ->
            val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = "${location.label} • ${location.entryCount} öğe\n${location.path}"
                isAllCaps = false
                maxLines = 3
                setOnClickListener {
                    startActivity(Intent(this@SaveScoutActivity, AdbBrowserActivity::class.java).apply {
                        putExtra(AdbBrowserActivity.EXTRA_INITIAL_PATH, location.path)
                    })
                }
            }
            val margin = (10 * resources.displayMetrics.density).toInt()
            button.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = margin }
            binding.resultsContainer.addView(button)
        }
    }

    private fun setBusy(value: Boolean, message: String? = null) {
        busy = value
        binding.scanButton.isEnabled = !value
        binding.refreshAppsButton.isEnabled = !value
        binding.packageInput.isEnabled = !value
        if (!message.isNullOrBlank()) binding.statusText.text = message
    }
}
