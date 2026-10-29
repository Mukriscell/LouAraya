package cl.duoc.louaraya.data.local.migration

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.entity.CommercialRequestEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration3To4Test {
    @Test
    fun migrationAddsNullableImageAndPreservesCatalogAndRequest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-3-4-test.db"
        context.deleteDatabase(name)
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                old.execSQL("""
                    CREATE TABLE products (
                        id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL,
                        width_cm REAL NOT NULL, length_cm REAL NOT NULL, material TEXT NOT NULL,
                        price INTEGER NOT NULL, campaign_category TEXT NOT NULL DEFAULT 'INTERIOR_DECORATION'
                    )
                """.trimIndent())
                old.execSQL("INSERT INTO products VALUES (7, 'Pieza migración ficticia', 50, 80, 'Yute', 12000, 'TRAVEL')")
                old.execSQL("""
                    CREATE TABLE commercial_requests (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, product_id INTEGER NOT NULL,
                        date INTEGER NOT NULL, intention TEXT NOT NULL, anonymized_client TEXT NOT NULL,
                        comment TEXT, total INTEGER NOT NULL, status TEXT NOT NULL, campaign_category TEXT
                    )
                """.trimIndent())
                old.execSQL("CREATE INDEX index_commercial_requests_product_id ON commercial_requests (product_id)")
                old.execSQL("INSERT INTO commercial_requests VALUES (11, 7, 1234, 'PURCHASE', 'CLIENTE-DEMO', 'Consulta ficticia', 12000, 'PENDING', 'TRAVEL')")
                old.version = 3
            }
            val db = Room.databaseBuilder(context, LouArayaDatabase::class.java, name)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build()
            try {
                val product = db.productDao().getById(7L)
                assertEquals("Pieza migración ficticia", product?.name)
                assertEquals(50.0, product?.widthCm)
                assertEquals(80.0, product?.lengthCm)
                assertEquals("Yute", product?.material)
                assertEquals(12000L, product?.price)
                assertEquals("TRAVEL", product?.campaignCategory)
                assertNull(product?.imageReference)
                val request = db.commercialRequestDao().getById(11L)
                assertEquals(
                    CommercialRequestEntity(
                        id = 11L, productId = 7L, date = 1234L, intention = "PURCHASE",
                        anonymizedClient = "CLIENTE-DEMO", comment = "Consulta ficticia",
                        total = 12000L, status = "PENDING", campaignCategory = "TRAVEL"
                    ),
                    request
                )
                assertEquals(1, db.commercialRequestDao().getAll().size)
                assertEquals(1, db.productDao().updateImageReference(7L, PERSISTED_REFERENCE))
                assertEquals(PERSISTED_REFERENCE, db.productDao().getById(7L)?.imageReference)
            } finally {
                db.close()
            }
            val reopened = Room.databaseBuilder(context, LouArayaDatabase::class.java, name)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build()
            try {
                val persistedProduct = reopened.productDao().getById(7L)
                assertEquals(PERSISTED_REFERENCE, persistedProduct?.imageReference)
                assertEquals("Pieza migración ficticia", persistedProduct?.name)
                assertEquals(50.0, persistedProduct?.widthCm)
                assertEquals(80.0, persistedProduct?.lengthCm)
                assertEquals("Yute", persistedProduct?.material)
                assertEquals(12000L, persistedProduct?.price)
                assertEquals("TRAVEL", persistedProduct?.campaignCategory)
                assertEquals(11L, reopened.commercialRequestDao().getById(11L)?.id)
                assertEquals(1, reopened.commercialRequestDao().getAll().size)
            } finally {
                reopened.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }

    private companion object {
        const val PERSISTED_REFERENCE = "product-images/00000000-0000-0000-0000-000000000001.jpg"
    }
}
