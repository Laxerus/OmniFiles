package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createTempFile

class FilePathPolicyTest {
    @Test fun acceptsChild() {
        val root = createTempDirectory("omnifiles-root-").toFile()
        try {
            val child = File(root, "folder/file.txt")
            child.parentFile!!.mkdirs()
            child.writeText("x")
            assertEquals(child.canonicalPath, FilePathPolicy.requireInside(child, root).canonicalPath)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun rejectsEscapedPath() {
        val root = createTempDirectory("omnifiles-root-").toFile()
        val outside = createTempFile("omnifiles-outside-").toFile()
        try {
            assertThrows(IllegalArgumentException::class.java) {
                FilePathPolicy.requireInside(outside, root)
            }
        } finally {
            root.deleteRecursively()
            outside.delete()
        }
    }

    @Test fun sanitizesSimpleChildName() {
        assertEquals("save.db", FilePathPolicy.sanitizeChildName("  save.db  "))
    }

    @Test fun rejectsTraversalNames() {
        listOf(".", "..", "../save", "folder/save", "folder\\save").forEach { name ->
            assertThrows(IllegalArgumentException::class.java) {
                FilePathPolicy.sanitizeChildName(name)
            }
        }
    }

    @Test fun protectsCriticalAndroidDirectoryItself() {
        val root = createTempDirectory("omnifiles-root-").toFile()
        try {
            val data = File(root, "Android/data")
            data.mkdirs()
            assertThrows(IllegalArgumentException::class.java) {
                FilePathPolicy.requireMutableTarget(data, root)
            }
        } finally {
            root.deleteRecursively()
        }
    }
}
