package cl.duoc.louaraya.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE commercial_requests ADD COLUMN quantity INTEGER NOT NULL DEFAULT 1")
        db.execSQL("DELETE FROM list_dismissals WHERE kind = 'REQUEST' AND userId IN (SELECT id FROM users WHERE role = 'CLIENT')")
    }
}
