package dev.laxerus.omnifiles.ui

import java.io.File

enum class BrowserSortMode { NAME, DATE, SIZE }

object FileBrowserSorter {
    fun compare(
        left: File,
        right: File,
        mode: BrowserSortMode,
        descending: Boolean,
    ): Int {
        if (left.isDirectory != right.isDirectory) return if (left.isDirectory) -1 else 1

        val primary = when (mode) {
            BrowserSortMode.NAME -> NaturalNameComparator.compare(left.name, right.name)
            BrowserSortMode.DATE -> left.lastModified().compareTo(right.lastModified())
            BrowserSortMode.SIZE -> {
                if (left.isDirectory && right.isDirectory) 0
                else left.length().compareTo(right.length())
            }
        }
        if (primary != 0) return if (descending) -primary else primary

        return NaturalNameComparator.compare(left.name, right.name)
    }
}
