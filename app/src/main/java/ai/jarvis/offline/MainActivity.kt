package ai.jarvis.offline

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Brush
import android.content.Intent
import android.provider.Settings
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.os.StatFs

private val Ink = Color(0xFF07121D)
private val Panel = Color(0xFF0D2030)
private val Panel2 = Color(0xFF102A3D)
private val Cyan = Color(0xFF42DDF5)
private val Muted = Color(0xFF91A9B8)
private val White = Color(0xFFEAF7FC)
private val Green = Color(0xFF55E6B0)

class MainActivity : ComponentActivity() {
    private lateinit var memory: LocalMemory

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(7, 18, 29)
        window.navigationBarColor = android.graphics.Color.rgb(7, 18, 29)
        memory = LocalMemory(this)
        val setupPrefs = getSharedPreferences("jarvis_setup", MODE_PRIVATE)
        setContent {
            var onboarded by remember { mutableStateOf(setupPrefs.getBoolean("onboarding_complete", false)) }
            var showPhoneControlInfo by remember { mutableStateOf(false) }
            var modelPath by remember { mutableStateOf("") }
            var prompt by remember { mutableStateOf("") }
            var answer by remember { mutableStateOf("System ready. Import a compatible GGUF model to begin local inference.") }
            var busy by remember { mutableStateOf(false) }
            var memoryText by remember { mutableStateOf(memory.read()) }
            var tab by remember { mutableStateOf("HOME") }
            var history by remember { mutableStateOf(listOf<Pair<String, String>>()) }
            val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                uri?.let { selectedUri ->
                    if (!busy) {
                        busy = true
                        answer = "Checking storage and importing GGUF model…"
                        lifecycleScope.launch {
                            val previousPath = modelPath
                            var stagedFile: File? = null
                            val result = runCatching {
                                val copied = withContext(Dispatchers.IO) {
                                    val modelDir = File(filesDir, "models").apply { mkdirs() }
                                    val available = StatFs(filesDir.absolutePath).availableBytes
                                    val reserveBytes = 128L * 1024L * 1024L
                                    require(available > reserveBytes) { "Not enough free storage. Keep at least 128 MB free for Android." }
                                    val out = File(modelDir, "model-${System.currentTimeMillis()}.gguf")
                                    stagedFile = out
                                    val source = contentResolver.openInputStream(selectedUri)
                                        ?: error("Android could not open the selected model file.")
                                    var total = 0L
                                    source.use { input ->
                                        out.outputStream().buffered().use { output ->
                                            val buffer = ByteArray(64 * 1024)
                                            while (true) {
                                                val count = input.read(buffer)
                                                if (count < 0) break
                                                total += count
                                                require(total <= available - reserveBytes) {
                                                    "Import stopped because there is not enough free storage."
                                                }
                                                output.write(buffer, 0, count)
                                            }
                                        }
                                    }
                                    require(total >= 4L) { "The selected file is empty or too small to be a GGUF model." }
                                    require(GgufFileValidator.hasValidHeader(out)) {
                                        "This file does not have a valid GGUF header. Choose a .gguf model."
                                    }
                                    out.absolutePath
                                }
                                val loaded = withContext(Dispatchers.IO) { NativeLlama.loadModel(copied, 1024) }
                                if (!loaded) {
                                    if (previousPath.isNotBlank() && File(previousPath).exists()) {
                                        withContext(Dispatchers.IO) { NativeLlama.loadModel(previousPath, 1024) }
                                    }
                                    error("Model load failed. The previous model was restored when possible. Check model compatibility and available RAM.")
                                }
                                copied
                            }
                            result.onSuccess { path ->
                                modelPath = path
                                if (previousPath.isNotBlank() && previousPath != path) {
                                    runCatching { File(previousPath).delete() }
                                }
                                answer = "GGUF model loaded. Local inference is ready."
                            }.onFailure { error ->
                                stagedFile?.let { runCatching { it.delete() } }
                                answer = "Could not load model: ${error.message ?: "unknown error"}"
                            }
                            busy = false
                        }
                    }
                }
            }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Cyan, onPrimary = Ink, secondary = Green,
                    background = Ink, surface = Panel, onSurface = White,
                    onBackground = White, outline = Color(0xFF29475A)
                )
            ) {
                if (!onboarded) {
                    OnboardingScreen(onFinish = {
                        setupPrefs.edit().putBoolean("onboarding_complete", true).apply()
                        onboarded = true
                    })
                } else {
                Column(Modifier.fillMaxSize().background(Ink)) {
                    TopBar()
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (tab) {
                            "HOME" -> HomeScreen(
                                modelName = if (modelPath.isBlank()) "NO MODEL LOADED" else File(modelPath).name,
                                busy = busy, answer = answer, onImport = { picker.launch(arrayOf("application/octet-stream", "application/*")) },
                                onOpenChat = { tab = "CHAT" }, onOpenMemory = { tab = "MEMORY" }
                            )
                            "CHAT" -> ChatScreen(
                                prompt = prompt, onPrompt = { prompt = it }, answer = answer, busy = busy,
                                history = history, onSend = {
                                    if (prompt.isNotBlank() && !busy) {
                                        val question = prompt.trim()
                                        prompt = ""
                                        val localReply = LocalAgentRuntime.runLocalCommand(question, memory, this@MainActivity)
                                        if (localReply != null) {
                                            answer = localReply
                                            memoryText = memory.read()
                                            history = history + (question to localReply)
                                            LocalAgentRuntime.trace(memory, question, localReply, 0L)
                                        } else if (modelPath.isBlank()) {
                                            answer = "Import a GGUF model first. Local date, time and memory commands work without a model."
                                            history = history + (question to answer)
                                        } else {
                                            val conversationPrompt = LocalAgentRuntime.buildPrompt(question, history, memory)
                                            history = history + (question to "Thinking locally…")
                                            busy = true
                                            lifecycleScope.launch {
                                                val started = System.nanoTime()
                                                val result = runCatching {
                                                    withContext(Dispatchers.IO) { NativeLlama.generate(conversationPrompt, 220) }
                                                }.getOrElse { "Local inference failed: ${it.message ?: "unknown error"}" }
                                                val elapsed = (System.nanoTime() - started) / 1_000_000
                                                answer = result
                                                history = history.dropLast(1) + (question to result)
                                                LocalAgentRuntime.trace(memory, question, result, elapsed)
                                                busy = false
                                            }
                                        }
                                    }
                                }
                            )
                            "MEMORY" -> MemoryScreen(
                                value = memoryText, onValue = { memoryText = it },
                                onSave = { memory.write(memoryText); answer = "Private memory saved on this device." },
                                onClear = { memory.clear(); memoryText = ""; answer = "Private memory cleared." }
                            )
                            else -> SettingsScreen(modelPath.isNotBlank(), onImport = { picker.launch(arrayOf("application/octet-stream", "application/*")) }, onPhoneControl = { showPhoneControlInfo = true })
                        }
                    }
                    BottomNav(tab = tab, onSelect = { tab = it })
                }
                }
                if (showPhoneControlInfo) {
                    AlertDialog(
                        onDismissRequest = { showPhoneControlInfo = false },
                        containerColor = Panel2,
                        title = { Text("Enable phone control?", color = White) },
                        text = { Text("Accessibility access lets JARVIS read visible screen labels and perform supported actions after you enable it. Screen content can be sensitive. JARVIS cannot enable this permission itself. You can cancel or switch it off later in Android Settings.", color = Muted) },
                        confirmButton = {
                            TextButton(onClick = {
                                showPhoneControlInfo = false
                                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }) { Text("OPEN ANDROID SETTINGS", color = Cyan) }
                        },
                        dismissButton = { TextButton(onClick = { showPhoneControlInfo = false }) { Text("NOT NOW", color = Muted) } }
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        if (::memory.isInitialized) NativeLlama.unload()
        super.onDestroy()
    }
}


