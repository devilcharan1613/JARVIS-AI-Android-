package ai.jarvis.offline

import java.io.File

/** Lightweight file signature check before loading a user-selected model into native memory. */
object GgufFileValidator {
    fun hasValidHeader(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        return runCatching {
            val magic = ByteArray(4)
            file.inputStream().use { it.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "GGUF" }
        }.getOrDefault(false)
    }
}
