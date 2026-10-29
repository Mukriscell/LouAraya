package cl.duoc.louaraya.presentation.local

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import cl.duoc.louaraya.AppContainer
import cl.duoc.louaraya.R
import cl.duoc.louaraya.domain.model.StockMovementType
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
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AtelierMotionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun aWordEntranceKeepsTheWholeHeadingAccessibleAndItsLayoutStable() {
        compose.mainClock.autoAdvance = false
        val title = "Cada nudo,\nuna historia."
        compose.setContent { LouArayaTheme { AuthMotionScope("welcome") { AuthTitle(title) } } }
        val heading = compose.onNodeWithText(title)
        val before = heading.fetchSemanticsNode().boundsInRoot
        compose.mainClock.advanceTimeBy(160)
        heading.assertExists()
        assertEquals(1, compose.onAllNodesWithText(title).fetchSemanticsNodes().size)
        compose.mainClock.advanceTimeBy(2_000)
        heading.assertIsDisplayed()
        assertEquals(before, heading.fetchSemanticsNode().boundsInRoot)
    }

    @Test fun loadingButtonKeepsItsLabelAndSizeAndCannotSubmitTwice() {
        val loading = mutableStateOf(false)
        val clicks = AtomicInteger()
        compose.setContent { LouArayaTheme { Box(Modifier.width(280.dp)) {
            Action("Registrar entrada", loading = loading.value) { clicks.incrementAndGet() }
        } } }
        val button = compose.onNodeWithText("Registrar entrada")
        val width = button.fetchSemanticsNode().boundsInRoot.width
        compose.runOnIdle { loading.value = true }
        button.assertIsNotEnabled()
        compose.onNodeWithTag("action-loading", useUnmergedTree = true).assertExists()
        button.performTouchInput { click() }
        assertEquals(0, clicks.get())
        assertEquals(width, button.fetchSemanticsNode().boundsInRoot.width, .5f)
        compose.runOnIdle { loading.value = false }
        button.assertIsEnabled()
        compose.onNodeWithTag("action-loading", useUnmergedTree = true).assertDoesNotExist()
        button.performClick()
        assertEquals(1, clicks.get())
    }

    @Test fun statusTransitionExposesOnlyTheCurrentAccessibleLabel() {
        val status = mutableStateOf(RequestStatus.PENDING)
        compose.mainClock.autoAdvance = false
        compose.setContent { LouArayaTheme { StatusBadge(status.value) } }
        compose.onNodeWithText("Pendiente").assertExists()
        compose.runOnIdle { status.value = RequestStatus.DELIVERED }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("Entregado").assertExists()
        compose.onNodeWithText("Pendiente").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("Entregado").assertIsDisplayed()
    }

    @Test fun validationMessagesExpandTheFormWithoutLosingTheTypedValue() {
        val error = mutableStateOf<String?>(null)
        compose.mainClock.autoAdvance = false
        compose.setContent { LouArayaTheme { Column(Modifier.width(220.dp)) {
            Field("Contraseña", "Abcdef1!", {}, error = error.value)
            Text("Siguiente campo")
        } } }
        val before = compose.onNodeWithText("Siguiente campo").fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { error.value = "La contraseña debe incluir una mayúscula, un número y un carácter especial." }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        val during = compose.onNodeWithText("Siguiente campo").fetchSemanticsNode().boundsInRoot.top
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        val after = compose.onNodeWithText("Siguiente campo").fetchSemanticsNode().boundsInRoot.top
        assertTrue(during > before)
        assertTrue(after > during)
        compose.onNodeWithText("Abcdef1!").assertExists()
        compose.runOnIdle { error.value = null }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(600)
        compose.waitForIdle()
        assertEquals(before, compose.onNodeWithText("Siguiente campo").fetchSemanticsNode().boundsInRoot.top, .5f)
    }

    @Test fun pressingAnAnimatedButtonShrinksItAndReleasingTriggersOnlyOneAction() {
        val clicks = AtomicInteger()
        compose.mainClock.autoAdvance = false
        compose.setContent { LouArayaTheme { Box(Modifier.width(280.dp)) { Action("Guardar movimiento") { clicks.incrementAndGet() } } } }
        val button = compose.onNodeWithText("Guardar movimiento")
        val restingWidth = button.fetchSemanticsNode().boundsInRoot.width
        button.performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(240)
        compose.waitForIdle()
        assertTrue(button.fetchSemanticsNode().boundsInRoot.width < restingWidth)
        assertEquals(0, clicks.get())
        button.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        assertEquals(1, clicks.get())
        assertEquals(restingWidth, button.fetchSemanticsNode().boundsInRoot.width, .5f)
    }

    @Test fun aBusyStateCancelsAnInProgressAnimatedPress() {
        val busy = mutableStateOf(false)
        val clicks = AtomicInteger()
        compose.setContent { LouArayaTheme {
            CompositionLocalProvider(LocalBusy provides busy.value) { Action("Registrar venta") { clicks.incrementAndGet() } }
        } }
        val button = compose.onNodeWithText("Registrar venta")
        button.performTouchInput { down(center) }
        compose.runOnIdle { busy.value = true }
        button.assertIsNotEnabled()
        button.performTouchInput { up() }
        assertEquals(0, clicks.get())
        compose.runOnIdle { busy.value = false }
        button.assertIsEnabled().performClick()
        assertEquals(1, clicks.get())
    }

    @Test fun changingACounterExposesOnlyTheActualValueDuringItsTransition() {
        val value = mutableIntStateOf(10)
        compose.mainClock.autoAdvance = false
        compose.setContent { LouArayaTheme {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("EXISTENCIAS DISPONIBLES", style = MaterialTheme.typography.labelSmall)
                AnimatedCount(value.intValue, MaterialTheme.typography.displayMedium, MaterialTheme.colorScheme.primary)
                Text("unidades en bodega", style = MaterialTheme.typography.titleMedium)
            }
        } }
        compose.onNodeWithText("10").assertExists()
        compose.runOnIdle { value.intValue = 7 }
        compose.waitForIdle()
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithText("7").assertExists()
        compose.onNodeWithText("10").assertDoesNotExist()
        capture("contador-transicion")
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithText("7").assertIsDisplayed()
        capture("contador-final")
    }

    @Test fun aFavoriteKeepsItsCurrentLabelAndSelectionDuringTheSpringAnimation() {
        val selected = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent { LouArayaTheme {
            DesignIconButton(R.drawable.la_heart, if (selected.value) "Quitar favorito" else "Guardar favorito", selected = selected.value) {
                selected.value = !selected.value
            }
        } }
        compose.onNodeWithContentDescription("Guardar favorito").performClick()
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Quitar favorito").assertIsSelected()
        compose.onNodeWithContentDescription("Guardar favorito").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Quitar favorito").performClick()
        compose.mainClock.advanceTimeBy(1_000)
        compose.onNodeWithContentDescription("Guardar favorito").assertIsNotSelected()
    }

    @Test fun animatedScreensSettleWithTheStockFormAndCatalogStillUsable() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("louaraya.db")
        val container = AppContainer(context)
        runBlocking {
            container.localUseCases.initialize()
            container.localUseCases.signIn("admin.demo", "LouDemo2026!")
            container.localUseCases.changeStock(1, 7, StockMovementType.ENTRY, "Conteo ficticio para vista previa")
        }
        compose.setContent { LouArayaTheme { LouArayaApp(container) } }
        waitFor("Hoy, en el taller.")
        compose.onNodeWithTag("tab-products").performClick()
        waitFor("Las piezas del taller.")
        compose.onAllNodesWithText("Gestionar stock")[0].performScrollTo().performClick()
        waitFor("EXISTENCIAS DISPONIBLES")
        compose.onNodeWithText("Registrar entrada").assertIsDisplayed()
        capture("bodega")
        compose.onNodeWithText("Historial de movimientos").performScrollTo()
        capture("bodega-historial")
        runBlocking { container.localUseCases.signIn("cliente.demo", "LouDemo2026!") }
        waitFor("Texturas que\nhacen hogar.")
        capture("inicio")
        compose.onNodeWithTag("tab-catalog").performClick()
        waitFor("Encuentra tu pieza.")
        capture("catalogo")
        compose.onNodeWithContentDescription("Guardar favorito: Tapiz Aurora").performScrollTo().performClick()
        waitForDescription("Quitar favorito: Tapiz Aurora")
        capture("catalogo-favorito")
        assertEquals(7, runBlocking { container.localUseCases.snapshot().products.first().stock })
    }

    private fun waitFor(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun waitForDescription(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithContentDescription(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val output = File("C:/Users/lucas/Documents/Codex/2026-10-24/puedes-resumirme-los-ltimos-cambios-y/previews-animaciones").apply { mkdirs() }
                File(output, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }
}
