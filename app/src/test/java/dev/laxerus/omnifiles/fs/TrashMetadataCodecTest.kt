package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashMetadataCodecTest {
    private val originalPath = "/storage/emulated/0/Documents/example.txt"
    private val displayName = "example.txt"
    private val trashedAt = 123456789L

    @Test
    fun roundTripsSealedRecord() {
        val seal = TrashMetadataCodec.integrityFor(originalPath, displayName, trashedAt)
        val decoded = TrashMetadataCodec.validateFields(
            originalPath = originalPath,
            displayName = displayName,
            trashedAt = trashedAt,
            schemaVersion = TrashMetadataCodec.SCHEMA_VERSION,
            integritySha256 = seal,
        )
        assertNotNull(decoded)
        requireNotNull(decoded)
        assertEquals(originalPath, decoded.originalPath)
        assertEquals(displayName, decoded.displayName)
        assertEquals(trashedAt, decoded.trashedAt)
        assertTrue(decoded.sealed)
    }

    @Test
    fun rejectsModifiedOriginalPath() {
        val seal = TrashMetadataCodec.integrityFor(originalPath, displayName, trashedAt)
        assertNull(
            TrashMetadataCodec.validateFields(
                originalPath = "$originalPath.changed",
                displayName = displayName,
                trashedAt = trashedAt,
                schemaVersion = TrashMetadataCodec.SCHEMA_VERSION,
                integritySha256 = seal,
            )
        )
    }

    @Test
    fun rejectsModifiedDisplayName() {
        val seal = TrashMetadataCodec.integrityFor(originalPath, displayName, trashedAt)
        assertNull(
            TrashMetadataCodec.validateFields(
                originalPath = originalPath,
                displayName = "changed.txt",
                trashedAt = trashedAt,
                schemaVersion = TrashMetadataCodec.SCHEMA_VERSION,
                integritySha256 = seal,
            )
        )
    }

    @Test
    fun rejectsModifiedTimestamp() {
        val seal = TrashMetadataCodec.integrityFor(originalPath, displayName, trashedAt)
        assertNull(
            TrashMetadataCodec.validateFields(
                originalPath = originalPath,
                displayName = displayName,
                trashedAt = trashedAt + 1,
                schemaVersion = TrashMetadataCodec.SCHEMA_VERSION,
                integritySha256 = seal,
            )
        )
    }

    @Test
    fun acceptsLegacyUnsealedRecordForCompatibility() {
        val decoded = TrashMetadataCodec.validateFields(
            originalPath = originalPath,
            displayName = displayName,
            trashedAt = trashedAt,
            schemaVersion = null,
            integritySha256 = null,
        )
        assertNotNull(decoded)
        requireNotNull(decoded)
        assertFalse(decoded.sealed)
    }

    @Test
    fun rejectsHalfSealedRecord() {
        assertNull(
            TrashMetadataCodec.validateFields(
                originalPath = originalPath,
                displayName = displayName,
                trashedAt = trashedAt,
                schemaVersion = TrashMetadataCodec.SCHEMA_VERSION,
                integritySha256 = null,
            )
        )
        val seal = TrashMetadataCodec.integrityFor(originalPath, displayName, trashedAt)
        assertNull(
            TrashMetadataCodec.validateFields(
                originalPath = originalPath,
                displayName = displayName,
                trashedAt = trashedAt,
                schemaVersion = null,
                integritySha256 = seal,
            )
        )
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val seal = TrashMetadataCodec.integrityFor(
            originalPath = originalPath,
            displayName = displayName,
            trashedAt = trashedAt,
            schemaVersion = TrashMetadataCodec.SCHEMA_VERSION + 1,
        )
        assertNull(
            TrashMetadataCodec.validateFields(
                originalPath = originalPath,
                displayName = displayName,
                trashedAt = trashedAt,
                schemaVersion = TrashMetadataCodec.SCHEMA_VERSION + 1,
                integritySha256 = seal,
            )
        )
    }

    @Test
    fun rejectsInvalidSealEncoding() {
        assertNull(
            TrashMetadataCodec.validateFields(
                originalPath = originalPath,
                displayName = displayName,
                trashedAt = trashedAt,
                schemaVersion = TrashMetadataCodec.SCHEMA_VERSION,
                integritySha256 = "not-a-digest",
            )
        )
    }

    @Test
    fun rejectsMissingOrInvalidCoreFields() {
        val seal = TrashMetadataCodec.integrityFor(originalPath, displayName, trashedAt)
        assertNull(
            TrashMetadataCodec.validateFields(
                originalPath = "",
                displayName = displayName,
                trashedAt = trashedAt,
                schemaVersion = TrashMetadataCodec.SCHEMA_VERSION,
                integritySha256 = seal,
            )
        )
        assertNull(
            TrashMetadataCodec.validateFields(
                originalPath = originalPath,
                displayName = "",
                trashedAt = trashedAt,
                schemaVersion = TrashMetadataCodec.SCHEMA_VERSION,
                integritySha256 = seal,
            )
        )
        assertNull(
            TrashMetadataCodec.validateFields(
                originalPath = originalPath,
                displayName = displayName,
                trashedAt = -1L,
                schemaVersion = TrashMetadataCodec.SCHEMA_VERSION,
                integritySha256 = seal,
            )
        )
    }
}
