package cl.duoc.louaraya.data.repository

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.migration.MIGRATION_9_10
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.usecase.FormValidation
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import cl.duoc.louaraya.domain.usecase.QuotationDetails
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RequestQuantityCancellationTest {
    private lateinit var db: LouArayaDatabase
    private lateinit var repo: RoomLocalRepository
    private lateinit var cases: LocalUseCases
    @Before fun start() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), LouArayaDatabase::class.java).addCallback(DemoSeedCallback()).build()
        repo = RoomLocalRepository(db); cases = LocalUseCases(repo); cases.initialize()
        cases.signIn("cliente.demo", "LouDemo2026!")
        Unit
    }
    @After fun stop() { db.close() }
    private suspend fun request(quantity: Int, kind: RequestIntention = RequestIntention.PURCHASE) =
        cases.createRequest(1, kind, cases.snapshot().products.first { it.id == 1L }.price, "Prueba de cantidades", null, quantity = quantity)
    private suspend fun admin() { cases.signIn("admin.demo", "LouDemo2026!") }
    private suspend fun client() { cases.signIn("cliente.demo", "LouDemo2026!") }
    private suspend fun reject(block: suspend () -> Unit) {
        try { block(); fail("La operación debe rechazarse") } catch (_: IllegalArgumentException) { }
    }

    @Test fun quantityAndTrustedUnitPricePersistAndTotalDoesNotFollowLaterPriceChanges() = runBlocking {
        val created = request(3)
        assertEquals(3, created.quantity)
        assertEquals(45000L, created.unitPrice)
        assertEquals(135000L, created.total)
        assertEquals(created, repo.request(created.id))
        admin(); val product = cases.snapshot().products.first { it.id == 1L }
        cases.saveProduct(product.copy(price = 50000))
        assertEquals(135000L, repo.request(created.id)!!.total)
        assertEquals(45000L, repo.request(created.id)!!.unitPrice)
    }

    @Test fun invalidQuantitiesAndOverflowDoNotWriteRequestsNoticesOrTrace() = runBlocking {
        for (quantity in listOf(0, -1, Int.MIN_VALUE)) reject { request(quantity) }
        assertTrue(cases.snapshot().requests.isEmpty())
        assertTrue(cases.snapshot().explorationEvents.isEmpty())
        admin(); assertTrue(cases.snapshot().notices.isEmpty())
        val product = cases.snapshot().products.first { it.id == 1L }
        cases.saveProduct(product.copy(price = Long.MAX_VALUE))
        client(); reject { request(2) }
        assertTrue(cases.snapshot().requests.isEmpty())
        listOf("", "0", "-1", "1.5", "1,5", "2147483648").forEach { raw -> reject { FormValidation.stockQuantity(raw) } }
    }

    @Test fun deliveryConsumesTheWholeQuantityAtomicallyAndOnlyOnce() = runBlocking {
        val created = request(3, RequestIntention.RESERVATION)
        admin(); cases.changeStock(1, 2, StockMovementType.ENTRY)
        reject { cases.updateStatus(created.id, RequestStatus.DELIVERED) }
        assertEquals(RequestStatus.PENDING, repo.request(created.id)!!.status)
        assertEquals(2, cases.snapshot().products.first { it.id == 1L }.stock)
        assertFalse(repo.hasStockMovementForRequest(created.id))
        cases.changeStock(1, 2, StockMovementType.ENTRY)
        cases.updateStatus(created.id, RequestStatus.DELIVERED)
        assertEquals(1, cases.snapshot().products.first { it.id == 1L }.stock)
        assertEquals(-3, cases.snapshot().stockMovements.single { it.requestId == created.id }.quantityDelta)
        cases.updateStatus(created.id, RequestStatus.CONFIRMED)
        cases.updateStatus(created.id, RequestStatus.DELIVERED)
        assertEquals(1, cases.snapshot().products.first { it.id == 1L }.stock)
        assertEquals(1, cases.snapshot().stockMovements.count { it.requestId == created.id })
    }

    @Test fun ownerCanCancelPendingAndConfirmedRequestsWithoutChangingStockOrLosingHistory() = runBlocking {
        val pending = request(2)
        val confirmed = request(3)
        admin(); cases.changeStock(1, 7, StockMovementType.ENTRY)
        cases.updateStatus(confirmed.id, RequestStatus.CONFIRMED)
        val before = cases.snapshot()
        client()
        cases.cancelRequest(pending.id, 1); cases.cancelRequest(confirmed.id, 1)
        cases.cancelRequest(pending.id, 1)
        assertTrue(cases.snapshot().requests.all { it.status == RequestStatus.CANCELLED })
        assertEquals(2, cases.snapshot().explorationEvents.count { it.kind == ExplorationKind.REQUEST_CANCELLED })
        admin()
        assertEquals(before.products, cases.snapshot().products)
        assertEquals(before.stockMovements, cases.snapshot().stockMovements)
        assertEquals(2, cases.snapshot().notices.count { it.eventKey.startsWith("cancel/") })
        reject { cases.updateStatus(pending.id, RequestStatus.PENDING) }
        reject { cases.updateStatus(pending.id, RequestStatus.DELIVERED) }
        reject { cases.agreeTotal(pending.id, 10000) }
    }

    @Test fun cancellationChecksOwnerSessionRoleAndDeliveryHistory() = runBlocking {
        val created = request(1)
        cases.register("Otro cliente", "segundo.cliente", "Abcdef1!", 0, "")
        reject { cases.cancelRequest(created.id, cases.profile()!!.id) }
        client(); reject { cases.cancelRequest(created.id, 2) }
        reject { cases.clearRequests(listOf(created.id), 1) }
        admin(); reject { cases.cancelRequest(created.id, 2) }
        cases.changeStock(1, 1, StockMovementType.ENTRY)
        cases.updateStatus(created.id, RequestStatus.DELIVERED)
        client(); reject { cases.cancelRequest(created.id, 1) }
        admin(); cases.updateStatus(created.id, RequestStatus.PENDING)
        client(); reject { cases.cancelRequest(created.id, 1) }
        assertEquals(RequestStatus.PENDING, repo.request(created.id)!!.status)
    }

    @Test fun aCustomQuotationKeepsItsQuantityWithoutConsumingTheReferenceProductsStock() = runBlocking {
        val created = cases.createRequest(1, RequestIntention.QUOTATION, 45000, "Cinco piezas a medida",
            QuotationDetails("Evento", 40.0, 60.0, "Algodón", "Crudo", "2099-01-01"), quantity = 5)
        assertEquals(5, created.quantity); assertEquals(225000L, created.total)
        admin(); cases.changeStock(1, 3, StockMovementType.ENTRY)
        cases.updateStatus(created.id, RequestStatus.DELIVERED)
        assertEquals(3, cases.snapshot().products.first { it.id == 1L }.stock)
        assertFalse(repo.hasStockMovementForRequest(created.id))
    }

    @Test fun migrationRestoresOldClientRequestsAndKeepsTheirPriceQuantityStockAndOtherDismissals() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "quantity-v9-migration.db"
        context.deleteDatabase(name)
        try {
            var requestId = 0L
            Room.databaseBuilder(context, LouArayaDatabase::class.java, name).addCallback(DemoSeedCallback()).build().let { old ->
                try {
                    val repository = RoomLocalRepository(old); val use = LocalUseCases(repository)
                    use.initialize(); use.signIn("cliente.demo", "LouDemo2026!")
                    requestId = use.createRequest(1, RequestIntention.PURCHASE, 45000, "Anterior", null).id
                    repository.dismissRequests(1, listOf(requestId)); repository.dismissRequests(2, listOf(requestId))
                    use.signIn("admin.demo", "LouDemo2026!"); use.changeStock(1, 9, StockMovementType.ENTRY)
                    val notices = use.snapshot().notices
                    use.clearNotices(notices.map { it.id }, 2)
                } finally { old.close() }
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("ALTER TABLE commercial_requests DROP COLUMN quantity"); it.version = 9
            }
            repeat(2) { pass ->
                val upgraded = Room.databaseBuilder(context, LouArayaDatabase::class.java, name).addMigrations(MIGRATION_9_10).build()
                try {
                    val repository = RoomLocalRepository(upgraded); val use = LocalUseCases(repository)
                    assertEquals(2L, use.profile()!!.id)
                    assertEquals(setOf(requestId), use.snapshot().hiddenRequestIds)
                    assertTrue(use.snapshot().notices.isEmpty())
                    assertEquals(9, use.snapshot().products.first { it.id == 1L }.stock)
                    val saved = repository.request(requestId)!!
                    assertEquals(1, saved.quantity); assertEquals(45000L, saved.unitPrice); assertEquals(45000L, saved.total)
                    use.signIn("cliente.demo", "LouDemo2026!")
                    assertTrue(use.snapshot().hiddenRequestIds.isEmpty())
                    assertEquals(listOf(requestId), use.snapshot().requests.map { it.id })
                    assertTrue(repository.hiddenRequestIds(1).isEmpty())
                    use.signIn("admin.demo", "LouDemo2026!")
                } finally { upgraded.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
