package cl.duoc.louaraya.presentation

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.domain.model.RequestIntention
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement


class LocalReactiveFlowTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @get:Rule(order = 0) val permission = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            base.evaluate()
        }
    } }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val cases get() = (compose.activity.application as LouArayaApplication).container.localUseCases
    private fun waitText(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    @Test fun productChangesAppearWithoutNavigationRefreshOrRecreation() {
        compose.signInDemo("admin.demo")
        compose.onNodeWithTag("tab-products").performClick()
        waitText("Tapiz Aurora")
        val original = runBlocking { cases.snapshot().products.single { it.id == 1L } }
        val changed = "Pieza reactiva ${System.currentTimeMillis()}"
        try {
            runBlocking { cases.saveProduct(original.copy(name = changed)) }
            waitText(changed)
            compose.onNodeWithText("Tapiz Aurora").assertDoesNotExist()
        } finally {
            runBlocking { cases.saveProduct(original) }
        }
        waitText("Tapiz Aurora")
    }

    @Test fun sessionChangesAutomaticallyReplaceTheVisibleRoleAndClearForeignAlerts() {
        compose.signInDemo("admin.demo")
        compose.onNodeWithTag("tab-products").assertExists()
        runBlocking { cases.signIn("cliente.demo", "LouDemo2026!") }
        compose.waitUntil(20_000) { compose.onAllNodesWithTag("tab-catalog").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-products").assertDoesNotExist()
        runBlocking { cases.signOut() }
        waitText("Comenzar")
        compose.onNodeWithTag("tab-catalog").assertDoesNotExist()
        compose.waitUntil(10_000) { context.getSystemService(NotificationManager::class.java).activeNotifications.isEmpty() }
    }

    @Test fun requestAlertArrivesWhileActivityIsInBackgroundAndIsPersistedOnce() {
        compose.signInDemo("cliente.demo")
        val localCases = cases
        val request = runBlocking {
            val product = localCases.snapshot().products.single { it.id == 1L }
            localCases.createRequest(product.id, RequestIntention.RESERVATION, product.price, "Aviso local en segundo plano", null)
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancelAll()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        try {
            runBlocking { localCases.signIn("admin.demo", "LouDemo2026!") }
            val title = "Nueva solicitud #${request.id}"
            val deadline = System.currentTimeMillis() + 20_000
            while (System.currentTimeMillis() < deadline && manager.activeNotifications.none {
                    it.notification.extras.getString("android.title") == title }) Thread.sleep(100)
            val alerts = manager.activeNotifications.filter { it.notification.extras.getString("android.title") == title }
            assertEquals(1, alerts.size)
            assertEquals(2L, alerts.single().notification.extras.getLong("louarayaUserId"))
            val persisted = runBlocking { localCases.snapshot().notices.single { it.title == title } }
            val ackDeadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < ackDeadline && !runBlocking {
                    localCases.snapshot().notices.single { it.id == persisted.id }.delivered }) Thread.sleep(100)
            assertTrue(runBlocking { localCases.snapshot().notices.single { it.id == persisted.id }.delivered })
            runBlocking { localCases.signIn("cliente.demo", "LouDemo2026!") }
            val clearDeadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < clearDeadline && manager.activeNotifications.any {
                    it.notification.extras.getLong("louarayaUserId") == 2L }) Thread.sleep(100)
            assertTrue(manager.activeNotifications.none { it.notification.extras.getLong("louarayaUserId") == 2L })
        } finally {
            compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
            manager.cancelAll()
        }
        waitText("Escanear QR")
    }

    @Test fun tappingAnAlertWithAnUnconfirmedDeliveryMarksItReadBeforeRetrying() {
        compose.signInDemo("cliente.demo")
        val request = runBlocking {
            val product = cases.snapshot().products.single { it.id == 1L }
            cases.createRequest(product.id, RequestIntention.RESERVATION, product.price, "Confirmación de lectura local", null)
        }
        compose.signInDemo("admin.demo")
        val manager = context.getSystemService(NotificationManager::class.java)
        val title = "Nueva solicitud #${request.id}"
        compose.waitUntil(20_000) { manager.activeNotifications.any { it.notification.extras.getString("android.title") == title } }
        val alert = manager.activeNotifications.single { it.notification.extras.getString("android.title") == title }
        compose.waitUntil(10_000) { runBlocking { cases.snapshot().notices.single { it.id == alert.id.toLong() }.delivered } }

        SQLiteDatabase.openDatabase(context.getDatabasePath("louaraya.db").path, null, SQLiteDatabase.OPEN_READWRITE).use {
            it.execSQL("UPDATE local_notices SET delivered = 0 WHERE id = ?", arrayOf(alert.id.toLong()))
        }
        try {
            alert.notification.contentIntent.send()
            waitText("Solicitud #${request.id}")
            compose.waitUntil(10_000) { runBlocking { cases.snapshot().notices.single { it.id == alert.id.toLong() }.read } }

            assertFalse(runBlocking { cases.snapshot().notices.single { it.id == alert.id.toLong() }.delivered })
        } finally {
            manager.cancelAll()
        }
    }
}
