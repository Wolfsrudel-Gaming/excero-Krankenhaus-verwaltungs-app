package de.excero.tvwartung.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.ui.theme.ErrorRed
import de.excero.tvwartung.ui.theme.OkGreen
import de.excero.tvwartung.ui.theme.WarnAmber
import de.excero.tvwartung.util.Dates

object FreenetLinks {
    const val VERLAENGERN = "https://www.freenet.tv/guthaben-einloesen"
    const val AKTIVIEREN = "https://www.freenet.tv/aktivierung"

    fun open(context: Context, url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}

/**
 * Sortierung der Stationen nach Lage: A- und B-Station derselben Etage liegen
 * hintereinander auf dem Flur (A2, B2, A3, B3, A4, B4, A5, B5, …);
 * alle übrigen (C, D, E, F, Not, …) kommen alphabetisch ans Ende.
 */
private fun stationSortKey(station: String): Pair<Int, String> {
    val m = Regex("^([AB])(\\d+)$").find(station.trim().uppercase())
    return if (m != null) {
        val etage = m.groupValues[2].toInt()
        val seite = if (m.groupValues[1] == "A") 0 else 1
        0 to "%03d%d".format(etage, seite)
    } else {
        1 to station.trim().uppercase()
    }
}

val stationComparator: Comparator<String> =
    compareBy({ stationSortKey(it).first }, { stationSortKey(it).second })

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

/** Gelber Warnhinweis, z. B. bei doppelt vergebenen Seriennummern/IDs. */
@Composable
fun DuplicateWarning(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(WarnAmber.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            Icons.Default.Warning,
            contentDescription = null,
            tint = WarnAmber,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

/** TV-Marken-Feld mit Schnellauswahl bekannter Marken plus Freitext. */
@Composable
fun TvTypAuswahl(
    value: String,
    onValueChange: (String) -> Unit,
    bekannteTypen: List<String>,
    label: String = "TV-Typ / Marke",
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                IconButton(onClick = { expanded = true }) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Marke auswählen")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            bekannteTypen.forEach { typ ->
                DropdownMenuItem(
                    text = { Text(typ) },
                    onClick = {
                        onValueChange(typ)
                        expanded = false
                    }
                )
            }
        }
    }
}
