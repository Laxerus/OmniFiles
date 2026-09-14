package dev.laxerus.omnifiles.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemotePathPolicyTest {
    @Test fun normalizesRepeatedSlashes() {
        assertEquals("/sdcard/Android/data", RemotePathPolicy.normalizeAbsolute("/sdcard//Android/data"))
    }

    @Test fun rejectsTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            RemotePathPolicy.normalizeAbsolute("/sdcard/../data")
        }
    }

    @Test fun buildsSafeChild() {
        assertEquals("/sdcard/a b.txt", RemotePathPolicy.child("/sdcard", "a b.txt"))
    }

    @Test fun rejectsSlashInChildName() {
        assertThrows(IllegalArgumentException::class.java) {
            RemotePathPolicy.child("/sdcard", "a/b")
        }
    }
}
