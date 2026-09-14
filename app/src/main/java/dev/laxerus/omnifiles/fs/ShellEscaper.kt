package dev.laxerus.omnifiles.fs

object ShellEscaper {
    fun quote(value: String): String = "'" + value.replace("'", "'\"'\"'") + "'"
}
