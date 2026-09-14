package dev.laxerus.omnifiles.access

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

object SystemSettingsNavigator {
    enum class Destination {
        ALL_FILES_ACCESS,
        DEVELOPER_OPTIONS,
        WIFI,
        APP_DETAILS
    }

    fun open(activity: Activity, destination: Destination): Boolean {
        val candidates = when (destination) {
            Destination.ALL_FILES_ACCESS -> allFilesAccessIntents(activity)
            Destination.DEVELOPER_OPTIONS -> listOf(
                Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            Destination.WIFI -> listOf(
                Intent(Settings.ACTION_WIFI_SETTINGS),
                Intent(Settings.ACTION_WIRELESS_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
            Destination.APP_DETAILS -> listOf(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                ),
                Intent(Settings.ACTION_APPLICATION_SETTINGS),
                Intent(Settings.ACTION_SETTINGS)
            )
        }

        candidates.forEach { intent ->
            if (runCatching { activity.startActivity(intent) }.isSuccess) return true
        }
        return false
    }

    private fun allFilesAccessIntents(activity: Activity): List<Intent> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return listOf(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${activity.packageName}")
                ),
                Intent(Settings.ACTION_SETTINGS)
            )
        }
        return listOf(
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${activity.packageName}")
            ),
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${activity.packageName}")
            ),
            Intent(Settings.ACTION_SETTINGS)
        )
    }
}
