package dev.laxerus.omnifiles.adb

data class AdbDirectoryListing(
    val entries: List<AdbRemoteEntry>,
    val skippedUnsafeEntries: Int
)
