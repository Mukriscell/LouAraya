package cl.duoc.louaraya.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase


val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE products ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE products ADD COLUMN availability TEXT NOT NULL DEFAULT 'AVAILABLE'")
        db.execSQL("ALTER TABLE products ADD COLUMN active INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE commercial_requests ADD COLUMN owner_id INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE commercial_requests ADD COLUMN customization TEXT")
        db.execSQL("CREATE INDEX index_commercial_requests_owner_id ON commercial_requests (owner_id)")
        db.execSQL("CREATE TABLE IF NOT EXISTS users (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, alias TEXT NOT NULL, passwordHash TEXT NOT NULL, passwordSalt TEXT NOT NULL, role TEXT NOT NULL, avatar INTEGER NOT NULL, contact TEXT NOT NULL, notificationsEnabled INTEGER NOT NULL, preferredCategory TEXT)")
        db.execSQL("CREATE UNIQUE INDEX index_users_alias ON users (alias)")
        db.execSQL("CREATE TABLE IF NOT EXISTS app_session (id INTEGER NOT NULL PRIMARY KEY, userId INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS favorites (userId INTEGER NOT NULL, productId INTEGER NOT NULL, PRIMARY KEY(userId, productId))")
        db.execSQL("CREATE TABLE IF NOT EXISTS collections (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, description TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS collection_products (collectionId INTEGER NOT NULL, productId INTEGER NOT NULL, PRIMARY KEY(collectionId, productId))")
        db.execSQL("CREATE TABLE IF NOT EXISTS campaigns (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, collectionId INTEGER NOT NULL, startsOn TEXT NOT NULL, endsOn TEXT NOT NULL, published INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS fair_events (id INTEGER NOT NULL PRIMARY KEY, name TEXT NOT NULL, date TEXT NOT NULL, location TEXT NOT NULL, latitude REAL NOT NULL, longitude REAL NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS local_notices (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, destination TEXT NOT NULL, date INTEGER NOT NULL, read INTEGER NOT NULL, eventKey TEXT NOT NULL, delivered INTEGER NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX index_local_notices_userId_eventKey ON local_notices (userId, eventKey)")
        db.execSQL("CREATE TABLE IF NOT EXISTS qr_scans (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, userId INTEGER NOT NULL, destination TEXT NOT NULL, date INTEGER NOT NULL, sessionKey TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX index_qr_scans_sessionKey ON qr_scans (sessionKey)")
    }
}
