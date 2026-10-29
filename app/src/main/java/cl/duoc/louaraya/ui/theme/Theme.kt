package cl.duoc.louaraya.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFF1B097),
    secondary = androidx.compose.ui.graphics.Color(0xFFDFC0A8),
    tertiary = androidx.compose.ui.graphics.Color(0xFFB5C9A9),
    background = androidx.compose.ui.graphics.Color(0xFF1D1511),
    surface = androidx.compose.ui.graphics.Color(0xFF1D1511),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFF2A201A),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFF241B16),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFF342820),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFF403127)
)

private val LightColorScheme = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFA64E32),
    secondary = androidx.compose.ui.graphics.Color(0xFF76675B),
    tertiary = androidx.compose.ui.graphics.Color(0xFF53664C),
    background = androidx.compose.ui.graphics.Color(0xFFFAF7F1),
    surface = androidx.compose.ui.graphics.Color(0xFFFAF7F1),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFEBE2D5),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFEBE2D5),
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFF4E0D5),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFA64E32),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF2D1D14),
    onSurface = androidx.compose.ui.graphics.Color(0xFF30251F),
    onBackground = androidx.compose.ui.graphics.Color(0xFF30251F),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF76675B),
    tertiaryContainer = androidx.compose.ui.graphics.Color(0xFFE4E9DC),
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFF53664C),
    surfaceContainer = androidx.compose.ui.graphics.Color(0xFFEBE2D5),
    surfaceContainerLow = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    surfaceContainerHigh = androidx.compose.ui.graphics.Color(0xFFEBE2D5),
    surfaceContainerHighest = androidx.compose.ui.graphics.Color(0xFFD8CABC),
    outline = androidx.compose.ui.graphics.Color(0xFF76675B),
    outlineVariant = androidx.compose.ui.graphics.Color(0xFFD8CABC),
    error = androidx.compose.ui.graphics.Color(0xFFAA302B),
    errorContainer = androidx.compose.ui.graphics.Color(0xFFFBE5E1)










)

@Composable
fun LouArayaTheme(
    darkTheme: Boolean = false,

    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
