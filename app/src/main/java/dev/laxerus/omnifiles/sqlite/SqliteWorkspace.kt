package dev.laxerus.omnifiles.sqlite

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class SqliteWorkspace(private val context: Context) : Closeable {
    private data class FileFingerprint(val size: Long, val sha256: String)

    private val root = File(context.cacheDir, "sqlite-studio").apply { mkdirs() }
    private var db: SQLiteDatabase? = null
    private var sourceUri: Uri? = null
    private var workspaceFile: File? = null
    private var backupFile: File? = null

    var dirty: Boolean = false
        private set

    fun open(uri: Uri): List<SqliteTableInfo> {
        closeDatabaseOnly()
        cleanupWorkspaceFiles()
        sourceUri = null
        dirty = false

        val token = UUID.randomUUID().toString()
        val work = File(root, "$token-work.db")
        val backup = File(root, "$token-original.db")
        copyUriToFile(uri, work)
        check(work.length() > 0L) { "Veritabanı dosyası boş" }
        work.copyTo(backup, overwrite = false)

        workspaceFile = work
        backupFile = backup
        sourceUri = uri
        try {
            db = openConfiguredDatabase(work)
            check(integrityOk()) { "Veritabanı bütünlük kontrolünden geçmedi" }
            return listTables()
        } catch (error: Throwable) {
            closeDatabaseOnly()
            cleanupWorkspaceFiles()
            sourceUri = null
            throw error
        }
    }

    fun listTables(): List<SqliteTableInfo> {
        val database = requireDb()
        return database.rawQuery(
            "SELECT name, sql FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name COLLATE NOCASE",
            null
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val sql = cursor.getString(1).orEmpty()
                    add(SqliteTableInfo(name, sql.contains("WITHOUT ROWID", ignoreCase = true)))
                }
            }
        }
    }

    fun loadRows(table: SqliteTableInfo, limit: Int = 200): List<SqliteRow> {
        require(limit in 1..1000)
        val database = requireDb()
        val sql = if (table.withoutRowId) {
            "SELECT * FROM ${quoteIdentifier(table.name)} LIMIT $limit"
        } else {
            "SELECT rowid, * FROM ${quoteIdentifier(table.name)} LIMIT $limit"
        }
        return database.rawQuery(sql, null).use { cursor ->
            val valueStart = if (table.withoutRowId) 0 else 1
            buildList {
                while (cursor.moveToNext()) {
                    val rowId = if (table.withoutRowId) null else cursor.getLong(0)
                    val cells = (valueStart until cursor.columnCount).map { index -> cursorToCell(cursor, index) }
                    add(SqliteRow(rowId = rowId, cells = cells))
                }
            }
        }
    }

    fun updateCell(table: SqliteTableInfo, row: SqliteRow, cell: SqliteCell, newValue: Any?) {
        require(!table.withoutRowId && row.rowId != null) { "Bu tabloda güvenli rowid düzenleme yok" }
        require(cell.kind != SqliteCellKind.BLOB) { "BLOB hücreleri salt okunur" }
        val database = requireDb()
        val sql = "UPDATE ${quoteIdentifier(table.name)} SET ${quoteIdentifier(cell.column)} = ? WHERE rowid = ?"
        database.beginTransaction()
        try {
            database.execSQL(sql, arrayOf(newValue, row.rowId))
            database.setTransactionSuccessful()
            dirty = true
        } finally {
            database.endTransaction()
        }
    }

    fun integrityOk(): Boolean = integrityOk(requireDb())

    fun saveToSource() {
        check(dirty) { "Kaydedilecek değişiklik yok" }
        check(integrityOk()) { "Bütünlük kontrolü başarısız" }
        val uri = sourceUri ?: error("Kaynak URI yok")
        val work = workspaceFile ?: error("Çalışma dosyası yok")
        val backup = backupFile ?: error("Yedek dosya yok")

        closeDatabaseOnly()
        val expectedWork = fingerprint(work)
        var failure: Throwable? = runCatching {
            writeFileToUri(work, uri)
            verifySource(uri, expectedWork)
        }.exceptionOrNull()

        if (failure != null) {
            val restoreFailure = runCatching {
                val expectedBackup = fingerprint(backup)
                writeFileToUri(backup, uri)
                verifySource(uri, expectedBackup)
            }.exceptionOrNull()
            if (restoreFailure != null) failure.addSuppressed(restoreFailure)
        }

        val reopenFailure = runCatching {
            db = openConfiguredDatabase(work)
        }.exceptionOrNull()
        if (reopenFailure != null) {
            if (failure != null) failure.addSuppressed(reopenFailure) else failure = reopenFailure
        }
        failure?.let { throw it }

        work.copyTo(backup, overwrite = true)
        dirty = false
    }

    fun discardChanges(): List<SqliteTableInfo> {
        val backup = backupFile ?: error("Yedek dosya yok")
        val work = workspaceFile ?: error("Çalışma dosyası yok")
        closeDatabaseOnly()
        backup.copyTo(work, overwrite = true)
        db = openConfiguredDatabase(work)
        dirty = false
        return listTables()
    }

    override fun close() {
        closeDatabaseOnly()
        cleanupWorkspaceFiles()
        sourceUri = null
        dirty = false
    }

    private fun cursorToCell(cursor: Cursor, index: Int): SqliteCell {
        val column = cursor.getColumnName(index)
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> SqliteCell(column, SqliteCellKind.NULL, null, "NULL")
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index).let { SqliteCell(column, SqliteCellKind.INTEGER, it, it.toString()) }
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index).let { SqliteCell(column, SqliteCellKind.FLOAT, it, it.toString()) }
            Cursor.FIELD_TYPE_BLOB -> {
                val bytes = cursor.getBlob(index)
                SqliteCell(column, SqliteCellKind.BLOB, null, "<BLOB ${bytes.size} bayt>")
            }
            else -> {
                val value = cursor.getString(index).orEmpty()
                SqliteCell(column, SqliteCellKind.TEXT, value, value.replace("\n", "↵"))
            }
        }
    }

    private fun openConfiguredDatabase(file: File): SQLiteDatabase {
        val database = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            database.rawQuery("PRAGMA journal_mode=DELETE", null).use { cursor ->
                check(cursor.moveToFirst()) { "SQLite journal modu ayarlanamadı" }
                check(cursor.getString(0).equals("delete", ignoreCase = true)) {
                    "SQLite çalışma kopyası güvenli DELETE journal moduna geçirilemedi"
                }
            }
            database.execSQL("PRAGMA synchronous=FULL")
            return database
        } catch (error: Throwable) {
            runCatching { database.close() }
            throw error
        }
    }

    private fun verifySource(uri: Uri, expected: FileFingerprint) {
        val verifyFile = File(root, "${UUID.randomUUID()}-verify.db")
        try {
            copyUriToFile(uri, verifyFile)
            val actual = fingerprint(verifyFile)
            check(actual == expected) { "Kaynak dosyaya yazılan baytlar doğrulanamadı" }

            val verifyDb = SQLiteDatabase.openDatabase(verifyFile.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                check(integrityOk(verifyDb)) { "Kaydedilen veritabanı bütünlük kontrolünden geçmedi" }
            } finally {
                verifyDb.close()
            }
        } finally {
            verifyFile.delete()
        }
    }

    private fun integrityOk(database: SQLiteDatabase): Boolean =
        database.rawQuery("PRAGMA integrity_check", null).use { cursor ->
            cursor.moveToFirst() && cursor.getString(0).equals("ok", ignoreCase = true)
        }

    private fun fingerprint(file: File): FileFingerprint {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        val hash = digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        return FileFingerprint(file.length(), hash)
    }

    private fun copyUriToFile(uri: Uri, destination: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Veritabanı dosyası açılamadı")
    }

    private fun writeFileToUri(file: File, uri: Uri) {
        context.contentResolver.openOutputStream(uri, "w")?.use { output ->
            file.inputStream().use { input -> input.copyTo(output) }
            output.flush()
        } ?: error("Kaynak dosya yazma için açılamadı")
    }

    private fun closeDatabaseOnly() {
        runCatching { db?.close() }
        db = null
    }

    private fun cleanupWorkspaceFiles() {
        workspaceFile?.delete()
        backupFile?.delete()
        workspaceFile = null
        backupFile = null
    }

    private fun requireDb(): SQLiteDatabase = db ?: error("Veritabanı açık değil")

    companion object {
        fun quoteIdentifier(value: String): String = "\"${value.replace("\"", "\"\"")}\""
    }
}
