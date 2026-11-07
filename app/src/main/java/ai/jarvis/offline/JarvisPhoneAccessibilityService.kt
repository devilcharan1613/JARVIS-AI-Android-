package ai.jarvis.offline

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * User-enabled phone operator. It only acts after the user explicitly enables
 * this service in Android Settings. No hidden activation or background control.
 */
class JarvisPhoneAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var active: JarvisPhoneAccessibilityService? = null
        @Volatile var emergencyStopped: Boolean = false
        fun isEnabled(context: android.content.Context): Boolean {
            val expected = context.packageName + "/" + JarvisPhoneAccessibilityService::class.java.name
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(expected, true) }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        emergencyStopped = getSharedPreferences("jarvis_setup", MODE_PRIVATE)
            .getBoolean("phone_emergency_stop", false)
        active = this
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* Screen is read on demand only. */ }
    override fun onInterrupt() {}
    override fun onDestroy() { if (active === this) active = null; super.onDestroy() }

    fun observeScreen(): String {
        if (emergencyStopped) return "Emergency STOP is active. Resume phone control explicitly."
        val root = rootInActiveWindow ?: return "No accessible app window is currently available."
        val lines = mutableListOf<String>()
        fun walk(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null || lines.size >= 100) return
            val label = (node.text?.toString() ?: node.contentDescription?.toString() ?: "").trim()
            if (label.isNotBlank() && !node.isPassword) lines += ("  ".repeat(depth.coerceAtMost(4)) + label.take(120))
            for (i in 0 until node.childCount) walk(node.getChild(i), depth + 1)
        }
        walk(root, 0)
        return if (lines.isEmpty()) "The current screen exposes no readable accessibility labels." else lines.joinToString("\n")
    }

    fun clickLabel(query: String): String {
        if (emergencyStopped) return "Emergency STOP is active. Resume phone control explicitly."
        val root = rootInActiveWindow ?: return "No active app window. Open the target app first."
        val target = findNode(root, query) ?: return "I couldn't find a visible control matching '$query'."
        if (target.isPassword) return "Blocked: password fields cannot be activated by this command."
        var node: AccessibilityNodeInfo? = target
        while (node != null) {
            if (node.isEnabled && node.isClickable && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return "Tapped '$query'. Check the screen to confirm the result."
            node = node.parent
        }
        return "The matching control is not clickable."
    }

    fun typeIntoFocusedField(value: String): String {
        if (emergencyStopped) return "Emergency STOP is active. Resume phone control explicitly."
        val root = rootInActiveWindow ?: return "No active app window."
        val target = findFocusedEditable(root) ?: return "No focused text field found. Tap a text field first."
        if (target.isPassword) return "Blocked: JARVIS will not type into password or PIN fields."
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value.take(2000)) }
        return if (target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) "Text entered. Review it before submitting." else "Android refused to enter text."
    }

    fun scroll(direction: String): String {
        if (emergencyStopped) return "Emergency STOP is active. Resume phone control explicitly."
        val root = rootInActiveWindow ?: return "No active app window."
        val node = findScrollable(root) ?: return "No scrollable area found."
        val action = if (direction.equals("up", true)) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        return if (node.performAction(action)) "Scroll $direction requested." else "The app refused the scroll action."
    }

    fun navigate(home: Boolean): String {
        if (emergencyStopped) return "Emergency STOP is active. Resume phone control explicitly."
        val action = if (home) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK
        return if (performGlobalAction(action)) if (home) "Home navigation requested." else "Back navigation requested." else "Android refused navigation."
    }

    private fun findNode(node: AccessibilityNodeInfo, query: String): AccessibilityNodeInfo? {
        val q = query.lowercase(Locale.ROOT)
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        if ((text.contains(q, true) || desc.contains(q, true)) && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) findNode(node.getChild(i), query)?.let { return it }
        return null
    }

    private fun findFocusedEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isFocused && (node.isEditable || node.actionList.any { it.id == AccessibilityNodeInfo.ACTION_SET_TEXT })) return node
        for (i in 0 until node.childCount) findFocusedEditable(node.getChild(i))?.let { return it }
        return null
    }

    private fun findScrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) findScrollable(node.getChild(i))?.let { return it }
        return null
    }
}
