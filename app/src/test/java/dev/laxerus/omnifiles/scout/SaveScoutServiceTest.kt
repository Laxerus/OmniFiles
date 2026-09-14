package dev.laxerus.omnifiles.scout

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveScoutServiceTest {
    @Test fun acceptsNormalPackageNames() {
        assertTrue(SaveScoutService.isValidPackageName("com.example.game"))
        assertTrue(SaveScoutService.isValidPackageName("io.foo.game_2"))
    }

    @Test fun rejectsTraversalAndShellSyntax() {
        assertFalse(SaveScoutService.isValidPackageName("../data"))
        assertFalse(SaveScoutService.isValidPackageName("com.game;rm"))
        assertFalse(SaveScoutService.isValidPackageName("com game.app"))
    }
}
