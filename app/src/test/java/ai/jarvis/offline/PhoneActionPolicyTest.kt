package ai.jarvis.offline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneActionPolicyTest {
    @Test fun ordinaryControlsCanBeTapped() {
        assertTrue(PhoneActionPolicy.canAutomateTap("Settings"))
    }
    @Test fun sensitiveControlsAreNeverAutomated() {
        listOf("Send", "Pay now", "Delete account", "Transfer", "Call", "Submit", "Enter PIN").forEach {
            assertTrue(PhoneActionPolicy.requiresManualCompletion(it))
            assertFalse(PhoneActionPolicy.canAutomateTap(it))
        }
    }
}
