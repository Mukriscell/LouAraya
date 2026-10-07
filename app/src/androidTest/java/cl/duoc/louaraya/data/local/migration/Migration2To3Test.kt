package cl.duoc.louaraya.data.local.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.entity.CommercialRequestEntity
import cl.duoc.louaraya.domain.model.CampaignCategory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration2To3Test {

    @Test
    fun migrationMapsDemoCategoriesAndPreservesProductAndRequestSnapshotsAfterReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-2-3-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        try {
            createVersion2Database(file)
            val migrated = Room.databaseBuilder(context, LouArayaDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .build()
            try {
                val products = migrated.productDao().getAll().associateBy { it.id }
                assertEquals(5, products.size)
                assertEquals(CampaignCategory.INTERIOR_DECORATION.name, products.getValue(1).campaignCategory)
                assertEquals(CampaignCategory.FESTIVALS.name, products.getValue(2).campaignCategory)
                assertEquals(CampaignCategory.WEDDINGS.name, products.getValue(3).campaignCategory)
                assertEquals(CampaignCategory.TRAVEL.name, products.getValue(4).campaignCategory)
                assertEquals(CampaignCategory.INTERIOR_DECORATION.name, products.getValue(5).campaignCategory)
                assertEquals("Tapiz Demo Aurora", products.getValue(1).name)
                assertEquals(60.0, products.getValue(1).widthCm, 0.0)
                assertEquals(90.0, products.getValue(1).lengthCm, 0.0)
                assertEquals("Algodón", products.getValue(1).material)
                assertEquals(45000L, products.getValue(1).price)
                assertEquals(requestSnapshot, migrated.commercialRequestDao().getById(requestSnapshot.id))
            } finally {
                migrated.close()
            }

            val reopened = Room.databaseBuilder(context, LouArayaDatabase::class.java, name)
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4)
                .build()
            try {
                assertEquals(
                    CampaignCategory.INTERIOR_DECORATION.name,
                    reopened.productDao().getById(1L)?.campaignCategory
                )
                assertEquals(requestSnapshot, reopened.commercialRequestDao().getById(requestSnapshot.id))
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private fun createVersion2Database(file: java.io.File) {
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                """
                CREATE TABLE products (
                    id INTEGER NOT NULL PRIMARY KEY,
                    name TEXT NOT NULL,
                    width_cm REAL NOT NULL,
                    length_cm REAL NOT NULL,
                    material TEXT NOT NULL,
                    price INTEGER NOT NULL
                )
                """.trimIndent()
            )
            insertProduct(db, 1, "Tapiz Demo Aurora")
            insertProduct(db, 2, "Colgante Demo Brisa")
            insertProduct(db, 3, "Camino de Mesa Demo")
            insertProduct(db, 4, "Portamacetero Demo")
            insertProduct(db, 5, "Pieza legacy sin categoría")
            db.execSQL(
                """
                CREATE TABLE commercial_requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    product_id INTEGER NOT NULL,
                    date INTEGER NOT NULL,
                    intention TEXT NOT NULL,
                    anonymized_client TEXT NOT NULL,
                    comment TEXT,
                    total INTEGER NOT NULL,
                    status TEXT NOT NULL,
                    campaign_category TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX index_commercial_requests_product_id " +
                    "ON commercial_requests (product_id)"
            )
            db.execSQL(
                """
                INSERT INTO commercial_requests (
                    id, product_id, date, intention, anonymized_client, comment,
                    total, status, campaign_category
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                arrayOf<Any?>(
                    requestSnapshot.id,
                    requestSnapshot.productId,
                    requestSnapshot.date,
                    requestSnapshot.intention,
                    requestSnapshot.anonymizedClient,
                    requestSnapshot.comment,
                    requestSnapshot.total,
                    requestSnapshot.status,
                    requestSnapshot.campaignCategory
                )
            )
            db.version = 2
        }
    }

    private fun insertProduct(db: SQLiteDatabase, id: Int, name: String) {
        db.execSQL(
            "INSERT INTO products (id, name, width_cm, length_cm, material, price) VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf<Any>(id, name, 60.0, 90.0, "Algodón", 45000L)
        )
    }

    private companion object {
        val requestSnapshot = CommercialRequestEntity(
            id = 19L,
            productId = 1L,
            date = 1_700_000_000_123L,
            intention = "RESERVATION",
            anonymizedClient = "CLIENTE-DEMO-LEGACY",
            comment = "Categoría histórica debe conservarse",
            total = 45000L,
            status = "PENDING",
            campaignCategory = "FESTIVALS"
        )
    }
}
