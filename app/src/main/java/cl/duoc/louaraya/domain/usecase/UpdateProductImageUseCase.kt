package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.repository.ProductRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Saves a normalized private image, updates one database column, then removes the old file. */
class UpdateProductImageUseCase(
    private val productRepository: ProductRepository,
    private val imageStorage: ProductImageStorage
) {
    suspend operator fun invoke(productId: Long, sourceUri: String): String {
        val current = requireNotNull(productRepository.getProductById(productId)) {
            "El producto ya no está disponible."
        }
        val newReference = imageStorage.saveFromSource(sourceUri)
        try {
            currentCoroutineContext().ensureActive()
            check(productRepository.updateImageReference(productId, newReference)) {
                "No fue posible guardar la imagen del producto."
            }
        } catch (failure: Throwable) {
            // Room may finish its UPDATE immediately before cancellation is delivered. Inspect
            // the committed reference before cleaning files so no row points to a deleted file.
            withContext(NonCancellable) {
                var lookupSucceeded = false
                var latest: Product? = null
                try {
                    latest = productRepository.getProductById(productId)
                    lookupSucceeded = true
                } catch (_: Exception) {
                    // An unknown commit outcome must preserve the new file rather than risk a dangling row.
                }
                if (lookupSucceeded) {
                    if (latest?.imageReference == newReference) {
                        val oldReference = current.imageReference
                        if (oldReference != null) try { imageStorage.delete(oldReference) } catch (_: Exception) { }
                    } else {
                        try { imageStorage.delete(newReference) } catch (_: Exception) { }
                    }
                }
            }
            throw failure
        }
        withContext(NonCancellable) {
            val oldReference = current.imageReference
            if (oldReference != null) try { imageStorage.delete(oldReference) } catch (_: Exception) { }
        }
        return newReference
    }
}
