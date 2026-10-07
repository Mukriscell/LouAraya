package cl.duoc.louaraya.data.local.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Añade la campaña del producto. La clasificación de los cuatro nombres demo es
 * ficticia y se mantiene igual que en [cl.duoc.louaraya.data.local.seed.DemoProductSeed].
 * Los productos legacy no demo reciben Decoración de Interiores como categoría
 * oficial de respaldo; no se modifican las solicitudes históricas.
 */
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
