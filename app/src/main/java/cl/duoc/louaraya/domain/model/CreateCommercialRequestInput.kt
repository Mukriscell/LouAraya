package cl.duoc.louaraya.domain.model

/** Datos elegidos para una intención; la campaña se deriva del producto en dominio. */
data class CreateCommercialRequestInput(
    val productId: Long,
    val intention: RequestIntention,
    /** Importe que el usuario vio al abrir el formulario, usado para detectar cambios de precio. */
    val displayedTotal: Long,
    val comment: String? = null
)
