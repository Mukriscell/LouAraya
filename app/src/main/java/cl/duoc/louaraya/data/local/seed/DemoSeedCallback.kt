package cl.duoc.louaraya.data.local.seed

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Inserta los productos de [DemoProductSeed] al abrir la base de datos,
 * únicamente si la tabla `products` está vacía.
 *
 * Se usa SQL directo porque los DAO no pueden invocarse mientras Room abre la
 * base de datos. Al ejecutarse durante la apertura, el seed queda listo antes
 * de que se resuelva la primera consulta del catálogo.
 */
class DemoSeedCallback : RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        if (isProductsTableEmpty(db)) {
            insertDemoProducts(db)
        }
    }

    private fun isProductsTableEmpty(db: SupportSQLiteDatabase): Boolean =
        db.query("SELECT COUNT(*) FROM products").use { cursor ->
            cursor.moveToFirst() && cursor.getLong(0) == 0L
        }

    private fun insertDemoProducts(db: SupportSQLiteDatabase) {
        db.beginTransaction()
        try {
            DemoProductSeed.products.forEach { product ->
                db.execSQL(
                    "INSERT INTO products (id, name, width_cm, length_cm, material, price, campaign_category) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    arrayOf<Any>(
                        product.id,
                        product.name,
                        product.widthCm,
                        product.lengthCm,
                        product.material,
                        product.price,
                        product.campaignCategory
                    )
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
