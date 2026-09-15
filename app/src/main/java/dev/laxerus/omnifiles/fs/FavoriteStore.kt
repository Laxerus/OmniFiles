package dev.laxerus.omnifiles.fs

import android.content.Context
import java.io.File

class FavoriteStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun list(sharedRoot: File): List<File> {
        val root = FilePathPolicy.canonical(sharedRoot)
        val stored = preferences.getStringSet(KEY_PATHS, emptySet()).orEmpty().toSet()
        val valid = stored.asSequence()
            .mapNotNull { path ->
                runCatching { FilePathPolicy.requireInside(File(path), root) }.getOrNull()
                    ?.takeIf { it.exists() && it.isDirectory }
                    ?.canonicalFile
            }
            .distinctBy(File::getCanonicalPath)
            .take(MAX_FAVORITES)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.path } })
            .toList()

        val validPaths = valid.mapTo(linkedSetOf()) { it.canonicalPath }
        if (validPaths != stored) persist(validPaths)
        return valid
    }

    fun isFavorite(directory: File, sharedRoot: File): Boolean {
        val root = FilePathPolicy.canonical(sharedRoot)
        val safe = FilePathPolicy.requireInside(directory, root)
        if (!safe.exists() || !safe.isDirectory) return false
        val path = safe.canonicalPath
        return list(root).any { it.canonicalPath == path }
    }

    fun toggle(directory: File, sharedRoot: File): Boolean {
        val root = FilePathPolicy.canonical(sharedRoot)
        val safe = FilePathPolicy.requireInside(directory, root)
        require(safe.exists() && safe.isDirectory) { "Yalnız mevcut klasörler favoriye eklenebilir" }

        val paths = list(root).mapTo(linkedSetOf()) { it.canonicalPath }
        val path = safe.canonicalPath
        val nowFavorite = if (path in paths) {
            paths.remove(path)
            false
        } else {
            require(paths.size < MAX_FAVORITES) { "En fazla $MAX_FAVORITES klasör favoriye eklenebilir" }
            paths.add(path)
            true
        }
        persist(paths)
        return nowFavorite
    }

    private fun persist(paths: Set<String>) {
        preferences.edit().putStringSet(KEY_PATHS, paths.toSet()).apply()
    }

    companion object {
        const val MAX_FAVORITES = 64
        private const val PREFS_NAME = "omnifiles_favorites"
        private const val KEY_PATHS = "folder_paths"
    }
}
