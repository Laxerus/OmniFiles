package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class RecentFileHistoryPolicyTest {
    @Test fun newestFileMovesToFrontAndDuplicatesCollapse() {
        val root = createTempDirectory("omnifiles-recent-files-").toFile()
        try {
            val first = root.resolve("first.txt").apply { writeText("first") }
            val second = root.resolve("second.txt").apply { writeText("second") }

            val result = RecentFileHistoryPolicy.push(
                existingPaths = listOf(first.canonicalPath, second.canonicalPath, first.canonicalPath),
                file = second,
                sharedRoot = root
            )

            assertEquals(listOf(second.canonicalPath, first.canonicalPath), result.map { it.canonicalPath })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun staleOutsideAndDirectoryEntriesAreRemoved() {
        val root = createTempDirectory("omnifiles-recent-files-root-").toFile()
        val outside = createTempDirectory("omnifiles-recent-files-outside-").toFile()
        try {
            val valid = root.resolve("valid.txt").apply { writeText("ok") }
            val stale = root.resolve("missing.txt")
            val directory = root.resolve("folder").apply { mkdirs() }
            val outsideFile = outside.resolve("foreign.txt").apply { writeText("outside") }

            val result = RecentFileHistoryPolicy.normalize(
                listOf(stale.canonicalPath, directory.canonicalPath, outsideFile.canonicalPath, valid.canonicalPath),
                root
            )

            assertEquals(listOf(valid.canonicalPath), result.map { it.canonicalPath })
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test fun historyIsCappedWithoutReorderingNewestEntries() {
        val root = createTempDirectory("omnifiles-recent-files-cap-").toFile()
        try {
            val files = (0 until 20).map { index ->
                root.resolve("file-$index.txt").apply { writeText(index.toString()) }
            }

            val result = RecentFileHistoryPolicy.normalize(
                files.map { it.canonicalPath },
                root,
                maxEntries = 5
            )

            assertEquals(5, result.size)
            assertEquals(files.take(5).map { it.canonicalPath }, result.map { it.canonicalPath })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun directoryCannotBeRecordedAsRecentFile() {
        val root = createTempDirectory("omnifiles-recent-files-dir-").toFile()
        try {
            val directory = root.resolve("folder").apply { mkdirs() }

            val result = runCatching {
                RecentFileHistoryPolicy.push(emptyList(), directory, root)
            }

            assertTrue(result.isFailure)
        } finally {
            root.deleteRecursively()
        }
    }
}
