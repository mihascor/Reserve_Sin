package ru.reserve.sin.data.export

import org.junit.Assert.assertEquals
import org.junit.Test
import ru.reserve.sin.data.local.ExportTransactionRow
import ru.reserve.sin.data.local.SyncStatus

class HistoryCsvTest {
    @Test
    fun `escapes values and includes synchronization state`() {
        val csv = historyCsv(
            listOf(
                ExportTransactionRow("2026-08-07T00:00:00Z", "Подушка", null, -500, "Перевод, \"наличные\"", "batch", false, SyncStatus.PENDING),
            ),
        )

        assertEquals(
            "\uFEFF\"Дата\",\"Категория\",\"Метка\",\"Сумма (руб.)\",\"Комментарий\",\"Группа\",\"Отменено\",\"Статус синхронизации\"\n" +
                "\"2026-08-07\",\"Подушка\",\"\",\"-500\",\"Перевод, \"\"наличные\"\"\",\"batch\",\"нет\",\"PENDING\"\n",
            csv,
        )
    }
}
