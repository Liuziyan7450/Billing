package com.example.billing

import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.billing.data.AppDatabase
import com.example.billing.data.BillingRepository
import com.example.billing.data.CategoryEntity
import com.example.billing.data.CategoryType
import com.example.billing.data.RecordType
import com.example.billing.data.TimeRange
import com.example.billing.theme.BillingTheme
import com.example.billing.viewmodel.BillingViewModel
import com.example.billing.viewmodel.BillingViewModelFactory
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = BillingRepository(AppDatabase.get(this).billingDao())
        setContent {
            BillingTheme {
                val vm: BillingViewModel = viewModel(factory = BillingViewModelFactory(repository))
                AppScreen(vm)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppScreen(vm: BillingViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var showSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var showDataMenu by remember { mutableStateOf(false) }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        vm.exportJson { json ->
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
            Toast.makeText(context, "导出成功：billing-backup.json", Toast.LENGTH_SHORT).show()
        }
    }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
        vm.importJson(json) {
            Toast.makeText(context, if (it.isSuccess) "导入成功" else "导入失败", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("皓运账本") },
                actions = {
                    Box {
                        IconButton(onClick = { showDataMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "数据操作")
                        }
                        DropdownMenu(expanded = showDataMenu, onDismissRequest = { showDataMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("导入备份（JSON）") },
                                onClick = {
                                    showDataMenu = false
                                    openDocument.launch(arrayOf("application/json"))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导出备份（JSON）") },
                                onClick = {
                                    showDataMenu = false
                                    createDocument.launch("billing-backup.json")
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(onClick = { showSheet = true }) { Icon(Icons.Default.Add, null) }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.Home, null) }, label = { Text("首页") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.AutoMirrored.Filled.List, null) }, label = { Text("分类") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Analytics, null) }, label = { Text("统计") })
            }
        }
    ) { padding ->
        when (tab) {
            0 -> HomeScreen(modifier = Modifier.padding(padding), vm = vm)
            1 -> CategoryScreen(modifier = Modifier.padding(padding), vm = vm)
            else -> AnalysisScreen(modifier = Modifier.padding(padding), vm = vm)
        }
    }

    if (showSheet) {
        AddRecordSheet(vm = vm, onDismiss = { showSheet = false }) { amount, categoryId, type, note ->
            vm.addRecord(amount, categoryId, type, note)
            showSheet = false
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier, vm: BillingViewModel) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    val records by vm.todayRecords.collectAsStateWithLifecycle()
    val expense = records.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }
    val income = records.filter { it.type == RecordType.INCOME }.sumOf { it.amount }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), MaterialTheme.colorScheme.background))),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("今日支出")
                        Text("¥%.2f".format(expense), fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("今日收入")
                        Text("¥%.2f".format(income), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Text("今天 ${LocalDate.now()} 的记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

        items(records, key = { it.id }) { record ->
            val category = categories.firstOrNull { it.id == record.categoryId }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${category?.emoji ?: "🧾"} ${category?.name ?: "未分类"}")
                        Text(record.note.ifBlank { "无备注" }, style = MaterialTheme.typography.bodySmall)
                    }
                    Text((if (record.type == RecordType.EXPENSE) "-" else "+") + "¥%.2f".format(record.amount), fontWeight = FontWeight.Bold)
                    IconButton(onClick = { vm.deleteRecord(record.id) }) { Icon(Icons.Default.Delete, "删除") }
                }
            }
        }
    }
}

