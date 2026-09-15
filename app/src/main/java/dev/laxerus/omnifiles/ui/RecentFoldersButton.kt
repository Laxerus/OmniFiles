package dev.laxerus.omnifiles.ui

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.fs.QuickFolderPolicy
import dev.laxerus.omnifiles.fs.RecentFolderStore
import java.io.File

class RecentFoldersButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonOutlinedStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    private data class MenuEntry(
        val label: String,
        val folder: File,
    )

    private val recentStore by lazy { RecentFolderStore(context.applicationContext) }

    init {
        setOnClickListener { showQuickAccess() }
    }

    private fun showQuickAccess() {
        if (!StorageAccessController.hasSharedStorageAccess(context)) {
            Toast.makeText(context, R.string.recent_folders_need_access, Toast.LENGTH_SHORT).show()
            return
        }

        val sharedRoot = runCatching { StorageAccessController.sharedRoot().canonicalFile }
            .getOrElse {
                Toast.makeText(context, R.string.recent_folders_unavailable, Toast.LENGTH_SHORT).show()
                return
            }
        val quickFolders = runCatching { QuickFolderPolicy.available(sharedRoot) }
            .getOrElse {
                Toast.makeText(context, R.string.recent_folders_unavailable, Toast.LENGTH_SHORT).show()
                return
            }
        val recents = runCatching { recentStore.list(sharedRoot) }
            .getOrElse {
                Toast.makeText(context, R.string.recent_folders_unavailable, Toast.LENGTH_SHORT).show()
                return
            }

        val quickPaths = quickFolders.mapTo(linkedSetOf()) { it.directory.canonicalPath }
        val menuEntries = buildList {
            quickFolders.forEach { entry ->
                add(
                    MenuEntry(
                        context.getString(R.string.quick_access_item, quickLabel(entry.kind)),
                        entry.directory,
                    )
                )
            }
            recents.asSequence()
                .filterNot { it.canonicalPath in quickPaths }
                .forEach { folder ->
                    add(
                        MenuEntry(
                            context.getString(R.string.quick_access_recent_item, displayPath(folder, sharedRoot)),
                            folder,
                        )
                    )
                }
        }
        if (menuEntries.isEmpty()) {
            Toast.makeText(context, R.string.quick_access_empty, Toast.LENGTH_SHORT).show()
            return
        }

        val builder = MaterialAlertDialogBuilder(context)
            .setTitle(R.string.quick_access_title)
            .setItems(menuEntries.map { it.label }.toTypedArray()) { _, index ->
                openFolder(menuEntries[index].folder, sharedRoot)
            }
            .setNegativeButton(R.string.cancel, null)

        if (recents.isNotEmpty()) {
            builder.setNeutralButton(R.string.recent_folders_clear) { _, _ ->
                recentStore.clear()
                Toast.makeText(context, R.string.recent_folders_cleared, Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
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

    private fun quickLabel(kind: QuickFolderPolicy.Kind): String = context.getString(
        when (kind) {
            QuickFolderPolicy.Kind.DOWNLOADS -> R.string.quick_folder_downloads
            QuickFolderPolicy.Kind.DOCUMENTS -> R.string.quick_folder_documents
            QuickFolderPolicy.Kind.PICTURES -> R.string.quick_folder_pictures
            QuickFolderPolicy.Kind.CAMERA -> R.string.quick_folder_camera
            QuickFolderPolicy.Kind.VIDEOS -> R.string.quick_folder_videos
            QuickFolderPolicy.Kind.MUSIC -> R.string.quick_folder_music
            QuickFolderPolicy.Kind.ANDROID_MEDIA -> R.string.quick_folder_android_media
        }
    )

    private fun displayPath(folder: File, sharedRoot: File): String {
        val relative = folder.canonicalPath
            .removePrefix(sharedRoot.canonicalPath)
            .trimStart(File.separatorChar)
        return relative.ifBlank { folder.name.ifBlank { folder.path } }
    }
}
