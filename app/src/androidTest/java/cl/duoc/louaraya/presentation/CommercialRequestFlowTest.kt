package cl.duoc.louaraya.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.pressBack
import cl.duoc.louaraya.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifica la navegación real y una reserva sintética persistida, sin cambiar estados administrativos. */
@RunWith(AndroidJUnit4::class)
class CommercialRequestFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun reservePieceAndConsultItsStateAfterActivityRecreation() {
        waitForText("Tapiz Demo Aurora")
        compose.onNodeWithText("Tapiz Demo Aurora").performClick()
        waitForText("Solicitar compra o reserva")
        compose.onNodeWithText("Solicitar compra o reserva").performScrollTo().performClick()
        waitForText("Reserva")
        compose.onNodeWithText("Reserva").performScrollTo().performClick()
        compose.onNodeWithText("Campaña asociada: Decoración de Interiores").assertIsDisplayed()
        compose.onNodeWithText("Comentario o requerimiento (opcional)")
            .performScrollTo().performTextInput("Ambientación ficticia para prueba")
        closeSoftKeyboard()
        compose.onNodeWithText("Registrar intención").performScrollTo().performClick()
        waitForText("Intención registrada")
        compose.onNodeWithText("Campaña: Decoración de Interiores").assertIsDisplayed()
        val number = compose.onNode(hasText("Solicitud #", substring = true))
            .fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        compose.onNodeWithText("Estado: Pendiente").assertIsDisplayed()
        compose.onNodeWithText("Ver solicitudes").performClick()
        waitForText("$number · Reserva")
        compose.onNodeWithText("$number · Reserva").performClick()
        waitForText("Categoría: Decoración de Interiores")
        compose.onNodeWithText("Categoría: Decoración de Interiores").assertIsDisplayed()
        compose.onNodeWithText("Intención: Reserva").assertIsDisplayed()
        compose.onNodeWithText("Requerimiento: Ambientación ficticia para prueba").performScrollTo().assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        waitForText(number)
        compose.onNodeWithText(number).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Estado: Pendiente").assertIsDisplayed()

        pressBack()
        waitForText("$number · Reserva")
        pressBack()
        waitForText("Catálogo")
        compose.onNodeWithText("Tapiz Demo Aurora").performClick()
        waitForText("Solicitar compra o reserva")
        compose.onNodeWithText("Solicitar compra o reserva").performScrollTo().performClick()
        waitForText("Registrar intención")
        compose.onNodeWithText("Registrar intención").performScrollTo().assertIsDisplayed()
        assertTrue(
            compose.onAllNodes(hasText("Intención registrada"))
                .fetchSemanticsNodes().isEmpty()
        )
    }

    @Test
    fun selectedCampaignSurvivesDetailBackAndFlowsIntoRequest() {
        waitForText("Todos")
        compose.onNodeWithText("Matrimonios").performClick().assertIsSelected()
        compose.onNodeWithText("Todos").assertIsNotSelected()
        waitForText("Camino de Mesa Demo")
        compose.onNodeWithText("Camino de Mesa Demo").performClick()
        waitForText("Camino de Mesa Demo")
        compose.onNodeWithText("Matrimonios").assertIsDisplayed()

        compose.onNodeWithContentDescription("Volver").performClick()
        waitForText("Todos")
        compose.onNodeWithText("Matrimonios").assertIsSelected()
        compose.onNodeWithText("Camino de Mesa Demo").performClick()
        compose.onNodeWithText("Solicitar compra o reserva").performScrollTo().performClick()
        waitForText("Campaña asociada: Matrimonios")
        compose.onNodeWithText("Registrar intención").performScrollTo().performClick()
        waitForText("Campaña: Matrimonios")
        val number = compose.onNode(hasText("Solicitud #", substring = true))
            .fetchSemanticsNode().config[SemanticsProperties.Text].first().text
        compose.onNodeWithText("Ver solicitudes").performClick()

        waitForText("$number · Compra")
        compose.onNodeWithText("$number · Compra").performClick()
        waitForText("Categoría: Matrimonios")
        compose.onNodeWithText("Estado: Pendiente").assertIsDisplayed()
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
