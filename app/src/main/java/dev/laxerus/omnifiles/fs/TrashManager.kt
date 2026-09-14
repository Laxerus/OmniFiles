package dev.laxerus.omnifiles.fs

import android.content.Context
import dev.laxerus.omnifiles.access.StorageAccessController
import org.json.JSONObject
import java.io.File
import java.util.ArrayDeque
import java.util.Locale
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
        val sharedRoot = StorageAccessController.sharedRoot().canonicalFile
        val safeTarget = FilePathPolicy.requireSafeTrashTarget(target, sharedRoot)
        val originalFile = safeTarget.canonicalFile
        val trashedAt = System.currentTimeMillis()
        val destination = File(trashRoot, "$trashedAt-${UUID.randomUUID()}-${safeTarget.name}")
        require(!destination.exists()) { "Çöp hedefi zaten var" }

        moveIntoTrash(safeTarget, destination, sharedRoot)
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
        val sharedRoot = StorageAccessController.sharedRoot().canonicalFile
        val destination = FilePathPolicy.requireMutableTarget(ticket.originalFile, sharedRoot)
        val parent = destination.parentFile?.canonicalFile ?: error("Eski üst klasör bulunamadı")
        FilePathPolicy.requireInside(parent, sharedRoot)
        require(parent.exists() && parent.isDirectory) { "Eski üst klasör artık mevcut değil" }
        require(!destination.exists()) { "Eski konumda aynı adda başka bir öğe var; geri alma iptal edildi" }

        if (source.renameTo(destination)) {
            removeMetadata(source)
            return destination.canonicalFile
        }

        requireEnoughFreeSpace(source, trashRoot, parent)
        copyTreeVerified(
            source = source,
            destination = destination,
            sourceRoot = trashRoot,
            destinationRoot = sharedRoot
        )
        deleteTreeVerified(source, trashRoot)
        removeMetadata(source)
        return destination.canonicalFile
    }

    fun deletePermanently(entry: TrashEntry) {
        val source = requireTrashEntry(entry.trashedFile)
        deleteTreeVerified(source, trashRoot)
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

    private fun moveIntoTrash(source: File, destination: File, sharedRoot: File) {
        if (source.renameTo(destination)) return

        requireEnoughFreeSpace(source, sharedRoot, trashRoot)
        copyTreeVerified(
            source = source,
            destination = destination,
            sourceRoot = sharedRoot,
            destinationRoot = trashRoot
        )
        deleteTreeVerified(source, sharedRoot)
    }

    private fun copyTreeVerified(
        source: File,
        destination: File,
        sourceRoot: File,
        destinationRoot: File
    ) {
        val created = mutableListOf<File>()
        try {
            copyTreeRecursive(
                source = source,
                destination = destination,
                sourceRoot = sourceRoot,
                destinationRoot = destinationRoot,
                activeDirectories = mutableSetOf(),
                created = created
            )
        } catch (error: Throwable) {
            rollbackCreated(created, destinationRoot)
            throw error
        }
    }

    private fun copyTreeRecursive(
        source: File,
        destination: File,
        sourceRoot: File,
        destinationRoot: File,
        activeDirectories: MutableSet<String>,
        created: MutableList<File>
    ) {
        val safeSource = FilePathPolicy.requireInside(source, sourceRoot)
        val safeDestination = FilePathPolicy.requireInside(destination, destinationRoot)
        require(safeSource.exists()) { "Kopyalanacak öğe artık mevcut değil: ${source.name}" }
        require(!safeDestination.exists()) { "Kopya hedefi zaten mevcut: ${destination.name}" }

        if (safeSource.isDirectory) {
            val canonicalPath = safeSource.canonicalPath
            require(activeDirectories.add(canonicalPath)) { "Döngüsel klasör bağlantısı algılandı" }
            try {
                check(safeDestination.mkdir()) { "Hedef klasör oluşturulamadı: ${safeDestination.name}" }
                created += safeDestination
                val children = safeSource.listFiles() ?: error("Klasör okunamadı: ${safeSource.name}")
                children.forEach { child ->
                    copyTreeRecursive(
                        source = child,
                        destination = File(safeDestination, child.name),
                        sourceRoot = sourceRoot,
                        destinationRoot = destinationRoot,
                        activeDirectories = activeDirectories,
                        created = created
                    )
                }
                safeDestination.setLastModified(safeSource.lastModified())
            } finally {
                activeDirectories.remove(canonicalPath)
            }
            return
        }

        safeDestination.outputStream().use { output ->
            created += safeDestination
            safeSource.inputStream().use { input -> input.copyTo(output) }
        }
        check(safeDestination.length() == safeSource.length()) {
            "Dosya kopyası doğrulanamadı: ${safeSource.name}"
        }
        safeDestination.setLastModified(safeSource.lastModified())
    }

    private fun rollbackCreated(created: List<File>, destinationRoot: File) {
        created.asReversed().forEach { candidate ->
            runCatching {
                val safe = FilePathPolicy.requireInside(candidate, destinationRoot)
                if (safe.exists()) safe.delete()
            }
        }
    }

    private fun deleteTreeVerified(target: File, allowedRoot: File) {
        val safeTarget = FilePathPolicy.requireInside(target, allowedRoot)
        require(safeTarget.exists()) { "Silinecek öğe artık mevcut değil" }
        if (safeTarget.isDirectory) {
            val children = safeTarget.listFiles() ?: error("Klasör silme için okunamadı: ${safeTarget.name}")
            children.forEach { child -> deleteTreeVerified(child, allowedRoot) }
        }
        check(safeTarget.delete()) { "Öğe temizlenemedi: ${safeTarget.name}" }
    }

    private fun requireEnoughFreeSpace(source: File, sourceRoot: File, destinationDirectory: File) {
        val requiredBytes = estimateTreeBytes(source, sourceRoot)
        if (requiredBytes <= 0L) return
        val usableBytes = destinationDirectory.usableSpace
        if (usableBytes <= 0L) return

        val reserve = (usableBytes / 50L)
            .coerceAtLeast(MIN_FREE_SPACE_RESERVE_BYTES)
            .coerceAtMost(MAX_FREE_SPACE_RESERVE_BYTES)
        val safelyAvailable = (usableBytes - reserve).coerceAtLeast(0L)
        require(requiredBytes <= safelyAvailable) {
            "Hedefte yeterli boş alan yok. Gerekli: ${formatBytes(requiredBytes)}, güvenli kullanılabilir: ${formatBytes(safelyAvailable)}"
        }
    }

    private fun estimateTreeBytes(source: File, allowedRoot: File): Long {
        val root = FilePathPolicy.canonical(allowedRoot)
        val safeSource = FilePathPolicy.requireInside(source, root)
        require(safeSource.exists()) { "Kaynak öğe artık mevcut değil" }
        var total = 0L
        val pending = ArrayDeque<File>()
        val visitedDirectories = mutableSetOf<String>()
        pending.add(safeSource)

        while (pending.isNotEmpty()) {
            val current = FilePathPolicy.requireInside(pending.removeFirst(), root)
            require(current.exists()) { "Kaynak öğe tarama sırasında kayboldu: ${current.name}" }
            if (current.isFile) {
                total = saturatingAdd(total, current.length().coerceAtLeast(0L))
                continue
            }

            val canonicalPath = current.canonicalPath
            require(visitedDirectories.add(canonicalPath)) { "Döngüsel klasör bağlantısı algılandı" }
            val children = current.listFiles() ?: error("Klasör okunamadı: ${current.name}")
            children.forEach { child -> pending.addLast(FilePathPolicy.requireInside(child, root)) }
        }
        return total
    }

    private fun saturatingAdd(left: Long, right: Long): Long {
        if (right <= 0L) return left
        return if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB", "PB")
        var value = bytes.toDouble()
        var index = -1
        while (value >= 1024.0 && index < units.lastIndex) {
            value /= 1024.0
            index++
        }
        return "%.1f %s".format(Locale.ROOT, value, units[index])
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
        private const val MIN_FREE_SPACE_RESERVE_BYTES = 8L * 1024L * 1024L
        private const val MAX_FREE_SPACE_RESERVE_BYTES = 64L * 1024L * 1024L
        private val STORED_NAME_PATTERN = Regex("^\\d+-[0-9a-fA-F-]{36}-(.+)$")
    }
}
