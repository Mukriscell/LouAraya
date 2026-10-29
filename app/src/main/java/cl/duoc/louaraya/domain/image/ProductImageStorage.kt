package cl.duoc.louaraya.domain.image


interface ProductImageStorage {
    suspend fun saveFromSource(sourceUri: String): String
    suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray?
    suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray?
    suspend fun delete(reference: String)


    suspend fun createCameraCaptureUri(): String =
        error("Camera capture is not supported by this image storage")


    suspend fun deleteCameraCapture(uri: String) = Unit


    suspend fun cleanupAbandonedCameraCaptures(preserveUris: Set<String>) = Unit
}
