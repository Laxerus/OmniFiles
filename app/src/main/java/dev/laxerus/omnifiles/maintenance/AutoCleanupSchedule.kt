package dev.laxerus.omnifiles.maintenance

object AutoCleanupSchedule {
    const val NORMAL_INTERVAL_MS = 24L * 60L * 60L * 1000L
    const val RETRY_INTERVAL_MS = 60L * 60L * 1000L

    fun shouldRun(
        enabled: Boolean,
        hasStorageAccess: Boolean,
        lastAttemptAt: Long,
        lastSuccessAt: Long,
        now: Long,
        force: Boolean = false,
    ): Boolean {
        if (!enabled || !hasStorageAccess) return false
        if (force) return true
        if (now <= 0L) return false

        val attemptAge = safeAge(now, lastAttemptAt)
        if (lastAttemptAt > 0L && attemptAge < RETRY_INTERVAL_MS) return false

        val successAge = safeAge(now, lastSuccessAt)
        if (lastSuccessAt > 0L && successAge < NORMAL_INTERVAL_MS) return false

        return true
    }

    private fun safeAge(now: Long, timestamp: Long): Long {
        if (timestamp <= 0L || timestamp > now) return Long.MAX_VALUE
        return now - timestamp
    }
}
