package de.excero.tvwartung.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Klinik-Grün, angelehnt an die Zimmerausstattung
val Teal700 = Color(0xFF00695C)
val Teal500 = Color(0xFF00897B)
val Teal100 = Color(0xFFB2DFDB)
val TealDark = Color(0xFF003731)
val WarnAmber = Color(0xFFF9A825)
val ErrorRed = Color(0xFFC62828)
val OkGreen = Color(0xFF2E7D32)

private val LightColors = lightColorScheme(
    primary = Teal700,
    onPrimary = Color.White,
    primaryContainer = Teal100,
    onPrimaryContainer = TealDark,
    secondary = Teal500,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE7E3),
    onSecondaryContainer = TealDark,
    surface = Color(0xFFFAFDFB),
    surfaceVariant = Color(0xFFE4EAE8),
    background = Color(0xFFF4F8F6),
    error = ErrorRed
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DB6AC),
    onPrimary = Color(0xFF00332D),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Teal100,
    secondary = Color(0xFF80CBC4),
    surface = Color(0xFF101413),
    background = Color(0xFF0C100F),
    error = Color(0xFFEF9A9A)
)

@Composable
fun KKHTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content
    )
}
