package cl.duoc.louaraya.data.repository

import android.app.Application
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.local.migration.*
import cl.duoc.louaraya.domain.model.RequestStatus
import cl.duoc.louaraya.domain.model.StockMovementType
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
class StockMigrationTest {
    @Test fun versionSevenMigrationPreservesDataAndNeverDiscountsAnOldDelivery() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val name = "stock-migration-v7.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE products (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, width_cm REAL NOT NULL, length_cm REAL NOT NULL, material TEXT NOT NULL, price INTEGER NOT NULL, campaign_category TEXT NOT NULL DEFAULT 'INTERIOR_DECORATION', image_reference TEXT)")
                    db.execSQL("CREATE TABLE commercial_requests (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, product_id INTEGER NOT NULL, date INTEGER NOT NULL, intention TEXT NOT NULL, anonymized_client TEXT NOT NULL, comment TEXT, total INTEGER NOT NULL, status TEXT NOT NULL, campaign_category TEXT)")
                    db.execSQL("CREATE INDEX index_commercial_requests_product_id ON commercial_requests(product_id)")
                    MIGRATION_4_5.migrate(db)
                    MIGRATION_5_6.migrate(db)
                    MIGRATION_6_7.migrate(db)
                    db.execSQL("INSERT INTO users VALUES (42,'Perfil conservado','previo.demo','hash','salt','ADMIN',3,'Contacto',1,NULL,'avatar-images/previa.jpg')")
                    db.execSQL("INSERT INTO app_session VALUES (1,42)")
                    db.execSQL("INSERT INTO products VALUES (1,'Pieza conservada',40,60,'Yute',12000,'TRAVEL','product-images/previa.jpg','Descripción','AVAILABLE',1)")
                    db.execSQL("INSERT INTO favorites VALUES (42,1)")
                    db.execSQL("INSERT INTO commercial_requests (id, product_id, date, intention, anonymized_client, total, status, owner_id) VALUES (10,1,123,'PURCHASE','CLIENTE-42',12000,'DELIVERED',42)")
                    db.execSQL("INSERT INTO commercial_requests (id, product_id, date, intention, anonymized_client, total, status, owner_id) VALUES (11,1,124,'RESERVATION','CLIENTE-42',12000,'PENDING',42)")
                    db.execSQL("INSERT INTO commercial_requests (id, product_id, date, intention, anonymized_client, total, status, owner_id) VALUES (12,1,125,'QUOTATION','CLIENTE-42',12000,'DELIVERED',42)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase
        helper.close()
        try {
            repeat(2) { pass ->
                val db = Room.databaseBuilder(context, LouArayaDatabase::class.java, name).addMigrations(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
                try {
                    val cases = LocalUseCases(RoomLocalRepository(db))
                    val snapshot = cases.snapshot()
                    assertEquals(42L, snapshot.profile!!.id)
                    assertEquals("avatar-images/previa.jpg", snapshot.profile.avatarImageReference)
                    assertEquals("product-images/previa.jpg", snapshot.products.single().imageReference)
                    assertEquals(3, snapshot.requests.size)
                    assertEquals(if (pass == 0) 0 else 9, snapshot.products.single().stock)
                    assertEquals(1, snapshot.stockMovements.count { it.type == StockMovementType.LEGACY_SALE })
                    if (pass == 0) {
                        cases.changeStock(1, 10, StockMovementType.ENTRY, "Primer conteo real")
                        cases.updateStatus(10, RequestStatus.PENDING)
                        cases.updateStatus(10, RequestStatus.DELIVERED)
                        assertEquals(10, cases.snapshot().products.single().stock)
                        cases.updateStatus(11, RequestStatus.DELIVERED)
                        assertEquals(9, cases.snapshot().products.single().stock)
                    } else {
                        assertEquals(3, snapshot.stockMovements.size)
                        assertEquals(RequestStatus.DELIVERED, snapshot.requests.first { it.id == 11L }.status)
                    }
                    db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM favorites WHERE userId=42 AND productId=1").use { assertTrue(it.moveToFirst()); assertEquals(1, it.getInt(0)) }
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
