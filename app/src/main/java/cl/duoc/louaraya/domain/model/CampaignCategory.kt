package cl.duoc.louaraya.domain.model

/**
 * Categorías de campaña para clasificar productos por tipo de evento.
 *
 * @property displayName nombre legible de la categoría.
 */
enum class CampaignCategory(val displayName: String) {
    WEDDINGS("Matrimonios"),
    FESTIVALS("Festivales"),
    INTERIOR_DECORATION("Decoración de Interiores"),
    TRAVEL("Viajes")
}
