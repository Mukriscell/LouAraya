package cl.duoc.louaraya.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.usecase.GetProductsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel del catálogo. Carga los productos al crearse y expone el
 * resultado como [CatalogUiState].
 */
class CatalogViewModel(
    private val getProductsUseCase: GetProductsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        _uiState.value = CatalogUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                val products = getProductsUseCase()
                if (products.isEmpty()) {
                    CatalogUiState.Empty
                } else {
                    CatalogUiState.Success(products)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                CatalogUiState.Error(e.message ?: DEFAULT_ERROR_MESSAGE)
            }
        }
    }

    private companion object {
        const val DEFAULT_ERROR_MESSAGE = "No fue posible cargar el catálogo."
    }
}
