package ru.reserve.sin.ui.categories

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.reserve.sin.data.ReserveRepository
import ru.reserve.sin.data.local.CategoryEntity
import ru.reserve.sin.ui.theme.ReserveSinPrimaryButtonBackground

private val CategoryActionButtonBorder = BorderStroke(1.dp, ReserveSinPrimaryButtonBackground)

class CategoriesViewModel(private val repository: ReserveRepository) : ViewModel() {
    val categories = repository.observeCategories()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun save(category: CategoryEntity?, name: String, targetText: String) {
        viewModelScope.launch {
            runCatching {
                val target = targetText.trim().ifEmpty { null }?.toLongOrNull()
                    ?: if (targetText.isBlank()) null else error("Цель должна быть целым числом")
                if (category == null) repository.createCategory(name, target) else repository.updateCategory(category, name, target)
            }.onSuccess {
                _message.value = if (category == null) "Категория создана" else "Категория изменена"
            }.onFailure { _message.value = it.message ?: "Не удалось сохранить категорию" }
        }
    }

    fun setArchived(category: CategoryEntity, archived: Boolean) {
        viewModelScope.launch {
            repository.setCategoryArchived(category, archived)
            _message.value = if (archived) "Категория архивирована" else "Категория восстановлена"
        }
    }

    fun delete(category: CategoryEntity) {
        viewModelScope.launch {
            runCatching { repository.deleteCategory(category) }
                .onSuccess { _message.value = "Категория удалена" }
                .onFailure { _message.value = it.message ?: "Не удалось удалить категорию" }
        }
    }
}

class CategoriesViewModelFactory(private val repository: ReserveRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(CategoriesViewModel::class.java))
        return CategoriesViewModel(repository) as T
    }
}

@Composable
fun CategoriesRoute(viewModel: CategoriesViewModel, onBack: () -> Unit) {
    val categories by viewModel.categories.collectAsState(initial = emptyList())
    val message by viewModel.message.collectAsState()
    CategoriesScreen(categories, message, viewModel::save, viewModel::setArchived, viewModel::delete, onBack)
}

@Composable
private fun CategoriesScreen(
    categories: List<CategoryEntity>,
    message: String?,
    onSave: (CategoryEntity?, String, String) -> Unit,
    onArchive: (CategoryEntity, Boolean) -> Unit,
    onDelete: (CategoryEntity) -> Unit,
    onBack: () -> Unit,
) {
    var editor by remember { mutableStateOf<CategoryEntity?>(null) }
    var createMode by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Категории", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = onBack, border = CategoryActionButtonBorder) { Text("Назад") }
            }
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item { Button(onClick = { createMode = true }) { Text("Новая категория") } }
        items(categories, key = { it.id }) { category ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(category.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (category.isArchived) "Архив" else "Активна",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                onClick = { editor = category },
                                border = CategoryActionButtonBorder,
                            ) { Text("Изменить") }
                            OutlinedButton(
                                onClick = { onArchive(category, !category.isArchived) },
                                border = CategoryActionButtonBorder,
                            ) {
                                Text(if (category.isArchived) "Из архива" else "В архив")
                            }
                            if (category.remoteId == null) {
                                OutlinedButton(
                                    onClick = { categoryToDelete = category },
                                    border = CategoryActionButtonBorder,
                                ) { Text("Удалить") }
                            }
                        }
                    }
                }
            }
        }
    }
    if (createMode || editor != null) {
        CategoryEditorDialog(
            category = editor,
            onDismiss = { createMode = false; editor = null },
            onSave = { name, target -> onSave(editor, name, target); createMode = false; editor = null },
        )
    }
    categoryToDelete?.let { category ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("Удалить категорию?") },
            text = { Text("«${category.name}» будет удалена без возможности восстановления.") },
            confirmButton = {
                Button(onClick = { onDelete(category); categoryToDelete = null }) { Text("Удалить") }
            },
            dismissButton = {
                OutlinedButton(onClick = { categoryToDelete = null }, border = CategoryActionButtonBorder) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun CategoryEditorDialog(
    category: CategoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var name by remember(category?.id) { mutableStateOf(category?.name.orEmpty()) }
    var target by remember(category?.id) { mutableStateOf(category?.targetAmountRub?.toString().orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (category == null) "Новая категория" else "Изменить категорию") },
        text = {
            Column {
                OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(target, { target = it }, label = { Text("Цель, ₽ (необязательно)") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onSave(name, target) }) { Text("Сохранить") } },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, border = CategoryActionButtonBorder) { Text("Отмена") }
        },
    )
}
