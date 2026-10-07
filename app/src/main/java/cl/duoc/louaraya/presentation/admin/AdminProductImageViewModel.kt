package cl.duoc.louaraya.presentation.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import cl.duoc.louaraya.domain.usecase.UpdateProductImageUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AdminProductImageViewModel(
    private val productId: Long,
    private val getProductById: GetProductByIdUseCase,
    private val updateProductImage: UpdateProductImageUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow<AdminProductImageUiState>(AdminProductImageUiState.Loading)
    val uiState: StateFlow<AdminProductImageUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null

    init { loadProduct() }

    fun loadProduct() {
        loadJob?.cancel()
        _uiState.value = AdminProductImageUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                val product = getProductById(productId)
                _uiState.value = product?.let { AdminProductImageUiState.Ready(it) }
                    ?: AdminProductImageUiState.Error("El producto ya no está disponible.")
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
        if (!ready.isSaving) _uiState.value = ready.copy(
            previewSourceUri = sourceUri,
            error = null,
            isSaved = false
        )
    }

    fun cancelSelection() {
        val ready = _uiState.value as? AdminProductImageUiState.Ready ?: return
        if (!ready.isSaving) _uiState.value = ready.copy(previewSourceUri = null, error = null, isSaved = false)
    }

    fun saveImage() {
        val ready = _uiState.value as? AdminProductImageUiState.Ready ?: return
        val sourceUri = ready.previewSourceUri ?: return
        if (ready.isSaving) return
        _uiState.value = ready.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val imageReference = updateProductImage(productId, sourceUri)
                _uiState.value = ready.copy(
                    product = ready.product.copy(imageReference = imageReference),
                    previewSourceUri = null,
                    isSaved = true
                )
            } catch (failure: CancellationException) {
                throw failure
            } catch (failure: Exception) {
                _uiState.value = ready.copy(error = failure.message ?: SAVE_ERROR)
            }
        }
    }

    private companion object {
        const val LOAD_ERROR = "No fue posible cargar el producto."
        const val SAVE_ERROR = "No fue posible guardar la imagen."
    }
}
