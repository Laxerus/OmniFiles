package dev.laxerus.omnifiles.fs

import java.io.File

object BrowserHighlightPolicy {
    fun findIndex(rawPath: String?, entries: List<File>): Int? {
        if (rawPath.isNullOrBlank() || entries.isEmpty()) return null
        val target = runCatching {
            val absolute = File(rawPath).absoluteFile
            val canonical = absolute.canonicalFile
            require(absolute.path == canonical.path) { "Dolaylı hedef vurgulanamaz" }
            require(canonical.exists()) { "Vurgu hedefi artık mevcut değil" }
            canonical.path
        }.getOrNull() ?: return null

        return entries.indexOfFirst { entry ->
            runCatching {
                val absolute = entry.absoluteFile
                val canonical = absolute.canonicalFile
                absolute.path == canonical.path && canonical.exists() && canonical.path == target
            }.getOrDefault(false)
        }.takeIf { it >= 0 }
    }
}
