package cl.duoc.louaraya.presentation.local

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.model.LocalNotice
import kotlinx.coroutines.CancellationException


internal fun <T> notificationSystemCall(fallback: T, operation: () -> T): T = try {
    operation()
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (error: RuntimeException) {
    Log.w("LouArayaNotifications", "Android could not complete a notification operation.", error)
    fallback
}

internal interface SystemNotifications {
    fun enabled(): Boolean
    fun retainProfile(userId: Long): Boolean
    fun retainNotices(userId: Long, visibleIds: Set<Long>): Boolean = true
    fun clear(): Boolean
    fun post(userId: Long, notice: LocalNotice): Boolean
}

internal class AndroidSystemNotifications(private val context: Context) : SystemNotifications {
    override fun enabled(): Boolean = notificationSystemCall(false) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@notificationSystemCall false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return@notificationSystemCall false
        NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 26 || manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
    }

    override fun retainProfile(userId: Long): Boolean = notificationSystemCall(false) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return@notificationSystemCall false
        manager.activeNotifications.filter {
            it.notification.extras.containsKey("louarayaUserId") && it.notification.extras.getLong("louarayaUserId") != userId
        }.forEach { manager.cancel(it.tag, it.id) }
        true
    }

    override fun clear(): Boolean = notificationSystemCall(false) {
        NotificationManagerCompat.from(context).cancelAll()
        true
    }

    override fun retainNotices(userId: Long, visibleIds: Set<Long>): Boolean = notificationSystemCall(false) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return@notificationSystemCall false
        manager.activeNotifications.filter {
            it.notification.extras.getLong("louarayaUserId", -1) == userId &&
                it.notification.extras.getLong("louarayaNoticeId", it.id.toLong()) !in visibleIds
        }.forEach { manager.cancel(it.tag, it.id) }
        true
    }


    @SuppressLint("MissingPermission")
    override fun post(userId: Long, notice: LocalNotice): Boolean = notificationSystemCall(false) {
        if (!enabled()) return@notificationSystemCall false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return@notificationSystemCall false
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Novedades y solicitudes", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(context, MainActivity::class.java).apply {
            data = "louaraya-notice://$userId/${notice.id}".toUri()
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("noticeUserId", userId)
            putExtra("noticeId", notice.id)
        }
        val pendingIntent = PendingIntent.getActivity(context, notice.id.toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        NotificationManagerCompat.from(context).notify(notice.id.toInt(), NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(notice.title).setContentText(notice.body)
            .addExtras(Bundle().apply { putLong("louarayaUserId", userId); putLong("louarayaNoticeId", notice.id) })
            .setContentIntent(pendingIntent).setAutoCancel(true).setOnlyAlertOnce(true).build())
        true
    }

    private companion object { const val CHANNEL = "local-commercial" }
}


internal class NotificationDeliveryAttempt(private val system: SystemNotifications) {
    private val attemptedIds = mutableSetOf<Long>()

    fun pending(notices: List<LocalNotice>) = notices.filter { !it.read && !it.delivered && it.id !in attemptedIds }

    fun post(userId: Long, notices: List<LocalNotice>): List<Long> = pending(notices).mapNotNull { notice ->
        attemptedIds += notice.id
        if (notice.userId != userId) null
        else if (system.post(userId, notice)) notice.id
        else null
    }
}
