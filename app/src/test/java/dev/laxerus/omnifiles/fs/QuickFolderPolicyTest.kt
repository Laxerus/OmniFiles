package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

class QuickFolderPolicyTest {
    @Test
    fun returnsOnlyExistingReadableQuickFoldersInStableOrder() {
        val root = createTempDirectory("omnifiles-quick-").toFile()
        try {
            Files.createDirectories(root.toPath().resolve("Pictures"))
            Files.createDirectories(root.toPath().resolve("Download"))
            Files.createDirectories(root.toPath().resolve("Android/media"))

            val entries = QuickFolderPolicy.available(root)

            assertEquals(
                listOf(
                    QuickFolderPolicy.Kind.DOWNLOADS,
                    QuickFolderPolicy.Kind.PICTURES,
                    QuickFolderPolicy.Kind.ANDROID_MEDIA,
                ),
                entries.map { it.kind },
            )
            assertTrue(entries.all { it.directory.exists() && it.directory.isDirectory })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun ignoresFilesThatOnlyLookLikeQuickFolders() {
        val root = createTempDirectory("omnifiles-quick-file-").toFile()
        try {
            root.resolve("Download").writeText("not a directory")
            Files.createDirectories(root.toPath().resolve("Documents"))

            val entries = QuickFolderPolicy.available(root)

            assertFalse(entries.any { it.kind == QuickFolderPolicy.Kind.DOWNLOADS })
            assertTrue(entries.any { it.kind == QuickFolderPolicy.Kind.DOCUMENTS })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsSymlinkedQuickFolderThatEscapesSharedRoot() {
        val root = createTempDirectory("omnifiles-quick-root-").toFile()
        val outside = createTempDirectory("omnifiles-quick-outside-").toFile()
        try {
            val link = root.toPath().resolve("Download")
            val symlinkCreated = runCatching {
                Files.createSymbolicLink(link, outside.toPath())
            }.isSuccess
            if (!symlinkCreated) return

            val entries = QuickFolderPolicy.available(root)

            assertFalse(entries.any { it.kind == QuickFolderPolicy.Kind.DOWNLOADS })
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }
}
