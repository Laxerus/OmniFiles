package dev.laxerus.omnifiles.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
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

class FileBrowserActivity : OmniActivity() {
    private lateinit var binding: ActivityFileBrowserBinding
    private lateinit var adapter: FileListAdapter
    private val sharedRoot: File by lazy { StorageAccessController.sharedRoot().canonicalFile }
    private var currentDir: File = StorageAccessController.sharedRoot()

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
    }

    override fun onResume() {
        super.onResume()
        load(currentDir)
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
        lifecycleScope.launch {
            val files = withContext(Dispatchers.IO) {
                currentDir.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() })).orEmpty()
            }
            adapter.submitList(files)
            binding.emptyText.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun openEntry(file: File) {
        val safe = runCatching { FilePathPolicy.requireInside(file, sharedRoot) }
            .getOrElse {
                Toast.makeText(this, "Güvenli depolama alanının dışına yönlenen öğe engellendi.", Toast.LENGTH_LONG).show()
                return
            }
        if (safe.isDirectory) {
            load(safe)
            return
        }
        val uri = FileProvider.getUriForFile(this, "$packageName.files", safe)
        val extension = safe.extension.lowercase()
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
