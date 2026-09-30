package cl.duoc.louaraya.presentation.productdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del detalle de producto. Carga el producto [productId] al crearse
 * y expone el resultado como [ProductDetailUiState].
 */
class ProductDetailViewModel(
    private val productId: Long,
    private val getProductByIdUseCase: GetProductByIdUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProductDetailUiState>(ProductDetailUiState.Loading)
    val uiState: StateFlow<ProductDetailUiState> = _uiState.asStateFlow()

    init {
        loadProduct()
    }

    fun loadProduct() {
        _uiState.value = ProductDetailUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val product = getProductByIdUseCase(productId)
                if (product == null) {
                    ProductDetailUiState.NotFound
                } else {
                    ProductDetailUiState.Success(product)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ProductDetailUiState.Error(e.message ?: DEFAULT_ERROR_MESSAGE)
            }
        }
    }

    private companion object {
        const val DEFAULT_ERROR_MESSAGE = "No fue posible cargar el producto."
    }
}
