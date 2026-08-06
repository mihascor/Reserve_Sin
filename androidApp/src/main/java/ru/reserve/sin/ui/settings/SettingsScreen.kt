package ru.reserve.sin.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.reserve.sin.data.remote.ServerConnectionChecker
import ru.reserve.sin.data.remote.ServerConnectionResult
import ru.reserve.sin.data.ReserveRepository
import ru.reserve.sin.data.settings.ServerSettings
import ru.reserve.sin.data.settings.ServerSettingsRepository

class SettingsViewModel(
    private val settingsRepository: ServerSettingsRepository,
    private val connectionChecker: ServerConnectionChecker,
    private val reserveRepository: ReserveRepository,
) : ViewModel() {
    val settings = settingsRepository.settings
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun save(serverUrl: String, token: String) {
        viewModelScope.launch {
            runCatching { settingsRepository.save(serverUrl, token) }
                .onSuccess { _message.value = "Настройки сохранены" }
                .onFailure { _message.value = it.message ?: "Не удалось сохранить настройки" }
        }
    }

    fun checkConnection() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val result = runCatching { connectionChecker.check(settings.serverUrl, settingsRepository.token()) }
                .getOrElse { ServerConnectionResult.Failed("Не удалось прочитать настройки") }
            _message.value = when (result) {
                ServerConnectionResult.Connected -> "Подключение к серверу подтверждено"
                ServerConnectionResult.Unauthorized -> "Сервер отклонил API-токен"
                is ServerConnectionResult.Failed -> result.message
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            val settings = settingsRepository.settings.first()
            val token = settingsRepository.token()
            if (token.isNullOrBlank()) {
                _message.value = "Сначала сохраните API-токен"
                return@launch
            }
            runCatching { reserveRepository.sync(settings.serverUrl, token) }
                .onSuccess { _message.value = "Синхронизировано: категорий ${it.categoriesSynced}, операций ${it.transactionsSynced}, получено изменений ${it.changesReceived}" }
                .onFailure { _message.value = it.message ?: "Не удалось синхронизировать данные" }
        }
    }
}

class SettingsViewModelFactory(
    private val settingsRepository: ServerSettingsRepository,
    private val connectionChecker: ServerConnectionChecker,
    private val reserveRepository: ReserveRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        check(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        return SettingsViewModel(settingsRepository, connectionChecker, reserveRepository) as T
    }
}

@Composable
fun SettingsRoute(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState(initial = ServerSettings("", false))
    val message by viewModel.message.collectAsState()
    SettingsScreen(settings, message, viewModel::save, viewModel::checkConnection, viewModel::syncNow, onBack)
}

@Composable
private fun SettingsScreen(
    settings: ServerSettings,
    message: String?,
    onSave: (String, String) -> Unit,
    onCheckConnection: () -> Unit,
    onSyncNow: () -> Unit,
    onBack: () -> Unit,
) {
    var serverUrl by remember(settings.serverUrl) { mutableStateOf(settings.serverUrl) }
    var token by remember { mutableStateOf("") }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Синхронизация", style = MaterialTheme.typography.headlineMedium)
                OutlinedButton(onClick = onBack) { Text("Назад") }
            }
        }
        item {
            Text(
                if (settings.hasToken) "API-токен сохранён и скрыт" else "API-токен ещё не сохранён",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it },
                label = { Text("HTTPS-адрес сервера") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text("Новый API-токен") },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(
                "Поле токена оставьте пустым, чтобы сохранить уже введённый токен.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        message?.let { item { Text(it, color = MaterialTheme.colorScheme.primary) } }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSave(serverUrl, token) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Сохранить")
                }
                OutlinedButton(onClick = onCheckConnection, modifier = Modifier.fillMaxWidth()) {
                    Text("Проверить подключение")
                }
                OutlinedButton(onClick = onSyncNow, modifier = Modifier.fillMaxWidth()) {
                    Text("Синхронизировать сейчас")
                }
            }
        }
    }
}
