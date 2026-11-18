package cl.duoc.louaraya.push

import android.content.Context
import android.util.AtomicFile
import cl.duoc.louaraya.data.remote.ConnectedLocalRepository
import cl.duoc.louaraya.data.remote.PushDevice
import cl.duoc.louaraya.data.remote.RemoteFailure
import cl.duoc.louaraya.domain.model.UserProfile
import cl.duoc.louaraya.presentation.local.SystemNotifications
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.unifiedpush.android.connector.UnifiedPush
import org.unifiedpush.android.connector.data.PushEndpoint
import java.io.File
import java.util.UUID

internal data class CampaignRegistration(
    val userId: Long, val instance: String, val ready: Boolean = false, val pendingDevice: PushDevice? = null
)
internal data class CampaignPushState(val registration: CampaignRegistration? = null, val issue: String? = null)

/** Keeps consent and connection identifiers on this device, outside Android's backup. */
internal class CampaignPush(
    private val context: Context,
    scope: String,
    private val repository: ConnectedLocalRepository,
    private val notifications: SystemNotifications
) {
    private val store = CampaignRegistrationStore(context, scope)
    val state = store.state

    fun activate(userId: Long) {
        if (!notifications.enabled()) {
            store.report("Permite las notificaciones de Android para activar los avisos de campañas.")
            return
        }
        if (NTFY_PACKAGE !in UnifiedPush.getDistributors(context)) {
            store.report("Instala y habilita ntfy como distribuidor de UnifiedPush para continuar.")
            return
        }
        store.state.value.registration?.let { previous ->
            if (previous.userId == userId) return
            disconnect(previous.userId)
        }
        val registration = CampaignRegistration(userId, UUID.randomUUID().toString())
        try {
            store.save(registration)
            UnifiedPush.saveDistributor(context, NTFY_PACKAGE)
            UnifiedPush.register(context, instance = registration.instance, messageForDistributor = "Lou Araya")
        } catch (_: Exception) {
            store.clear(registration.instance, "No se pudo activar ntfy. Comprueba que el distribuidor esté funcionando.")
        }
    }

    fun deactivate(userId: Long) {
        val instance = disconnect(userId) ?: return
        CampaignPushJobs.schedule(context, PushJob.Revoke(instance, userId))
    }

    /** A category change refreshes an existing subscription without enabling a new one. */
    fun profileSaved(profile: UserProfile) {
        val current = store.state.value.registration?.takeIf { it.userId == profile.id } ?: return
        if (!profile.notificationsEnabled) {
            deactivate(profile.id)
            return
        }
        try {
            UnifiedPush.register(context, instance = current.instance, messageForDistributor = "Lou Araya")
        } catch (_: Exception) {
            store.report("No se pudo actualizar la categoría en ntfy. Puedes volver a activar los avisos.", current.instance)
        }
    }

    /** Stops local delivery before an opt-out or account switch. Returns the identifier for server cleanup. */
    fun disconnect(userId: Long): String? {
        val current = store.state.value.registration?.takeIf { it.userId == userId } ?: return null
        if (!store.clear(current.instance)) return null
        runCatching { UnifiedPush.unregister(context, current.instance) }
        return current.instance
    }

    fun endpointChanged(instance: String, endpoint: PushEndpoint) {
        val registration = active(instance) ?: return
        val keySet = endpoint.pubKeySet
        if (keySet == null || endpoint.temporary) {
            store.report("ntfy aún no entregó un enlace cifrado para los avisos.", instance)
            return
        }
        val device = PushDevice(endpoint.url, keySet.pubKey, keySet.auth)
        // Keep the endpoint and authenticator in app-private storage, outside scheduler extras.
        if (store.queue(registration.instance, device) && !CampaignPushJobs.schedule(context, PushJob.Subscribe(instance))) {
            store.report("No se pudo agendar el registro de avisos. Vuelve a intentarlo.", instance)
        }
    }

    fun received(instance: String, content: ByteArray, decrypted: Boolean) {
        val noticeId = campaignNoticeId(content, decrypted) ?: return
        if (active(instance) != null) CampaignPushJobs.schedule(context, PushJob.Deliver(instance, noticeId))
    }

    fun registrationFailed(instance: String) {
        store.clear(instance, "No se pudo activar ntfy. Revisa su conexión e inténtalo de nuevo.")
    }

    fun unregistered(instance: String) {
        val current = active(instance) ?: return
        if (store.clear(instance, "ntfy desconectó los avisos de campañas de este dispositivo.")) {
            CampaignPushJobs.schedule(context, PushJob.Revoke(instance, current.userId))
        }
    }

    /** True asks JobScheduler to retry a transient failure with backoff. */
    suspend fun run(job: PushJob): Boolean = try {
        repository.initialize()
        when (job) {
            is PushJob.Subscribe -> {
                val current = active(job.instance)
                val device = current?.pendingDevice
                if (current != null && device != null && repository.registerPushDevice(current.userId, job.instance, device) {
                        active(job.instance) != null
                    }) {
                    store.ready(job.instance, device)
                }
            }
            is PushJob.Deliver -> deliver(job.instance, job.noticeId)
            is PushJob.Revoke -> repository.unregisterPushDevice(job.userId, job.instance)
        }
        false
    } catch (error: RemoteFailure) {
        if (job is PushJob.Subscribe) {
            store.report(if (error.canRetry) "Esperando conexión al taller para activar los avisos."
                else "El taller no pudo registrar ntfy. Revisa su configuración e inténtalo de nuevo.", job.instance)
        }
        error.canRetry && (job is PushJob.Revoke || active(job.instance) != null)
    }

    private suspend fun deliver(instance: String, noticeId: Long) {
        val registration = active(instance)?.takeIf { it.ready } ?: return
        if (!notifications.enabled()) return
        val notice = repository.pendingCampaignNotice(registration.userId, noticeId) ?: return
        val posted = withContext(Dispatchers.Main.immediate) {
            val actor = repository.session()
            active(instance)?.ready == true && actor?.id == registration.userId && actor.notificationsEnabled &&
                notifications.post(registration.userId, notice)
        }
        if (posted) repository.acknowledgeCampaignNotice(registration.userId, noticeId)
    }

    private fun active(instance: String) = store.state.value.registration?.takeIf { it.instance == instance }

    private companion object { const val NTFY_PACKAGE = "io.heckel.ntfy" }
}

