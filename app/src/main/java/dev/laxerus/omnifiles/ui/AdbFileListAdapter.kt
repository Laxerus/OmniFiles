package dev.laxerus.omnifiles.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.adb.AdbRemoteEntry
import dev.laxerus.omnifiles.databinding.RowFileBinding
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class AdbFileListAdapter(
    private val onClick: (AdbRemoteEntry) -> Unit,
    private val onLongClick: (AdbRemoteEntry) -> Unit,
    private val onMore: (AdbRemoteEntry) -> Unit
) : ListAdapter<AdbRemoteEntry, AdbFileListAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(RowFileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: RowFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: AdbRemoteEntry) {
            binding.icon.text = iconFor(entry)
            binding.name.text = entry.name
            val type = when {
                entry.isDirectory -> "Klasör"
                entry.isSymlink -> "Bağlantı"
                else -> formatBytes(entry.size)
            }
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(entry.modifiedAtMillis))
            binding.meta.text = "$type • $date"
            binding.selectionMark.visibility = View.GONE
            binding.checksumButton.visibility = if (!entry.isDirectory && !entry.isSymlink) View.VISIBLE else View.GONE
            binding.root.setOnClickListener { onClick(entry) }
            binding.root.setOnLongClickListener {
                onLongClick(entry)
                true
            }
            binding.checksumButton.contentDescription = binding.root.context.getString(R.string.adb_checksum_title)
            binding.checksumButton.setOnClickListener { launchChecksum(entry) }
            binding.moreButton.isEnabled = true
            binding.moreButton.visibility = View.VISIBLE
            binding.moreButton.contentDescription = binding.root.context.getString(R.string.more_actions)
            binding.moreButton.setOnClickListener { onMore(entry) }
        }

        private fun launchChecksum(entry: AdbRemoteEntry) {
            val context = binding.root.context
            runCatching {
                context.startActivity(AdbChecksumActivity.createIntent(context, entry))
            }.onFailure {
                Toast.makeText(context, it.message ?: "ADB SHA-256 ekranı açılamadı", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun iconFor(entry: AdbRemoteEntry): String {
        if (entry.isDirectory) return "📁"
        if (entry.isSymlink) return "🔗"
        val extension = entry.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return when (extension) {
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif" -> "🖼️"
            "mp4", "mkv", "webm", "avi", "mov", "m4v" -> "🎞️"
            "mp3", "wav", "ogg", "m4a", "flac", "aac" -> "🎵"
            "zip", "rar", "7z", "tar", "gz", "xz" -> "🗜️"
            "apk", "apks", "xapk" -> "📦"
            "db", "sqlite", "sqlite3" -> "🗃️"
            "json", "xml", "yaml", "yml", "toml", "ini", "properties" -> "⚙️"
            "kt", "java", "js", "ts", "py", "sh", "html", "css", "c", "cpp", "h" -> "💻"
            "pdf" -> "📕"
            "txt", "md", "log", "csv" -> "📝"
            else -> "📄"
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
        return "%.1f %s".format(Locale.ROOT, value, units[index])
    }

    private object Diff : DiffUtil.ItemCallback<AdbRemoteEntry>() {
        override fun areItemsTheSame(oldItem: AdbRemoteEntry, newItem: AdbRemoteEntry) = oldItem.path == newItem.path
        override fun areContentsTheSame(oldItem: AdbRemoteEntry, newItem: AdbRemoteEntry) = oldItem == newItem
    }
}
