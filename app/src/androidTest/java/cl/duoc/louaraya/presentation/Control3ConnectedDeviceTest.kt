package cl.duoc.louaraya.presentation

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.R
import cl.duoc.louaraya.data.remote.Credentials
import cl.duoc.louaraya.data.remote.IdentityApi
import cl.duoc.louaraya.data.remote.WorkshopApi
import cl.duoc.louaraya.domain.model.RequestStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class Control3ConnectedDeviceTest {
    private val arguments get() = InstrumentationRegistry.getArguments()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val password get() = arguments.getString("control3DemoPassword") ?: "LouDemo2026!"

    @get:Rule(order = 0)
    val connectedOnly = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                assumeTrue("Execute explicitly with -e control3 true against a test backend", arguments.getString("control3") == "true")
                base.evaluate()
            }
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun productCrudAndRequestDeliveryPersistOnTheServerAndAppearForBothRoles() {
        val identity = api<IdentityApi>(R.string.control3_identity_url)
        val workshop = api<WorkshopApi>(R.string.control3_taller_url)
        val observer = "Bearer ${runBlocking { identity.login(Credentials("admin.demo", password)) }.token}"
        val name = "Pieza Control 3 ${System.currentTimeMillis()}"
        var productId: Long? = null

        fun remoteState() = runBlocking { workshop.snapshot(observer) }

        try {
            signIn("admin.demo", "tab-products")
            compose.onNodeWithTag("tab-products").performClick()
            click("Crear producto")
            fill("Nombre del diseño", name)
            fill("Ancho · cm", "45")
            fill("Largo · cm", "70")
            fill("Material", "Algodón ficticio")
            fill("Precio de la pieza · CLP", "18000")
            click("Guardar producto")
            waitText("Las piezas del taller.")
            val created = remoteState().products.orEmpty().single { it.name == name }
            productId = created.id
            assertEquals(18000L, created.price)

            fill("Buscar productos", name)
            click("Editar ficha")
            fill("Precio de la pieza · CLP", "19500")
            click("Guardar producto")
            waitText("Las piezas del taller.")
            assertEquals(19500L, remoteState().products.orEmpty().single { it.id == created.id }.price)

            fill("Buscar productos", name)
            click("Gestionar stock")
            fill("Cantidad de unidades", "5")
            click("Registrar entrada")
            waitText("+5 unidades · Saldo: 5")
            assertEquals(5, remoteState().products.orEmpty().single { it.id == created.id }.stock)

            signIn("cliente.demo", "tab-catalog")
            showInCatalog(name)
            click("Solicitar esta pieza")
            fill("Cantidad", "2")
            click("Solicitar compra")
            waitText("Solicitud registrada.")
            val request = remoteState().requests.orEmpty().single { it.productId == created.id }
            assertEquals(2, request.quantity)
            assertEquals(39000L, request.total)
            assertEquals(RequestStatus.PENDING, request.status)

            signIn("admin.demo", "tab-products")
            openRequest(request.id)
            click("Confirmado")
            waitStatus("Confirmado")
            signIn("cliente.demo", "tab-catalog")
            openRequest(request.id)
            waitStatus("Confirmado")

            signIn("admin.demo", "tab-products")
            openRequest(request.id)
            click("Entregado")
            waitStatus("Entregado")
            val delivered = remoteState()
            assertEquals(RequestStatus.DELIVERED, delivered.requests.orEmpty().single { it.id == request.id }.status)
            assertEquals(3, delivered.products.orEmpty().single { it.id == created.id }.stock)
            assertEquals(-2, delivered.stockMovements.orEmpty().single { it.requestId == request.id }.quantityDelta)

            click("Volver a solicitudes")
            compose.onNodeWithTag("tab-products").performClick()
            fill("Buscar productos", name)
            click("Editar ficha")
            click("Dar de baja")
            click("Confirmar baja")
            waitText("Las piezas del taller.")
            assertFalse(remoteState().products.orEmpty().single { it.id == created.id }.active)
            productId = null

            signIn("cliente.demo", "tab-catalog")
            compose.onNodeWithTag("tab-catalog").performClick()
            fill("Buscar una pieza", name)
            waitText("No encontramos\ncoincidencias.")
            openRequest(request.id)
            waitStatus("Entregado")

            val other = "Bearer ${runBlocking { identity.login(Credentials("cliente2.demo", password)) }.token}"
            try {
                assertTrue(runBlocking { workshop.snapshot(other).requests.orEmpty().none { it.id == request.id } })
            } finally {
                runBlocking { identity.logout(other) }
            }
        } finally {
            runBlocking {
                productId?.let { id -> runCatching { workshop.deactivateProduct(observer, id) } }
                runCatching { identity.logout(observer) }
            }
        }
    }

    private fun showInCatalog(name: String) {
        compose.onNodeWithTag("tab-catalog").performClick()
        fill("Buscar una pieza", name)
        click(name)
    }

    private fun signIn(alias: String, tab: String) {
        val profile = hasContentDescription("Mi perfil") and hasClickAction()
        val welcome = hasText("Comenzar") and hasClickAction()
        compose.waitUntil(35_000) {
            compose.onAllNodes(profile).fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodes(welcome).fetchSemanticsNodes().isNotEmpty()
        }
        if (compose.onAllNodes(profile).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(profile).performClick()
            click("Cerrar sesión / cambiar usuario")
        }
        click("Comenzar")
        fill("Nombre de Usuario", alias)
        fill("Contraseña", password, obscured = true)
        click("Iniciar sesión")
        compose.waitUntil(35_000) { compose.onAllNodesWithTag(tab).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun openRequest(id: Long) {
        compose.onNodeWithTag("tab-requests").performClick()
        click("Solicitud #$id · Compra")
    }

    private fun waitStatus(status: String) {
        val selector = hasTestTag("request-status-badge") and hasText(status)
        compose.waitUntil(25_000) { compose.onAllNodes(selector).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitText(text: String) {
        try {
            compose.waitUntil(25_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Exception) {
            val visible = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text))
                .fetchSemanticsNodes().mapNotNull { it.config.getOrNull(SemanticsProperties.Text) }
            throw AssertionError("No apareció '$text'. Textos en la pantalla: $visible", failure)
        }
    }

    private fun click(label: String) {
        val action = hasText(label) and hasClickAction() and isEnabled() and !hasSetTextAction()
        compose.waitUntil(25_000) { compose.onAllNodes(action).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNode(action)
        if (!node.isDisplayed()) node.performScrollTo()
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(300, 5_000)
        node.assertIsDisplayed().performClick()
    }

    private fun fill(label: String, value: String, obscured: Boolean = false) {
        val editable = hasSetTextAction()
        val selector = if (label.startsWith("Buscar")) editable else hasText(label) and editable
        compose.waitUntil(25_000) { compose.onAllNodes(selector).fetchSemanticsNodes().size == 1 }
        val field = compose.onNode(selector).performScrollTo()
        field.performTextReplacement(value)
        // Wait for Android to finish resizing the activity before pressing a fixed footer action.
        if (!obscured) {
            compose.waitUntil(10_000) { compose.onAllNodes(hasText(value) and editable).fetchSemanticsNodes().isNotEmpty() }
        }
        compose.activityRule.scenario.onActivity {
            ViewCompat.getWindowInsetsController(it.window.decorView)?.hide(WindowInsetsCompat.Type.ime())
        }
        compose.waitUntil(10_000) {
            var keyboardHidden = false
            compose.activityRule.scenario.onActivity { activity ->
                keyboardHidden = ViewCompat.getRootWindowInsets(activity.window.decorView)
                    ?.getInsets(WindowInsetsCompat.Type.ime())?.bottom == 0
            }
            keyboardHidden
        }
    }

    private inline fun <reified T> api(urlResource: Int): T {
        val url = context.getString(urlResource)
        assertTrue("This APK must be configured for the Control 3 test services", url.isNotBlank())
        return Retrofit.Builder().baseUrl(url)
            .addConverterFactory(GsonConverterFactory.create())
            .build().create(T::class.java)
    }
}
