package de.excero.tvwartung.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.MeetingRoom
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.QueryStats
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.Badge
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.Routes

/** Ein Eintrag im Menü (2.0: Gruppen mit Überschriften, aktive Seite hervorgehoben). */
data class DrawerItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

data class DrawerGroup(val titel: String, val eintraege: List<DrawerItem>)

val drawerGroups = listOf(
    DrawerGroup(
        "Zimmer & Stationen",
        listOf(
            DrawerItem(Routes.DASHBOARD, "Dashboard", Icons.Outlined.Dashboard),
            DrawerItem(Routes.HOME, "Zimmerliste", Icons.Outlined.MeetingRoom)
        )
    ),
    DrawerGroup(
        "Berichte & Auswertung",
        listOf(
            DrawerItem(Routes.FREENET, "Freenet-Ablauf", Icons.Outlined.Timer),
            DrawerItem(Routes.STATISTIK, "Statistik", Icons.Outlined.QueryStats),
            DrawerItem(Routes.PROFIL, "Mein Profil", Icons.Outlined.Person),
            DrawerItem(Routes.SUCHE, "Berichte & Papierkorb", Icons.Outlined.FindInPage),
            DrawerItem(Routes.KI_PRUEFUNG, "KI-Prüfung", Icons.Outlined.AutoAwesome)
        )
    ),
    DrawerGroup(
        "Arbeitszeit",
        listOf(
            DrawerItem(Routes.STUNDENZETTEL_LISTE, "Stundenzettel", Icons.AutoMirrored.Outlined.Assignment)
        )
    ),
    DrawerGroup(
        "Verwaltung",
        listOf(
            DrawerItem(Routes.VERWALTUNG, "Material & Prüfpunkte", Icons.Outlined.Inventory2)
        )
    ),
    DrawerGroup(
        "Einstellungen",
        listOf(
            DrawerItem(Routes.SETTINGS, "Einstellungen", Icons.Outlined.Settings)
        )
    )
)

private val alleDrawerItems = drawerGroups.flatMap { it.eintraege }

/**
 * Einschiebbares Hauptmenü: Logo + Mitarbeitername, gruppierte Navigation,
 * Zähler-Badges (z. B. Freenet/KI), Anpinnen häufiger Seiten per Long-Press.
 */
@Composable
fun AppDrawerContent(
    mitarbeiter: String,
    currentRoute: String?,
    badges: Map<String, Int>,
    gepinnt: List<String>,
    onNavigate: (String) -> Unit,
    onTogglePin: (String) -> Unit
) {
    ModalDrawerSheet {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text(
                    "EXCERO",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "TV-Wartung KKH",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (mitarbeiter.isNotBlank()) {
                    Text(
                        mitarbeiter,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            HorizontalDivider()

            // Angepinnte Seiten zuerst
            val pinItems = gepinnt.mapNotNull { route -> alleDrawerItems.firstOrNull { it.route == route } }
            if (pinItems.isNotEmpty()) {
                GruppenTitel("Angepinnt")
                pinItems.forEach { item ->
                    DrawerRow(item, currentRoute == item.route, badges[item.route] ?: 0,
                        gepinnt = true, onNavigate, onTogglePin)
                }
            }

            drawerGroups.forEach { gruppe ->
                GruppenTitel(gruppe.titel)
                gruppe.eintraege.forEach { item ->
                    DrawerRow(item, currentRoute == item.route, badges[item.route] ?: 0,
                        gepinnt = item.route in gepinnt, onNavigate, onTogglePin)
                }
            }
            Text(
                "Tipp: Menüpunkt lange drücken, um ihn oben anzupinnen.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, top = 12.dp, bottom = 16.dp, end = 16.dp)
            )
        }
    }
}

@Composable
private fun GruppenTitel(titel: String) {
    Text(
        titel,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp)
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerRow(
    item: DrawerItem,
    selected: Boolean,
    badge: Int,
    gepinnt: Boolean,
    onNavigate: (String) -> Unit,
    onTogglePin: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .combinedClickable(
                onClick = { onNavigate(item.route) },
                onLongClick = { onTogglePin(item.route) }
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            item.icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(vertical = 14.dp)
                .size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(
            item.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (gepinnt) {
            Icon(
                Icons.Outlined.PushPin,
                contentDescription = "angepinnt",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(8.dp))
        }
        if (badge > 0) {
            Badge { Text("$badge") }
        }
    }
}
