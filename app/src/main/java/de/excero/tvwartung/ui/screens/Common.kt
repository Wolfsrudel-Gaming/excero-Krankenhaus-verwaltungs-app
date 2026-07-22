package de.excero.tvwartung.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.excero.tvwartung.ui.AppViewModel
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

/**
 * Einheitlicher Leer-Zustand für leere Listen (großes Icon + Titel + Hinweis),
 * damit alle Screens gleich aufgeräumt wirken statt „einfach leer".
 */
@Composable
fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    titel: String,
    hinweis: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp)
        )
        Text(
            titel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (hinweis != null) {
            Text(
                hinweis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Skeleton-Platzhalter mit sanftem Pulsieren – statt eines Spinners, während
 * Daten geladen werden (wirkt ruhiger und moderner).
 */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, corner: Int = 8) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            animation = tween(750),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )
    Box(
        modifier
            .clip(RoundedCornerShape(corner.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
    )
}

/** Mehrere Skeleton-Kartenzeilen als Ladeanzeige für Listen. */
@Composable
fun SkeletonList(anzahl: Int = 6, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(anzahl) {
            SkeletonBox(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                corner = 12
            )
        }
    }
}

/** Technische Werte (Seriennummer, Freenet-ID) in Monospace – erleichtert das Ablesen. */
@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp
) {
    Text(
        text = text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSize,
        color = color,
        style = MaterialTheme.typography.bodyMedium
    )
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

/**
 * Sync-Status als "Sync:" + farbiger Punkt statt einer Meldung bei jedem Abgleich
 * (grau = nie, gelb = läuft, grün = ok, rot = Fehler). Tippen löst Sync aus.
 */
@Composable
fun SyncStatusIndicator(viewModel: AppViewModel, modifier: Modifier = Modifier) {
    val status by viewModel.syncStatus.collectAsState()
    val farbe = when (status) {
        AppViewModel.SyncStatus.NIE -> MaterialTheme.colorScheme.outline
        AppViewModel.SyncStatus.LAEUFT -> WarnAmber
        AppViewModel.SyncStatus.OK -> OkGreen
        AppViewModel.SyncStatus.FEHLER -> ErrorRed
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clickable { viewModel.syncNow() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            "Sync:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(farbe)
        )
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
