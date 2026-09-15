package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.io.path.createTempDirectory

class RecentFolderHistoryPolicyTest {
    @Test fun newestEntryMovesToFrontAndDuplicatesCollapse() {
        val root = createTempDirectory("omnifiles-recents-").toFile()
        try {
            val first = root.resolve("first").apply { mkdirs() }
            val second = root.resolve("second").apply { mkdirs() }

            val result = RecentFolderHistoryPolicy.push(
                existingPaths = listOf(first.canonicalPath, second.canonicalPath, first.canonicalPath),
                directory = second,
                sharedRoot = root
            )

            assertEquals(listOf(second.canonicalPath, first.canonicalPath), result.map { it.canonicalPath })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun staleOutsideAndRootEntriesAreRemoved() {
        val root = createTempDirectory("omnifiles-recents-root-").toFile()
        val outside = createTempDirectory("omnifiles-recents-outside-").toFile()
        try {
            val valid = root.resolve("valid").apply { mkdirs() }
            val stale = root.resolve("missing")
            val outsideFolder = outside.resolve("foreign").apply { mkdirs() }

            val result = RecentFolderHistoryPolicy.normalize(
                listOf(root.canonicalPath, stale.canonicalPath, outsideFolder.canonicalPath, valid.canonicalPath),
                root
            )

            assertEquals(listOf(valid.canonicalPath), result.map { it.canonicalPath })
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test fun historyIsCappedWithoutReorderingNewestEntries() {
        val root = createTempDirectory("omnifiles-recents-cap-").toFile()
        try {
            val folders = (0 until 20).map { index ->
                root.resolve("folder-$index").apply { mkdirs() }
            }

            val result = RecentFolderHistoryPolicy.normalize(
                folders.map { it.canonicalPath },
                root,
                maxEntries = 5
            )

            assertEquals(5, result.size)
            assertEquals(folders.take(5).map { it.canonicalPath }, result.map { it.canonicalPath })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rootIsNeverAddedAsRecentFolder() {
        val root = createTempDirectory("omnifiles-recents-root-only-").toFile()
        try {
            val child = root.resolve("child").apply { mkdirs() }
            val existing = listOf(child.canonicalPath)

            val result = RecentFolderHistoryPolicy.push(existing, root, root)

            assertEquals(listOf(child.canonicalPath), result.map { it.canonicalPath })
            assertTrue(result.none { it.canonicalPath == root.canonicalPath })
        } finally {
            root.deleteRecursively()
        }
    }
}
