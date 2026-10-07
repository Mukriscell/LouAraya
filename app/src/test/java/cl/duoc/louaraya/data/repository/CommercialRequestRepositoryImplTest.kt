package cl.duoc.louaraya.data.repository

import cl.duoc.louaraya.data.local.dao.CommercialRequestDao
import cl.duoc.louaraya.data.local.entity.CommercialRequestEntity
import cl.duoc.louaraya.data.mapper.toEntity
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.usecase.TestData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommercialRequestRepositoryImplTest {
    private val dao = FakeDao()
    private val repository = CommercialRequestRepositoryImpl(dao)

    @Test
    fun createsDistinctIdsAndCanReadBothRequests() = runBlocking {
        val newRequest = TestData.request1.copy(id = 0)
        val first = repository.createRequest(newRequest)
        val second = repository.createRequest(newRequest)
        assertNotEquals(first.id, second.id)
        assertEquals(first, repository.getRequestById(first.id))
        assertEquals(listOf(second, first), repository.getRequests())
        assertNull(repository.getRequestById(99))
    }

    @Test
    fun rejectsAssignedIdWithoutOverwritingExistingRequest() = runBlocking {
        dao.insert(TestData.request1.toEntity())
        try {
            repository.createRequest(TestData.request1.copy(total = 1))
            fail("No debe aceptar un ID ya asignado")
        } catch (_: IllegalArgumentException) {
            assertEquals(TestData.request1, repository.getRequestById(1))
        }
    }

    @Test
    fun updatePreservesAllOtherFields() = runBlocking {
        dao.insert(TestData.request1.toEntity())
        assertEquals(TestData.request1.copy(status = RequestStatus.CONFIRMED),
            repository.updateRequestStatus(1, RequestStatus.CONFIRMED))
    }

    @Test(expected = NoSuchElementException::class)
    fun updateMissingRequestFailsExplicitly() = runBlocking {
        repository.updateRequestStatus(99, RequestStatus.DELIVERED)
        Unit
    }

    @Test(expected = IllegalStateException::class)
    fun insertionFailureIsPropagated() = runBlocking {
        val failing = object : CommercialRequestDao by dao {
            override suspend fun insert(request: CommercialRequestEntity): Long = error("Sin espacio")
        }
        CommercialRequestRepositoryImpl(failing).createRequest(TestData.request1.copy(id = 0))
        Unit
    }

    private class FakeDao : CommercialRequestDao {
        private val rows = mutableMapOf<Long, CommercialRequestEntity>()
        override suspend fun getAll() = rows.values.sortedWith(
            compareByDescending<CommercialRequestEntity> { it.date }.thenByDescending { it.id })
        override suspend fun getById(id: Long) = rows[id]
        override suspend fun insert(request: CommercialRequestEntity): Long {
            val id = if (request.id == 0L) (rows.keys.maxOrNull() ?: 0) + 1 else request.id
            rows[id] = request.copy(id = id)
            return id
        }
        override suspend fun updateStatus(id: Long, status: String): Int {
            val row = rows[id] ?: return 0
            rows[id] = row.copy(status = status)
            return 1
        }
    }
}
