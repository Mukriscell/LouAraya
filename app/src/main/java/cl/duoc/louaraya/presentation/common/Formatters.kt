package cl.duoc.louaraya.presentation.common

import java.text.NumberFormat
import java.util.Locale

private val chileanLocale: Locale = Locale.forLanguageTag("es-CL")

/** Formatea un precio en pesos chilenos, por ejemplo: "$45.000 CLP". */
fun formatClp(price: Long): String =
    "$" + NumberFormat.getIntegerInstance(chileanLocale).format(price) + " CLP"

/** Muestra las medidas sin decimales cuando son enteras (60.0 → "60"). */
fun formatCm(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
