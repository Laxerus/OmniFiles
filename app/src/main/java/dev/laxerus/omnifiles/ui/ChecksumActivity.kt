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
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.databinding.ActivityChecksumBinding
import dev.laxerus.omnifiles.fs.DigestUtils
import dev.laxerus.omnifiles.fs.Sha256Verifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class ChecksumActivity : OmniActivity() {
    private lateinit var binding: ActivityChecksumBinding
    private var generation = 0
    private var lastHash: String? = null
    private var activeUri: Uri? = null

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
        binding.pasteExpectedHashButton.setOnClickListener { pasteExpectedHash() }
        binding.expectedHashInput.doAfterTextChanged { renderVerification() }

        lastHash = savedInstanceState?.getString(STATE_HASH)
        activeUri = savedInstanceState?.getString(STATE_URI)
            ?.let(Uri::parse)
            ?: intent?.data?.takeIf { it.scheme == "content" }
        binding.fileInfoText.text = savedInstanceState?.getString(STATE_INFO)
            ?: getString(R.string.checksum_no_file)
        binding.hashText.text = lastHash ?: getString(R.string.checksum_not_calculated)
        binding.expectedHashInput.setText(savedInstanceState?.getString(STATE_EXPECTED).orEmpty())
        binding.copyHashButton.isEnabled = lastHash != null
        renderVerification()

        if (lastHash == null) activeUri?.let(::calculateChecksum)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_HASH, lastHash)
        outState.putString(STATE_INFO, binding.fileInfoText.text?.toString())
        outState.putString(STATE_EXPECTED, binding.expectedHashInput.text?.toString().orEmpty())
        activeUri?.let { outState.putString(STATE_URI, it.toString()) }
        super.onSaveInstanceState(outState)
    }

    private fun calculateChecksum(uri: Uri) {
        activeUri = uri
        val request = ++generation
        lastHash = null
        binding.hashText.setText(R.string.checksum_calculating)
        binding.copyHashButton.isEnabled = false
        renderVerification()
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
                renderVerification()
            }.onFailure { error ->
                binding.fileInfoText.setText(R.string.checksum_failed)
                binding.hashText.text = error.message ?: getString(R.string.checksum_failed)
                renderVerification()
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

    private fun pasteExpectedHash() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(this)
            ?.toString()
            ?.takeIf { it.isNotBlank() }
        if (text == null) {
            Toast.makeText(this, R.string.checksum_clipboard_empty, Toast.LENGTH_SHORT).show()
            return
        }
        binding.expectedHashInput.setText(text.trim())
        binding.expectedHashInput.setSelection(binding.expectedHashInput.text?.length ?: 0)
    }

    private fun renderVerification() {
        if (!::binding.isInitialized) return
        val expected = binding.expectedHashInput.text?.toString().orEmpty()
        val status = Sha256Verifier.verify(lastHash, expected).status
        binding.expectedHashLayout.error = if (status == Sha256Verifier.Status.INVALID) {
            getString(R.string.checksum_verify_invalid)
        } else {
            null
        }
        binding.verificationText.setText(
            when (status) {
                Sha256Verifier.Status.EMPTY -> R.string.checksum_verify_idle
                Sha256Verifier.Status.INVALID -> R.string.checksum_verify_invalid
                Sha256Verifier.Status.WAITING_FOR_HASH -> R.string.checksum_verify_waiting
                Sha256Verifier.Status.MATCH -> R.string.checksum_verify_match
                Sha256Verifier.Status.MISMATCH -> R.string.checksum_verify_mismatch
            }
        )
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
        binding.pasteExpectedHashButton.isEnabled = !busy
        binding.expectedHashInput.isEnabled = !busy
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
        private const val STATE_URI = "checksum_uri"
        private const val STATE_EXPECTED = "checksum_expected"
    }
}
