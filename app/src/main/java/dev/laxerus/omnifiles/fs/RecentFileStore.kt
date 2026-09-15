package dev.laxerus.omnifiles.fs

import android.content.Context
import java.io.File

class RecentFileStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun list(sharedRoot: File): List<File> {
        val stored = readRawPaths()
        val valid = RecentFileHistoryPolicy.normalize(stored, sharedRoot, MAX_RECENT_FILES)
        val validPaths = valid.map(File::getCanonicalPath)
        if (validPaths != stored) persist(validPaths)
        return valid
    }

    fun record(file: File, sharedRoot: File): List<File> {
        val updated = RecentFileHistoryPolicy.push(
            existingPaths = readRawPaths(),
            file = file,
            sharedRoot = sharedRoot,
            maxEntries = MAX_RECENT_FILES
        )
        persist(updated.map(File::getCanonicalPath))
        return updated
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun readRawPaths(): List<String> {
        val count = preferences.getInt(KEY_COUNT, 0).coerceIn(0, MAX_RECENT_FILES)
        return buildList(count) {
            repeat(count) { index ->
                preferences.getString(keyFor(index), null)
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
    }

    private fun persist(paths: List<String>) {
        preferences.edit().apply {
            clear()
            val limited = paths.take(MAX_RECENT_FILES)
            putInt(KEY_COUNT, limited.size)
            limited.forEachIndexed { index, path -> putString(keyFor(index), path) }
            apply()
        }
    }

    private fun keyFor(index: Int): String = "${KEY_PATH_PREFIX}_$index"

    companion object {
        const val MAX_RECENT_FILES = RecentFileHistoryPolicy.DEFAULT_MAX_ENTRIES
        private const val PREFS_NAME = "omnifiles_recent_files"
        private const val KEY_COUNT = "count"
        private const val KEY_PATH_PREFIX = "path"
    }
}