internal class CampaignRegistrationStore(context: Context, scope: String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "campaign-push-$scope.json"))
    private val gson = Gson()
    private val mutable = MutableStateFlow(CampaignPushState(runCatching {
        gson.fromJson(file.readFully().toString(Charsets.UTF_8), CampaignRegistration::class.java)
    }.getOrNull()))
    val state = mutable.asStateFlow()

    @Synchronized fun save(registration: CampaignRegistration) {
        val stream = file.startWrite()
        try {
            stream.write(gson.toJson(registration).toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
        mutable.value = CampaignPushState(registration)
    }

    @Synchronized fun queue(instance: String, device: PushDevice): Boolean {
        val current = mutable.value.registration?.takeIf { it.instance == instance } ?: return false
        save(current.copy(pendingDevice = device))
        return true
    }

    @Synchronized fun ready(instance: String, device: PushDevice) {
        val current = mutable.value.registration?.takeIf { it.instance == instance && it.pendingDevice == device } ?: return
        save(current.copy(ready = true, pendingDevice = null))
    }

    @Synchronized fun clear(instance: String, issue: String? = null): Boolean {
        if (mutable.value.registration?.instance != instance) return false
        file.delete()
        mutable.value = CampaignPushState(issue = issue)
        return true
    }

    @Synchronized fun report(issue: String, instance: String? = null) {
        if (instance == null || mutable.value.registration?.instance == instance) {
            mutable.value = mutable.value.copy(issue = issue)
        }
    }
}

private val noticePayload = Regex("""[ \r\n\t]*\{[ \r\n\t]*"noticeId"[ \r\n\t]*:[ \r\n\t]*([1-9][0-9]{0,18})[ \r\n\t]*\}[ \r\n\t]*""")

internal fun campaignNoticeId(content: ByteArray, decrypted: Boolean): Long? {
    if (!decrypted || content.size > 96) return null
    return noticePayload.matchEntire(content.toString(Charsets.UTF_8))?.groupValues?.get(1)?.toLongOrNull()
}
