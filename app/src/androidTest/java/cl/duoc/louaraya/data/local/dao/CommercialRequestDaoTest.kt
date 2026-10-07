package cl.duoc.louaraya.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.entity.CommercialRequestEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommercialRequestDaoTest {
    private lateinit var database: LouArayaDatabase
    private lateinit var dao: CommercialRequestDao
    private val request = CommercialRequestEntity(
        productId = 1, date = 123L, intention = "RESERVATION", anonymizedClient = "CLIENTE-DEMO-001",
        comment = "Evento ficticio", total = 45000, status = "PENDING", campaignCategory = "WEDDINGS"
    )

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            LouArayaDatabase::class.java).build()
        dao = database.commercialRequestDao()
    }
    @After fun tearDown() { database.close() }

    @Test fun generatedIdsAreUniqueAndAllFieldsAreStored() = runBlocking {
        val first = dao.insert(request)
        val second = dao.insert(request.copy(comment = null, campaignCategory = null))
        assertTrue(first > 0)
        assertNotEquals(first, second)
        assertEquals(request.copy(id = first), dao.getById(first))
        assertNull(dao.getById(second)?.comment)
        assertNull(dao.getById(second)?.campaignCategory)
    }

    @Test fun requestsAreOrderedByDateThenIdDescending() = runBlocking {
        val oldest = dao.insert(request.copy(date = 1))
        val newer = dao.insert(request.copy(date = 2))
        val tied = dao.insert(request.copy(date = 2))
        assertEquals(listOf(tied, newer, oldest), dao.getAll().map { it.id })
    }

    @Test fun missingRequestReturnsNull() = runBlocking { assertNull(dao.getById(99)) }

    @Test fun updatePreservesRequestDataAndSupportsAllCaseStates() = runBlocking {
        val id = dao.insert(request)
        for (status in listOf("EXPLORATION", "PENDING", "CONFIRMED", "DELIVERED")) {
            assertEquals(request.copy(id = id, status = status), dao.updateStatusAndGet(id, status))
        }
    }

    @Test fun updatingMissingRequestFailsWithoutCreatingOne() = runBlocking {
        try {
            dao.updateStatusAndGet(99, "CONFIRMED")
            fail("Debe informar que no existe")
        } catch (_: NoSuchElementException) {
            assertTrue(dao.getAll().isEmpty())
        }
    }
}
