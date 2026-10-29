package cl.duoc.louaraya.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.duoc.louaraya.domain.usecase.GetProductsUseCase





class CatalogViewModelFactory(
    private val getProductsUseCase: GetProductsUseCase
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CatalogViewModel::class.java)) {
            "ViewModel desconocido: ${modelClass.name}"
        }
        return CatalogViewModel(getProductsUseCase) as T
    }
}