@Composable
private fun OnboardingScreen(onFinish: () -> Unit) {
    var page by remember { mutableIntStateOf(0) }
    val headings = listOf("Your AI. Your device.", "Private by design.", "You stay in control.")
    val body = listOf(
        "Run a personal language model directly on your Android phone. Import a compatible GGUF model to begin offline conversations.",
        "Your model and encrypted notes stay in app storage. JARVIS has no internet permission, cloud sync or remote AI service.",
        "Choose features when you need them. Phone control is optional and must be enabled manually in Android Accessibility settings."
    )
    val labels = listOf("ON-DEVICE INTELLIGENCE", "LOCAL-ONLY PRIVACY", "PERMISSIONS & SAFETY")
    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Panel2, Ink, Ink))).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        TopBar()
        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = page,
            transitionSpec = { (fadeIn(tween(260)) + scaleIn(initialScale = .96f, animationSpec = tween(260))) togetherWith fadeOut(tween(160)) },
            label = "onboarding"
        ) { current ->
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                OnboardingReactor()
                Spacer(Modifier.height(30.dp))
                Text(labels[current], color = Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(12.dp))
                Text(headings[current], color = White, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(14.dp))
                Text(body[current], color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i ->
                Box(Modifier.width(if (i == page) 25.dp else 7.dp).height(7.dp).clip(CircleShape).background(if (i == page) Cyan else BorderColor))
            }
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = { if (page < 2) page++ else onFinish() },
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(15.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)
        ) { Text(if (page == 2) "ENTER J.A.R.V.I.S." else "CONTINUE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        TextButton(onClick = onFinish) { Text("SKIP INTRODUCTION", color = Muted) }
    }
}

