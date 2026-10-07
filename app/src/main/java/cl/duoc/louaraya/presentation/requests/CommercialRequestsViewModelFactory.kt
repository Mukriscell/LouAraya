package cl.duoc.louaraya.presentation.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import cl.duoc.louaraya.domain.usecase.GetCommercialRequestsUseCase

class CommercialRequestsViewModelFactory(private val getRequests: GetCommercialRequestsUseCase) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(CommercialRequestsViewModel::class.java))
        return CommercialRequestsViewModel(getRequests) as T
    }
}