@Composable
private fun CategoryScreen(modifier: Modifier, vm: BillingViewModel) {
    val context = LocalContext.current
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val budgetProgress by vm.expenseBudgetProgress.collectAsStateWithLifecycle()

    var selected by remember { mutableStateOf(CategoryType.EXPENSE) }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }

    val current = if (selected == CategoryType.EXPENSE) expenseCategories else incomeCategories

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = selected == CategoryType.EXPENSE, onClick = { selected = CategoryType.EXPENSE }, label = { Text("支出分类") })
            FilterChip(selected = selected == CategoryType.INCOME, onClick = { selected = CategoryType.INCOME }, label = { Text("收入分类") })
        }

        Button(onClick = { creating = true }) { Text("新增${if (selected == CategoryType.EXPENSE) "支出" else "收入"}分类") }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(current, key = { it.id }) { category ->
                val progress = budgetProgress[category.id]
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))) {
                    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("${category.emoji} ${category.name}")
                            Row {
                                AssistChip(onClick = { editing = category }, label = { Text("编辑") })
                                Spacer(Modifier.width(6.dp))
                                AssistChip(onClick = {
                                    vm.deleteCategory(category.id) { ok ->
                                        Toast.makeText(context, if (ok) "已删除分类" else "该分类已有记录，请先清空数据", Toast.LENGTH_SHORT).show()
                                    }
                                }, label = { Text("删除") })
                            }
                        }

                        if (category.categoryType == CategoryType.EXPENSE) {
                            Text("预算：${category.budgetLimit?.let { "¥%.2f".format(it) } ?: "未设置"}")
                            Text("本月已支出：¥%.2f".format(progress?.spent ?: 0.0))
                            if ((category.budgetLimit ?: 0.0) > 0) {
                                LinearProgressIndicator(progress = { (progress?.ratio ?: 0f).coerceAtMost(1f) }, modifier = Modifier.fillMaxWidth())
                                if ((progress?.ratio ?: 0f) > 1f) {
                                    Text("已超预算 ${(progress!!.ratio * 100).toInt()}%", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                    }
                }
            }
        }
    }

    if (creating) {
        CategoryDialog(
            title = "新增分类",
            isExpense = selected == CategoryType.EXPENSE,
            onDismiss = { creating = false }
        ) { name, emoji, budget ->
            vm.addCategory(name, emoji, selected, budget)
            creating = false
        }
    }

    editing?.let { category ->
        CategoryDialog(
            title = "编辑分类",
            defaultName = category.name,
            defaultEmoji = category.emoji,
            defaultBudget = category.budgetLimit,
            isExpense = category.categoryType == CategoryType.EXPENSE,
            onDismiss = { editing = null }
        ) { name, emoji, budget ->
            vm.updateCategory(category.copy(name = name, emoji = emoji, budgetLimit = budget))
            editing = null
        }
    }
}

