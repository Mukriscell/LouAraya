package cl.duoc.louaraya.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import cl.duoc.louaraya.domain.image.QrImageDecoder
import cl.duoc.louaraya.domain.image.QrImageReadException
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MlKitQrImageDecoder(context: Context) : QrImageDecoder {
    private val context = context.applicationContext

    override suspend fun decode(sourceUri: String): String = withContext(Dispatchers.IO) {
        require(sourceUri.toUri().scheme == "content") { "Selecciona una imagen desde el selector de fotos." }
        val source = File.createTempFile("qr-image-", ".source", context.cacheDir)
        try {
            val input = context.contentResolver.openInputStream(sourceUri.toUri())
                ?: throw QrImageReadException("No pudimos abrir la imagen. Selecciónala de nuevo.")
            input.use { stream ->
                source.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = stream.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw QrImageReadException("La imagen supera los 20 MB. Elige una imagen más pequeña.")
                        output.write(buffer, 0, count)
                    }
                }
            }
            val bitmap = decodeBounded(source)
            try {
                currentCoroutineContext().ensureActive()
            } catch (error: Exception) {
                bitmap.recycle()
                throw error
            }
            scan(bitmap)
        } finally {
            source.delete()
        }
    }

    private fun decodeBounded(file: File): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw QrImageReadException(
            "El archivo no contiene una imagen válida. Elige una foto PNG o JPEG.")
        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: throw QrImageReadException("No pudimos leer la imagen. Prueba con otra foto.")
        try {
            val exif = ExifInterface(file)
            val matrix = Matrix().apply {
                if (exif.isFlipped) postScale(-1f, 1f)
                postRotate(exif.rotationDegrees.toFloat())
            }
            return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
    }

    private suspend fun scan(bitmap: Bitmap): String {

        val scanner = try {
            BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        } catch (error: Exception) {
            bitmap.recycle()
            throw error
        }
        return suspendCancellableCoroutine { continuation ->
            try {
                scanner.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
                    try {
                        if (continuation.isActive) {
                            if (task.isSuccessful) continuation.resume(singleQrValue(task.result.mapNotNull { it.rawValue }))
                            else continuation.resumeWithException(task.exception ?: QrImageReadException("No pudimos analizar la imagen. Inténtalo de nuevo."))
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    } finally {
                        scanner.close()
                        bitmap.recycle()
                    }
                }
            } catch (error: Exception) {
                scanner.close()
                bitmap.recycle()
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
    }

    private companion object {
        const val MAX_BYTES = 20L * 1024 * 1024
        const val MAX_DIMENSION = 2048
    }
}

internal fun singleQrValue(values: List<String>): String {
    val distinct = values.filter { it.isNotBlank() }.distinct()
    if (distinct.isEmpty()) throw QrImageReadException("No encontramos un QR en la imagen. Prueba con una foto más nítida y con el código completo.")
    if (distinct.size > 1) throw QrImageReadException("La imagen contiene varios códigos QR. Elige una imagen con una sola etiqueta.")
    return distinct.single()
}
