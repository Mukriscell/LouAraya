package cl.duoc.louaraya.domain.image

/** Platform-neutral boundary for private product-image files. References are app-relative strings. */
interface ProductImageStorage {
    suspend fun saveFromSource(sourceUri: String): String
    suspend fun loadFromReference(reference: String, maxDimensionPx: Int): ByteArray?
    suspend fun loadPreview(sourceUri: String, maxDimensionPx: Int): ByteArray?
    suspend fun delete(reference: String)
}
