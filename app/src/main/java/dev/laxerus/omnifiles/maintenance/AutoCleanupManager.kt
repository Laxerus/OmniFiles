package dev.laxerus.omnifiles.maintenance

import android.content.Context
import dev.laxerus.omnifiles.access.StorageAccessController
import dev.laxerus.omnifiles.fs.JunkCleaner

data class AutoCleanupState(
    val enabled: Boolean,
    val lastAttemptAt: Long,
    val lastSuccessAt: Long,
    val lastDeleted: Int,
    val lastFailed: Int,
    val lastReclaimedBytes: Long,
    val lastScanTruncated: Boolean,
)

data class AutoCleanupRun(
    val deleted: Int,
    val failed: Int,
    val reclaimedBytes: Long,
    val scanTruncated: Boolean,
    val completedAt: Long,
)

object AutoCleanupManager {
    private const val PREFS = "omnifiles_auto_cleanup"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_LAST_ATTEMPT = "last_attempt_at"
    private const val KEY_LAST_SUCCESS = "last_success_at"
    private const val KEY_LAST_DELETED = "last_deleted"
    private const val KEY_LAST_FAILED = "last_failed"
    private const val KEY_LAST_RECLAIMED = "last_reclaimed_bytes"
    private const val KEY_LAST_TRUNCATED = "last_scan_truncated"

    fun state(context: Context): AutoCleanupState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return AutoCleanupState(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            lastAttemptAt = prefs.getLong(KEY_LAST_ATTEMPT, 0L),
            lastSuccessAt = prefs.getLong(KEY_LAST_SUCCESS, 0L),
            lastDeleted = prefs.getInt(KEY_LAST_DELETED, 0),
            lastFailed = prefs.getInt(KEY_LAST_FAILED, 0),
            lastReclaimedBytes = prefs.getLong(KEY_LAST_RECLAIMED, 0L).coerceAtLeast(0L),
            lastScanTruncated = prefs.getBoolean(KEY_LAST_TRUNCATED, false),
        )
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    @Synchronized
    fun runIfDue(
        context: Context,
        force: Boolean = false,
        now: Long = System.currentTimeMillis(),
    ): AutoCleanupRun? {
        val appContext = context.applicationContext
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val hasAccess = StorageAccessController.hasSharedStorageAccess(appContext)
        val lastAttempt = prefs.getLong(KEY_LAST_ATTEMPT, 0L)
        val lastSuccess = prefs.getLong(KEY_LAST_SUCCESS, 0L)

        if (!AutoCleanupSchedule.shouldRun(
                enabled = enabled,
                hasStorageAccess = hasAccess,
                lastAttemptAt = lastAttempt,
                lastSuccessAt = lastSuccess,
                now = now,
                force = force,
            )
        ) {
            return null
        }

        prefs.edit().putLong(KEY_LAST_ATTEMPT, now).commit()

        val scan = JunkCleaner.scan(StorageAccessController.sharedRoot(), now = now)
        val cleanup = JunkCleaner.clean(
            sharedRoot = StorageAccessController.sharedRoot(),
            candidates = scan.candidates,
            now = now,
        )
        val completedAt = System.currentTimeMillis().coerceAtLeast(now)
        prefs.edit()
            .putLong(KEY_LAST_SUCCESS, completedAt)
            .putInt(KEY_LAST_DELETED, cleanup.deleted)
            .putInt(KEY_LAST_FAILED, cleanup.failed)
            .putLong(KEY_LAST_RECLAIMED, cleanup.reclaimedBytes.coerceAtLeast(0L))
            .putBoolean(KEY_LAST_TRUNCATED, scan.truncated)
            .apply()

        return AutoCleanupRun(
            deleted = cleanup.deleted,
            failed = cleanup.failed,
            reclaimedBytes = cleanup.reclaimedBytes.coerceAtLeast(0L),
            scanTruncated = scan.truncated,
            completedAt = completedAt,
        )
    }
}
