package cl.duoc.louaraya.presentation

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AuthScreensDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val cases get() = (compose.activity.application as LouArayaApplication).container.localUseCases
    private val title = "Cada nudo,\nuna historia."
    private fun waitText(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun click(text: String) {
        val matcher = hasText(text) and hasClickAction() and isEnabled()
        compose.waitUntil(15_000) { compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(matcher).performScrollTo().performClick()
    }
    private fun fill(label: String, value: String) {
        compose.onNodeWithText(label).performScrollTo().performTextReplacement(value)
        Espresso.closeSoftKeyboard()
    }
    private fun publicStart() {
        runBlocking { cases.initialize(); cases.signOut() }
        compose.activityRule.scenario.recreate()
        waitText("Comenzar")
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(120)
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "acceso-figma-2026-10-28").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun theThreePublicScreensHaveTheirOwnContentAndBackPreservesTheLoginDraft() {
        publicStart()
        compose.onNodeWithText("Nombre de Usuario").assertDoesNotExist()
        compose.onNodeWithText("Contraseña").assertDoesNotExist()
        shot("01-bienvenida")
        click("Comenzar")
        waitText("Nombre de Usuario")
        compose.onNodeWithText("Nombre").assertDoesNotExist()
        shot("02-inicio-sesion")
        fill("Nombre de Usuario", "cliente.demo")
        click("Crear cuenta")
        waitText("Nombre")
        shot("03-registro")
        fill("Nombre", "Borrador local")
        compose.activityRule.scenario.recreate()
        waitText("Nombre")
        compose.onNodeWithText("Nombre").assertTextContains("Borrador local")
        Espresso.pressBack()
        waitText("Nombre de Usuario")
        compose.onNodeWithText("Nombre de Usuario").assertTextContains("cliente.demo")
        Espresso.pressBack()
        waitText("Comenzar")
        click("Crear cuenta")
        waitText("Nombre")
        click("Ya tengo cuenta")
        waitText("Nombre de Usuario")
        compose.onNodeWithText("Nombre").assertDoesNotExist()
        Espresso.pressBack()
        waitText("Comenzar")
    }

    @Test fun customerAndAdminKeepTheirOwnPanelAndAnActiveSessionSkipsWelcome() {
        publicStart()
        click("Comenzar")
        waitText("Nombre de Usuario")
        fill("Nombre de Usuario", "cliente.demo")
        fill("Contraseña", "LouDemo2026!")
        click("Iniciar sesión")
        waitText("Escanear QR")
        compose.onNodeWithTag("tab-favorites").assertExists()
        compose.onNodeWithTag("tab-products").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mi perfil").performClick()
        click("Cerrar sesión / cambiar usuario")
        waitText("Comenzar")
        compose.onNodeWithText("Nombre de Usuario").assertDoesNotExist()
        click("Comenzar")
        waitText("Nombre de Usuario")
        fill("Nombre de Usuario", "admin.demo")
        fill("Contraseña", "LouDemo2026!")
        click("Iniciar sesión")
        waitText("Escanear QR")
        compose.onNodeWithTag("tab-products").assertExists()
        compose.activityRule.scenario.recreate()
        waitText("Escanear QR")
        compose.onNodeWithText("Comenzar").assertDoesNotExist()
        compose.onNodeWithTag("tab-favorites").assertDoesNotExist()
    }

    @Test fun theHeadingMovesBetweenFramesWithoutChangingItsAccessibleTextOrBounds() {
        compose.mainClock.autoAdvance = false
        try {
            runBlocking { cases.initialize(); cases.signOut() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(20_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
            }
            compose.mainClock.advanceTimeBy(400)
            val heading = compose.onNodeWithText(title)
            val bounds = heading.fetchSemanticsNode().boundsInRoot
            val first = heading.captureToImage().toPixelMap()
            shot("04-titulo-entrada")
            compose.mainClock.advanceTimeBy(700)
            val second = heading.captureToImage().toPixelMap()
            assertEquals(bounds, heading.fetchSemanticsNode().boundsInRoot)
            assertEquals(1, compose.onAllNodesWithText(title).fetchSemanticsNodes().size)
            assertTrue("Las palabras deben cambiar entre fotogramas", (0 until first.width).any { x ->
                (0 until first.height).any { y -> first[x, y] != second[x, y] }
            })
            compose.mainClock.advanceTimeBy(2_000)
            shot("05-titulo-final")
            compose.onNodeWithText("Comenzar").assertIsEnabled()
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun loginWorksWithTheKeyboardInLandscape() {
        publicStart()
        click("Comenzar")
        waitText("Nombre de Usuario")
        compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        try {
            compose.waitUntil(15_000) { compose.activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE }
            compose.onNodeWithText("Nombre de Usuario").performScrollTo().performTextReplacement("cliente.demo")
            compose.onNodeWithText("Contraseña").performScrollTo().performTextReplacement("LouDemo2026!")
            compose.onNode(hasText("Iniciar sesión") and hasClickAction()).assertIsDisplayed()
            shot("06-acceso-horizontal")
            compose.onNode(hasText("Iniciar sesión") and hasClickAction()).performClick()
            Espresso.closeSoftKeyboard()
            waitText("Escanear QR")
            assertEquals("cliente.demo", runBlocking { cases.profile()!!.alias })
        } finally { compose.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    @Test fun carouselHasSynchronizedIndicatorsAndKeepsMovingAfterSixtyFourSeconds() {
        compose.mainClock.autoAdvance = false
        try {
            // Infinite animations must start under the manual test clock; autoAdvance
            // intentionally cancels them through Compose's InfiniteAnimationPolicy.
            runBlocking { cases.initialize(); cases.signOut() }
            compose.activityRule.scenario.recreate()
            compose.waitUntil(20_000) {
                compose.mainClock.advanceTimeByFrame()
                compose.onAllNodes(hasContentDescription("Tapiz Aurora") or hasContentDescription("Colgante Brisa")).fetchSemanticsNodes().isNotEmpty()
            }
            compose.mainClock.advanceTimeBy(2_000)
            val photo = compose.onNode(hasContentDescription("Tapiz Aurora") or hasContentDescription("Colgante Brisa"))
            val actions = photo.fetchSemanticsNode().config[SemanticsActions.CustomActions]
            compose.runOnIdle { assertTrue(actions[0].action()) }
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithContentDescription("Tapiz Aurora").assertExists()
            val first = compose.onNodeWithTag("welcome-dot-0", useUnmergedTree = true).captureToImage().toPixelMap()
            shot("07-carrusel-tapiz")
            compose.mainClock.advanceTimeBy(4_500)
            compose.onNodeWithContentDescription("Colgante Brisa").assertExists()
            val second = compose.onNodeWithTag("welcome-dot-0", useUnmergedTree = true).captureToImage().toPixelMap()
            assertTrue((0 until first.width).any { x -> (0 until first.height).any { y -> first[x, y] != second[x, y] } })
            shot("08-carrusel-brisa")
            compose.mainClock.advanceTimeBy(64_000)
            compose.onNodeWithContentDescription("Colgante Brisa").assertExists()
            compose.mainClock.advanceTimeBy(4_000)
            compose.onNodeWithContentDescription("Tapiz Aurora").assertExists()
        } finally { compose.mainClock.autoAdvance = true }
    }

    @Test fun registrationSectionsAndPhotoSelectionStayReachable() {
        publicStart()
        click("Crear cuenta")
        waitText("DATOS PERSONALES")
        compose.onNodeWithText("Nombre de Usuario").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Contacto").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("SEGURIDAD").performScrollTo().assertIsDisplayed()
        fill("Contraseña", "Abcdef1!")
        compose.onAllNodes(hasText("Ver") and hasClickAction())[0].performScrollTo().performClick()
        compose.onNodeWithText("Abcdef1!").assertExists()
        compose.onNode(hasText("Ocultar") and hasClickAction()).performClick()
        shot("09-registro-seguridad")
        compose.onNodeWithTag("avatar-6").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Tomar una foto").performScrollTo().assertIsDisplayed()
        shot("10-registro-imagen-perfil")
        compose.onNodeWithText("Crear mi cuenta").assertIsDisplayed()
    }
}
