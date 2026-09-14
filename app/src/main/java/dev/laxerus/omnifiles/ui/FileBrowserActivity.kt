package dev.laxerus.omnifiles.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import dev.laxerus.omnifiles.fs.FavoriteStore
import dev.laxerus.omnifiles.fs.FileInspector
import dev.laxerus.omnifiles.fs.FileOperations
import dev.laxerus.omnifiles.fs.FilePathPolicy
import dev.laxerus.omnifiles.fs.TrashManager
import dev.laxerus.omnifiles.fs.TrashTicket
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class FileBrowserActivity : OmniActivity() {
    private enum class SortMode { NAME, DATE, SIZE }
    private enum class TransferMode { COPY, MOVE }
    private data class PendingTransfer(val sourcePaths: List<String>, val mode: TransferMode)

    private lateinit var binding: ActivityFileBrowserBinding
    private lateinit var adapter: FileListAdapter
    private val sharedRoot: File by lazy { StorageAccessController.sharedRoot().canonicalFile }
    private val favoriteStore: FavoriteStore by lazy { FavoriteStore(this) }
    private var currentDir: File = StorageAccessController.sharedRoot()
    private var allEntries: List<File> = emptyList()
    private var sortMode = SortMode.NAME
    private var showHidden = false
    private var loadGeneration = 0
    private var pendingTransfer: PendingTransfer? = null
    private var operationBusy = false
    private val selectedPaths = linkedSetOf<String>()

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

        adapter = FileListAdapter(::handleEntryClick, ::handleEntryLongClick, ::showEntryActions)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.favoriteToggleButton.setOnClickListener { toggleCurrentFavorite() }
        binding.favoritesButton.setOnClickListener { showFavoritePicker() }
        binding.newFolderButton.setOnClickListener { showCreateFolderDialog() }
        binding.pasteButton.setOnClickListener { pastePendingTransfer() }
        binding.cancelTransferButton.setOnClickListener { clearPendingTransfer() }
        binding.selectAllButton.setOnClickListener { selectAllVisible() }
        binding.selectionShareButton.setOnClickListener { shareSelectedFiles() }
        binding.selectionCopyButton.setOnClickListener { stageSelectedTransfer(TransferMode.COPY) }
        binding.selectionMoveButton.setOnClickListener { stageSelectedTransfer(TransferMode.MOVE) }
        binding.selectionTrashButton.setOnClickListener { confirmSelectedTrash() }
        binding.cancelSelectionButton.setOnClickListener { clearSelection() }

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
        updateSelectionUi()
        updateTransferUi()
        updateFavoriteUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_CURRENT_PATH, runCatching { currentDir.canonicalPath }.getOrElse { currentDir.path })
        outState.putString(STATE_SORT_MODE, sortMode.name)
        outState.putBoolean(STATE_SHOW_HIDDEN, showHidden)
        outState.putString(STATE_SEARCH_QUERY, binding.searchInput.text?.toString().orEmpty())
        outState.putStringArrayList(STATE_SELECTED_PATHS, ArrayList(selectedPaths))
        pendingTransfer?.let {
            outState.putStringArrayList(STATE_TRANSFER_PATHS, ArrayList(it.sourcePaths))
            outState.putString(STATE_TRANSFER_MODE, it.mode.name)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onResume() {
        super.onResume()
        val hasAccess = StorageAccessController.hasSharedStorageAccess(this)
        val pending = pendingTransfer
        if (pending != null) {
            val existing = pending.sourcePaths.filter { File(it).exists() }
            if (existing.size != pending.sourcePaths.size) {
                pendingTransfer = existing.takeIf { it.isNotEmpty() }?.let { PendingTransfer(it, pending.mode) }
                Toast.makeText(this, R.string.transfer_missing, Toast.LENGTH_LONG).show()
            }
        }
        selectedPaths.removeAll { !File(it).exists() }
        updateSelectionUi()
        updateTransferUi()
        if (hasAccess) {
            load(currentDir)
        } else {
            loadGeneration++
            selectedPaths.clear()
            allEntries = emptyList()
            adapter.submitList(emptyList())
            adapter.setSelectedPaths(emptySet())
            binding.emptyText.setText(R.string.storage_access_required)
            binding.emptyText.visibility = View.VISIBLE
            updateSelectionUi()
            updateFavoriteUi()
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

        savedInstanceState?.getStringArrayList(STATE_SELECTED_PATHS)
            ?.mapNotNull { path ->
                runCatching { FilePathPolicy.requireInside(File(path), sharedRoot) }.getOrNull()
                    ?.takeIf { it.exists() && it.parentFile?.canonicalPath == currentDir.canonicalPath }
                    ?.canonicalPath
            }
            ?.let(selectedPaths::addAll)

        val restoredTransferPaths = savedInstanceState?.getStringArrayList(STATE_TRANSFER_PATHS)
            ?.mapNotNull { path ->
                runCatching { FilePathPolicy.requireInside(File(path), sharedRoot) }.getOrNull()
                    ?.takeIf(File::exists)
                    ?.canonicalPath
            }
            .orEmpty()
        val restoredTransferMode = savedInstanceState?.getString(STATE_TRANSFER_MODE)
            ?.let { runCatching { TransferMode.valueOf(it) }.getOrNull() }
        if (restoredTransferPaths.isNotEmpty() && restoredTransferMode != null) {
            pendingTransfer = PendingTransfer(restoredTransferPaths.distinct(), restoredTransferMode)
        }
    }

    private fun navigateUpOrFinish() {
        if (operationBusy) return
        if (selectedPaths.isNotEmpty()) {
            clearSelection()
            return
        }
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
        if (::binding.isInitialized && currentDir.canonicalPath != readableDir.canonicalPath) {
            selectedPaths.clear()
            updateSelectionUi()
        }
        currentDir = readableDir
        binding.pathText.text = currentDir.path
        updateFavoriteUi()
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
                val validPaths = it.mapTo(mutableSetOf()) { entry ->
                    runCatching { entry.canonicalPath }.getOrElse { entry.absolutePath }
                }
                selectedPaths.retainAll(validPaths)
                renderEntries()
                updateSelectionUi()
            }.onFailure {
                selectedPaths.clear()
                allEntries = emptyList()
                adapter.submitList(emptyList())
                adapter.setSelectedPaths(emptySet())
                binding.emptyText.text = it.message ?: "Klasör okunamadı"
                binding.emptyText.visibility = View.VISIBLE
                updateSelectionUi()
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
        adapter.setSelectedPaths(selectedPaths)
        if (visible.isEmpty()) {
            val filtered = query.isNotEmpty() || (!showHidden && allEntries.any(::isHidden))
            binding.emptyText.setText(if (filtered) R.string.empty_search else R.string.empty_folder)
            binding.emptyText.visibility = View.VISIBLE
        } else {
            binding.emptyText.visibility = View.GONE
        }
        updateSelectionUi()
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

    private fun handleEntryClick(file: File) {
        if (selectedPaths.isNotEmpty()) toggleSelection(file) else openEntry(file)
    }

    private fun handleEntryLongClick(file: File) {
        if (operationBusy) return
        toggleSelection(file)
    }

    private fun toggleSelection(file: File) {
        if (operationBusy) return
        val safe = runCatching { FilePathPolicy.requireInside(file, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, it.message ?: "Öğe seçilemedi", Toast.LENGTH_LONG).show()
                return
            }
        val path = safe.canonicalPath
        if (!selectedPaths.add(path)) selectedPaths.remove(path)
        updateSelectionUi()
        adapter.setSelectedPaths(selectedPaths)
    }

    private fun selectAllVisible() {
        if (operationBusy) return
        adapter.currentList.forEach { file ->
            runCatching { FilePathPolicy.requireInside(file, sharedRoot).canonicalPath }
                .getOrNull()
                ?.let(selectedPaths::add)
        }
        updateSelectionUi()
        adapter.setSelectedPaths(selectedPaths)
    }

    private fun clearSelection() {
        if (operationBusy) return
        selectedPaths.clear()
        updateSelectionUi()
        adapter.setSelectedPaths(emptySet())
    }

    private fun updateSelectionUi() {
        if (!::binding.isInitialized) return
        val active = selectedPaths.isNotEmpty()
        binding.selectionBar.visibility = if (active) View.VISIBLE else View.GONE
        binding.selectedCountText.text = getString(R.string.selected_count, selectedPaths.size)
        binding.selectAllButton.isEnabled = !operationBusy && adapter.currentList.isNotEmpty()
        binding.selectionCopyButton.isEnabled = active && !operationBusy

        val selectedFiles = selectedPaths.map(::File)
        val allFiles = active && selectedFiles.all { file ->
            runCatching { FilePathPolicy.requireInside(file, sharedRoot) }.getOrNull()?.isFile == true
        }
        binding.selectionShareButton.isEnabled = allFiles && !operationBusy

        val allMutable = active && selectedFiles.all { file ->
            runCatching { FilePathPolicy.requireMutableTarget(file, sharedRoot) }.isSuccess
        }
        binding.selectionMoveButton.isEnabled = allMutable && !operationBusy
        binding.selectionTrashButton.isEnabled = allMutable && !operationBusy
        binding.cancelSelectionButton.isEnabled = active && !operationBusy
        updateTransferUi()
        updateFavoriteUi()
    }

    private fun updateFavoriteUi() {
        if (!::binding.isInitialized) return
        val hasAccess = StorageAccessController.hasSharedStorageAccess(this)
        if (!hasAccess) {
            binding.favoriteToggleButton.setText(R.string.add_favorite)
            binding.favoritesButton.setText(R.string.favorites)
            binding.favoriteToggleButton.isEnabled = false
            binding.favoritesButton.isEnabled = false
            return
        }

        val favorites = runCatching { favoriteStore.list(sharedRoot) }.getOrDefault(emptyList())
        val currentFavorite = runCatching { favoriteStore.isFavorite(currentDir, sharedRoot) }.getOrDefault(false)
        binding.favoriteToggleButton.setText(if (currentFavorite) R.string.remove_favorite else R.string.add_favorite)
        binding.favoritesButton.text = getString(R.string.favorites_count, favorites.size)
        val controlsEnabled = !operationBusy && selectedPaths.isEmpty()
        binding.favoriteToggleButton.isEnabled = controlsEnabled && currentDir.exists() && currentDir.isDirectory
        binding.favoritesButton.isEnabled = controlsEnabled
    }

    private fun toggleCurrentFavorite() {
        if (operationBusy || selectedPaths.isNotEmpty()) return
        val result = runCatching { favoriteStore.toggle(currentDir, sharedRoot) }
        result.onSuccess { nowFavorite ->
            Toast.makeText(
                this,
                if (nowFavorite) R.string.favorite_added else R.string.favorite_removed,
                Toast.LENGTH_SHORT
            ).show()
        }.onFailure {
            Toast.makeText(this, it.message ?: "Favori güncellenemedi.", Toast.LENGTH_LONG).show()
        }
        updateFavoriteUi()
    }

    private fun showFavoritePicker() {
        if (operationBusy || selectedPaths.isNotEmpty()) return
        val favorites = runCatching { favoriteStore.list(sharedRoot) }
            .getOrElse {
                Toast.makeText(this, it.message ?: "Favoriler okunamadı.", Toast.LENGTH_LONG).show()
                return
            }
        updateFavoriteUi()
        if (favorites.isEmpty()) {
            Toast.makeText(this, R.string.favorites_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val labels = favorites.map { folder ->
            val relative = folder.canonicalPath.removePrefix(sharedRoot.path).trimStart(File.separatorChar)
            if (relative.isBlank()) folder.path else relative
        }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.favorites)
            .setItems(labels) { _, index ->
                binding.searchInput.setText("")
                load(favorites[index])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

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
        if (operationBusy || selectedPaths.isNotEmpty()) return
        val safe = runCatching { FilePathPolicy.requireInside(file, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, it.message ?: "Öğe güvenli alanın dışında", Toast.LENGTH_LONG).show()
                return
            }
        val mutable = runCatching { FilePathPolicy.requireMutableTarget(safe, sharedRoot) }.isSuccess

        val actions = buildList {
            add(R.string.details)
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
                    R.string.details -> showDetails(safe)
                    R.string.share -> shareFile(safe)
                    R.string.copy -> stageTransfer(listOf(safe), TransferMode.COPY)
                    R.string.move -> stageTransfer(listOf(safe), TransferMode.MOVE)
                    R.string.rename -> showRenameDialog(safe)
                    R.string.copy_path -> copyPath(safe)
                    R.string.move_to_trash -> confirmTrash(safe)
                }
            }
            .show()
    }

    private fun showDetails(file: File) {
        if (operationBusy) return
        val safe = runCatching { FilePathPolicy.requireInside(file, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, it.message ?: "Ayrıntılar okunamadı", Toast.LENGTH_LONG).show()
                return
            }
        setOperationBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { FileInspector.inspect(safe, sharedRoot) }
            }
            setOperationBusy(false)
            result.onSuccess { inspection ->
                val type = if (safe.isDirectory) "Klasör" else mimeFor(safe)
                val lines = buildList {
                    add(getString(R.string.detail_type, type))
                    add(getString(R.string.detail_size, formatBytes(inspection.totalBytes)))
                    if (safe.isDirectory) {
                        add(getString(R.string.detail_contents, inspection.fileCount, inspection.directoryCount))
                    }
                    if (inspection.skippedCount > 0) {
                        add(getString(R.string.detail_skipped, inspection.skippedCount))
                    }
                    if (inspection.truncated) add(getString(R.string.detail_scan_limited))
                    add(
                        getString(
                            R.string.detail_modified,
                            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(inspection.lastModified))
                        )
                    )
                    add(
                        getString(
                            R.string.detail_access,
                            getString(if (inspection.readable) R.string.yes else R.string.no),
                            getString(if (inspection.writable) R.string.yes else R.string.no)
                        )
                    )
                    add(getString(R.string.detail_path, safe.path))
                }
                MaterialAlertDialogBuilder(this@FileBrowserActivity)
                    .setTitle(safe.name.ifBlank { safe.path })
                    .setMessage(lines.joinToString("\n"))
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }.onFailure {
                Toast.makeText(this@FileBrowserActivity, it.message ?: "Ayrıntılar okunamadı", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareSelectedFiles() {
        if (operationBusy || selectedPaths.isEmpty()) return
        val files = selectedPaths.mapNotNull { path ->
            runCatching { FilePathPolicy.requireInside(File(path), sharedRoot) }.getOrNull()?.takeIf(File::isFile)
        }
        if (files.size != selectedPaths.size || files.isEmpty()) {
            Toast.makeText(this, "Toplu paylaşım yalnız dosyalar için kullanılabilir.", Toast.LENGTH_LONG).show()
            return
        }

        val uris = ArrayList<Uri>(files.size)
        files.forEach { file ->
            uris += FileProvider.getUriForFile(this, "$packageName.files", file)
        }
        val mimeTypes = files.map(::mimeFor).distinct()
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeTypes.singleOrNull() ?: "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            clipData = ClipData.newUri(contentResolver, files.first().name, uris.first()).apply {
                uris.drop(1).forEach { uri -> addItem(ClipData.Item(uri)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.share))) }
            .onFailure { Toast.makeText(this, "Paylaşım ekranı açılamadı.", Toast.LENGTH_SHORT).show() }
    }

    private fun stageSelectedTransfer(mode: TransferMode) {
        if (operationBusy || selectedPaths.isEmpty()) return
        stageTransfer(selectedPaths.map(::File), mode)
    }

    private fun stageTransfer(files: List<File>, mode: TransferMode) {
        if (operationBusy) return
        val safeFiles = files.mapNotNull { file ->
            runCatching { FilePathPolicy.requireInside(file, sharedRoot) }.getOrNull()
        }.distinctBy(File::getCanonicalPath)
        if (safeFiles.size != files.size || safeFiles.isEmpty()) {
            Toast.makeText(this, "Seçimin bir bölümü güvenli değil veya artık mevcut değil.", Toast.LENGTH_LONG).show()
            return
        }
        if (mode == TransferMode.MOVE && safeFiles.any {
                runCatching { FilePathPolicy.requireMutableTarget(it, sharedRoot) }.isFailure
            }) {
            Toast.makeText(this, "Seçimde taşınamayan sistem klasörü var.", Toast.LENGTH_LONG).show()
            return
        }

        val paths = safeFiles.map { it.canonicalPath }
        pendingTransfer = PendingTransfer(paths, mode)
        selectedPaths.clear()
        adapter.setSelectedPaths(emptySet())
        updateSelectionUi()
        updateTransferUi()
        Toast.makeText(
            this,
            when {
                paths.size == 1 && mode == TransferMode.COPY -> getString(R.string.copy_ready)
                paths.size == 1 -> getString(R.string.move_ready)
                mode == TransferMode.COPY -> getString(R.string.batch_copy_ready, paths.size)
                else -> getString(R.string.batch_move_ready, paths.size)
            },
            Toast.LENGTH_LONG
        ).show()
    }

    private fun pastePendingTransfer() {
        if (operationBusy || !StorageAccessController.hasSharedStorageAccess(this)) return
        val pending = pendingTransfer ?: return
        val existingPaths = pending.sourcePaths.filter { File(it).exists() }
        if (existingPaths.size != pending.sourcePaths.size) {
            pendingTransfer = existingPaths.takeIf { it.isNotEmpty() }?.let { PendingTransfer(it, pending.mode) }
            Toast.makeText(this, R.string.transfer_missing, Toast.LENGTH_LONG).show()
        }
        if (existingPaths.isEmpty()) {
            updateTransferUi()
            return
        }

        val destinationDirectory = currentDir
        setOperationBusy(true)
        lifecycleScope.launch {
            val outcomes = withContext(Dispatchers.IO) {
                val succeeded = mutableListOf<String>()
                val failed = mutableListOf<String>()
                existingPaths.forEach { path ->
                    val source = File(path)
                    runCatching {
                        when (pending.mode) {
                            TransferMode.COPY -> FileOperations.copy(source, destinationDirectory, sharedRoot)
                            TransferMode.MOVE -> FileOperations.move(source, destinationDirectory, sharedRoot)
                        }
                    }.onSuccess { succeeded += path }
                        .onFailure { failed += path }
                }
                succeeded to failed
            }

            val succeeded = outcomes.first
            val failed = outcomes.second
            if (pending.mode == TransferMode.MOVE) {
                pendingTransfer = failed.takeIf { it.isNotEmpty() }?.let { PendingTransfer(it, TransferMode.MOVE) }
            } else {
                pendingTransfer = PendingTransfer(existingPaths, TransferMode.COPY)
            }

            setOperationBusy(false)
            val message = when {
                failed.isEmpty() && succeeded.size == 1 -> getString(R.string.transfer_done)
                failed.isEmpty() -> getString(R.string.batch_transfer_done, succeeded.size)
                else -> getString(R.string.batch_transfer_partial, succeeded.size, failed.size)
            }
            Toast.makeText(this@FileBrowserActivity, message, if (failed.isEmpty()) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
            if (succeeded.isNotEmpty()) load(destinationDirectory) else updateTransferUi()
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

        val count = pending?.sourcePaths?.size ?: 0
        val sourceName = pending?.sourcePaths?.singleOrNull()?.let { File(it).name.ifBlank { it } }.orEmpty()
        binding.transferText.text = when {
            pending == null -> ""
            count == 1 && pending.mode == TransferMode.COPY -> getString(R.string.transfer_copy_label, sourceName)
            count == 1 -> getString(R.string.transfer_move_label, sourceName)
            pending.mode == TransferMode.COPY -> getString(R.string.batch_copy_label, count)
            else -> getString(R.string.batch_move_label, count)
        }
        binding.pasteButton.text = when (pending?.mode) {
            TransferMode.MOVE -> getString(R.string.paste_move)
            else -> getString(R.string.paste_copy)
        }
        val selectionActive = selectedPaths.isNotEmpty()
        binding.pasteButton.isEnabled = pending != null && hasAccess && !operationBusy && !selectionActive
        binding.cancelTransferButton.isEnabled = pending != null && !operationBusy
        binding.newFolderButton.isEnabled = hasAccess && !operationBusy && !selectionActive
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
        updateSelectionUi()
        updateTransferUi()
        updateFavoriteUi()
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
        if (operationBusy || selectedPaths.isNotEmpty() || !StorageAccessController.hasSharedStorageAccess(this)) return
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

    private fun confirmSelectedTrash() {
        if (operationBusy || selectedPaths.isEmpty()) return
        val paths = selectedPaths.toList()
        if (paths.any { runCatching { FilePathPolicy.requireMutableTarget(File(it), sharedRoot) }.isFailure }) {
            Toast.makeText(this, "Seçimde çöpe taşınamayan sistem klasörü var.", Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.batch_trash_title)
            .setMessage(getString(R.string.batch_trash_message, paths.size))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.move_to_trash) { _, _ -> moveSelectedToTrash(paths) }
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
            ticket?.let { invalidatePendingTransferIfAffected(file) }
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

    private fun moveSelectedToTrash(paths: List<String>) {
        if (operationBusy || paths.isEmpty()) return
        setOperationBusy(true)
        lifecycleScope.launch {
            val outcomes = withContext(Dispatchers.IO) {
                val manager = TrashManager(this@FileBrowserActivity)
                val tickets = mutableListOf<TrashTicket>()
                val failed = mutableListOf<String>()
                paths.forEach { path ->
                    runCatching { manager.moveToTrash(File(path)) }
                        .onSuccess { tickets += it }
                        .onFailure { failed += path }
                }
                tickets to failed
            }
            val tickets = outcomes.first
            val failed = outcomes.second
            tickets.forEach { ticket -> invalidatePendingTransferIfAffectedPath(ticket.originalFile.path) }
            selectedPaths.clear()
            selectedPaths.addAll(failed.filter { File(it).exists() })
            setOperationBusy(false)
            load(currentDir)

            val message = if (failed.isEmpty()) {
                getString(R.string.batch_trash_done, tickets.size)
            } else {
                getString(R.string.batch_trash_partial, tickets.size, failed.size)
            }
            if (tickets.isNotEmpty()) {
                Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
                    .setAction(R.string.undo) { restoreTrashTickets(tickets) }
                    .show()
            } else {
                Toast.makeText(this@FileBrowserActivity, message, Toast.LENGTH_LONG).show()
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

    private fun restoreTrashTickets(tickets: List<TrashTicket>) {
        if (operationBusy || tickets.isEmpty()) return
        setOperationBusy(true)
        lifecycleScope.launch {
            val counts = withContext(Dispatchers.IO) {
                val manager = TrashManager(this@FileBrowserActivity)
                var restored = 0
                var failed = 0
                tickets.forEach { ticket ->
                    runCatching { manager.restore(ticket) }
                        .onSuccess { restored++ }
                        .onFailure { failed++ }
                }
                restored to failed
            }
            setOperationBusy(false)
            load(currentDir)
            val message = if (counts.second == 0) {
                getString(R.string.batch_restore_done, counts.first)
            } else {
                getString(R.string.batch_restore_partial, counts.first, counts.second)
            }
            Toast.makeText(this@FileBrowserActivity, message, if (counts.second == 0) Toast.LENGTH_SHORT else Toast.LENGTH_LONG).show()
        }
    }

    private fun invalidatePendingTransferIfAffected(file: File) {
        val affectedPath = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        invalidatePendingTransferIfAffectedPath(affectedPath)
    }

    private fun invalidatePendingTransferIfAffectedPath(affectedPath: String) {
        val pending = pendingTransfer ?: return
        val remaining = pending.sourcePaths.filterNot { sourcePath ->
            sourcePath == affectedPath || sourcePath.startsWith(affectedPath + File.separator)
        }
        pendingTransfer = remaining.takeIf { it.isNotEmpty() }?.let { PendingTransfer(it, pending.mode) }
        updateTransferUi()
    }

    private fun mimeFor(file: File): String {
        val extension = file.extension.lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/octet-stream"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB", "EB")
        var value = bytes.toDouble()
        var index = -1
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return "%.1f %s".format(Locale.ROOT, value, units[index])
    }

    companion object {
        private const val STATE_CURRENT_PATH = "current_path"
        private const val STATE_SORT_MODE = "sort_mode"
        private const val STATE_SHOW_HIDDEN = "show_hidden"
        private const val STATE_SEARCH_QUERY = "search_query"
        private const val STATE_SELECTED_PATHS = "selected_paths"
        private const val STATE_TRANSFER_PATHS = "transfer_paths"
        private const val STATE_TRANSFER_MODE = "transfer_mode"
    }
}
