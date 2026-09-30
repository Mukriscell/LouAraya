package cl.duoc.louaraya.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetCommercialRequestsUseCaseTest {

    @Test
    fun invoke_returnsAllRequestsFromRepository() = runBlocking {
        val requests = listOf(TestData.request1, TestData.request2)
        val useCase = GetCommercialRequestsUseCase(FakeCommercialRequestRepository(requests))

        val result = useCase()

        assertEquals(requests, result)
    }

    @Test
    fun invoke_returnsEmptyListWhenRepositoryIsEmpty() = runBlocking {
        val useCase = GetCommercialRequestsUseCase(FakeCommercialRequestRepository())

        val result = useCase()

        assertTrue(result.isEmpty())
    }
}
