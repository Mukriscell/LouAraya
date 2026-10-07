package cl.duoc.louaraya.presentation

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.duoc.louaraya.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommercialRequestLandscapeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun requestFormKeepsSubmitReachableWithKeyboardInLandscape() {
        var previousRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        var previousConfigurationOrientation = Configuration.ORIENTATION_UNDEFINED
        compose.activityRule.scenario.onActivity { activity ->
            previousRequestedOrientation = activity.requestedOrientation
            previousConfigurationOrientation = activity.resources.configuration.orientation
        }

        try {
            if (previousConfigurationOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                compose.activityRule.scenario.onActivity { activity ->
                    activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                }
                awaitOrientation(
                    Configuration.ORIENTATION_PORTRAIT,
                    "The activity did not enter portrait to verify a real orientation change."
                )
            }
            compose.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            awaitOrientation(
                Configuration.ORIENTATION_LANDSCAPE,
                "The activity did not enter landscape after requesting that orientation."
            )

            waitForText("Tapiz Demo Aurora")
            compose.onNodeWithText("Tapiz Demo Aurora").performClick()
            compose.onNodeWithText("Solicitar compra o reserva")
                .performScrollTo()
                .performClick()

            val comment = compose.onNodeWithText("Comentario o requerimiento (opcional)")
            comment.performScrollTo().performClick()
            comment.performTextInput("Prueba horizontal ficticia")

            val keyboardVisible = runCatching {
                compose.waitUntil(5_000) { isKeyboardVisible() }
            }.isSuccess
            assertTrue(
                "The software keyboard did not become visible in the landscape request form.",
                keyboardVisible
            )

            val submit = compose.onNodeWithText("Registrar intención")
            submit.performScrollTo().assertIsDisplayed()
            assertTrue(
                "The software keyboard closed before the submit action was checked.",
                isKeyboardVisible()
            )
            submit.performClick()
            waitForText("Intención registrada")
            compose.onNodeWithText("Intención registrada").assertIsDisplayed()
        } finally {
            runCatching { closeSoftKeyboard() }
            compose.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = previousRequestedOrientation
            }
            if (previousConfigurationOrientation != Configuration.ORIENTATION_UNDEFINED) {
                awaitOrientation(
                    previousConfigurationOrientation,
                    "The activity did not return to its original orientation after the test."
                )
            }
        }
    }

    private fun awaitOrientation(expected: Int, failureMessage: String) {
        val reached = runCatching {
            compose.waitUntil(10_000) { currentOrientation() == expected }
        }.isSuccess
        assertTrue(failureMessage, reached)
        assertEquals(failureMessage, expected, currentOrientation())
    }

    private fun currentOrientation(): Int {
        var orientation = Configuration.ORIENTATION_UNDEFINED
        compose.activityRule.scenario.onActivity { activity ->
            orientation = activity.resources.configuration.orientation
        }
        return orientation
    }

    private fun isKeyboardVisible(): Boolean {
        var visible = false
        compose.activityRule.scenario.onActivity { activity ->
            visible = ViewCompat.getRootWindowInsets(activity.window.decorView)
                ?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        return visible
    }

    private fun waitForText(text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
