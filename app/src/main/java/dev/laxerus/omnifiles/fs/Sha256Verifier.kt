package dev.laxerus.omnifiles.fs

import java.util.Locale

object Sha256Verifier {
    enum class Status {
        EMPTY,
        INVALID,
        WAITING_FOR_HASH,
        MATCH,
        MISMATCH
    }

    data class Result(
        val status: Status,
        val normalizedExpected: String? = null
    )

    fun normalizeExpected(raw: String): String? {
        var value = raw.trim()
        if (value.regionMatches(0, "sha256:", 0, 7, ignoreCase = true)) {
            value = value.substring(7).trim()
        }
        if (value.length != SHA256_HEX_LENGTH || value.any { !it.isHexDigit() }) return null
        return value.lowercase(Locale.ROOT)
    }

    fun verify(actualSha256: String?, expectedRaw: String): Result {
        if (expectedRaw.isBlank()) return Result(Status.EMPTY)
        val expected = normalizeExpected(expectedRaw) ?: return Result(Status.INVALID)
        val actual = actualSha256?.trim()?.lowercase(Locale.ROOT)
        if (actual == null || actual.length != SHA256_HEX_LENGTH || actual.any { !it.isHexDigit() }) {
            return Result(Status.WAITING_FOR_HASH, expected)
        }
        return Result(
            status = if (actual == expected) Status.MATCH else Status.MISMATCH,
            normalizedExpected = expected
        )
    }

    private fun Char.isHexDigit(): Boolean =
        this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private const val SHA256_HEX_LENGTH = 64
}
