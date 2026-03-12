package com.example.billing.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.billing.data.BillingRepository
import com.example.billing.data.CategoryEntity
import com.example.billing.data.CategoryType
import com.example.billing.data.DaySummary
import com.example.billing.data.RecordEntity
import com.example.billing.data.RecordType
import com.example.billing.data.TimeRange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BillingViewModel(private val repository: BillingRepository) : ViewModel() {
    val expenseCategories = repository.expenseCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val incomeCategories = repository.incomeCategories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayRecords = repository.todayRecords().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allRecords = repository.allRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val selectedRange = MutableStateFlow(TimeRange.MONTH)
    private val selectedTypeForPie = MutableStateFlow(RecordType.EXPENSE)

    private val _monthlyTrend = MutableStateFlow<Map<LocalDate, DaySummary>>(emptyMap())
    val monthlyTrend: StateFlow<Map<LocalDate, DaySummary>> = _monthlyTrend

    private val selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDateRecords = selectedDate.flatMapLatest { repository.recordsByDate(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val chartData = combine(selectedRange, selectedTypeForPie) { range, type -> range to type }
        .flatMapLatest { (range, type) ->
            combine(repository.categoryTotals(range, type), categories) { totals, categoryList ->
                categoryList.filter { category ->
                    category.categoryType == if (type == RecordType.EXPENSE) CategoryType.EXPENSE else CategoryType.INCOME
                }.mapNotNull { category ->
                    totals[category.id]?.takeIf { it > 0 }?.let { PieSlice(category.name, it) }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val analysisHeader = allRecords.combine(todayRecords) { all, today ->
        val now = LocalDate.now()
        val monthRecords = all.filter {
            val date = Instant.ofEpochMilli(it.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            date.year == now.year && date.month == now.month
        }
        val monthExpense = monthRecords.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }
        val monthIncome = monthRecords.filter { it.type == RecordType.INCOME }.sumOf { it.amount }
        val todayExpense = today.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }
        Overview(todayExpense, monthExpense, monthIncome, monthIncome - monthExpense)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Overview())

    val selectedRangeState: StateFlow<TimeRange> = selectedRange
    val selectedTypeForPieState: StateFlow<RecordType> = selectedTypeForPie
    val selectedDateState: StateFlow<LocalDate> = selectedDate

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
            refreshTrend()
        }
    }

    fun refreshTrend() {
        viewModelScope.launch {
            _monthlyTrend.value = repository.monthlyDailySummary()
        }
    }

    fun addRecord(amount: Double, categoryId: Long, type: RecordType, note: String) {
        viewModelScope.launch {
            repository.addRecord(amount, categoryId, type, note)
            refreshTrend()
        }
    }

    fun deleteRecord(id: Long) {
        viewModelScope.launch {
            repository.deleteRecord(id)
            refreshTrend()
        }
    }

    fun addCategory(name: String, emoji: String, type: CategoryType) = viewModelScope.launch {
        repository.addCategory(name, emoji, type)
    }

    fun updateCategory(category: CategoryEntity) = viewModelScope.launch { repository.updateCategory(category) }
    fun deleteCategory(id: Long) = viewModelScope.launch { repository.deleteCategory(id) }

    fun setRange(range: TimeRange) {
        selectedRange.update { range }
    }

    fun setPieType(type: RecordType) {
        selectedTypeForPie.update { type }
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
    }

    fun exportJson(onResult: (String) -> Unit) = viewModelScope.launch { onResult(repository.exportJson()) }

    fun importJson(json: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            val result = repository.importJson(json)
            if (result.isSuccess) refreshTrend()
            onResult(result)
        }
    }

    data class Overview(
        val todayExpense: Double = 0.0,
        val monthExpense: Double = 0.0,
        val monthIncome: Double = 0.0,
        val monthBalance: Double = 0.0
    )
}

data class PieSlice(val name: String, val value: Double)

class BillingViewModelFactory(private val repository: BillingRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = BillingViewModel(repository) as T
}
