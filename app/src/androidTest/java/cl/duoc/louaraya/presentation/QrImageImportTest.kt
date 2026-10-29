package cl.duoc.louaraya.presentation

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.platform.app.InstrumentationRegistry
import cl.duoc.louaraya.MainActivity
import cl.duoc.louaraya.data.image.MlKitQrImageDecoder
import cl.duoc.louaraya.domain.image.QrImageReadException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID


class QrImageImportTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val files = mutableListOf<File>()
    @After fun cleanup() { files.forEach { it.delete() } }

    private fun newFile(extension: String = "png"): File = File(context.filesDir,
        "camera-captures/qr-test-${UUID.randomUUID()}.$extension").also { it.parentFile!!.mkdirs(); files += it }
    private fun uri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    private fun qr(): File = newFile().also { file ->
        instrumentation.context.assets.open("qr-tapiz-aurora.png").use { input -> file.outputStream().use { output -> input.copyTo(output) } }
    }
    private fun waitText(text: String, substring: Boolean = false) = compose.waitUntil(30_000) {
        compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
    }
    private fun scanCount(): Long = SQLiteDatabase.openDatabase(context.getDatabasePath("louaraya.db").path,
        null, SQLiteDatabase.OPEN_READONLY).use { db -> db.rawQuery("SELECT COUNT(*) FROM qr_scans", null).use {
            it.moveToFirst(); it.getLong(0)
        } }
    private fun openScanner(alias: String = "cliente.demo") {
        compose.signInDemo(alias)
        compose.onNodeWithText("Escanear QR").performScrollTo().performClick()
        waitText("Leer QR desde imagen")
    }
    private fun chooseImage(source: Uri?) {
        val result = Instrumentation.ActivityResult(if (source == null) Activity.RESULT_CANCELED else Activity.RESULT_OK,
            source?.let { Intent().setData(it).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
        val monitor = instrumentation.addMonitor(IntentFilter(MediaStore.ACTION_PICK_IMAGES).apply { addDataType("image/*") }, result, true)
        try {
            compose.onNodeWithText("Leer QR desde imagen").performScrollTo().performClick()
            compose.waitUntil(10_000) { monitor.hits == 1 }
        } finally { instrumentation.removeMonitor(monitor) }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val folder = File(context.getExternalFilesDir(null), "qr-image-evidence").apply { mkdirs() }
        val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun selectedPngOpensItsProductAndRecordsExactlyOneScanAcrossRecreation() {
        openScanner()
        val before = scanCount()
        chooseImage(uri(qr()))
        waitText("La pieza")
        compose.onNodeWithText("Tapiz Aurora").assertIsDisplayed()
        assertEquals(before + 1, scanCount())
        shot("02-imagen-abre-producto")
        compose.activityRule.scenario.recreate()
        waitText("La pieza")
        assertEquals(before + 1, scanCount())
    }

    @Test fun imageWithoutQrCanBeRetriedAndReadByAnAdministrator() {
        openScanner("admin.demo")
        val before = scanCount()
        val blank = newFile()
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        blank.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        chooseImage(uri(blank))
        waitText("No encontramos un QR en la imagen.", substring = true)
        assertEquals(before, scanCount())
        shot("03-imagen-sin-qr-recuperable")
        chooseImage(uri(qr()))
        waitText("La pieza")
        compose.onNodeWithText("Editar ficha").performScrollTo().assertIsDisplayed()
        assertEquals(before + 1, scanCount())
    }

    @Test fun corruptImageShowsAnErrorAndManualEntryStillWorks() {
        openScanner()
        val before = scanCount()
        chooseImage(uri(newFile().apply { writeText("Not an image") }))
        waitText("El archivo no contiene una imagen válida.", substring = true)
        assertEquals(before, scanCount())
        compose.onNodeWithText("Código de etiqueta").performScrollTo().performTextInput("louaraya://product/1")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Consultar código").performScrollTo().performClick()
        waitText("La pieza")
        assertEquals(before + 1, scanCount())
    }

    @Test fun nativePickerCanBeCancelledWithoutScanningOrLosingManualText() {
        openScanner()
        val before = scanCount()
        compose.onNodeWithText("Código de etiqueta").performScrollTo().performTextInput("louaraya://product/1")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        shot("01-lector-con-imagen")
        compose.onNodeWithText("Leer QR desde imagen").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            val active = instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString()
            active != null && active != context.packageName
        }
        instrumentation.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").use { }
        compose.waitUntil(10_000) { instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName }
        compose.onNodeWithText("Código de etiqueta").performScrollTo().assertTextContains("louaraya://product/1")
        assertEquals(before, scanCount())
        compose.onNodeWithText("Consultar código").performScrollTo().assertIsEnabled()
    }

    @Test fun largeJpegWithExifRotationIsDecodedAndTemporaryCopiesAreDeleted() = runBlocking {
        val original = BitmapFactory.decodeFile(qr().path)
        val large = Bitmap.createBitmap(3000, 3000, Bitmap.Config.ARGB_8888)
        Canvas(large).apply {
            drawColor(Color.WHITE)
            drawBitmap(original, null, Rect(0, 0, 3000, 3000), Paint())
        }
        val jpeg = newFile("jpg")
        jpeg.outputStream().use { large.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        large.recycle(); original.recycle()
        ExifInterface(jpeg).apply { setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
        assertEquals("louaraya://product/1", MlKitQrImageDecoder(context).decode(uri(jpeg).toString()))
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("qr-image-") })
    }

    @Test fun oversizedImageIsRejectedWithoutLeavingATemporaryCopy() = runBlocking {
        val oversized = newFile()
        oversized.outputStream().use { output ->
            val block = ByteArray(1024 * 1024)
            repeat(21) { output.write(block) }
        }
        try {
            MlKitQrImageDecoder(context).decode(uri(oversized).toString())
            fail("Expected oversized image rejection")
        } catch (error: QrImageReadException) { assertTrue(error.message!!.contains("20 MB")) }
        assertTrue(context.cacheDir.listFiles().orEmpty().none { it.name.startsWith("qr-image-") })
    }
}
