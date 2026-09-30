package cl.duoc.louaraya.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class CreateCommercialRequestUseCaseTest {

    @Test
    fun invoke_storesRequestInRepositoryAndReturnsIt() = runBlocking {
        val repository = FakeCommercialRequestRepository()
        val useCase = CreateCommercialRequestUseCase(repository)

        val result = useCase(TestData.request1)

        assertEquals(TestData.request1, result)
        assertEquals(listOf(TestData.request1), repository.requests)
    }
}
