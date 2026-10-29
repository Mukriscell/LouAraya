package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.image.ProductImageStorage
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

class UpdateAvatarPhotoUseCase(private val local: LocalUseCases, private val storage: ProductImageStorage) {
    suspend operator fun invoke(userId: Long, sourceUri: String): String {
        require(local.profile()?.id == userId) { "La sesión cambió. Vuelve a tu perfil." }
        val reference = storage.saveFromSource(sourceUri)
        return withContext(NonCancellable) {
            val old = try { local.setAvatarPhoto(userId, reference) }
            catch (failure: Throwable) { runCatching { storage.delete(reference) }; throw failure }
            if (old != null && old != reference) runCatching { storage.delete(old) }
            reference
        }
    }
}
