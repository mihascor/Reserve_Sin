package ru.reserve.sin.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import ru.reserve.sin.data.ReserveRepository
import ru.reserve.sin.data.local.HomeCategoryRow

data class HomeCategoryUi(
    val id: String,
    val name: String,
    val balanceRub: Long,
    val targetAmountRub: Long?,
    val progressPercent: Int?,
    val hasPendingChanges: Boolean,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val totalBalanceRub: Long = 0,
    val categories: List<HomeCategoryUi> = emptyList(),
    val pendingTransactionCount: Int = 0,
    val lastSuccessfulSyncAt: String? = null,
)

class HomeViewModel(repository: ReserveRepository) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = combine(
        repository.observeHomeCategories(),
        repository.observeTotalBalance(),
        repository.observePendingTransactionCount(),
        repository.observeLastSuccessfulSyncAt(),
    ) { categories, totalBalance, pendingCount, lastSuccessfulSyncAt ->
        HomeUiStateFactory.create(categories, totalBalance, pendingCount, lastSuccessfulSyncAt)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )
}

object HomeUiStateFactory {
    fun create(
        categories: List<HomeCategoryRow>,
        totalBalanceRub: Long,
        pendingTransactionCount: Int,
        lastSuccessfulSyncAt: String?,
    ): HomeUiState = HomeUiState(
        isLoading = false,
        totalBalanceRub = totalBalanceRub,
        categories = categories.map {
            HomeCategoryUi(
                id = it.id,
                name = it.name,
                balanceRub = it.balanceRub,
                targetAmountRub = it.targetAmountRub,
                progressPercent = it.targetAmountRub?.takeIf { target -> target > 0 }?.let { target ->
                    ((it.balanceRub.coerceAtLeast(0) * 100) / target).coerceAtMost(100).toInt()
                },
                hasPendingChanges = it.hasPendingChanges,
            )
        },
        pendingTransactionCount = pendingTransactionCount,
        lastSuccessfulSyncAt = lastSuccessfulSyncAt,
    )
}

class HomeViewModelFactory(
    private val repository: ReserveRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(HomeViewModel::class.java))
        return HomeViewModel(repository) as T
    }
}
