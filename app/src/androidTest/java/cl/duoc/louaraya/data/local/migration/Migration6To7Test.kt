package cl.duoc.louaraya.data.local.migration

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import cl.duoc.louaraya.data.local.LouArayaDatabase
import cl.duoc.louaraya.data.repository.RoomLocalRepository
import cl.duoc.louaraya.domain.usecase.LocalUseCases
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class Migration6To7Test {
    @Test fun migrationPreservesLegacyDataAndDoesNotInventCampaignsOrAgreements() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "commercial-migration-v6.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE products (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, width_cm REAL NOT NULL, length_cm REAL NOT NULL, material TEXT NOT NULL, price INTEGER NOT NULL, campaign_category TEXT NOT NULL DEFAULT 'INTERIOR_DECORATION', image_reference TEXT)")
                    db.execSQL("CREATE TABLE commercial_requests (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, product_id INTEGER NOT NULL, date INTEGER NOT NULL, intention TEXT NOT NULL, anonymized_client TEXT NOT NULL, comment TEXT, total INTEGER NOT NULL, status TEXT NOT NULL, campaign_category TEXT)")
                    db.execSQL("CREATE INDEX index_commercial_requests_product_id ON commercial_requests(product_id)")
                    MIGRATION_4_5.migrate(db)
                    MIGRATION_5_6.migrate(db)
                    db.execSQL("INSERT INTO users VALUES (42,'Perfil ficticio','previo.demo','hash','salt','CLIENT',3,'',1,NULL,'avatar-images/previa.jpg')")
                    db.execSQL("INSERT INTO app_session VALUES (1,42)")
                    db.execSQL("INSERT INTO products VALUES (1,'Pieza previa',40,60,'Yute',12000,'TRAVEL','product-images/previa.jpg','Descripción previa','AVAILABLE',1)")
                    db.execSQL("INSERT INTO commercial_requests VALUES (55,1,123,'RESERVATION','CLIENTE-42','Comentario previo',12000,'PENDING','TRAVEL',42,NULL)")
                    db.execSQL("INSERT INTO favorites VALUES (42,1)")
                    db.execSQL("INSERT INTO collections VALUES (8,'Colección previa','Anterior')")
                    db.execSQL("INSERT INTO collection_products VALUES (8,1)")
                    db.execSQL("INSERT INTO campaigns VALUES (9,'Campaña previa',8,'2000-01-01','2099-12-31',1)")
                    db.execSQL("INSERT INTO qr_scans VALUES (11,42,'product/1',123,'lectura-previa')")
                    db.execSQL("INSERT INTO local_notices VALUES (51,42,'Aviso previo','Demo','collection/8',123,1,'previo',1)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build())
        helper.writableDatabase; helper.close()
        try {
            repeat(2) { pass ->
                val db = Room.databaseBuilder(context, LouArayaDatabase::class.java, name).addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
                try {
                    val cases = LocalUseCases(RoomLocalRepository(db))
                    val snapshot = cases.snapshot()
                    assertEquals(42L, snapshot.profile!!.id)
                    assertEquals("avatar-images/previa.jpg", snapshot.profile.avatarImageReference)
                    assertEquals("product-images/previa.jpg", snapshot.products.single().imageReference)
                    val request = snapshot.requests.single()
                    assertEquals(55L, request.id); assertEquals(12000L, request.total)
                    assertNull(request.campaignId); assertNull(request.journeyId); assertNull(request.agreedTotal)
                    assertNull(snapshot.campaigns.single().targetCategory)
                    assertTrue(snapshot.journeys.isEmpty())
                    assertEquals(if (pass == 0) 0 else 1, snapshot.explorationEvents.size)
                    assertEquals(setOf(1L), snapshot.favoriteIds)
                    assertTrue(snapshot.notices.single().delivered)
                    assertEquals(if (pass == 0) emptySet<Long>() else setOf(8L), snapshot.favoriteCollectionIds)
                    db.openHelper.readableDatabase.query("SELECT destination FROM qr_scans WHERE id=11").use { assertTrue(it.moveToFirst()); assertEquals("product/1", it.getString(0)) }
                    if (pass == 0) cases.setFavoriteCollection(8, true)
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
