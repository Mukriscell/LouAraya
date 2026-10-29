package cl.duoc.louaraya.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.core.content.FileProvider
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.exifinterface.media.ExifInterface
import cl.duoc.louaraya.domain.image.ProductImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference


class LocalProductImageStorage(
    context: Context,
    private val imageDirectoryName: String = "product-images",
    private val captureDirectoryName: String = "camera-captures",
    private val savedDimension: Int = 1600
) : ProductImageStorage {
    init {
        require(imageDirectoryName in setOf("product-images", "avatar-images"))
        require(captureDirectoryName in setOf("camera-captures", "avatar-captures"))
        require(savedDimension in 1..1600)
    }
    private val appContext = context.applicationContext
    private val imageDirectory = File(appContext.filesDir, imageDirectoryName)
    private val cameraCaptureDirectory = File(appContext.filesDir, captureDirectoryName)
    private val referencePattern = Regex(Regex.escape(imageDirectoryName) + "/[0-9a-fA-F-]{36}\\.jpg")

    override suspend fun createCameraCaptureUri(): String {
        val createdFile = AtomicReference<File?>(null)
        try {
            return withContext(Dispatchers.IO) {
                check(cameraCaptureDirectory.mkdirs() || cameraCaptureDirectory.isDirectory) {
                    "No se pudo preparar la captura de fotografía."
                }
                val file = File(cameraCaptureDirectory, "${UUID.randomUUID()}.jpg")
                check(file.createNewFile()) { "No se pudo crear el archivo temporal de fotografía." }
                createdFile.set(file)
                FileProvider.getUriForFile(
                    appContext,
                    "${appContext.packageName}.fileprovider",
                    file
                ).toString()
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { createdFile.get()?.delete() }
            throw failure
        }
    }

    override suspend fun deleteCameraCapture(uri: String) {
        withContext(Dispatchers.IO) { resolveCameraCapture(uri)?.delete() }
    }

    override suspend fun cleanupAbandonedCameraCaptures(preserveUris: Set<String>) {
        withContext(Dispatchers.IO) {
            val preservedPaths = preserveUris.mapNotNull(::resolveCameraCapture)
                .mapTo(mutableSetOf()) { it.canonicalPath }
            cameraCaptureDirectory.listFiles()?.filter(File::isFile)
                ?.filterNot { it.canonicalPath in preservedPaths }
                ?.forEach(File::delete)
        }
    }

    override suspend fun saveFromSource(sourceUri: String): String {
        val createdFile = AtomicReference<File?>(null)
        try {
            return withContext(Dispatchers.IO) {
                val uri = sourceUri.toUri()
                require(uri.scheme == "content" || uri.scheme == "file") { "La imagen seleccionada no es válida." }
                check(imageDirectory.mkdirs() || imageDirectory.isDirectory) { "No se pudo preparar el almacenamiento de imágenes." }
                val token = UUID.randomUUID().toString()
                val sourceFile = File(appContext.cacheDir, "$token.source")
                val pendingFile = File(imageDirectory, "$token.pending")
                val finalFile = File(imageDirectory, "$token.jpg")
                createdFile.set(finalFile)
                try {
                    val input = if (uri.scheme == "file") {
                        requireNotNull(uri.path).let(::File).inputStream()
                    } else {
                        requireNotNull(appContext.contentResolver.openInputStream(uri)) { "No se pudo abrir la imagen seleccionada." }
                    }
                    input.use { source ->
                        sourceFile.outputStream().use { target ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            var total = 0L
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                currentCoroutineContext().ensureActive()
                                total += count
                                require(total <= MAX_SOURCE_BYTES) { "La imagen supera el tamaño permitido." }
                                target.write(buffer, 0, count)
                            }
                        }
                    }
                    val bitmap = decodeOriented(sourceFile, savedDimension)
                        ?: error("El archivo seleccionado no contiene una imagen válida.")
                    try {
                        currentCoroutineContext().ensureActive()
                        FileOutputStream(pendingFile).use { output ->
                            check(bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)) {
                                "No se pudo procesar la imagen seleccionada."
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                    check(pendingFile.renameTo(finalFile)) { "No se pudo guardar la imagen seleccionada." }
                    imageDirectoryName + "/" + finalFile.name
                } finally {
                    sourceFile.delete()
                    pendingFile.delete()
                }
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { createdFile.get()?.delete() }
            throw failure
        }
    }

    override suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            resolve(reference)?.takeIf(File::isFile)?.let { file ->
                runCatching { decodeOriented(file, maxDimensionPx)?.toJpegBytes() }.getOrNull()
            }
        }

    override suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray? =
        withContext(Dispatchers.IO) {
            try {
                val uri = sourceUri.toUri()
                val temporary = if (uri.scheme == "file") {
                    requireNotNull(uri.path).let(::File)
                } else {
                    require(uri.scheme == "content")
                    val file = File(appContext.cacheDir, "preview-${UUID.randomUUID()}.source")
                    try {
                        requireNotNull(appContext.contentResolver.openInputStream(uri)).use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                var total = 0L
                                while (true) {
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    currentCoroutineContext().ensureActive()
                                    total += count
                                    require(total <= MAX_SOURCE_BYTES)
                                    output.write(buffer, 0, count)
                                }
                            }
                        }
                        file
                    } catch (failure: Throwable) {
                        file.delete()
                        throw failure
                    }
                }
                try {
                    decodeOriented(temporary, maxDimensionPx)?.toJpegBytes()
                } finally {
                    if (uri.scheme != "file") temporary.delete()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        }

    override suspend fun delete(reference: String) {
        withContext(Dispatchers.IO) { resolve(reference)?.delete() }
    }

    private fun resolve(reference: String): File? {
        if (!referencePattern.matches(reference)) return null
        val file = File(appContext.filesDir, reference)
        val base = imageDirectory.canonicalFile
        val target = file.canonicalFile
        return file.takeIf { target.parentFile == base }
    }

    private fun resolveCameraCapture(uriString: String): File? {
        val uri = runCatching { uriString.toUri() }.getOrNull() ?: return null
        if (uri.scheme != "content" || uri.authority != "${appContext.packageName}.fileprovider") return null
        val segments = uri.pathSegments
        if (segments.size != 2 || segments[0] != captureDirectoryName ||
            !CAMERA_FILENAME_PATTERN.matches(segments[1])
        ) return null
        val file = File(cameraCaptureDirectory, segments[1])
        val base = cameraCaptureDirectory.canonicalFile
        return file.takeIf { it.canonicalFile.parentFile == base }
    }

    private fun decodeOriented(file: File, maxDimension: Int): Bitmap? {
        require(maxDimension > 0)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0 ||
            bounds.outWidth.toLong() * bounds.outHeight > MAX_SOURCE_PIXELS
        ) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxDimension) sample *= 2
        val decoded = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }) ?: return null
        val orientation = runCatching { ExifInterface(file.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        ) }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(270f); postScale(-1f, 1f) }
                ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
            }
        }
        val oriented = if (!matrix.isIdentity) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else decoded
        val largest = maxOf(oriented.width, oriented.height)
        if (largest <= maxDimension) return oriented
        val ratio = maxDimension.toFloat() / largest
        val scaled = oriented.scale(
            (oriented.width * ratio).toInt().coerceAtLeast(1),
            (oriented.height * ratio).toInt().coerceAtLeast(1), true
        )
        if (scaled !== oriented) oriented.recycle()
        return scaled
    }

    private fun Bitmap.toJpegBytes(): ByteArray = ByteArrayOutputStream().use { output ->
        try {
            check(compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output))
            output.toByteArray()
        } finally {
            recycle()
        }
    }

    private companion object {
        const val MAX_SOURCE_BYTES = 25L * 1024 * 1024
        const val MAX_SOURCE_PIXELS = 100_000_000L
        const val JPEG_QUALITY = 88
        val CAMERA_FILENAME_PATTERN = Regex("[0-9a-fA-F-]{36}\\.jpg")
    }
}
