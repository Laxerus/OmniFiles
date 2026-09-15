package dev.laxerus.omnifiles.ui

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.fs.RecentFolderStore
import java.io.File

class RecentFoldersButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonOutlinedStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    private val recentStore by lazy { RecentFolderStore(context.applicationContext) }

    init {
        setOnClickListener { showRecentFolders() }
    }

    private fun showRecentFolders() {
        if (!StorageAccessController.hasSharedStorageAccess(context)) {
            Toast.makeText(context, R.string.recent_folders_need_access, Toast.LENGTH_SHORT).show()
            return
        }

        val sharedRoot = runCatching { StorageAccessController.sharedRoot().canonicalFile }
            .getOrElse {
                Toast.makeText(context, R.string.recent_folders_unavailable, Toast.LENGTH_SHORT).show()
                return
            }
        val recents = runCatching { recentStore.list(sharedRoot) }
            .getOrElse {
                Toast.makeText(context, R.string.recent_folders_unavailable, Toast.LENGTH_SHORT).show()
                return
            }
        if (recents.isEmpty()) {
            Toast.makeText(context, R.string.recent_folders_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val labels = recents.map { folder -> displayPath(folder, sharedRoot) }.toTypedArray()
        MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.recent_folders_count, recents.size))
            .setItems(labels) { _, index -> openFolder(recents[index], sharedRoot) }
            .setNeutralButton(R.string.recent_folders_clear) { _, _ ->
                recentStore.clear()
                Toast.makeText(context, R.string.recent_folders_cleared, Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openFolder(folder: File, sharedRoot: File) {
        val safeFolder = runCatching {
            recentStore.record(folder, sharedRoot).firstOrNull()
        }.getOrNull()
        if (safeFolder == null) {
            Toast.makeText(context, R.string.recent_folder_stale, Toast.LENGTH_SHORT).show()
            return
        }

        val intent = Intent(context, FileBrowserActivity::class.java)
            .putExtra(FileBrowserActivity.EXTRA_START_PATH, safeFolder.canonicalPath)
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, R.string.recent_folders_unavailable, Toast.LENGTH_SHORT).show()
            }
    }

    private fun displayPath(folder: File, sharedRoot: File): String {
        val relative = folder.canonicalPath
            .removePrefix(sharedRoot.canonicalPath)
            .trimStart(File.separatorChar)
        return relative.ifBlank { folder.name.ifBlank { folder.path } }
    }
}
