package dev.laxerus.omnifiles.fs

import java.io.File
import java.security.MessageDigest
import java.util.ArrayDeque

class TransferCancelledException : RuntimeException("Aktarım iptal edildi")

object FileOperations {
    private const val MIN_FREE_SPACE_RESERVE_BYTES = 8L * 1024L * 1024L
    private const val MAX_FREE_SPACE_RESERVE_BYTES = 64L * 1024L * 1024L
    private const val COPY_BUFFER_BYTES = 64 * 1024
    private const val STAGING_PREFIX = ".omnifiles-transfer-v2-"
    private const val DEFAULT_STAGING_STALE_AFTER_MS = 6L * 60L * 60L * 1000L

    private data class CopyTask(
        val source: File,
        val destination: File,
        val finalizeDirectory: Boolean = false,
        val directorySnapshot: DirectorySnapshot? = null
    )

    private data class DirectorySnapshot(
        val modifiedAt: Long,
        val childNames: Set<String>
    )

    private data class FileSnapshot(
        val length: Long,
        val modifiedAt: Long
    )

    fun createDirectory(parent: File, rawName: String, sharedRoot: File): File {
        val destination = FilePathPolicy.resolveChild(parent, rawName, sharedRoot)
        require(!destination.exists()) { "Bu adda bir öğe zaten var" }
        check(destination.mkdir()) { "Klasör oluşturulamadı" }
        return destination.canonicalFile
    }

    fun rename(target: File, rawName: String, sharedRoot: File): File {
        val safeTarget = FilePathPolicy.requireMutableTarget(target, sharedRoot)
        require(safeTarget.exists()) { "Öğe artık mevcut değil" }
        val parent = safeTarget.parentFile ?: error("Üst klasör bulunamadı")
        val destination = FilePathPolicy.resolveChild(parent, rawName, sharedRoot)
        require(destination.canonicalPath != safeTarget.canonicalPath) { "Yeni ad mevcut adla aynı" }
        require(!destination.exists()) { "Bu adda bir öğe zaten var" }
        check(safeTarget.renameTo(destination)) { "Yeniden adlandırma başarısız" }
        return destination.canonicalFile
    }

    fun estimateTransferBytes(
        source: File,
        sharedRoot: File,
        isCancelled: (() -> Boolean)? = null
    ): Long {
        val root = FilePathPolicy.canonical(sharedRoot)
        val safeSource = FilePathPolicy.requireDirectEntry(source, root)
        require(safeSource.exists()) { "Kaynak öğe artık mevcut değil" }
        require(safeSource.path != root.path) { "Depolama kökünün tamamı aktarılamaz" }

        var total = 0L
        val pending = ArrayDeque<File>()
        val visitedDirectories = mutableSetOf<String>()
        pending.add(safeSource)

        while (pending.isNotEmpty()) {
            checkCancelled(isCancelled)
            val current = FilePathPolicy.requireDirectEntry(pending.removeFirst(), root)
            require(current.exists()) { "Kaynak öğe tarama sırasında kayboldu: ${current.name}" }
            if (current.isFile) {
                total = saturatingAdd(total, current.length().coerceAtLeast(0L))
                continue
            }

            val canonicalPath = current.canonicalPath
            require(visitedDirectories.add(canonicalPath)) { "Döngüsel klasör bağlantısı algılandı" }
            val children = current.listFiles() ?: error("Klasör okunamadı: ${current.name}")
            children.forEach { child ->
                checkCancelled(isCancelled)
                pending.addLast(FilePathPolicy.requireDirectEntry(child, root))
            }
        }
        checkCancelled(isCancelled)
        return total
    }

