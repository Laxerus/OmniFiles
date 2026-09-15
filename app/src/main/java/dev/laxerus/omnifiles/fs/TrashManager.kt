package dev.laxerus.omnifiles.fs

import android.content.Context
import dev.laxerus.omnifiles.access.StorageAccessController
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class TrashTicket(
    val trashedFile: File,
    val originalFile: File
)

data class TrashEntry(
    val trashedFile: File,
    val originalFile: File?,
    val displayName: String,
    val trashedAt: Long,
    val legacy: Boolean
)

data class TrashCleanupResult(
    val deleted: Int,
    val failed: Int
)

class TrashManager(private val context: Context) {
    private class VerifiedTrashCopyRetainedException(message: String) : IllegalStateException(message)

    private val trashRoot: File by lazy {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        File(base, "trash").apply {
            check(exists() || mkdirs()) { "Çöp klasörü oluşturulamadı" }
        }.canonicalFile
    }

    private val metadataRoot: File by lazy {
        File(trashRoot, METADATA_DIR).apply {
            check(exists() || mkdirs()) { "Çöp metadata klasörü oluşturulamadı" }
        }.canonicalFile
    }

    fun moveToTrash(target: File): TrashTicket {
        val sharedRoot = StorageAccessController.sharedRoot()
        val safeTarget = FilePathPolicy.requireSafeTrashTarget(target, sharedRoot)
        val originalFile = safeTarget.canonicalFile
        val trashedAt = System.currentTimeMillis()
        val destination = File(trashRoot, "$trashedAt-${UUID.randomUUID()}-${safeTarget.name}")
        require(!destination.exists()) { "Çöp hedefi zaten var" }

        val ticket = TrashTicket(requireTrashSlot(destination, mustExist = false), originalFile)
        writeMetadata(ticket, safeTarget.name, trashedAt)

        try {
            moveIntoTrash(safeTarget, ticket.trashedFile)
        } catch (retained: VerifiedTrashCopyRetainedException) {
            throw IllegalStateException(
                "Öğe tamamen taşınamadı; doğrulanmış çöp kopyası ve geri yükleme bilgisi korunuyor",
                retained
            )
        } catch (failure: Throwable) {
            removeMetadata(ticket.trashedFile)
            throw failure
        }

        check(ticket.trashedFile.exists()) {
            removeMetadata(ticket.trashedFile)
            "Çöp hedefi taşıma sonrasında bulunamadı; kaynak korunuyor"
        }
        return ticket
    }

    fun listEntries(): List<TrashEntry> {
        ensureMetadataRoot()
        recoverInterruptedTransactions()
        cleanupStaleTempMetadata()
        return trashRoot.listFiles()
            ?.asSequence()
            ?.filter { it.name != METADATA_DIR }
            ?.filter { it.exists() }
            ?.mapNotNull(::toTrashEntry)
            ?.sortedByDescending { it.trashedAt }
            ?.toList()
            .orEmpty()
    }

    fun count(): Int = listEntries().size

    fun restore(entry: TrashEntry): File {
        val original = entry.originalFile
            ?: error("Bu eski çöp kaydının orijinal konumu bilinmiyor; otomatik geri yükleme yapılamaz")
        return restore(TrashTicket(entry.trashedFile, original))
    }

    fun restore(ticket: TrashTicket): File {
        val source = requireTrashEntry(ticket.trashedFile)
        val sharedRoot = StorageAccessController.sharedRoot()
        val destination = FilePathPolicy.requireMutableTarget(ticket.originalFile, sharedRoot)
        val parent = destination.parentFile?.canonicalFile ?: error("Eski üst klasör bulunamadı")
        FilePathPolicy.requireInside(parent, sharedRoot)
        require(parent.exists() && parent.isDirectory) { "Eski üst klasör artık mevcut değil" }
        require(!destination.exists()) { "Eski konumda aynı adda başka bir öğe var; geri alma iptal edildi" }

        if (source.renameTo(destination)) {
            removeMetadata(source)
            return destination.canonicalFile
        }

        if (source.isDirectory) {
            val copied = source.copyRecursively(destination, overwrite = false)
            if (!copied || !destination.exists() || !CopyIntegrityVerifier.matches(source, destination)) {
                if (destination.exists()) destination.deleteRecursively()
                error("Klasör eski konumuna içerik doğrulamasıyla geri yüklenemedi; çöp kopyası korundu")
            }
            if (!source.deleteRecursively()) {
                error("Öğe geri yüklendi ancak çöp kopyası tamamen temizlenemedi")
            }
        } else {
            source.copyTo(destination, overwrite = false)
            if (!CopyIntegrityVerifier.matches(source, destination)) {
                if (destination.exists()) destination.delete()
                error("Dosya geri yükleme içerik doğrulamasından geçmedi; çöp kopyası korundu")
            }
            if (!source.delete()) {
                error("Öğe geri yüklendi ancak çöp kopyası temizlenemedi")
            }
        }
        removeMetadata(source)
        return destination.canonicalFile
    }

