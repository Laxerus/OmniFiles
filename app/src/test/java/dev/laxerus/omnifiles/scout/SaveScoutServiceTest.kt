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

    @Test fun recognizesCommonSaveDirectoryNames() {
        assertTrue(SaveScoutService.isLikelySaveDirectoryName("SaveGames"))
        assertTrue(SaveScoutService.isLikelySaveDirectoryName("save_data"))
        assertTrue(SaveScoutService.isLikelySaveDirectoryName("UserData01"))
        assertTrue(SaveScoutService.isLikelySaveDirectoryName("UE4Game"))
        assertTrue(SaveScoutService.isLikelySaveDirectoryName("Worlds"))
    }

    @Test fun ignoresGenericContentDirectories() {
        assertFalse(SaveScoutService.isLikelySaveDirectoryName("cache"))
        assertFalse(SaveScoutService.isLikelySaveDirectoryName("textures"))
        assertFalse(SaveScoutService.isLikelySaveDirectoryName("downloads"))
    }
}
