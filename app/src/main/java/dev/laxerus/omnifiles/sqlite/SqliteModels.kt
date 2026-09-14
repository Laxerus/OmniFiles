package dev.laxerus.omnifiles.sqlite

enum class SqliteCellKind { NULL, INTEGER, FLOAT, TEXT, BLOB }

data class SqliteTableInfo(
    val name: String,
    val withoutRowId: Boolean
)

data class SqliteCell(
    val column: String,
    val kind: SqliteCellKind,
    val value: Any?,
    val displayValue: String
)

data class SqliteRow(
    val rowId: Long?,
    val cells: List<SqliteCell>
)
