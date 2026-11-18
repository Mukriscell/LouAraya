package cl.duoc.louaraya.push

import android.app.Application
import cl.duoc.louaraya.data.remote.PushDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class CampaignRegistrationStoreTest {
    @Test fun keepsConsentAcrossRestartAndNeverAppliesAnOldEndpointToAnotherRegistration() {
        val context = RuntimeEnvironment.getApplication()
        val scope = UUID.randomUUID().toString()
        val account = CampaignRegistration(3, "test-installation")
        val previous = PushDevice("https://example.test/upOld?up=1", "old-public-key", "old-auth")
        val current = previous.copy(endpoint = "https://example.test/upCurrent?up=1")
        val original = CampaignRegistrationStore(context, scope)
        original.save(account)
        assertTrue(original.queue(account.instance, previous))

        val restarted = CampaignRegistrationStore(context, scope)
        assertEquals(account.copy(pendingDevice = previous), restarted.state.value.registration)
        assertFalse(restarted.queue("previous-installation", current))
        assertTrue(restarted.queue(account.instance, current))
        restarted.ready(account.instance, previous)
        assertEquals(account.copy(pendingDevice = current), restarted.state.value.registration)

        restarted.ready(account.instance, current)
        assertEquals(account.copy(ready = true), CampaignRegistrationStore(context, scope).state.value.registration)
        assertFalse(restarted.clear("previous-installation"))
        assertTrue(restarted.clear(account.instance))
        assertNull(CampaignRegistrationStore(context, scope).state.value.registration)
    }
}
