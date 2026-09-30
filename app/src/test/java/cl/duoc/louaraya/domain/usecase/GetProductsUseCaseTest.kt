package cl.duoc.louaraya.domain.usecase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GetProductsUseCaseTest {

    @Test
    fun invoke_returnsAllProductsFromRepository() = runBlocking {
        val products = listOf(TestData.product1, TestData.product2)
        val useCase = GetProductsUseCase(FakeProductRepository(products))

        val result = useCase()

        assertEquals(products, result)
    }

    @Test
    fun invoke_returnsEmptyListWhenRepositoryIsEmpty() = runBlocking {
        val useCase = GetProductsUseCase(FakeProductRepository())

        val result = useCase()

        assertTrue(result.isEmpty())
    }
}
