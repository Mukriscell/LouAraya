package cl.duoc.louaraya.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS list_dismissals (userId INTEGER NOT NULL, kind TEXT NOT NULL, itemId INTEGER NOT NULL, PRIMARY KEY(userId, kind, itemId))")
    }
}
