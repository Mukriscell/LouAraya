package cl.duoc.louaraya.presentation.local

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cl.duoc.louaraya.AppContainer
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.ui.theme.LouArayaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StockFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var container: AppContainer
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("louaraya.db")
        container = AppContainer(context)
        runBlocking { container.localUseCases.initialize(); container.localUseCases.signIn("admin.demo", "LouDemo2026!") }
        compose.setContent { LouArayaTheme { LouArayaApp(container) } }
        waitFor("Hoy, en el taller.")
    }
    private fun waitFor(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun openStock() {
        compose.onNodeWithTag("tab-products").performClick()
        waitFor("Las piezas del taller.")
        compose.onAllNodesWithText("Gestionar stock")[0].performScrollTo().performClick()
        waitFor("Cantidad de unidades")
    }
    private fun movement(quantity: String, action: String) {
        compose.onNodeWithText("Cantidad de unidades").performScrollTo().performTextReplacement(quantity)
        compose.onNodeWithText(action).performClick()
    }

    @Test fun administratorCanReceiveAndSellUnitsAndFindTheBalanceAfterReturning() {
        openStock()
        movement("10", "Registrar entrada")
        waitFor("+10 unidades · Saldo: 10")
        compose.onNodeWithText("Venta directa").performScrollTo().performClick()
        movement("3", "Registrar venta directa")
        waitFor("-3 unidades · Saldo: 7")
        compose.onNodeWithText("7").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick()
        waitFor("Las piezas del taller.")
        compose.onNodeWithText("Bodega: 7 unidades").assertExists()
        assertEquals(7, runBlocking { container.localUseCases.snapshot().products.first().stock })
        compose.onAllNodesWithText("Gestionar stock")[0].performScrollTo().performClick()
        waitFor("-3 unidades · Saldo: 7")
    }

    @Test fun oversellingShowsAFieldErrorAndDoesNotChangeTheStock() {
        openStock()
        movement("2", "Registrar entrada")
        waitFor("+2 unidades · Saldo: 2")
        compose.onNodeWithText("Venta directa").performScrollTo().performClick()
        movement("3", "Registrar venta directa")
        waitFor("Stock insuficiente: hay 2 unidades en bodega.")
        assertEquals(2, runBlocking { container.localUseCases.snapshot().products.first().stock })
        movement("0", "Registrar venta directa")
        waitFor("Escribe una cantidad entera mayor a cero.")
        movement("1", "Registrar venta directa")
        waitFor("-1 unidades · Saldo: 1")
    }

    @Test fun deliveryRequiresStockAndItsStockShortcutReturnsToTheRequest() {
        val request = runBlocking {
            val cases = container.localUseCases
            cases.signIn("cliente.demo", "LouDemo2026!")
            val result = cases.createRequest(1, RequestIntention.PURCHASE, 45000, "Pedido de prueba", null)
            cases.signIn("admin.demo", "LouDemo2026!")
            result
        }
        compose.onNodeWithTag("tab-requests").performClick()
        waitFor("Solicitud #${request.id} · Compra")
        compose.onNodeWithText("Solicitud #${request.id} · Compra").performScrollTo().performClick()
        waitFor("Actualizar estado")
        compose.onNode(hasText("Entregado") and hasClickAction()).performScrollTo().performClick()
        waitFor("Stock insuficiente para entregar esta solicitud. Cantidad requerida: 1. Registra una entrada en bodega antes de continuar.")
        compose.onNodeWithText("Gestionar stock").performScrollTo().performClick()
        waitFor("Cantidad de unidades")
        movement("1", "Registrar entrada")
        waitFor("+1 unidades · Saldo: 1")
        compose.onNodeWithContentDescription("Volver").performClick()
        waitFor("Actualizar estado")
        compose.onNode(hasText("Entregado") and hasClickAction()).performScrollTo().performClick()
        waitFor("Las existencias de esta solicitud ya se descontaron de bodega. Cambiar el estado no vuelve a descontarlas ni devuelve existencias.")
        val snapshot = runBlocking { container.localUseCases.snapshot() }
        assertEquals(0, snapshot.products.first().stock)
        assertEquals(RequestStatus.DELIVERED, snapshot.requests.single().status)
    }
}
