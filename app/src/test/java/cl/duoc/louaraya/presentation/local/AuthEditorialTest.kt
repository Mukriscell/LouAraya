package cl.duoc.louaraya.presentation.local

import android.app.Application
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import android.graphics.Bitmap
import android.graphics.Canvas
import cl.duoc.louaraya.AppContainer
import cl.duoc.louaraya.ui.theme.LouArayaTheme
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
class AuthEditorialTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun indicatorPixels(): IntArray {
        val bounds = compose.onNodeWithTag("welcome-dot-0", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        var pixels = intArrayOf()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val width = bounds.width.toInt().coerceAtLeast(1)
            val height = bounds.height.toInt().coerceAtLeast(1)
            pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, bounds.left.toInt(), bounds.top.toInt(), width, height)
            bitmap.recycle()
        }
        return pixels
    }

    @Test fun carouselAndIndicatorsChangeTogetherAndContinuePastSixtyFourSeconds() {
        compose.mainClock.autoAdvance = false
        compose.setContent { LouArayaTheme { AuthMotionScope("welcome", reducedMotion = false) { WelcomeScreen({}, {}) } } }
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithContentDescription("Tapiz Aurora").assertExists()
        val first = indicatorPixels()
        compose.mainClock.advanceTimeBy(2_500)
        compose.onNodeWithContentDescription("Colgante Brisa").assertExists()
        val second = indicatorPixels()
        assertFalse(first.contentEquals(second))
        compose.mainClock.advanceTimeBy(64_000)
        compose.onNodeWithContentDescription("Colgante Brisa").assertExists()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithContentDescription("Tapiz Aurora").assertExists()
    }

    @Test fun systemReducedMotionKeepsContentStaticAndAllowsManualPhotoSelection() {
        val resolver = RuntimeEnvironment.getApplication().contentResolver
        val previous = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f)
        try {
            compose.mainClock.autoAdvance = false
            var reduced = false
            compose.setContent { LouArayaTheme { AuthMotionScope("welcome") {
                val motion = LocalAuthMotion.current
                SideEffect { reduced = motion.reduced }
                WelcomeScreen({}, {})
            } } }
            compose.mainClock.advanceTimeByFrame()
            compose.runOnIdle { assertTrue(reduced) }
            compose.onNodeWithText("Cada nudo,\nuna historia.").assertIsDisplayed()
            compose.mainClock.advanceTimeBy(68_000)
            compose.onNodeWithContentDescription("Tapiz Aurora").assertExists().performClick()
            compose.mainClock.advanceTimeByFrame()
            compose.onNodeWithContentDescription("Colgante Brisa").assertExists()
            compose.mainClock.advanceTimeBy(68_000)
            compose.onNodeWithContentDescription("Colgante Brisa").assertExists()
        } finally { Settings.Global.putFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, previous) }
    }

    @Test @Config(qualifiers = "w320dp-h640dp")
    fun smallPhoneWithLargeTextCanReachEveryPublicActionAndPasswordToggle() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("louaraya.db")
        val container = AppContainer(context)
        compose.setContent {
            val density = androidx.compose.ui.platform.LocalDensity.current.density
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density, 2f)) {
                LouArayaTheme { LouArayaApp(container) }
            }
        }
        compose.waitUntil(20_000) { compose.onAllNodesWithText("Comenzar").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Comenzar").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Crear cuenta").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("Nombre de Usuario").performScrollTo().assertIsDisplayed().performTextInput("usuario.prueba")
        compose.onNodeWithText("Contacto").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Contraseña").performScrollTo().performTextInput("Abcdef1!")
        compose.onAllNodes(hasText("Ver") and hasClickAction()).onFirst().performScrollTo().assertIsDisplayed().performClick()
        compose.onNode(hasText("Ocultar") and hasClickAction()).assertIsDisplayed().performClick()
        compose.onNodeWithText("Repetir contraseña").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Tomar una foto").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("avatar-4").performScrollTo().assertIsDisplayed().performClick().assertIsSelected()
        compose.onNodeWithText("Ya tengo cuenta").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Crear mi cuenta").assertIsDisplayed()
    }
}
