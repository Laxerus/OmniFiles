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

        moveIntoTrash(safeTarget, destination)
        val ticket = TrashTicket(destination.canonicalFile, originalFile)

        runCatching {
            writeMetadata(ticket, safeTarget.name, trashedAt)
        }.onFailure { metadataFailure ->
            val rollback = runCatching { restore(ticket) }
            if (rollback.isSuccess) {
                throw IllegalStateException("Çöp kaydı oluşturulamadı; işlem güvenli biçimde geri alındı", metadataFailure)
            }
            throw IllegalStateException(
                "Öğe çöpe taşındı ancak kalıcı geri yükleme bilgisi yazılamadı; öğe çöpte korundu",
                metadataFailure
            )
        }

        return ticket
    }

    fun listEntries(): List<TrashEntry> {
        ensureMetadataRoot()
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
            if (!copied || !destination.exists()) {
                if (destination.exists()) destination.deleteRecursively()
                error("Klasör eski konumuna geri yüklenemedi; çöp kopyası korundu")
            }
            if (!source.deleteRecursively()) {
                error("Öğe geri yüklendi ancak çöp kopyası temizlenemedi")
            }
        } else {
            source.copyTo(destination, overwrite = false)
            if (!destination.exists() || destination.length() != source.length()) {
                destination.delete()
                error("Dosya geri yükleme doğrulamasından geçmedi; çöp kopyası korundu")
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
        cleanupOrphanMetadata()
        return TrashCleanupResult(deleted = deleted, failed = failed)
    }

    private fun moveIntoTrash(source: File, destination: File) {
        if (source.renameTo(destination)) return

        if (source.isDirectory) {
            val copied = source.copyRecursively(destination, overwrite = false)
            check(copied && destination.exists()) { "Klasör güvenli biçimde kopyalanamadı; kaynak korunuyor" }
            check(source.deleteRecursively()) {
                "Kopya oluşturuldu ancak kaynak temizlenemedi; iki kopya da korunuyor"
            }
        } else {
            source.copyTo(destination, overwrite = false)
            check(destination.exists() && destination.length() == source.length()) {
                "Dosya doğrulanamadı; kaynak korunuyor"
            }
            check(source.delete()) {
                "Kopya oluşturuldu ancak kaynak temizlenemedi; iki kopya da korunuyor"
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
        val source = requireTrashEntry(ticket.trashedFile)
        val destination = metadataFileFor(source)
        require(!destination.exists()) { "Çöp metadata kaydı zaten var" }
        val temp = File(metadataRoot, "${destination.name}.${UUID.randomUUID()}.tmp")
        val json = JSONObject()
            .put(KEY_ORIGINAL_PATH, ticket.originalFile.canonicalPath)
            .put(KEY_DISPLAY_NAME, displayName)
            .put(KEY_TRASHED_AT, trashedAt)

        try {
            temp.writeText(json.toString(), Charsets.UTF_8)
            check(temp.renameTo(destination)) { "Çöp metadata kaydı tamamlanamadı" }
        } finally {
            if (temp.exists()) temp.delete()
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
        return File(metadataRoot, "${source.name}.json")
    }

    private fun cleanupOrphanMetadata() {
        ensureMetadataRoot()
        val liveNames = trashRoot.listFiles()
            ?.asSequence()
            ?.filter { it.name != METADATA_DIR }
            ?.map { "${it.name}.json" }
            ?.toSet()
            .orEmpty()
        metadataRoot.listFiles()?.forEach { metadata ->
            if (metadata.name !in liveNames) metadata.delete()
        }
    }

    private fun ensureMetadataRoot() {
        metadataRoot
    }

    private fun requireTrashEntry(candidate: File): File {
        val safe = candidate.canonicalFile
        require(safe.parentFile?.canonicalPath == trashRoot.path) { "Geçersiz çöp kaydı" }
        require(safe.name != METADATA_DIR) { "Geçersiz çöp kaydı" }
        require(safe.exists()) { "Çöp kaydı artık mevcut değil" }
        return safe
    }

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
