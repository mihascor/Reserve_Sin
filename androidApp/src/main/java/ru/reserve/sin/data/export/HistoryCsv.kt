package ru.reserve.sin.data.export

import android.content.Context
import android.net.Uri
import java.io.OutputStreamWriter
import ru.reserve.sin.data.ReserveRepository
import ru.reserve.sin.data.local.ExportTransactionRow

class HistoryCsvExporter(
    private val context: Context,
    private val repository: ReserveRepository,
) {
    suspend fun export(uri: Uri) {
        val csv = repository.exportHistoryCsv()
        val stream = requireNotNull(context.contentResolver.openOutputStream(uri)) { "Не удалось открыть файл для экспорта" }
        stream.use { output -> OutputStreamWriter(output, Charsets.UTF_8).use { it.write(csv) } }
    }
}

internal fun historyCsv(transactions: List<ExportTransactionRow>): String = buildString {
    append('\uFEFF')
    appendCsvRow(listOf("Дата", "Категория", "Метка", "Сумма (руб.)", "Комментарий", "Группа", "Отменено", "Статус синхронизации"))
    transactions.forEach { transaction ->
        appendCsvRow(
            listOf(
                transaction.occurredAt.take(10),
                transaction.categoryName,
                transaction.labelName.orEmpty(),
                transaction.amountRub.toString(),
                transaction.comment.orEmpty(),
                transaction.batchId.orEmpty(),
                if (transaction.isCancelled) "да" else "нет",
                transaction.syncStatus.name,
            ),
        )
    }
}

private fun StringBuilder.appendCsvRow(values: List<String>) {
    append(values.joinToString(",") { value -> "\"${value.replace("\"", "\"\"")}\"" })
    append('\n')
}
