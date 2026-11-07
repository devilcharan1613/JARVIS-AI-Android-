package ai.jarvis.offline

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufFileValidatorTest {
    private fun header(version: Int = 3): ByteArray = ByteArray(24).apply {
        this[0] = 0x47
        this[1] = 0x47
        this[2] = 0x55
        this[3] = 0x46
        this[4] = (version and 0xFF).toByte()
        this[5] = ((version shr 8) and 0xFF).toByte()
        this[6] = ((version shr 16) and 0xFF).toByte()
        this[7] = ((version shr 24) and 0xFF).toByte()
    }

    @Test fun acceptsSupportedGgufHeader() {
        val file = File.createTempFile("jarvis-valid-", ".gguf")
        try {
            file.writeBytes(header())
            assertTrue(GgufFileValidator.hasValidHeader(file))
        } finally { file.delete() }
    }

    @Test fun rejectsNonGgufAndShortFiles() {
        val file = File.createTempFile("jarvis-invalid-", ".gguf")
        try {
            file.writeBytes("NOPE".toByteArray())
            assertFalse(GgufFileValidator.hasValidHeader(file))
            file.writeBytes(byteArrayOf(0x47, 0x47, 0x55))
            assertFalse(GgufFileValidator.hasValidHeader(file))
        } finally { file.delete() }
    }

    @Test fun rejectsUnsupportedVersions() {
        val file = File.createTempFile("jarvis-version-", ".gguf")
        try {
            file.writeBytes(header(version = 99))
            assertFalse(GgufFileValidator.hasValidHeader(file))
        } finally { file.delete() }
    }

    @Test fun rejectsMissingFile() {
        assertFalse(GgufFileValidator.hasValidHeader(File("missing-model-file.gguf")))
    }
}
