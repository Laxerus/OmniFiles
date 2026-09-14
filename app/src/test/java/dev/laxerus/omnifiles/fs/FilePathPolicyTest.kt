package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class FilePathPolicyTest {
    @Test fun acceptsChild() {
        val root = createTempDir(prefix = "omnifiles-root-")
        val child = File(root, "folder/file.txt")
        child.parentFile!!.mkdirs()
        child.writeText("x")
        assertEquals(child.canonicalPath, FilePathPolicy.requireInside(child, root).canonicalPath)
        root.deleteRecursively()
    }

    @Test fun rejectsEscapedPath() {
        val root = createTempDir(prefix = "omnifiles-root-")
        val outside = createTempFile(prefix = "omnifiles-outside-")
        assertThrows(IllegalArgumentException::class.java) {
            FilePathPolicy.requireInside(outside, root)
        }
        root.deleteRecursively()
        outside.delete()
    }
}
