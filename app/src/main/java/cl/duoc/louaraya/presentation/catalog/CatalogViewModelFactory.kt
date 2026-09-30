package cl.duoc.louaraya.presentation.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.duoc.louaraya.domain.usecase.GetProductsUseCase

/**
 * Crea [CatalogViewModel] con sus dependencias mientras no se use un
 * framework de inyección de dependencias.
 */
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
