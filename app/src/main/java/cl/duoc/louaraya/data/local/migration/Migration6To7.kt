package cl.duoc.louaraya.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE campaigns ADD COLUMN targetCategory TEXT")
        db.execSQL("ALTER TABLE commercial_requests ADD COLUMN campaignId INTEGER")
        db.execSQL("ALTER TABLE commercial_requests ADD COLUMN sourceCollectionId INTEGER")
        db.execSQL("ALTER TABLE commercial_requests ADD COLUMN journeyId TEXT")
        db.execSQL("ALTER TABLE commercial_requests ADD COLUMN agreedTotal INTEGER")
        db.execSQL("ALTER TABLE commercial_requests ADD COLUMN agreedAt INTEGER")
        db.execSQL("CREATE TABLE favorite_collections (userId INTEGER NOT NULL, collectionId INTEGER NOT NULL, PRIMARY KEY(userId, collectionId))")
        db.execSQL("CREATE TABLE exploration_journeys (id TEXT NOT NULL PRIMARY KEY, userId INTEGER NOT NULL, campaignId INTEGER NOT NULL, collectionId INTEGER NOT NULL, date INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE exploration_events (eventKey TEXT NOT NULL PRIMARY KEY, userId INTEGER NOT NULL, kind TEXT NOT NULL, date INTEGER NOT NULL, journeyId TEXT, campaignId INTEGER, collectionId INTEGER, productId INTEGER, requestId INTEGER, amount INTEGER)")
    }
}
