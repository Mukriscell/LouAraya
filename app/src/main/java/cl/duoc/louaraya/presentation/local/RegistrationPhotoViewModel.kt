package cl.duoc.louaraya.presentation.local

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.image.ProductImageStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class RegistrationPhotoState(
    val previewUri: String? = null, val pendingUri: String? = null,
    val launchUri: String? = null, val busy: Boolean = true, val error: String? = null
)


class RegistrationPhotoViewModel(
    private val storage: ProductImageStorage,
    private val handle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val mutable = MutableStateFlow(RegistrationPhotoState(handle["preview"], handle["pending"]))
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
        val capture = mutable.value.pendingUri ?: return
        handle["cameraResult"] = success
        if (mutable.value.busy) return
        publish(mutable.value.copy(busy = true, launchUri = null, error = null))
        viewModelScope.launch {
            try {
                if (success) {
                    require(storage.loadPreview(capture, 640)?.isNotEmpty() == true) { "No se pudo leer la foto. Vuelve a tomarla." }
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

    fun useIllustration() {
        if (mutable.value.busy) return
        val captures = setOfNotNull(mutable.value.previewUri, mutable.value.pendingUri)
        publish(RegistrationPhotoState(busy = true))
        viewModelScope.launch {
            captures.forEach { cleanup(it) }
            handle.remove<Boolean>("cameraResult")
            publish(RegistrationPhotoState(busy = false))
        }
    }

    fun cameraUnavailable() {
        cameraResult(false)
        showError("No se pudo abrir la cámara. Puedes volver a intentar o elegir un icono.")
    }

    fun showError(message: String) { publish(mutable.value.copy(error = message)) }
    private suspend fun cleanup(uri: String) = withContext(NonCancellable) { runCatching { storage.deleteCameraCapture(uri) }; Unit }
    private fun publish(value: RegistrationPhotoState) {
        handle["preview"] = value.previewUri
        handle["pending"] = value.pendingUri
        mutable.value = value
    }

    override fun onCleared() {
        val captures = setOfNotNull(mutable.value.previewUri, mutable.value.pendingUri)
        CoroutineScope(Dispatchers.IO + NonCancellable).launch { captures.forEach { cleanup(it) } }
        super.onCleared()
    }
}
