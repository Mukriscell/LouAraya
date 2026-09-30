package cl.duoc.louaraya.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetProductByIdUseCaseTest {

    private val repository = FakeProductRepository(
        listOf(TestData.product1, TestData.product2)
    )
    private val useCase = GetProductByIdUseCase(repository)

    @Test
    fun invoke_returnsProductWhenIdExists() = runBlocking {
        val result = useCase(2L)

        assertEquals(TestData.product2, result)
    }

    @Test
    fun invoke_returnsNullWhenIdDoesNotExist() = runBlocking {
        val result = useCase(99L)

        assertNull(result)
    }
}
