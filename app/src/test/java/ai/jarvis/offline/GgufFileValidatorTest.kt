package ai.jarvis.offline

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufFileValidatorTest {
    @Test fun acceptsGgufMagicHeader() {
        val file = File.createTempFile("jarvis-valid-", ".gguf")
        try {
            file.writeBytes(byteArrayOf(0x47, 0x47, 0x55, 0x46))
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

    @Test fun rejectsMissingFile() {
        assertFalse(GgufFileValidator.hasValidHeader(File("missing-model-file.gguf")))
    }
}
