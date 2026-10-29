package cl.duoc.louaraya.presentation

import android.Manifest
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.model.RequestStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runners.model.Statement
import java.io.File


class RequirementsAuditTest {

    @get:Rule(order = 0) val notificationsPermission = TestRule { base, _ ->
        object : Statement() {
            override fun evaluate() {
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
                }
                base.evaluate()
            }
        }
    }
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val cases get() = (compose.activity.application as LouArayaApplication).container.localUseCases

    private fun waitText(text: String) = compose.waitUntil(20_000) {
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
    private fun fill(label: String, text: String) {
        compose.onNodeWithText(label).performScrollTo().performTextReplacement(text)
        closeSoftKeyboard()
        compose.waitUntil(10_000) {
            var hidden = false
            compose.activityRule.scenario.onActivity {
                hidden = ViewCompat.getRootWindowInsets(it.window.decorView)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom == 0
            }
            hidden
        }
    }
    private fun click(text: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText(text) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
        val node = compose.onNodeWithText(text).performScrollTo()
        instrumentation.uiAutomation.waitForIdle(300, 5_000)
        node.assertIsDisplayed().performClick()
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(750, 5_000)
        val directory = File(context.getExternalFilesDir(null), "audit").apply { mkdirs() }
        val screenshot = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(directory, "$name.png").outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
        screenshot.recycle()
    }

    @Test fun registrationProfileAndFavoritesRemainSeparatedAfterUserSwitchAndRecreation() {
        runBlocking { cases.signOut() }
        compose.activityRule.scenario.recreate()
        waitText("Crear cuenta")
        click("Crear cuenta")
        val alias = "auditoria.${System.currentTimeMillis()}"
        fill("Nombre", "Cliente de prueba")
        fill("Nombre de Usuario", alias)
        fill("Contacto", "prueba@demo.example")
        fill("Contraseña", "ClaveFicticia2026!")
        fill("Repetir contraseña", "ClaveFicticia2026!")
        compose.onNodeWithTag("avatar-6").performScrollTo().performClick()
        click("Crear mi cuenta")
        waitText("Escanear QR")
        val user = runBlocking { cases.profile()!! }
        assertEquals(alias, user.alias)
        assertEquals(6, user.avatar)
        compose.onNodeWithTag("tab-products").assertDoesNotExist()
        compose.onNodeWithTag("tab-catalog").performClick()
        compose.onNodeWithContentDescription("Guardar favorito: Tapiz Aurora").performScrollTo().performClick()
        compose.waitUntil(10_000) { runBlocking { 1L in cases.snapshot().favoriteIds } }
        compose.onNodeWithContentDescription("Mi perfil").performClick()
        fill("Nombre", "Perfil editado de prueba")
        fill("Nombre de Usuario", "$alias.editado")
        click("Guardar perfil")
        waitText("Perfil actualizado.")
        shot("registro-perfil-guardado")
        compose.activityRule.scenario.recreate()
        waitText("Perfil editado de prueba")
        assertEquals(user.id, runBlocking { cases.profile()!!.id })
        click("Volver a mi perfil")
        click("Cerrar sesión / cambiar usuario")
        waitText("Comenzar"); click("Comenzar"); waitText("Nombre de Usuario")
        fill("Nombre de Usuario", "$alias.editado")
        fill("Contraseña", "ClaveFicticia2026!")
        click("Iniciar sesión")
        waitText("Escanear QR")
        compose.onNodeWithTag("tab-favorites").performClick()
        compose.onNodeWithText("Tapiz Aurora").performScrollTo().assertIsDisplayed()
        runBlocking { cases.register("Segundo cliente de prueba", "$alias.segundo", "ClaveFicticia2026!", 0, "") }
        compose.activityRule.scenario.recreate()
        waitText("Escanear QR")
        compose.onNodeWithTag("tab-favorites").performClick()
        waitText("Tu próxima favorita\nte espera.")
        assertTrue(runBlocking { cases.snapshot().favoriteIds.isEmpty() })
        assertTrue(runBlocking { cases.snapshot().requests.isEmpty() })
        shot("segundo-cliente-sin-favoritos")
    }

    @Test fun administratorCanCreateEditAndDeactivateAProductWithoutErasingItsRequest() {
        compose.signInDemo("admin.demo")
        compose.onNodeWithTag("tab-products").performClick()
        click("Crear producto")
        val name = "Pieza auditoría ${System.currentTimeMillis()}"
        fill("Nombre del diseño", name)
        fill("Descripción ornamental", "Diseño ficticio para verificar el formulario completo.")
        fill("Ancho · cm", "45")
        fill("Largo · cm", "70")
        fill("Material", "Algodón de demostración")
        fill("Precio de la pieza · CLP", "18000")
        compose.onNodeWithText("A pedido").onParent().performScrollTo()
        click("A pedido")
        compose.onNodeWithText("A pedido").assertIsSelected()
        click("Guardar producto")
        compose.waitUntil(15_000) { runBlocking { cases.snapshot().products.any { it.name == name } } }
        val product = runBlocking { cases.snapshot().products.single { it.name == name } }
        assertEquals("MADE_TO_ORDER", product.availability)
        runBlocking {
            cases.signIn("cliente.demo", "LouDemo2026!")
            cases.createRequest(product.id, RequestIntention.RESERVATION, product.price, "Reserva de auditoría", null)
        }
        compose.signInDemo("admin.demo")
        compose.onNodeWithTag("tab-products").performClick()
        fill("Buscar productos", name)
        click("Editar ficha")
        fill("Precio de la pieza · CLP", "19000")
        compose.onNode(isToggleable()).performScrollTo().performClick()
        click("Guardar producto")
        compose.waitUntil(15_000) { runBlocking { cases.snapshot().products.any { it.id == product.id && !it.active && it.price == 19000L } } }
        shot("producto-editado-desactivado")
        compose.signInDemo("cliente.demo")
        compose.onNodeWithTag("tab-catalog").performClick()
        fill("Buscar una pieza", name)
        waitText("No encontramos\ncoincidencias.")
        val saved = runBlocking { cases.snapshot().requests.first { it.productId == product.id } }
        assertEquals(18000L, saved.total)
        assertEquals(RequestStatus.PENDING, saved.status)
        assertEquals("Reserva de auditoría", saved.comment)
    }

    @Test fun systemNotificationOpensItsRequestAndCannotLeakToAnotherProfile() {
        android.util.Log.i("AuditNotification", "start")
        val manager = context.getSystemService(NotificationManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= 33) assertEquals("Grant notification permission before starting instrumentation.", PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS))
            android.util.Log.i("AuditNotification", "permission checked")
            val request = runBlocking {
                cases.initialize()
                cases.signIn("cliente.demo", "LouDemo2026!")
                cases.createRequest(1, RequestIntention.PURCHASE, 45000, "Aviso de auditoría", null)
            }
            android.util.Log.i("AuditNotification", "request saved")
            compose.signInDemo("admin.demo")
            android.util.Log.i("AuditNotification", "admin signed in")
            compose.waitUntil(20_000) { manager.activeNotifications.any { it.notification.extras.getString("android.title") == "Nueva solicitud #${request.id}" } }
            val notification = manager.activeNotifications.single { it.notification.extras.getString("android.title") == "Nueva solicitud #${request.id}" }
            android.util.Log.i("AuditNotification", "notification posted")
            assertEquals(2L, notification.notification.extras.getLong("louarayaUserId"))
            instrumentation.uiAutomation.executeShellCommand("cmd statusbar expand-notifications").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            shot("aviso-sistema-generado")
            android.util.Log.i("AuditNotification", "shade captured")
            instrumentation.uiAutomation.executeShellCommand("cmd statusbar collapse").use {
                android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes()
            }
            notification.notification.contentIntent.send()
            android.util.Log.i("AuditNotification", "pending intent sent")
            waitText("Solicitud #${request.id}")
            waitText("Aviso de auditoría")
            assertTrue(runBlocking { cases.snapshot().notices.single { it.id == notification.id.toLong() }.read })
            shot("aviso-abre-solicitud")
            android.util.Log.i("AuditNotification", "destination checked")
            compose.onNodeWithContentDescription("Volver").performClick()


            compose.activityRule.scenario.recreate()
            waitText("Escanear QR")
            compose.onNodeWithContentDescription("Mi perfil").performClick()
            click("Cerrar sesión / cambiar usuario")
            waitText("Comenzar"); click("Comenzar"); waitText("Nombre de Usuario")
            fill("Nombre de Usuario", "cliente.demo")
            fill("Contraseña", "LouDemo2026!")
            click("Iniciar sesión")
            waitText("Escanear QR")
            compose.waitUntil(10_000) { manager.activeNotifications.none { it.notification.extras.getLong("louarayaUserId") == 2L } }
            assertFalse(runBlocking { cases.snapshot().notices.any { it.title == "Nueva solicitud #${request.id}" } })

            notification.notification.contentIntent.send()
            waitText("Cambia al perfil destinatario para abrir este aviso.")
            compose.onNodeWithText("Solicitud #${request.id}").assertDoesNotExist()
            assertEquals(1L, runBlocking { cases.profile()!!.id })
        } finally {
            manager.cancelAll()
        }
    }
}
