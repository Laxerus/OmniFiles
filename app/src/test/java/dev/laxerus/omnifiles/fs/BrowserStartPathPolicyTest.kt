package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class BrowserStartPathPolicyTest {
    @Test fun acceptsExistingDirectDirectoryInsideRoot() {
        val root = createTempDirectory("omnifiles-browser-root-").toFile()
        try {
            val child = File(root, "Games").apply { mkdir() }

            val resolved = BrowserStartPathPolicy.resolve(child.path, root)

            assertEquals(child.canonicalPath, resolved.canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsFileMissingAndOutsideTargetsToRoot() {
        val root = createTempDirectory("omnifiles-browser-root-").toFile()
        val outside = createTempDirectory("omnifiles-browser-outside-").toFile()
        try {
            val file = File(root, "save.dat").apply { writeText("save") }
            val missing = File(root, "missing")

            assertEquals(root.canonicalPath, BrowserStartPathPolicy.resolve(file.path, root).canonicalPath)
            assertEquals(root.canonicalPath, BrowserStartPathPolicy.resolve(missing.path, root).canonicalPath)
            assertEquals(root.canonicalPath, BrowserStartPathPolicy.resolve(outside.path, root).canonicalPath)
            assertEquals(root.canonicalPath, BrowserStartPathPolicy.resolve(null, root).canonicalPath)
            assertEquals(root.canonicalPath, BrowserStartPathPolicy.resolve("   ", root).canonicalPath)
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test fun rejectsSymlinkDirectoryWhenSupported() {
        val root = createTempDirectory("omnifiles-browser-root-").toFile()
        val outside = createTempDirectory("omnifiles-browser-target-").toFile()
        try {
            val link = root.toPath().resolve("linked")
            val created = runCatching {
                java.nio.file.Files.createSymbolicLink(link, outside.toPath())
            }.isSuccess
            if (!created) return

            val resolved = BrowserStartPathPolicy.resolve(link.toString(), root)

            assertTrue(resolved.canonicalPath == root.canonicalPath)
        } finally {
            runCatching { java.nio.file.Files.deleteIfExists(root.toPath().resolve("linked")) }
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }
}
