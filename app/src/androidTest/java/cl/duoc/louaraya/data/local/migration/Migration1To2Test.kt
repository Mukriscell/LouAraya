package cl.duoc.louaraya.data.local.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.entity.CommercialRequestEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {
    @Test fun migrationFromVersion1RunsFullChainAndPreservesProductsAndRequestsAcrossReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-1-2-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        try {

            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                old.execSQL("""
                    CREATE TABLE products (
                        id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL,
                        width_cm REAL NOT NULL, length_cm REAL NOT NULL,
                        material TEXT NOT NULL, price INTEGER NOT NULL
                    )
                """.trimIndent())
                old.execSQL("INSERT INTO products VALUES (42, 'Pieza ficticia conservada', 60, 90, 'Algodón', 45000)")
                old.version = 1
            }
            val purchase = CommercialRequestEntity(
                productId = 42,
                date = 123,
                intention = "PURCHASE",
                anonymizedClient = "CLIENTE-DEMO-001",
                comment = "Consulta de prueba",
                total = 45000,
                status = "PENDING",
                campaignCategory = "FESTIVALS"
            )
            val reservation = purchase.copy(
                date = 456,
                intention = "RESERVATION",
                anonymizedClient = "CLIENTE-DEMO-002",
                comment = null,
                total = 50000,
                campaignCategory = "WEDDINGS"
            )
            var purchaseId = 0L
            var reservationId = 0L
            val migrated = Room.databaseBuilder(context, LouArayaDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
            try {
                val product = migrated.productDao().getById(42)
                assertEquals("Pieza ficticia conservada", product?.name)
                assertEquals(45000L, product?.price)
                assertEquals("INTERIOR_DECORATION", product?.campaignCategory)
                assertTrue(migrated.commercialRequestDao().getAll().isEmpty())
                purchaseId = migrated.commercialRequestDao().insert(purchase)
                reservationId = migrated.commercialRequestDao().insert(reservation)
            } finally {
                migrated.close()
            }
            val reopened = Room.databaseBuilder(context, LouArayaDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
            try {
                assertEquals(purchase.copy(id = purchaseId), reopened.commercialRequestDao().getById(purchaseId))
                assertEquals(reservation.copy(id = reservationId), reopened.commercialRequestDao().getById(reservationId))
                assertEquals(1, reopened.productDao().getAll().size)
                assertEquals(2, reopened.commercialRequestDao().getAll().size)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
