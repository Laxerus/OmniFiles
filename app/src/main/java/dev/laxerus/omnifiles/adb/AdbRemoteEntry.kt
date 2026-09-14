package dev.laxerus.omnifiles.adb

data class AdbRemoteEntry(
    val parentPath: String,
    val name: String,
    val mode: Int,
    val size: Long,
    val modifiedAtMillis: Long,
    val errorCode: Int?
) {
    val path: String get() = RemotePathPolicy.child(parentPath, name)
    val isDirectory: Boolean get() = mode and FILE_TYPE_MASK == FILE_TYPE_DIRECTORY
    val isSymlink: Boolean get() = mode and FILE_TYPE_MASK == FILE_TYPE_SYMLINK

    companion object {
        private const val FILE_TYPE_MASK = 0xF000
        private const val FILE_TYPE_DIRECTORY = 0x4000
        private const val FILE_TYPE_SYMLINK = 0xA000
    }
}
