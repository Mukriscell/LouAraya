package cl.duoc.louaraya.presentation

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.File

class SplashAndClearDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @get:Rule(order = 0) val prepare = TestRule { base, _ -> object : Statement() {
        override fun evaluate() {
            compose.mainClock.autoAdvance = false
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            base.evaluate()
        }
    } }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val cases get() = (compose.activity.application as LouArayaApplication).container.localUseCases
    private fun waitText(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }
    private fun shot(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync()
        Thread.sleep(350)
        val folder = File(context.getExternalFilesDir(null), "splash-limpieza").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun splashAnimatesBeforeWelcomeAndResumesAfterActivityRecreation() {
        runBlocking { cases.initialize(); cases.signOut() }
        compose.waitUntil(20_000) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag("launch-splash").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Comenzar").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(700)
        val first = compose.onNodeWithTag("splash-wordmark").captureToImage().toPixelMap()
        shot("01-splash-letras")
        compose.mainClock.advanceTimeBy(1_900)
        val second = compose.onNodeWithTag("splash-wordmark").captureToImage().toPixelMap()
        assertTrue((0 until first.width).any { x -> (0 until first.height).any { y -> first[x, y] != second[x, y] } })
        shot("02-splash-completa")
        compose.activityRule.scenario.recreate()
        compose.mainClock.advanceTimeBy(1_200)
        compose.onNodeWithTag("launch-splash").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        waitText("Comenzar")
        shot("03-bienvenida-tras-splash")
        click("Comenzar")
        waitText("Nombre de Usuario")
        compose.onNodeWithContentDescription("Volver").performClick()
        waitText("Comenzar")
        compose.onNodeWithTag("launch-splash").assertDoesNotExist()
    }

    @Test fun bothProfilesCanClearTheirListsWithoutErasingRequestsAndNewAlertsStillArrive() {
        compose.mainClock.autoAdvance = true
        val request = runBlocking {
            cases.initialize(); cases.signIn("cliente.demo", "LouDemo2026!")
            cases.createRequest(1, RequestIntention.PURCHASE, cases.snapshot().products.first { it.id == 1L }.price, "Solicitud ficticia de validación", null).also {
                cases.signIn("admin.demo", "LouDemo2026!"); cases.updateStatus(it.id, RequestStatus.CONFIRMED)
                cases.signIn("cliente.demo", "LouDemo2026!")
            }
        }
        compose.activityRule.scenario.recreate()
        waitText("Escanear QR")
        for ((alias, userId) in listOf("cliente.demo" to 1L, "admin.demo" to 2L)) {
            if (userId == 2L) {
                runBlocking { cases.signIn(alias, "LouDemo2026!") }
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("tab-products").fetchSemanticsNodes().isNotEmpty() }
            }
            compose.onNodeWithTag("tab-requests").performClick()
            if (userId == 2L) {
                click("Limpiar solicitudes"); shot("04-confirmacion-solicitudes-$userId")
                click("Cancelar")
                assertTrue(runBlocking { cases.snapshot().hiddenRequestIds.isEmpty() })
                click("Limpiar solicitudes"); click("Limpiar")
                waitText("Lista de solicitudes limpiada.")
                compose.onNodeWithText("Limpiar solicitudes").assertIsNotEnabled()
                shot("05-solicitudes-limpias-$userId")
            } else compose.onNodeWithText("Limpiar solicitudes").assertDoesNotExist()
            val before = runBlocking { cases.snapshot().notices.size }
            assertTrue(before > 0)
            compose.onNodeWithContentDescription("Avisos ($before)").performClick()
            compose.onNodeWithContentDescription("Limpiar notificaciones").performClick(); click("Cancelar")
            assertEquals(before, runBlocking { cases.snapshot().notices.size })
            compose.onNodeWithContentDescription("Limpiar notificaciones").performClick(); click("Limpiar")
            waitText("Notificaciones limpiadas.")
            compose.onNodeWithContentDescription("Limpiar notificaciones").assertIsNotEnabled()
            compose.waitUntil(10_000) {
                context.getSystemService(NotificationManager::class.java).activeNotifications.none {
                    it.notification.extras.getLong("louarayaUserId") == userId
                }
            }
            shot("06-notificaciones-limpias-$userId")
            compose.onNodeWithContentDescription("Volver").performClick()
        }
        assertNotNull(runBlocking { cases.snapshot().requests.singleOrNull { it.id == request.id } })
        runBlocking { cases.updateStatus(request.id, RequestStatus.PENDING); cases.signIn("cliente.demo", "LouDemo2026!") }
        compose.waitUntil(15_000) { compose.onAllNodesWithContentDescription("Avisos (1)").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(15_000) { context.getSystemService(NotificationManager::class.java).activeNotifications.any {
            it.notification.extras.getLong("louarayaUserId") == 1L
        } }
        shot("07-nuevo-aviso-tras-limpiar")
    }
}
