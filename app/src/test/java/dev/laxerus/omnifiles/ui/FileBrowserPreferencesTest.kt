package dev.laxerus.omnifiles.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileBrowserPreferencesTest {
    @Test
    fun acceptsKnownSortModes() {
        assertEquals("NAME", FileBrowserPreferences.normalizeSortMode("NAME"))
        assertEquals("DATE", FileBrowserPreferences.normalizeSortMode("DATE"))
        assertEquals("SIZE", FileBrowserPreferences.normalizeSortMode("SIZE"))
    }

    @Test
    fun fallsBackToNameForMissingOrUnknownSortMode() {
        assertEquals("NAME", FileBrowserPreferences.normalizeSortMode(null))
        assertEquals("NAME", FileBrowserPreferences.normalizeSortMode("RANDOM"))
        assertEquals("NAME", FileBrowserPreferences.normalizeSortMode("date"))
    }

    @Test
    fun descendingStateIsResolvedPerSortMode() {
        val state = FileBrowserPreferences.State(
            sortModeName = "NAME",
            showHidden = false,
            nameDescending = false,
            dateDescending = true,
            sizeDescending = true,
        )

        assertFalse(state.descendingFor("NAME"))
        assertTrue(state.descendingFor("DATE"))
        assertTrue(state.descendingFor("SIZE"))
        assertFalse(state.descendingFor("UNKNOWN"))
    }
}
