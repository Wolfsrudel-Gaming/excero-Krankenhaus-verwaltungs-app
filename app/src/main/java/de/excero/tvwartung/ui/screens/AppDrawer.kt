package de.excero.tvwartung.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FindInPage
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.excero.tvwartung.Routes

/** Ein Eintrag im Menü (2.0-Beta: Gruppen mit Überschriften, aktive Seite hervorgehoben). */
data class DrawerItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

data class DrawerGroup(val titel: String, val eintraege: List<DrawerItem>)

val drawerGroups = listOf(
    DrawerGroup(
        "Zimmer & Stationen",
        listOf(
            DrawerItem(Routes.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
            DrawerItem(Routes.HOME, "Zimmerliste", Icons.Default.MeetingRoom)
        )
    ),
    DrawerGroup(
        "Berichte & Auswertung",
        listOf(
            DrawerItem(Routes.FREENET, "Freenet-Ablauf", Icons.Default.Timer),
            DrawerItem(Routes.STATISTIK, "Statistik", Icons.Default.QueryStats),
            DrawerItem(Routes.SUCHE, "Berichte & Papierkorb", Icons.Default.FindInPage)
        )
    ),
    DrawerGroup(
        "Arbeitszeit",
        listOf(
            DrawerItem(Routes.STUNDENZETTEL_LISTE, "Stundenzettel", Icons.AutoMirrored.Filled.Assignment)
        )
    ),
    DrawerGroup(
        "Verwaltung",
        listOf(
            DrawerItem(Routes.VERWALTUNG, "Material & Prüfpunkte", Icons.Default.Inventory2)
        )
    ),
    DrawerGroup(
        "Einstellungen",
        listOf(
            DrawerItem(Routes.SETTINGS, "Einstellungen", Icons.Default.Settings)
        )
    )
)

/** Einschiebbares Hauptmenü (2.0-Beta): Logo + Mitarbeitername, gruppierte Navigation. */
@Composable
fun AppDrawerContent(
    mitarbeiter: String,
    currentRoute: String?,
    onNavigate: (String) -> Unit
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
            drawerGroups.forEach { gruppe ->
                Text(
                    gruppe.titel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp)
                )
                gruppe.eintraege.forEach { item ->
                    NavigationDrawerItem(
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                        selected = currentRoute == item.route,
                        onClick = { onNavigate(item.route) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}
