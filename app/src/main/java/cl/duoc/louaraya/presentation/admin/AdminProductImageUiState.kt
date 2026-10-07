package cl.duoc.louaraya.presentation.admin

import cl.duoc.louaraya.domain.model.Product

sealed interface AdminProductImageUiState {
    data object Loading : AdminProductImageUiState
    data class Error(val message: String) : AdminProductImageUiState
    data class Ready(
        val product: Product,
        val previewSourceUri: String? = null,
        val isSaving: Boolean = false,
        val error: String? = null,
        val isSaved: Boolean = false,
        val isPreparingCameraCapture: Boolean = false,
        val pendingCameraUri: String? = null,
        val cameraLaunchUri: String? = null,
        val selectedCameraUri: String? = null
    ) : AdminProductImageUiState
}

internal object AdminProductImageSavedStateKeys {
    const val PREVIEW_SOURCE_URI = "admin_image.preview_source_uri"
    const val PENDING_CAMERA_URI = "admin_image.pending_camera_uri"
    const val CAMERA_LAUNCH_URI = "admin_image.camera_launch_uri"
    const val SELECTED_CAMERA_URI = "admin_image.selected_camera_uri"
    const val CAMERA_RESULT_RECEIVED = "admin_image.camera_result_received"
    const val CAMERA_RESULT_SUCCESS = "admin_image.camera_result_success"
}
