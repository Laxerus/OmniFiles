package dev.laxerus.omnifiles.maintenance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoCleanupScheduleTest {
    private val day = AutoCleanupSchedule.NORMAL_INTERVAL_MS
    private val hour = AutoCleanupSchedule.RETRY_INTERVAL_MS

    @Test
    fun disabledOrMissingAccessNeverRuns() {
        val now = 10L * day
        assertFalse(
            AutoCleanupSchedule.shouldRun(
                enabled = false,
                hasStorageAccess = true,
                lastAttemptAt = 0L,
                lastSuccessAt = 0L,
                now = now,
            )
        )
        assertFalse(
            AutoCleanupSchedule.shouldRun(
                enabled = true,
                hasStorageAccess = false,
                lastAttemptAt = 0L,
                lastSuccessAt = 0L,
                now = now,
                force = true,
            )
        )
    }

    @Test
    fun firstRunIsImmediatelyDue() {
        assertTrue(
            AutoCleanupSchedule.shouldRun(
                enabled = true,
                hasStorageAccess = true,
                lastAttemptAt = 0L,
                lastSuccessAt = 0L,
                now = day,
            )
        )
    }

    @Test
    fun successfulRunIsLimitedToOncePerDay() {
        val success = 5L * day
        assertFalse(
            AutoCleanupSchedule.shouldRun(
                enabled = true,
                hasStorageAccess = true,
                lastAttemptAt = success,
                lastSuccessAt = success,
                now = success + day - 1L,
            )
        )
        assertTrue(
            AutoCleanupSchedule.shouldRun(
                enabled = true,
                hasStorageAccess = true,
                lastAttemptAt = success,
                lastSuccessAt = success,
                now = success + day,
            )
        )
    }

    @Test
    fun failedAttemptWaitsAtLeastOneHourBeforeRetry() {
        val attempt = 7L * day
        assertFalse(
            AutoCleanupSchedule.shouldRun(
                enabled = true,
                hasStorageAccess = true,
                lastAttemptAt = attempt,
                lastSuccessAt = 0L,
                now = attempt + hour - 1L,
            )
        )
        assertTrue(
            AutoCleanupSchedule.shouldRun(
                enabled = true,
                hasStorageAccess = true,
                lastAttemptAt = attempt,
                lastSuccessAt = 0L,
                now = attempt + hour,
            )
        )
    }

    @Test
    fun forceBypassesTimeWindowsButNotSafetyGates() {
        val now = 9L * day
        assertTrue(
            AutoCleanupSchedule.shouldRun(
                enabled = true,
                hasStorageAccess = true,
                lastAttemptAt = now,
                lastSuccessAt = now,
                now = now,
                force = true,
            )
        )
    }
}
