package ai.jarvis.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnlineIntentRouterTest {
    @Test fun explicitGoogleSearchIsRouted() {
        assertEquals("AI news", OnlineIntentRouter.queryFor("Google search for AI news"))
    }

    @Test fun currentInformationIsRoutedAutomatically() {
        assertEquals("what is the latest Android version", OnlineIntentRouter.queryFor("what is the latest Android version"))
    }

    @Test fun ordinaryChatStaysLocal() {
        assertNull(OnlineIntentRouter.queryFor("Explain photosynthesis simply"))
    }

    @Test fun localPhoneCommandsStayLocal() {
        assertNull(OnlineIntentRouter.queryFor("Open YouTube"))
        assertNull(OnlineIntentRouter.queryFor("What time is it"))
    }
    @Test fun localTimeQueriesStayOfflineInAutoMode() {
        assertNull(OnlineIntentRouter.queryFor("What is the current time?"))
        assertNull(OnlineIntentRouter.queryFor("What's today's date"))
    }
}
