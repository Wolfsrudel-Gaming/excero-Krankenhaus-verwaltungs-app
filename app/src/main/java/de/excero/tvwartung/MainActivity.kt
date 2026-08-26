package de.excero.tvwartung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.screens.AppDrawerContent
import de.excero.tvwartung.ui.screens.BerichtScreen
import de.excero.tvwartung.ui.screens.DashboardScreen
import de.excero.tvwartung.ui.screens.ExportScreen
import de.excero.tvwartung.ui.screens.FreenetScreen
import de.excero.tvwartung.ui.screens.GlobalSearchScreen
import de.excero.tvwartung.ui.screens.HomeScreen
import de.excero.tvwartung.ui.screens.KiPruefungScreen
import de.excero.tvwartung.ui.screens.StatistikScreen
import de.excero.tvwartung.ui.screens.SucheScreen
import de.excero.tvwartung.ui.screens.PruefbogenScreen
import de.excero.tvwartung.ui.screens.RoomDetailScreen
import de.excero.tvwartung.ui.screens.RoomEditScreen
import de.excero.tvwartung.ui.screens.SettingsScreen
import de.excero.tvwartung.ui.screens.StundenzettelListeScreen
import de.excero.tvwartung.ui.screens.StundenzettelScreen
import de.excero.tvwartung.ui.screens.VerwaltungScreen
import de.excero.tvwartung.ui.theme.KKHTheme
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val DASHBOARD = "dashboard"
    const val HOME = "home"
    const val EXPORT = "export"
    const val SETTINGS = "settings"
    const val VERWALTUNG = "verwaltung"
    const val ZIMMER_NEU = "zimmer_neu"
    const val FREENET = "freenet"
    const val STATISTIK = "statistik"
    const val SUCHE = "suche"
    const val SUCHE_GLOBAL = "suche_global"
    const val KI_PRUEFUNG = "ki_pruefung"
    fun room(id: String) = "room/${URLEncoder.encode(id, "UTF-8")}"
    fun pruefbogen(id: String) = "pruefbogen/${URLEncoder.encode(id, "UTF-8")}"
    fun bericht(id: Long) = "bericht/$id"
    const val STUNDENZETTEL_LISTE = "stundenzettel_liste"
    fun stundenzettel(station: String) = "stundenzettel/${URLEncoder.encode(station, "UTF-8")}"
    fun stundenzettelEdit(id: Long) = "stundenzettel_edit/$id"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AppViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            KKHTheme(theme = settings.theme) {
                val navController = rememberNavController()
                val snackbarHostState = remember { SnackbarHostState() }
                val message by viewModel.message.collectAsState()

                LaunchedEffect(message) {
                    message?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.consumeMessage()
                    }
                }

                // 2.0-Beta: einschiebbares Hauptmenü mit gruppierter Navigation
                val drawerState = rememberDrawerState(DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val currentBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route

                // Zähler-Badges fürs Menü
                val rooms by viewModel.rooms.collectAsState()
                val kiAbweichungen by viewModel.kiAbweichungen.collectAsState()
                val alleZettel by viewModel.alleStundenzettel.collectAsState()
                val gepinnt by viewModel.gepinnteMenue.collectAsState()
                val badges = remember(rooms, kiAbweichungen, alleZettel) {
                    buildMap {
                        val freenetKritisch = rooms.count {
                            !it.inaktiv &&
                                de.excero.tvwartung.ui.screens.FreenetStatus.of(it.gueltigBis) ==
                                de.excero.tvwartung.ui.screens.FreenetStatus.ABGELAUFEN
                        }
                        if (freenetKritisch > 0) put(Routes.FREENET, freenetKritisch)
                        if (kiAbweichungen > 0) put(Routes.KI_PRUEFUNG, kiAbweichungen)
                        val offeneZettel = alleZettel.count { it.stunden.isBlank() }
                        if (offeneZettel > 0) put(Routes.STUNDENZETTEL_LISTE, offeneZettel)
                    }
                }

                // „Was ist neu"-Hinweis einmal pro Version
                var zeigeWasIstNeu by remember {
                    mutableStateOf(viewModel.wasIstNeuFaellig(BuildConfig.VERSION_NAME))
                }

                fun openDrawer() = scope.launch { drawerState.open() }
                fun navigateFromDrawer(route: String) {
                    scope.launch { drawerState.close() }
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }

                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        AppDrawerContent(
                            mitarbeiter = settings.mitarbeiter,
                            currentRoute = currentRoute,
                            badges = badges,
                            gepinnt = gepinnt,
                            onNavigate = ::navigateFromDrawer,
                            onTogglePin = { viewModel.toggleMenuePin(it) }
                        )
                    }
                ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.DASHBOARD,
                        modifier = Modifier.padding(padding)
                    ) {
                        composable(Routes.DASHBOARD) {
                            DashboardScreen(
                                viewModel = viewModel,
                                onMenuClick = { openDrawer() },
                                onRoomClick = { navController.navigate(Routes.room(it)) },
                                onFreenet = { navigateFromDrawer(Routes.FREENET) },
                                onArbeitszeit = { navigateFromDrawer(Routes.STUNDENZETTEL_LISTE) },
                                onVerwaltung = { navigateFromDrawer(Routes.VERWALTUNG) },
                                onKiPruefung = { navigateFromDrawer(Routes.KI_PRUEFUNG) },
                                onSuche = { navController.navigate(Routes.SUCHE_GLOBAL) },
                                onOffeneZimmer = {
                                    viewModel.setZimmerNurOffen(true)
                                    navigateFromDrawer(Routes.HOME)
                                }
                            )
                        }
                        composable(Routes.HOME) {
                            HomeScreen(
                                viewModel = viewModel,
                                onRoomClick = { navController.navigate(Routes.room(it)) },
                                onExportClick = { navController.navigate(Routes.EXPORT) },
                                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                                onStundenzettel = { navController.navigate(Routes.stundenzettel(it)) },
                                onVerwaltung = { navController.navigate(Routes.VERWALTUNG) },
                                onNeuesZimmer = { navController.navigate(Routes.ZIMMER_NEU) },
                                onFreenet = { navController.navigate(Routes.FREENET) },
                                onStatistik = { navController.navigate(Routes.STATISTIK) },
                                onSuche = { navController.navigate(Routes.SUCHE) },
                                onGlobalSuche = { navController.navigate(Routes.SUCHE_GLOBAL) }
                            )
                        }
                        composable(Routes.FREENET) {
                            FreenetScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onRoomClick = { navController.navigate(Routes.room(it)) }
                            )
                        }
                        composable(Routes.STATISTIK) {
                            StatistikScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.SUCHE) {
                            SucheScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpen = { navController.navigate(Routes.bericht(it)) }
                            )
                        }
                        composable(Routes.KI_PRUEFUNG) {
                            KiPruefungScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.SUCHE_GLOBAL) {
                            GlobalSearchScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onRoom = { navController.navigate(Routes.room(it)) },
                                onBericht = { navController.navigate(Routes.bericht(it)) },
                                onStundenzettel = { navController.navigate(Routes.stundenzettelEdit(it)) },
                                onVerwaltung = { navController.navigate(Routes.VERWALTUNG) }
                            )
                        }
                        composable("room/{roomId}") { entry ->
                            val roomId = URLDecoder.decode(
                                entry.arguments?.getString("roomId").orEmpty(), "UTF-8"
                            )
                            RoomDetailScreen(
                                viewModel = viewModel,
                                roomId = roomId,
                                onBack = { navController.popBackStack() },
                                onStartPruefbogen = { navController.navigate(Routes.pruefbogen(roomId)) },
                                onOpenBericht = { navController.navigate(Routes.bericht(it)) }
                            )
                        }
                        composable("pruefbogen/{roomId}") { entry ->
                            val roomId = URLDecoder.decode(
                                entry.arguments?.getString("roomId").orEmpty(), "UTF-8"
                            )
                            PruefbogenScreen(
                                viewModel = viewModel,
                                roomId = roomId,
                                onDone = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.EXPORT) {
                            ExportScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onStundenzettelListe = {
                                    navController.navigate(Routes.STUNDENZETTEL_LISTE)
                                }
                            )
                        }
                        composable("bericht/{inspectionId}") { entry ->
                            val inspectionId =
                                entry.arguments?.getString("inspectionId")?.toLongOrNull() ?: 0L
                            BerichtScreen(
                                viewModel = viewModel,
                                inspectionId = inspectionId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("stundenzettel/{station}") { entry ->
                            val station = URLDecoder.decode(
                                entry.arguments?.getString("station").orEmpty(), "UTF-8"
                            )
                            StundenzettelScreen(
                                viewModel = viewModel,
                                station = station,
                                zettelId = null,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable("stundenzettel_edit/{zettelId}") { entry ->
                            val zettelId =
                                entry.arguments?.getString("zettelId")?.toLongOrNull() ?: 0L
                            StundenzettelScreen(
                                viewModel = viewModel,
                                station = null,
                                zettelId = zettelId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.STUNDENZETTEL_LISTE) {
                            StundenzettelListeScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onOpen = { navController.navigate(Routes.stundenzettelEdit(it)) }
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.VERWALTUNG) {
                            VerwaltungScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.ZIMMER_NEU) {
                            RoomEditScreen(
                                viewModel = viewModel,
                                onBack = { navController.popBackStack() },
                                onCreated = { roomId ->
                                    navController.popBackStack()
                                    navController.navigate(Routes.room(roomId))
                                }
                            )
                        }
                    }
                }
                }

                if (zeigeWasIstNeu) {
                    WasIstNeuDialog(onDismiss = {
                        viewModel.wasIstNeuGesehen(BuildConfig.VERSION_NAME)
                        zeigeWasIstNeu = false
                    })
                }
            }
        }
    }
}

@Composable
private fun WasIstNeuDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Verstanden") }
        },
        title = { Text("Neu in Version 2.0") },
        text = {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                listOf(
                    "Neues Menü links (Symbol oben links oder von der Kante wischen)",
                    "Dashboard als Startseite mit Kennzahlen",
                    "Globale Suche über die Lupe oben",
                    "KI-Vorschläge direkt im Prüfbogen",
                    "Menüpunkte lange drücken zum Anpinnen"
                ).forEach { Text("•  $it", style = MaterialTheme.typography.bodyMedium) }
            }
        }
    )
}
