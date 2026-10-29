package cl.duoc.louaraya.presentation

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.LouArayaApplication
import cl.duoc.louaraya.MainActivity
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit


class AvatarAndQrTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val container get() = (compose.activity.application as LouArayaApplication).container
    private val cases get() = container.localUseCases
    private fun waitText(text: String) = compose.waitUntil(20_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    private fun shot(name: String) {
        compose.waitForIdle()
        instrumentation.uiAutomation.waitForIdle(750, 5000)
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "avatar-qr-evidence").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun qrImageIsDecodedOnDeviceAndOpensItsProduct() {
        compose.signInDemo("cliente.demo")
        val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        val bitmap = instrumentation.context.assets.open("qr-tapiz-aurora.png").use { BitmapFactory.decodeStream(it) }
        val raw = try { Tasks.await(scanner.process(InputImage.fromBitmap(bitmap, 0)), 20, TimeUnit.SECONDS).single().rawValue }
        finally { bitmap.recycle(); scanner.close() }
        assertEquals("louaraya://product/1", raw)
        compose.onNodeWithText("Escanear QR").performScrollTo().performClick()
        compose.onNodeWithText("Código de etiqueta").performScrollTo().performTextInput(requireNotNull(raw))
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Consultar código").performScrollTo().performClick()
        waitText("Tapiz Aurora")
        compose.onNodeWithText("Solicitar esta pieza").assertIsDisplayed()
        shot("05-qr-decodificado-detalle")
    }

    @Test fun eightAvatarsPersistAndPhotoScreenCanBeCancelled() {
        compose.signInDemo("cliente.demo")
        compose.onNodeWithContentDescription("Mi perfil").performClick()
        compose.onNodeWithTag("avatar-7").performScrollTo().performClick()
        shot("01-ocho-avatares")
        compose.onNodeWithText("Guardar perfil").performClick()
        waitText("Perfil actualizado.")
        assertEquals(7, runBlocking { cases.profile()!!.avatar })
        assertNull(runBlocking { cases.profile()!!.avatarImageReference })
        compose.onNodeWithText("Volver a mi perfil").performClick()
        compose.onNodeWithText("Tomar mi foto").performScrollTo().performClick()
        shot("02-foto-antes-de-captura")
        compose.onNodeWithText("Cancelar y volver").performClick()
        waitText("Tomar mi foto")
        compose.activityRule.scenario.recreate()
        waitText("Tomar mi foto")
        assertEquals(7, runBlocking { cases.profile()!!.avatar })
    }

    private fun nativeNodes(): List<AccessibilityNodeInfo> {
        val result = mutableListOf<AccessibilityNodeInfo>()
        fun walk(node: AccessibilityNodeInfo) { result += node; for (i in 0 until node.childCount) node.getChild(i)?.let(::walk) }
        instrumentation.uiAutomation.rootInActiveWindow?.let(::walk)
        return result
    }

    @Test fun nativeCameraCanCapturePreviewConfirmAndPersistPersonalAvatar() {
        compose.signInDemo("cliente.demo")
        val user = runBlocking { cases.profile()!! }
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply { flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS }

        instrumentation.uiAutomation.executeShellCommand("pm grant ${compose.activity.packageName} android.permission.CAMERA").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).readBytes() }
        compose.onNodeWithContentDescription("Mi perfil").performClick()
        val cameraLabel = if (user.avatarImageReference == null) "Tomar mi foto" else "Cambiar mi foto"
        compose.onNodeWithText(cameraLabel).performScrollTo().performClick()
        compose.onNodeWithText("Abrir cámara").performScrollTo().performClick()
        var shutter: AccessibilityNodeInfo? = null
        compose.waitUntil(20_000) {
            shutter = nativeNodes().firstOrNull { node ->
                val id = node.viewIdResourceName.orEmpty()
                val description = node.contentDescription?.toString().orEmpty()
                node.isEnabled && node.isClickable && (id.endsWith(":id/shutter_button") || id.endsWith(":id/shutter") || Regex("^(Capture|Shutter|Take picture|Tomar foto|Capturar|Obturador)$", RegexOption.IGNORE_CASE).matches(description))
            }
            shutter != null
        }
        assertTrue(requireNotNull(shutter).performAction(AccessibilityNodeInfo.ACTION_CLICK))

        try { compose.waitUntil(20_000) {
            val accept = nativeNodes().firstOrNull { node ->
                val id = node.viewIdResourceName.orEmpty()
                val description = node.contentDescription?.toString().orEmpty()
                node.isEnabled && node.isClickable && (id.endsWith(":id/done_button") || id.endsWith(":id/done") || id.endsWith(":id/review_done") || Regex("^(OK|Done|Aceptar|Listo|Usar foto)$", RegexOption.IGNORE_CASE).matches(description))
            }
            if (accept != null) accept.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            runCatching { compose.onAllNodesWithText("Usar esta foto").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        } } catch (failure: Exception) {
            val nodes = nativeNodes().joinToString("\n") { "${it.viewIdResourceName} | ${it.contentDescription} | ${it.text} | clickable=${it.isClickable}" }
            val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "avatar-qr-evidence").apply { mkdirs() }
            File(folder, "camera-review-ui.txt").writeText(nodes)
            instrumentation.uiAutomation.takeScreenshot()?.let { bitmap -> File(folder, "camera-review-ui.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle() }
            throw AssertionError("Native camera did not return to preview:\n$nodes", failure)
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithTag("avatar-photo-preview").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("avatar-photo-preview").assertExists()
        shot("03-vista-previa-foto-real")
        compose.onNodeWithText("Usar esta foto").performClick()
        waitText("Cambiar mi foto")
        val photo = runBlocking { cases.profile()!!.avatarImageReference }
        assertNotNull(photo)
        assertTrue(runBlocking { container.avatarImageStorage.loadFromReference(requireNotNull(photo), 640) }!!.isNotEmpty())
        compose.activityRule.scenario.recreate()
        waitText("Cambiar mi foto")
        assertEquals(photo, runBlocking { cases.profile()!!.avatarImageReference })
        shot("04-foto-perfil-persistida")
    }
}
