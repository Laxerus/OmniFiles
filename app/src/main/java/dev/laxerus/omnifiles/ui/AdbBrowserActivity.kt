package dev.laxerus.omnifiles.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.adb.AdbRemoteEntry
import dev.laxerus.omnifiles.adb.AdbSessionManager
import dev.laxerus.omnifiles.adb.RemotePathPolicy
import dev.laxerus.omnifiles.databinding.ActivityFileBrowserBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class AdbBrowserActivity : OmniActivity() {
    private lateinit var binding: ActivityFileBrowserBinding
    private lateinit var adapter: AdbFileListAdapter
    private val manager by lazy { AdbSessionManager.get(this) }
    private var currentPath = DEFAULT_PATH
    private var loading = false
    private var pendingExport: File? = null

    private val createDocument = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val source = pendingExport.also { pendingExport = null } ?: return@registerForActivityResult
        if (uri == null) return@registerForActivityResult
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri, "w")?.use { output ->
                        source.inputStream().use { input -> input.copyTo(output) }
                    } ?: error("Hedef dosya açılamadı")
                }
            }
            Toast.makeText(
                this@AdbBrowserActivity,
                if (result.isSuccess) R.string.adb_export_done else R.string.adb_export_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.title = getString(R.string.adb_browse_title)
        binding.toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
        binding.toolbar.setNavigationOnClickListener { navigateUpOrFinish() }
        adapter = AdbFileListAdapter(::openEntry, ::exportEntry)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        prunePreviewCache()
        if (manager.endpoint() == null) {
            Toast.makeText(this, R.string.adb_not_ready, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        Toast.makeText(this, R.string.adb_long_press_export, Toast.LENGTH_SHORT).show()
        load(currentPath)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!loading && currentPath != DEFAULT_PATH) {
            RemotePathPolicy.parent(currentPath)?.let(::load) ?: super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    private fun navigateUpOrFinish() {
        if (loading) return
        if (currentPath == DEFAULT_PATH) finish()
        else RemotePathPolicy.parent(currentPath)?.let(::load) ?: finish()
    }

    private fun load(path: String) {
        if (loading) return
        val safePath = RemotePathPolicy.normalizeAbsolute(path)
        loading = true
        binding.pathText.text = safePath
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = getString(R.string.adb_loading)

        lifecycleScope.launch {
            runCatching { manager.listDirectory(safePath) }
                .onSuccess { entries ->
                    currentPath = safePath
                    val visible = entries
                        .filter { it.name != "." && it.name != ".." && it.errorCode in listOf(null, 0) }
                        .sortedWith(compareBy<AdbRemoteEntry>({ !it.isDirectory }, { it.name.lowercase() }))
                    adapter.submitList(visible)
                    binding.emptyText.text = getString(R.string.empty_folder)
                    binding.emptyText.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
                }
                .onFailure { error ->
                    adapter.submitList(emptyList())
                    binding.emptyText.text = error.message ?: "ADB klasörü okunamadı"
                    binding.emptyText.visibility = View.VISIBLE
                }
            loading = false
        }
    }

    private fun openEntry(entry: AdbRemoteEntry) {
        when {
            entry.isDirectory -> load(entry.path)
            entry.isSymlink -> Toast.makeText(this, R.string.adb_symlink_blocked, Toast.LENGTH_SHORT).show()
            else -> preview(entry)
        }
    }

    private fun exportEntry(entry: AdbRemoteEntry) {
        if (entry.isDirectory || entry.isSymlink || loading) return
        loading = true
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = "Dosya dışa aktarım için hazırlanıyor…"
        lifecycleScope.launch {
            val target = newPreviewFile(entry.name)
            runCatching { manager.pull(entry.path, target) }
                .onSuccess { local ->
                    pendingExport = local
                    Toast.makeText(this@AdbBrowserActivity, R.string.adb_export_ready, Toast.LENGTH_SHORT).show()
                    createDocument.launch(entry.name.ifBlank { "export.bin" })
                }
                .onFailure {
                    target.delete()
                    Toast.makeText(this@AdbBrowserActivity, it.message ?: "Dosya alınamadı", Toast.LENGTH_LONG).show()
                }
            binding.emptyText.visibility = if (adapter.currentList.isEmpty()) View.VISIBLE else View.GONE
            loading = false
        }
    }

    private fun preview(entry: AdbRemoteEntry) {
        if (loading) return
        loading = true
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = "Dosya geçici alana alınıyor…"
        lifecycleScope.launch {
            val target = newPreviewFile(entry.name)
            runCatching { manager.pull(entry.path, target) }
                .onSuccess { openLocalPreview(it) }
                .onFailure {
                    target.delete()
                    Toast.makeText(this@AdbBrowserActivity, it.message ?: "Dosya alınamadı", Toast.LENGTH_LONG).show()
                }
            binding.emptyText.visibility = if (adapter.currentList.isEmpty()) View.VISIBLE else View.GONE
            loading = false
        }
    }

    private fun openLocalPreview(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase()) ?: "application/octet-stream"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Bu dosya türünü açabilecek uygulama bulunamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun newPreviewFile(name: String): File =
        File(previewRoot(), "${UUID.randomUUID()}-${sanitize(name)}")

    private fun previewRoot(): File = File(cacheDir, "adb-preview").apply { mkdirs() }

    private fun prunePreviewCache() {
        previewRoot().listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > PREVIEW_MAX_AGE_MS) file.deleteRecursively()
        }
    }

    private fun sanitize(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(96).ifBlank { "preview.bin" }

    companion object {
        private const val DEFAULT_PATH = "/sdcard/Android/data"
        private const val PREVIEW_MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