    fun cleanupStaleStaging(
        directory: File,
        sharedRoot: File,
        nowMs: Long = System.currentTimeMillis(),
        staleAfterMs: Long = DEFAULT_STAGING_STALE_AFTER_MS
    ): Int {
        require(nowMs >= 0L) { "Geçerli saat negatif olamaz" }
        require(staleAfterMs >= 0L) { "Staging yaş sınırı negatif olamaz" }
        val root = FilePathPolicy.canonical(sharedRoot)
        val safeDirectory = requireDestinationDirectory(directory, root)
        val cutoff = if (nowMs >= staleAfterMs) nowMs - staleAfterMs else Long.MIN_VALUE
        var removed = 0

        safeDirectory.listFiles().orEmpty().forEach { candidate ->
            val createdAt = stagingTimestamp(candidate.name) ?: return@forEach
            if (createdAt > cutoff) return@forEach

            val safeCandidate = runCatching { FilePathPolicy.requireDirectEntry(candidate, root) }
                .getOrNull() ?: return@forEach
            val modifiedAt = safeCandidate.lastModified()
            if (modifiedAt <= 0L || modifiedAt > cutoff) return@forEach

            if (deleteValidatedStagingTree(safeCandidate, root)) removed++
        }
        return removed
    }

    fun copy(
        source: File,
        destinationDirectory: File,
        sharedRoot: File,
        onProgress: ((Long, Long) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): File {
        val root = FilePathPolicy.canonical(sharedRoot)
        val safeSource = FilePathPolicy.requireDirectEntry(source, root)
        require(safeSource.exists()) { "Kaynak öğe artık mevcut değil" }
        require(safeSource.path != root.path) { "Depolama kökünün tamamı kopyalanamaz" }
        checkCancelled(isCancelled)

        val safeDestinationDirectory = requireDestinationDirectory(destinationDirectory, root)
        cleanupStaleStaging(safeDestinationDirectory, root)
        requireNotInsideSource(safeSource, safeDestinationDirectory)
        val requiredBytes = estimateTransferBytes(safeSource, root, isCancelled)
        requireEnoughFreeSpace(requiredBytes, safeDestinationDirectory)
        onProgress?.invoke(0L, requiredBytes)
        checkCancelled(isCancelled)

        val preferredDestination = nextAvailableDestination(safeDestinationDirectory, safeSource)
        val staging = nextStagingDestination(safeDestinationDirectory)
        val created = mutableListOf<File>()

        return try {
            copyTree(
                source = safeSource,
                destination = staging,
                allowedRoot = root,
                created = created,
                totalBytes = requiredBytes,
                onProgress = onProgress,
                isCancelled = isCancelled
            )
            checkCancelled(isCancelled)
            val committed = commitStagingCopy(staging, preferredDestination, safeDestinationDirectory, safeSource)
            onProgress?.invoke(requiredBytes, requiredBytes)
            committed
        } catch (error: Throwable) {
            rollbackCreated(created)
            throw error
        }
    }

    fun move(
        source: File,
        destinationDirectory: File,
        sharedRoot: File,
        onProgress: ((Long, Long) -> Unit)? = null,
        isCancelled: (() -> Boolean)? = null
    ): File {
        val root = FilePathPolicy.canonical(sharedRoot)
        val safeSource = FilePathPolicy.requireMutableTarget(source, root)
        require(safeSource.exists()) { "Kaynak öğe artık mevcut değil" }
        val safeDestinationDirectory = requireDestinationDirectory(destinationDirectory, root)
        cleanupStaleStaging(safeDestinationDirectory, root)
        requireNotInsideSource(safeSource, safeDestinationDirectory)
        checkCancelled(isCancelled)

        val sourceParent = safeSource.parentFile?.canonicalFile ?: error("Kaynak üst klasörü bulunamadı")
        require(sourceParent.path != safeDestinationDirectory.path) { "Öğe zaten bu klasörde" }

        val destination = File(safeDestinationDirectory, safeSource.name)
        require(!destination.exists()) { "Hedef klasörde aynı adda bir öğe zaten var" }
        FilePathPolicy.requireInside(destination, root)

        onProgress?.invoke(0L, 0L)
        checkCancelled(isCancelled)
        if (safeSource.renameTo(destination)) {
            onProgress?.invoke(1L, 1L)
            return destination.canonicalFile
        }

        val requiredBytes = estimateTransferBytes(safeSource, root, isCancelled)
        requireEnoughFreeSpace(requiredBytes, safeDestinationDirectory)
        onProgress?.invoke(0L, requiredBytes)
        checkCancelled(isCancelled)
        val staging = nextStagingDestination(safeDestinationDirectory)
        val created = mutableListOf<File>()
        val committed = try {
            copyTree(
                source = safeSource,
                destination = staging,
                allowedRoot = root,
                created = created,
                totalBytes = requiredBytes,
                onProgress = onProgress,
                isCancelled = isCancelled
            )
            checkCancelled(isCancelled)
            require(!destination.exists()) { "Hedef klasörde aynı adda bir öğe işlem sırasında oluşturuldu" }
            check(staging.renameTo(destination)) { "Doğrulanan geçici kopya hedefe taşınamadı" }
            onProgress?.invoke(requiredBytes, requiredBytes)
            destination.canonicalFile
        } catch (error: Throwable) {
            rollbackCreated(created)
            throw error
        }

        removeVerifiedSource(safeSource, root)
        return committed
    }

    private fun requireDestinationDirectory(directory: File, root: File): File {
        val safe = FilePathPolicy.requireDirectEntry(directory, root)
        require(safe.exists() && safe.isDirectory) { "Hedef klasör geçerli değil" }
        return safe
    }

    private fun requireNotInsideSource(source: File, destinationDirectory: File) {
        if (!source.isDirectory) return
        require(
            destinationDirectory.path != source.path &&
                !destinationDirectory.path.startsWith(source.path + File.separator)
        ) { "Klasör kendi içine kopyalanamaz veya taşınamaz" }
    }

    private fun requireEnoughFreeSpace(requiredBytes: Long, destinationDirectory: File) {
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

    private fun nextAvailableDestination(parent: File, source: File): File {
        val direct = File(parent, source.name)
        if (!direct.exists()) return direct

        val name = source.name
        val dot = if (source.isFile) name.lastIndexOf('.').takeIf { it > 0 } else null
        val base = dot?.let { name.substring(0, it) } ?: name
        val suffix = dot?.let { name.substring(it) }.orEmpty()
        for (index in 1..9999) {
            val candidate = File(parent, "$base ($index)$suffix")
            if (!candidate.exists()) return candidate
        }
        error("Uygun kopya adı oluşturulamadı")
    }

    private fun nextStagingDestination(parent: File): File {
        val createdAt = System.currentTimeMillis().coerceAtLeast(0L)
        val token = java.lang.Long.toUnsignedString(System.nanoTime(), 36)
        for (index in 0..999) {
            val suffix = if (index == 0) token else "$token-$index"
            val candidate = File(parent, "$STAGING_PREFIX$createdAt-$suffix")
            if (!candidate.exists()) return candidate
        }
        error("Güvenli geçici aktarım alanı oluşturulamadı")
    }

    private fun stagingTimestamp(name: String): Long? {
        if (!name.startsWith(STAGING_PREFIX)) return null
        val payload = name.substring(STAGING_PREFIX.length)
        val separator = payload.indexOf('-')
        if (separator <= 0 || separator == payload.lastIndex) return null
        val timestamp = payload.substring(0, separator).toLongOrNull() ?: return null
        if (timestamp < 0L) return null
        val token = payload.substring(separator + 1)
        if (token.any { !(it in '0'..'9' || it in 'a'..'z' || it == '-') }) return null
        return timestamp
    }

    private fun deleteValidatedStagingTree(target: File, allowedRoot: File): Boolean {
        val validated = collectValidatedTree(target, allowedRoot) ?: return false
        for (entry in validated.asReversed()) {
            if (entry.exists() && !entry.delete()) return false
        }
        return !target.exists()
    }

    private fun collectValidatedTree(target: File, allowedRoot: File): List<File>? {
        val pending = ArrayDeque<File>()
        val validated = mutableListOf<File>()
        val visitedDirectories = mutableSetOf<String>()
        pending.add(target)

        while (pending.isNotEmpty()) {
            val safeCurrent = runCatching {
                FilePathPolicy.requireDirectEntry(pending.removeFirst(), allowedRoot)
            }.getOrNull() ?: return null
            if (!safeCurrent.exists()) continue
            validated += safeCurrent

            if (!safeCurrent.isDirectory) continue
            if (!visitedDirectories.add(safeCurrent.canonicalPath)) return null
            val children = safeCurrent.listFiles() ?: return null
            for (child in children) {
                val safeChild = runCatching { FilePathPolicy.requireDirectEntry(child, allowedRoot) }
                    .getOrNull() ?: return null
                pending.addLast(safeChild)
            }
        }
        return validated
    }

    private fun commitStagingCopy(
        staging: File,
        preferredDestination: File,
        parent: File,
        source: File
    ): File {
        var destination = preferredDestination
        if (destination.exists()) destination = nextAvailableDestination(parent, source)
        check(!destination.exists()) { "Kopya hedefi işlem sırasında kullanıma alındı" }
        check(staging.renameTo(destination)) { "Doğrulanan geçici kopya hedefe taşınamadı" }
        return destination.canonicalFile
    }

    private fun copyTree(
        source: File,
        destination: File,
        allowedRoot: File,
        created: MutableList<File>,
        totalBytes: Long,
        onProgress: ((Long, Long) -> Unit)?,
        isCancelled: (() -> Boolean)?
    ) {
        val pending = ArrayDeque<CopyTask>()
        val visitedDirectories = mutableSetOf<String>()
        var copiedBytes = 0L
        pending.addLast(CopyTask(source, destination))

        while (pending.isNotEmpty()) {
            checkCancelled(isCancelled)
            val task = pending.removeFirst()
            val safeSource = FilePathPolicy.requireDirectEntry(task.source, allowedRoot)

            if (task.finalizeDirectory) {
                val snapshot = task.directorySnapshot ?: error("Klasör snapshot bilgisi eksik")
                require(safeSource.exists() && safeSource.isDirectory) {
                    "Kaynak klasör kopyalama sırasında değişti veya kayboldu: ${safeSource.name}"
                }
                val currentChildren = safeSource.listFiles()
                    ?: error("Kaynak klasör doğrulama sırasında okunamadı: ${safeSource.name}")
                val currentNames = currentChildren.mapTo(mutableSetOf()) { it.name }
                check(safeSource.lastModified() == snapshot.modifiedAt && currentNames == snapshot.childNames) {
                    "Kaynak klasör kopyalama sırasında değişti: ${safeSource.name}"
                }
                task.destination.setLastModified(snapshot.modifiedAt)
                continue
            }

            require(safeSource.exists()) { "Kopyalanacak öğe artık mevcut değil: ${safeSource.name}" }
            require(!task.destination.exists()) { "Kopya hedefi zaten mevcut: ${task.destination.name}" }

            if (!safeSource.isDirectory) {
                val sourceModifiedAt = copyFileVerified(
                    source = safeSource,
                    destination = task.destination,
                    created = created,
                    isCancelled = isCancelled
                ) { delta ->
                    copiedBytes = saturatingAdd(copiedBytes, delta)
                    onProgress?.invoke(copiedBytes.coerceAtMost(totalBytes), totalBytes)
                }
                task.destination.setLastModified(sourceModifiedAt)
                continue
            }

            val canonicalPath = safeSource.canonicalPath
            require(visitedDirectories.add(canonicalPath)) { "Döngüsel klasör bağlantısı algılandı" }
            val children = safeSource.listFiles() ?: error("Klasör okunamadı: ${safeSource.name}")
            val snapshot = DirectorySnapshot(
                modifiedAt = safeSource.lastModified(),
                childNames = children.mapTo(mutableSetOf()) { it.name }
            )

            checkCancelled(isCancelled)
            check(task.destination.mkdir()) { "Hedef klasör oluşturulamadı: ${task.destination.name}" }
            created += task.destination
            pending.addFirst(
                CopyTask(
                    source = safeSource,
                    destination = task.destination,
                    finalizeDirectory = true,
                    directorySnapshot = snapshot
                )
            )
            for (index in children.indices.reversed()) {
                checkCancelled(isCancelled)
                val safeChild = FilePathPolicy.requireDirectEntry(children[index], allowedRoot)
                pending.addFirst(CopyTask(safeChild, File(task.destination, safeChild.name)))
            }
        }
    }

    private fun copyFileVerified(
        source: File,
        destination: File,
        created: MutableList<File>,
        isCancelled: (() -> Boolean)?,
        onChunkCopied: (Long) -> Unit
    ): Long {
        require(source.isFile) { "Kaynak normal bir dosya değil: ${source.name}" }
        checkCancelled(isCancelled)
        val before = FileSnapshot(source.length(), source.lastModified())
        val sourceDigest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_BYTES)

        destination.outputStream().buffered(COPY_BUFFER_BYTES).use { output ->
            created += destination
            source.inputStream().buffered(COPY_BUFFER_BYTES).use { input ->
                while (true) {
                    checkCancelled(isCancelled)
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    sourceDigest.update(buffer, 0, read)
                    output.write(buffer, 0, read)
                    onChunkCopied(read.toLong())
                }
            }
            output.flush()
        }

        checkCancelled(isCancelled)
        require(source.exists() && source.isFile) { "Kaynak dosya kopyalama sırasında kayboldu: ${source.name}" }
        val after = FileSnapshot(source.length(), source.lastModified())
        check(after == before) { "Kaynak dosya kopyalama sırasında değişti: ${source.name}" }
        check(destination.length() == before.length) { "Dosya kopyası boyut doğrulamasından geçmedi: ${source.name}" }
        val copiedDigest = sha256(destination, isCancelled)
        checkCancelled(isCancelled)
        check(sourceDigest.digest().contentEquals(copiedDigest)) {
            "Dosya kopyası SHA-256 doğrulamasından geçmedi: ${source.name}"
        }
        return before.modifiedAt
    }

    private fun sha256(file: File, isCancelled: (() -> Boolean)?): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        file.inputStream().buffered(COPY_BUFFER_BYTES).use { input ->
            while (true) {
                checkCancelled(isCancelled)
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) continue
                digest.update(buffer, 0, read)
            }
        }
        checkCancelled(isCancelled)
        return digest.digest()
    }

    private fun rollbackCreated(created: List<File>) {
        created.asReversed().forEach { createdEntry ->
            runCatching { if (createdEntry.exists()) createdEntry.delete() }
        }
    }

    private fun removeVerifiedSource(target: File, allowedRoot: File) {
        val safeTarget = FilePathPolicy.requireMutableTarget(target, allowedRoot)
        val validated = collectValidatedTree(safeTarget, allowedRoot)
            ?: error("Taşınan kaynak ağacı güvenli biçimde doğrulanamadı; hedef kopya korundu")
        for (entry in validated.asReversed()) {
            check(!entry.exists() || entry.delete()) {
                "Kopya oluşturuldu ancak eski konum tamamen temizlenemedi; hedef kopya korundu"
            }
        }
    }

    private fun checkCancelled(isCancelled: (() -> Boolean)?) {
        if (isCancelled?.invoke() == true) throw TransferCancelledException()
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
        return "%.1f %s".format(java.util.Locale.ROOT, value, units[index])
    }
}
