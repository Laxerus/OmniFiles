package dev.laxerus.omnifiles.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.databinding.RowFileBinding
import dev.laxerus.omnifiles.fs.TrashEntry
import java.text.DateFormat
import java.util.Date

class TrashListAdapter(
    private val onAction: (TrashEntry) -> Unit
) : ListAdapter<TrashEntry, TrashListAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = RowFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position))

    inner class Holder(private val binding: RowFileBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(entry: TrashEntry) {
            val context = binding.root.context
            binding.icon.text = if (entry.trashedFile.isDirectory) "🗂️" else "🗑️"
            binding.name.text = entry.displayName
            val date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                .format(Date(entry.trashedAt))
            val location = entry.originalFile?.path ?: context.getString(R.string.trash_legacy_location)
            binding.meta.text = "$date • $location"
            binding.root.setOnClickListener { onAction(entry) }
            binding.root.setOnLongClickListener {
                onAction(entry)
                true
            }
            binding.moreButton.contentDescription = context.getString(R.string.more_actions)
            binding.moreButton.setOnClickListener { onAction(entry) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<TrashEntry>() {
        override fun areItemsTheSame(oldItem: TrashEntry, newItem: TrashEntry): Boolean =
            oldItem.trashedFile.absolutePath == newItem.trashedFile.absolutePath

        override fun areContentsTheSame(oldItem: TrashEntry, newItem: TrashEntry): Boolean =
            oldItem.displayName == newItem.displayName &&
                oldItem.originalFile?.path == newItem.originalFile?.path &&
                oldItem.trashedAt == newItem.trashedAt &&
                oldItem.trashedFile.exists() == newItem.trashedFile.exists()
    }
}
