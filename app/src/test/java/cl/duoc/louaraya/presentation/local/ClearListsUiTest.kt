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
class ClearListsUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private fun click(text: String) {
        val node = compose.onNode(hasText(text) and hasClickAction())
        if (!node.isDisplayed()) node.performScrollTo()
        node.assertIsDisplayed().performClick()
    }
    private fun waitText(text: String) = compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

    @Test fun bothProfilesCanCancelOrConfirmClearingTheirOwnRequestsAndNotifications() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("louaraya.db")
        val container = AppContainer(context)
        val cases = container.localUseCases
        val request = runBlocking {
            cases.initialize(); cases.signIn("cliente.demo", "LouDemo2026!")
            cases.createRequest(1, RequestIntention.PURCHASE, cases.snapshot().products.first { it.id == 1L }.price, "Prueba UI", null).also {
                cases.signIn("admin.demo", "LouDemo2026!"); cases.updateStatus(it.id, RequestStatus.CONFIRMED)
                cases.signIn("cliente.demo", "LouDemo2026!")
            }
        }
        compose.setContent { LouArayaTheme { LouArayaApp(container) } }
        waitText("Escanear QR")
        for ((alias, id) in listOf("cliente.demo" to 1L, "admin.demo" to 2L)) {
            if (id == 2L) {
                runBlocking { cases.signIn(alias, "LouDemo2026!") }
                compose.waitUntil(15_000) { compose.onAllNodesWithTag("tab-products").fetchSemanticsNodes().isNotEmpty() }
            }
            compose.onNodeWithTag("tab-requests").performClick()
            if (id == 2L) {
                click("Limpiar solicitudes"); click("Cancelar")
                assertTrue(runBlocking { cases.snapshot().hiddenRequestIds.isEmpty() })
                click("Limpiar solicitudes"); click("Limpiar")
                compose.waitUntil(15_000) { runBlocking { request.id in cases.snapshot().hiddenRequestIds } }
                waitText("Lista de solicitudes limpiada.")
                compose.onNodeWithText("Limpiar solicitudes").assertIsNotEnabled()
            } else compose.onNodeWithText("Limpiar solicitudes").assertDoesNotExist()
            compose.onNodeWithContentDescription("Avisos (1)").performClick()
            compose.onNodeWithContentDescription("Limpiar notificaciones").assertExists()
            compose.onNodeWithContentDescription("Limpiar notificaciones").performClick(); click("Cancelar")
            assertEquals(1, runBlocking { cases.snapshot().notices.size })
            compose.onNodeWithContentDescription("Limpiar notificaciones").performClick(); click("Limpiar")
            compose.waitUntil(15_000) { runBlocking { cases.snapshot().notices.isEmpty() } }
            waitText("Notificaciones limpiadas.")
            compose.onNodeWithContentDescription("Limpiar notificaciones").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Volver").performClick()
        }
        assertEquals(1, runBlocking { cases.snapshot().requests.size })
    }
}
