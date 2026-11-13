package cl.duoc.louaraya.data.repository

import cl.duoc.louaraya.data.local.dao.CommercialRequestDao
import cl.duoc.louaraya.data.mapper.toEntity
import cl.duoc.louaraya.domain.usecase.TestData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest

class CommercialRequestRepositorySpec : FunSpec({
    test("conserva cantidad y total al insertar y consultar una solicitud") {
        runTest {
            val dao = mockk<CommercialRequestDao>()
            val repository = CommercialRequestRepositoryImpl(dao)
            val request = TestData.request1.copy(id = 0, quantity = 3, total = 135_000)
            val saved = request.copy(id = 42)
            coEvery { dao.insert(request.toEntity()) } returns saved.id
            coEvery { dao.getById(saved.id) } returns saved.toEntity()

            repository.createRequest(request) shouldBe saved
            repository.getRequestById(saved.id) shouldBe saved
            coVerify(exactly = 1) { dao.insert(request.toEntity()) }
            coVerify(exactly = 1) { dao.getById(saved.id) }
        }
    }
})
