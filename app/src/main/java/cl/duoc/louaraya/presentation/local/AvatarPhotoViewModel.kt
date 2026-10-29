package cl.duoc.louaraya.presentation.local

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.usecase.UpdateAvatarPhotoUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AvatarPhotoState(
    val previewUri: String? = null, val pendingUri: String? = null,
    val launchUri: String? = null, val busy: Boolean = true,
    val error: String? = null, val saved: Boolean = false
)


class AvatarPhotoViewModel(
    private val userId: Long,
    private val storage: ProductImageStorage,
    private val update: UpdateAvatarPhotoUseCase,
    private val handle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val mutable = MutableStateFlow(AvatarPhotoState(handle["preview"], handle["pending"]))
    val state = mutable.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { storage.cleanupAbandonedCameraCaptures(setOfNotNull(handle["preview"], handle["pending"])) }
            publish(mutable.value.copy(busy = false))
            handle.get<Boolean>("cameraResult")?.let(::cameraResult)
        }
    }

    fun takePhoto() {
        if (mutable.value.busy || mutable.value.launchUri != null) return
        mutable.value.pendingUri?.let { publish(mutable.value.copy(launchUri = it, error = null)); return }
        publish(mutable.value.copy(busy = true, error = null))
        viewModelScope.launch {
            try {
                val uri = storage.createCameraCaptureUri()
                publish(mutable.value.copy(pendingUri = uri, launchUri = uri, busy = false))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { publish(mutable.value.copy(busy = false, error = "No se pudo preparar la cámara. Vuelve a intentar.")) }
        }
    }

    fun claimLaunch(uri: String): Boolean {
        if (mutable.value.launchUri != uri) return false
        publish(mutable.value.copy(launchUri = null))
        return true
    }

    fun cameraResult(success: Boolean) {
        if (mutable.value.pendingUri == null) return
        handle["cameraResult"] = success
        if (mutable.value.busy) return
        val capture = requireNotNull(mutable.value.pendingUri)
        publish(mutable.value.copy(busy = true, launchUri = null, error = null))
        viewModelScope.launch {
            try {
                if (success) {
                    val preview = storage.loadPreview(capture, 640)
                    require(preview != null && preview.isNotEmpty()) { "No se pudo leer la foto. Vuelve a tomarla." }
                    mutable.value.previewUri?.takeIf { it != capture }?.let { cleanup(it) }
                    publish(mutable.value.copy(previewUri = capture, pendingUri = null, busy = false))
                } else {
                    cleanup(capture)
                    publish(mutable.value.copy(pendingUri = null, busy = false))
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                cleanup(capture)
                publish(mutable.value.copy(pendingUri = null, busy = false, error = failure.message ?: "Vuelve a tomar la foto."))
            } finally { handle.remove<Boolean>("cameraResult") }
        }
    }

    fun cameraUnavailable() {
        cameraResult(false)
        showError("No se pudo abrir la cámara. Comprueba que haya una aplicación de cámara disponible.")
    }

    fun showError(message: String) { publish(mutable.value.copy(error = message)) }

    fun save() {
        val current = mutable.value
        val uri = current.previewUri ?: return
        if (current.busy || current.pendingUri != null) return
        publish(current.copy(busy = true, error = null))
        viewModelScope.launch {
            try {
                update(userId, uri)
                cleanup(uri)
                publish(mutable.value.copy(busy = false, previewUri = null, saved = true))
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                publish(mutable.value.copy(busy = false, error = failure.message ?: "No pudimos guardar la foto. Puedes reintentar."))
            }
        }
    }

    suspend fun discard(): Boolean {
        if (mutable.value.busy) return false
        val uris = setOfNotNull(mutable.value.previewUri, mutable.value.pendingUri)
        publish(mutable.value.copy(busy = true))
        uris.forEach { cleanup(it) }
        handle.remove<Boolean>("cameraResult")
        publish(AvatarPhotoState(busy = false))
        return true
    }

    private suspend fun cleanup(uri: String) = withContext(NonCancellable) { runCatching { storage.deleteCameraCapture(uri) }; Unit }
    private fun publish(value: AvatarPhotoState) {
        handle["preview"] = value.previewUri
        handle["pending"] = value.pendingUri
        mutable.value = value
    }
}
