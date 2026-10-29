package cl.duoc.louaraya.data.repository

import android.app.Application
import androidx.room.Room
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.repository.LocalRepository
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import cl.duoc.louaraya.domain.usecase.QuotationDetails
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class StockInventoryTest {
    private lateinit var db: LouArayaDatabase
    private lateinit var repository: RoomLocalRepository
    private lateinit var cases: LocalUseCases

    @Before fun setup() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LouArayaDatabase::class.java).addCallback(DemoSeedCallback()).build()
        repository = RoomLocalRepository(db)
        cases = LocalUseCases(repository)
        cases.initialize()
        cases.signIn("admin.demo", "LouDemo2026!")
        Unit
    }
    @After fun close() { db.close() }
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("La operación debía rechazarse") } catch (_: IllegalArgumentException) { }
    }
    private suspend fun stock() = repository.products().first { it.id == 1L }.stock
    private suspend fun request(intention: RequestIntention = RequestIntention.PURCHASE): CommercialRequest {
        cases.signIn("cliente.demo", "LouDemo2026!")
        val details = if (intention == RequestIntention.QUOTATION) QuotationDetails("Evento", 40.0, 60.0, "Algodón", "Crudo", "2099-12-31") else null
        val result = cases.createRequest(1, intention, 45000, "", details)
        cases.signIn("admin.demo", "LouDemo2026!")
        return result
    }

    @Test fun entriesSalesAndWithdrawalsKeepBalancesAndAnAuditTrail() = runBlocking {
        assertEquals(0, stock())
        cases.changeStock(1, 10, StockMovementType.ENTRY, "  Conteo inicial  ")
        cases.changeStock(1, 3, StockMovementType.DIRECT_SALE, "Feria")
        cases.changeStock(1, 2, StockMovementType.WITHDRAWAL, "Piezas dañadas")
        assertEquals(5, stock())
        val movements = cases.snapshot().stockMovements
        assertEquals(listOf(5, 7, 10), movements.map { it.stockAfter })
        assertEquals(listOf(-2, -3, 10), movements.map { it.quantityDelta })
        assertTrue(movements.all { it.actorId == 2L })
        assertEquals("Conteo inicial", movements.last().note)
    }

    @Test fun insufficientStockInvalidQuantitiesAndOverflowNeverChangeTheBalance() = runBlocking {
        rejected { cases.changeStock(1, 1, StockMovementType.DIRECT_SALE) }
        for (amount in listOf(0, -1, Int.MIN_VALUE)) rejected { cases.changeStock(1, amount, StockMovementType.ENTRY) }
        cases.changeStock(1, Int.MAX_VALUE, StockMovementType.ENTRY)
        rejected { cases.changeStock(1, 1, StockMovementType.ENTRY) }
        rejected { cases.changeStock(1, 1, StockMovementType.ENTRY, "x".repeat(251)) }
        rejected { cases.changeStock(1, 1, StockMovementType.REQUEST_SALE) }
        assertEquals(Int.MAX_VALUE, stock())
        assertEquals(1, cases.snapshot().stockMovements.size)
    }

    @Test fun aSaleAffectsOnlyItsProduct() = runBlocking {
        cases.changeStock(1, 5, StockMovementType.ENTRY)
        cases.changeStock(2, 7, StockMovementType.ENTRY)
        cases.changeStock(1, 2, StockMovementType.DIRECT_SALE)
        assertEquals(3, stock())
        assertEquals(7, repository.products().first { it.id == 2L }.stock)
        rejected { cases.changeStock(99999, 1, StockMovementType.ENTRY) }
    }

    @Test fun clientsCannotChangeStockAndDoNotReceiveTheMovementHistory() = runBlocking {
        cases.changeStock(1, 4, StockMovementType.ENTRY, "Nota interna")
        cases.signIn("cliente.demo", "LouDemo2026!")
        for (type in listOf(StockMovementType.ENTRY, StockMovementType.DIRECT_SALE, StockMovementType.WITHDRAWAL)) rejected { cases.changeStock(1, 1, type) }
        assertTrue(cases.snapshot().stockMovements.isEmpty())
        cases.signOut()
        rejected { cases.changeStock(1, 1, StockMovementType.ENTRY) }
        assertEquals(4, stock())
    }

    @Test fun editingProductDetailsCannotOverwriteStockWithAStaleCopy() = runBlocking {
        val stale = cases.snapshot().products.first()
        cases.changeStock(1, 8, StockMovementType.ENTRY)
        cases.saveProduct(stale.copy(name = "Nombre actualizado", stock = 999))
        assertEquals(8, stock())
        val id = cases.saveProduct(stale.copy(id = 0, name = "Pieza nueva", stock = 999))
        assertEquals(0, repository.products().first { it.id == id }.stock)
    }

    @Test fun deliveryDeductsOnceEvenAfterRepeatingOrReopeningItsStatus() = runBlocking {
        cases.changeStock(1, 5, StockMovementType.ENTRY)
        val request = request()
        cases.updateStatus(request.id, RequestStatus.CONFIRMED)
        assertEquals(5, stock())
        cases.updateStatus(request.id, RequestStatus.DELIVERED)
        cases.updateStatus(request.id, RequestStatus.DELIVERED)
        cases.updateStatus(request.id, RequestStatus.PENDING)
        cases.updateStatus(request.id, RequestStatus.DELIVERED)
        assertEquals(4, stock())
        assertEquals(1, cases.snapshot().stockMovements.count { it.requestId == request.id })
        assertEquals(RequestStatus.DELIVERED, repository.request(request.id)!!.status)
    }

    @Test fun deliveryWithoutStockPreservesStatusAndDoesNotSendAStatusNotice() = runBlocking {
        val request = request(RequestIntention.RESERVATION)
        cases.updateStatus(request.id, RequestStatus.CONFIRMED)
        val before = repository.notices(request.ownerId)
        rejected { cases.updateStatus(request.id, RequestStatus.DELIVERED) }
        assertEquals(RequestStatus.CONFIRMED, repository.request(request.id)!!.status)
        assertEquals(before, repository.notices(request.ownerId))
        assertTrue(cases.snapshot().stockMovements.isEmpty())
        cases.changeStock(1, 1, StockMovementType.ENTRY)
        cases.updateStatus(request.id, RequestStatus.DELIVERED)
        assertEquals(0, stock())
    }

    @Test fun customizedQuotationDoesNotConsumeItsReferenceProduct() = runBlocking {
        val request = request(RequestIntention.QUOTATION)
        cases.updateStatus(request.id, RequestStatus.DELIVERED)
        assertEquals(0, stock())
        assertFalse(repository.hasStockMovementForRequest(request.id))
    }

    @Test fun aFailedMovementWriteRollsBackTheStockUpdate() = runBlocking {
        val broken = LocalUseCases(object : LocalRepository by repository {
            override suspend fun addStockMovement(movement: StockMovement) { error("Fallo simulado") }
        })
        try { broken.changeStock(1, 8, StockMovementType.ENTRY); fail("La escritura debía fallar") } catch (_: IllegalStateException) { }
        assertEquals(0, stock())
        assertTrue(repository.stockMovements().isEmpty())
    }

    @Test fun aFailedDeliveryNoticeRollsBackStockMovementAndStatusTogether() = runBlocking {
        cases.changeStock(1, 3, StockMovementType.ENTRY)
        val request = request()
        val broken = LocalUseCases(object : LocalRepository by repository {
            override suspend fun addNotice(notice: LocalNotice) { error("Fallo simulado") }
        })
        try { broken.updateStatus(request.id, RequestStatus.DELIVERED); fail("La escritura debía fallar") } catch (_: IllegalStateException) { }
        assertEquals(3, stock())
        assertEquals(RequestStatus.PENDING, repository.request(request.id)!!.status)
        assertFalse(repository.hasStockMovementForRequest(request.id))
    }

    @Test fun concurrentSalesCannotOversellTheLastUnit() = runBlocking {
        cases.changeStock(1, 1, StockMovementType.ENTRY)
        val results = coroutineScope {
            (1..2).map { async(Dispatchers.IO) { runCatching { cases.changeStock(1, 1, StockMovementType.DIRECT_SALE) }.isSuccess } }.awaitAll()
        }
        assertEquals(1, results.count { it })
        assertEquals(0, stock())
        assertEquals(1, repository.stockMovements().count { it.type == StockMovementType.DIRECT_SALE })
    }

    @Test fun concurrentDeliveryAttemptsDeductOnlyOneUnit() = runBlocking {
        cases.changeStock(1, 3, StockMovementType.ENTRY)
        val request = request()
        coroutineScope { (1..2).map { async(Dispatchers.IO) { cases.updateStatus(request.id, RequestStatus.DELIVERED) } }.awaitAll() }
        assertEquals(2, stock())
        assertEquals(1, repository.stockMovements().count { it.requestId == request.id })
    }
}
