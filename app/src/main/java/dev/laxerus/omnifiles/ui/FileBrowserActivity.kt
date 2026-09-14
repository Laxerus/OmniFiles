package dev.laxerus.omnifiles.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.databinding.ActivityFileBrowserBinding
import dev.laxerus.omnifiles.fs.FilePathPolicy
import dev.laxerus.omnifiles.fs.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FileBrowserActivity : OmniActivity() {
    private enum class SortMode { NAME, DATE, SIZE }

    private lateinit var binding: ActivityFileBrowserBinding
    private lateinit var adapter: FileListAdapter
    private val sharedRoot: File by lazy { StorageAccessController.sharedRoot().canonicalFile }
    private var currentDir: File = StorageAccessController.sharedRoot()
    private var allEntries: List<File> = emptyList()
    private var sortMode = SortMode.NAME
    private var showHidden = false
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { navigateUpOrFinish() }

        adapter = FileListAdapter(::openEntry, ::confirmTrash)
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
    }

    override fun onResume() {
        super.onResume()
        if (StorageAccessController.hasSharedStorageAccess(this)) {
            load(currentDir)
        } else {
            loadGeneration++
            allEntries = emptyList()
            adapter.submitList(emptyList())
            binding.emptyText.setText(R.string.storage_access_required)
            binding.emptyText.visibility = View.VISIBLE
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (currentDir.canonicalPath != sharedRoot.path) {
            currentDir.parentFile?.let { load(it) } ?: super.onBackPressed()
        } else {
            super.onBackPressed()
        }
    }

    private fun navigateUpOrFinish() {
        if (currentDir.canonicalPath == sharedRoot.path) finish()
        else currentDir.parentFile?.let(::load) ?: finish()
    }

    private fun load(directory: File) {
        val safeDir = runCatching { FilePathPolicy.requireInside(directory, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, "Depolama kökünün dışına çıkılamaz.", Toast.LENGTH_LONG).show()
                sharedRoot
            }
        currentDir = safeDir
        binding.pathText.text = currentDir.path
        val generation = ++loadGeneration
        val requestedDir = safeDir

        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                requestedDir.listFiles()?.toList().orEmpty()
            }
            if (generation != loadGeneration || currentDir.canonicalPath != requestedDir.canonicalPath) return@launch
            allEntries = files
            renderEntries()
        }
    }

    private fun renderEntries() {
        val query = binding.searchInput.text?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val visible = allEntries.asSequence()
            .filter { showHidden || !isHidden(it) }
            .filter { query.isEmpty() || it.name.lowercase(Locale.ROOT).contains(query) }
            .sortedWith(::compareEntries)
            .toList()

        adapter.submitList(visible)
        if (visible.isEmpty()) {
            val filtered = query.isNotEmpty() || (!showHidden && allEntries.any(::isHidden))
            binding.emptyText.setText(if (filtered) R.string.empty_search else R.string.empty_folder)
            binding.emptyText.visibility = View.VISIBLE
        } else {
            binding.emptyText.visibility = View.GONE
        }
    }

    private fun compareEntries(left: File, right: File): Int {
        if (left.isDirectory != right.isDirectory) return if (left.isDirectory) -1 else 1

        val primary = when (sortMode) {
            SortMode.NAME -> left.name.lowercase(Locale.ROOT).compareTo(right.name.lowercase(Locale.ROOT))
            SortMode.DATE -> right.lastModified().compareTo(left.lastModified())
            SortMode.SIZE -> {
                if (left.isDirectory && right.isDirectory) 0
                else right.length().compareTo(left.length())
            }
        }
        return if (primary != 0) primary
        else left.name.lowercase(Locale.ROOT).compareTo(right.name.lowercase(Locale.ROOT))
    }

    private fun isHidden(file: File): Boolean = file.name.startsWith('.') || file.isHidden

    private fun openEntry(file: File) {
        val safe = runCatching { FilePathPolicy.requireInside(file, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, "Güvenli depolama alanının dışına yönlenen öğe engellendi.", Toast.LENGTH_LONG).show()
                return
            }
        if (safe.isDirectory) {
            binding.searchInput.setText("")
            load(safe)
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", safe)
        val extension = safe.extension.lowercase(Locale.ROOT)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
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

    private fun confirmTrash(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.move_to_trash) { _, _ -> moveToTrash(file) }
            .show()
    }

    private fun moveToTrash(file: File) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { TrashManager(this@FileBrowserActivity).moveToTrash(file) }
            }
            result.onSuccess {
                Toast.makeText(this@FileBrowserActivity, "Çöpe taşındı.", Toast.LENGTH_SHORT).show()
                load(currentDir)
            }.onFailure {
                Toast.makeText(this@FileBrowserActivity, it.message ?: "İşlem başarısız", Toast.LENGTH_LONG).show()
            }
        }
    }
}
