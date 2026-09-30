package cl.duoc.louaraya.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetCommercialRequestByIdUseCaseTest {

    private val repository = FakeCommercialRequestRepository(
        listOf(TestData.request1, TestData.request2)
    )
    private val useCase = GetCommercialRequestByIdUseCase(repository)

    @Test
    fun invoke_returnsRequestWhenIdExists() = runBlocking {
        val result = useCase(1L)

        assertEquals(TestData.request1, result)
    }

    @Test
    fun invoke_returnsNullWhenIdDoesNotExist() = runBlocking {
        val result = useCase(99L)

        assertNull(result)
    }
}
