package dev.laxerus.omnifiles.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.databinding.ActivityFileBrowserBinding
import dev.laxerus.omnifiles.fs.FileOperations
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

        adapter = FileListAdapter(::openEntry, ::showEntryActions)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.newFolderButton.setOnClickListener { showCreateFolderDialog() }

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
        val hasAccess = StorageAccessController.hasSharedStorageAccess(this)
        binding.newFolderButton.isEnabled = hasAccess
        if (hasAccess) {
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
            .sortedWith(Comparator(::compareEntries))
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
        openFile(safe)
    }

    private fun openFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", file)
        val mime = mimeFor(file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            clipData = ClipData.newUri(contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Bu dosya türünü açabilecek uygulama bulunamadı.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEntryActions(file: File) {
        val safe = runCatching { FilePathPolicy.requireInside(file, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, it.message ?: "Öğe güvenli alanın dışında", Toast.LENGTH_LONG).show()
                return
            }

        val actions = buildList {
            if (safe.isFile) add(R.string.share)
            add(R.string.rename)
            add(R.string.copy_path)
            add(R.string.move_to_trash)
        }
        val labels = actions.map(::getString).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(safe.name.ifBlank { safe.path })
            .setItems(labels) { _, which ->
                when (actions[which]) {
                    R.string.share -> shareFile(safe)
                    R.string.rename -> showRenameDialog(safe)
                    R.string.copy_path -> copyPath(safe)
                    R.string.move_to_trash -> confirmTrash(safe)
                }
            }
            .show()
    }

    private fun shareFile(file: File) {
        val safe = runCatching { FilePathPolicy.requireInside(file, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, it.message ?: "Dosya paylaşılamadı", Toast.LENGTH_LONG).show()
                return
            }
        if (!safe.isFile) return
        val uri = FileProvider.getUriForFile(this, "$packageName.files", safe)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeFor(safe)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(contentResolver, safe.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.share))) }
            .onFailure { Toast.makeText(this, "Paylaşım ekranı açılamadı.", Toast.LENGTH_SHORT).show() }
    }

    private fun copyPath(file: File) {
        val safe = runCatching { FilePathPolicy.requireInside(file, sharedRoot) }.getOrNull() ?: return
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("OmniFiles path", safe.path))
        Toast.makeText(this, R.string.path_copied, Toast.LENGTH_SHORT).show()
    }

    private fun showCreateFolderDialog() {
        if (!StorageAccessController.hasSharedStorageAccess(this)) return
        val input = EditText(this).apply {
            hint = getString(R.string.new_folder_hint)
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.new_folder)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.create) { _, _ -> createFolder(input.text?.toString().orEmpty()) }
            .show()
    }

    private fun createFolder(name: String) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { FileOperations.createDirectory(currentDir, name, sharedRoot) }
            }
            result.onSuccess {
                Toast.makeText(this@FileBrowserActivity, R.string.folder_created, Toast.LENGTH_SHORT).show()
                load(currentDir)
            }.onFailure {
                Toast.makeText(this@FileBrowserActivity, it.message ?: "Klasör oluşturulamadı", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showRenameDialog(file: File) {
        val input = EditText(this).apply {
            setText(file.name)
            setSelection(text.length)
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.rename)
            .setView(input)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.rename) { _, _ -> renameEntry(file, input.text?.toString().orEmpty()) }
            .show()
    }

    private fun renameEntry(file: File, name: String) {
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { FileOperations.rename(file, name, sharedRoot) }
            }
            result.onSuccess {
                Toast.makeText(this@FileBrowserActivity, R.string.renamed, Toast.LENGTH_SHORT).show()
                load(currentDir)
            }.onFailure {
                Toast.makeText(this@FileBrowserActivity, it.message ?: "Yeniden adlandırma başarısız", Toast.LENGTH_LONG).show()
            }
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

    private fun mimeFor(file: File): String {
        val extension = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }
}
