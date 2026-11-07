package ai.jarvis.offline

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Validates the fixed GGUF header before native model loading. */
object GgufFileValidator {
    private const val MIN_HEADER_BYTES = 24L
    private const val MIN_SUPPORTED_VERSION = 1
    private const val MAX_SUPPORTED_VERSION = 3

    fun hasValidHeader(file: File): Boolean {
        if (!file.isFile || file.length() < MIN_HEADER_BYTES) return false
        return runCatching {
            val header = ByteArray(8)
            file.inputStream().use { input ->
                var offset = 0
                while (offset < header.size) {
                    val read = input.read(header, offset, header.size - offset)
                    if (read <= 0) return@use
                    offset += read
                }
                if (offset != header.size) return@use
            }
            if (String(header, 0, 4, Charsets.US_ASCII) != "GGUF") return@runCatching false
            val version = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            version in MIN_SUPPORTED_VERSION..MAX_SUPPORTED_VERSION
        }.getOrDefault(false)
    }
}
