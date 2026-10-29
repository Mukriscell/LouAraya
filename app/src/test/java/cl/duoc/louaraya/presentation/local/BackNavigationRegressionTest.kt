package cl.duoc.louaraya.presentation.local

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import cl.duoc.louaraya.AppContainer
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.ui.theme.LouArayaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BackNavigationRegressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var container: AppContainer
    private lateinit var restoration: StateRestorationTester

    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("louaraya.db")
        container = AppContainer(context)
        runBlocking {
            container.localUseCases.initialize()
            container.localUseCases.signIn("cliente.demo", "LouDemo2026!")
        }
        restoration = StateRestorationTester(compose)
        restoration.setContent { LouArayaTheme { LouArayaApp(container) } }
        waitFor("Texturas que\nhacen hogar.")
    }

    private fun waitFor(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }

    private fun openRequest() {
        compose.onNodeWithTag("tab-catalog").performClick()
        waitFor("Encuentra tu pieza.")
        compose.onNodeWithText("Tapiz Aurora").performScrollTo().performClick()
        waitFor("Solicitar esta pieza")
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasText("Solicitar esta pieza") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Solicitar esta pieza").performScrollTo().performClick()
        waitFor("Quiero esta pieza.")
    }

    private fun back() {
        compose.waitUntil(20_000) {
            compose.onAllNodes(hasContentDescription("Volver") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Volver").performClick()
    }

    @Test @Config(sdk = [36]) fun headerBackLeavesPurchaseFormAndReturnsToItsProduct() {
        openRequest()
        back()
        waitFor("Solicitar esta pieza")
        compose.onNodeWithText("Quiero esta pieza.").assertDoesNotExist()
        back()
        waitFor("Encuentra tu pieza.")
    }

    @Test fun quotationBackStepsThenLeavesWithoutLosingTheEditedMeasurements() {
        openRequest()
        compose.onNodeWithText("Cotización").performScrollTo().performClick()
        compose.onNodeWithText("Ancho · cm").performScrollTo().performTextReplacement("91")
        compose.onNodeWithText("Colores").performScrollTo().performTextInput("Crudo")
        compose.onNodeWithText("Continuar con mi idea").performClick()
        waitFor("Ahora, tu historia.")
        restoration.emulateSavedInstanceStateRestore()
        waitFor("Ahora, tu historia.")
        back()
        waitFor("Primero, la idea.")
        compose.onNodeWithText("91").assertExists()
        back()
        waitFor("Solicitar esta pieza")
    }

    @Test fun systemBackFromPurchaseReturnsToProduct() {
        openRequest()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor("Solicitar esta pieza")
    }

    @Test fun profileAndPhotoCancellationReturnToTheirOrigin() {
        compose.onNodeWithContentDescription("Mi perfil").performClick()
        waitFor("Guardar perfil")
        compose.onNodeWithText("Tomar mi foto").performScrollTo().performClick()
        waitFor("Cancelar y volver")
        back()
        waitFor("Guardar perfil")
        back()
        waitFor("Texturas que\nhacen hogar.")
    }

    @Test @Config(sdk = [35, 36]) fun toolbarBackFromQuotationDoesNotGetConsumedByAnActivityBackCallback() {
        openRequest()
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.addCallback(compose.activity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            })
        }
        back()
        waitFor("Solicitar esta pieza")
    }

    @Test fun quotationStepBackIsIndependentOfOtherActivityBackCallbacks() {
        openRequest()
        compose.onNodeWithText("Cotización").performScrollTo().performClick()
        compose.onNodeWithText("Colores").performScrollTo().performTextInput("Natural")
        compose.onNodeWithText("Continuar con mi idea").performClick()
        waitFor("Ahora, tu historia.")
        compose.runOnIdle {
            compose.activity.onBackPressedDispatcher.addCallback(compose.activity, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            })
        }
        back()
        waitFor("Primero, la idea.")
        back()
        waitFor("Solicitar esta pieza")
    }

    @Test fun productPhotoBackPreservesUnsavedEditorFieldsAndCollectionCancelReturnsToManagement() {
        runBlocking { container.localUseCases.signIn("admin.demo", "LouDemo2026!") }
        waitFor("Hoy, en el taller.")
        compose.onNodeWithTag("tab-products").performClick()
        waitFor("Las piezas del taller.")
        compose.onAllNodesWithText("Editar ficha")[0].performScrollTo().performClick()
        compose.onNodeWithText("Nombre del diseño").performScrollTo().performTextReplacement("Borrador conservado")
        compose.onNodeWithText("Cambiar fotografía").performScrollTo().performClick()
        waitFor("Elegir de galería")
        back()
        waitFor("Guardar producto")
        compose.onNodeWithText("Borrador conservado").assertExists()
        back()
        waitFor("Las piezas del taller.")
        compose.onNodeWithTag("tab-campaigns").performClick()
        compose.onNodeWithText("Crear colección").performScrollTo().performClick()
        waitFor("Guardar colección")
        compose.onNodeWithText("Cancelar").performScrollTo().performClick()
        waitFor("Crear colección")
    }

    @Test fun finishingASaveAfterSystemBackDoesNotPopTheScreenNowBeingViewed() {
        runBlocking { container.localUseCases.signIn("admin.demo", "LouDemo2026!") }
        waitFor("Hoy, en el taller.")
        compose.onNodeWithTag("tab-products").performClick()
        waitFor("Las piezas del taller.")
        compose.onAllNodesWithText("Editar ficha")[0].performScrollTo().performClick()
        compose.onNodeWithText("Nombre del diseño").performScrollTo().performTextReplacement("Pieza corregida")
        val context = RuntimeEnvironment.getApplication()
        val lock = SQLiteDatabase.openDatabase(context.getDatabasePath("louaraya.db").path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            lock.beginTransaction()
            lock.execSQL("UPDATE products SET name = name WHERE id = 1")
            compose.onNodeWithText("Guardar producto").performClick()
            compose.onNodeWithText("Guardar producto").assertIsNotEnabled()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            waitFor("Las piezas del taller.")
            compose.onNodeWithTag("tab-requests").performClick()
            waitFor("Cada idea\nmerece respuesta.")
            lock.endTransaction()
            compose.waitUntil(20_000) {
                compose.onAllNodes(hasContentDescription("Mi perfil") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("tab-requests").assertIsSelected()
            compose.onNodeWithText("Cada idea\nmerece respuesta.").assertExists()
            assertEquals("Pieza corregida", runBlocking { container.localUseCases.snapshot().products.first().name })
        } finally {
            if (lock.inTransaction()) lock.endTransaction()
            lock.close()
        }
    }

    @Test fun leavingQrDuringLookupDoesNotOpenItsProductOnAnotherTab() {
        compose.onNodeWithText("Escanear QR").performScrollTo().performClick()
        compose.onNodeWithText("Código de etiqueta").performScrollTo().performTextInput("louaraya://product/1")
        val lock = SQLiteDatabase.openDatabase(RuntimeEnvironment.getApplication().getDatabasePath("louaraya.db").path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            lock.beginTransaction()
            lock.execSQL("UPDATE products SET name = name WHERE id = 1")
            compose.onNodeWithText("Consultar código").performScrollTo().performClick()
            compose.onNodeWithText("Consultar código").assertIsNotEnabled()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            waitFor("Texturas que\nhacen hogar.")
            compose.onNodeWithTag("tab-favorites").performClick()
            waitFor("Lo que te inspira.")
            lock.endTransaction()
            compose.waitUntil(20_000) {
                compose.onAllNodes(hasContentDescription("Mi perfil") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("tab-favorites").assertIsSelected()
            compose.onNodeWithText("Solicitar esta pieza").assertDoesNotExist()
        } finally {
            if (lock.inTransaction()) lock.endTransaction()
            lock.close()
        }
    }

    @Test fun leavingNoticesDuringLookupDoesNotOpenADetailOverProductManagement() {
        val request = runBlocking {
            val product = container.localUseCases.snapshot().products.first()
            container.localUseCases.createRequest(product.id, RequestIntention.RESERVATION, product.price, "Prueba de navegación", null)
        }
        runBlocking { container.localUseCases.signIn("admin.demo", "LouDemo2026!") }
        waitFor("Hoy, en el taller.")
        compose.onNode(hasContentDescription("Avisos", substring = true)).performClick()
        waitFor("Buenas noticias\npara tu espacio.")
        val lock = SQLiteDatabase.openDatabase(RuntimeEnvironment.getApplication().getDatabasePath("louaraya.db").path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            lock.beginTransaction()
            lock.execSQL("UPDATE products SET name = name WHERE id = 1")
            compose.onNodeWithText("Nueva solicitud #${request.id}", substring = true).performScrollTo().performClick()
            compose.waitForIdle()
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            waitFor("Hoy, en el taller.")
            compose.onNodeWithTag("tab-products").performClick()
            waitFor("Las piezas del taller.")
            lock.endTransaction()
            compose.waitUntil(20_000) {
                compose.onAllNodes(hasContentDescription("Mi perfil") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithTag("tab-products").assertIsSelected()
            compose.onNodeWithText("Las piezas del taller.").assertExists()
        } finally {
            if (lock.inTransaction()) lock.endTransaction()
            lock.close()
        }
    }
}
