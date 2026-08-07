package ru.reserve.sin.ui.category

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import ru.reserve.sin.data.HistoryFilter
import ru.reserve.sin.data.ReserveRepository
import ru.reserve.sin.data.local.CategoryDetailsRow
import ru.reserve.sin.data.local.HistoryTransactionRow

data class CategoryPeriodTotals(val incomeRub: Long, val expenseRub: Long)

data class CategoryUiState(
    val category: CategoryDetailsRow? = null,
    val after: String = "",
    val before: String = "",
    val transactions: List<HistoryTransactionRow> = emptyList(),
)

class CategoryViewModel(
    private val categoryId: String,
    repository: ReserveRepository,
) : ViewModel() {
    private val period = MutableStateFlow("" to "")
    private val transactions = period.flatMapLatest { (after, before) ->
        repository.observeHistoryTransactions(
            HistoryFilter(
                after = after.ifBlank { null },
                before = before.ifBlank { null },
                categoryId = categoryId,
            ),
        )
    }

    val uiState: StateFlow<CategoryUiState> = combine(
        repository.observeCategoryDetails(categoryId),
        period,
        transactions,
    ) { category, (after, before), rows ->
        CategoryUiState(category = category, after = after, before = before, transactions = rows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryUiState())

    fun updatePeriod(after: String, before: String) {
        period.value = after.trim() to before.trim()
    }
}

class CategoryViewModelFactory(
    private val categoryId: String,
    private val repository: ReserveRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(CategoryViewModel::class.java))
        return CategoryViewModel(categoryId, repository) as T
    }
}

internal fun categoryPeriodTotals(transactions: List<HistoryTransactionRow>): CategoryPeriodTotals = CategoryPeriodTotals(
    incomeRub = transactions.filter { it.amountRub > 0 }.sumOf { it.amountRub },
    expenseRub = transactions.filter { it.amountRub < 0 }.sumOf { it.amountRub },
)

@Composable
fun CategoryRoute(viewModel: CategoryViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    CategoryScreen(state, viewModel::updatePeriod, onBack)
}

@Composable
private fun CategoryScreen(
    state: CategoryUiState,
    onUpdatePeriod: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val category = state.category
    val totals = categoryPeriodTotals(state.transactions)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(category?.name ?: "Категория", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }
        }
        if (category == null) {
            item { Text("Категория не найдена") }
        } else {
            item { CategorySummaryCard(category) }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Период", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = state.after,
                            onValueChange = { onUpdatePeriod(it, state.before) },
                            label = { Text("С даты (ГГГГ-ММ-ДД)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.before,
                            onValueChange = { onUpdatePeriod(state.after, it) },
                            label = { Text("По дату (ГГГГ-ММ-ДД)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Пополнения: ${formatRub(totals.incomeRub)}")
                        Text("Списания: ${formatRub(totals.expenseRub)}")
                    }
                }
            }
            item { Text("История", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold) }
            if (state.transactions.isEmpty()) {
                item { Text("Операций за выбранный период нет") }
            } else {
                items(state.transactions, key = { it.id }) { transaction ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(transaction.occurredAt.take(10))
                                Text(formatRub(transaction.amountRub), fontWeight = FontWeight.SemiBold)
                            }
                            transaction.labelName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                            transaction.comment?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySummaryCard(category: CategoryDetailsRow) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Текущий остаток", style = MaterialTheme.typography.titleMedium)
            Text(formatRub(category.balanceRub), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            category.targetAmountRub?.let { target ->
                val progress = if (target > 0) ((category.balanceRub.coerceAtLeast(0) * 100) / target).coerceAtMost(100).toInt() else null
                Text("Цель: ${formatRub(target)}${progress?.let { " · $it%" } ?: ""}")
                progress?.let { LinearProgressIndicator(progress = { it / 100f }, modifier = Modifier.fillMaxWidth()) }
            }
        }
    }
}

private fun formatRub(amount: Long): String = NumberFormat.getIntegerInstance(Locale("ru", "RU")).format(amount) + " ₽"
