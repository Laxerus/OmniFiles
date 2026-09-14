package dev.laxerus.omnifiles.scout

import android.content.Context
import dev.laxerus.omnifiles.adb.AdbSessionManager

data class SaveLocation(
    val label: String,
    val path: String,
    val entryCount: Int
)

class SaveScoutService(context: Context) {
    private val adb = AdbSessionManager.get(context)

    suspend fun listThirdPartyPackages(): List<String> {
        val output = adb.shell("cmd package list packages -3")
        return output.lineSequence()
            .map { it.trim() }
            .filter { it.startsWith("package:") }
            .map { it.removePrefix("package:").trim() }
            .filter(::isValidPackageName)
            .distinct()
            .sorted()
            .toList()
    }

    suspend fun scan(packageName: String): List<SaveLocation> {
        val pkg = packageName.trim()
        require(isValidPackageName(pkg)) { "Geçerli bir Android paket adı gir." }

        val candidates = listOf(
            "Uygulama dosyaları" to "/sdcard/Android/data/$pkg/files",
            "Uygulama data kökü" to "/sdcard/Android/data/$pkg",
            "Android media" to "/sdcard/Android/media/$pkg",
            "OBB" to "/sdcard/Android/obb/$pkg"
        )

        val found = mutableListOf<SaveLocation>()
        for ((label, path) in candidates) {
            val entries = runCatching { adb.listDirectory(path) }.getOrNull() ?: continue
            found += SaveLocation(label = label, path = path, entryCount = entries.count { it.name != "." && it.name != ".." })
        }
        return found.distinctBy { it.path }
    }

    companion object {
        private val PACKAGE_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$")

        fun isValidPackageName(value: String): Boolean =
            value.length in 3..255 && PACKAGE_PATTERN.matches(value)
    }
}
