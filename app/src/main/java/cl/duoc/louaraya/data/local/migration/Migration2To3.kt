package cl.duoc.louaraya.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase







val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE products ADD COLUMN campaign_category TEXT NOT NULL DEFAULT 'INTERIOR_DECORATION'"
        )
        db.execSQL(
"""
            UPDATE products
            SET campaign_category = CASE name
                WHEN 'Tapiz Demo Aurora' THEN 'INTERIOR_DECORATION'
                WHEN 'Colgante Demo Brisa' THEN 'FESTIVALS'
                WHEN 'Camino de Mesa Demo' THEN 'WEDDINGS'
                WHEN 'Portamacetero Demo' THEN 'TRAVEL'
                ELSE 'INTERIOR_DECORATION'
            END
            """.trimIndent()
        )
    }
}
