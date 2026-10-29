package cl.duoc.louaraya.presentation.local

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import cl.duoc.louaraya.AppContainer
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
class LaunchSplashTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun startupShowsTheSplashBeforeWelcomeAndItDoesNotRepeatOnBackOrRecreation() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("louaraya.db")
        val container = AppContainer(context)
        runBlocking { container.localUseCases.initialize() }
        compose.mainClock.autoAdvance = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent { LouArayaTheme { LouArayaApp(container) } }
        compose.onNodeWithTag("launch-splash").assertIsDisplayed()
        compose.onNodeWithText("Comenzar").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(3_000)
        compose.onNodeWithText("LouAraya").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithTag("launch-splash").assertDoesNotExist()
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Comenzar").performScrollTo().performClick()
        compose.onNodeWithText("Nombre de Usuario").assertExists()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Comenzar").assertExists()
        compose.onNodeWithTag("launch-splash").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("launch-splash").assertDoesNotExist()
    }

    @Test fun reducedMotionCompletesWithoutMakingTheUserWaitForTheTimeline() {
        var finished = false
        compose.mainClock.autoAdvance = false
        compose.setContent { LouArayaTheme { LaunchSplash({ finished = true }, reducedMotion = true) } }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertTrue(finished) }
    }

    @Test fun titleRevealsPairedLettersFromTheCenterAndAllMotionSettlesBeforeLaunchEnds() {
        assertEquals(SplashTimeline.character(700f, 3), SplashTimeline.character(700f, 4), .0001f)
        assertTrue(SplashTimeline.character(700f, 3) > SplashTimeline.character(700f, 0))
        for (i in 0..7) assertEquals(1f, SplashTimeline.character(1_250f, i), .001f)
        assertEquals(10f, SplashTimeline.titleY(1_250f), .001f)
        assertEquals(-3f, SplashTimeline.titleY(1_650f), .001f)
        assertEquals(0f, SplashTimeline.titleY(2_100f), .001f)
        assertEquals(1f, SplashTimeline.knotScale(2_100f), .001f)
        assertEquals(0f, SplashTimeline.knotDegrees(2_100f, true), .001f)
        assertEquals(1f, SplashTimeline.reveal(2_400f, 1_750f, 500f), .001f)
    }
}
