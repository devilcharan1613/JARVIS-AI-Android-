package ai.jarvis.offline
object NativeLlama {
 init { System.loadLibrary("jarvis_llama") }
 external fun loadModel(path: String, contextSize: Int): Boolean
 external fun generate(prompt: String, maxTokens: Int): String
 external fun unload()
}
