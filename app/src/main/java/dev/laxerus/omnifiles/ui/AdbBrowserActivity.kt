package dev.laxerus.omnifiles.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
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
import java.util.Locale
import java.util.UUID

class AdbBrowserActivity : OmniActivity() {
    private enum class SortMode { NAME, DATE, SIZE }

    private lateinit var binding: ActivityFileBrowserBinding
    private lateinit var adapter: AdbFileListAdapter
    private val manager by lazy { AdbSessionManager.get(this) }
    private var currentPath = DEFAULT_PATH
    private var rootPath = DEFAULT_PATH
    private var loading = false
    private var pendingExport: File? = null
    private var allEntries: List<AdbRemoteEntry> = emptyList()
    private var sortMode = SortMode.NAME
    private var showHidden = false

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

        rootPath = runCatching {
            RemotePathPolicy.normalizeAbsolute(intent.getStringExtra(EXTRA_INITIAL_PATH) ?: DEFAULT_PATH)
        }.getOrDefault(DEFAULT_PATH)
        currentPath = rootPath

        binding.toolbar.title = getString(R.string.adb_browse_title)
        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { navigateUpOrFinish() }
        adapter = AdbFileListAdapter(::openEntry, ::exportEntry)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter

        binding.searchInput.doAfterTextChanged { renderEntries() }
        binding.hiddenSwitch.setOnCheckedChangeListener { _, checked ->
            showHidden = checked
            renderEntries()
        }
        binding.sortGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            sortMode = when (checkedId) {
                R.id.sortDateButton -> SortMode.DATE
                R.id.sortSizeButton -> SortMode.SIZE
                else -> SortMode.NAME
            }
            renderEntries()
        }

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
        if (!loading && currentPath != rootPath) {
            safeParentWithinRoot(currentPath)?.let(::load) ?: super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    private fun navigateUpOrFinish() {
        if (loading) return
        if (currentPath == rootPath) finish()
        else safeParentWithinRoot(currentPath)?.let(::load) ?: finish()
    }

    private fun safeParentWithinRoot(path: String): String? {
        val parent = RemotePathPolicy.parent(path) ?: return null
        if (rootPath == "/") return parent
        return if (parent == rootPath || parent.startsWith("$rootPath/")) parent else null
    }

    private fun isInsideRoot(path: String): Boolean =
        rootPath == "/" || path == rootPath || path.startsWith("$rootPath/")

    private fun load(path: String) {
        if (loading) return
        val safePath = RemotePathPolicy.normalizeAbsolute(path)
        if (!isInsideRoot(safePath)) {
            Toast.makeText(this, "Tarayıcı kökünün dışına çıkılamaz.", Toast.LENGTH_LONG).show()
            return
        }
        loading = true
        binding.pathText.text = safePath
        binding.emptyText.visibility = View.VISIBLE
        binding.emptyText.text = getString(R.string.adb_loading)

        lifecycleScope.launch {
            runCatching { manager.listDirectory(safePath) }
                .onSuccess { entries ->
                    currentPath = safePath
                    allEntries = entries.filter {
                        it.name != "." && it.name != ".." && it.errorCode in listOf(null, 0)
                    }
                    binding.searchInput.setText("")
                    renderEntries()
                }
                .onFailure { error ->
                    allEntries = emptyList()
                    adapter.submitList(emptyList())
                    binding.emptyText.text = error.message ?: "ADB klasörü okunamadı"
                    binding.emptyText.visibility = View.VISIBLE
                }
            loading = false
        }
    }

    private fun renderEntries() {
        val query = binding.searchInput.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val visible = allEntries.asSequence()
            .filter { showHidden || !it.name.startsWith('.') }
            .filter { query.isEmpty() || it.name.lowercase(Locale.ROOT).contains(query) }
            .sortedWith(Comparator(::compareEntries))
            .toList()

        adapter.submitList(visible)
        if (visible.isEmpty()) {
            val filtered = query.isNotEmpty() || (!showHidden && allEntries.any { it.name.startsWith('.') })
            binding.emptyText.setText(if (filtered) R.string.empty_search else R.string.empty_folder)
            binding.emptyText.visibility = View.VISIBLE
        } else if (!loading) {
            binding.emptyText.visibility = View.GONE
        }
    }

    private fun compareEntries(left: AdbRemoteEntry, right: AdbRemoteEntry): Int {
        if (left.isDirectory != right.isDirectory) return if (left.isDirectory) -1 else 1
        val primary = when (sortMode) {
            SortMode.NAME -> left.name.lowercase(Locale.ROOT).compareTo(right.name.lowercase(Locale.ROOT))
            SortMode.DATE -> right.modifiedAtMillis.compareTo(left.modifiedAtMillis)
            SortMode.SIZE -> if (left.isDirectory && right.isDirectory) 0 else right.size.compareTo(left.size)
        }
        return if (primary != 0) primary
        else left.name.lowercase(Locale.ROOT).compareTo(right.name.lowercase(Locale.ROOT))
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
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase(Locale.ROOT)) ?: "application/octet-stream"
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
        const val EXTRA_INITIAL_PATH = "dev.laxerus.omnifiles.extra.INITIAL_PATH"
        private const val DEFAULT_PATH = "/sdcard/Android/data"
        private const val PREVIEW_MAX_AGE_MS = 24L * 60L * 60L * 1000L
    }
}
