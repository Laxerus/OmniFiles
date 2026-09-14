package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class DigestUtilsTest {
    @Test fun hashesKnownSha256Vector() {
        val hash = DigestUtils.sha256Hex(ByteArrayInputStream("abc".toByteArray()))
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            hash
        )
    }

    @Test fun hashesBinaryDataWithoutSignExtensionArtifacts() {
        val bytes = byteArrayOf(0x00, 0x7f, 0x80.toByte(), 0xff.toByte())
        val hash = DigestUtils.sha256Hex(ByteArrayInputStream(bytes))
        assertEquals(64, hash.length)
        assertEquals(hash.lowercase(), hash)
    }
}
