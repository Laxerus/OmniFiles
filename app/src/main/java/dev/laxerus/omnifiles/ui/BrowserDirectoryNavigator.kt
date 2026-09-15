package dev.laxerus.omnifiles.ui

import java.io.File

interface BrowserDirectoryNavigator {
    fun navigateToDirectory(directory: File): Boolean
}
