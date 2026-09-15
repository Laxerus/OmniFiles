package dev.laxerus.omnifiles.ui

import android.content.ActivityNotFoundException
import android.content.Context
import java.io.File

object LocalFileLauncher {
    enum class Result {
        OPENED,
        INVALID,
        NO_VIEWER,
        DENIED,
        FAILED,
    }

    fun open(context: Context, file: File): Result {
        val intent = runCatching { LocalFileIntents.viewIntent(context, file) }
            .getOrElse { return Result.INVALID }

        return try {
            context.startActivity(intent)
            Result.OPENED
        } catch (_: ActivityNotFoundException) {
            Result.NO_VIEWER
        } catch (_: SecurityException) {
            Result.DENIED
        } catch (_: RuntimeException) {
            Result.FAILED
        }
    }
}
