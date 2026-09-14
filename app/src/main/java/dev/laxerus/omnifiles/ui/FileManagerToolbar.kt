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
            val target = when (item.itemId) {
                R.id.actionSettingsTools -> MainActivity::class.java
                R.id.actionJunkCleaner -> JunkCleanerActivity::class.java
                R.id.actionStorageAnalyzer -> StorageAnalyzerActivity::class.java
                R.id.actionTrash -> TrashActivity::class.java
                R.id.actionChecksum -> ChecksumActivity::class.java
                R.id.actionWirelessAdb -> AdbPairingActivity::class.java
                else -> null
            } ?: return@setOnMenuItemClickListener false
            context.startActivity(Intent(context, target))
            true
        }
    }
}
