package cl.duoc.louaraya.presentation.local

import android.app.Application
import cl.duoc.louaraya.domain.model.LocalNotice
import cl.duoc.louaraya.domain.model.LocalSnapshot
import cl.duoc.louaraya.domain.model.UserProfile
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalNotificationCoordinatorTest {
    private val user = UserProfile(2, "Cliente ficticio", "cliente.prueba")
    private fun notice(id: Long = 1, owner: Long = 2) = LocalNotice(
        id, owner, "Aviso de prueba", "Cambio local", "notifications", eventKey = "test/$id")

    private class FakeSystem : SystemNotifications {
        var allowed = true
        var succeeds = true
        var clears = 0
        val posts = mutableListOf<Pair<Long, Long>>()
        val active = mutableSetOf<Pair<Long, Long>>()
        override fun enabled() = allowed
        override fun retainProfile(userId: Long) = true
        override fun retainNotices(userId: Long, visibleIds: Set<Long>): Boolean {
            active.removeAll { it.first == userId && it.second !in visibleIds }
            return true
        }
        override fun clear(): Boolean { clears++; return true }
        override fun post(userId: Long, notice: LocalNotice): Boolean {
            posts += userId to notice.id
            if (succeeds) active += userId to notice.id
            return succeeds
        }
    }

    private inner class Fixture(scope: TestScope) {
        val state = MutableStateFlow(LocalSnapshot(profile = user))
        val system = FakeSystem()
        val acknowledgements = mutableListOf<Long>()
        var failAcknowledgement = false
        val coordinator = LocalNotificationCoordinator(
            snapshots = { state }, currentProfile = { state.value.profile },
            acknowledge = { ids ->
                if (failAcknowledgement) error("SQLite temporarily unavailable")
                acknowledgements += ids
                state.value = state.value.copy(notices = state.value.notices.map {
                    if (it.id in ids) it.copy(delivered = true) else it
                })
            }, system = system, scope = scope.backgroundScope)
    }

    @Test fun arrivingNoticePostsAndAcknowledgesOnceWithoutScreenOperation() = runTest {
        val f = Fixture(this)
        f.coordinator.start(); runCurrent()
        f.state.value = f.state.value.copy(notices = listOf(notice()))
        runCurrent()
        assertEquals(listOf(2L to 1L), f.system.posts)
        assertEquals(listOf(1L), f.acknowledgements)
        assertTrue(f.state.value.notices.single().delivered)
        f.state.value = f.state.value.copy(favoriteIds = setOf(10))
        runCurrent()
        assertEquals(1, f.system.posts.size)
    }

    @Test fun deniedPermissionLeavesPendingUntilPermissionRetry() = runTest {
        val f = Fixture(this)
        f.system.allowed = false
        f.state.value = f.state.value.copy(notices = listOf(notice()))
        f.coordinator.start(); runCurrent()
        assertTrue(f.system.posts.isEmpty())
        assertFalse(f.state.value.notices.single().delivered)
        f.system.allowed = true
        f.coordinator.refresh(); runCurrent()
        assertEquals(listOf(1L), f.acknowledgements)
    }

    @Test fun postingFailureWaitsForRetryInsteadOfLoopingOnOtherChanges() = runTest {
        val f = Fixture(this)
        f.system.succeeds = false
        f.state.value = f.state.value.copy(notices = listOf(notice()))
        f.coordinator.start(); runCurrent()
        f.state.value = f.state.value.copy(favoriteIds = setOf(10))
        runCurrent()
        assertEquals(1, f.system.posts.size)
        assertTrue(f.acknowledgements.isEmpty())
        f.system.succeeds = true
        f.coordinator.refresh(); runCurrent()
        assertEquals(2, f.system.posts.size)
        assertEquals(listOf(1L), f.acknowledgements)
    }

    @Test fun persistenceFailureDoesNotLoopAndCanBeRetriedOnResume() = runTest {
        val f = Fixture(this)
        f.failAcknowledgement = true
        f.state.value = f.state.value.copy(notices = listOf(notice()))
        f.coordinator.start(); runCurrent()
        f.state.value = f.state.value.copy(favoriteIds = setOf(10))
        runCurrent()
        assertEquals(1, f.system.posts.size)
        f.failAcknowledgement = false
        f.coordinator.refresh(); runCurrent()
        assertEquals(listOf(1L), f.acknowledgements)
    }

    @Test fun logoutOptOutAndForeignInboxNeverLeakAnAlert() = runTest {
        val f = Fixture(this)
        f.state.value = LocalSnapshot(profile = user.copy(notificationsEnabled = false), notices = listOf(notice()))
        f.coordinator.start(); runCurrent()
        assertTrue(f.system.posts.isEmpty())
        f.state.value = LocalSnapshot(); runCurrent()
        assertTrue(f.system.clears >= 2)
        f.state.value = LocalSnapshot(profile = user.copy(id = 3), notices = listOf(notice(), notice(2, 3)))
        runCurrent()
        assertEquals(listOf(3L to 2L), f.system.posts)
        assertEquals(listOf(2L), f.acknowledgements)
    }

    @Test fun openingNotificationDefersPendingDeliveryUntilIntentConsumed() = runTest {
        val f = Fixture(this)
        f.state.value = f.state.value.copy(notices = listOf(notice()))
        f.coordinator.refresh(deferDelivery = true)
        f.coordinator.start(); runCurrent()
        assertTrue(f.system.posts.isEmpty())
        f.coordinator.refresh(); runCurrent()
        assertEquals(listOf(1L), f.acknowledgements)
    }

    @Test fun failedObservationCanReconnectOnResume() = runTest {
        var fail = true
        val system = FakeSystem()
        val coordinator = LocalNotificationCoordinator(
            snapshots = { flow {
                if (fail) error("Temporary read failure")
                emit(LocalSnapshot(profile = user, notices = listOf(notice())))
            } }, currentProfile = { user }, acknowledge = {}, system = system, scope = backgroundScope)
        coordinator.start(); runCurrent()
        assertTrue(system.posts.isEmpty())
        fail = false
        coordinator.refresh(); runCurrent()
        assertEquals(listOf(2L to 1L), system.posts)
    }

    @Test fun clearingTheInboxRemovesPostedAlertsAndNewNoticesCanStillArrive() = runTest {
        val f = Fixture(this)
        f.state.value = f.state.value.copy(notices = listOf(notice()))
        f.coordinator.start(); runCurrent()
        assertEquals(setOf(2L to 1L), f.system.active)
        f.state.value = f.state.value.copy(notices = emptyList()); runCurrent()
        assertTrue(f.system.active.isEmpty())
        f.state.value = f.state.value.copy(notices = listOf(notice(2))); runCurrent()
        assertEquals(setOf(2L to 2L), f.system.active)
        assertEquals(listOf(2L to 1L, 2L to 2L), f.system.posts)
    }
}
