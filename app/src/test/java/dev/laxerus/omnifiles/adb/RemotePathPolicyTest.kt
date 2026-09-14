package dev.laxerus.omnifiles.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RemotePathPolicyTest {
    @Test fun normalizesRepeatedSeparatorsAndPreservesUnicodeNames() {
        assertEquals("/Android/data/Oyun 🎮", RemotePathPolicy.normalizeAbsolute("//Android///data/Oyun 🎮"))
        assertEquals("/Android/data/Oyun 🎮", RemotePathPolicy.child("/Android/data", "Oyun 🎮"))
    }

    @Test fun rejectsTraversalAndControlCharacters() {
        expectIllegal { RemotePathPolicy.normalizeAbsolute("Android/data") }
        expectIllegal { RemotePathPolicy.normalizeAbsolute("/Android/../data") }
        expectIllegal { RemotePathPolicy.normalizeAbsolute("/Android/bad\nname") }
        expectIllegal { RemotePathPolicy.child("/Android/data", "bad\tname") }
        expectIllegal { RemotePathPolicy.child("/Android/data", "bad/name") }
        expectIllegal { RemotePathPolicy.child("/Android/data", "a".repeat(256)) }
    }

    @Test fun childNameSafetyIsExplicitAndBounded() {
        assertTrue(RemotePathPolicy.isSafeChildName("save-slot_01.dat"))
        assertTrue(RemotePathPolicy.isSafeChildName("日本語.dat"))
        assertFalse(RemotePathPolicy.isSafeChildName("."))
        assertFalse(RemotePathPolicy.isSafeChildName(".."))
        assertFalse(RemotePathPolicy.isSafeChildName("line\rbreak"))
        assertFalse(RemotePathPolicy.isSafeChildName("\u0000hidden"))
    }

    @Test fun parentNeverEscapesNormalizedRoot() {
        assertNull(RemotePathPolicy.parent("/"))
        assertEquals("/", RemotePathPolicy.parent("/Android"))
        assertEquals("/Android/data", RemotePathPolicy.parent("/Android/data/pkg"))
    }

    private fun expectIllegal(block: () -> Unit) {
        try {
            block()
            fail("IllegalArgumentException bekleniyordu")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
