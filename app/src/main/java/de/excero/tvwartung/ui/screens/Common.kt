package de.excero.tvwartung.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.ui.theme.ErrorRed
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.ui.theme.WarnAmber
import de.excero.tvwartung.util.Dates

/** Ampel-Status für die Freenet-Gültigkeit. */
enum class FreenetStatus(val label: String, val color: Color) {
    OK("gültig", OkGreen),
    BALD("läuft bald ab", WarnAmber),
    ABGELAUFEN("abgelaufen", ErrorRed),
    UNBEKANNT("kein Datum", Color(0xFF757575));

    companion object {
        fun of(gueltigBisIso: String): FreenetStatus {
            val days = Dates.daysUntil(gueltigBisIso) ?: return UNBEKANNT
            return when {
                days < 0 -> ABGELAUFEN
                days <= 92 -> BALD   // Prüfpunkt: Gültigkeit > 3 Monate?
                else -> OK
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Anzeige "gültig bis" mit Ampelfarbe. */
@Composable
fun GueltigBisBadge(gueltigBisIso: String) {
    val status = FreenetStatus.of(gueltigBisIso)
    val text = if (gueltigBisIso.isBlank()) "Freenet: kein Datum"
    else "Freenet bis ${Dates.isoToGerman(gueltigBisIso)}"
    StatusBadge(text = text, color = status.color)
}
