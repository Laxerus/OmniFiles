package dev.laxerus.omnifiles.ui

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.databinding.ActivityTrashBinding
import dev.laxerus.omnifiles.fs.TrashEntry
import dev.laxerus.omnifiles.fs.TrashManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TrashActivity : OmniActivity() {
    private lateinit var binding: ActivityTrashBinding
    private lateinit var adapter: TrashListAdapter
    private val trashManager by lazy { TrashManager(this) }
    private var busy = false
    private var entries: List<TrashEntry> = emptyList()
    private var loadGeneration = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { if (!busy) finish() }

        adapter = TrashListAdapter(::showEntryActions)
        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.emptyTrashButton.setOnClickListener { confirmEmptyTrash() }
    }

    override fun onResume() {
        super.onResume()
        loadEntries()
    }

    private fun loadEntries() {
        val generation = ++loadGeneration
        setBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { trashManager.listEntries() }
            }
            if (generation != loadGeneration) return@launch
            result.onSuccess {
                entries = it
                adapter.submitList(it)
                renderEmptyState()
            }.onFailure {
                entries = emptyList()
                adapter.submitList(emptyList())
                binding.emptyText.text = it.message ?: getString(R.string.trash_load_failed)
                binding.emptyText.visibility = View.VISIBLE
            }
            setBusy(false)
        }
    }

    private fun renderEmptyState() {
        binding.emptyText.setText(R.string.trash_empty)
        binding.emptyText.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        binding.emptyTrashButton.isEnabled = entries.isNotEmpty() && !busy
    }

    private fun showEntryActions(entry: TrashEntry) {
        if (busy) return
        val actions = buildList {
            if (entry.originalFile != null) add(R.string.restore)
            add(R.string.delete_permanently)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(entry.displayName)
            .setMessage(
                entry.originalFile?.path
                    ?: getString(R.string.trash_restore_unavailable)
            )
            .setItems(actions.map(::getString).toTypedArray()) { _, which ->
                when (actions[which]) {
                    R.string.restore -> restore(entry)
                    R.string.delete_permanently -> confirmDelete(entry)
                }
            }
            .show()
    }

    private fun restore(entry: TrashEntry) {
        if (busy || entry.originalFile == null) return
        setBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { trashManager.restore(entry) }
            }
            result.onSuccess {
                Toast.makeText(this@TrashActivity, R.string.trash_restored, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(
                    this@TrashActivity,
                    it.message ?: getString(R.string.trash_restore_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
            setBusy(false)
            loadEntries()
        }
    }

    private fun confirmDelete(entry: TrashEntry) {
        if (busy) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_permanently_title)
            .setMessage(getString(R.string.delete_permanently_message, entry.displayName))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete_permanently) { _, _ -> deletePermanently(entry) }
            .show()
    }

    private fun deletePermanently(entry: TrashEntry) {
        if (busy) return
        setBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { trashManager.deletePermanently(entry) }
            }
            result.onSuccess {
                Toast.makeText(this@TrashActivity, R.string.trash_deleted, Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@TrashActivity, it.message ?: getString(R.string.trash_delete_failed), Toast.LENGTH_LONG).show()
            }
            setBusy(false)
            loadEntries()
        }
    }

    private fun confirmEmptyTrash() {
        if (busy || entries.isEmpty()) return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.empty_trash_title)
            .setMessage(R.string.empty_trash_message)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.empty_trash) { _, _ -> emptyTrash() }
            .show()
    }

    private fun emptyTrash() {
        if (busy) return
        setBusy(true)
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { trashManager.emptyTrash() }
            }
            result.onSuccess { cleanup ->
                val message = if (cleanup.failed == 0) {
                    getString(R.string.trash_emptied, cleanup.deleted)
                } else {
                    getString(R.string.trash_emptied_partial, cleanup.deleted, cleanup.failed)
                }
                Toast.makeText(this@TrashActivity, message, Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(this@TrashActivity, it.message ?: getString(R.string.trash_delete_failed), Toast.LENGTH_LONG).show()
            }
            setBusy(false)
            loadEntries()
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.progress.visibility = if (value) View.VISIBLE else View.GONE
        binding.list.alpha = if (value) 0.6f else 1f
        binding.emptyTrashButton.isEnabled = !value && entries.isNotEmpty()
    }
}
