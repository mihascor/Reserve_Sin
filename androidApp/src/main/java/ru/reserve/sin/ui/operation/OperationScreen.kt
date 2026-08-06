package ru.reserve.sin.ui.operation

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.reserve.sin.data.OperationLine
import ru.reserve.sin.data.ReserveRepository
import ru.reserve.sin.data.local.CategoryEntity

data class OperationDraftRow(
    val categoryId: String = "",
    val amountText: String = "",
    val isIncome: Boolean = true,
)

class OperationViewModel(private val repository: ReserveRepository) : ViewModel() {
    val categories = repository.observeActiveCategories()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun save(date: String, comment: String, rows: List<OperationDraftRow>) {
        viewModelScope.launch {
            runCatching {
                repository.createTransactions(
                    date = date,
                    comment = comment,
                    rows = rows.map { row ->
                        val amount = row.amountText.trim().toLongOrNull()
                            ?: error("Сумма должна быть целым числом")
                        OperationLine(row.categoryId, if (row.isIncome) amount else -amount)
                    },
                )
            }.onSuccess {
                _message.value = "Операция сохранена локально и ожидает синхронизации"
            }.onFailure { _message.value = it.message ?: "Не удалось сохранить операцию" }
        }
    }
}

class OperationViewModelFactory(private val repository: ReserveRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(OperationViewModel::class.java))
        return OperationViewModel(repository) as T
    }
}

@Composable
fun OperationRoute(viewModel: OperationViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val message by viewModel.message.collectAsState()
    OperationScreen(categories, message, viewModel::save, onBack)
}

@Composable
private fun OperationScreen(
    categories: List<CategoryEntity>,
    message: String?,
    onSave: (String, String, List<OperationDraftRow>) -> Unit,
    onBack: () -> Unit,
) {
    var date by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
    var comment by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf(listOf(OperationDraftRow())) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Новая операция", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item {
            OutlinedTextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Дата (ГГГГ-ММ-ДД)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = comment,
                onValueChange = { comment = it },
                label = { Text("Комментарий (необязательно)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        itemsIndexed(rows) { index, row ->
            OperationLineEditor(
                row = row,
                categories = categories,
                canRemove = rows.size > 1,
                onUpdate = { updated -> rows = rows.toMutableList().also { it[index] = updated } },
                onRemove = { rows = rows.toMutableList().also { it.removeAt(index) } },
            )
        }
        item {
            OutlinedButton(onClick = { rows = rows + OperationDraftRow() }) { Text("Добавить строку") }
        }
        item {
            Button(
                onClick = { onSave(date, comment, rows) },
                enabled = categories.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Сохранить локально") }
        }
    }
}

@Composable
private fun OperationLineEditor(
    row: OperationDraftRow,
    categories: List<CategoryEntity>,
    canRemove: Boolean,
    onUpdate: (OperationDraftRow) -> Unit,
    onRemove: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = categories.firstOrNull { it.id == row.categoryId }?.name ?: "Выберите категорию"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedName) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                categories.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.name) },
                        onClick = { onUpdate(row.copy(categoryId = category.id)); expanded = false },
                    )
                }
            }
            OutlinedTextField(
                value = row.amountText,
                onValueChange = { onUpdate(row.copy(amountText = it)) },
                label = { Text("Сумма, ₽") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onUpdate(row.copy(isIncome = true)) }, enabled = !row.isIncome) { Text("Пополнение") }
                OutlinedButton(onClick = { onUpdate(row.copy(isIncome = false)) }, enabled = row.isIncome) { Text("Списание") }
                if (canRemove) OutlinedButton(onClick = onRemove) { Text("Удалить") }
            }
        }
    }
}
