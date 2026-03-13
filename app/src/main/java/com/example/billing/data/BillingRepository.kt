package com.example.billing.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

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

    suspend fun addCategory(name: String, emoji: String, type: CategoryType, budgetLimit: Double?) {
        dao.insertCategory(CategoryEntity(name = name, emoji = emoji, categoryType = type, budgetLimit = budgetLimit))
    }

    suspend fun updateCategory(category: CategoryEntity) = dao.updateCategory(category)

    suspend fun deleteCategoryIfEmpty(categoryId: Long): Boolean {
        if (dao.countRecordsByCategory(categoryId) > 0) return false
        dao.deleteCategory(categoryId)
        return true
    }

    suspend fun deleteRecord(id: Long) = dao.deleteRecord(id)

    suspend fun seedIfEmpty() {
        if (dao.countCategories() > 0) return
        listOf(
            "餐饮" to "🍜",
            "交通" to "🚌",
            "购物" to "🛍️",
            "住房" to "🏠",
            "娱乐" to "🎮"
        ).forEach { (name, emoji) -> addCategory(name, emoji, CategoryType.EXPENSE, null) }

        listOf(
            "工资" to "💼",
            "红包" to "🧧",
            "其他收入" to "💰"
        ).forEach { (name, emoji) -> addCategory(name, emoji, CategoryType.INCOME, null) }
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
        val localCategories = dao.observeCategories().first()
        val localRecords = dao.observeAllRecords().first()

        val categoryUidMap = localCategories.associate { category ->
            category.id to stableCategoryUid(category.name, category.categoryType)
        }

        val categories = localCategories.map { category ->
            BackupCategory(
                id = category.id,
                name = category.name,
                emoji = category.emoji,
                categoryType = category.categoryType.name,
                budgetLimit = category.budgetLimit,
                uid = stableCategoryUid(category.name, category.categoryType),
                createdAt = System.currentTimeMillis()
            )
        }

        val records = localRecords.map { record ->
            BackupRecord(
                amount = record.amount,
                categoryId = record.categoryId,
                type = record.type.name,
                note = record.note,
                timestamp = record.timestamp,
                uid = stableRecordUid(record),
                dateLabel = Instant.ofEpochMilli(record.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate().format(DateTimeFormatter.ISO_DATE),
                categoryUid = categoryUidMap[record.categoryId],
                createdAt = record.timestamp
            )
        }

        val metadata = BackupMetadata(
            exportId = UUID.randomUUID().toString(),
            exportedAt = System.currentTimeMillis(),
            timezone = ZoneId.systemDefault().id,
            schemaVersion = 2
        )

        return Json { prettyPrint = true }.encodeToString(BackupData(metadata, categories, records))
    }

    /**
     * 全量导入（合并策略）
     * - 不清空本地数据
     * - 分类按 uid（或类型+名称）合并
     * - 记录按 uid（或稳定指纹）去重后追加
     */
    suspend fun importJson(json: String): Result<Unit> = runCatching {
        val parsed = Json { ignoreUnknownKeys = true }.decodeFromString<BackupData>(json)

        val localCategories = dao.observeCategories().first().toMutableList()
        val localRecords = dao.observeAllRecords().first().toMutableList()

        val categoryByUid = localCategories.associateBy { stableCategoryUid(it.name, it.categoryType) }.toMutableMap()
        val categoryIdByRemoteId = mutableMapOf<Long, Long>()

        parsed.categories.forEach { backupCategory ->
            val cType = runCatching { CategoryType.valueOf(backupCategory.categoryType) }.getOrDefault(CategoryType.EXPENSE)
            val uid = backupCategory.uid ?: stableCategoryUid(backupCategory.name, cType)
            val existing = categoryByUid[uid]
            if (existing != null) {
                categoryIdByRemoteId[backupCategory.id] = existing.id
                if (existing.budgetLimit != backupCategory.budgetLimit || existing.emoji != backupCategory.emoji || existing.name != backupCategory.name) {
                    dao.updateCategory(existing.copy(name = backupCategory.name, emoji = backupCategory.emoji, budgetLimit = backupCategory.budgetLimit))
                }
            } else {
                dao.insertCategory(
                    CategoryEntity(
                        name = backupCategory.name,
                        emoji = backupCategory.emoji,
                        categoryType = cType,
                        budgetLimit = backupCategory.budgetLimit
                    )
                )
                val refreshed = dao.observeCategories().first().last()
                categoryByUid[uid] = refreshed
                categoryIdByRemoteId[backupCategory.id] = refreshed.id
            }
        }

        val localRecordFingerprints = localRecords.map { stableRecordUid(it) }.toMutableSet()

        parsed.records.forEach { backupRecord ->
            val mappedCategoryId = when {
                backupRecord.categoryUid != null -> {
                    val c = categoryByUid[backupRecord.categoryUid]
                    c?.id
                }
                else -> categoryIdByRemoteId[backupRecord.categoryId]
            } ?: return@forEach

            val type = runCatching { RecordType.valueOf(backupRecord.type) }.getOrDefault(RecordType.EXPENSE)
            val candidate = RecordEntity(
                amount = backupRecord.amount,
                categoryId = mappedCategoryId,
                type = type,
                note = backupRecord.note,
                timestamp = backupRecord.timestamp
            )
            val uid = backupRecord.uid ?: stableRecordUid(candidate)
            if (uid !in localRecordFingerprints) {
                dao.insertRecord(candidate)
                localRecordFingerprints += uid
            }
        }
    }

    companion object {
        private fun stableCategoryUid(name: String, type: CategoryType): String {
            val raw = "${type.name}|${name.trim().lowercase()}"
            return UUID.nameUUIDFromBytes(raw.toByteArray()).toString()
        }

        private fun stableRecordUid(record: RecordEntity): String {
            val raw = listOf(
                record.timestamp.toString(),
                record.amount.toString(),
                record.type.name,
                record.categoryId.toString(),
                record.note.trim()
            ).joinToString("|")
            return UUID.nameUUIDFromBytes(raw.toByteArray()).toString()
        }

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
