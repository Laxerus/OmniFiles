package dev.laxerus.omnifiles.fs

enum class TrashRecoveryAction {
    KEEP,
    DELETE_METADATA,
}

object TrashRecoveryPolicy {
    fun decide(
        metadataModifiedAt: Long,
        now: Long,
        trashExists: Boolean,
        originalExists: Boolean,
    ): TrashRecoveryAction {
        if (!TrashMetadataPolicy.isPastGrace(metadataModifiedAt, now)) return TrashRecoveryAction.KEEP
        if (trashExists) return TrashRecoveryAction.KEEP
        if (!originalExists) return TrashRecoveryAction.KEEP
        return TrashRecoveryAction.DELETE_METADATA
    }
}
