package dev.laxerus.omnifiles.ui

import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.flyfishxu.kadb.mdns.MdnsDiscoveryState
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.SystemSettingsNavigator
import dev.laxerus.omnifiles.adb.AdbDiscoveryManager
import dev.laxerus.omnifiles.adb.AdbSessionManager
import dev.laxerus.omnifiles.databinding.ActivityAdbPairingBinding
import kotlinx.coroutines.launch

class AdbPairingActivity : OmniActivity() {
    private data class DiscoveredEndpoints(
        val host: String?,
        val pairPort: Int?,
        val connectPort: Int?
    )

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
            openSystemSettings(SystemSettingsNavigator.Destination.DEVELOPER_OPTIONS)
        }
        binding.wifiSettingsButton.setOnClickListener {
            openSystemSettings(SystemSettingsNavigator.Destination.WIFI)
        }
        binding.pairButton.setOnClickListener { pairAndConnect() }
        binding.connectButton.setOnClickListener { connectOnly() }

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

    private fun openSystemSettings(destination: SystemSettingsNavigator.Destination) {
        if (!SystemSettingsNavigator.open(this, destination)) {
            Toast.makeText(this, R.string.settings_open_failed, Toast.LENGTH_LONG).show()
        }
    }

    private fun applyDiscoveredEndpoints(state: MdnsDiscoveryState) {
        val endpoints = selectDiscoveredEndpoints(state)
        if (binding.hostInput.text.isNullOrBlank() && !endpoints.host.isNullOrBlank()) {
            binding.hostInput.setText(endpoints.host)
        }
        if (binding.pairPortInput.text.isNullOrBlank() && endpoints.pairPort != null) {
            binding.pairPortInput.setText(endpoints.pairPort.toString())
        }
        if (binding.connectPortInput.text.isNullOrBlank() && endpoints.connectPort != null) {
            binding.connectPortInput.setText(endpoints.connectPort.toString())
        }

        if (!busy && endpoints.host != null) {
            val pieces = buildList {
                endpoints.pairPort?.let { add("eşleştirme ${endpoints.host}:$it") }
                endpoints.connectPort?.let { add("bağlantı ${endpoints.host}:$it") }
            }
            if (pieces.isNotEmpty()) {
                binding.resultText.text = "Otomatik keşif: ${pieces.joinToString(" • ")}"
            }
        }
    }

    private fun pairAndConnect() {
        val endpoints = selectDiscoveredEndpoints(discoveryState)
        val host = inputHost(endpoints)
        val pairPort = binding.pairPortInput.text?.toString()?.toIntOrNull() ?: endpoints.pairPort
        val connectPort = binding.connectPortInput.text?.toString()?.toIntOrNull() ?: endpoints.connectPort
        val code = binding.codeInput.text?.toString()?.trim().orEmpty()

        val invalid = host.isEmpty() ||
            pairPort == null || pairPort !in 1..65535 ||
            connectPort == null || connectPort !in 1..65535 ||
            !code.matches(Regex("\\d{6}"))

        if (invalid) {
            binding.resultText.text = "Host, bağlantı bilgileri ve 6 haneli eşleştirme kodunu kontrol et. Otomatik keşif birkaç saniye sürebilir."
            return
        }

        setBusy(true, "Eşleştiriliyor…")
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

    private fun connectOnly() {
        val endpoints = selectDiscoveredEndpoints(discoveryState)
        val host = inputHost(endpoints)
        val connectPort = binding.connectPortInput.text?.toString()?.toIntOrNull() ?: endpoints.connectPort
        if (host.isEmpty() || connectPort == null || connectPort !in 1..65535) {
            binding.resultText.text = "Bağlantı için geçerli host ve ADB bağlantı portu gerekli."
            return
        }

        setBusy(true, "Bağlanılıyor…")
        lifecycleScope.launch {
            runCatching { manager.connect(host, connectPort) }
                .onSuccess { identity ->
                    binding.resultText.text = "Bağlantı hazır: $identity"
                }
                .onFailure { error ->
                    binding.resultText.text = "Bağlantı kurulamadı: ${error.message ?: error.javaClass.simpleName}"
                }
            setBusy(false)
        }
    }

    private fun inputHost(endpoints: DiscoveredEndpoints): String =
        binding.hostInput.text?.toString()?.trim().orEmpty().ifEmpty { endpoints.host.orEmpty() }

    private fun selectDiscoveredEndpoints(state: MdnsDiscoveryState): DiscoveredEndpoints {
        val matchedPair = state.pairDevices.firstOrNull { pair ->
            state.connectDevices.any { connect -> connect.host == pair.host }
        }
        val pair = matchedPair ?: state.pairDevices.firstOrNull()
        val connect = pair?.let { selected ->
            state.connectDevices.firstOrNull { it.host == selected.host }
        } ?: state.connectDevices.firstOrNull()
        val host = connect?.host ?: pair?.host
        return DiscoveredEndpoints(
            host = host,
            pairPort = pair?.takeIf { it.host == host }?.port,
            connectPort = connect?.takeIf { it.host == host }?.port
        )
    }

    private fun setBusy(value: Boolean, message: String? = null) {
        busy = value
        binding.pairButton.isEnabled = !value
        binding.connectButton.isEnabled = !value
        binding.settingsButton.isEnabled = !value
        binding.wifiSettingsButton.isEnabled = !value
        if (value && message != null) binding.resultText.text = message
    }
}
