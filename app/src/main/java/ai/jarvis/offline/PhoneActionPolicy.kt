package ai.jarvis.offline

/** Conservative policy: risky actions must remain manual. */
object PhoneActionPolicy {
    private val sensitiveTerms = setOf("send", "pay", "payment", "buy", "purchase", "delete", "remove", "transfer", "call", "message", "confirm", "submit", "install", "uninstall", "factory reset", "password", "pin", "otp")
    fun requiresManualCompletion(label: String): Boolean =
        sensitiveTerms.any { label.contains(it, ignoreCase = true) }
    fun canAutomateTap(label: String): Boolean = !requiresManualCompletion(label)
}
