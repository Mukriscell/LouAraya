package cl.duoc.louaraya.data.image

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.exifinterface.media.ExifInterface
import androidx.core.content.FileProvider
import cl.duoc.louaraya.R
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class LocalProductImageStorageTest {
    @Test
    fun savesPrivateRelativeReferenceLoadsBoundedImageAndDeletesIt() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = LocalProductImageStorage(context)
        val source = File(context.cacheDir, "image-storage-fixture.jpg")
        context.resources.openRawResource(R.drawable.product_tapiz_aurora).use { input ->
            source.outputStream().use(input::copyTo)
        }
        try {
            val reference = storage.saveFromSource(Uri.fromFile(source).toString())
            assertTrue(reference.startsWith("product-images/"))
            assertTrue(!File(reference).isAbsolute)
            val persistedFile = File(context.filesDir, reference)
            assertTrue(persistedFile.isFile)
            val freshStorage = LocalProductImageStorage(context)
            val bytes = freshStorage.loadFromReference(reference, 160)
            assertNotNull(bytes)
            val decoded = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes!!.size))
            assertNotNull(decoded)
            assertTrue(maxOf(decoded.width, decoded.height) <= 160)
            decoded.recycle()

            freshStorage.delete(reference)
            assertNull(freshStorage.loadFromReference(reference, 160))
            assertNull(freshStorage.loadFromReference("../louaraya.db", 160))
            val corruptStoredFile = File(context.filesDir, reference)
            corruptStoredFile.writeText("corrupt image bytes")
            assertNull(freshStorage.loadFromReference(reference, 160))
            corruptStoredFile.delete()
        } finally {
            source.delete()
        }
        Unit
    }

    @Test
    fun corruptPickerInputIsRejected() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val imageDirectory = File(context.filesDir, "product-images")
        val imageFilesBefore = imageDirectory.list()?.toSet().orEmpty()
        val sourceTempsBefore = context.cacheDir.listFiles { file -> file.name.endsWith(".source") }
            ?.map(File::getName)?.toSet().orEmpty()
        val source = File(context.cacheDir, "corrupt-image-fixture.bin").apply { writeText("not an image") }
        try {
            val failure = runCatching {
                LocalProductImageStorage(context).saveFromSource(Uri.fromFile(source).toString())
            }.exceptionOrNull()
            assertNotNull(failure)
        } finally {
            source.delete()
        }
        assertEquals(imageFilesBefore, imageDirectory.list()?.toSet().orEmpty())
        val sourceTempsAfter = context.cacheDir.listFiles { file -> file.name.endsWith(".source") }
            ?.map(File::getName)?.toSet().orEmpty()
        assertEquals(sourceTempsBefore, sourceTempsAfter)
        Unit
    }

    @Test
    fun normalizesExifRotatedImagesBeforePersisting() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = LocalProductImageStorage(context)
        val source = File(context.cacheDir, "exif-orientation-fixture.jpg")
        val bitmap = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        FileOutputStream(source).use { output -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output) }
        bitmap.recycle()
        ExifInterface(source.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        try {
            val reference = storage.saveFromSource(Uri.fromFile(source).toString())
            val bytes = requireNotNull(storage.loadFromReference(reference, 512))
            val normalized = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            assertTrue("EXIF rotation should be applied", normalized.height > normalized.width)
            normalized.recycle()
            storage.delete(reference)
        } finally {
            source.delete()
        }
        Unit
    }

    @Test
    fun cameraCaptureUriIsScopedPreservedDuringCleanupAndSavedAsPrivateImage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val storage = LocalProductImageStorage(context)
        val activeUri = Uri.parse(storage.createCameraCaptureUri())
        val abandonedUri = Uri.parse(storage.createCameraCaptureUri())
        val captureDirectory = File(context.filesDir, "camera-captures")
        val activeFile = File(captureDirectory, requireNotNull(activeUri.lastPathSegment))
        val abandonedFile = File(captureDirectory, requireNotNull(abandonedUri.lastPathSegment))
        val outsideFile = File(context.filesDir, "outside-camera-captures.jpg")

        try {
            assertEquals("content", activeUri.scheme)
            assertEquals("${context.packageName}.fileprovider", activeUri.authority)
            assertEquals(listOf("camera-captures", activeFile.name), activeUri.pathSegments)
            assertTrue(activeFile.isFile)
            assertTrue(abandonedFile.isFile)
            assertTrue(outsideFile.createNewFile())
            val outsideFileFailure = runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outsideFile
                )
            }.exceptionOrNull()
            assertTrue("The FileProvider must reject files outside its configured directory", outsideFileFailure is IllegalArgumentException)

            storage.cleanupAbandonedCameraCaptures(setOf(activeUri.toString()))
            assertTrue("The active camera target must survive cleanup", activeFile.isFile)
            assertTrue("An abandoned camera target must be removed", !abandonedFile.exists())

            context.resources.openRawResource(R.drawable.product_tapiz_aurora).use { input ->
                requireNotNull(context.contentResolver.openOutputStream(activeUri)).use(input::copyTo)
            }
            assertNotNull(storage.loadPreview(activeUri.toString(), 1600))

            val reference = storage.saveFromSource(activeUri.toString())
            val persisted = File(context.filesDir, reference)
            assertTrue(persisted.isFile)
            assertNotNull(LocalProductImageStorage(context).loadFromReference(reference, 1600))

            storage.deleteCameraCapture(activeUri.toString())
            assertTrue("Deleting a capture must remove only its temporary file", !activeFile.exists())
            storage.delete(reference)

            val protectedUri = Uri.parse(storage.createCameraCaptureUri())
            val protectedFile = File(captureDirectory, requireNotNull(protectedUri.lastPathSegment))
            storage.deleteCameraCapture("content://untrusted.example/camera-captures/${protectedFile.name}")
            assertTrue("A foreign provider URI must not delete app files", protectedFile.isFile)
            storage.deleteCameraCapture(protectedUri.toString())
        } finally {
            activeFile.delete()
            abandonedFile.delete()
            outsideFile.delete()
        }
        Unit
    }
}
