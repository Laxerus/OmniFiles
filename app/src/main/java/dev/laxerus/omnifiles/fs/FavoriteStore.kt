package dev.laxerus.omnifiles.fs

import android.content.Context
import java.io.File

class FavoriteStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun list(sharedRoot: File): List<File> {
        val root = FilePathPolicy.canonical(sharedRoot)
        val stored = preferences.getStringSet(KEY_PATHS, emptySet()).orEmpty().toSet()
        val valid = stored.mapNotNull { path ->
            runCatching { FilePathPolicy.requireInside(File(path), root) }.getOrNull()
                ?.takeIf { it.exists() && it.isDirectory }
                ?.canonicalFile
        }.distinctBy(File::getCanonicalPath)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.ifBlank { it.path } })

        val validPaths = valid.mapTo(linkedSetOf()) { it.canonicalPath }
        if (validPaths != stored) preferences.edit().putStringSet(KEY_PATHS, validPaths).apply()
        return valid
    }

    fun isFavorite(directory: File, sharedRoot: File): Boolean {
        val safe = FilePathPolicy.requireInside(directory, sharedRoot)
        if (!safe.isDirectory) return false
        return safe.canonicalPath in preferences.getStringSet(KEY_PATHS, emptySet()).orEmpty()
    }

    fun toggle(directory: File, sharedRoot: File): Boolean {
        val safe = FilePathPolicy.requireInside(directory, sharedRoot)
        require(safe.exists() && safe.isDirectory) { "Yalnız mevcut klasörler favoriye eklenebilir" }
        val paths = preferences.getStringSet(KEY_PATHS, emptySet()).orEmpty().toMutableSet()
        val path = safe.canonicalPath
        val nowFavorite = if (path in paths) {
            paths.remove(path)
            false
        } else {
            paths.add(path)
            true
        }
        preferences.edit().putStringSet(KEY_PATHS, paths).apply()
        return nowFavorite
    }

    companion object {
        private const val PREFS_NAME = "omnifiles_favorites"
        private const val KEY_PATHS = "folder_paths"
    }
}
