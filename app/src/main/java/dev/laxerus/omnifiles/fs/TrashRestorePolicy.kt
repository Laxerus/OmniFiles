package dev.laxerus.omnifiles.fs

import java.io.File

enum class TrashRestoreAvailability {
    AVAILABLE,
    ORIGINAL_UNKNOWN,
    DESTINATION_OCCUPIED,
}

object TrashRestorePolicy {
    fun availability(originalFile: File?): TrashRestoreAvailability = when {
        originalFile == null -> TrashRestoreAvailability.ORIGINAL_UNKNOWN
        originalFile.exists() -> TrashRestoreAvailability.DESTINATION_OCCUPIED
        else -> TrashRestoreAvailability.AVAILABLE
    }
}
