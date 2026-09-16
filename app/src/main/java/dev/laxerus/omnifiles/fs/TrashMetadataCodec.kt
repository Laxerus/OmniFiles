package dev.laxerus.omnifiles.fs

import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object TrashMetadataCodec {
    const val SCHEMA_VERSION = 1

    data class Record(
        val originalPath: String,
        val displayName: String,
        val trashedAt: Long,
        val sealed: Boolean,
    )

    fun encode(originalPath: String, displayName: String, trashedAt: Long): String {
        require(originalPath.isNotBlank()) { "Çöp metadata orijinal yolu boş olamaz" }
        require(displayName.isNotBlank()) { "Çöp metadata görünen adı boş olamaz" }
        require(trashedAt >= 0L) { "Çöp metadata zamanı geçersiz" }

        val seal = sealOf(
            schemaVersion = SCHEMA_VERSION,
            originalPath = originalPath,
            displayName = displayName,
            trashedAt = trashedAt,
        )
        return JSONObject()
            .put(KEY_SCHEMA_VERSION, SCHEMA_VERSION)
            .put(KEY_ORIGINAL_PATH, originalPath)
            .put(KEY_DISPLAY_NAME, displayName)
            .put(KEY_TRASHED_AT, trashedAt)
            .put(KEY_INTEGRITY_SHA256, seal)
            .toString()
    }

    fun decode(text: String): Record? {
        val json = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val originalPath = json.optString(KEY_ORIGINAL_PATH).takeIf { it.isNotBlank() } ?: return null
        val displayName = json.optString(KEY_DISPLAY_NAME).takeIf { it.isNotBlank() } ?: return null
        val trashedAt = json.optLong(KEY_TRASHED_AT, Long.MIN_VALUE).takeIf { it >= 0L } ?: return null

        val hasSchema = json.has(KEY_SCHEMA_VERSION)
        val hasSeal = json.has(KEY_INTEGRITY_SHA256)
        if (!hasSchema && !hasSeal) {
            return Record(originalPath, displayName, trashedAt, sealed = false)
        }
        if (!hasSchema || !hasSeal) return null

        val schemaVersion = json.optInt(KEY_SCHEMA_VERSION, Int.MIN_VALUE)
        if (schemaVersion != SCHEMA_VERSION) return null
        val supplied = json.optString(KEY_INTEGRITY_SHA256)
        if (!HEX_64.matches(supplied)) return null
        val expected = sealOf(schemaVersion, originalPath, displayName, trashedAt)
        if (!MessageDigest.isEqual(
                supplied.lowercase().toByteArray(StandardCharsets.US_ASCII),
                expected.toByteArray(StandardCharsets.US_ASCII),
            )
        ) return null

        return Record(originalPath, displayName, trashedAt, sealed = true)
    }

    private fun sealOf(
        schemaVersion: Int,
        originalPath: String,
        displayName: String,
        trashedAt: Long,
    ): String {
        val payload = buildString {
            append(schemaVersion)
            append('\u0000')
            append(originalPath)
            append('\u0000')
            append(displayName)
            append('\u0000')
            append(trashedAt)
        }.toByteArray(StandardCharsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(payload)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private const val KEY_SCHEMA_VERSION = "schemaVersion"
    private const val KEY_ORIGINAL_PATH = "originalPath"
    private const val KEY_DISPLAY_NAME = "displayName"
    private const val KEY_TRASHED_AT = "trashedAt"
    private const val KEY_INTEGRITY_SHA256 = "integritySha256"
    private val HEX_64 = Regex("^[0-9a-fA-F]{64}$")
}
