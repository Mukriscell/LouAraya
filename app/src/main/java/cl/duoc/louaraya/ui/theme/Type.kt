package cl.duoc.louaraya.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.sp
import cl.duoc.louaraya.R


val AtelierSerif = FontFamily(Font(R.font.cormorant_garamond_600, FontWeight.SemiBold))
val AtelierEditorial = FontFamily(
    Font(R.font.cormorant_garamond_500, FontWeight.Medium),
    Font(R.font.cormorant_garamond_500_italic, FontWeight.Medium, FontStyle.Italic),
    Font(R.font.cormorant_garamond_600, FontWeight.SemiBold))
private val AtelierSans = FontFamily(Font(R.font.dm_sans_400), Font(R.font.dm_sans_500, FontWeight.Medium), Font(R.font.dm_sans_600, FontWeight.SemiBold))
private fun role(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(fontFamily = AtelierSans, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = 0.sp)
private fun serif(size: Int, line: Int) = TextStyle(fontFamily = AtelierSerif, fontSize = size.sp, lineHeight = line.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp)
val Typography = Typography(
    displayLarge = serif(44, 44),
    displayMedium = serif(36, 38),
    displaySmall = serif(30, 34),
    headlineLarge = serif(36, 38),
    headlineMedium = serif(30, 34),
    headlineSmall = serif(28, 32),
    titleLarge = role(20, 26, FontWeight.SemiBold),
    titleMedium = role(16, 22, FontWeight.Medium),
    titleSmall = role(14, 20, FontWeight.SemiBold),
    bodyLarge = role(14, 21),
    bodyMedium = role(14, 21),
    bodySmall = role(12, 17),
    labelLarge = role(16, 22, FontWeight.Medium),
    labelMedium = role(12, 16, FontWeight.SemiBold),
    labelSmall = role(10, 14, FontWeight.Medium).copy(letterSpacing = 1.2.sp)
)
