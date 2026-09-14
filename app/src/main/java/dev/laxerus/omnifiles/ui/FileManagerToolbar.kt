package dev.laxerus.omnifiles.ui

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.android.material.appbar.MaterialToolbar
import dev.laxerus.omnifiles.R

class FileManagerToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.appcompat.R.attr.toolbarStyle
) : MaterialToolbar(context, attrs, defStyleAttr) {

    init {
        inflateMenu(R.menu.menu_file_browser)
        setOnMenuItemClickListener { item ->
            if (item.itemId != R.id.actionSettingsTools) return@setOnMenuItemClickListener false
            context.startActivity(Intent(context, MainActivity::class.java))
            true
        }
    }
}
