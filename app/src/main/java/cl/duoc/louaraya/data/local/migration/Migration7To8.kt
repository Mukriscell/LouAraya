package cl.duoc.louaraya.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN stock INTEGER NOT NULL DEFAULT 0")
        db.execSQL("CREATE TABLE stock_movements (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, productId INTEGER NOT NULL, quantityDelta INTEGER NOT NULL, stockAfter INTEGER NOT NULL, type TEXT NOT NULL, date INTEGER NOT NULL, actorId INTEGER, note TEXT NOT NULL, requestId INTEGER)")
        db.execSQL("CREATE INDEX index_stock_movements_productId ON stock_movements(productId)")
        db.execSQL("CREATE UNIQUE INDEX index_stock_movements_requestId ON stock_movements(requestId)")
        db.execSQL("INSERT INTO stock_movements (productId, quantityDelta, stockAfter, type, date, actorId, note, requestId) SELECT product_id, 0, 0, 'LEGACY_SALE', date, NULL, 'Entrega anterior al registro de existencias; no modifica el stock.', id FROM commercial_requests WHERE status = 'DELIVERED' AND intention != 'QUOTATION'")
    }
}
