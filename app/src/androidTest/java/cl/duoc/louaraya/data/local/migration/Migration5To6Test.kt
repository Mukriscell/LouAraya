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

class Migration5To6Test {
    @Test fun existingProfileSessionFavoriteAndQrHistorySurviveMigrationAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "avatar-migration-v5.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object : SupportSQLiteOpenHelper.Callback(5) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE products (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, width_cm REAL NOT NULL, length_cm REAL NOT NULL, material TEXT NOT NULL, price INTEGER NOT NULL, campaign_category TEXT NOT NULL DEFAULT 'INTERIOR_DECORATION', image_reference TEXT)")
                db.execSQL("CREATE TABLE commercial_requests (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, product_id INTEGER NOT NULL, date INTEGER NOT NULL, intention TEXT NOT NULL, anonymized_client TEXT NOT NULL, comment TEXT, total INTEGER NOT NULL, status TEXT NOT NULL, campaign_category TEXT)")
                db.execSQL("CREATE INDEX index_commercial_requests_product_id ON commercial_requests(product_id)")
                MIGRATION_4_5.migrate(db)
                db.execSQL("INSERT INTO users VALUES (42, 'Perfil anterior', 'previo.demo', 'hash', 'salt', 'CLIENT', 3, '', 1, NULL)")
                db.execSQL("INSERT INTO app_session VALUES (1,42)")
                db.execSQL("INSERT INTO favorites VALUES (42,1)")
                db.execSQL("INSERT INTO qr_scans VALUES (11,42,'product/1',123,'qr-anterior')")
            }
            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }).build())
        helper.writableDatabase; helper.close()
        try {
            repeat(2) { pass ->
                val db = Room.databaseBuilder(context, LouArayaDatabase::class.java, name).addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10).build()
                try {
                    val cases = LocalUseCases(RoomLocalRepository(db))
                    val user = requireNotNull(cases.profile())
                    assertEquals(42L, user.id); assertEquals(3, user.avatar)
                    assertEquals("previo.demo", user.alias)
                    val ref = "avatar-images/00000000-0000-0000-0000-000000000001.jpg"
                    assertEquals(if (pass == 0) null else ref, user.avatarImageReference)
                    assertEquals(setOf(1L), RoomLocalRepository(db).favoriteIds(42))
                    db.openHelper.readableDatabase.query("SELECT destination FROM qr_scans WHERE id=11").use { assertTrue(it.moveToFirst()); assertEquals("product/1", it.getString(0)) }
                    if (pass == 0) cases.setAvatarPhoto(42, ref)
                } finally { db.close() }
            }
        } finally { context.deleteDatabase(name) }
    }
}
