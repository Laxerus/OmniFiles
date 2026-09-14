package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class BrowserHighlightPolicyTest {
    @Test fun findsExistingDirectEntryInVisibleList() {
        val root = createTempDirectory("omnifiles-highlight-").toFile()
        try {
            val first = File(root, "first.txt").apply { writeText("a") }
            val target = File(root, "target.txt").apply { writeText("b") }
            val third = File(root, "third.txt").apply { writeText("c") }

            assertEquals(1, BrowserHighlightPolicy.findIndex(target.path, listOf(first, target, third)))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsMissingAndUnlistedTargets() {
        val root = createTempDirectory("omnifiles-highlight-missing-").toFile()
        val outside = createTempDirectory("omnifiles-highlight-outside-").toFile()
        try {
            val visible = File(root, "visible.txt").apply { writeText("ok") }
            val unlisted = File(root, "other.txt").apply { writeText("no") }
            val outsideFile = File(outside, "outside.txt").apply { writeText("no") }

            assertNull(BrowserHighlightPolicy.findIndex(File(root, "missing.txt").path, listOf(visible)))
            assertNull(BrowserHighlightPolicy.findIndex(unlisted.path, listOf(visible)))
            assertNull(BrowserHighlightPolicy.findIndex(outsideFile.path, listOf(visible)))
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test fun rejectsSymlinkHighlightWhenSupported() {
        val root = createTempDirectory("omnifiles-highlight-link-").toFile()
        try {
            val real = File(root, "real.txt").apply { writeText("ok") }
            val link = File(root, "link.txt")
            val created = runCatching {
                Files.createSymbolicLink(link.toPath(), real.toPath())
                true
            }.getOrDefault(false)
            if (created) {
                assertNull(BrowserHighlightPolicy.findIndex(link.path, listOf(link, real)))
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
