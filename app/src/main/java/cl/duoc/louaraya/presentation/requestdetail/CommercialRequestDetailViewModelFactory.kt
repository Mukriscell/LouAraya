package cl.duoc.louaraya.presentation.requestdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestByIdUseCase
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase

class CommercialRequestDetailViewModelFactory(
    private val requestId: Long,
    private val getRequestById: GetCommercialRequestByIdUseCase,
    private val getProductById: GetProductByIdUseCase
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CommercialRequestDetailViewModel::class.java))
        return CommercialRequestDetailViewModel(requestId, getRequestById, getProductById) as T
    }
}
