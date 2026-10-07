package cl.duoc.louaraya.presentation.admin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.image.ProductImageStorage
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import cl.duoc.louaraya.domain.usecase.UpdateProductImageUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class AdminProductImageViewModel(
    private val productId: Long,
    private val getProductById: GetProductByIdUseCase,
    private val updateProductImage: UpdateProductImageUseCase,
    private val imageStorage: ProductImageStorage,
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
    private val _uiState = MutableStateFlow<AdminProductImageUiState>(AdminProductImageUiState.Loading)
    val uiState: StateFlow<AdminProductImageUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var cameraPreparationJob: Job? = null
    private var cameraResultJob: Job? = null
    private var isHandlingCameraResult = false
    private val pendingCameraDeletionUris = ConcurrentHashMap.newKeySet<String>()

    private val abandonedCaptureCleanupJob = viewModelScope.launch {
        try {
            imageStorage.cleanupAbandonedCameraCaptures(restoredCameraUris())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Cleanup is best-effort; capture and image flows remain usable if a stale file is locked.
        }
    }

    init { loadProduct() }

    override fun onCleared() {
        val activeCaptures = restoredCameraUris() + pendingCameraDeletionUris
        super.onCleared()
        if (activeCaptures.isNotEmpty()) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                deleteCameraCaptures(activeCaptures.toList())
            }
        }
    }

    fun loadProduct() {
        loadJob?.cancel()
        _uiState.value = AdminProductImageUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val product = getProductById(productId)
                if (product == null) {
                    _uiState.value = AdminProductImageUiState.Error("El producto ya no está disponible.")
                } else {
                    val restored = restoredReady(product)
                    publish(restored)
                    processCameraResultIfReady()
                }
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                _uiState.value = AdminProductImageUiState.Error(failure.message ?: LOAD_ERROR)
            }
        }
    }

    /** A null picker result means cancellation; keep any prior unsaved selection. */
    fun selectImage(sourceUri: String?) {
        if (sourceUri == null) return
        val ready = _uiState.value as? AdminProductImageUiState.Ready ?: return
        if (ready.isSaving || ready.isPreparingCameraCapture || ready.pendingCameraUri != null) return

        val previousCameraUri = ready.selectedCameraUri
        publish(ready.copy(
            previewSourceUri = sourceUri,
            selectedCameraUri = null,
            error = null,
            isSaved = false
        ))
        previousCameraUri?.let(::deleteCameraCaptureInBackground)
    }

    fun prepareCameraCapture() {
        val ready = _uiState.value as? AdminProductImageUiState.Ready ?: return
        if (ready.isSaving || ready.isPreparingCameraCapture || ready.pendingCameraUri != null) return

        publish(ready.copy(isPreparingCameraCapture = true, error = null, isSaved = false))
        cameraPreparationJob = viewModelScope.launch {
            var createdUri: String? = null
            try {
                abandonedCaptureCleanupJob.join()
                createdUri = imageStorage.createCameraCaptureUri()
                val current = _uiState.value as? AdminProductImageUiState.Ready
                if (current == null || !current.isPreparingCameraCapture) {
                    deleteCameraCaptures(listOfNotNull(createdUri))
                    return@launch
                }
                clearCameraResultState()
                publish(current.copy(
                    isPreparingCameraCapture = false,
                    pendingCameraUri = createdUri,
                    cameraLaunchUri = createdUri,
                    error = null
                ))
                createdUri = null
            } catch (cancelled: CancellationException) {
                createdUri?.let { deleteCameraCaptures(listOf(it)) }
                throw cancelled
            } catch (failure: Exception) {
                createdUri?.let { deleteCameraCaptures(listOf(it)) }
                (_uiState.value as? AdminProductImageUiState.Ready)?.let { current ->
                    publish(current.copy(
                        isPreparingCameraCapture = false,
                        error = failure.message ?: CAMERA_PREPARATION_ERROR
                    ))
                }
            }
        }
    }

    /** Consumes the one-shot launcher request before calling ActivityResultLauncher.launch. */
    fun consumeCameraLaunchRequest(uri: String): Boolean {
        val ready = _uiState.value as? AdminProductImageUiState.Ready ?: return false
        if (ready.cameraLaunchUri != uri || ready.pendingCameraUri != uri) return false
        publish(ready.copy(cameraLaunchUri = null))
        return true
    }

    /** Reuses an interrupted capture target after process recreation without launching automatically. */
    fun retryCameraCapture() {
        val ready = _uiState.value as? AdminProductImageUiState.Ready ?: return
        val pendingUri = ready.pendingCameraUri ?: return
        if (ready.isSaving || ready.isPreparingCameraCapture || ready.cameraLaunchUri != null) return
        publish(ready.copy(cameraLaunchUri = pendingUri, error = null))
    }

    fun onCameraLaunchFailed(uri: String, message: String? = null) {
        val ready = _uiState.value as? AdminProductImageUiState.Ready ?: return
        if (ready.pendingCameraUri != uri) return
        clearCameraResultState()
        publish(ready.copy(
            pendingCameraUri = null,
            cameraLaunchUri = null,
            isPreparingCameraCapture = false,
            error = message ?: CAMERA_LAUNCH_ERROR
        ))
        deleteCameraCaptureInBackground(uri)
    }

    /** Stores the result even if product loading has not completed after process recreation. */
    fun onCameraCaptureResult(saved: Boolean) {
        if (savedStateHandle.get<String>(AdminProductImageSavedStateKeys.PENDING_CAMERA_URI) == null &&
            (_uiState.value as? AdminProductImageUiState.Ready)?.pendingCameraUri == null
        ) return
        savedStateHandle[AdminProductImageSavedStateKeys.CAMERA_RESULT_RECEIVED] = true
        savedStateHandle[AdminProductImageSavedStateKeys.CAMERA_RESULT_SUCCESS] = saved
        processCameraResultIfReady()
    }

    fun cancelPendingCameraCapture() {
        val ready = _uiState.value as? AdminProductImageUiState.Ready ?: return
        if (ready.isSaving || ready.isPreparingCameraCapture) return
        val pendingUri = ready.pendingCameraUri ?: return
        clearCameraResultState()
        publish(ready.copy(
            pendingCameraUri = null,
            cameraLaunchUri = null,
            error = null,
            isSaved = false
        ))
        deleteCameraCaptureInBackground(pendingUri)
    }

    fun cancelSelection() {
        val ready = _uiState.value as? AdminProductImageUiState.Ready ?: return
        if (ready.isSaving || ready.isPreparingCameraCapture || ready.pendingCameraUri != null) return
        val captures = setOfNotNull(ready.selectedCameraUri)
        clearCameraResultState()
        publish(ready.copy(
            previewSourceUri = null,
            selectedCameraUri = null,
            pendingCameraUri = null,
            cameraLaunchUri = null,
            error = null,
            isSaved = false
        ))
        deleteCameraCapturesInBackground(captures)
    }

    /** Await cleanup before the editor route is popped so active camera files are not orphaned. */
    suspend fun discardCameraDraft() {
        cameraPreparationJob?.cancel()
        cameraPreparationJob?.join()
        cameraResultJob?.cancel()
        cameraResultJob?.join()

        val captures = restoredCameraUris()
        clearCameraResultState()
        (_uiState.value as? AdminProductImageUiState.Ready)?.let { ready ->
            publish(ready.copy(
                previewSourceUri = null,
                isPreparingCameraCapture = false,
                pendingCameraUri = null,
                cameraLaunchUri = null,
                selectedCameraUri = null,
                error = null,
                isSaved = false
            ))
        }
        deleteCameraCaptures(captures.toList())
    }

    fun saveImage() {
        val ready = _uiState.value as? AdminProductImageUiState.Ready ?: return
        val sourceUri = ready.previewSourceUri ?: return
        if (ready.isSaving || ready.isPreparingCameraCapture || ready.pendingCameraUri != null) return
        val cameraDraftUri = ready.selectedCameraUri?.takeIf { it == sourceUri }
        publish(ready.copy(isSaving = true, error = null))
        viewModelScope.launch {
            try {
                val imageReference = updateProductImage(productId, sourceUri)
                cameraDraftUri?.let { deleteCameraCaptures(listOf(it)) }
                clearCameraResultState()
                publish(ready.copy(
                    product = ready.product.copy(imageReference = imageReference),
                    previewSourceUri = null,
                    isSaving = false,
                    error = null,
                    isSaved = true,
                    selectedCameraUri = null,
                    pendingCameraUri = null,
                    cameraLaunchUri = null,
                    isPreparingCameraCapture = false
                ))
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                val message = failure.message ?: SAVE_ERROR
                if (cameraDraftUri != null) {
                    deleteCameraCaptures(listOf(cameraDraftUri))
                    clearCameraResultState()
                    publish(ready.copy(
                        previewSourceUri = null,
                        isSaving = false,
                        error = message,
                        isSaved = false,
                        selectedCameraUri = null,
                        pendingCameraUri = null,
                        cameraLaunchUri = null,
                        isPreparingCameraCapture = false
                    ))
                } else {
                    publish(ready.copy(isSaving = false, error = message))
                }
            }
        }
    }

    private fun processCameraResultIfReady() {
        if (isHandlingCameraResult) return
        val ready = _uiState.value as? AdminProductImageUiState.Ready ?: return
        if (savedStateHandle.get<Boolean>(AdminProductImageSavedStateKeys.CAMERA_RESULT_RECEIVED) != true) return
        val captureUri = savedStateHandle.get<String>(AdminProductImageSavedStateKeys.PENDING_CAMERA_URI)
            ?: ready.pendingCameraUri
        if (captureUri == null) {
            clearCameraResultState()
            return
        }
        val saved = savedStateHandle.get<Boolean>(AdminProductImageSavedStateKeys.CAMERA_RESULT_SUCCESS) == true
        isHandlingCameraResult = true
        publish(ready.copy(isPreparingCameraCapture = true, cameraLaunchUri = null, error = null))
        cameraResultJob = viewModelScope.launch {
            try {
                if (!saved) {
                    deleteCameraCaptures(listOf(captureUri))
                    (_uiState.value as? AdminProductImageUiState.Ready)?.takeIf {
                        it.pendingCameraUri == captureUri
                    }?.let { current ->
                        publish(current.copy(
                            isPreparingCameraCapture = false,
                            pendingCameraUri = null,
                            cameraLaunchUri = null,
                            error = null
                        ))
                    }
                } else {
                    val preview = imageStorage.loadPreview(captureUri, CAMERA_VALIDATION_DIMENSION)
                    require(preview != null && preview.isNotEmpty()) { INVALID_CAMERA_CAPTURE_ERROR }
                    val current = _uiState.value as? AdminProductImageUiState.Ready
                        ?: return@launch
                    if (current.pendingCameraUri != captureUri) {
                        deleteCameraCaptures(listOf(captureUri))
                        return@launch
                    }
                    current.selectedCameraUri?.takeIf { it != captureUri }?.let {
                        deleteCameraCaptures(listOf(it))
                    }
                    publish(current.copy(
                        previewSourceUri = captureUri,
                        selectedCameraUri = captureUri,
                        isPreparingCameraCapture = false,
                        pendingCameraUri = null,
                        cameraLaunchUri = null,
                        error = null,
                        isSaved = false
                    ))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                deleteCameraCaptures(listOf(captureUri))
                (_uiState.value as? AdminProductImageUiState.Ready)?.takeIf {
                    it.pendingCameraUri == captureUri
                }?.let { current ->
                    publish(current.copy(
                        isPreparingCameraCapture = false,
                        pendingCameraUri = null,
                        cameraLaunchUri = null,
                        error = INVALID_CAMERA_CAPTURE_ERROR
                    ))
                }
            } finally {
                clearCameraResultState()
                isHandlingCameraResult = false
            }
        }
    }

    private fun restoredReady(product: Product): AdminProductImageUiState.Ready {
        val resultReceived = savedStateHandle.get<Boolean>(AdminProductImageSavedStateKeys.CAMERA_RESULT_RECEIVED) == true
        return AdminProductImageUiState.Ready(
            product = product,
            previewSourceUri = savedStateHandle[AdminProductImageSavedStateKeys.PREVIEW_SOURCE_URI],
            isPreparingCameraCapture = resultReceived,
            pendingCameraUri = savedStateHandle[AdminProductImageSavedStateKeys.PENDING_CAMERA_URI],
            cameraLaunchUri = if (resultReceived) null else savedStateHandle[AdminProductImageSavedStateKeys.CAMERA_LAUNCH_URI],
            selectedCameraUri = savedStateHandle[AdminProductImageSavedStateKeys.SELECTED_CAMERA_URI]
        )
    }

    private fun restoredCameraUris(): Set<String> = setOfNotNull(
        savedStateHandle[AdminProductImageSavedStateKeys.PENDING_CAMERA_URI],
        savedStateHandle[AdminProductImageSavedStateKeys.SELECTED_CAMERA_URI]
    )

    private fun publish(ready: AdminProductImageUiState.Ready) {
        savedStateHandle[AdminProductImageSavedStateKeys.PREVIEW_SOURCE_URI] = ready.previewSourceUri
        savedStateHandle[AdminProductImageSavedStateKeys.PENDING_CAMERA_URI] = ready.pendingCameraUri
        savedStateHandle[AdminProductImageSavedStateKeys.CAMERA_LAUNCH_URI] = ready.cameraLaunchUri
        savedStateHandle[AdminProductImageSavedStateKeys.SELECTED_CAMERA_URI] = ready.selectedCameraUri
        _uiState.value = ready
    }

    private fun clearCameraResultState() {
        savedStateHandle.remove<Boolean>(AdminProductImageSavedStateKeys.CAMERA_RESULT_RECEIVED)
        savedStateHandle.remove<Boolean>(AdminProductImageSavedStateKeys.CAMERA_RESULT_SUCCESS)
    }

    private fun deleteCameraCaptureInBackground(uri: String) {
        pendingCameraDeletionUris += uri
        viewModelScope.launch { deleteCameraCaptures(listOf(uri)) }
    }

    private fun deleteCameraCapturesInBackground(uris: Set<String>) {
        pendingCameraDeletionUris.addAll(uris)
        viewModelScope.launch { deleteCameraCaptures(uris.toList()) }
    }

    private suspend fun deleteCameraCaptures(uris: List<String>) {
        val uniqueUris = uris.distinct()
        try {
            withContext(NonCancellable) {
                uniqueUris.forEach { uri ->
                    try {
                        imageStorage.deleteCameraCapture(uri)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // A later abandoned-file sweep will remove a capture that could not be deleted now.
                    }
                }
            }
        } finally {
            pendingCameraDeletionUris.removeAll(uniqueUris.toSet())
        }
    }

    private companion object {
        const val LOAD_ERROR = "No fue posible cargar el producto."
        const val SAVE_ERROR = "No fue posible guardar la imagen."
        const val CAMERA_PREPARATION_ERROR = "No se pudo preparar la fotografía. Elige una imagen o vuelve a intentar."
        const val CAMERA_LAUNCH_ERROR = "No se pudo abrir la cámara. Elige una imagen o vuelve a intentar."
        const val INVALID_CAMERA_CAPTURE_ERROR = "No se pudo leer la fotografía. Elige una imagen o vuelve a tomarla."
        const val CAMERA_VALIDATION_DIMENSION = 1600
    }
}
