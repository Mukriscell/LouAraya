package cl.duoc.louaraya.domain.usecase

class FormValidationException(val fields: Map<String, String>) : IllegalArgumentException(fields.values.first())

internal fun fieldRequire(valid: Boolean, field: String, message: String) {
    if (!valid) throw FormValidationException(mapOf(field to message))
}

object FormValidation {
    fun requestTotal(unitPrice: Long, quantity: Int): Long {
        fieldRequire(quantity > 0, "quantity", "Escribe una cantidad entera mayor a cero.")
        require(unitPrice >= 0) { "El precio de la pieza no es válido." }
        return try { Math.multiplyExact(unitPrice, quantity.toLong()) }
        catch (_: ArithmeticException) { throw FormValidationException(mapOf("quantity" to "La cantidad genera un total demasiado alto.")) }
    }
    const val REGISTRATION_PASSWORD_HINT = "Entre 8 y 20 caracteres, con al menos una mayúscula, un número y un carácter especial (por ejemplo, !, @ o #)."
    private val passwordSymbol = Regex("[\\p{P}\\p{S}]")

    fun registrationPassword(password: String) {
        fieldRequire(password.length in 8..20, "password", "La contraseña debe tener entre 8 y 20 caracteres.")
        fieldRequire(password.any { it.isUpperCase() }, "password", "Incluye al menos una letra mayúscula.")
        fieldRequire(password.any { it.isDigit() }, "password", "Incluye al menos un número.")
        fieldRequire(passwordSymbol.containsMatchIn(password), "password", "Incluye al menos un carácter especial, como !, @ o #.")
    }

    fun stockQuantity(value: String): Int {
        val quantity = value.trim().toIntOrNull()
        fieldRequire(quantity != null && quantity > 0, "quantity", "Escribe una cantidad entera mayor a cero.")
        return requireNotNull(quantity)
    }
    fun matchingPasswords(password: String, confirmation: String) {
        fieldRequire(password == confirmation, "confirm", "Las contraseñas no coinciden.")
    }

    fun productPrice(value: String): Long = value.trim().toLongOrNull()
        ?: throw FormValidationException(mapOf("price" to "Escribe un precio entero, sin puntos ni comas."))

    fun agreedTotal(value: String): Long = value.trim().toLongOrNull()
        ?: throw FormValidationException(mapOf("agreedTotal" to "Escribe un importe entero válido, sin puntos ni comas."))

    fun dimension(value: String): Double = value.trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN

    fun quotationPiece(width: String, length: String, material: String, colors: String) =
        quotationPiece(dimension(width), dimension(length), material, colors)

    fun quotationPiece(width: Double, length: Double, material: String, colors: String) {
        val errors = buildMap {
            if (!width.isFinite() || width <= 0) put("width", "Escribe un ancho positivo.")
            if (!length.isFinite() || length <= 0) put("length", "Escribe un largo positivo.")
            if (material.trim().length !in 1..100) put("material", "Escribe el material preferido (máximo 100 caracteres).")
            if (colors.trim().length !in 1..100) put("colors", "Cuéntanos qué colores prefieres (máximo 100 caracteres).")
        }
        if (errors.isNotEmpty()) throw FormValidationException(errors)
    }

    fun quotationDetails(event: String, width: String, length: String, material: String, colors: String, date: String) =
        QuotationDetails(event, dimension(width), dimension(length), material, colors, date)
}
