package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.model.UserProfile
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext


class RegisterWithPhotoUseCase(private val local: LocalUseCases, private val storage: ProductImageStorage) {
    suspend operator fun invoke(name: String, alias: String, password: String, avatar: Int, contact: String, photoUri: String?, confirmation: String = password): UserProfile {
        FormValidation.matchingPasswords(password, confirmation)
        FormValidation.registrationPassword(password)
        val reference = photoUri?.let { storage.saveFromSource(it) }
        return withContext(NonCancellable) {
            val profile = try { local.register(name, alias, password, avatar, contact, reference) }
            catch (failure: Throwable) {
                if (reference != null) runCatching { storage.delete(reference) }
                throw failure
            }
            if (photoUri != null) runCatching { storage.deleteCameraCapture(photoUri) }
            profile
        }
    }
}
