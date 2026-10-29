package cl.duoc.louaraya.presentation

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.domain.model.RequestIntention
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class CommercialRequestLandscapeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun requestFormKeepsSubmitReachableWithKeyboardInLandscape() {
        compose.signInDemo("cliente.demo")
        val cases = (compose.activity.application as LouArayaApplication).container.localUseCases
        val previousRequests = runBlocking { cases.snapshot().requests.size }
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

            compose.onNodeWithTag("tab-catalog").performClick()
            waitForText("Tapiz Aurora")
            compose.onNodeWithText("Tapiz Aurora").performScrollTo().performClick()
            waitForText("Solicitar esta pieza")
            compose.onNodeWithText("Solicitar esta pieza")
                .performClick()

            compose.waitUntil(10_000) {
                compose.onAllNodes(hasText("Reserva") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }

            compose.onNodeWithText("Reserva").onParent().performScrollTo()
            compose.onNodeWithText("Reserva").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
            compose.onNodeWithText("Solicitar reserva").assertExists()
            val comment = compose.onNodeWithText("Observaciones · opcional")
            comment.performScrollTo().performClick()
            comment.performTextInput("Prueba horizontal ficticia")

            val keyboardVisible = runCatching {
                compose.waitUntil(5_000) { isKeyboardVisible() }
            }.isSuccess
            assertTrue(
                "The software keyboard did not become visible in the landscape request form.",
                keyboardVisible
            )

            val submit = compose.onNodeWithText("Solicitar reserva")
            saveKeyboardScreenshot()
            submit.assertIsDisplayed()
            assertTrue(
                "The software keyboard closed before the submit action was checked.",
                isKeyboardVisible()
            )
            submit.performClick()
            waitForText("Solicitud registrada.")
            compose.onNodeWithText("Solicitud registrada.").assertIsDisplayed()
            val saved = runBlocking { cases.snapshot().requests }
            assertEquals(previousRequests + 1, saved.size)
            assertEquals(RequestIntention.RESERVATION, saved.first().intention)
            assertEquals("Prueba horizontal ficticia", saved.first().comment)
        } catch (failure: Throwable) {
            runCatching { saveKeyboardScreenshot() }
            throw failure
        } finally {
            runCatching { closeSoftKeyboard() }
            compose.activityRule.scenario.onActivity { activity ->
                activity.requestedOrientation = if (previousConfigurationOrientation == Configuration.ORIENTATION_LANDSCAPE)
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            if (previousConfigurationOrientation != Configuration.ORIENTATION_UNDEFINED) {
                awaitOrientation(
                    previousConfigurationOrientation,
                    "The activity did not return to its original orientation after the test."
                )
            }
            compose.activityRule.scenario.onActivity { it.requestedOrientation = previousRequestedOrientation }
        }
    }

    private fun saveKeyboardScreenshot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.waitForIdle(750, 5_000)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "audit").apply { mkdirs() }
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "reserva-horizontal-teclado.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
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
            compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
