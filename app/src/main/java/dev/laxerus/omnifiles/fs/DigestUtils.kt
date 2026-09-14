package dev.laxerus.omnifiles.fs

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object DigestUtils {
    private const val BUFFER_BYTES = 64 * 1024
    private val HEX = "0123456789abcdef".toCharArray()

    fun sha256Bytes(input: InputStream): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            digest.update(buffer, 0, read)
        }
        return digest.digest()
    }

    fun sha256Bytes(file: File): ByteArray = file.inputStream().buffered(BUFFER_BYTES).use(::sha256Bytes)

    fun sha256Hex(input: InputStream): String = toHex(sha256Bytes(input))

    fun sha256Hex(file: File): String = file.inputStream().buffered(BUFFER_BYTES).use(::sha256Hex)

    fun toHex(bytes: ByteArray): String {
        val output = CharArray(bytes.size * 2)
        bytes.forEachIndexed { index, byte ->
            val value = byte.toInt() and 0xFF
            output[index * 2] = HEX[value ushr 4]
            output[index * 2 + 1] = HEX[value and 0x0F]
        }
        return output.concatToString()
    }
}
