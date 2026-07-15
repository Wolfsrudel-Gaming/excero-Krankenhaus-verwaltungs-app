package de.excero.tvwartung

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.excero.tvwartung.ui.AppViewModel
import de.excero.tvwartung.ui.screens.BerichtScreen
import de.excero.tvwartung.ui.screens.ExportScreen
import de.excero.tvwartung.ui.screens.HomeScreen
import de.excero.tvwartung.ui.screens.PruefbogenScreen
import de.excero.tvwartung.ui.screens.RoomDetailScreen
import de.excero.tvwartung.ui.screens.RoomEditScreen
import de.excero.tvwartung.ui.screens.SettingsScreen
import de.excero.tvwartung.ui.screens.StundenzettelScreen
import de.excero.tvwartung.ui.screens.VerwaltungScreen
import de.excero.tvwartung.ui.theme.KKHTheme
import java.net.URLDecoder
import java.net.URLEncoder

object Routes {
    const val HOME = "home"
    const val EXPORT = "export"
    const val SETTINGS = "settings"
    const val VERWALTUNG = "verwaltung"
    const val ZIMMER_NEU = "zimmer_neu"
    fun room(id: String) = "room/${URLEncoder.encode(id, "UTF-8")}"
    fun pruefbogen(id: String) = "pruefbogen/${URLEncoder.encode(id, "UTF-8")}"
    fun bericht(id: Long) = "bericht/$id"
    fun stundenzettel(station: String) = "stundenzettel/${URLEncoder.encode(station, "UTF-8")}"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KKHTheme {
                val navController = rememberNavController()
                val viewModel: AppViewModel = viewModel()
                val snackbarHostState = remember { SnackbarHostState() }
                val message by viewModel.message.collectAsState()

                LaunchedEffect(message) {
                    message?.let {
                        snackbarHostState.showSnackbar(it)
                        viewModel.consumeMessage()
                    }
                }

                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) }
                ) { padding ->
                    NavHost(
                        navController = navController,
                        startDestination = Routes.HOME,
                        modifier = Modifier.padding(padding)
                    ) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                viewModel = viewModel,
                                onRoomClick = { navController.navigate(Routes.room(it)) },
                                onExportClick = { navController.navigate(Routes.EXPORT) },
                                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                                onStundenzettel = { navController.navigate(Routes.stundenzettel(it)) },
                                onVerwaltung = { navController.navigate(Routes.VERWALTUNG) },
                                onNeuesZimmer = { navController.navigate(Routes.ZIMMER_NEU) }
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
                                onBack = { navController.popBackStack() }
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
                                onBack = { navController.popBackStack() }
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
        }
    }
}
