package cl.duoc.louaraya.presentation.local

import cl.duoc.louaraya.data.image.singleQrValue
import cl.duoc.louaraya.domain.image.QrImageDecoder
import cl.duoc.louaraya.domain.image.QrImageReadException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QrImageViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { Dispatchers.resetMain() }

    @Test fun cancellingThePickerDoesNotStartReadingOrEraseAPreviousResult() = runTest(dispatcher) {
        var calls = 0
        val vm = QrImageViewModel(QrImageDecoder { calls++; "louaraya://product/1" })
        vm.selectImage("content://first")
        advanceUntilIdle()
        vm.selectImage(null)
        assertEquals("louaraya://product/1", vm.uiState.value.value)
        assertEquals(1, calls)
    }

    @Test fun failedReadCanBeRetriedWithoutKeepingTheError() = runTest(dispatcher) {
        var fail = true
        val vm = QrImageViewModel(QrImageDecoder {
            if (fail) throw QrImageReadException("No encontramos un QR")
            "louaraya://product/1"
        })
        vm.selectImage("content://blank")
        advanceUntilIdle()
        assertEquals("No encontramos un QR", vm.uiState.value.error)
        assertFalse(vm.uiState.value.reading)
        fail = false
        vm.selectImage("content://qr")
        assertTrue(vm.uiState.value.reading)
        advanceUntilIdle()
        assertNull(vm.uiState.value.error)
        assertEquals("louaraya://product/1", vm.uiState.value.value)
        vm.consumeValue()
        assertNull(vm.uiState.value.value)
    }

    @Test fun lostImagePermissionProducesARecoverableMessage() = runTest(dispatcher) {
        val vm = QrImageViewModel(QrImageDecoder { throw SecurityException("private URI details") })
        vm.selectImage("content://expired")
        advanceUntilIdle()
        assertEquals("No pudimos leer la imagen. Selecciónala de nuevo o prueba con otra foto.", vm.uiState.value.error)
        assertFalse(vm.uiState.value.reading)
    }

    @Test fun clearingACancelledReadCannotPublishALateResult() = runTest(dispatcher) {
        val value = CompletableDeferred<String>()
        val vm = QrImageViewModel(QrImageDecoder { value.await() })
        vm.selectImage("content://slow")
        runCurrent()
        vm.clear()
        value.complete("louaraya://product/1")
        advanceUntilIdle()
        assertEquals(QrImageState(), vm.uiState.value)
    }

    @Test fun aNewSelectionSupersedesThePreviousRead() = runTest(dispatcher) {
        val vm = QrImageViewModel(QrImageDecoder { uri ->
            if (uri == "content://slow") awaitCancellation()
            "louaraya://product/2"
        })
        vm.selectImage("content://slow")
        runCurrent()
        vm.selectImage("content://new")
        advanceUntilIdle()
        assertEquals("louaraya://product/2", vm.uiState.value.value)
    }

    @Test fun timeoutAllowsTheUserToChooseAgain() = runTest(dispatcher) {
        val vm = QrImageViewModel(QrImageDecoder { awaitCancellation() })
        vm.selectImage("content://slow")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.reading)
        assertEquals("La lectura tardó demasiado. Selecciona la imagen de nuevo.", vm.uiState.value.error)
    }

    @Test fun unavailablePickerDoesNotLeaveTheScreenBusy() {
        val vm = QrImageViewModel(QrImageDecoder { "unused" })
        vm.pickerUnavailable()
        assertFalse(vm.uiState.value.reading)
        assertNotNull(vm.uiState.value.error)
    }

    @Test fun identicalQrValuesAreResolvedAsOneDestination() {
        assertEquals("louaraya://product/1", singleQrValue(listOf("", "louaraya://product/1", "louaraya://product/1")))
    }

    @Test(expected = QrImageReadException::class)
    fun anImageWithoutQrDoesNotProduceADestination() { singleQrValue(emptyList()) }

    @Test(expected = QrImageReadException::class)
    fun multipleDestinationsRequireAnUnambiguousImage() {
        singleQrValue(listOf("louaraya://product/1", "louaraya://product/2"))
    }
}
