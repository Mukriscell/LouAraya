package cl.duoc.louaraya.push

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.PersistableBundle
import cl.duoc.louaraya.LouArayaApplication
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

/** The connector's receiver dispatches only to this private in-app service. */
class CampaignPushService : PushService() {
    private val push get() = (application as LouArayaApplication).container.campaignPush

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) { push?.endpointChanged(instance, endpoint) }
    override fun onMessage(message: PushMessage, instance: String) { push?.received(instance, message.content, message.decrypted) }
    override fun onRegistrationFailed(reason: FailedReason, instance: String) { push?.registrationFailed(instance) }
    override fun onUnregistered(instance: String) { push?.unregistered(instance) }
}

/** Keeps a pending delivery alive after UnifiedPush releases its short-lived connection. */
class CampaignPushJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val running = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val job = CampaignPushJobs.decode(params.extras) ?: return false
        val push = (application as LouArayaApplication).container.campaignPush ?: return false
        running[params.jobId] = scope.launch {
            val retry = try { push.run(job) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
            running.remove(params.jobId)
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        running.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

internal sealed interface PushJob {
    val instance: String
    data class Subscribe(override val instance: String) : PushJob
    data class Deliver(override val instance: String, val noticeId: Long) : PushJob
    data class Revoke(override val instance: String, val userId: Long) : PushJob
}

internal object CampaignPushJobs {
    fun schedule(context: Context, job: PushJob): Boolean {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return false
        val params = PersistableBundle().apply {
            putString("instance", job.instance)
            when (job) {
                is PushJob.Subscribe -> putString("action", "subscribe")
                is PushJob.Deliver -> { putString("action", "deliver"); putLong("noticeId", job.noticeId) }
                is PushJob.Revoke -> { putString("action", "revoke"); putLong("userId", job.userId) }
            }
        }
        val identity = when (job) {
            is PushJob.Subscribe -> "subscribe:${job.instance}"
            is PushJob.Deliver -> "deliver:${job.instance}:${job.noticeId}"
            is PushJob.Revoke -> "revoke:${job.instance}"
        }
        val id = 0x40000000 or (identity.hashCode() and 0x3fffffff)
        val info = JobInfo.Builder(id, ComponentName(context, CampaignPushJobService::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setOverrideDeadline(1_000)
            .setExtras(params)
            .build()
        return runCatching { scheduler.schedule(info) == JobScheduler.RESULT_SUCCESS }.getOrDefault(false)
    }

    fun decode(params: PersistableBundle): PushJob? {
        val instance = params.getString("instance") ?: return null
        return when (params.getString("action")) {
            "subscribe" -> PushJob.Subscribe(instance)
            "deliver" -> PushJob.Deliver(instance, params.getLong("noticeId"))
            "revoke" -> PushJob.Revoke(instance, params.getLong("userId"))
            else -> null
        }
    }
}
