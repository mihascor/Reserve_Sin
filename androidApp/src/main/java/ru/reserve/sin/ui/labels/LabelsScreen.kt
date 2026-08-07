package ru.reserve.sin.ui.labels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.reserve.sin.data.ReserveRepository
import ru.reserve.sin.data.local.LabelEntity

class LabelsViewModel(private val repository: ReserveRepository) : ViewModel() {
    val labels = repository.observeLabels()
    fun save(label: LabelEntity?, name: String, archived: Boolean) = viewModelScope.launch {
        if (label == null) repository.createLabel(name) else repository.updateLabel(label, name, archived)
    }
}
class LabelsViewModelFactory(private val repository: ReserveRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>): T = LabelsViewModel(repository) as T
}
@Composable fun LabelsRoute(viewModel: LabelsViewModel, onBack: () -> Unit) {
    val labels by viewModel.labels.collectAsState(emptyList())
    LabelsScreen(labels, viewModel::save, onBack)
}
@Composable private fun LabelsScreen(labels: List<LabelEntity>, onSave: (LabelEntity?, String, Boolean) -> Unit, onBack: () -> Unit) {
    var editing by remember { mutableStateOf<LabelEntity?>(null) }; var creating by remember { mutableStateOf(false) }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Метки", style = MaterialTheme.typography.headlineMedium); OutlinedButton(onClick = onBack) { Text("Назад") } } }
        item { Button(onClick = { creating = true }) { Text("Новая метка") } }
        items(labels, key = { it.id }) { label -> Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text(label.name, style = MaterialTheme.typography.titleMedium); Text(if (label.isArchived) "Архив" else "Активна") }; OutlinedButton(onClick = { editing = label }) { Text("Изменить") } } } }
    }
    if (creating || editing != null) LabelEditor(editing, { creating = false; editing = null }) { name, archived -> onSave(editing, name, archived); creating = false; editing = null }
}
@Composable private fun LabelEditor(label: LabelEntity?, dismiss: () -> Unit, save: (String, Boolean) -> Unit) {
    var name by remember(label?.id) { mutableStateOf(label?.name.orEmpty()) }; var archived by remember(label?.id) { mutableStateOf(label?.isArchived ?: false) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (label == null) "Новая метка" else "Изменить метку") }, text = { Column { OutlinedTextField(name, { name = it }, label = { Text("Название") }, singleLine = true); if (label != null) Row { Checkbox(archived, { archived = it }); Text("В архив") } } }, confirmButton = { Button(onClick = { save(name, archived) }) { Text("Сохранить") } }, dismissButton = { OutlinedButton(onClick = dismiss) { Text("Отмена") } })
}
