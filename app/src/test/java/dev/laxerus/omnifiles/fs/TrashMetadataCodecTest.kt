package dev.laxerus.omnifiles.fs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrashMetadataCodecTest {
    @Test
    fun roundTripsSealedRecord() {
        val encoded = TrashMetadataCodec.encode(
            originalPath = "/storage/emulated/0/Documents/example.txt",
            displayName = "example.txt",
            trashedAt = 123456789L,
        )
        val decoded = TrashMetadataCodec.decode(encoded)
        assertNotNull(decoded)
        requireNotNull(decoded)
        assertEquals("/storage/emulated/0/Documents/example.txt", decoded.originalPath)
        assertEquals("example.txt", decoded.displayName)
        assertEquals(123456789L, decoded.trashedAt)
        assertTrue(decoded.sealed)
    }

    @Test
    fun rejectsModifiedOriginalPath() {
        val json = JSONObject(TrashMetadataCodec.encode("/safe/a.txt", "a.txt", 42L))
        json.put("originalPath", "/safe/b.txt")
        assertNull(TrashMetadataCodec.decode(json.toString()))
    }

    @Test
    fun rejectsModifiedDisplayName() {
        val json = JSONObject(TrashMetadataCodec.encode("/safe/a.txt", "a.txt", 42L))
        json.put("displayName", "b.txt")
        assertNull(TrashMetadataCodec.decode(json.toString()))
    }

    @Test
    fun rejectsModifiedTimestamp() {
        val json = JSONObject(TrashMetadataCodec.encode("/safe/a.txt", "a.txt", 42L))
        json.put("trashedAt", 43L)
        assertNull(TrashMetadataCodec.decode(json.toString()))
    }

    @Test
    fun acceptsLegacyUnsealedRecordForCompatibility() {
        val legacy = JSONObject()
            .put("originalPath", "/safe/a.txt")
            .put("displayName", "a.txt")
            .put("trashedAt", 42L)
            .toString()
        val decoded = TrashMetadataCodec.decode(legacy)
        assertNotNull(decoded)
        requireNotNull(decoded)
        assertFalse(decoded.sealed)
    }

    @Test
    fun rejectsHalfSealedRecord() {
        val json = JSONObject()
            .put("schemaVersion", TrashMetadataCodec.SCHEMA_VERSION)
            .put("originalPath", "/safe/a.txt")
            .put("displayName", "a.txt")
            .put("trashedAt", 42L)
        assertNull(TrashMetadataCodec.decode(json.toString()))
    }

    @Test
    fun rejectsUnsupportedSchemaVersion() {
        val json = JSONObject(TrashMetadataCodec.encode("/safe/a.txt", "a.txt", 42L))
        json.put("schemaVersion", TrashMetadataCodec.SCHEMA_VERSION + 1)
        assertNull(TrashMetadataCodec.decode(json.toString()))
    }

    @Test
    fun rejectsInvalidSealEncoding() {
        val json = JSONObject(TrashMetadataCodec.encode("/safe/a.txt", "a.txt", 42L))
        json.put("integritySha256", "not-a-digest")
        assertNull(TrashMetadataCodec.decode(json.toString()))
    }
}
