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
        requireValidCoreFields(originalPath, displayName, trashedAt)
        val seal = integrityFor(originalPath, displayName, trashedAt)
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
        val originalPath = json.optString(KEY_ORIGINAL_PATH).takeIf { it.isNotBlank() }
        val displayName = json.optString(KEY_DISPLAY_NAME).takeIf { it.isNotBlank() }
        val trashedAt = if (json.has(KEY_TRASHED_AT)) {
            json.optLong(KEY_TRASHED_AT, Long.MIN_VALUE)
        } else {
            null
        }
        val hasSchema = json.has(KEY_SCHEMA_VERSION)
        val hasSeal = json.has(KEY_INTEGRITY_SHA256)
        val schemaVersion = if (hasSchema) json.optInt(KEY_SCHEMA_VERSION, Int.MIN_VALUE) else null
        val suppliedSeal = if (hasSeal) json.optString(KEY_INTEGRITY_SHA256) else null
        return validateFields(
            originalPath = originalPath,
            displayName = displayName,
            trashedAt = trashedAt,
            schemaVersion = schemaVersion,
            integritySha256 = suppliedSeal,
        )
    }

    internal fun integrityFor(
        originalPath: String,
        displayName: String,
        trashedAt: Long,
        schemaVersion: Int = SCHEMA_VERSION,
    ): String {
        requireValidCoreFields(originalPath, displayName, trashedAt)
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

    internal fun validateFields(
        originalPath: String?,
        displayName: String?,
        trashedAt: Long?,
        schemaVersion: Int?,
        integritySha256: String?,
    ): Record? {
        val path = originalPath?.takeIf { it.isNotBlank() } ?: return null
        val name = displayName?.takeIf { it.isNotBlank() } ?: return null
        val timestamp = trashedAt?.takeIf { it >= 0L } ?: return null

        val hasSchema = schemaVersion != null
        val hasSeal = integritySha256 != null
        if (!hasSchema && !hasSeal) {
            return Record(path, name, timestamp, sealed = false)
        }
        if (!hasSchema || !hasSeal) return null
        if (schemaVersion != SCHEMA_VERSION) return null

        val supplied = integritySha256 ?: return null
        if (!HEX_64.matches(supplied)) return null
        val expected = integrityFor(path, name, timestamp, schemaVersion)
        if (!MessageDigest.isEqual(
                supplied.lowercase().toByteArray(StandardCharsets.US_ASCII),
                expected.toByteArray(StandardCharsets.US_ASCII),
            )
        ) return null

        return Record(path, name, timestamp, sealed = true)
    }

    private fun requireValidCoreFields(originalPath: String, displayName: String, trashedAt: Long) {
        require(originalPath.isNotBlank()) { "Çöp metadata orijinal yolu boş olamaz" }
        require(displayName.isNotBlank()) { "Çöp metadata görünen adı boş olamaz" }
        require(trashedAt >= 0L) { "Çöp metadata zamanı geçersiz" }
    }

    private const val KEY_SCHEMA_VERSION = "schemaVersion"
    private const val KEY_ORIGINAL_PATH = "originalPath"
    private const val KEY_DISPLAY_NAME = "displayName"
    private const val KEY_TRASHED_AT = "trashedAt"
    private const val KEY_INTEGRITY_SHA256 = "integritySha256"
    private val HEX_64 = Regex("^[0-9a-fA-F]{64}$")
}
