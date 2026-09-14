package dev.laxerus.omnifiles.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flyfishxu.kadb.mdns.MdnsDiscoveryState
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.adb.AdbDiscoveryManager
import dev.laxerus.omnifiles.adb.AdbSessionManager
import dev.laxerus.omnifiles.databinding.ActivityAdbPairingBinding
import kotlinx.coroutines.launch

class AdbPairingActivity : OmniActivity() {
    private lateinit var binding: ActivityAdbPairingBinding
    private val manager by lazy { AdbSessionManager.get(this) }
    private val discovery by lazy { AdbDiscoveryManager(this) }
    private var discoveryState = MdnsDiscoveryState()
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdbPairingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
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

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                discovery.state.collect { state ->
                    discoveryState = state
                    applyDiscoveredEndpoints(state)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        discovery.start()
    }

    override fun onStop() {
        discovery.stop()
        super.onStop()
    }

    override fun onDestroy() {
        discovery.close()
        super.onDestroy()
    }

    private fun applyDiscoveredEndpoints(state: MdnsDiscoveryState) {
        val pair = state.pairDevices.firstOrNull()
        val connect = state.connectDevices.firstOrNull()

        val host = connect?.host ?: pair?.host
        if (binding.hostInput.text.isNullOrBlank() && !host.isNullOrBlank()) {
            binding.hostInput.setText(host)
        }
        if (binding.pairPortInput.text.isNullOrBlank() && pair != null) {
            binding.pairPortInput.setText(pair.port.toString())
        }
        if (binding.connectPortInput.text.isNullOrBlank() && connect != null) {
            binding.connectPortInput.setText(connect.port.toString())
        }

        if (!busy && (pair != null || connect != null)) {
            val pieces = buildList {
                pair?.let { add("eşleştirme ${it.host}:${it.port}") }
                connect?.let { add("bağlantı ${it.host}:${it.port}") }
            }
            binding.resultText.text = "Otomatik keşif: ${pieces.joinToString(" • ")}"
        }
    }

    private fun pairAndConnect() {
        val discoveredPair = discoveryState.pairDevices.firstOrNull()
        val discoveredConnect = discoveryState.connectDevices.firstOrNull()
        val host = binding.hostInput.text?.toString()?.trim().orEmpty().ifEmpty {
            discoveredConnect?.host ?: discoveredPair?.host.orEmpty()
        }
        val pairPort = binding.pairPortInput.text?.toString()?.toIntOrNull() ?: discoveredPair?.port
        val connectPort = binding.connectPortInput.text?.toString()?.toIntOrNull() ?: discoveredConnect?.port
        val code = binding.codeInput.text?.toString()?.trim().orEmpty()

        val invalid = host.isEmpty() ||
            pairPort == null || pairPort !in 1..65535 ||
            connectPort == null || connectPort !in 1..65535 ||
            !code.matches(Regex("\\d{6}"))

        if (invalid) {
            binding.resultText.text = "Host, bağlantı bilgileri ve 6 haneli eşleştirme kodunu kontrol et. Otomatik keşif birkaç saniye sürebilir."
            return
        }

        setBusy(true)
        lifecycleScope.launch {
            runCatching {
                manager.pair(host, requireNotNull(pairPort), code)
                val newestConnect = discoveryState.connectDevices.firstOrNull { it.host == host }
                manager.connect(host, newestConnect?.port ?: requireNotNull(connectPort))
            }.onSuccess { identity ->
                binding.resultText.text = "Bağlantı hazır: $identity"
            }.onFailure { error ->
                binding.resultText.text = "Bağlantı kurulamadı: ${error.message ?: error.javaClass.simpleName}"
            }
            setBusy(false)
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.pairButton.isEnabled = !value
        binding.settingsButton.isEnabled = !value
        if (value) binding.resultText.text = "Eşleştiriliyor…"
    }
}
