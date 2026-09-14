package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class JunkCleanerTest {
    @Test fun findsOnlyConservativeJunkCandidates() {
        val root = Files.createTempDirectory("omnifiles-junk-").toFile()
        val now = System.currentTimeMillis()
        val old = now - 40L * 24L * 60L * 60L * 1000L
        try {
            val staleTemp = root.resolve("leftover.tmp").apply { writeText("temp"); setLastModified(old) }
            val partial = root.resolve("movie.crdownload").apply { writeText("partial"); setLastModified(old) }
            val metadata = root.resolve(".DS_Store").apply { writeText("meta") }
            val recentTemp = root.resolve("recent.tmp").apply { writeText("keep") }
            val photo = root.resolve("photo.jpg").apply { writeText("photo"); setLastModified(old) }
            val emptyLog = root.resolve("old.log").apply { writeText(""); setLastModified(old) }
            val cache = root.resolve("cache").apply { mkdirs(); setLastModified(old) }
            val android = root.resolve("Android/cache").apply { mkdirs() }
            android.resolve("hidden.tmp").apply { writeText("skip"); setLastModified(old) }

            val result = JunkCleaner.scan(root, now = now)
            val paths = result.candidates.map { it.path }.toSet()

            assertTrue(staleTemp.canonicalPath in paths)
            assertTrue(partial.canonicalPath in paths)
            assertTrue(metadata.canonicalPath in paths)
            assertTrue(emptyLog.canonicalPath in paths)
            assertTrue(cache.canonicalPath in paths)
            assertFalse(recentTemp.canonicalPath in paths)
            assertFalse(photo.canonicalPath in paths)
            assertFalse(android.resolve("hidden.tmp").canonicalPath in paths)
            assertEquals(5, result.candidates.size)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun respectsCandidateLimit() {
        val root = Files.createTempDirectory("omnifiles-junk-limit-").toFile()
        val now = System.currentTimeMillis()
        val old = now - 10L * 24L * 60L * 60L * 1000L
        try {
            repeat(5) { index -> root.resolve("$index.tmp").apply { writeText("x"); setLastModified(old) } }
            val result = JunkCleaner.scan(root, now = now, maxCandidates = 2)
            assertEquals(2, result.candidates.size)
            assertTrue(result.truncated)
        } finally {
            root.deleteRecursively()
        }
    }
}
