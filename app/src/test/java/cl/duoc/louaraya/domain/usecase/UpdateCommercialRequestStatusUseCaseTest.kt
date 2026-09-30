package cl.duoc.louaraya.domain.usecase

import cl.duoc.louaraya.domain.model.RequestStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCommercialRequestStatusUseCaseTest {

    @Test
    fun invoke_updatesStatusInRepositoryAndReturnsUpdatedRequest() = runBlocking {
        val repository = FakeCommercialRequestRepository(
            listOf(TestData.request1, TestData.request2)
        )
        val useCase = UpdateCommercialRequestStatusUseCase(repository)

        val result = useCase(TestData.request1.id, RequestStatus.CONFIRMED)

        val expected = TestData.request1.copy(status = RequestStatus.CONFIRMED)
        assertEquals(expected, result)
        assertEquals(expected, repository.getRequestById(TestData.request1.id))
        assertEquals(TestData.request2, repository.getRequestById(TestData.request2.id))
    }
}
