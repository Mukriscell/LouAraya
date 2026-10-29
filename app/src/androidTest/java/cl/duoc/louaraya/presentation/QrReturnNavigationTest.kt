package cl.duoc.louaraya.presentation

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cl.duoc.louaraya.MainActivity
import org.junit.Rule
import org.junit.Test

class QrReturnNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun successfulLookupDoesNotBecomeAnErrorWhenReturningToScanner() {
        compose.signInDemo("cliente.demo")
        compose.onNodeWithText("Escanear QR").performScrollTo().performClick()
        compose.onNodeWithText("Código de etiqueta").performScrollTo().performTextInput("louaraya://product/1")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Consultar código").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Solicitar esta pieza").fetchSemanticsNodes().isNotEmpty()
        }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasContentDescription("Volver") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Código de etiqueta").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Código de etiqueta").assertExists()
        compose.onNodeWithText("No encontramos\nesta etiqueta.").assertDoesNotExist()
        compose.onNodeWithText("Consulta una\nnueva etiqueta.").assertExists()
        compose.onNodeWithText("Volver a escanear").performScrollTo().performClick()
        compose.onNodeWithText("Código de etiqueta").performScrollTo().performTextReplacement("louaraya://product/2")
        compose.onNodeWithText("Consultar código").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Solicitar esta pieza").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Colgante Brisa").assertExists()
    }

    @Test fun unknownQrStillShowsItsRealErrorAndCanBeRetried() {
        compose.signInDemo("cliente.demo")
        compose.onNodeWithText("Escanear QR").performScrollTo().performClick()
        compose.onNodeWithText("Código de etiqueta").performScrollTo().performTextInput("louaraya://product/999999")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Consultar código").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("El código no existe o ya no está disponible.").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("No encontramos\nesta etiqueta.").assertExists()
        compose.onNodeWithText("Volver a escanear").performScrollTo().performClick()
        compose.onNodeWithText("No encontramos\nesta etiqueta.").assertDoesNotExist()
        compose.onNodeWithText("Código de etiqueta").performScrollTo().performTextReplacement("louaraya://product/1")
        compose.onNodeWithText("Consultar código").performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText("Solicitar esta pieza").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
