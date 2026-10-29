package cl.duoc.louaraya.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.LouArayaApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommercialRequestFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun reservePieceAndConsultItsStateAfterActivityRecreation() {
        compose.signInDemo("cliente.demo")
        compose.onNodeWithTag("tab-catalog").performClick()
        compose.onNodeWithText("Tapiz Aurora").performScrollTo().performClick()
        waitForText("Solicitar esta pieza")
        compose.onNodeWithText("Solicitar esta pieza").performClick()
        compose.onNodeWithText("Reserva").performScrollTo().performClick()
        val comment = "Reserva ficticia ${System.currentTimeMillis()}"
        compose.onNodeWithText("Observaciones · opcional").performScrollTo().performTextInput(comment)
        compose.onNodeWithText("Solicitar reserva").performClick()
        waitForText("Solicitud registrada.")
        compose.onNodeWithText("Ver solicitud").performScrollTo().performClick()
        waitForText("Pendiente")
        compose.activityRule.scenario.recreate()
        waitForText("Pendiente")
        compose.onNodeWithText("$comment").performScrollTo().assertIsDisplayed()
        val cases = (compose.activity.application as LouArayaApplication).container.localUseCases
        assertEquals(comment, runBlocking { cases.snapshot().requests.first().comment })
    }

    @Test fun selectedCategorySurvivesDetailBackAndReservationRetainsIt() {
        compose.signInDemo("cliente.demo")
        compose.onNodeWithTag("tab-catalog").performClick()
        compose.onNode(hasText("Matrimonios") and hasClickAction()).performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Camino de Mesa").performScrollTo().performClick()
        waitForText("Solicitar esta pieza")
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNode(hasText("Matrimonios") and hasClickAction()).assertIsSelected()
        compose.onNodeWithText("Camino de Mesa").performScrollTo().performClick()
        waitForText("Solicitar esta pieza")
        compose.onNodeWithText("Solicitar esta pieza").performClick()
        compose.onNodeWithText("Compra").performClick()
        compose.onNodeWithText("Solicitar compra").performClick()
        waitForText("Solicitud registrada.")
        val cases = (compose.activity.application as LouArayaApplication).container.localUseCases
        assertEquals("WEDDINGS", runBlocking { cases.snapshot().requests.first().campaignCategory?.name })
    }
    private fun waitForText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    }
}
