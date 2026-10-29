package cl.duoc.louaraya.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.model.CampaignCategory
import cl.duoc.louaraya.domain.model.Product
import cl.duoc.louaraya.domain.usecase.GetProductsUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch





class CatalogViewModel(
    private val getProductsUseCase: GetProductsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val uiState: StateFlow<CatalogUiState> = _uiState.asStateFlow()
    private val _selectedCategory = MutableStateFlow<CampaignCategory?>(null)
    val selectedCategory: StateFlow<CampaignCategory?> = _selectedCategory.asStateFlow()
    private var allProducts: List<Product>? = null
    private var loadJob: Job? = null

    init {
        loadProducts()
    }

    fun loadProducts() {
        loadJob?.cancel()
        allProducts = null
        _uiState.value = CatalogUiState.Loading
        loadJob = viewModelScope.launch {
            try {
                allProducts = getProductsUseCase()
                publishFilteredProducts()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = CatalogUiState.Error(e.message ?: DEFAULT_ERROR_MESSAGE)
            }
        }
    }

    fun selectCategory(category: CampaignCategory?) {
        _selectedCategory.value = category
        publishFilteredProducts()
    }

    private fun publishFilteredProducts() {
        val products = allProducts ?: return
        val category = _selectedCategory.value
        val visibleProducts = if (category == null) {
            products
        } else {
            products.filter { it.campaignCategory == category }
        }
        _uiState.value = if (visibleProducts.isEmpty()) {
            CatalogUiState.Empty
        } else {
            CatalogUiState.Success(visibleProducts)
        }
    }

    private companion object {
        const val DEFAULT_ERROR_MESSAGE = "No fue posible cargar el catálogo."
    }
}
