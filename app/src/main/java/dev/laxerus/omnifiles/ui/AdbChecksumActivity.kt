package dev.laxerus.omnifiles.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.adb.AdbRemoteEntry
import dev.laxerus.omnifiles.adb.AdbSessionManager
import dev.laxerus.omnifiles.adb.RemotePathPolicy
import dev.laxerus.omnifiles.databinding.ActivityAdbChecksumBinding
import kotlinx.coroutines.launch
import java.util.Locale

class AdbChecksumActivity : OmniActivity() {
    private lateinit var binding: ActivityAdbChecksumBinding
    private val manager by lazy { AdbSessionManager.get(this) }
    private var generation = 0
    private var lastHash: String? = null
    private lateinit var remotePath: String
    private lateinit var displayName: String
    private var remoteSize = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdbChecksumBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        val path = intent.getStringExtra(EXTRA_REMOTE_PATH)
        remotePath = runCatching { RemotePathPolicy.normalizeAbsolute(path.orEmpty()) }
            .getOrElse {
                Toast.makeText(this, R.string.adb_checksum_invalid_path, Toast.LENGTH_LONG).show()
                finish()
                return
            }
        displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
            ?.takeIf(String::isNotBlank)
            ?: remotePath.substringAfterLast('/').ifBlank { remotePath }
        remoteSize = intent.getLongExtra(EXTRA_REMOTE_SIZE, -1L)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.retryButton.setOnClickListener { calculateChecksum() }
        binding.copyHashButton.setOnClickListener { copyHash() }

        lastHash = savedInstanceState?.getString(STATE_HASH)
        binding.fileInfoText.text = getString(
            R.string.adb_checksum_file_info,
            displayName,
            if (remoteSize >= 0L) formatBytes(remoteSize) else getString(R.string.checksum_size_unknown),
            remotePath
        )
        binding.hashText.text = lastHash ?: getString(R.string.checksum_not_calculated)
        binding.copyHashButton.isEnabled = lastHash != null

        if (lastHash == null) calculateChecksum()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_HASH, lastHash)
        super.onSaveInstanceState(outState)
    }

    private fun calculateChecksum() {
        val request = ++generation
        lastHash = null
        binding.hashText.setText(R.string.adb_checksum_calculating)
        binding.copyHashButton.isEnabled = false
        setBusy(true)

        lifecycleScope.launch {
            val result = runCatching { manager.sha256(remotePath) }
            if (request != generation) return@launch
            setBusy(false)
            result.onSuccess { hash ->
                lastHash = hash
                binding.hashText.text = hash
                binding.copyHashButton.isEnabled = true
            }.onFailure { error ->
                binding.hashText.text = error.message ?: getString(R.string.adb_checksum_failed)
                Toast.makeText(
                    this@AdbChecksumActivity,
                    error.message ?: getString(R.string.adb_checksum_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun copyHash() {
        val hash = lastHash ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("ADB SHA-256", hash))
        Toast.makeText(this, R.string.checksum_copied, Toast.LENGTH_SHORT).show()
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.retryButton.isEnabled = !busy
        binding.copyHashButton.isEnabled = !busy && lastHash != null
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

    companion object {
        private const val EXTRA_REMOTE_PATH = "adb_checksum_remote_path"
        private const val EXTRA_DISPLAY_NAME = "adb_checksum_display_name"
        private const val EXTRA_REMOTE_SIZE = "adb_checksum_remote_size"
        private const val STATE_HASH = "adb_checksum_hash"

        fun createIntent(context: Context, entry: AdbRemoteEntry): Intent {
            require(!entry.isDirectory && !entry.isSymlink) { "Yalnız normal ADB dosyalarının SHA-256 değeri hesaplanabilir" }
            val safePath = RemotePathPolicy.normalizeAbsolute(entry.path)
            return Intent(context, AdbChecksumActivity::class.java).apply {
                putExtra(EXTRA_REMOTE_PATH, safePath)
                putExtra(EXTRA_DISPLAY_NAME, entry.name)
                putExtra(EXTRA_REMOTE_SIZE, entry.size)
            }
        }
    }
}
