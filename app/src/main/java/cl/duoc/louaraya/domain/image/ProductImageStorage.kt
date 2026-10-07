package cl.duoc.louaraya.domain.image

/** Platform-neutral boundary for private product-image files. References are app-relative strings. */
interface ProductImageStorage {
    suspend fun saveFromSource(sourceUri: String): String
    suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray?
    suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray?
    suspend fun delete(reference: String)

    /** Creates an app-private capture target and returns its temporary content URI as a string. */
    suspend fun createCameraCaptureUri(): String =
        error("Camera capture is not supported by this image storage")

    /** Removes one camera capture URI if it belongs to this app's private capture directory. */
    suspend fun deleteCameraCapture(uri: String) = Unit

    /** Removes abandoned capture files while preserving captures referenced by a restored draft. */
    suspend fun cleanupAbandonedCameraCaptures(preserveUris: Set<String>) = Unit
}
