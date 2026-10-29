package cl.duoc.louaraya.presentation

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.model.StockMovementType
import cl.duoc.louaraya.domain.usecase.FormValidation
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class DeviceUpdateValidationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val cases get() = (compose.activity.application as LouArayaApplication).container.localUseCases
    private fun waitText(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(text) and hasClickAction() and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasText(text) and hasClickAction()).performScrollTo().performClick()
    }
    private fun fill(label: String, value: String) {
        compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        closeSoftKeyboard()
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        instrumentation.waitForIdleSync()
        Thread.sleep(120)
        val folder = File(context.getExternalFilesDir(null), "validacion-2026-10-28").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun registrationRejectsEachMissingRequirementAndAcceptsACorrectedPassword() {
        runBlocking { cases.initialize(); cases.signOut() }
        compose.activityRule.scenario.recreate()
        click("Crear cuenta")
        compose.onNodeWithText(FormValidation.REGISTRATION_PASSWORD_HINT).performScrollTo().assertIsDisplayed()
        shot("01-requisitos-contrasena")
        fill("Nombre", "Cuenta de prueba")
        val alias = "movil.${System.currentTimeMillis()}"
        fill("Nombre de Usuario", alias)
        val invalid = listOf(
            "Abcde1!" to "La contraseña debe tener entre 8 y 20 caracteres.",
            ("A1!" + "a".repeat(18)) to "La contraseña debe tener entre 8 y 20 caracteres.",
            "abcdef1!" to "Incluye al menos una letra mayúscula.",
            "Abcdefg!" to "Incluye al menos un número.",
            "Abcdef12" to "Incluye al menos un carácter especial, como !, @ o #."
        )
        for ((password, error) in invalid) {
            fill("Contraseña", password)
            fill("Repetir contraseña", password)
            click("Crear mi cuenta")
            waitText(error)
            assertNull(runBlocking { cases.profile() })
        }
        compose.onNodeWithText(invalid.last().second).performScrollTo()
        shot("02-validacion-campo")
        fill("Contraseña", "MotorolaSeguro1!")
        fill("Repetir contraseña", "MotorolaSeguro1!")
        click("Crear mi cuenta")
        waitText("Escanear QR")
        assertEquals(alias, runBlocking { cases.profile()!!.alias })
        shot("03-registro-correcto")
    }

    @Test fun deliveredPurchaseDiscountsOneUnitAndRepeatedStatusChangesDoNotDuplicateIt() {
        compose.signInDemo("cliente.demo")
        val request = runBlocking {
            val product = cases.snapshot().products.single { it.id == 1L }
            cases.createRequest(1, RequestIntention.PURCHASE, product.price, "Solicitud ficticia para verificar stock", null)
        }
        compose.signInDemo("admin.demo")
        val before = runBlocking {
            cases.changeStock(1, 2, StockMovementType.ENTRY, "Preparación de prueba física")
            cases.snapshot().products.single { it.id == 1L }.stock
        }
        compose.onNodeWithTag("tab-requests").performClick()
        click("Solicitud #${request.id} · Compra")
        val deliveredButton = compose.onNode(hasText("Entregado") and hasClickAction())
        deliveredButton.performScrollTo()
        compose.mainClock.autoAdvance = false
        try {
            compose.mainClock.advanceTimeBy(1_000)
            deliveredButton.performClick()
            compose.waitUntil(15_000) { runBlocking { cases.snapshot().requests.single { it.id == request.id }.status == RequestStatus.DELIVERED } }
            compose.waitUntil(15_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodes(hasTestTag("request-status-badge") and hasText("Entregado")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.mainClock.advanceTimeBy(80)
            shot("04-estado-transicion")
            compose.mainClock.advanceTimeBy(700)
            compose.mainClock.autoAdvance = true
            compose.onNodeWithTag("request-status-badge").performScrollTo()
            shot("05-estado-entregado")
            assertEquals(before - 1, runBlocking { cases.snapshot().products.single { it.id == 1L }.stock })
            click("Confirmado")
            compose.waitUntil(15_000) { runBlocking { cases.snapshot().requests.single { it.id == request.id }.status == RequestStatus.CONFIRMED } }
            click("Entregado")
            compose.waitUntil(15_000) { runBlocking { cases.snapshot().requests.single { it.id == request.id }.status == RequestStatus.DELIVERED } }
            assertEquals(before - 1, runBlocking { cases.snapshot().products.single { it.id == 1L }.stock })
            assertEquals(1, runBlocking { cases.snapshot().stockMovements.count { it.requestId == request.id } })
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun savingStockDisplaysAMovingSpinnerAndAnimatesTheNewBalance() {
        compose.signInDemo("admin.demo")
        runBlocking {
            val stock = cases.snapshot().products.single { it.id == 1L }.stock
            if (stock > 0) cases.changeStock(1, stock, StockMovementType.WITHDRAWAL, "Preparación de prueba de animación")
        }
        compose.onNodeWithTag("tab-products").performClick()
        val manageStock = hasText("Gestionar stock") and hasClickAction() and isEnabled()
        compose.waitUntil(15_000) { compose.onAllNodes(manageStock).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(manageStock)[0].performScrollTo().performClick()
        waitText("EXISTENCIAS DISPONIBLES")
        val emptyNotice = "Sin existencias. Registra una entrada antes de vender o entregar esta pieza."
        waitText(emptyNotice)
        fill("Cantidad de unidades", "1")
        compose.onNodeWithText("Registrar entrada").performScrollTo()
        val locker = SQLiteDatabase.openDatabase(context.getDatabasePath("louaraya.db").path, null, SQLiteDatabase.OPEN_READWRITE)
        locker.beginTransaction()
        var released = false
        compose.mainClock.autoAdvance = false
        try {
            compose.mainClock.advanceTimeBy(1_000)
            compose.onNodeWithText("Registrar entrada").performClick()
            compose.waitUntil(10_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithTag("action-loading", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.mainClock.advanceTimeBy(500)
            val spinner = compose.onNodeWithTag("action-loading", useUnmergedTree = true)
            compose.onNodeWithText("Registrar entrada").assertIsNotEnabled()
            val first = spinner.captureToImage().toPixelMap()
            shot("06-spinner-primer-fotograma")
            compose.mainClock.advanceTimeBy(240)
            val second = spinner.captureToImage().toPixelMap()
            shot("07-spinner-segundo-fotograma")
            assertTrue("El spinner debe cambiar entre fotogramas", (0 until first.width).any { x ->
                (0 until first.height).any { y -> first[x, y] != second[x, y] }
            })
            locker.endTransaction()
            released = true
            compose.waitUntil(15_000) { runBlocking { cases.snapshot().products.single { it.id == 1L }.stock == 1 } }
            compose.waitUntil(15_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodes(hasTestTag("stock-balance") and hasText("1")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.mainClock.advanceTimeBy(80)
            shot("08-bodega-transicion")
            compose.mainClock.advanceTimeBy(700)
            compose.waitForIdle()
            compose.onNodeWithTag("stock-balance").assertTextEquals("1")
            compose.onNodeWithText(emptyNotice).assertDoesNotExist()
            compose.onNodeWithTag("action-loading", useUnmergedTree = true).assertDoesNotExist()
            shot("09-bodega-final")
        } finally {
            if (!released) locker.endTransaction()
            locker.close()
            compose.mainClock.autoAdvance = true
        }
    }
}
