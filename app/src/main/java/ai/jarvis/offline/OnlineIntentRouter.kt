package ai.jarvis.offline

import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Routes requests that clearly need fresh public information to Google Search.
 * Local commands and ordinary chat stay on the on-device model.
 */
object OnlineIntentRouter {
    private val onlinePatterns = listOf(
        Regex("\\b(search|google|browse|look up|find online|search the web)\\b", RegexOption.IGNORE_CASE),
        Regex("\\b(latest|current|today'?s news|breaking news|live score|weather today|today's weather|current price|stock price)\\b", RegexOption.IGNORE_CASE)
    )

    fun queryFor(input: String): String? {
        val text = input.trim()
        if (text.isBlank()) return null
        val normalized = text.lowercase().replace(Regex("[?.!]+$"), "").trim()
        val localTimeOrDate = setOf(
            "time", "what time is it", "current time", "what is the current time",
            "what's the current time", "tell me the time", "date", "today's date",
            "what is the date", "what is today's date", "what's today's date"
        )
        if (normalized in localTimeOrDate) return null
        if (onlinePatterns.none { it.containsMatchIn(text) }) return null
        return text
            .replace(Regex("^(please\\s+)?(google\\s+search\\s+for|search(\\s+google)?\\s+for|google|look up|find online)\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { text }
    }

    fun openGoogleSearch(context: Context, query: String): Boolean {
        val webSearch = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(webSearch)
            true
        } catch (_: ActivityNotFoundException) {
            val browser = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(browser)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }
        }
    }
}
