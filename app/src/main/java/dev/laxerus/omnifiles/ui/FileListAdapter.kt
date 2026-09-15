package dev.laxerus.omnifiles.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.databinding.RowFileBinding
import dev.laxerus.omnifiles.fs.BrowserHighlightPolicy
import dev.laxerus.omnifiles.fs.RecentFileStore
import dev.laxerus.omnifiles.fs.RecentFolderStore
import java.io.File
import java.text.DateFormat
import java.util.Locale

class FileListAdapter(
    private val onClick: (File) -> Unit,
    private val onLongClick: (File) -> Unit,
    private val onMoreClick: (File) -> Unit
) : ListAdapter<File, FileListAdapter.Holder>(Diff) {

    private var selectedPaths: Set<String> = emptySet()
    private var highlightedPath: String? = null
    private var pendingLaunchHighlight: String? = null
    private var attachedRecyclerView: RecyclerView? = null

    fun setSelectedPaths(paths: Set<String>) {
        if (selectedPaths == paths) return
        selectedPaths = paths.toSet()
        notifyDataSetChanged()
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attachedRecyclerView = recyclerView
        val activity = findActivity(recyclerView.context)
        if (activity != null && !activity.intent.getBooleanExtra(BrowserLaunchExtras.EXTRA_HIGHLIGHT_CONSUMED, false)) {
            pendingLaunchHighlight = activity.intent.getStringExtra(BrowserLaunchExtras.EXTRA_HIGHLIGHT_PATH)
            activity.intent.putExtra(BrowserLaunchExtras.EXTRA_HIGHLIGHT_CONSUMED, true)
            activity.intent.removeExtra(BrowserLaunchExtras.EXTRA_HIGHLIGHT_PATH)
        }
        applyLaunchHighlightIfPossible()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (attachedRecyclerView === recyclerView) attachedRecyclerView = null
        super.onDetachedFromRecyclerView(recyclerView)
    }

    override fun onCurrentListChanged(previousList: MutableList<File>, currentList: MutableList<File>) {
        super.onCurrentListChanged(previousList, currentList)
        applyLaunchHighlightIfPossible()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = RowFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    private fun applyLaunchHighlightIfPossible() {
        val rawPath = pendingLaunchHighlight ?: return
        if (currentList.isEmpty()) return
        pendingLaunchHighlight = null
        val index = BrowserHighlightPolicy.findIndex(rawPath, currentList) ?: return
        val file = currentList[index]
        highlightedPath = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        notifyItemChanged(index)
        attachedRecyclerView?.post { attachedRecyclerView?.scrollToPosition(index) }
    }

    private fun clearHighlight() {
        val previous = highlightedPath ?: return
        highlightedPath = null
        val index = currentList.indexOfFirst { file ->
            runCatching { file.canonicalPath }.getOrElse { file.absolutePath } == previous
        }
        if (index >= 0) notifyItemChanged(index)
    }

    inner class Holder(private val binding: RowFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: File) {
            val path = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
            val selected = path in selectedPaths
            val highlighted = path == highlightedPath
            val selectionMode = selectedPaths.isNotEmpty()
            val checksumEligible = !selectionMode && file.isFile &&
                runCatching { file.absolutePath == file.canonicalPath }.getOrDefault(false)
            val presentation = FileRowPresenter.describe(file)
            val kindLabel = kindLabel(binding.root.context, presentation.kind)
            val modifiedLabel = formatModified(binding.root.context, file.lastModified())
            val typeLabel = presentation.extensionLabel
                ?.takeUnless { it.equals(kindLabel, ignoreCase = true) }
                ?.let { "$it • $kindLabel" }
                ?: kindLabel

            binding.icon.text = presentation.icon
            binding.icon.contentDescription = kindLabel
            binding.name.text = file.name.ifEmpty { file.path }
            binding.meta.text = if (file.isDirectory) {
                "$typeLabel • $modifiedLabel"
            } else {
                "$typeLabel • ${formatBytes(file.length())} • $modifiedLabel"
            }
            binding.selectionMark.visibility = if (selected) View.VISIBLE else View.GONE
            binding.checksumButton.visibility = if (checksumEligible) View.VISIBLE else View.GONE
            binding.moreButton.visibility = if (selectionMode) View.GONE else View.VISIBLE

            binding.root.strokeWidth = dp(binding.root.context, if (highlighted) 2 else 1)
            binding.root.setStrokeColor(
                MaterialColors.getColor(
                    binding.root,
                    if (highlighted) androidx.appcompat.R.attr.colorPrimary
                    else com.google.android.material.R.attr.colorOutlineVariant,
                )
            )
            binding.root.contentDescription = buildString {
                append(binding.root.context.getString(R.string.file_row_accessibility, binding.name.text, binding.meta.text))
                if (selected) append(" • seçili")
                if (highlighted) append(" • ${binding.root.context.getString(R.string.browser_highlighted_entry)}")
            }
            binding.root.setOnClickListener {
                if (highlighted) clearHighlight()
                if (selectionMode) {
                    onClick(file)
                    return@setOnClickListener
                }
                when {
                    file.isDirectory -> {
                        recordRecentDirectory(binding.root.context, file)
                        onClick(file)
                    }
                    file.isFile -> launchFile(file)
                    else -> onClick(file)
                }
            }
            binding.root.setOnLongClickListener {
                if (highlighted) clearHighlight()
                onLongClick(file)
                true
            }
            binding.checksumButton.contentDescription = binding.root.context.getString(R.string.checksum_tool)
            binding.checksumButton.setOnClickListener { launchChecksum(file) }
            binding.moreButton.contentDescription = binding.root.context.getString(R.string.more_actions)
            binding.moreButton.setOnClickListener { onMoreClick(file) }
        }

        private fun launchFile(file: File) {
            val context = binding.root.context
            val intent = runCatching { LocalFileIntents.viewIntent(context, file) }
                .getOrElse {
                    Toast.makeText(context, R.string.file_open_invalid, Toast.LENGTH_LONG).show()
                    return
                }
            try {
                context.startActivity(intent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, R.string.file_open_no_viewer, Toast.LENGTH_SHORT).show()
                return
            } catch (_: SecurityException) {
                Toast.makeText(context, R.string.file_open_failed, Toast.LENGTH_LONG).show()
                return
            }
            recordRecentFile(context, file)
        }

        private fun launchChecksum(file: File) {
            val context = binding.root.context
            val intent = runCatching { LocalFileIntents.checksumIntent(context, file) }
                .getOrElse {
                    Toast.makeText(context, it.message ?: "SHA-256 için dosya açılamadı", Toast.LENGTH_LONG).show()
                    return
                }
            runCatching { context.startActivity(intent) }
                .onFailure {
                    Toast.makeText(context, "SHA-256 ekranı açılamadı", Toast.LENGTH_SHORT).show()
                }
        }

        private fun recordRecentDirectory(context: Context, directory: File) {
            runCatching {
                RecentFolderStore(context.applicationContext).record(
                    directory = directory,
                    sharedRoot = StorageAccessController.sharedRoot()
                )
            }
        }

        private fun recordRecentFile(context: Context, file: File) {
            runCatching {
                RecentFileStore(context.applicationContext).record(
                    file = file,
                    sharedRoot = StorageAccessController.sharedRoot()
                )
            }
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val next = current.baseContext
            if (next === current) break
            current = next
        }
        return current as? Activity
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    private fun kindLabel(context: Context, kind: FileVisualKind): String = context.getString(
        when (kind) {
            FileVisualKind.DIRECTORY -> R.string.file_kind_directory
            FileVisualKind.IMAGE -> R.string.file_kind_image
            FileVisualKind.VIDEO -> R.string.file_kind_video
            FileVisualKind.AUDIO -> R.string.file_kind_audio
            FileVisualKind.ARCHIVE -> R.string.file_kind_archive
            FileVisualKind.ANDROID_PACKAGE -> R.string.file_kind_android_package
            FileVisualKind.DATABASE -> R.string.file_kind_database
            FileVisualKind.CONFIG -> R.string.file_kind_config
            FileVisualKind.CODE -> R.string.file_kind_code
            FileVisualKind.PDF -> R.string.file_kind_pdf
            FileVisualKind.DOCUMENT -> R.string.file_kind_document
            FileVisualKind.SPREADSHEET -> R.string.file_kind_spreadsheet
            FileVisualKind.PRESENTATION -> R.string.file_kind_presentation
            FileVisualKind.EBOOK -> R.string.file_kind_ebook
            FileVisualKind.FONT -> R.string.file_kind_font
            FileVisualKind.TEXT -> R.string.file_kind_text
            FileVisualKind.OTHER -> R.string.file_kind_other
        }
    )

    private fun formatModified(context: Context, timestamp: Long): String {
        if (timestamp <= 0L) return context.getString(R.string.file_modified_unknown)
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(timestamp)
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var index = -1
        while (value >= 1024 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return "%.1f %s".format(Locale.ROOT, value, units[index])
    }

    private object Diff : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File) = oldItem.absolutePath == newItem.absolutePath
        override fun areContentsTheSame(oldItem: File, newItem: File) =
            oldItem.lastModified() == newItem.lastModified() && oldItem.length() == newItem.length()
    }
}
