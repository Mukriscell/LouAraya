package cl.duoc.louaraya.presentation.local

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import cl.duoc.louaraya.domain.model.LocalNotice
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SystemNotificationsTest {
    private fun notice(id: Long, userId: Long = 2, read: Boolean = false, delivered: Boolean = false) =
        LocalNotice(id = id, userId = userId, title = "Aviso ficticio", body = "Prueba", destination = "request/1",
            date = 1, read = read, delivered = delivered, eventKey = "test/$id")

    private class FakeSystem : SystemNotifications {
        val attempted = mutableListOf<Long>()
        var failingIds = setOf<Long>()
        override fun enabled() = true
        override fun retainProfile(userId: Long) = true
        override fun clear() = true
        override fun post(userId: Long, notice: LocalNotice): Boolean {
            attempted += notice.id
            return notice.id !in failingIds
        }
    }

    @Test fun permissionAndServiceFailuresReturnFallback() {
        assertFalse(notificationSystemCall(false) { throw SecurityException("Permission revoked during notify") })
        assertFalse(notificationSystemCall(false) { throw IllegalStateException("Android service unavailable") })
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotConvertedIntoAServiceFailure() {
        notificationSystemCall(false) { throw CancellationException("Activity disposed") }
    }

    @Test(expected = AssertionError::class)
    fun fatalErrorsAreNotSwallowed() {
        notificationSystemCall(false) { throw AssertionError("Programming error") }
    }

    @Test fun partialFailureOnlyAcknowledgesSuccessfulPostsAndDoesNotRetryInALoop() {
        val system = FakeSystem().apply { failingIds = setOf(1) }
        val delivery = NotificationDeliveryAttempt(system)
        val notices = listOf(notice(1), notice(2))
        assertEquals(listOf(2L), delivery.post(2, notices))

        val persisted = listOf(notice(1), notice(2, delivered = true))
        assertTrue(delivery.post(2, persisted).isEmpty())
        assertEquals(listOf(1L, 2L), system.attempted)
        system.failingIds = emptySet()
        assertEquals(listOf(1L), NotificationDeliveryAttempt(system).post(2, persisted))
    }

    @Test fun readDeliveredAndOtherProfileNoticesAreNeverPosted() {
        val system = FakeSystem()
        assertEquals(listOf(4L), NotificationDeliveryAttempt(system).post(2,
            listOf(notice(1, read = true), notice(2, delivered = true), notice(3, userId = 1), notice(4))))
        assertEquals(listOf(4L), system.attempted)
    }

    @Test fun successfulPostIsNotRepeatedIfPersistingDeliveryFails() {
        val system = FakeSystem()
        val delivery = NotificationDeliveryAttempt(system)
        assertEquals(listOf(1L), delivery.post(2, listOf(notice(1))))
        assertTrue(delivery.post(2, listOf(notice(1))).isEmpty())
        assertEquals(listOf(1L), system.attempted)
    }

    @Test fun deniedPermissionKeepsNoticePendingAndPostsNothing() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val system = AndroidSystemNotifications(context)
        assertFalse(system.enabled())
        assertTrue(NotificationDeliveryAttempt(system).post(2, listOf(notice(1))).isEmpty())
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty())
    }

    @Test fun disabledChannelDoesNotAcknowledgeUndeliveredNotices() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("local-commercial", "Prueba", NotificationManager.IMPORTANCE_NONE))
        val system = AndroidSystemNotifications(context)
        assertFalse(system.enabled())
        assertFalse(system.post(2, notice(1)))
    }

    @Test fun clearingVisibleAlertsCancelsOnlyRemovedNoticesOfThatProfile() {
        val context = RuntimeEnvironment.getApplication()
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        val system = AndroidSystemNotifications(context)
        assertTrue(system.post(2, notice(101)))
        assertTrue(system.post(2, notice(102)))
        assertTrue(system.post(3, notice(103, userId = 3)))
        assertTrue(system.retainNotices(2, setOf(101)))
        assertEquals(setOf(101, 103), manager.activeNotifications.map { it.id }.toSet())
        assertTrue(system.retainNotices(2, emptySet()))
        assertEquals(setOf(103), manager.activeNotifications.map { it.id }.toSet())
        manager.cancelAll()
    }
}
