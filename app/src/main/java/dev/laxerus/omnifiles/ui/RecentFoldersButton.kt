package dev.laxerus.omnifiles.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.util.AttributeSet
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.fs.FilePathPolicy
import dev.laxerus.omnifiles.fs.QuickFolderPolicy
import dev.laxerus.omnifiles.fs.RecentFileStore
import dev.laxerus.omnifiles.fs.RecentFolderStore
import java.io.File

class RecentFoldersButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonOutlinedStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    private enum class EntryType { FOLDER, FILE }

    private data class MenuEntry(
        val label: String,
        val target: File,
        val type: EntryType,
    )

    private val recentFolderStore by lazy { RecentFolderStore(context.applicationContext) }
    private val recentFileStore by lazy { RecentFileStore(context.applicationContext) }

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
        val recentFolders = runCatching { recentFolderStore.list(sharedRoot) }
            .getOrElse {
                Toast.makeText(context, R.string.recent_folders_unavailable, Toast.LENGTH_SHORT).show()
                return
            }
        val recentFiles = runCatching { recentFileStore.list(sharedRoot) }
            .getOrElse {
                Toast.makeText(context, R.string.recent_files_unavailable, Toast.LENGTH_SHORT).show()
                return
            }

        val quickPaths = quickFolders.mapTo(linkedSetOf()) { it.directory.canonicalPath }
        val visibleRecentFolders = recentFolders.filterNot { it.canonicalPath in quickPaths }
        val menuEntries = buildList {
            quickFolders.forEach { entry ->
                add(
                    MenuEntry(
                        label = context.getString(R.string.quick_access_item, quickLabel(entry.kind)),
                        target = entry.directory,
                        type = EntryType.FOLDER,
                    )
                )
            }
            visibleRecentFolders.forEach { folder ->
                add(
                    MenuEntry(
                        label = context.getString(R.string.quick_access_recent_item, displayPath(folder, sharedRoot)),
                        target = folder,
                        type = EntryType.FOLDER,
                    )
                )
            }
            recentFiles.forEach { file ->
                add(
                    MenuEntry(
                        label = context.getString(R.string.quick_access_recent_file_item, displayPath(file, sharedRoot)),
                        target = file,
                        type = EntryType.FILE,
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
            .setMessage(
                context.getString(
                    R.string.quick_access_summary,
                    quickFolders.size,
                    visibleRecentFolders.size,
                    recentFiles.size,
                )
            )
            .setItems(menuEntries.map { it.label }.toTypedArray()) { _, index ->
                when (menuEntries[index].type) {
                    EntryType.FOLDER -> openFolder(menuEntries[index].target, sharedRoot)
                    EntryType.FILE -> openFile(menuEntries[index].target, sharedRoot)
                }
            }
            .setNegativeButton(R.string.cancel, null)

        if (recentFolders.isNotEmpty() || recentFiles.isNotEmpty()) {
            builder.setNeutralButton(R.string.recent_folders_clear) { _, _ ->
                recentFolderStore.clear()
                recentFileStore.clear()
                Toast.makeText(context, R.string.quick_access_history_cleared, Toast.LENGTH_SHORT).show()
            }
        }
        builder.show()
    }

    private fun openFolder(folder: File, sharedRoot: File) {
        val safeFolder = runCatching { FilePathPolicy.requireDirectEntry(folder, sharedRoot) }
            .getOrNull()
            ?.takeIf { it.exists() && it.isDirectory && it.canRead() }
        if (safeFolder == null) {
            Toast.makeText(context, R.string.recent_folder_stale, Toast.LENGTH_SHORT).show()
            return
        }

        val browserHost = findActivity(context) as? FileBrowserActivity
        val intent = Intent(context, FileBrowserActivity::class.java)
            .putExtra(FileBrowserActivity.EXTRA_START_PATH, safeFolder.canonicalPath)
        runCatching { context.startActivity(intent) }
            .onSuccess {
                runCatching { recentFolderStore.record(safeFolder, sharedRoot) }
                browserHost?.finish()
            }
            .onFailure {
                Toast.makeText(context, R.string.recent_folders_unavailable, Toast.LENGTH_SHORT).show()
            }
    }

    private fun openFile(file: File, sharedRoot: File) {
        val safeFile = runCatching { FilePathPolicy.requireDirectEntry(file, sharedRoot) }
            .getOrNull()
            ?.takeIf { it.exists() && it.isFile }
        if (safeFile == null) {
            Toast.makeText(context, R.string.recent_file_stale, Toast.LENGTH_SHORT).show()
            return
        }

        when (LocalFileLauncher.open(context, safeFile)) {
            LocalFileLauncher.Result.OPENED -> {
                runCatching { recentFileStore.record(safeFile, sharedRoot) }
            }
            LocalFileLauncher.Result.INVALID -> {
                Toast.makeText(context, R.string.recent_file_stale, Toast.LENGTH_SHORT).show()
            }
            LocalFileLauncher.Result.NO_VIEWER -> {
                Toast.makeText(context, R.string.file_open_no_viewer, Toast.LENGTH_SHORT).show()
            }
            LocalFileLauncher.Result.DENIED,
            LocalFileLauncher.Result.FAILED -> {
                Toast.makeText(context, R.string.recent_file_open_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun findActivity(start: Context): Activity? {
        var current: Context? = start
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val next = current.baseContext
            if (next === current) break
            current = next
        }
        return current as? Activity
    }

    private fun quickLabel(kind: QuickFolderPolicy.Kind): String = context.getString(
        when (kind) {
            QuickFolderPolicy.Kind.DOWNLOADS -> R.string.quick_folder_downloads
            QuickFolderPolicy.Kind.DOCUMENTS -> R.string.quick_folder_documents
            QuickFolderPolicy.Kind.PICTURES -> R.string.quick_folder_pictures
            QuickFolderPolicy.Kind.CAMERA -> R.string.quick_folder_camera
            QuickFolderPolicy.Kind.SCREENSHOTS -> R.string.quick_folder_screenshots
            QuickFolderPolicy.Kind.VIDEOS -> R.string.quick_folder_videos
            QuickFolderPolicy.Kind.RECORDINGS -> R.string.quick_folder_recordings
            QuickFolderPolicy.Kind.MUSIC -> R.string.quick_folder_music
            QuickFolderPolicy.Kind.ANDROID_MEDIA -> R.string.quick_folder_android_media
        }
    )

    private fun displayPath(file: File, sharedRoot: File): String {
        val relative = file.canonicalPath
            .removePrefix(sharedRoot.canonicalPath)
            .trimStart(File.separatorChar)
        return relative.ifBlank { file.name.ifBlank { file.path } }
    }
}
