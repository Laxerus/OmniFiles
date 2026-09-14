package dev.laxerus.omnifiles.fs

import java.io.File

object BrowserStartPathPolicy {
    fun resolve(rawPath: String?, sharedRoot: File): File {
        val root = FilePathPolicy.canonical(sharedRoot)
        if (rawPath.isNullOrBlank()) return root

        return runCatching {
            FilePathPolicy.requireDirectEntry(File(rawPath), root)
        }.getOrNull()
            ?.takeIf { it.exists() && it.isDirectory }
            ?: root
    }
}
