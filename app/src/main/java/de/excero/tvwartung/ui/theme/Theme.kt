package de.excero.tvwartung.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Nutzer-Einstellung fürs Erscheinungsbild (2.0: manueller Override möglich). */
enum class AppTheme { SYSTEM, HELL, DUNKEL }

// 2.0: modernisiertes, etwas frischeres Teal/Türkis
val Teal700 = Color(0xFF00786B)
val Teal500 = Color(0xFF12A594)
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

// 2.0: dunkles Grau statt reinem Schwarz, Statusfarben leicht gedämpft
private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD0C1),
    onPrimary = Color(0xFF00332D),
    primaryContainer = Color(0xFF00544A),
    onPrimaryContainer = Teal100,
    secondary = Color(0xFF80CBC4),
    surface = Color(0xFF181C1B),
    surfaceVariant = Color(0xFF2A302E),
    background = Color(0xFF141817),
    error = Color(0xFFE57373)
)

@Composable
fun KKHTheme(theme: AppTheme = AppTheme.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.HELL -> false
        AppTheme.DUNKEL -> true
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        content = content
    )
}

/**
 * Etwas großzügiger gerundete Formen als die Material3-Standardwerte – wirkt
 * moderner/weicher, ohne die „mittlere" Rundung zu übertreiben. Gilt zentral
 * für Karten, Dialoge, Textfelder und Buttons.
 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)
