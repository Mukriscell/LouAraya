package cl.duoc.louaraya.presentation

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.domain.model.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.File

class RequestQuantityDeviceTest {
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
    private fun waitText(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) {
        val matcher = hasText(text) and hasClickAction() and isEnabled()
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNode(matcher)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }
    private fun fill(label: String, value: String) {
        compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        Espresso.closeSoftKeyboard()
    }
    private fun shot(name: String) {
        compose.waitForIdle(); instrumentation.waitForIdleSync(); Thread.sleep(350)
        val folder = File(context.getExternalFilesDir(null), "cantidad-cancelacion").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
    private fun roleReady(client: Boolean) = compose.waitUntil(15_000) {
        compose.onAllNodesWithTag(if (client) "tab-catalog" else "tab-products").fetchSemanticsNodes().isNotEmpty()
    }
    private suspend fun stock(target: Int) {
        val current = cases.snapshot().products.first { it.id == 1L }.stock
        if (current < target) cases.changeStock(1, target - current, StockMovementType.ENTRY, "Preparación de prueba física")
        else if (current > target) cases.changeStock(1, current - target, StockMovementType.WITHDRAWAL, "Preparación de prueba física")
    }

    @Test fun clientChoosesQuantityAndCancelsAConfirmedRequestWithAnAdminNotice() {
        runBlocking { cases.initialize(); cases.signIn("cliente.demo", "LouDemo2026!") }
        compose.activityRule.scenario.recreate(); roleReady(true)
        compose.onNodeWithTag("tab-requests").performClick()
        compose.onNodeWithText("Limpiar solicitudes").assertDoesNotExist()
        compose.onNodeWithTag("tab-catalog").performClick()
        waitText("Encuentra tu pieza."); click("Tapiz Aurora"); click("Solicitar esta pieza")
        fill("Cantidad", "0"); click("Solicitar compra")
        waitText("Escribe una cantidad entera mayor a cero.")
        fill("Cantidad", "3")
        compose.onNodeWithText("Total de referencia").performScrollTo()
        shot("01-cantidad-y-total")
        click("Solicitar compra"); waitText("Solicitud registrada.")
        val request = runBlocking { cases.snapshot().requests.maxBy { it.id } }
        assertEquals(3, request.quantity); assertEquals(135000L, request.total)
        click("Ver solicitud")
        compose.onNodeWithText("3 unidades").performScrollTo().assertIsDisplayed()
        repeat(3) {
            if (compose.onAllNodesWithTag("tab-catalog").fetchSemanticsNodes().isEmpty()) Espresso.pressBack()
        }
        runBlocking { cases.signIn("admin.demo", "LouDemo2026!"); stock(5); cases.updateStatus(request.id, RequestStatus.CONFIRMED); cases.signIn("cliente.demo", "LouDemo2026!") }
        roleReady(true); compose.onNodeWithTag("tab-requests").performClick()
        click("Solicitud #${request.id} · Compra")
        click("Cancelar solicitud"); shot("02-confirmacion-cancelar")
        click("Seguir con la solicitud")
        assertEquals(RequestStatus.CONFIRMED, runBlocking { cases.snapshot().requests.first { it.id == request.id }.status })
        click("Cancelar solicitud"); click("Sí, cancelar"); waitText("Solicitud cancelada.")
        compose.onNodeWithTag("request-status-badge").performScrollTo().assertTextEquals("Cancelada")
        compose.onNodeWithText("Cancelar solicitud").assertDoesNotExist()
        compose.onNodeWithText("Entregado").assertDoesNotExist()
        shot("03-solicitud-cancelada")
        runBlocking { cases.signIn("admin.demo", "LouDemo2026!") }; roleReady(false)
        val snapshot = runBlocking { cases.snapshot() }
        assertEquals(5, snapshot.products.first { it.id == 1L }.stock)
        assertTrue(snapshot.notices.any { it.eventKey == "cancel/${request.id}" })
        compose.onNodeWithTag("tab-requests").performClick()
        compose.onNodeWithText("Limpiar solicitudes").assertIsEnabled()
        shot("04-lista-admin")
    }

    @Test fun deliveryUsesAllRequestedUnitsAndTheTrashIconClearsNotifications() {
        val request = runBlocking {
            cases.initialize(); cases.signIn("cliente.demo", "LouDemo2026!")
            cases.createRequest(1, RequestIntention.RESERVATION, 45000, "Cuatro unidades para validación", null, quantity = 4).also {
                cases.signIn("admin.demo", "LouDemo2026!"); stock(3)
            }
        }
        compose.activityRule.scenario.recreate(); roleReady(false)
        compose.onNodeWithTag("tab-requests").performClick()
        click("Solicitud #${request.id} · Reserva")
        click("Entregado")
        waitText("Stock insuficiente para entregar esta solicitud. Cantidad requerida: 4. Registra una entrada en bodega antes de continuar.")
        assertEquals(3, runBlocking { cases.snapshot().products.first { it.id == 1L }.stock })
        assertEquals(RequestStatus.PENDING, runBlocking { cases.snapshot().requests.first { it.id == request.id }.status })
        runBlocking { stock(5) }
        click("Entregado"); waitText("Estado actualizado")
        compose.onNodeWithTag("request-status-badge").performScrollTo().assertTextEquals("Entregado")
        assertEquals(1, runBlocking { cases.snapshot().products.first { it.id == 1L }.stock })
        assertEquals(-4, runBlocking { cases.snapshot().stockMovements.single { it.requestId == request.id }.quantityDelta })
        click("Confirmado"); waitText("Estado actualizado")
        click("Entregado"); waitText("Estado actualizado")
        assertEquals(1, runBlocking { cases.snapshot().products.first { it.id == 1L }.stock })
        assertEquals(1, runBlocking { cases.snapshot().stockMovements.count { it.requestId == request.id } })
        shot("05-entrega-cuatro-unidades")
        runBlocking { cases.signIn("cliente.demo", "LouDemo2026!") }; roleReady(true)
        compose.onNodeWithTag("tab-requests").performClick(); click("Solicitud #${request.id} · Reserva")
        compose.onNodeWithText("Cancelar solicitud").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNode(hasContentDescription("Avisos", substring = true)).performClick()
        compose.onNodeWithText("Limpiar notificaciones").assertDoesNotExist()
        compose.onNodeWithContentDescription("Limpiar notificaciones").assertIsEnabled()
        shot("06-papelera-notificaciones")
        compose.onNodeWithContentDescription("Limpiar notificaciones").performClick(); click("Limpiar")
        waitText("Notificaciones limpiadas.")
        compose.onNodeWithContentDescription("Limpiar notificaciones").assertIsNotEnabled()
        compose.waitUntil(10_000) { context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.notification.extras.getLong("louarayaUserId") == 1L } }
        shot("07-notificaciones-limpias")
    }
}
