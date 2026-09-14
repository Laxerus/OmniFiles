package dev.laxerus.omnifiles.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.databinding.ActivityChecksumBinding
import dev.laxerus.omnifiles.fs.DigestUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ChecksumActivity : OmniActivity() {
    private lateinit var binding: ActivityChecksumBinding
    private var generation = 0
    private var lastHash: String? = null

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(::calculateChecksum)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChecksumBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.selectFileButton.setOnClickListener { openDocument.launch(arrayOf("*/*")) }
        binding.copyHashButton.setOnClickListener { copyHash() }

        lastHash = savedInstanceState?.getString(STATE_HASH)
        binding.fileInfoText.text = savedInstanceState?.getString(STATE_INFO)
            ?: getString(R.string.checksum_no_file)
        binding.hashText.text = lastHash ?: getString(R.string.checksum_not_calculated)
        binding.copyHashButton.isEnabled = lastHash != null
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_HASH, lastHash)
        outState.putString(STATE_INFO, binding.fileInfoText.text?.toString())
        super.onSaveInstanceState(outState)
    }

    private fun calculateChecksum(uri: Uri) {
        val request = ++generation
        lastHash = null
        binding.hashText.setText(R.string.checksum_calculating)
        binding.copyHashButton.isEnabled = false
        setBusy(true)

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { inspectAndHash(uri) }
            }
            if (request != generation) return@launch
            setBusy(false)

            result.onSuccess { checksum ->
                lastHash = checksum.sha256
                binding.fileInfoText.text = getString(
                    R.string.checksum_file_info,
                    checksum.displayName,
                    checksum.sizeLabel,
                    checksum.mimeType
                )
                binding.hashText.text = checksum.sha256
                binding.copyHashButton.isEnabled = true
            }.onFailure { error ->
                binding.fileInfoText.setText(R.string.checksum_failed)
                binding.hashText.text = error.message ?: getString(R.string.checksum_failed)
                Toast.makeText(
                    this@ChecksumActivity,
                    error.message ?: getString(R.string.checksum_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun inspectAndHash(uri: Uri): ChecksumResult {
        val metadata = readMetadata(uri)
        val hash = contentResolver.openInputStream(uri)?.use(DigestUtils::sha256Hex)
            ?: error("Dosya okunamadı")
        return ChecksumResult(
            displayName = metadata.first,
            sizeLabel = metadata.second?.let(::formatBytes) ?: getString(R.string.checksum_size_unknown),
            mimeType = contentResolver.getType(uri) ?: "application/octet-stream",
            sha256 = hash
        )
    }

    private fun readMetadata(uri: Uri): Pair<String, Long?> {
        var displayName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
            ?: getString(R.string.checksum_unknown_file)
        var size: Long? = null
        contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0 && !cursor.isNull(nameIndex)) {
                    displayName = cursor.getString(nameIndex).takeIf { it.isNotBlank() } ?: displayName
                }
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    size = cursor.getLong(sizeIndex).takeIf { it >= 0L }
                }
            }
        }
        return displayName to size
    }

    private fun copyHash() {
        val hash = lastHash ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("SHA-256", hash))
        Toast.makeText(this, R.string.checksum_copied, Toast.LENGTH_SHORT).show()
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.selectFileButton.isEnabled = !busy
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

    private data class ChecksumResult(
        val displayName: String,
        val sizeLabel: String,
        val mimeType: String,
        val sha256: String
    )

    companion object {
        private const val STATE_HASH = "checksum_hash"
        private const val STATE_INFO = "checksum_info"
    }
}
