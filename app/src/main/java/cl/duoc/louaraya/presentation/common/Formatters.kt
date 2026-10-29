package cl.duoc.louaraya.presentation.common

import java.text.NumberFormat
import java.util.Locale

private val chileanLocale: Locale = Locale.forLanguageTag("es-CL")


fun formatClp(price: Long): String =
    "$" + NumberFormat.getIntegerInstance(chileanLocale).format(price) + " CLP"


fun formatCm(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()


fun displayClientReference(reference: String): String =
    if (reference == "CLIENTE-DEMO-001") "CLIENTE-001" else reference