private val BorderColor = Color(0xFF29475A)

@Composable
private fun OnboardingReactor() {
    val transition = rememberInfiniteTransition(label = "intro_pulse")
    val pulse by transition.animateFloat(
        initialValue = .72f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    Canvas(Modifier.size(190.dp)) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = size.minDimension / 2
        drawCircle(Cyan.copy(alpha = .12f), radius * .94f, center, style = Stroke(2.dp.toPx()))
        drawCircle(Cyan.copy(alpha = .38f), radius * .72f, center, style = Stroke(2.dp.toPx()))
        drawCircle(Cyan.copy(alpha = .2f), radius * .48f, center, style = Stroke(8.dp.toPx()))
        drawCircle(Cyan.copy(alpha = pulse), radius * .28f, center, style = Stroke(2.dp.toPx()))
        drawCircle(White.copy(alpha = .8f), radius * .1f, center)
    }
}

@Composable
private fun TopBar() {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Panel2).border(1.dp, Cyan.copy(alpha = .65f), CircleShape), contentAlignment = Alignment.Center) {
            Text("J", color = Cyan, fontSize = 22.sp, fontWeight = FontWeight.Light)
        }
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text("J.A.R.V.I.S.", color = White, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
            Text("PERSONAL LOCAL INTELLIGENCE", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(Green))
            Spacer(Modifier.width(6.dp))
            Text("OFFLINE", color = Green, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
    HorizontalDivider(color = Cyan.copy(alpha = .15f))
}

@Composable
private fun HomeScreen(modelName: String, busy: Boolean, answer: String, onImport: () -> Unit, onOpenChat: () -> Unit, onOpenMemory: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(4.dp))
        Text("PERSONAL AI SYSTEM  /  READY", color = Muted, fontSize = 10.sp, letterSpacing = 2.sp)
        Text("Your world.\nYour intelligence.", color = White, fontSize = 29.sp, lineHeight = 34.sp, fontWeight = FontWeight.Light)
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(Brush.verticalGradient(listOf(Panel2, Panel))).border(1.dp, Cyan.copy(alpha = .28f), RoundedCornerShape(26.dp)).padding(20.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                ArcReactor()
                Spacer(Modifier.height(10.dp))
                Text(if (busy) "PROCESSING REQUEST" else "SYSTEM STANDBY", color = Cyan, fontSize = 11.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                Text("LOCAL AI CORE", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Metric("NETWORK", "DISABLED", Green)
                    Metric("INFERENCE", "ON DEVICE", Cyan)
                    Metric("PRIVACY", "LOCAL", Green)
                }
            }
        }
        Text("SYSTEM OVERVIEW", color = White, fontSize = 13.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusTile("01", "AI CORE", "llama.cpp", true, Modifier.weight(1f))
            StatusTile("02", "MEMORY", "Encrypted notes", true, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatusTile("03", "VOICE", "Not configured", false, Modifier.weight(1f))
            StatusTile("04", "WAKE WORD", "Not configured", false, Modifier.weight(1f))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Color(0xFF203D50), RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("MODEL STATUS", color = Cyan, fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold)
            Text(modelName, color = White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text("No model is bundled. Import your own compatible GGUF file. Smaller quantized models are recommended for phones with 4 GB RAM.", color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) {
                Text("＋  IMPORT GGUF MODEL", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel2).clickable { onOpenChat() }.padding(16.dp)) {
            Text("OPEN AI CONSOLE  ↗", color = Cyan, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Open your private, on-device AI console", color = Muted, fontSize = 12.sp)
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel2).clickable { onOpenMemory() }.padding(16.dp)) {
            Text("PRIVATE KNOWLEDGE VAULT  ↗", color = Cyan, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text("Your saved notes stay on this phone", color = Muted, fontSize = 12.sp)
        }
        Text("LAST SYSTEM MESSAGE", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
        Text(answer, color = White, fontSize = 12.sp, lineHeight = 18.sp)
        Text("Offline means no network access. Live web search, cloud sync and remote services are intentionally unavailable.", color = Muted, fontSize = 10.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun ArcReactor() {
    val transition = rememberInfiniteTransition(label = "reactor_rotation")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    Canvas(Modifier.size(174.dp)) {
        val c = Offset(size.width / 2, size.height / 2)
        val r = size.minDimension / 2
        drawCircle(Cyan.copy(alpha = .12f), r * .92f, c, style = Stroke(2.dp.toPx()))
        drawCircle(Cyan.copy(alpha = .5f), r * .76f, c, style = Stroke(1.dp.toPx()))
        drawCircle(Cyan.copy(alpha = .2f), r * .59f, c, style = Stroke(8.dp.toPx()))
        drawCircle(Cyan, r * .44f, c, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(Cyan.copy(alpha = .12f), r * .31f, c)
        drawCircle(White.copy(alpha = .8f), r * .12f, c)
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0 + rotation)
            val start = Offset(c.x + kotlin.math.cos(a).toFloat() * r * .8f, c.y + kotlin.math.sin(a).toFloat() * r * .8f)
            val end = Offset(c.x + kotlin.math.cos(a).toFloat() * r * .94f, c.y + kotlin.math.sin(a).toFloat() * r * .94f)
            drawLine(Cyan, start, end, 2.dp.toPx())
        }
    }
}

@Composable
private fun Metric(label: String, value: String, tint: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = tint, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(label, color = Muted, fontSize = 8.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun StatusTile(id: String, title: String, detail: String, ready: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Panel).border(1.dp, Color(0xFF20394A), RoundedCornerShape(14.dp)).padding(13.dp)) {
        Text(id, color = Cyan.copy(alpha = .65f), fontSize = 9.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
        Text(detail, color = Muted, fontSize = 10.sp)
        Spacer(Modifier.height(7.dp))
        Text(if (ready) "● READY" else "○ SETUP NEEDED", color = if (ready) Green else Color(0xFFFFC46B), fontSize = 8.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun ChatScreen(prompt: String, onPrompt: (String) -> Unit, answer: String, busy: Boolean, history: List<Pair<String, String>>, onSend: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("AI CONVERSATION", color = Cyan, fontSize = 11.sp, letterSpacing = 2.sp)
        Text("Private inference console", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (history.isEmpty()) item {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(16.dp)) {
                    Text("J.A.R.V.I.S.  /  SYSTEM", color = Cyan, fontSize = 10.sp, letterSpacing = 1.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(answer, color = White, fontSize = 14.sp, lineHeight = 21.sp)
                }
            }
            items(history) { pair ->
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("YOU", color = Muted, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    Text(pair.first, color = White, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel2).padding(13.dp), fontSize = 13.sp)
                    Text("J.A.R.V.I.S.", color = Cyan, fontSize = 9.sp, letterSpacing = 1.5.sp)
                    Text(pair.second, color = White, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Panel).padding(13.dp), fontSize = 13.sp, lineHeight = 19.sp)
                }
            }
        }
        OutlinedTextField(value = prompt, onValueChange = onPrompt, modifier = Modifier.fillMaxWidth(), placeholder = { Text("Enter a command or question…", color = Muted) }, minLines = 2, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF29475A), focusedTextColor = White, unfocusedTextColor = White))
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSend, enabled = !busy, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) {
            Text(if (busy) "PROCESSING LOCALLY…" else "RUN LOCAL INFERENCE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun MemoryScreen(value: String, onValue: (String) -> Unit, onSave: () -> Unit, onClear: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("PERSONAL KNOWLEDGE VAULT", color = Cyan, fontSize = 11.sp, letterSpacing = 1.7.sp)
        Text("Your memory. Your device.", color = White, fontSize = 25.sp, fontWeight = FontWeight.Light)
        Text("Save notes, preferences and reminders for your own reference. Notes are encrypted locally and relevant matching lines are retrieved into model prompts on this device.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
        OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth().weight(1f, fill = false), minLines = 10, placeholder = { Text("Write private notes…") }, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cyan, unfocusedBorderColor = Color(0xFF29475A), focusedTextColor = White, unfocusedTextColor = White))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Cyan, contentColor = Ink)) { Text("SAVE TO THIS DEVICE") }
        OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) { Text("CLEAR LOCAL MEMORY", color = Color(0xFFFF9B9B)) }
        Text("Memory is stored in app-private storage. Uninstalling the app may remove it.", color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun SettingsScreen(modelLoaded: Boolean, onImport: () -> Unit, onPhoneControl: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("SYSTEM CONFIGURATION", color = Cyan, fontSize = 11.sp, letterSpacing = 1.7.sp)
        Text("JARVIS core settings", color = White, fontSize = 25.sp, fontWeight = FontWeight.Light)
        SettingRow("NETWORK ACCESS", "Disabled by manifest. No internet permission.", "SECURE")
        SettingRow("INFERENCE ENGINE", "llama.cpp native runtime", "LOCAL")
        SettingRow("MODEL", if (modelLoaded) "A model has been selected in this session." else "No model currently loaded.", if (modelLoaded) "LOADED" else "MISSING")
        SettingRow("LANGUAGES", "English • తెలుగు • हिन्दी (depends on model)", "MODEL-BASED")
        SettingRow("VOICE CONTROL", "Speech recognition and speech output are not yet connected.", "NOT READY")
        SettingRow("WAKE WORD", "Background activation is not implemented.", "NOT READY")
        val accessibilityEnabled = JarvisPhoneAccessibilityService.isEnabled(androidx.compose.ui.platform.LocalContext.current)
        SettingRow("PHONE CONTROL", if (accessibilityEnabled) "Accessibility service enabled. Use explicit commands in Chat." else "Requires your approval in Android Accessibility settings.", if (accessibilityEnabled) "ENABLED" else "PERMISSION")
        OutlinedButton(onClick = onPhoneControl, modifier = Modifier.fillMaxWidth()) { Text("CONFIGURE PHONE CONTROL", color = Cyan) }
        SettingRow("OWNER VERIFICATION", "Sensitive actions remain blocked; biometric confirmation is not yet integrated.", "PENDING")
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("IMPORT / REPLACE MODEL", color = Cyan) }
        Text("Phone control is optional and requires enabling Android Accessibility access. JARVIS can open apps, navigate, read visible labels, tap non-sensitive controls, type into focused non-password fields and scroll. Android restricts protected system settings.", color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
    }
}

@Composable
private fun SettingRow(title: String, detail: String, status: String) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(Panel).padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(status, color = Cyan, fontSize = 8.sp, letterSpacing = .7.sp)
        }
        Text(detail, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun BottomNav(tab: String, onSelect: (String) -> Unit) {
    val entries = listOf("HOME" to "⌂", "CHAT" to "◉", "MEMORY" to "▤", "SYSTEM" to "⚙")
    Row(Modifier.fillMaxWidth().background(Panel).padding(horizontal = 8.dp, vertical = 10.dp), horizontalArrangement = Arrangement.SpaceAround) {
        entries.forEach { (name, glyph) ->
            val selected = tab == name
            Column(Modifier.clip(RoundedCornerShape(13.dp)).background(if (selected) Cyan.copy(alpha = .08f) else Color.Transparent).clickable { onSelect(name) }.padding(horizontal = 13.dp, vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(glyph, color = if (selected) Cyan else Muted, fontSize = 20.sp)
                Text(name, color = if (selected) Cyan else Muted, fontSize = 8.sp, letterSpacing = .8.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}
