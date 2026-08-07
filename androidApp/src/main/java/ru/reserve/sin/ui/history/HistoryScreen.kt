package ru.reserve.sin.ui.history

import java.text.NumberFormat
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import ru.reserve.sin.data.HistoryDirection
import ru.reserve.sin.data.HistoryFilter
import ru.reserve.sin.data.ReserveRepository
import ru.reserve.sin.data.local.CategoryEntity
import ru.reserve.sin.data.local.LabelEntity
import ru.reserve.sin.data.local.HistoryTransactionRow
import ru.reserve.sin.data.local.SyncStatus

data class HistoryGroup(
    val id: String,
    val occurredAt: String,
    val labelName: String?,
    val comment: String?,
    val transactions: List<HistoryTransactionRow>,
) {
    val totalAmountRub: Long get() = transactions.sumOf { it.amountRub }
    val isCancelled: Boolean get() = transactions.all { it.isCancelled }
    val hasUnsyncedChanges: Boolean get() = transactions.any { it.syncStatus != SyncStatus.SYNCED }
}

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter(),
    val categories: List<CategoryEntity> = emptyList(),
    val labels: List<LabelEntity> = emptyList(),
    val groups: List<HistoryGroup> = emptyList(),
)

class HistoryViewModel(private val repository: ReserveRepository) : ViewModel() {
    private val filter = MutableStateFlow(HistoryFilter())
    private val groups = filter.flatMapLatest { currentFilter ->
        repository.observeHistoryTransactions(currentFilter)
    }

    val uiState: StateFlow<HistoryUiState> = combine(
        filter,
        repository.observeCategories(),
        repository.observeLabels(),
        groups,
    ) { currentFilter, categories, labels, transactions ->
        HistoryUiState(
            filter = currentFilter,
            categories = categories,
            labels = labels,
            groups = historyGroups(transactions),
        )
    }.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun updateFilter(transform: (HistoryFilter) -> HistoryFilter) {
        filter.value = transform(filter.value)
    }
}

class HistoryViewModelFactory(private val repository: ReserveRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(HistoryViewModel::class.java))
        return HistoryViewModel(repository) as T
    }
}

internal fun historyGroups(transactions: List<HistoryTransactionRow>): List<HistoryGroup> =
    transactions.groupBy { it.batchId ?: it.id }.map { (id, rows) ->
        HistoryGroup(
            id = id,
            occurredAt = rows.maxOf { it.occurredAt },
            labelName = rows.firstNotNullOfOrNull { it.labelName },
            comment = rows.firstNotNullOfOrNull { it.comment },
            transactions = rows,
        )
    }.sortedByDescending { it.occurredAt }

@Composable
fun HistoryRoute(viewModel: HistoryViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    HistoryScreen(state, viewModel::updateFilter, onBack)
}

@Composable
private fun HistoryScreen(
    state: HistoryUiState,
    onUpdateFilter: ((HistoryFilter) -> HistoryFilter) -> Unit,
    onBack: () -> Unit,
) {
    var selectedGroup by remember { mutableStateOf<HistoryGroup?>(null) }
    selectedGroup?.let { group ->
        HistoryDetailsScreen(group, onBack = { selectedGroup = null })
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("История", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }
        }
        item { HistoryFilters(state, onUpdateFilter) }
        if (state.groups.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text("Операций по выбранным фильтрам пока нет", modifier = Modifier.padding(16.dp))
                }
            }
        } else {
            items(state.groups, key = { it.id }) { group ->
                HistoryGroupCard(group, onClick = { selectedGroup = group })
            }
        }
    }
}

@Composable
private fun HistoryFilters(state: HistoryUiState, onUpdateFilter: ((HistoryFilter) -> HistoryFilter) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { expanded = !expanded }) {
                Text(if (expanded) "Скрыть фильтры" else "Фильтры")
            }
            if (expanded) {
                OutlinedTextField(
                    value = state.filter.after.orEmpty(),
                    onValueChange = { value -> onUpdateFilter { it.copy(after = value.trim().ifEmpty { null }) } },
                    label = { Text("С даты (ГГГГ-ММ-ДД)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.filter.before.orEmpty(),
                    onValueChange = { value -> onUpdateFilter { it.copy(before = value.trim().ifEmpty { null }) } },
                    label = { Text("По дату (ГГГГ-ММ-ДД)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterMenu(
                    title = state.categories.firstOrNull { it.id == state.filter.categoryId }?.name ?: "Все категории",
                    entries = listOf(null to "Все категории") + state.categories.map { it.id to it.name },
                    onSelect = { id -> onUpdateFilter { it.copy(categoryId = id) } },
                )
                FilterMenu(
                    title = state.labels.firstOrNull { it.id == state.filter.labelId }?.name ?: "Все метки",
                    entries = listOf(null to "Все метки") + state.labels.map { it.id to it.name },
                    onSelect = { id -> onUpdateFilter { it.copy(labelId = id) } },
                )
                FilterMenu(
                    title = when (state.filter.direction) {
                        HistoryDirection.INCOME -> "Только пополнения"
                        HistoryDirection.EXPENSE -> "Только списания"
                        null -> "Все типы"
                    },
                    entries = listOf(null to "Все типы", "INCOME" to "Только пополнения", "EXPENSE" to "Только списания"),
                    onSelect = { direction -> onUpdateFilter { it.copy(direction = direction?.let(HistoryDirection::valueOf)) } },
                )
                FilterCheck("Показывать отменённые", state.filter.includeCancelled) {
                    onUpdateFilter { filter -> filter.copy(includeCancelled = !filter.includeCancelled) }
                }
                FilterCheck("Только не синхронизированные", state.filter.onlyUnsynced) {
                    onUpdateFilter { filter -> filter.copy(onlyUnsynced = !filter.onlyUnsynced) }
                }
            }
        }
    }
}

@Composable
private fun FilterMenu(title: String, entries: List<Pair<String?, String>>, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { onSelect(id); expanded = false })
            }
        }
    }
}

@Composable
private fun FilterCheck(text: String, checked: Boolean, onCheckedChange: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = { onCheckedChange() })
        Text(text)
    }
}

@Composable
private fun HistoryGroupCard(group: HistoryGroup, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(group.occurredAt.take(10), fontWeight = FontWeight.SemiBold)
                Text(formatRub(group.totalAmountRub), style = MaterialTheme.typography.titleMedium)
            }
            group.labelName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            group.comment?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            Text(
                if (group.transactions.size == 1) group.transactions.single().categoryName
                else "${group.transactions.size} строк(и) в группе",
                style = MaterialTheme.typography.bodyMedium,
            )
            when {
                group.isCancelled -> Text("Отменено", color = MaterialTheme.colorScheme.error)
                group.hasUnsyncedChanges -> Text("Не синхронизировано", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun HistoryDetailsScreen(group: HistoryGroup, onBack: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Операция", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Дата: ${group.occurredAt.take(10)}")
                    group.labelName?.let { Text("Метка: $it") }
                    group.comment?.let { Text("Комментарий: $it") }
                    Text("Итог: ${formatRub(group.totalAmountRub)}", fontWeight = FontWeight.SemiBold)
                    if (group.isCancelled) Text("Операция отменена", color = MaterialTheme.colorScheme.error)
                    else if (group.hasUnsyncedChanges) Text("Есть не синхронизированные изменения", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        items(group.transactions, key = { it.id }) { transaction ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(transaction.categoryName, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Text(formatRub(transaction.amountRub), fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun formatRub(amount: Long): String = NumberFormat.getIntegerInstance(Locale("ru", "RU")).format(amount) + " ₽"
