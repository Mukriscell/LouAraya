package cl.duoc.louaraya.presentation

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runAndroidComposeUiTest
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.domain.model.RequestIntention
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

@OptIn(ExperimentalTestApi::class)
class RequestsScreenJupiterTest {
    @Test
    fun showsQuantityAndTotalAndOpensRequestDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val cases = (context.applicationContext as LouArayaApplication).container.localUseCases
        val request = runBlocking {
            cases.initialize()
            cases.signIn("cliente.demo", "LouDemo2026!")
            val product = cases.snapshot().products.first { it.id == 1L }
            cases.createRequest(product.id, RequestIntention.PURCHASE, product.price, "", null, quantity = 3)
        }
        runAndroidComposeUiTest<MainActivity> {
            waitUntil(timeoutMillis = 20_000) {
                onAllNodes(hasText("Escanear QR")).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("tab-requests").performClick()
            val title = "Solicitud #${request.id} · Compra"
            val card = hasText(title) and hasClickAction()
            waitUntil(timeoutMillis = 10_000) { onAllNodes(card).fetchSemanticsNodes().isNotEmpty() }
            onNode(card).performScrollTo().assertIsDisplayed()
            onNodeWithText("3 unidades · Total de referencia: $135.000").assertIsDisplayed()
            onNode(card).performClick()
            onNodeWithText("Cantidad solicitada").performScrollTo().assertIsDisplayed()
        }
    }
}
