package cl.duoc.louaraya.presentation.createrequest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.model.CreateCommercialRequestInput
import cl.duoc.louaraya.domain.model.RequestIntention
import cl.duoc.louaraya.domain.usecase.CommercialRequestProductPriceChangedException
import cl.duoc.louaraya.domain.usecase.CommercialRequestProductUnavailableException
import cl.duoc.louaraya.domain.usecase.CreateCommercialRequestUseCase
import cl.duoc.louaraya.domain.usecase.GetProductByIdUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreateCommercialRequestViewModel(
    private val productId: Long,
    private val getProductById: GetProductByIdUseCase,
    private val createRequest: CreateCommercialRequestUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow<CreateCommercialRequestUiState>(CreateCommercialRequestUiState.Loading)
    val uiState = _uiState.asStateFlow()

    init { loadProduct() }

    fun loadProduct() {
        if (_uiState.value is CreateCommercialRequestUiState.Saved ||
            (_uiState.value as? CreateCommercialRequestUiState.Form)?.isSaving == true) return
        _uiState.value = CreateCommercialRequestUiState.Loading
        viewModelScope.launch {
            _uiState.value = try {
                getProductById(productId)?.let { CreateCommercialRequestUiState.Form(it) }
                    ?: CreateCommercialRequestUiState.NotFound
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                CreateCommercialRequestUiState.Error("No fue posible cargar la pieza. Inténtalo nuevamente.")
            }
        }
    }

    fun setIntention(value: RequestIntention) = editForm { copy(intention = value, error = null) }
    fun setComment(value: String) = editForm { copy(comment = value, error = null) }

    private fun editForm(update: CreateCommercialRequestUiState.Form.() -> CreateCommercialRequestUiState.Form) {
        val form = _uiState.value as? CreateCommercialRequestUiState.Form ?: return
        if (!form.isSaving) _uiState.value = form.update()
    }

    fun submit() {
        val form = _uiState.value as? CreateCommercialRequestUiState.Form ?: return
        if (form.isSaving) return
        // Cambia antes de lanzar la coroutine para bloquear dos pulsaciones consecutivas.
        _uiState.value = form.copy(isSaving = true, error = null)
        viewModelScope.launch {
            try {
                val request = createRequest(
                    CreateCommercialRequestInput(
                        productId = productId,
                        intention = form.intention,
                        displayedTotal = form.product.price,
                        comment = form.comment
                    )
                )
                _uiState.value = CreateCommercialRequestUiState.Saved(request)
            } catch (_: CommercialRequestProductUnavailableException) {
                _uiState.value = CreateCommercialRequestUiState.NotFound
            } catch (e: CommercialRequestProductPriceChangedException) {
                _uiState.value = form.copy(
                    product = form.product.copy(price = e.currentPrice),
                    error = "El total de referencia cambió. Revisa el nuevo importe antes de registrar la intención."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: IllegalArgumentException) {
                _uiState.value = form.copy(error = e.message ?: "Revisa los datos de la solicitud.")
            } catch (_: Exception) {
                _uiState.value = form.copy(error = "No fue posible guardar la solicitud. Inténtalo nuevamente.")
            }
        }
    }
}
