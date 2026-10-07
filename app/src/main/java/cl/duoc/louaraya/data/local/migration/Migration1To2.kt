package cl.duoc.louaraya.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** Añade solicitudes sin modificar ni eliminar los productos de la versión 1. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS commercial_requests (
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
        db.execSQL("CREATE INDEX IF NOT EXISTS index_commercial_requests_product_id ON commercial_requests (product_id)")
    }
}