@Composable
private fun AnalysisScreen(modifier: Modifier, vm: BillingViewModel) {
    var tab by remember { mutableStateOf(0) }
    val overview by vm.analysisHeader.collectAsStateWithLifecycle()
    val trend by vm.monthlyTrend.collectAsStateWithLifecycle()
    val selectedDate by vm.selectedDateState.collectAsStateWithLifecycle()
    val selectedDateRecords by vm.selectedDateRecords.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val chartData by vm.chartData.collectAsStateWithLifecycle()
    val selectedRange by vm.selectedRangeState.collectAsStateWithLifecycle()
    val selectedPieType by vm.selectedTypeForPieState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refreshTrend() }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TabRow(selectedTabIndex = tab) {
            listOf("概览", "趋势", "分类").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }

        when (tab) {
            0 -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("当日支出：¥%.2f".format(overview.todayExpense))
                        Text("本月支出：¥%.2f".format(overview.monthExpense))
                        Text("本月收入：¥%.2f".format(overview.monthIncome), color = Color(0xFF2E7D32))
                        Text("本月结余：¥%.2f".format(overview.monthBalance), fontWeight = FontWeight.Bold)
                    }
                }
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))) {
                    Text("💡 小贴士：持续记录收入和支出，月底能快速掌握结余变化。", Modifier.padding(14.dp))
                }
            }

            1 -> {
                TrendCalendarSection(
                    trend = trend,
                    selectedDate = selectedDate,
                    onSelectDate = vm::selectDate,
                    selectedDateRecords = selectedDateRecords,
                    categories = categories
                )
            }

            else -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    TimeRange.entries.forEach { range ->
                        FilterChip(
                            selected = selectedRange == range,
                            onClick = { vm.setRange(range) },
                            label = { Text(range.label) }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = selectedPieType == RecordType.EXPENSE, onClick = { vm.setPieType(RecordType.EXPENSE) }, label = { Text("支出构成") })
                    FilterChip(selected = selectedPieType == RecordType.INCOME, onClick = { vm.setPieType(RecordType.INCOME) }, label = { Text("收入构成") })
                }

                if (chartData.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Text("当前筛选暂无数据", Modifier.padding(16.dp), textAlign = TextAlign.Center)
                    }
                } else {
                    PieChartCard(chartData = chartData.map { it.name to it.value })
                }

                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))) {
                    Text("图表说明：高亮按钮表示当前统计维度，饼图展示各分类占比。", Modifier.padding(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrendCalendarSection(
    trend: Map<LocalDate, com.example.billing.data.DaySummary>,
    selectedDate: LocalDate,
    onSelectDate: (LocalDate) -> Unit,
    selectedDateRecords: List<com.example.billing.data.RecordEntity>,
    categories: List<CategoryEntity>
) {
    val currentMonth = YearMonth.now()
    val firstDayOfWeek = firstDayOfWeekFromLocale()
    val daysOfWeek = daysOfWeek(firstDayOfWeek)
    val calendarState = rememberCalendarState(
        startMonth = currentMonth.minusMonths(6),
        endMonth = currentMonth.plusMonths(6),
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeek
    )
    var showDetailSheet by remember { mutableStateOf(false) }

    fun openDetailFor(date: LocalDate) {
        if (date == selectedDate) {
            showDetailSheet = true
        } else {
            onSelectDate(date)
        }
    }

    val dayExpense = selectedDateRecords.filter { it.type == RecordType.EXPENSE }.sumOf { it.amount }
    val dayIncome = selectedDateRecords.filter { it.type == RecordType.INCOME }.sumOf { it.amount }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            daysOfWeek.forEach { day ->
                Text(day.name.take(2), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
            }
        }

        HorizontalCalendar(
            state = calendarState,
            dayContent = { day ->
                TrendDayCell(day, trend[day.date], selectedDate == day.date) { clicked ->
                    openDetailFor(clicked)
                }
            }
        )

        Card(
            Modifier
                .fillMaxWidth()
                .clickable { showDetailSheet = true }
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${selectedDate} 收支汇总", fontWeight = FontWeight.SemiBold)
                Text("当日累计支出：¥%.2f    当日累计收入：¥%.2f".format(dayExpense, dayIncome), style = MaterialTheme.typography.bodySmall)
                Text("再次点击日期或点击本卡片可展开明细", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if (showDetailSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showDetailSheet = false }, sheetState = sheetState) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.9f)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("${selectedDate} 收支详情", style = MaterialTheme.typography.titleLarge)
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("累计支出：¥%.2f".format(dayExpense), color = MaterialTheme.colorScheme.error)
                        Text("累计收入：¥%.2f".format(dayIncome), color = Color(0xFF2E7D32))
                    }
                }

                if (selectedDateRecords.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) { Text("当天暂无记录", Modifier.padding(14.dp)) }
                } else {
                    selectedDateRecords.forEach { item ->
                        val category = categories.firstOrNull { it.id == item.categoryId }
                        Card(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${category?.emoji ?: "🧾"} ${category?.name ?: "未分类"}")
                                    Text(item.note.ifBlank { "无备注" }, style = MaterialTheme.typography.bodySmall)
                                }
                                Text(
                                    text = (if (item.type == RecordType.EXPENSE) "-" else "+") + "¥%.2f".format(item.amount),
                                    color = if (item.type == RecordType.EXPENSE) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TrendDayCell(
    day: CalendarDay,
    summary: com.example.billing.data.DaySummary?,
    selected: Boolean,
    onSelectDate: (LocalDate) -> Unit
) {
    if (day.position != DayPosition.MonthDate) {
        Box(Modifier.size(54.dp))
        return
    }
    val hasExpense = (summary?.expense ?: 0.0) > 0
    val hasIncome = (summary?.income ?: 0.0) > 0
    val bg = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        hasExpense && hasIncome -> Color(0xFFE8EAF6)
        hasExpense -> Color(0xFFFFEBEE)
        hasIncome -> Color(0xFFE8F5E9)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    Column(
        modifier = Modifier
            .size(60.dp)
            .background(bg, RoundedCornerShape(20.dp))
            .clickable { onSelectDate(day.date) }
            .padding(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(day.date.dayOfMonth.toString(), style = MaterialTheme.typography.bodySmall)
        if (hasExpense || hasIncome) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (hasExpense) Box(Modifier.size(5.dp).background(Color(0xFFD32F2F), CircleShape))
                if (hasIncome) Box(Modifier.size(5.dp).background(Color(0xFF2E7D32), CircleShape))
            }
        }
        val expenseAmount = summary?.expense ?: 0.0
        if (expenseAmount > 0) {
            Text("¥%.0f".format(expenseAmount), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun PieChartCard(chartData: List<Pair<String, Double>>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("分类占比", fontWeight = FontWeight.SemiBold)
            AndroidView(
                factory = { ctx ->
                    PieChart(ctx).apply {
                        description.isEnabled = false
                        setUsePercentValues(true)
                        setDrawEntryLabels(false)
                        legend.isWordWrapEnabled = true
                        holeRadius = 55f
                    }
                },
                update = { chart ->
                    val entries = chartData.map { PieEntry(it.second.toFloat(), it.first) }
                    val dataSet = PieDataSet(entries, "").apply {
                        colors = listOf(
                            android.graphics.Color.parseColor("#EF5350"),
                            android.graphics.Color.parseColor("#42A5F5"),
                            android.graphics.Color.parseColor("#66BB6A"),
                            android.graphics.Color.parseColor("#FFCA28"),
                            android.graphics.Color.parseColor("#AB47BC")
                        )
                        valueTextSize = 12f
                    }
                    chart.data = PieData(dataSet).apply {
                        setValueFormatter(PercentFormatter(chart))
                    }
                    chart.invalidate()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecordSheet(vm: BillingViewModel, onDismiss: () -> Unit, onAdd: (Double, Long, RecordType, String) -> Unit) {
    val expenseCategories by vm.expenseCategories.collectAsStateWithLifecycle()
    val incomeCategories by vm.incomeCategories.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var amount by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(RecordType.EXPENSE) }
    var selectedCategory by remember { mutableStateOf<Long?>(null) }

    val categoryList = if (type == RecordType.EXPENSE) expenseCategories else incomeCategories

    LaunchedEffect(type, categoryList.size) {
        selectedCategory = categoryList.firstOrNull()?.id
    }

    fun inputDigit(d: String) {
        amount = if (amount == "0" && d != ".") d else amount + d
    }

    fun backspace() {
        amount = amount.dropLast(1).ifBlank { "0" }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("记一笔", style = MaterialTheme.typography.titleLarge)
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("¥$amount", Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.End)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == RecordType.EXPENSE, onClick = { type = RecordType.EXPENSE }, label = { Text("支出") })
                FilterChip(selected = type == RecordType.INCOME, onClick = { type = RecordType.INCOME }, label = { Text("收入") })
            }

            Text(if (type == RecordType.EXPENSE) "支出分类" else "收入分类")
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categoryList.forEach { category ->
                    FilterChip(selected = selectedCategory == category.id, onClick = { selectedCategory = category.id }, label = { Text("${category.emoji} ${category.name}") })
                }
            }

            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())

            NumberPad(
                onDigit = ::inputDigit,
                onDot = { if (!amount.contains('.')) amount += "." },
                onBackspace = ::backspace,
                onClear = { amount = "0" }
            )

            Button(
                onClick = {
                    val catId = selectedCategory ?: return@Button
                    onAdd(amount.toDoubleOrNull() ?: 0.0, catId, type, note)
                    scope.launch { onDismiss() }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("确认") }
        }
    }
}

@Composable
private fun NumberPad(onDigit: (String) -> Unit, onDot: () -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val rows = listOf(
        listOf("7", "8", "9", "⌫"),
        listOf("4", "5", "6", "C"),
        listOf("1", "2", "3", "."),
        listOf("0")
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { key ->
                    Button(
                        onClick = {
                            when (key) {
                                "⌫" -> onBackspace()
                                "C" -> onClear()
                                "." -> onDot()
                                else -> onDigit(key)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text(key) }
                }
                if (row.size == 1) {
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryDialog(
    title: String,
    defaultName: String = "",
    defaultEmoji: String = "🧾",
    defaultBudget: Double? = null,
    isExpense: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, String, Double?) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var emoji by remember { mutableStateOf(defaultEmoji) }
    var budgetInput by remember { mutableStateOf(defaultBudget?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") })
                OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text("Emoji") })
                if (isExpense) {
                    OutlinedTextField(
                        value = budgetInput,
                        onValueChange = { budgetInput = it },
                        label = { Text("预算上限（元）") }
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val budget = if (isExpense) budgetInput.toDoubleOrNull() else null
                onConfirm(name, emoji, budget)
            }) { Text("确定") }
        },
        dismissButton = { Button(onClick = onDismiss) { Text("取消") } }
    )
}
