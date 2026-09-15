package dev.laxerus.omnifiles

import android.app.Application
import com.google.android.material.color.DynamicColors
import dev.laxerus.omnifiles.maintenance.StartupMaintenance

class OmniFilesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        Thread(
            { runCatching { StartupMaintenance.prune(cacheDir) } },
            "omnifiles-startup-maintenance",
        ).apply {
            isDaemon = true
            start()
        }
    }
}
