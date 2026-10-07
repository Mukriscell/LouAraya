package cl.duoc.louaraya.data.image

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.exifinterface.media.ExifInterface
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
}
