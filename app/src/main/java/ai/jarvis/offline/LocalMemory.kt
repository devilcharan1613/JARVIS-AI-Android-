package ai.jarvis.offline

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale

/** Encrypted app-private memory. Retrieval is local and does not create a plaintext search index. */
class LocalMemory(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context, "jarvis_memory",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun read(): String = prefs.getString("notes", "") ?: ""
    fun write(value: String) { prefs.edit().putString("notes", value.take(12000)).apply() }

    fun addNote(note: String) {
        val updated = (read().lines().filter { it.isNotBlank() } + note.trim())
            .distinct().takeLast(100).joinToString("\n")
        write(updated)
    }

    fun findRelevant(query: String, maxChars: Int = 1800): String {
        val terms = query.lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.length >= 3 }.toSet()
        if (terms.isEmpty()) return ""
        return read().lines()
            .map { line -> line to terms.count { line.lowercase(Locale.ROOT).contains(it) } }
            .filter { it.second > 0 }.sortedByDescending { it.second }
            .map { it.first }.joinToString("\n").take(maxChars)
    }

    fun appendTrace(trace: String) {
        val entries = (prefs.getString("local_traces", "") ?: "")
            .lines().filter { it.isNotBlank() }.takeLast(49)
        prefs.edit().putString("local_traces", (entries + trace).joinToString("\n").takeLast(8000)).apply()
    }

    fun clear() { prefs.edit().clear().apply() }
}
