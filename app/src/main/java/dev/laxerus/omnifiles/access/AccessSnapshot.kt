package dev.laxerus.omnifiles.access

import android.content.Context
import android.os.Build
import dev.laxerus.omnifiles.adb.AdbEndpointStore
import java.io.File

data class AccessSnapshot(
    val sharedStorage: Boolean,
    val adbEndpointConfigured: Boolean,
    val rootBinaryPresent: Boolean,
    val sdk: Int
) {
    companion object {
        fun read(context: Context): AccessSnapshot = AccessSnapshot(
            sharedStorage = StorageAccessController.hasSharedStorageAccess(context),
            adbEndpointConfigured = AdbEndpointStore(context).load() != null,
            rootBinaryPresent = rootBinaryExists(),
            sdk = Build.VERSION.SDK_INT
        )

        private fun rootBinaryExists(): Boolean {
            val candidates = arrayOf(
                "/system/bin/su",
                "/system/xbin/su",
                "/sbin/su",
                "/data/adb/magisk/busybox"
            )
            return candidates.any { File(it).exists() }
        }
    }
}
