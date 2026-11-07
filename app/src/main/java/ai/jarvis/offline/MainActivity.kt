package ai.jarvis.offline
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
 private lateinit var memory: LocalMemory
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  memory = LocalMemory(this)
  setContent {
   var modelPath by remember { mutableStateOf("") }
   var prompt by remember { mutableStateOf("") }
   var answer by remember { mutableStateOf("Import a GGUF model to begin. No model is bundled.") }
   var busy by remember { mutableStateOf(false) }
   var memoryText by remember { mutableStateOf(memory.read()) }
   val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
    uri?.let {
     busy = true
     lifecycleScope.launch {
      val copied = withContext(Dispatchers.IO) {
       val out = File(filesDir, "models/selected.gguf").apply { parentFile?.mkdirs() }
       contentResolver.openInputStream(it)?.use { input -> out.outputStream().use(input::copyTo) }
       out.absolutePath
      }
      modelPath = copied
      answer = if (withContext(Dispatchers.IO) { NativeLlama.loadModel(copied, 1024) }) "Model loaded locally." else "Model load failed. Check GGUF compatibility and available RAM."
      busy = false
     }
    }
   }
   MaterialTheme {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
     Text("JARVIS OFFLINE", style = MaterialTheme.typography.headlineMedium)
     Text("Local inference • No network permission • English / తెలుగు / हिन्दी")
     Button(onClick = { picker.launch(arrayOf("application/octet-stream", "application/*")) }, enabled = !busy) { Text("Import GGUF model") }
     Text(if (modelPath.isBlank()) "No model selected" else "Model: " + File(modelPath).name)
     OutlinedTextField(prompt, { prompt = it }, label = { Text("Ask JARVIS") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
     Button(onClick = {
      if (prompt.isNotBlank() && modelPath.isNotBlank()) {
       busy = true
       lifecycleScope.launch {
        answer = withContext(Dispatchers.IO) { NativeLlama.generate(prompt, 160) }
        busy = false
       }
      }
     }, enabled = !busy && modelPath.isNotBlank()) { Text(if (busy) "Working…" else "Generate locally") }
     if (busy) CircularProgressIndicator()
     Text("Response", style = MaterialTheme.typography.titleMedium)
     Text(answer)
     HorizontalDivider()
     Text("Private local memory", style = MaterialTheme.typography.titleMedium)
     OutlinedTextField(memoryText, { memoryText = it }, modifier = Modifier.fillMaxWidth(), minLines = 3, label = { Text("Saved notes") })
     Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Button(onClick = { memory.write(memoryText); answer = "Memory saved on this device." }) { Text("Save memory") }
      OutlinedButton(onClick = { memory.clear(); memoryText = ""; answer = "Memory cleared." }) { Text("Clear") }
     }
     Text("Voice input, speech output and wake-word are not wired in this initial scaffold. Offline speech needs a Vosk language model; offline TTS depends on installed voice data.", style = MaterialTheme.typography.bodySmall)
    }
   }
  }
 }
 override fun onDestroy() { if (::memory.isInitialized) NativeLlama.unload(); super.onDestroy() }
}
