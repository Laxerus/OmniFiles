package dev.laxerus.omnifiles.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.laxerus.omnifiles.adb.AdbRemoteEntry
import dev.laxerus.omnifiles.databinding.RowFileBinding
import java.text.DateFormat
import java.util.Date

class AdbFileListAdapter(
    private val onClick: (AdbRemoteEntry) -> Unit,
    private val onLongClick: (AdbRemoteEntry) -> Unit
) : ListAdapter<AdbRemoteEntry, AdbFileListAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(RowFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: RowFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: AdbRemoteEntry) {
            binding.icon.text = when {
                entry.isDirectory -> "📁"
                entry.isSymlink -> "🔗"
                else -> "📄"
            }
            binding.name.text = entry.name
            val type = when {
                entry.isDirectory -> "Klasör"
                entry.isSymlink -> "Bağlantı"
                else -> formatBytes(entry.size)
            }
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(entry.modifiedAtMillis))
            binding.meta.text = "$type • $date"
            binding.root.setOnClickListener { onClick(entry) }
            binding.root.setOnLongClickListener {
                onLongClick(entry)
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

    private object Diff : DiffUtil.ItemCallback<AdbRemoteEntry>() {
        override fun areItemsTheSame(oldItem: AdbRemoteEntry, newItem: AdbRemoteEntry) = oldItem.path == newItem.path
        override fun areContentsTheSame(oldItem: AdbRemoteEntry, newItem: AdbRemoteEntry) = oldItem == newItem
    }
}
