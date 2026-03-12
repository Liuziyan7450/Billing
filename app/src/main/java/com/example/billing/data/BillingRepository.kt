package com.example.billing.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BillingRepository(private val dao: BillingDao) {
    val categories = dao.observeCategories()
    val expenseCategories = dao.observeCategoriesByType(CategoryType.EXPENSE)
    val incomeCategories = dao.observeCategoriesByType(CategoryType.INCOME)
    val allRecords = dao.observeAllRecords()

    fun todayRecords(): Flow<List<RecordEntity>> {
        val (start, end) = dayRange(LocalDate.now())
        return dao.observeRecordsBetween(start, end)
    }

    suspend fun addRecord(amount: Double, categoryId: Long, type: RecordType, note: String) {
        dao.insertRecord(
            RecordEntity(
                amount = amount,
                categoryId = categoryId,
                type = type,
                note = note,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun addCategory(name: String, emoji: String, type: CategoryType) {
        dao.insertCategory(CategoryEntity(name = name, emoji = emoji, categoryType = type))
    }

    suspend fun updateCategory(category: CategoryEntity) = dao.updateCategory(category)
    suspend fun deleteCategory(id: Long) = dao.deleteCategory(id)
    suspend fun deleteRecord(id: Long) = dao.deleteRecord(id)

    suspend fun seedIfEmpty() {
        if (dao.countCategories() > 0) return
        listOf(
            "餐饮" to "🍜",
            "交通" to "🚌",
            "购物" to "🛍️",
            "住房" to "🏠",
            "娱乐" to "🎮"
        ).forEach { (name, emoji) -> addCategory(name, emoji, CategoryType.EXPENSE) }

        listOf(
            "工资" to "💼",
            "红包" to "🧧",
            "其他收入" to "💰"
        ).forEach { (name, emoji) -> addCategory(name, emoji, CategoryType.INCOME) }
    }

    suspend fun monthlyDailySummary(): Map<LocalDate, DaySummary> {
        val now = LocalDate.now()
        val start = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = now.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
        return dao.observeRecordsBetween(start, end).first()
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate() }
            .mapValues { (_, records) ->
                DaySummary(
                    expense = records.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount },
                    income = records.filter { it.type == RecordType.INCOME }.sumOf { it.amount }
                )
            }
    }

    fun recordsByDate(target: LocalDate): Flow<List<RecordEntity>> {
        val (start, end) = dayRange(target)
        return dao.observeRecordsBetween(start, end)
    }

    fun categoryTotals(range: TimeRange, recordType: RecordType): Flow<Map<Long, Double>> = allRecords.map { records ->
        val filtered = records.filter { record ->
            when (range) {
                TimeRange.DAY -> isInDay(record.timestamp)
                TimeRange.WEEK -> isInRecentDays(record.timestamp, 7)
                TimeRange.MONTH -> isInCurrentMonth(record.timestamp)
                TimeRange.YEAR -> isInCurrentYear(record.timestamp)
            } && record.type == recordType
        }
        filtered.groupBy { it.categoryId }.mapValues { (_, list) -> list.sumOf { it.amount } }
    }

    suspend fun exportJson(): String {
        val categories = dao.observeCategories().first().map {
            BackupCategory(it.id, it.name, it.emoji, it.categoryType.name)
        }
        val records = dao.observeAllRecords().first().map {
            BackupRecord(it.amount, it.categoryId, it.type.name, it.note, it.timestamp)
        }
        return Json { prettyPrint = true }.encodeToString(BackupData(categories, records))
    }

    suspend fun importJson(json: String): Result<Unit> = runCatching {
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<BackupData>(json)
        dao.clearRecords()
        dao.clearCategories()
        dao.insertCategories(parsed.categories.map {
            CategoryEntity(it.id, it.name, it.emoji, CategoryType.valueOf(it.categoryType))
        })
        dao.insertRecords(parsed.records.map {
            RecordEntity(
                amount = it.amount,
                categoryId = it.categoryId,
                type = RecordType.valueOf(it.type),
                note = it.note,
                timestamp = it.timestamp
            )
        })
    }

    companion object {
        private fun dayRange(day: LocalDate): Pair<Long, Long> {
            val start = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val end = day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            return start to end
        }

        private fun isInDay(ts: Long): Boolean {
            val day = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
            return day == LocalDate.now()
        }

        private fun isInRecentDays(ts: Long, days: Long): Boolean {
            val date = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
            return !date.isBefore(LocalDate.now().minusDays(days - 1))
        }

        private fun isInCurrentMonth(ts: Long): Boolean {
            val date = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
            val now = LocalDate.now()
            return date.year == now.year && date.month == now.month
        }

        private fun isInCurrentYear(ts: Long): Boolean {
            val date = Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalDate()
            return date.year == LocalDate.now().year
        }
    }
}

enum class TimeRange(val label: String) {
    DAY("当日"),
    WEEK("本周"),
    MONTH("本月"),
    YEAR("本年")
}

data class DaySummary(
    val expense: Double = 0.0,
    val income: Double = 0.0
)
