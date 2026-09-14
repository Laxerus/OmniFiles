package dev.laxerus.omnifiles.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.laxerus.omnifiles.databinding.RowFileBinding
import java.io.File
import java.text.DateFormat

internal fun Sequence<File>.sortedWith(comparison: (File, File) -> Int): Sequence<File> =
    this.sortedWith(Comparator { left, right -> comparison(left, right) })

class FileListAdapter(
    private val onClick: (File) -> Unit,
    private val onLongClick: (File) -> Unit
) : ListAdapter<File, FileListAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = RowFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: RowFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(file: File) {
            binding.icon.text = if (file.isDirectory) "📁" else "📄"
            binding.name.text = file.name.ifEmpty { file.path }
            binding.meta.text = if (file.isDirectory) {
                "Klasör • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(file.lastModified())}"
            } else {
                "${formatBytes(file.length())} • ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(file.lastModified())}"
            }
            binding.root.setOnClickListener { onClick(file) }
            binding.root.setOnLongClickListener {
                onLongClick(file)
                true
            }
        }
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
        return "%.1f %s".format(value, units[index])
    }

    private object Diff : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File) = oldItem.absolutePath == newItem.absolutePath
        override fun areContentsTheSame(oldItem: File, newItem: File) =
            oldItem.lastModified() == newItem.lastModified() && oldItem.length() == newItem.length()
    }
}
