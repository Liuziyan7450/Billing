package com.example.billing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.billing.data.BillingRepository
import com.example.billing.data.CategoryEntity
import com.example.billing.data.RecordType
import com.example.billing.data.TimeRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BillingViewModel(private val repository: BillingRepository) : ViewModel() {
    val categories = repository.categories.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    val todayRecords = repository.todayRecords().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val timeRange = MutableStateFlow(TimeRange.MONTH)
    val allRecords = repository.allRecords.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        emptyList()
    )

    private val _monthlyTrend = MutableStateFlow<Map<LocalDate, Double>>(emptyMap())
    val monthlyTrend: StateFlow<Map<LocalDate, Double>> = _monthlyTrend

    val chartData = timeRange.flatMapLatest { range ->
        combine(repository.categoryTotals(range), categories) { totals, categoryList ->
            categoryList.mapNotNull { category ->
                totals[category.id]?.takeIf { it > 0 }?.let { category.name to it }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val overview = combine(todayRecords, repository.allRecords) { today, all ->
        val todayExpense = today.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }
        val now = LocalDate.now()
        val monthExpense = all.filter {
            val date = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            date.year == now.year && date.month == now.month && it.type == RecordType.EXPENSE
        }
        val avg = if (monthExpense.isEmpty()) 0.0 else monthExpense.sumOf { it.amount } / now.dayOfMonth
        Overview(todayExpense, avg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Overview())

    init {
        viewModelScope.launch {
            if (categories.value.isEmpty()) seedCategories()
            refreshTrend()
        }
    }

    fun refreshTrend() {
        viewModelScope.launch { _monthlyTrend.value = repository.monthlyDailyExpense() }
    }

    fun setRange(range: TimeRange) {
        timeRange.value = range
    }

    fun addRecord(amount: Double, categoryId: Long, type: RecordType, note: String) {
        viewModelScope.launch {
            repository.addRecord(amount, categoryId, type, note)
            refreshTrend()
        }
    }

    fun addCategory(name: String, emoji: String) = viewModelScope.launch { repository.addCategory(name, emoji) }
    fun updateCategory(category: CategoryEntity) = viewModelScope.launch { repository.updateCategory(category) }
    fun deleteCategory(id: Long) = viewModelScope.launch { repository.deleteCategory(id) }
    fun deleteRecord(id: Long) = viewModelScope.launch {
        repository.deleteRecord(id)
        refreshTrend()
    }

    fun exportJson(onResult: (String) -> Unit) = viewModelScope.launch { onResult(repository.exportJson()) }

    fun importJson(json: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.importJson(json)
            if (result.isSuccess) refreshTrend()
            onResult(result)
        }
    }

    private suspend fun seedCategories() {
        listOf("餐饮" to "🍜", "交通" to "🚌", "购物" to "🛍️", "住房" to "🏠", "娱乐" to "🎮", "医疗" to "💊")
            .forEach { (name, emoji) -> repository.addCategory(name, emoji) }
    }

    data class Overview(val todayExpense: Double = 0.0, val monthAvgExpense: Double = 0.0)
}

class BillingViewModelFactory(private val repository: BillingRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = BillingViewModel(repository) as T
}
