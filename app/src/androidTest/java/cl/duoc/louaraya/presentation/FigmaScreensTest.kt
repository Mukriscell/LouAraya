package cl.duoc.louaraya.presentation

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.domain.model.Collection
import cl.duoc.louaraya.domain.model.RequestStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File


class FigmaScreensTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val cases get() = (compose.activity.application as LouArayaApplication).container.localUseCases

    private fun waitFor(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun click(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).performClick()
    }
    private fun back() { compose.onNodeWithContentDescription("Volver").performClick() }
    private fun shot(name: String) {
        compose.waitUntil(20_000) { compose.onAllNodes(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes().isEmpty() }
        compose.waitForIdle()

        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(750, 5_000)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.getExternalFilesDir(null), "figma-implementation").apply { mkdirs() }
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun customerCanSearchResetQuoteAndReadPersistentConfirmation() {
        compose.signInDemo("cliente.demo")
        shot("01-inicio-cliente")
        compose.onNodeWithTag("tab-catalog").performClick()
        shot("02-catalogo")
        compose.onNodeWithText("Buscar una pieza").performTextInput("sin coincidencias ficticias")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        waitFor("No encontramos\ncoincidencias.")
        shot("03-busqueda-vacia")
        compose.onNodeWithText("Limpiar búsqueda").performScrollTo().performClick()
        compose.onNodeWithText("Tapiz Aurora").performScrollTo().performClick()
        shot("04-detalle")
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE }
        shot("05-detalle-horizontal")
        compose.activityRule.scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10_000) { compose.activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT }
        click("Solicitar esta pieza")
        shot("06-compra")
        click("Reserva")
        shot("07-reserva")
        click("Cotización")
        shot("08-cotizacion")
        val before = runBlocking { cases.snapshot().requests.size }
        click("Continuar con mi idea")
        waitFor("Cuéntanos qué colores prefieres (máximo 100 caracteres).")
        assertEquals(before, runBlocking { cases.snapshot().requests.size })
        shot("09-cotizacion-validacion")
        compose.onNodeWithText("Colores").performScrollTo().performTextInput("Crudo y terracota")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        click("Continuar con mi idea")
        waitFor("Ahora, tu historia.")
        shot("08b-cotizacion-ocasion")
        compose.onNodeWithText("Fecha deseada · AAAA-MM-DD").performScrollTo().performTextInput("2099-12-05")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        back()
        waitFor("Primero, la idea.")
        compose.onNodeWithText("Colores").performScrollTo().assertTextContains("Crudo y terracota")
        click("Continuar con mi idea")
        compose.activityRule.scenario.recreate()
        waitFor("Ahora, tu historia.")
        compose.onNodeWithText("Fecha deseada · AAAA-MM-DD").performScrollTo().assertTextContains("2099-12-05")
        click("Registrar solicitud")
        waitFor("Solicitud registrada.")
        shot("10-confirmacion-cotizacion")
        val request = runBlocking { cases.snapshot().requests.first() }
        assertEquals(before + 1, runBlocking { cases.snapshot().requests.size })
        click("Ver solicitud")
        waitFor("Pendiente")
        shot("11-seguimiento-pendiente")
        compose.activityRule.scenario.recreate()
        waitFor("Pendiente")
        assertEquals(request.id, runBlocking { cases.snapshot().requests.first().id })
        back()
        back()
        back()
        compose.onNodeWithContentDescription("Mi perfil").performClick()
        shot("12-perfil")
        click("Guardar perfil")
        waitFor("Perfil actualizado.")
        shot("13-perfil-guardado")
    }

    @Test fun adminCanValidateEditorsPublishAndAdvanceAllRequestStates() {
        val fixtureRequest = runBlocking {
            cases.initialize()
            cases.signIn("cliente.demo", "LouDemo2026!")
            cases.createRequest(1, cl.duoc.louaraya.domain.model.RequestIntention.PURCHASE, 45000, "Solicitud ficticia para verificar los cuatro estados", null)
        }
        compose.signInDemo("admin.demo")
        runBlocking { cases.changeStock(fixtureRequest.productId, 1, cl.duoc.louaraya.domain.model.StockMovementType.ENTRY, "Existencias ficticias de prueba") }
        shot("20-taller")
        compose.onNodeWithTag("tab-products").performClick()
        shot("21-productos")
        click("Crear producto")
        shot("22-nueva-pieza")
        click("Guardar producto")
        waitFor("Escribe un nombre de entre 2 y 100 caracteres.")
        shot("23-validacion-producto")
        back()
        compose.onNodeWithContentDescription("Editar fotografía: Tapiz Aurora").performScrollTo().performClick()
        waitFor("Muéstrala\ncon luz natural.")
        shot("24-fotografia")
        back()
        compose.onNodeWithTag("tab-campaigns").performClick()
        shot("25-colecciones-campanas")
        click("Crear colección")
        shot("26-editor-coleccion")
        val unique = "Colección prueba ${System.currentTimeMillis()}"
        compose.onNodeWithText("Nombre de colección").performTextInput(unique)
        compose.onNodeWithText("Tapiz Aurora").performScrollTo()
        compose.onAllNodes(isToggleable())[0].performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        click("Guardar colección")
        compose.onNodeWithText("Crear campaña").performScrollTo().performClick()
        shot("27-editor-campana")
        compose.onNodeWithText("Nombre de campaña").performTextInput("Campaña de prueba")
        compose.onNodeWithText("Inicio · AAAA-MM-DD").performScrollTo().performTextInput("2099-12-10")
        compose.onNodeWithText("Fin · AAAA-MM-DD").performScrollTo().performTextInput("2099-12-01")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        click("Guardar campaña")
        waitFor("La fecha final debe ser posterior o igual al inicio.")
        shot("28-validacion-campana")
        compose.onNodeWithText("Fin · AAAA-MM-DD").performScrollTo().performTextReplacement("2099-12-20")
        compose.onNode(isToggleable()).performScrollTo().performClick()
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        click("Guardar campaña")
        compose.waitUntil(10_000) { runBlocking { cases.snapshot().campaigns.any { it.name == "Campaña de prueba" && it.published } } }
        val requests = runBlocking { cases.snapshot().requests.filter { it.id == fixtureRequest.id } }
        if (requests.isNotEmpty()) {
            compose.onNodeWithTag("tab-requests").performClick()
            shot("29-solicitudes-taller")
            compose.onNodeWithText("Solicitud #${requests.first().id} · ${requests.first().intention.let { if (it.name == "QUOTATION") "Cotización" else if (it.name == "PURCHASE") "Compra" else "Reserva" }}").performScrollTo().performClick()
            RequestStatus.entries.forEach { status ->
                compose.onNode(hasText(when (status) { RequestStatus.EXPLORATION -> "Exploración"; RequestStatus.PENDING -> "Pendiente"; RequestStatus.CONFIRMED -> "Confirmado"; RequestStatus.DELIVERED -> "Entregado" }) and hasClickAction()).performScrollTo().performClick()
                compose.waitUntil(10_000) { runBlocking { cases.snapshot().requests.first { it.id == fixtureRequest.id }.status == status } }
                shot("30-solicitud-${status.name.lowercase()}")
            }
        }
    }

    @Test fun accessErrorsAndNativeFallbacksRemainRecoverable() {
        runBlocking { cases.signOut() }
        compose.activityRule.scenario.recreate()
        waitFor("Comenzar")
        shot("40-bienvenida")
        click("Comenzar")
        waitFor("Nombre de Usuario")
        shot("41-acceso")
        compose.onNodeWithText("Nombre de Usuario").performTextInput("cliente.demo")
        compose.onNodeWithText("Contraseña").performTextInput("clave-invalida")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        click("Iniciar sesión")
        waitFor("Nombre de usuario o contraseña incorrectos.")
        shot("42-acceso-error")
        click("Crear cuenta")
        shot("43-registro")
        back()
        compose.signInDemo("cliente.demo")
        compose.onNodeWithText("Escanear QR").performScrollTo().performClick()
        shot("44-permiso-qr")
        compose.onNodeWithText("Código de etiqueta").performScrollTo().performTextInput("louaraya://product/999999")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Consultar código").performScrollTo().performClick()
        waitFor("No encontramos\nesta etiqueta.")
        shot("45-qr-invalido")
        back()
        compose.onNodeWithText("Ver ferias").performScrollTo().performClick()
        shot("46-ferias")
        back()
        compose.onNodeWithContentDescription("Avisos (", substring = true).performClick()
        shot("47-avisos")
    }
}
