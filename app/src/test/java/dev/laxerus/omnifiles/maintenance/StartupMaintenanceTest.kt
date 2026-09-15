package dev.laxerus.omnifiles.maintenance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class StartupMaintenanceTest {
    @Test
    fun deletesOnlyStaleFilesFromManagedCacheRoots() {
        val cache = Files.createTempDirectory("omnifiles-cache").toFile()
        try {
            val now = 10L * 24L * 60L * 60L * 1000L
            val old = now - 8L * 24L * 60L * 60L * 1000L
            val recent = now - 60L * 60L * 1000L

            val preview = cache.resolve("adb-preview").apply { mkdirs() }
            val oldPreview = preview.resolve("old.bin").apply { writeText("old-preview"); setLastModified(old) }
            val recentPreview = preview.resolve("recent.bin").apply { writeText("recent-preview"); setLastModified(recent) }

            val sqlite = cache.resolve("sqlite-studio").apply { mkdirs() }
            val oldSqlite = sqlite.resolve("old.db").apply { writeText("old-db"); setLastModified(old) }
            val recentSqlite = sqlite.resolve("recent.db").apply { writeText("recent-db"); setLastModified(now - 2L * 24L * 60L * 60L * 1000L) }

            val unrelated = cache.resolve("unmanaged").apply { mkdirs() }.resolve("keep.bin").apply {
                writeText("keep")
                setLastModified(old)
            }

            val report = StartupMaintenance.prune(cache, nowMs = now)

            assertFalse(oldPreview.exists())
            assertTrue(recentPreview.exists())
            assertFalse(oldSqlite.exists())
            assertTrue(recentSqlite.exists())
            assertTrue(unrelated.exists())
            assertEquals(2, report.deletedFiles)
            assertFalse(report.truncated)
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun removesEmptyNestedDirectoriesAfterPruning() {
        val cache = Files.createTempDirectory("omnifiles-cache-empty").toFile()
        try {
            val now = 20L * 24L * 60L * 60L * 1000L
            val nested = cache.resolve("adb-preview/a/b").apply { mkdirs() }
            val stale = nested.resolve("preview.tmp").apply {
                writeText("preview")
                setLastModified(now - 3L * 24L * 60L * 60L * 1000L)
            }

            val report = StartupMaintenance.prune(cache, nowMs = now)

            assertFalse(stale.exists())
            assertFalse(nested.exists())
            assertTrue(report.deletedDirectories >= 2)
        } finally {
            cache.deleteRecursively()
        }
    }

    @Test
    fun respectsEntryLimit() {
        val cache = Files.createTempDirectory("omnifiles-cache-limit").toFile()
        try {
            val preview = cache.resolve("adb-preview").apply { mkdirs() }
            repeat(10) { preview.resolve("$it.tmp").writeText("x") }

            val report = StartupMaintenance.prune(cache, maxEntries = 2)

            assertTrue(report.truncated)
            assertTrue(report.scannedEntries <= 2)
        } finally {
            cache.deleteRecursively()
        }
    }
}
