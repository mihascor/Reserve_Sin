package ru.reserve.sin.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.util.Locale
import ru.reserve.sin.ui.theme.ReserveSinPrimaryButtonBackground
import ru.reserve.sin.ui.theme.ReserveSinPrimaryButtonText

@Composable
fun HomeRoute(
    viewModel: HomeViewModel,
    onManageCategories: () -> Unit,
    onAddOperation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    HomeScreen(state, onManageCategories, onAddOperation, onOpenSettings)
}

@Composable
fun HomeScreen(
    state: HomeUiState,
    onManageCategories: () -> Unit,
    onAddOperation: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Накопления",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            TotalBalanceCard(
                totalBalanceRub = state.totalBalanceRub,
                pendingTransactionCount = state.pendingTransactionCount,
                lastSuccessfulSyncAt = state.lastSuccessfulSyncAt,
            )
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HomeActionButton(text = "Категории", onClick = onManageCategories)
                HomeActionButton(
                    text = "Добавить операцию",
                    onClick = onAddOperation,
                    enabled = state.categories.isNotEmpty(),
                )
                HomeActionButton(text = "Настройки", onClick = onOpenSettings)
            }
        }
        if (state.categories.isEmpty()) {
            item { EmptyCategoriesCard(onManageCategories) }
        } else {
            item {
                Text(
                    text = "Категории",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            items(state.categories, key = { it.id }) { category ->
                CategoryCard(category)
            }
        }
    }
}

@Composable
private fun HomeActionButton(text: String, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(
                color = if (enabled) ReserveSinPrimaryButtonBackground else MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(24.dp),
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (enabled) ReserveSinPrimaryButtonText else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun TotalBalanceCard(
    totalBalanceRub: Long,
    pendingTransactionCount: Int,
    lastSuccessfulSyncAt: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Всего накоплений", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                text = formatRub(totalBalanceRub),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    pendingTransactionCount > 0 -> "Ожидают синхронизации: $pendingTransactionCount"
                    lastSuccessfulSyncAt != null -> "Последняя синхронизация: $lastSuccessfulSyncAt"
                    else -> "Синхронизация ещё не настроена"
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EmptyCategoriesCard(onManageCategories: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("Здесь появятся категории", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Создайте первую категорию, чтобы начать вести локальный учёт.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onManageCategories) { Text("Создать категорию") }
        }
    }
}

@Composable
private fun CategoryCard(category: HomeCategoryUi) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = category.name,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (category.hasPendingChanges) {
                    Spacer(Modifier.width(8.dp))
                    Text("Не синхр.", style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(formatRub(category.balanceRub), style = MaterialTheme.typography.headlineSmall)
            category.targetAmountRub?.let { target ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Цель: ${formatRub(target)}${category.progressPercent?.let { " · $it%" } ?: ""}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                category.progressPercent?.let { progress ->
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun formatRub(amount: Long): String = NumberFormat.getIntegerInstance(Locale("ru", "RU"))
    .format(amount) + " ₽"
