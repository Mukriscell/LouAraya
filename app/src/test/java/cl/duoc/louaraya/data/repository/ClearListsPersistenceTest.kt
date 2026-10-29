package cl.duoc.louaraya.data.repository

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.migration.MIGRATION_8_9
import cl.duoc.louaraya.data.local.migration.MIGRATION_9_10
import cl.duoc.louaraya.data.local.seed.DemoSeedCallback
import cl.duoc.louaraya.domain.model.*
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ClearListsPersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun memory() = Room.inMemoryDatabaseBuilder(context, LouArayaDatabase::class.java).addCallback(DemoSeedCallback()).build()
    private suspend fun purchase(cases: LocalUseCases) = cases.createRequest(1, RequestIntention.PURCHASE,
        cases.snapshot().products.single { it.id == 1L }.price, "Prueba de limpieza", null)
    private suspend fun reject(block: suspend () -> Unit) {
        try { block(); fail("La operación debe rechazar otro perfil") } catch (_: IllegalArgumentException) { }
    }

    @Test fun clientsAndAdministratorsClearOnlyTheirOwnListsWithoutChangingBusinessHistory() = runBlocking {
        val db = memory()
        try {
            val repo = RoomLocalRepository(db); val cases = LocalUseCases(repo)
            cases.initialize(); cases.signIn("cliente.demo", "LouDemo2026!")
            val first = purchase(cases)
            cases.register("Otro cliente", "otro.cliente", "Abcdef1!", 0, "")
            val secondOwner = cases.profile()!!.id
            val second = purchase(cases)
            cases.signIn("cliente.demo", "LouDemo2026!")
            reject { cases.clearRequests(listOf(first.id, second.id), 1) }
            assertTrue(cases.snapshot().hiddenRequestIds.isEmpty())
            reject { cases.clearRequests(listOf(first.id), 1) }
            // Old client dismissals must no longer hide requests.
            repo.dismissRequests(1, listOf(first.id))
            assertTrue(cases.snapshot().hiddenRequestIds.isEmpty())
            assertEquals(first, repo.request(first.id))
            cases.signIn("admin.demo", "LouDemo2026!")
            assertTrue(cases.snapshot().hiddenRequestIds.isEmpty())
            cases.changeStock(1, 4, StockMovementType.ENTRY)
            cases.updateStatus(first.id, RequestStatus.DELIVERED)
            val before = cases.snapshot()
            cases.clearRequests(listOf(first.id, second.id), 2)
            val after = cases.snapshot()
            assertEquals(setOf(first.id, second.id), after.hiddenRequestIds)
            assertEquals(before.requests, after.requests)
            assertEquals(before.products, after.products)
            assertEquals(before.stockMovements, after.stockMovements)
            assertEquals(before.explorationEvents, after.explorationEvents)
            cases.signIn("otro.cliente", "Abcdef1!")
            assertEquals(secondOwner, cases.profile()!!.id)
            assertTrue(cases.snapshot().hiddenRequestIds.isEmpty())
            assertEquals(listOf(second.id), cases.snapshot().requests.map { it.id })
            reject { cases.clearRequests(listOf(second.id), 2) }
        } finally { db.close() }
    }

    @Test fun clearingNotificationsDoesNotEraseAnotherProfileOrReplayOldEvents() = runBlocking {
        val db = memory()
        try {
            val repo = RoomLocalRepository(db); val cases = LocalUseCases(repo)
            cases.initialize(); cases.signIn("cliente.demo", "LouDemo2026!")
            val request = purchase(cases)
            cases.signIn("admin.demo", "LouDemo2026!")
            val adminNotices = cases.snapshot().notices
            assertTrue(adminNotices.isNotEmpty())
            cases.updateStatus(request.id, RequestStatus.CONFIRMED)
            cases.signIn("cliente.demo", "LouDemo2026!")
            val own = cases.snapshot().notices
            assertTrue(own.isNotEmpty())
            reject { cases.clearNotices(own.map { it.id } + adminNotices.first().id, 1) }
            assertEquals(own, cases.snapshot().notices)
            cases.clearNotices(own.map { it.id }, 1)
            assertTrue(cases.snapshot().notices.isEmpty())
            own.forEach { repo.addNotice(it.copy(id = 0, read = false, delivered = false)) }
            assertTrue(cases.snapshot().notices.isEmpty())
            assertNotNull(repo.request(request.id))
            cases.signIn("admin.demo", "LouDemo2026!")
            assertEquals(adminNotices, cases.snapshot().notices)
            cases.clearNotices(adminNotices.map { it.id }, 2)
            assertTrue(cases.snapshot().notices.isEmpty())
            cases.updateStatus(request.id, RequestStatus.PENDING)
            cases.signIn("cliente.demo", "LouDemo2026!")
            assertEquals(1, cases.snapshot().notices.size)
            assertNotEquals(own.single().id, cases.snapshot().notices.single().id)
        } finally { db.close() }
    }

    @Test fun versionEightMigrationAndReopeningPreserveAccountsStockAndDismissals() = runBlocking {
        val name = "clear-lists-v8-migration.db"
        context.deleteDatabase(name)
        try {
            var requestId = 0L
            Room.databaseBuilder(context, LouArayaDatabase::class.java, name).addCallback(DemoSeedCallback()).build().let { db ->
                try {
                    val cases = LocalUseCases(RoomLocalRepository(db)); cases.initialize()
                    cases.signIn("cliente.demo", "LouDemo2026!"); requestId = purchase(cases).id
                    cases.signIn("admin.demo", "LouDemo2026!"); cases.changeStock(1, 5, StockMovementType.ENTRY)
                } finally { db.close() }
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use {
                it.execSQL("ALTER TABLE commercial_requests DROP COLUMN quantity")
                it.execSQL("DROP TABLE list_dismissals"); it.version = 8
            }
            repeat(2) { pass ->
                val db = Room.databaseBuilder(context, LouArayaDatabase::class.java, name).addMigrations(MIGRATION_8_9, MIGRATION_9_10).build()
                try {
                    val cases = LocalUseCases(RoomLocalRepository(db)); val snapshot = cases.snapshot()
                    assertEquals(2L, snapshot.profile!!.id)
                    assertEquals(5, snapshot.products.single { it.id == 1L }.stock)
                    assertEquals(listOf(requestId), snapshot.requests.map { it.id })
                    if (pass == 0) {
                        cases.clearRequests(listOf(requestId), 2)
                        cases.clearNotices(snapshot.notices.map { it.id }, 2)
                    } else {
                        assertEquals(setOf(requestId), snapshot.hiddenRequestIds)
                        assertTrue(snapshot.notices.isEmpty())
                        assertEquals(1, snapshot.stockMovements.size)
                    }
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
