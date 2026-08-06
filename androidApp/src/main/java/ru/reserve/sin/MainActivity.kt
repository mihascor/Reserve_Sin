package ru.reserve.sin

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.reserve.sin.data.ReserveRepository
import ru.reserve.sin.data.local.ReserveDatabase
import ru.reserve.sin.ui.home.HomeRoute
import ru.reserve.sin.ui.home.HomeViewModel
import ru.reserve.sin.ui.home.HomeViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = ReserveRepository(ReserveDatabase.create(applicationContext))
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val homeViewModel: HomeViewModel = viewModel(
                        factory = HomeViewModelFactory(repository),
                    )
                    HomeRoute(homeViewModel)
                }
            }
        }
    }
}
