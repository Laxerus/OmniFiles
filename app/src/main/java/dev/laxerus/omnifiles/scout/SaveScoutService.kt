package dev.laxerus.omnifiles.scout

import android.content.Context
import dev.laxerus.omnifiles.adb.AdbRemoteEntry
import dev.laxerus.omnifiles.adb.AdbSessionManager
import java.util.Locale

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

        val standard = linkedMapOf<String, SaveLocation>()
        val likely = linkedMapOf<String, SaveLocation>()

        for ((label, path) in candidates) {
            val entries = runCatching { adb.listDirectory(path) }.getOrNull() ?: continue
            val visibleEntries = entries.filter(::isUsableEntry)
            standard[path] = SaveLocation(label = label, path = path, entryCount = visibleEntries.size)

            if (path.endsWith("/files") || path.endsWith("/$pkg")) {
                collectLikelySaveDirectories(visibleEntries, likely)
            }
        }

        return buildList {
            addAll(likely.values.take(MAX_LIKELY_RESULTS))
            standard.values.forEach { location ->
                if (none { it.path == location.path }) add(location)
            }
        }
    }

    private suspend fun collectLikelySaveDirectories(
        entries: List<AdbRemoteEntry>,
        output: LinkedHashMap<String, SaveLocation>
    ) {
        entries.asSequence()
            .filter { it.isDirectory && !it.isSymlink && isLikelySaveDirectoryName(it.name) }
            .take(MAX_LIKELY_RESULTS - output.size)
            .forEach { entry ->
                if (output.containsKey(entry.path)) return@forEach
                val childEntries = runCatching { adb.listDirectory(entry.path) }.getOrNull() ?: return@forEach
                output[entry.path] = SaveLocation(
                    label = "Muhtemel save • ${entry.name}",
                    path = entry.path,
                    entryCount = childEntries.count(::isUsableEntry)
                )
            }
    }

    private fun isUsableEntry(entry: AdbRemoteEntry): Boolean =
        entry.name != "." && entry.name != ".." && entry.errorCode in listOf(null, 0)

    companion object {
        private const val MAX_LIKELY_RESULTS = 12
        private val PACKAGE_PATTERN = Regex("^[A-Za-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_]+)+$")
        private val LIKELY_SAVE_NAMES = setOf(
            "save",
            "saves",
            "saved",
            "savedata",
            "savegame",
            "savegames",
            "savedgames",
            "userdata",
            "userprofile",
            "profile",
            "profiles",
            "world",
            "worlds",
            "backup",
            "backups",
            "ue4game",
            "unrealgame"
        )

        fun isValidPackageName(value: String): Boolean =
            value.length in 3..255 && PACKAGE_PATTERN.matches(value)

        fun isLikelySaveDirectoryName(value: String): Boolean {
            val normalized = value.trim()
                .lowercase(Locale.ROOT)
                .filter { it.isLetterOrDigit() }
            return normalized in LIKELY_SAVE_NAMES ||
                normalized.startsWith("savegame") ||
                normalized.startsWith("savedgame") ||
                normalized.startsWith("userdata")
        }
    }
}
