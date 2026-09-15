package dev.laxerus.omnifiles.ui

import android.content.Context

class FileBrowserPreferences(context: Context) {
    data class State(
        val sortModeName: String,
        val showHidden: Boolean,
    )

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): State = State(
        sortModeName = normalizeSortMode(preferences.getString(KEY_SORT_MODE, DEFAULT_SORT_MODE)),
        showHidden = preferences.getBoolean(KEY_SHOW_HIDDEN, false),
    )

    fun saveSortMode(sortModeName: String) {
        preferences.edit()
            .putString(KEY_SORT_MODE, normalizeSortMode(sortModeName))
            .apply()
    }

    fun saveShowHidden(showHidden: Boolean) {
        preferences.edit()
            .putBoolean(KEY_SHOW_HIDDEN, showHidden)
            .apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "omnifiles_file_browser"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_SHOW_HIDDEN = "show_hidden"
        private const val DEFAULT_SORT_MODE = "NAME"
        private val VALID_SORT_MODES = setOf("NAME", "DATE", "SIZE")

        internal fun normalizeSortMode(raw: String?): String =
            raw?.takeIf(VALID_SORT_MODES::contains) ?: DEFAULT_SORT_MODE
    }
}
