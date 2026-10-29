package cl.duoc.louaraya.data.local.seed

import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase









class DemoSeedCallback : RoomDatabase.Callback() {

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        updatePreviousProductLabels(db)
        if (isProductsTableEmpty(db)) {
            insertDemoProducts(db)
        }
    }


    private fun updatePreviousProductLabels(db: SupportSQLiteDatabase) {
        DemoProductSeed.products.forEach { product ->
            val previousName = DemoProductSeed.previousDisplayNames[product.id] ?: return@forEach
            db.execSQL(
                "UPDATE products SET name = ? WHERE id = ? AND name = ?",
                arrayOf<Any>(product.name, product.id, previousName)
            )
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
