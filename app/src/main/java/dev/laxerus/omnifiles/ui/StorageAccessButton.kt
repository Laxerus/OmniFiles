package dev.laxerus.omnifiles.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.AttributeSet
import android.view.View
import com.google.android.material.button.MaterialButton
import dev.laxerus.omnifiles.access.StorageAccessController

class StorageAccessButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle
) : MaterialButton(context, attrs, defStyleAttr) {

    init {
        setOnClickListener {
            findActivity()?.let(StorageAccessController::requestSharedStorageAccess)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshVisibility()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) refreshVisibility()
    }

    private fun refreshVisibility() {
        visibility = if (StorageAccessController.hasSharedStorageAccess(context)) View.GONE else View.VISIBLE
    }

    private fun findActivity(): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }
}
