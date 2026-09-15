package dev.laxerus.omnifiles.fs

object TrashMetadataPolicy {
    const val ORPHAN_GRACE_MS: Long = 10 * 60 * 1000L

    fun isPastGrace(modifiedAt: Long, now: Long): Boolean {
        if (modifiedAt <= 0L || now < modifiedAt) return false
        return now - modifiedAt >= ORPHAN_GRACE_MS
    }

    fun shouldDeleteStaleTemp(
        name: String,
        modifiedAt: Long,
        now: Long,
    ): Boolean {
        if (!name.endsWith(".tmp")) return false
        return isPastGrace(modifiedAt = modifiedAt, now = now)
    }
}
