package ai.jarvis.offline
import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
class LocalMemory(context: Context) {
 private val prefs = EncryptedSharedPreferences.create(context, "jarvis_memory",
  MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
  EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
  EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
 fun read(): String = prefs.getString("notes", "") ?: ""
 fun write(value: String) { prefs.edit().putString("notes", value.take(12000)).apply() }
 fun clear() { prefs.edit().clear().apply() }
}
