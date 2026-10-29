package cl.duoc.louaraya.presentation.local

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cl.duoc.louaraya.domain.image.QrImageDecoder
import cl.duoc.louaraya.domain.image.QrImageReadException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class QrImageState(val reading: Boolean = false, val value: String? = null, val error: String? = null)

class QrImageViewModel(private val decoder: QrImageDecoder) : ViewModel() {
    private val mutable = MutableStateFlow(QrImageState())
    val uiState = mutable.asStateFlow()
    private var job: Job? = null
    private var revision = 0

    fun selectImage(uri: String?) {
        if (uri == null) return
        job?.cancel()
        val request = ++revision
        mutable.value = QrImageState(reading = true)
        job = viewModelScope.launch {
            try {
                val result = withTimeout(30_000) { decoder.decode(uri) }
                if (request == revision) mutable.value = QrImageState(value = result)
            } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                if (request == revision) mutable.value = QrImageState(error = "La lectura tardó demasiado. Selecciona la imagen de nuevo.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (request == revision) mutable.value = QrImageState(error =
                    (error as? QrImageReadException)?.message ?: "No pudimos leer la imagen. Selecciónala de nuevo o prueba con otra foto.")
            }
        }
    }

    fun pickerUnavailable() {
        clear()
        mutable.value = QrImageState(error = "No pudimos abrir el selector de fotos. Puedes usar la cámara o ingresar el código.")
    }

    fun consumeValue() { mutable.value = mutable.value.copy(value = null) }

    fun clear() {
        revision++
        job?.cancel()
        mutable.value = QrImageState()
    }
}
