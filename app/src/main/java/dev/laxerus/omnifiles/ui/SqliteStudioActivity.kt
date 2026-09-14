package dev.laxerus.omnifiles.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import dev.laxerus.omnifiles.R
import dev.laxerus.omnifiles.databinding.ActivitySqliteStudioBinding
import dev.laxerus.omnifiles.sqlite.SqliteCell
import dev.laxerus.omnifiles.sqlite.SqliteCellKind
import dev.laxerus.omnifiles.sqlite.SqliteRow
import dev.laxerus.omnifiles.sqlite.SqliteTableInfo
import dev.laxerus.omnifiles.sqlite.SqliteWorkspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SqliteStudioActivity : OmniActivity() {
    private lateinit var binding: ActivitySqliteStudioBinding
    private lateinit var rowsAdapter: SqliteRowAdapter
    private val workspace by lazy { SqliteWorkspace(this) }
    private var tables: List<SqliteTableInfo> = emptyList()
    private var selectedTable: SqliteTableInfo? = null
    private var busy = false

    private val openDocument = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        loadDatabase(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySqliteStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24)
        binding.toolbar.setNavigationOnClickListener { handleExit() }
        rowsAdapter = SqliteRowAdapter(::chooseCell)
        binding.rowsList.layoutManager = LinearLayoutManager(this)
        binding.rowsList.adapter = rowsAdapter

        binding.openButton.setOnClickListener {
            if (!busy) openDocument.launch(arrayOf("*/*"))
        }
        binding.saveButton.setOnClickListener { save() }
        binding.tableInput.setOnItemClickListener { _, _, position, _ ->
            tables.getOrNull(position)?.let(::selectTable)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = handleExit()

    override fun onDestroy() {
        workspace.close()
        super.onDestroy()
    }

    private fun loadDatabase(uri: Uri) {
        setBusy(true, "Veritabanı güvenli çalışma alanına alınıyor…")
        lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { workspace.open(uri) } }
            if (result.isFailure) {
                binding.statusText.text = "Açılamadı: ${result.exceptionOrNull()?.message ?: "bilinmeyen hata"}"
                setBusy(false)
                refreshSaveState()
                return@launch
            }

            val found = result.getOrThrow()
            tables = found
            selectedTable = null
            rowsAdapter.submitList(emptyList())
            binding.tableInput.setAdapter(
                ArrayAdapter(this@SqliteStudioActivity, android.R.layout.simple_dropdown_item_1line, found.map { it.name })
            )
            setBusy(false)

            if (found.isEmpty()) {
                binding.tableInput.setText("", false)
                binding.statusText.text = getString(R.string.sqlite_no_tables)
            } else {
                binding.tableInput.setText(found.first().name, false)
                selectTable(found.first())
            }
            refreshSaveState()
        }
    }

    private fun selectTable(table: SqliteTableInfo) {
        if (busy) return
        selectedTable = table
        setBusy(true, "${table.name} okunuyor…")
        lifecycleScope.launch {
            val result = runCatching { withContext(Dispatchers.IO) { workspace.loadRows(table) } }
            result.onSuccess { rows ->
                rowsAdapter.submitList(rows)
                binding.statusText.text = buildString {
                    append("${table.name}: ${rows.size} satır gösteriliyor")
                    if (table.withoutRowId) append(" • ${getString(R.string.sqlite_without_rowid)}")
                }
            }.onFailure {
                binding.statusText.text = it.message ?: "Tablo okunamadı"
            }
            setBusy(false)
            refreshSaveState()
        }
    }

    private fun chooseCell(row: SqliteRow) {
        val table = selectedTable ?: return
        if (table.withoutRowId || row.rowId == null) {
            Toast.makeText(this, R.string.sqlite_without_rowid, Toast.LENGTH_LONG).show()
            return
        }
        val labels = row.cells.map { "${it.column} = ${it.displayValue}" }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sqlite_edit_cell)
            .setItems(labels) { _, which -> row.cells.getOrNull(which)?.let { editCell(table, row, it) } }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun editCell(table: SqliteTableInfo, row: SqliteRow, cell: SqliteCell) {
        if (cell.kind == SqliteCellKind.BLOB) {
            Toast.makeText(this, R.string.sqlite_blob_readonly, Toast.LENGTH_LONG).show()
            return
        }
        val input = TextInputEditText(this).apply {
            setText(cell.value?.toString().orEmpty())
            setSelection(text?.length ?: 0)
        }
        val layout = TextInputLayout(this).apply {
            hint = "${cell.column} • ${cell.kind.name}"
            val horizontal = (24 * resources.displayMetrics.density).toInt()
            val top = (8 * resources.displayMetrics.density).toInt()
            setPadding(horizontal, top, horizontal, 0)
            addView(input)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.sqlite_edit_cell)
            .setView(layout)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.sqlite_apply) { _, _ ->
                val parsed = runCatching { parseValue(cell, input.text?.toString().orEmpty()) }
                parsed.onSuccess { value -> applyCellEdit(table, row, cell, value) }
                    .onFailure { Toast.makeText(this, it.message ?: "Değer geçersiz", Toast.LENGTH_LONG).show() }
            }
            .show()
    }

    private fun parseValue(cell: SqliteCell, text: String): Any? = when (cell.kind) {
        SqliteCellKind.NULL -> text.takeIf { it.isNotEmpty() }
        SqliteCellKind.INTEGER -> text.toLongOrNull() ?: error("Tam sayı bekleniyor")
        SqliteCellKind.FLOAT -> text.toDoubleOrNull() ?: error("Ondalıklı sayı bekleniyor")
        SqliteCellKind.TEXT -> text
        SqliteCellKind.BLOB -> error("BLOB hücreleri düzenlenemez")
    }

    private fun applyCellEdit(table: SqliteTableInfo, row: SqliteRow, cell: SqliteCell, value: Any?) {
        if (busy) return
        setBusy(true, "Değişiklik çalışma kopyasına uygulanıyor…")
        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { workspace.updateCell(table, row, cell, value) }
            }
            if (result.isFailure) {
                binding.statusText.text = result.exceptionOrNull()?.message ?: "Hücre güncellenemedi"
                setBusy(false)
                refreshSaveState()
                return@launch
            }
            setBusy(false)
            refreshSaveState()
            selectTable(table)
        }
    }

    private fun save() {
        if (busy || !workspace.dirty) return
        setBusy(true, "Bütünlük kontrolü yapılıyor ve kaynak dosya güncelleniyor…")
        lifecycleScope.launch {
            val tableToReload = selectedTable
            val result = runCatching { withContext(Dispatchers.IO) { workspace.saveToSource() } }
            if (result.isSuccess) {
                Toast.makeText(this@SqliteStudioActivity, R.string.sqlite_saved, Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this@SqliteStudioActivity, R.string.sqlite_save_failed, Toast.LENGTH_LONG).show()
                binding.statusText.text = result.exceptionOrNull()?.message ?: getString(R.string.sqlite_save_failed)
            }
            setBusy(false)
            refreshSaveState()
            if (result.isSuccess && tableToReload != null) selectTable(tableToReload)
        }
    }

    private fun handleExit() {
        if (!workspace.dirty) {
            finish()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Kaydedilmemiş değişiklikler")
            .setMessage("Çalışma kopyasındaki değişiklikler kaydedilmedi. Çıkarsan orijinal dosya değişmeden kalır.")
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.discard_changes) { _, _ -> finish() }
            .show()
    }

    private fun setBusy(value: Boolean, message: String? = null) {
        busy = value
        binding.openButton.isEnabled = !value
        binding.tableInput.isEnabled = !value
        if (!message.isNullOrBlank()) binding.statusText.text = message
        refreshSaveState()
    }

    private fun refreshSaveState() {
        binding.saveButton.isEnabled = !busy && workspace.dirty
    }
}
