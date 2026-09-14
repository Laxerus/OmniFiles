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
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.databinding.ActivityFileBrowserBinding
import dev.laxerus.omnifiles.fs.FileOperations
import dev.laxerus.omnifiles.fs.FilePathPolicy
import dev.laxerus.omnifiles.fs.TrashManager
import dev.laxerus.omnifiles.fs.TrashTicket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class FileBrowserActivity : OmniActivity() {
    private enum class SortMode { NAME, DATE, SIZE }
    private enum class TransferMode { COPY, MOVE }
    private data class PendingTransfer(val sourcePath: String, val mode: TransferMode)

    private lateinit var binding: ActivityFileBrowserBinding
    private lateinit var adapter: FileListAdapter
    private val sharedRoot: File by lazy { StorageAccessController.sharedRoot().canonicalFile }
    private var currentDir: File = StorageAccessController.sharedRoot()
    private var allEntries: List<File> = emptyList()
    private var sortMode = SortMode.NAME
    private var showHidden = false
    private var loadGeneration = 0
    private var pendingTransfer: PendingTransfer? = null
    private var operationBusy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFileBrowserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        restoreBrowserState(savedInstanceState)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { navigateUpOrFinish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = navigateUpOrFinish()
        })

        adapter = FileListAdapter(::openEntry, ::showEntryActions)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.newFolderButton.setOnClickListener { showCreateFolderDialog() }
        binding.pasteButton.setOnClickListener { pastePendingTransfer() }
        binding.cancelTransferButton.setOnClickListener { clearPendingTransfer() }

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

        binding.hiddenSwitch.isChecked = showHidden
        binding.sortGroup.check(
            when (sortMode) {
                SortMode.NAME -> R.id.sortNameButton
                SortMode.DATE -> R.id.sortDateButton
                SortMode.SIZE -> R.id.sortSizeButton
            }
        )
        savedInstanceState?.getString(STATE_SEARCH_QUERY)?.takeIf { it.isNotEmpty() }?.let(binding.searchInput::setText)
        updateTransferUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CURRENT_PATH, runCatching { currentDir.canonicalPath }.getOrElse { currentDir.path })
        outState.putString(STATE_SORT_MODE, sortMode.name)
        outState.putBoolean(STATE_SHOW_HIDDEN, showHidden)
        outState.putString(STATE_SEARCH_QUERY, binding.searchInput.text?.toString().orEmpty())
        pendingTransfer?.let {
            outState.putString(STATE_TRANSFER_PATH, it.sourcePath)
            outState.putString(STATE_TRANSFER_MODE, it.mode.name)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        val hasAccess = StorageAccessController.hasSharedStorageAccess(this)
        val pending = pendingTransfer
        if (pending != null && !File(pending.sourcePath).exists()) {
            pendingTransfer = null
            Toast.makeText(this, R.string.transfer_missing, Toast.LENGTH_LONG).show()
        }
        updateTransferUi()
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

    private fun restoreBrowserState(savedInstanceState: Bundle?) {
        showHidden = savedInstanceState?.getBoolean(STATE_SHOW_HIDDEN, false) ?: false
        sortMode = savedInstanceState?.getString(STATE_SORT_MODE)
            ?.let { runCatching { SortMode.valueOf(it) }.getOrNull() }
            ?: SortMode.NAME

        val restoredPath = savedInstanceState?.getString(STATE_CURRENT_PATH)
        currentDir = restoredPath
            ?.let(::File)
            ?.let { candidate -> runCatching { FilePathPolicy.requireInside(candidate, sharedRoot) }.getOrNull() }
            ?.takeIf { it.isDirectory }
            ?: sharedRoot

        val restoredTransferPath = savedInstanceState?.getString(STATE_TRANSFER_PATH)
        val restoredTransferMode = savedInstanceState?.getString(STATE_TRANSFER_MODE)
            ?.let { runCatching { TransferMode.valueOf(it) }.getOrNull() }
        if (!restoredTransferPath.isNullOrBlank() && restoredTransferMode != null) {
            pendingTransfer = PendingTransfer(restoredTransferPath, restoredTransferMode)
        }
    }

    private fun navigateUpOrFinish() {
        if (operationBusy) return
        if (currentDir.canonicalPath == sharedRoot.path) finish()
        else currentDir.parentFile?.let(::load) ?: finish()
    }

    private fun load(directory: File) {
        val safeDir = runCatching { FilePathPolicy.requireInside(directory, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, "Depolama kökünün dışına çıkılamaz.", Toast.LENGTH_LONG).show()
                sharedRoot
            }
        val readableDir = if (safeDir.isDirectory) safeDir else sharedRoot
        currentDir = readableDir
        binding.pathText.text = currentDir.path
        val generation = ++loadGeneration
        val requestedDir = readableDir

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    requestedDir.listFiles()?.toList() ?: error("Klasör okunamadı")
                }
            }
            if (generation != loadGeneration || currentDir.canonicalPath != requestedDir.canonicalPath) return@launch
            result.onSuccess {
                allEntries = it
                renderEntries()
            }.onFailure {
                allEntries = emptyList()
                adapter.submitList(emptyList())
                binding.emptyText.text = it.message ?: "Klasör okunamadı"
                binding.emptyText.visibility = View.VISIBLE
            }
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
        if (operationBusy) return
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
        if (operationBusy) return
        val safe = runCatching { FilePathPolicy.requireInside(file, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, it.message ?: "Öğe güvenli alanın dışında", Toast.LENGTH_LONG).show()
                return
            }
        val mutable = runCatching { FilePathPolicy.requireMutableTarget(safe, sharedRoot) }.isSuccess

        val actions = buildList {
            if (safe.isFile) add(R.string.share)
            add(R.string.copy)
            if (mutable) add(R.string.move)
            if (mutable) add(R.string.rename)
            add(R.string.copy_path)
            if (mutable) add(R.string.move_to_trash)
        }
        val labels = actions.map(::getString).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(safe.name.ifBlank { safe.path })
            .setItems(labels) { _, which ->
                when (actions[which]) {
                    R.string.share -> shareFile(safe)
                    R.string.copy -> stageTransfer(safe, TransferMode.COPY)
                    R.string.move -> stageTransfer(safe, TransferMode.MOVE)
                    R.string.rename -> showRenameDialog(safe)
                    R.string.copy_path -> copyPath(safe)
                    R.string.move_to_trash -> confirmTrash(safe)
                }
            }
            .show()
    }

    private fun stageTransfer(file: File, mode: TransferMode) {
        val safe = runCatching { FilePathPolicy.requireInside(file, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, it.message ?: "Öğe seçilemedi", Toast.LENGTH_LONG).show()
                return
            }
        if (mode == TransferMode.MOVE && runCatching { FilePathPolicy.requireMutableTarget(safe, sharedRoot) }.isFailure) {
            Toast.makeText(this, "Bu sistem klasörü taşınamaz.", Toast.LENGTH_LONG).show()
            return
        }
        pendingTransfer = PendingTransfer(safe.path, mode)
        updateTransferUi()
        Toast.makeText(
            this,
            if (mode == TransferMode.COPY) R.string.copy_ready else R.string.move_ready,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun pastePendingTransfer() {
        if (operationBusy || !StorageAccessController.hasSharedStorageAccess(this)) return
        val pending = pendingTransfer ?: return
        val source = File(pending.sourcePath)
        if (!source.exists()) {
            pendingTransfer = null
            updateTransferUi()
            Toast.makeText(this, R.string.transfer_missing, Toast.LENGTH_LONG).show()
            return
        }

        val destinationDirectory = currentDir
        setOperationBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    when (pending.mode) {
                        TransferMode.COPY -> FileOperations.copy(source, destinationDirectory, sharedRoot)
                        TransferMode.MOVE -> FileOperations.move(source, destinationDirectory, sharedRoot)
                    }
                }
            }
            result.onSuccess {
                if (pending.mode == TransferMode.MOVE) pendingTransfer = null
                Toast.makeText(this@FileBrowserActivity, R.string.transfer_done, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@FileBrowserActivity, it.message ?: "Dosya işlemi başarısız", Toast.LENGTH_LONG).show()
            }
            setOperationBusy(false)
            if (result.isSuccess) load(destinationDirectory) else updateTransferUi()
        }
    }

    private fun clearPendingTransfer() {
        if (operationBusy) return
        pendingTransfer = null
        updateTransferUi()
    }

    private fun updateTransferUi() {
        if (!::binding.isInitialized) return
        val pending = pendingTransfer
        val hasAccess = StorageAccessController.hasSharedStorageAccess(this)
        val visible = pending != null
        binding.transferText.visibility = if (visible) View.VISIBLE else View.GONE
        binding.pasteButton.visibility = if (visible) View.VISIBLE else View.GONE
        binding.cancelTransferButton.visibility = if (visible) View.VISIBLE else View.GONE

        val sourceName = pending?.sourcePath?.let { File(it).name.ifBlank { it } }.orEmpty()
        binding.transferText.text = when (pending?.mode) {
            TransferMode.COPY -> getString(R.string.transfer_copy_label, sourceName)
            TransferMode.MOVE -> getString(R.string.transfer_move_label, sourceName)
            null -> ""
        }
        binding.pasteButton.text = when (pending?.mode) {
            TransferMode.MOVE -> getString(R.string.paste_move)
            else -> getString(R.string.paste_copy)
        }
        binding.pasteButton.isEnabled = pending != null && hasAccess && !operationBusy
        binding.cancelTransferButton.isEnabled = pending != null && !operationBusy
        binding.newFolderButton.isEnabled = hasAccess && !operationBusy
    }

    private fun setOperationBusy(value: Boolean) {
        operationBusy = value
        binding.operationProgress.visibility = if (value) View.VISIBLE else View.GONE
        binding.searchInput.isEnabled = !value
        binding.hiddenSwitch.isEnabled = !value
        for (index in 0 until binding.sortGroup.childCount) {
            binding.sortGroup.getChildAt(index).isEnabled = !value
        }
        binding.list.alpha = if (value) 0.65f else 1f
        updateTransferUi()
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
        if (operationBusy || !StorageAccessController.hasSharedStorageAccess(this)) return
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
        if (operationBusy) return
        setOperationBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { FileOperations.createDirectory(currentDir, name, sharedRoot) }
            }
            result.onSuccess {
                Toast.makeText(this@FileBrowserActivity, R.string.folder_created, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@FileBrowserActivity, it.message ?: "Klasör oluşturulamadı", Toast.LENGTH_LONG).show()
            }
            setOperationBusy(false)
            if (result.isSuccess) load(currentDir)
        }
    }

    private fun showRenameDialog(file: File) {
        if (operationBusy) return
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
        if (operationBusy) return
        setOperationBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { FileOperations.rename(file, name, sharedRoot) }
            }
            result.onSuccess {
                invalidatePendingTransferIfAffected(file)
                Toast.makeText(this@FileBrowserActivity, R.string.renamed, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@FileBrowserActivity, it.message ?: "Yeniden adlandırma başarısız", Toast.LENGTH_LONG).show()
            }
            setOperationBusy(false)
            if (result.isSuccess) load(currentDir)
        }
    }

    private fun confirmTrash(file: File) {
        if (operationBusy) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.move_to_trash) { _, _ -> moveToTrash(file) }
            .show()
    }

    private fun moveToTrash(file: File) {
        if (operationBusy) return
        setOperationBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { TrashManager(this@FileBrowserActivity).moveToTrash(file) }
            }
            val ticket = result.getOrNull()
            ticket?.let {
                invalidatePendingTransferIfAffected(file)
            }
            result.onFailure {
                Toast.makeText(this@FileBrowserActivity, it.message ?: "İşlem başarısız", Toast.LENGTH_LONG).show()
            }
            setOperationBusy(false)
            if (ticket != null) {
                load(currentDir)
                Snackbar.make(binding.root, R.string.moved_to_trash, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) { restoreTrash(ticket) }
                    .show()
            }
        }
    }

    private fun restoreTrash(ticket: TrashTicket) {
        if (operationBusy) return
        setOperationBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { TrashManager(this@FileBrowserActivity).restore(ticket) }
            }
            setOperationBusy(false)
            result.onSuccess {
                Toast.makeText(this@FileBrowserActivity, R.string.trash_restored, Toast.LENGTH_SHORT).show()
                load(currentDir)
            }.onFailure {
                val detail = it.message?.takeIf(String::isNotBlank)
                val message = if (detail == null) {
                    getString(R.string.trash_restore_failed)
                } else {
                    "${getString(R.string.trash_restore_failed)} $detail"
                }
                Toast.makeText(this@FileBrowserActivity, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun invalidatePendingTransferIfAffected(file: File) {
        val pending = pendingTransfer ?: return
        val affectedPath = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        if (pending.sourcePath == affectedPath || pending.sourcePath.startsWith(affectedPath + File.separator)) {
            pendingTransfer = null
        }
    }

    private fun mimeFor(file: File): String {
        val extension = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    companion object {
        private const val STATE_CURRENT_PATH = "current_path"
        private const val STATE_SORT_MODE = "sort_mode"
        private const val STATE_SHOW_HIDDEN = "show_hidden"
        private const val STATE_SEARCH_QUERY = "search_query"
        private const val STATE_TRANSFER_PATH = "transfer_path"
        private const val STATE_TRANSFER_MODE = "transfer_mode"
    }
}
