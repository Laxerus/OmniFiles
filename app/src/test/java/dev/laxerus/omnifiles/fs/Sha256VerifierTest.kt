package dev.laxerus.omnifiles.fs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Sha256VerifierTest {
    private val hash = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test fun normalizesUppercaseAndOptionalPrefix() {
        assertEquals(hash, Sha256Verifier.normalizeExpected("  SHA256: ${hash.uppercase()}  "))
        assertEquals(hash, Sha256Verifier.normalizeExpected(hash.uppercase()))
    }

    @Test fun rejectsMalformedExpectedHashes() {
        assertNull(Sha256Verifier.normalizeExpected(hash.dropLast(1)))
        assertNull(Sha256Verifier.normalizeExpected(hash + "0"))
        assertNull(Sha256Verifier.normalizeExpected(hash.replaceRange(10, 11, "g")))
        assertNull(Sha256Verifier.normalizeExpected(hash.substring(0, 32) + " " + hash.substring(32)))
    }

    @Test fun distinguishesWaitingMatchAndMismatch() {
        assertEquals(Sha256Verifier.Status.EMPTY, Sha256Verifier.verify(null, "").status)
        assertEquals(Sha256Verifier.Status.WAITING_FOR_HASH, Sha256Verifier.verify(null, hash).status)
        assertEquals(Sha256Verifier.Status.MATCH, Sha256Verifier.verify(hash.uppercase(), "sha256:$hash").status)
        assertEquals(
            Sha256Verifier.Status.MISMATCH,
            Sha256Verifier.verify("f".repeat(64), hash).status
        )
    }

    @Test fun invalidExpectedHashWinsBeforeActualHashAvailability() {
        assertEquals(Sha256Verifier.Status.INVALID, Sha256Verifier.verify(null, "not-a-hash").status)
    }
}