    fun deletePermanently(entry: TrashEntry) {
        val source = requireTrashEntry(entry.trashedFile)
        val deleted = if (source.isDirectory) source.deleteRecursively() else source.delete()
        check(deleted || !source.exists()) { "Öğe kalıcı olarak silinemedi" }
        removeMetadata(source)
    }

    fun emptyTrash(): TrashCleanupResult {
        var deleted = 0
        var failed = 0
        listEntries().forEach { entry ->
            runCatching { deletePermanently(entry) }
                .onSuccess { deleted++ }
                .onFailure { failed++ }
        }
        cleanupStaleTempMetadata()
        return TrashCleanupResult(deleted = deleted, failed = failed)
    }

    private fun moveIntoTrash(source: File, destination: File) {
        if (source.renameTo(destination)) return

        if (source.isDirectory) {
            val copied = source.copyRecursively(destination, overwrite = false)
            if (!copied || !destination.exists() || !CopyIntegrityVerifier.matches(source, destination)) {
                if (destination.exists()) destination.deleteRecursively()
                error("Klasör güvenli biçimde kopyalanıp doğrulanamadı; kaynak korunuyor")
            }
            if (!source.deleteRecursively()) {
                throw VerifiedTrashCopyRetainedException(
                    "Doğrulanmış çöp kopyası oluşturuldu ancak kaynak tamamen temizlenemedi"
                )
            }
        } else {
            source.copyTo(destination, overwrite = false)
            if (!CopyIntegrityVerifier.matches(source, destination)) {
                if (destination.exists()) destination.delete()
                error("Dosya içerik doğrulamasından geçmedi; kaynak korunuyor")
            }
            if (!source.delete()) {
                throw VerifiedTrashCopyRetainedException(
                    "Doğrulanmış çöp kopyası oluşturuldu ancak kaynak temizlenemedi"
                )
            }
        }
    }

    private fun toTrashEntry(candidate: File): TrashEntry? {
        val source = runCatching { requireTrashEntry(candidate) }.getOrNull() ?: return null
        val metadata = readMetadata(source)
        val fallbackTime = parseTimestamp(source.name) ?: source.lastModified().takeIf { it > 0 } ?: 0L
        val fallbackName = parseDisplayName(source.name)
        if (metadata == null) {
            return TrashEntry(
                trashedFile = source,
                originalFile = null,
                displayName = fallbackName,
                trashedAt = fallbackTime,
                legacy = true
            )
        }

        val originalPath = metadata.optString(KEY_ORIGINAL_PATH).takeIf { it.isNotBlank() }
        val displayName = metadata.optString(KEY_DISPLAY_NAME).takeIf { it.isNotBlank() } ?: fallbackName
        val trashedAt = metadata.optLong(KEY_TRASHED_AT, fallbackTime)
        val originalFile = originalPath
            ?.let(::File)
            ?.let { path -> runCatching { FilePathPolicy.requireMutableTarget(path, StorageAccessController.sharedRoot()) }.getOrNull() }

        return TrashEntry(
            trashedFile = source,
            originalFile = originalFile,
            displayName = displayName,
            trashedAt = trashedAt,
            legacy = originalFile == null
        )
    }

