package dev.laxerus.omnifiles.fs

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.ArrayDeque

/**
 * Verifies fallback copies before a source is deleted.
 *
 * Normal trash/restore operations use an atomic rename when possible. This verifier is only for
 * the slower cross-filesystem/copy fallback where byte-for-byte confidence matters more than
 * throughput.
 */
object CopyIntegrityVerifier {
    private const val BUFFER_SIZE = 128 * 1024

    fun matches(source: File, destination: File): Boolean {
        val safeSource = directCanonical(source) ?: return false
        val safeDestination = directCanonical(destination) ?: return false
        if (!safeSource.exists() || !safeDestination.exists()) return false
        if (safeSource.isFile != safeDestination.isFile) return false
        if (safeSource.isDirectory != safeDestination.isDirectory) return false

        return when {
            safeSource.isFile -> matchingFiles(safeSource, safeDestination)
            safeSource.isDirectory -> matchingDirectories(safeSource, safeDestination)
            else -> false
        }
    }

    private fun directCanonical(file: File): File? = runCatching {
        val absolute = file.absoluteFile
        val canonical = file.canonicalFile
        if (absolute.path != canonical.path) null else canonical
    }.getOrNull()

    private fun matchingFiles(source: File, destination: File): Boolean {
        val sourceSnapshot = snapshot(source) ?: return false
        val destinationSnapshot = snapshot(destination) ?: return false
        if (sourceSnapshot.size != destinationSnapshot.size) return false

        val sourceDigestBefore = stableSha256(source) ?: return false
        val destinationDigestBefore = stableSha256(destination) ?: return false
        if (!sourceDigestBefore.contentEquals(destinationDigestBefore)) return false

        // Hash both files again so a mutation that races a single hash pass cannot be accepted.
        val sourceDigestAfter = stableSha256(source) ?: return false
        val destinationDigestAfter = stableSha256(destination) ?: return false
        if (!sourceDigestBefore.contentEquals(sourceDigestAfter)) return false
        if (!destinationDigestBefore.contentEquals(destinationDigestAfter)) return false
        if (!sourceDigestAfter.contentEquals(destinationDigestAfter)) return false

        return snapshot(source) == sourceSnapshot && snapshot(destination) == destinationSnapshot
    }

    private fun matchingDirectories(source: File, destination: File): Boolean {
        val sourceDigestBefore = treeDigest(source) ?: return false
        val destinationDigestBefore = treeDigest(destination) ?: return false
        if (!sourceDigestBefore.contentEquals(destinationDigestBefore)) return false

        // Re-read both trees so a mutation that races verification cannot make a stale digest pass.
        val sourceDigestAfter = treeDigest(source) ?: return false
        val destinationDigestAfter = treeDigest(destination) ?: return false
        return sourceDigestBefore.contentEquals(sourceDigestAfter) &&
            destinationDigestBefore.contentEquals(destinationDigestAfter) &&
            sourceDigestAfter.contentEquals(destinationDigestAfter)
    }

    private data class Snapshot(
        val size: Long,
        val modifiedAt: Long,
    )

    private fun snapshot(file: File): Snapshot? {
        if (!file.exists() || !file.isFile) return null
        return Snapshot(
            size = file.length().coerceAtLeast(0L),
            modifiedAt = file.lastModified().coerceAtLeast(0L),
        )
    }

    private fun stableSha256(file: File): ByteArray? {
        val before = snapshot(file) ?: return null
        val digest = sha256(file) ?: return null
        val after = snapshot(file) ?: return null
        return digest.takeIf { before == after }
    }

    private fun sha256(file: File): ByteArray? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        FileInputStream(file).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        digest.digest()
    }.getOrNull()

    private data class PendingEntry(
        val file: File,
        val relativePath: String,
    )

    private fun treeDigest(root: File): ByteArray? = runCatching {
        val canonicalRoot = directCanonical(root) ?: return@runCatching null
        if (!canonicalRoot.exists() || !canonicalRoot.isDirectory) return@runCatching null

        val digest = MessageDigest.getInstance("SHA-256")
        val pending = ArrayDeque<PendingEntry>()
        pending.addLast(PendingEntry(canonicalRoot, ""))

        while (pending.isNotEmpty()) {
            val entry = pending.removeLast()
            val canonical = directCanonical(entry.file) ?: return@runCatching null
            if (canonical.path != canonicalRoot.path && !canonical.path.startsWith(canonicalRoot.path + File.separator)) {
                return@runCatching null
            }

            when {
                canonical.isDirectory -> {
                    updateByte(digest, 0x44) // D
                    updateToken(digest, entry.relativePath)
                    val children = canonical.listFiles()?.sortedBy { it.name } ?: return@runCatching null
                    for (index in children.indices.reversed()) {
                        val child = children[index]
                        val childPath = if (entry.relativePath.isEmpty()) {
                            child.name
                        } else {
                            entry.relativePath + "/" + child.name
                        }
                        pending.addLast(PendingEntry(child, childPath))
                    }
                }

                canonical.isFile -> {
                    updateByte(digest, 0x46) // F
                    updateToken(digest, entry.relativePath)
                    val before = snapshot(canonical) ?: return@runCatching null
                    updateLong(digest, before.size)
                    val fileDigest = stableSha256(canonical) ?: return@runCatching null
                    val after = snapshot(canonical) ?: return@runCatching null
                    if (before != after) return@runCatching null
                    digest.update(fileDigest)
                }

                else -> return@runCatching null
            }
        }
        digest.digest()
    }.getOrNull()

    private fun updateToken(digest: MessageDigest, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        updateLong(digest, bytes.size.toLong())
        digest.update(bytes)
    }

    private fun updateByte(digest: MessageDigest, value: Int) {
        digest.update(byteArrayOf(value.toByte()))
    }

    private fun updateLong(digest: MessageDigest, value: Long) {
        for (shift in 56 downTo 0 step 8) {
            digest.update(byteArrayOf(((value ushr shift) and 0xffL).toByte()))
        }
    }
}
