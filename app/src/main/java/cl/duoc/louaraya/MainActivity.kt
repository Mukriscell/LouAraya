package cl.duoc.louaraya

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import cl.duoc.louaraya.presentation.local.LouArayaApp
import cl.duoc.louaraya.ui.theme.LouArayaTheme

class MainActivity : ComponentActivity() {
    private var noticeRequest by mutableStateOf<Pair<Long, Long>?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        val savedUser = savedInstanceState?.getLong("pendingNoticeUser", 0) ?: 0
        val savedNotice = savedInstanceState?.getLong("pendingNoticeId", 0) ?: 0
        if (savedUser > 0 && savedNotice > 0) noticeRequest = savedUser to savedNotice
        readNotice(intent)
        val container = (application as LouArayaApplication).container
        container.localNotificationCoordinator.refresh(deferDelivery = noticeRequest != null)
        container.localNotificationCoordinator.start()
        setContent { LouArayaTheme { LouArayaApp(container, noticeRequest) { noticeRequest = null } } }
    }
    override fun onResume() {
        super.onResume()
        (application as LouArayaApplication).container.localNotificationCoordinator
            .refresh(deferDelivery = noticeRequest != null)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        noticeRequest?.let { (user, notice) ->
            outState.putLong("pendingNoticeUser", user)
            outState.putLong("pendingNoticeId", notice)
        }
        super.onSaveInstanceState(outState)
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)


        readNotice(intent)
        (application as LouArayaApplication).container.localNotificationCoordinator
            .refresh(deferDelivery = noticeRequest != null)
    }
    private fun readNotice(intent: Intent?) {
        val user = intent?.getLongExtra("noticeUserId", 0) ?: 0
        val notice = intent?.getLongExtra("noticeId", 0) ?: 0
        if (user > 0 && notice > 0) noticeRequest = user to notice
        intent?.removeExtra("noticeUserId")
        intent?.removeExtra("noticeId")
    }
}
