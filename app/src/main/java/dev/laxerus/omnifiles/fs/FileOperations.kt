package dev.laxerus.omnifiles.fs

import java.io.File

object FileOperations {
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

    fun copy(source: File, destinationDirectory: File, sharedRoot: File): File {
        val root = FilePathPolicy.canonical(sharedRoot)
        val safeSource = FilePathPolicy.requireInside(source, root)
        require(safeSource.exists()) { "Kaynak öğe artık mevcut değil" }
        require(safeSource.path != root.path) { "Depolama kökünün tamamı kopyalanamaz" }

        val safeDestinationDirectory = requireDestinationDirectory(destinationDirectory, root)
        requireNotInsideSource(safeSource, safeDestinationDirectory)
        val destination = nextAvailableDestination(safeDestinationDirectory, safeSource)
        val created = mutableListOf<File>()

        try {
            copyTree(
                source = safeSource,
                destination = destination,
                allowedRoot = root,
                activeDirectories = mutableSetOf(),
                created = created
            )
        } catch (error: Throwable) {
            rollbackCreated(created)
            throw error
        }
        return destination.canonicalFile
    }

    fun move(source: File, destinationDirectory: File, sharedRoot: File): File {
        val root = FilePathPolicy.canonical(sharedRoot)
        val safeSource = FilePathPolicy.requireMutableTarget(source, root)
        require(safeSource.exists()) { "Kaynak öğe artık mevcut değil" }
        val safeDestinationDirectory = requireDestinationDirectory(destinationDirectory, root)
        requireNotInsideSource(safeSource, safeDestinationDirectory)

        val sourceParent = safeSource.parentFile?.canonicalFile ?: error("Kaynak üst klasörü bulunamadı")
        require(sourceParent.path != safeDestinationDirectory.path) { "Öğe zaten bu klasörde" }

        val destination = File(safeDestinationDirectory, safeSource.name)
        require(!destination.exists()) { "Hedef klasörde aynı adda bir öğe zaten var" }
        FilePathPolicy.requireInside(destination, root)

        if (safeSource.renameTo(destination)) return destination.canonicalFile

        val created = mutableListOf<File>()
        try {
            copyTree(
                source = safeSource,
                destination = destination,
                allowedRoot = root,
                activeDirectories = mutableSetOf(),
                created = created
            )
        } catch (error: Throwable) {
            rollbackCreated(created)
            throw error
        }

        removeVerifiedSource(safeSource, root)
        return destination.canonicalFile
    }

    private fun requireDestinationDirectory(directory: File, root: File): File {
        val safe = FilePathPolicy.requireInside(directory, root)
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

    private fun copyTree(
        source: File,
        destination: File,
        allowedRoot: File,
        activeDirectories: MutableSet<String>,
        created: MutableList<File>
    ) {
        val safeSource = FilePathPolicy.requireInside(source, allowedRoot)
        require(safeSource.exists()) { "Kopyalanacak öğe artık mevcut değil: ${source.name}" }
        require(!destination.exists()) { "Kopya hedefi zaten mevcut: ${destination.name}" }

        if (safeSource.isDirectory) {
            val canonicalPath = safeSource.canonicalPath
            require(activeDirectories.add(canonicalPath)) { "Döngüsel klasör bağlantısı algılandı" }
            try {
                check(destination.mkdir()) { "Hedef klasör oluşturulamadı: ${destination.name}" }
                created += destination
                val children = safeSource.listFiles() ?: error("Klasör okunamadı: ${safeSource.name}")
                children.forEach { child ->
                    copyTree(
                        source = child,
                        destination = File(destination, child.name),
                        allowedRoot = allowedRoot,
                        activeDirectories = activeDirectories,
                        created = created
                    )
                }
                destination.setLastModified(safeSource.lastModified())
            } finally {
                activeDirectories.remove(canonicalPath)
            }
            return
        }

        destination.outputStream().use { output ->
            created += destination
            safeSource.inputStream().use { input -> input.copyTo(output) }
        }
        check(destination.length() == safeSource.length()) { "Dosya kopyası doğrulanamadı: ${safeSource.name}" }
        destination.setLastModified(safeSource.lastModified())
    }

    private fun rollbackCreated(created: List<File>) {
        created.asReversed().forEach { createdEntry ->
            runCatching { if (createdEntry.exists()) createdEntry.delete() }
        }
    }

    private fun removeVerifiedSource(target: File, allowedRoot: File) {
        val safeTarget = FilePathPolicy.requireMutableTarget(target, allowedRoot)
        if (safeTarget.isDirectory) {
            val children = safeTarget.listFiles() ?: error("Taşınan kaynak klasör okunamadı")
            children.forEach { removeVerifiedSource(it, allowedRoot) }
        }
        check(safeTarget.delete()) {
            "Kopya oluşturuldu ancak eski konum tamamen temizlenemedi; hedef kopya korundu"
        }
    }
}
