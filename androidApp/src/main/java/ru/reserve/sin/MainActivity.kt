package ru.reserve.sin

import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import ru.reserve.sin.data.ReserveRepository
import ru.reserve.sin.data.local.ReserveDatabase
import ru.reserve.sin.data.remote.ServerConnectionChecker
import ru.reserve.sin.data.settings.ServerSettingsRepository
import ru.reserve.sin.ui.home.HomeRoute
import ru.reserve.sin.ui.home.HomeViewModel
import ru.reserve.sin.ui.home.HomeViewModelFactory
import ru.reserve.sin.ui.categories.CategoriesRoute
import ru.reserve.sin.ui.categories.CategoriesViewModel
import ru.reserve.sin.ui.categories.CategoriesViewModelFactory
import ru.reserve.sin.ui.operation.OperationRoute
import ru.reserve.sin.ui.operation.OperationViewModel
import ru.reserve.sin.ui.operation.OperationViewModelFactory
import ru.reserve.sin.ui.settings.SettingsRoute
import ru.reserve.sin.ui.settings.SettingsViewModel
import ru.reserve.sin.ui.settings.SettingsViewModelFactory
import ru.reserve.sin.ui.history.HistoryRoute
import ru.reserve.sin.ui.history.HistoryViewModel
import ru.reserve.sin.ui.history.HistoryViewModelFactory
import ru.reserve.sin.ui.theme.ReserveSinTheme
import ru.reserve.sin.ui.labels.LabelsRoute
import ru.reserve.sin.ui.labels.LabelsViewModel
import ru.reserve.sin.ui.labels.LabelsViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(20, 18, 24)
        window.navigationBarColor = Color.rgb(20, 18, 24)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        val repository = ReserveRepository(ReserveDatabase.create(applicationContext))
        val settingsRepository = ServerSettingsRepository(applicationContext)
        setContent {
            ReserveSinTheme {
                Surface {
                    var route by remember { mutableStateOf("home") }
                    when (route) {
                        "categories" -> {
                            val categoriesViewModel: CategoriesViewModel = viewModel(
                                factory = CategoriesViewModelFactory(repository),
                            )
                            CategoriesRoute(categoriesViewModel) { route = "home" }
                        }
                        "operation" -> {
                            val operationViewModel: OperationViewModel = viewModel(
                                factory = OperationViewModelFactory(repository),
                            )
                            OperationRoute(operationViewModel) { route = "home" }
                        }
                        "settings" -> {
                            val settingsViewModel: SettingsViewModel = viewModel(
                                factory = SettingsViewModelFactory(settingsRepository, ServerConnectionChecker(), repository),
                            )
                            SettingsRoute(settingsViewModel, { route = "home" }, { route = "labels" })
                        }
                        "history" -> {
                            val historyViewModel: HistoryViewModel = viewModel(
                                factory = HistoryViewModelFactory(repository),
                            )
                            HistoryRoute(historyViewModel) { route = "home" }
                        }
                        "labels" -> { val labelsViewModel: LabelsViewModel = viewModel(factory = LabelsViewModelFactory(repository)); LabelsRoute(labelsViewModel) { route = "settings" } }
                        else -> {
                            val homeViewModel: HomeViewModel = viewModel(
                                factory = HomeViewModelFactory(repository),
                            )
                            HomeRoute(
                                viewModel = homeViewModel,
                                onManageCategories = { route = "categories" },
                                onAddOperation = { route = "operation" },
                                onOpenHistory = { route = "history" },
                                onOpenSettings = { route = "settings" },
                            )
                        }
                    }
                }
            }
        }
    }
}
