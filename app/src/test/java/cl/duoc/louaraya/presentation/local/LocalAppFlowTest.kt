package cl.duoc.louaraya.presentation.local

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import java.io.File
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cl.duoc.louaraya.AppContainer
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.ui.theme.LouArayaTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
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
class LocalAppFlowTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var container: AppContainer
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("louaraya.db")
        container = AppContainer(context)
    }
    private fun render() { compose.setContent { LouArayaTheme { LouArayaApp(container) } } }
    private fun waitFor(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
    private fun capture(name: String) {
        val directory = File(System.getProperty("java.io.tmpdir"), "louaraya-previews").apply { mkdirs() }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    private fun login(alias: String) {
        waitFor("Comenzar")
        capture("welcome")
        compose.onNodeWithText("Comenzar").performScrollTo().performClick()
        waitFor("Nombre de Usuario")
        compose.onNodeWithText("Nombre de Usuario").performTextInput(alias)
        compose.onNodeWithText("Contraseña").performTextInput("LouDemo2026!")
        compose.onNode(hasText("Iniciar sesión") and hasClickAction()).performScrollTo().performClick()
        waitFor("Escanear QR")
        capture("home-$alias")
    }

    @Test fun publicScreensAreSeparateAndBackKeepsTheLoginDraft() {
        render()
        waitFor("Comenzar")
        compose.onNodeWithText("Cada nudo,\nuna historia.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nombre de Usuario").assertDoesNotExist()
        compose.onNodeWithText("Comenzar").performScrollTo().performClick()
        waitFor("Nombre de Usuario")
        compose.onNodeWithText("Nombre").assertDoesNotExist()
        compose.onNodeWithText("Nombre de Usuario").performTextInput("cuenta.borrador")
        compose.onNodeWithText("Crear cuenta").performScrollTo().performClick()
        waitFor("Nombre")
        compose.onNodeWithContentDescription("Volver").performClick()
        waitFor("Nombre de Usuario")
        compose.onNodeWithText("Nombre de Usuario").assertTextContains("cuenta.borrador")
        compose.onNodeWithContentDescription("Volver").performClick()
        waitFor("Comenzar")
        compose.onNodeWithText("Crear cuenta").performScrollTo().performClick()
        waitFor("Nombre")
        compose.onNodeWithText("Ya tengo cuenta").performScrollTo().performClick()
        waitFor("Nombre de Usuario")
        compose.onNodeWithText("Nombre").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").performClick()
        waitFor("Comenzar")
    }

    @Test fun customerCanSaveAFavoriteAndMakeAReservation() {
        render(); login("cliente.demo")
        compose.onNodeWithTag("tab-catalog").performClick()
        waitFor("Encuentra tu pieza.")
        capture("catalog")
        compose.onNodeWithContentDescription("Guardar favorito: Tapiz Aurora").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { container.localUseCases.snapshot().favoriteIds.contains(1L) } }
        compose.onNodeWithTag("tab-favorites").performClick()
        waitFor("Lo que te inspira.")
        compose.onNodeWithText("Tapiz Aurora").performScrollTo().performClick()
        waitFor("Solicitar esta pieza")
        compose.waitUntil(20_000) { compose.onAllNodes(hasText("Solicitar esta pieza") and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Solicitar esta pieza").performClick()
        compose.onNodeWithText("Reserva").performScrollTo().performClick()
        compose.onNodeWithText("Observaciones · opcional").performScrollTo().performTextInput("Reserva de demostración")
        compose.onNodeWithText("Solicitar reserva").performClick()
        waitFor("Solicitud registrada.")
        compose.onNodeWithText("Ver solicitud").performScrollTo().performClick()
        waitFor("Pendiente")
        compose.onNodeWithText("Reserva de demostración").performScrollTo().assertIsDisplayed()
        assertEquals(RequestStatus.PENDING, runBlocking { container.localUseCases.snapshot().requests.single().status })
    }

    @Test fun welcomeOpensDedicatedRegistrationAndCreatesAccountWithChosenIcon() {
        render()
        waitFor("Comenzar")
        compose.onNodeWithText("Nombre de Usuario").assertDoesNotExist()
        compose.onNodeWithText("Contraseña").assertDoesNotExist()
        compose.onNodeWithText("Encontrar mi próxima pieza").assertDoesNotExist()
        compose.onNodeWithText("Crear cuenta").performScrollTo().performClick()
        compose.onNodeWithText("Nombre").performTextInput("Cuenta de prueba")
        compose.onNodeWithText("Nombre de Usuario").performTextInput("registro.icono")
        compose.onNodeWithText("Contraseña").performScrollTo().performTextInput("ClaveDistinta2026!")
        compose.onNodeWithText("Repetir contraseña").performScrollTo().performTextInput("ClaveFicticia2026!")
        compose.onNodeWithText("Crear mi cuenta").performClick()
        waitFor("Las contraseñas no coinciden.")
        compose.onNodeWithText("Contraseña").performScrollTo().performTextReplacement("ClaveFicticia2026!")
        compose.onNodeWithText("Las contraseñas no coinciden.").assertDoesNotExist()
        compose.onNodeWithText("Tomar una foto").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("avatar-7").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("Crear mi cuenta").performClick()
        waitFor("Escanear QR")
        val created = runBlocking { container.localUseCases.profile()!! }
        assertEquals("registro.icono", created.alias)
        assertEquals(7, created.avatar)
        assertEquals(null, created.avatarImageReference)
    }

    @Test fun registrationShowsPasswordRequirementsAndAllowsCorrectionBeforeCreatingAnAccount() {
        render()
        waitFor("Comenzar")
        compose.onNodeWithText("Crear cuenta").performScrollTo().performClick()
        compose.onNodeWithText(cl.duoc.louaraya.domain.usecase.FormValidation.REGISTRATION_PASSWORD_HINT).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Nombre").performScrollTo().performTextInput("Cuenta de prueba")
        compose.onNodeWithText("Nombre de Usuario").performScrollTo().performTextInput("registro.seguro")
        compose.onNodeWithText("Contraseña").performScrollTo().performTextInput("abcdef1!")
        compose.onNodeWithText("Repetir contraseña").performScrollTo().performTextInput("abcdef1!")
        compose.onNodeWithText("Crear mi cuenta").performClick()
        waitFor("Incluye al menos una letra mayúscula.")
        assertEquals(null, runBlocking { container.localUseCases.profile() })
        compose.onNodeWithText("Contraseña").performScrollTo().performTextReplacement("Abcdef1!")
        compose.onNodeWithText("Incluye al menos una letra mayúscula.").assertDoesNotExist()
        compose.onNodeWithText("Repetir contraseña").performScrollTo().performTextReplacement("Abcdef1!")
        compose.onNodeWithText("Crear mi cuenta").performClick()
        waitFor("Escanear QR")
        assertEquals("registro.seguro", runBlocking { container.localUseCases.profile()!!.alias })
    }

    @Test fun logoutReplacesTheWholeNavigationAndAllowsAdminLogin() {
        render(); login("cliente.demo")
        compose.onNodeWithTag("tab-products").assertDoesNotExist()
        compose.onNodeWithContentDescription("Mi perfil").performClick()
        compose.onNodeWithText("Cerrar sesión / cambiar usuario").performScrollTo().performClick()
        login("admin.demo")
        compose.onNodeWithTag("tab-favorites").assertDoesNotExist()
        compose.onNodeWithTag("tab-products").performClick()
        waitFor("Las piezas del taller.")
        compose.onNodeWithText("Crear producto").assertIsDisplayed()
        capture("products-admin")
        compose.onNodeWithText("Crear producto").performClick()
        compose.onNodeWithText("Guardar producto").assertIsDisplayed()
    }

    @Test fun qrUnknownCodeShowsAnErrorAndCanBeRetried() {
        render(); login("cliente.demo")
        compose.onNodeWithText("Escanear QR").performScrollTo().performClick()
        compose.onNodeWithText("Código de etiqueta").performTextInput("louaraya://product/999999")
        compose.onNodeWithText("Consultar código").performScrollTo().performClick()
        waitFor("El código no existe o ya no está disponible.")
        compose.onNodeWithText("Volver a escanear").performScrollTo().performClick()
        compose.onNodeWithText("Código de etiqueta").performTextReplacement("louaraya://product/1")
        compose.onNodeWithText("Consultar código").performScrollTo().performClick()
        waitFor("La pieza")
        compose.onNodeWithText("Tapiz Aurora").performScrollTo().assertIsDisplayed()
    }

    @Test fun correctingTheLoginAliasClearsThePreviousCredentialsError() {
        render()
        waitFor("Comenzar")
        compose.onNodeWithText("Comenzar").performScrollTo().performClick()
        waitFor("Nombre de Usuario")
        compose.onNodeWithText("Nombre de Usuario").performTextInput("alias.inexistente")
        compose.onNodeWithText("Contraseña").performTextInput("LouDemo2026!")
        compose.onNode(hasText("Iniciar sesión") and hasClickAction()).performScrollTo().performClick()
        waitFor("Nombre de usuario o contraseña incorrectos.")
        compose.onNodeWithText("Nombre de Usuario").performScrollTo().performTextReplacement("cliente.demo")
        compose.onNodeWithText("Nombre de usuario o contraseña incorrectos.").assertDoesNotExist()
        compose.onNode(hasText("Iniciar sesión") and hasClickAction()).performScrollTo().performClick()
        waitFor("Escanear QR")
        assertEquals("cliente.demo", runBlocking { container.localUseCases.profile()!!.alias })
    }

    @Test fun productPriceShowsTheRelevantErrorAndOnlySavesACorrectedValue() {
        render(); login("admin.demo")
        val original = runBlocking { container.localUseCases.snapshot().products.first() }
        compose.onNodeWithTag("tab-products").performClick()
        waitFor("Las piezas del taller.")
        compose.onAllNodesWithText("Editar ficha")[0].performScrollTo().performClick()
        compose.onNodeWithText("Precio de la pieza · CLP").performScrollTo().performTextReplacement("45.000")
        compose.onNodeWithText("Guardar producto").performClick()
        waitFor("Escribe un precio entero, sin puntos ni comas.")
        compose.onNodeWithContentDescription("Error en Precio de la pieza · CLP").assertExists()
        assertEquals(original, runBlocking { container.localUseCases.snapshot().products.first() })
        compose.onNodeWithText("Precio de la pieza · CLP").performScrollTo().performTextReplacement("-1")
        compose.onNodeWithText("Guardar producto").performClick()
        waitFor("El precio debe ser igual o mayor a cero.")
        assertEquals(original, runBlocking { container.localUseCases.snapshot().products.first() })
        compose.onNodeWithText("Precio de la pieza · CLP").performScrollTo().performTextReplacement("45000")
        compose.onNodeWithContentDescription("Error en Precio de la pieza · CLP").assertDoesNotExist()
        compose.onNodeWithText("Guardar producto").performClick()
        waitFor("Las piezas del taller.")
        assertEquals(45000L, runBlocking { container.localUseCases.snapshot().products.first().price })
    }
}
