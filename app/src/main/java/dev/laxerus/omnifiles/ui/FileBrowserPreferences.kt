package dev.laxerus.omnifiles.ui

import android.content.Context

class FileBrowserPreferences(context: Context) {
    data class State(
        val sortModeName: String,
        val showHidden: Boolean,
        val nameDescending: Boolean,
        val dateDescending: Boolean,
        val sizeDescending: Boolean,
    ) {
        fun descendingFor(sortModeName: String): Boolean = when (normalizeSortMode(sortModeName)) {
            "DATE" -> dateDescending
            "SIZE" -> sizeDescending
            else -> nameDescending
        }
    }

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): State = State(
        sortModeName = normalizeSortMode(preferences.getString(KEY_SORT_MODE, DEFAULT_SORT_MODE)),
        showHidden = preferences.getBoolean(KEY_SHOW_HIDDEN, false),
        nameDescending = preferences.getBoolean(KEY_SORT_NAME_DESCENDING, false),
        dateDescending = preferences.getBoolean(KEY_SORT_DATE_DESCENDING, true),
        sizeDescending = preferences.getBoolean(KEY_SORT_SIZE_DESCENDING, true),
    )

    fun saveSortMode(sortModeName: String) {
        preferences.edit()
            .putString(KEY_SORT_MODE, normalizeSortMode(sortModeName))
            .apply()
    }

    fun saveSortDescending(sortModeName: String, descending: Boolean) {
        val key = when (normalizeSortMode(sortModeName)) {
            "DATE" -> KEY_SORT_DATE_DESCENDING
            "SIZE" -> KEY_SORT_SIZE_DESCENDING
            else -> KEY_SORT_NAME_DESCENDING
        }
        preferences.edit().putBoolean(key, descending).apply()
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
        private const val KEY_SORT_NAME_DESCENDING = "sort_name_descending"
        private const val KEY_SORT_DATE_DESCENDING = "sort_date_descending"
        private const val KEY_SORT_SIZE_DESCENDING = "sort_size_descending"
        private const val DEFAULT_SORT_MODE = "NAME"
        private val VALID_SORT_MODES = setOf("NAME", "DATE", "SIZE")

        internal fun normalizeSortMode(raw: String?): String =
            raw?.takeIf(VALID_SORT_MODES::contains) ?: DEFAULT_SORT_MODE
    }
}
