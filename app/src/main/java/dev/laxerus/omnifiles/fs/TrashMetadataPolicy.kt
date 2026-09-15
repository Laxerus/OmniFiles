package dev.laxerus.omnifiles.fs

object TrashMetadataPolicy {
    const val ORPHAN_GRACE_MS: Long = 10 * 60 * 1000L

    fun shouldDeleteOrphan(
        name: String,
        modifiedAt: Long,
        liveMetadataNames: Set<String>,
        now: Long,
    ): Boolean {
        if (name in liveMetadataNames) return false
        val recognized = name.endsWith(".json") || name.endsWith(".tmp")
        if (!recognized || modifiedAt <= 0L || now < modifiedAt) return false
        return now - modifiedAt >= ORPHAN_GRACE_MS
    }
}