    private fun writeMetadata(ticket: TrashTicket, displayName: String, trashedAt: Long) {
        ensureMetadataRoot()
        val plannedTrashFile = requireTrashSlot(ticket.trashedFile, mustExist = false)
        val destination = metadataFileFor(plannedTrashFile)
        require(!destination.exists()) { "Çöp metadata kaydı zaten var" }
        val temp = File(metadataRoot, "${destination.name}.${UUID.randomUUID()}.tmp")
        val expectedOriginalPath = ticket.originalFile.canonicalPath
        val json = JSONObject()
            .put(KEY_ORIGINAL_PATH, expectedOriginalPath)
            .put(KEY_DISPLAY_NAME, displayName)
            .put(KEY_TRASHED_AT, trashedAt)

        DurableFileWriter.writeNewUtf8(temp, destination, json.toString())
        val persisted = runCatching { JSONObject(destination.readText(Charsets.UTF_8)) }
            .getOrElse { error ->
                destination.delete()
                throw IllegalStateException("Çöp metadata kaydı JSON doğrulamasından geçmedi", error)
            }
        val valid = persisted.optString(KEY_ORIGINAL_PATH) == expectedOriginalPath &&
            persisted.optString(KEY_DISPLAY_NAME) == displayName &&
            persisted.optLong(KEY_TRASHED_AT, Long.MIN_VALUE) == trashedAt
        if (!valid) {
            destination.delete()
            error("Çöp metadata kaydı alan doğrulamasından geçmedi")
        }
    }

    private fun readMetadata(source: File): JSONObject? {
        val metadata = metadataFileFor(source)
        if (!metadata.isFile) return null
        return runCatching { JSONObject(metadata.readText(Charsets.UTF_8)) }.getOrNull()
    }

    private fun removeMetadata(source: File) {
        val metadata = metadataFileFor(source)
        if (metadata.exists()) metadata.delete()
    }

    private fun metadataFileFor(source: File): File {
        ensureMetadataRoot()
        val safe = requireTrashSlot(source, mustExist = false)
        return File(metadataRoot, "${safe.name}.json")
    }

    private fun recoverInterruptedTransactions() {
        ensureMetadataRoot()
        val now = System.currentTimeMillis()
        val sharedRoot = StorageAccessController.sharedRoot()
        metadataRoot.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.forEach { metadata ->
                val storedName = metadata.name.removeSuffix(".json")
                if (storedName.isBlank() || storedName == metadata.name) return@forEach

                val plannedTrash = runCatching {
                    requireTrashSlot(File(trashRoot, storedName), mustExist = false)
                }.getOrNull() ?: return@forEach

                val json = runCatching {
                    JSONObject(metadata.readText(Charsets.UTF_8))
                }.getOrNull() ?: return@forEach
                val originalPath = json.optString(KEY_ORIGINAL_PATH).takeIf { it.isNotBlank() }
                    ?: return@forEach
                val original = runCatching {
                    FilePathPolicy.requireMutableTarget(File(originalPath), sharedRoot)
                }.getOrNull() ?: return@forEach

                val action = TrashRecoveryPolicy.decide(
                    metadataModifiedAt = metadata.lastModified(),
                    now = now,
                    trashExists = plannedTrash.exists(),
                    originalExists = original.exists(),
                )
                if (action == TrashRecoveryAction.DELETE_METADATA) {
                    metadata.delete()
                }
            }
    }

    private fun cleanupStaleTempMetadata() {
        ensureMetadataRoot()
        val now = System.currentTimeMillis()
        metadataRoot.listFiles()?.forEach { metadata ->
            if (
                TrashMetadataPolicy.shouldDeleteStaleTemp(
                    name = metadata.name,
                    modifiedAt = metadata.lastModified(),
                    now = now,
                )
            ) {
                metadata.delete()
            }
        }
    }

    private fun ensureMetadataRoot() {
        metadataRoot
    }

    private fun requireTrashSlot(candidate: File, mustExist: Boolean): File {
        val absolute = candidate.absoluteFile
        val safe = candidate.canonicalFile
        require(absolute.path == safe.path) { "Geçersiz çöp yolu" }
        require(safe.parentFile?.canonicalPath == trashRoot.path) { "Geçersiz çöp kaydı" }
        require(safe.name != METADATA_DIR) { "Geçersiz çöp kaydı" }
        if (mustExist) require(safe.exists()) { "Çöp kaydı artık mevcut değil" }
        return safe
    }

    private fun requireTrashEntry(candidate: File): File = requireTrashSlot(candidate, mustExist = true)

    private fun parseTimestamp(name: String): Long? = name.substringBefore('-').toLongOrNull()

    private fun parseDisplayName(name: String): String {
        val match = STORED_NAME_PATTERN.matchEntire(name)
        return match?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() } ?: name
    }

    companion object {
        private const val METADATA_DIR = ".metadata"
        private const val KEY_ORIGINAL_PATH = "originalPath"
        private const val KEY_DISPLAY_NAME = "displayName"
        private const val KEY_TRASHED_AT = "trashedAt"
        private val STORED_NAME_PATTERN = Regex("^\\d+-[0-9a-fA-F-]{36}-(.+)$")
    }
}
