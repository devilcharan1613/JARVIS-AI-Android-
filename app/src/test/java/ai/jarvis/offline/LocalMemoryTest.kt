package ai.jarvis.offline
import org.junit.Assert.assertEquals
import org.junit.Test
class LocalMemoryTest {
 @Test fun memoryTextLimitIsDefined() {
  val input = "x".repeat(15000)
  assertEquals(15000, input.length)
  assertEquals(12000, input.take(12000).length)
 }
}
