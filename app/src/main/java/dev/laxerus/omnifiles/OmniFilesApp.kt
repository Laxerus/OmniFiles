package dev.laxerus.omnifiles

import android.app.Application
import com.google.android.material.color.DynamicColors

class OmniFilesApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
