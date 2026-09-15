package dev.laxerus.omnifiles.access

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object StorageAccessController {
    const val LEGACY_STORAGE_REQUEST = 2201

    fun hasSharedStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val readGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            val writeGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
            readGranted && writeGranted
        }
    }

    fun requestSharedStorageAccess(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return SystemSettingsNavigator.open(
                activity,
                SystemSettingsNavigator.Destination.ALL_FILES_ACCESS,
            )
        }
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            ),
            LEGACY_STORAGE_REQUEST,
        )
        return true
    }

    fun sharedRoot() = Environment.getExternalStorageDirectory()
}
