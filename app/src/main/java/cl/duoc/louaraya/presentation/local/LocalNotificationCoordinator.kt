package cl.duoc.louaraya.presentation.local

import android.util.Log
import cl.duoc.louaraya.domain.model.LocalSnapshot
import cl.duoc.louaraya.domain.model.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch


internal class LocalNotificationCoordinator(
    private val snapshots: () -> Flow<LocalSnapshot>,
    private val currentProfile: suspend () -> UserProfile?,
    private val acknowledge: suspend (List<Long>) -> Unit,
    private val system: SystemNotifications,
    private val scope: CoroutineScope
) {
    private data class Control(val revision: Long = 0, val deferred: Boolean = false)
    private val control = MutableStateFlow(Control())
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            control.collectLatest { retry ->
                var profileId: Long? = null
                var attempt = NotificationDeliveryAttempt(system)
                snapshots().catch { error -> report(error) }.collect { snapshot ->
                    try {
                        val actor = snapshot.profile
                        if (actor?.id != profileId) {
                            profileId = actor?.id
                            attempt = NotificationDeliveryAttempt(system)
                        }
                        if (actor == null) {
                            system.clear()
                        } else if (system.retainProfile(actor.id) && system.retainNotices(actor.id, snapshot.notices.map { it.id }.toSet())) {
                            if (!actor.notificationsEnabled) system.clear()
                            else if (!retry.deferred && system.enabled()) {
                                val latest = currentProfile()
                                if (latest?.id == actor.id && latest.notificationsEnabled) {
                                    val posted = attempt.post(actor.id, snapshot.notices)
                                    val after = currentProfile()
                                    if (after?.id != actor.id || !after.notificationsEnabled) system.clear()
                                    else if (posted.isNotEmpty()) acknowledge(posted)
                                }
                            }
                        }
                    } catch (error: Exception) {
                        report(error)
                    }
                }
            }
        }
    }


    fun refresh(deferDelivery: Boolean = false) {
        control.value = Control(control.value.revision + 1, deferDelivery)
    }

    private fun report(error: Throwable) {
        if (error is CancellationException) throw error
        Log.w("LouArayaNotifications", "Pending local notifications will retry when the app resumes.", error)
    }
}
