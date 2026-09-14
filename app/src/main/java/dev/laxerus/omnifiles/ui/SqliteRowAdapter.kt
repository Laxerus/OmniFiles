package dev.laxerus.omnifiles.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import dev.laxerus.omnifiles.databinding.RowSqliteBinding
import dev.laxerus.omnifiles.sqlite.SqliteRow

class SqliteRowAdapter(
    private val onClick: (SqliteRow) -> Unit
) : ListAdapter<SqliteRow, SqliteRowAdapter.Holder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(RowSqliteBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(getItem(position), position)

    inner class Holder(private val binding: RowSqliteBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(row: SqliteRow, position: Int) {
            binding.title.text = row.rowId?.let { "rowid $it" } ?: "Satır ${position + 1}"
            binding.content.text = row.cells.joinToString("\n") { "${it.column} = ${it.displayValue}" }
            binding.root.setOnClickListener { onClick(row) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<SqliteRow>() {
        override fun areItemsTheSame(oldItem: SqliteRow, newItem: SqliteRow): Boolean =
            oldItem.rowId != null && oldItem.rowId == newItem.rowId
        override fun areContentsTheSame(oldItem: SqliteRow, newItem: SqliteRow): Boolean = oldItem == newItem
    }
}
