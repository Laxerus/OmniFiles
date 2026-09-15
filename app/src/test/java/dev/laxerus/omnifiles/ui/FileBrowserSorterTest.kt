package dev.laxerus.omnifiles.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class FileBrowserSorterTest {
    @Test
    fun directoriesStayBeforeFilesInBothDirections() {
        val root = createTempDirectory("omnifiles-sort-dir-").toFile()
        try {
            val directory = root.resolve("z-dir").apply { mkdirs() }
            val file = root.resolve("a-file.txt").apply { writeText("x") }

            assertTrue(FileBrowserSorter.compare(directory, file, BrowserSortMode.NAME, false) < 0)
            assertTrue(FileBrowserSorter.compare(directory, file, BrowserSortMode.NAME, true) < 0)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun nameDirectionUsesNaturalOrder() {
        val root = createTempDirectory("omnifiles-sort-name-").toFile()
        try {
            val file2 = root.resolve("file2.txt").apply { writeText("2") }
            val file10 = root.resolve("file10.txt").apply { writeText("10") }

            assertTrue(FileBrowserSorter.compare(file2, file10, BrowserSortMode.NAME, false) < 0)
            assertTrue(FileBrowserSorter.compare(file2, file10, BrowserSortMode.NAME, true) > 0)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun dateAndSizeDirectionsReversePrimaryOrder() {
        val root = createTempDirectory("omnifiles-sort-meta-").toFile()
        try {
            val smallOld = root.resolve("small-old.bin").apply {
                writeBytes(ByteArray(1))
                setLastModified(1_000L)
            }
            val largeNew = root.resolve("large-new.bin").apply {
                writeBytes(ByteArray(16))
                setLastModified(2_000L)
            }

            assertTrue(FileBrowserSorter.compare(smallOld, largeNew, BrowserSortMode.DATE, false) < 0)
            assertTrue(FileBrowserSorter.compare(smallOld, largeNew, BrowserSortMode.DATE, true) > 0)
            assertTrue(FileBrowserSorter.compare(smallOld, largeNew, BrowserSortMode.SIZE, false) < 0)
            assertTrue(FileBrowserSorter.compare(smallOld, largeNew, BrowserSortMode.SIZE, true) > 0)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun metadataTiesUseStableNaturalNameOrder() {
        val root = createTempDirectory("omnifiles-sort-tie-").toFile()
        try {
            val file2 = root.resolve("file2.txt").apply {
                writeText("x")
                setLastModified(5_000L)
            }
            val file10 = root.resolve("file10.txt").apply {
                writeText("x")
                setLastModified(5_000L)
            }

            assertEquals(-1, FileBrowserSorter.compare(file2, file10, BrowserSortMode.DATE, true).coerceIn(-1, 1))
        } finally {
            root.deleteRecursively()
        }
    }
}
