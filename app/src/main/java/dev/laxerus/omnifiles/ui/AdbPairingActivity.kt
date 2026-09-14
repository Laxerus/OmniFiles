package dev.laxerus.omnifiles.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.lifecycleScope
import dev.laxerus.omnifiles.adb.AdbSessionManager
import dev.laxerus.omnifiles.databinding.ActivityAdbPairingBinding
import kotlinx.coroutines.launch

class AdbPairingActivity : OmniActivity() {
    private lateinit var binding: ActivityAdbPairingBinding
    private val manager by lazy { AdbSessionManager.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdbPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationIcon(com.google.android.material.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { finish() }
        manager.endpoint()?.let {
            binding.hostInput.setText(it.host)
            binding.connectPortInput.setText(it.port.toString())
        }

        binding.settingsButton.setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
                .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
        }
        binding.pairButton.setOnClickListener { pairAndConnect() }
    }

    private fun pairAndConnect() {
        val host = binding.hostInput.text?.toString()?.trim().orEmpty()
        val pairPort = binding.pairPortInput.text?.toString()?.toIntOrNull()
        val connectPort = binding.connectPortInput.text?.toString()?.toIntOrNull()
        val code = binding.codeInput.text?.toString()?.trim().orEmpty()

        if (host.isEmpty() || pairPort !in 1..65535 || connectPort !in 1..65535 || !code.matches(Regex("\\d{6}"))) {
            binding.resultText.text = "Host, iki port ve 6 haneli eşleştirme kodunu kontrol et."
            return
        }

        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                manager.pair(host, pairPort!!, code)
                manager.connect(host, connectPort!!)
            }.onSuccess { identity ->
                binding.resultText.text = "Bağlantı hazır: $identity"
            }.onFailure { error ->
                binding.resultText.text = "Bağlantı kurulamadı: ${error.message ?: error.javaClass.simpleName}"
            }
            setBusy(false)
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.pairButton.isEnabled = !busy
        binding.settingsButton.isEnabled = !busy
        if (busy) binding.resultText.text = "Eşleştiriliyor…"
    }
}
