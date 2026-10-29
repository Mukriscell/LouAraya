package cl.duoc.louaraya.presentation.local

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cl.duoc.louaraya.AppContainer
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.ui.theme.LouArayaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w393dp-h852dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RequestQuantityUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun waitText(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) {
        val matcher = hasText(text) and hasClickAction() and isEnabled()
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNode(matcher)
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }

    @Test fun clientCanChooseQuantityReviewTotalAndCancelWithoutAListClearingAction() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("louaraya.db")
        val container = AppContainer(context)
        val cases = container.localUseCases
        runBlocking { cases.initialize(); cases.signIn("cliente.demo", "LouDemo2026!") }
        compose.setContent { LouArayaTheme { LouArayaApp(container) } }
        waitText("Escanear QR")
        compose.onNodeWithTag("tab-requests").performClick()
        compose.onNodeWithText("Limpiar solicitudes").assertDoesNotExist()
        compose.onNodeWithTag("tab-catalog").performClick()
        waitText("Encuentra tu pieza.")
        click("Tapiz Aurora")
        click("Solicitar esta pieza")
        compose.onNodeWithText("Cantidad").performScrollTo().performTextReplacement("0")
        click("Solicitar compra")
        waitText("Escribe una cantidad entera mayor a cero.")
        assertTrue(runBlocking { cases.snapshot().requests.isEmpty() })
        compose.onNodeWithText("Cantidad").performScrollTo().performTextReplacement("3")
        compose.onNodeWithText(priceLabel(135000)).performScrollTo().assertIsDisplayed()
        click("Solicitar compra")
        waitText("Solicitud registrada.")
        assertEquals(3, runBlocking { cases.snapshot().requests.single().quantity })
        click("Ver solicitud")
        compose.onNodeWithText("3 unidades").performScrollTo().assertIsDisplayed()
        click("Cancelar solicitud")
        click("Seguir con la solicitud")
        assertEquals(RequestStatus.PENDING, runBlocking { cases.snapshot().requests.single().status })
        click("Cancelar solicitud"); click("Sí, cancelar")
        waitText("Solicitud cancelada.")
        compose.onNodeWithTag("request-status-badge").performScrollTo().assertTextEquals("Cancelada")
        compose.onNodeWithText("Cancelar solicitud").assertDoesNotExist()
        compose.onNodeWithText("Entregado").assertDoesNotExist()
        val after = runBlocking { cases.snapshot() }
        assertEquals(RequestStatus.CANCELLED, after.requests.single().status)
        assertEquals(0, after.products.first { it.id == 1L }.stock)
        runBlocking { cases.signIn("admin.demo", "LouDemo2026!") }
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("tab-products").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab-requests").performClick()
        compose.onNodeWithText("Limpiar solicitudes").assertIsEnabled()
        assertTrue(runBlocking { cases.snapshot().notices.any { it.title.contains("cancelada") } })
    }
}
