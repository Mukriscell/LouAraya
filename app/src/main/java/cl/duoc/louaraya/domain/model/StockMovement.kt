package cl.duoc.louaraya.domain.model

enum class StockMovementType(val label: String) {
    ENTRY("Entrada"), DIRECT_SALE("Venta directa"), WITHDRAWAL("Retiro"),
    REQUEST_SALE("Venta de solicitud"), LEGACY_SALE("Entrega anterior al control de stock")
}

data class StockMovement(
    val id: Long = 0,
    val productId: Long,
    val quantityDelta: Int,
    val stockAfter: Int,
    val type: StockMovementType,
    val date: Long,
    val actorId: Long? = null,
    val note: String = "",
    val requestId: Long? = null
)
