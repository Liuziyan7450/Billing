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
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.billing.data.AppDatabase
import com.example.billing.data.BillingRepository
import com.example.billing.data.CategoryEntity
import com.example.billing.data.RecordType
import com.example.billing.data.TimeRange
import com.example.billing.theme.BillingTheme
import com.example.billing.viewmodel.BillingViewModel
import com.example.billing.viewmodel.BillingViewModelFactory
import java.time.LocalDate

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
    val context = androidx.compose.ui.platform.LocalContext.current

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        vm.exportJson { json ->
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(json) }
            Toast.makeText(context, "导出成功", Toast.LENGTH_SHORT).show()
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
                title = { Text("简记账") },
                actions = {
                    IconButton(onClick = { createDocument.launch("billing-backup.json") }) { Icon(Icons.Default.FileDownload, null) }
                    IconButton(onClick = { openDocument.launch(arrayOf("application/json")) }) { Icon(Icons.Default.FileUpload, null) }
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
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.List, null) }, label = { Text("分类") })
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
            .background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), MaterialTheme.colorScheme.background)
                )
            ),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("今日支出", style = MaterialTheme.typography.bodyMedium)
                        Text("¥%.2f".format(expense), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("今日收入", style = MaterialTheme.typography.bodyMedium)
                        Text("¥%.2f".format(income), style = MaterialTheme.typography.titleLarge, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item { Text("今天 ${LocalDate.now()} 的记录", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }

        if (records.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Text("还没有记录，点右下角 + 开始记账", Modifier.padding(20.dp), textAlign = TextAlign.Center)
                }
            }
        }

        items(records, key = { it.id }) { record ->
            val category = categories.firstOrNull { it.id == record.categoryId }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${category?.emoji.orEmpty()} ${category?.name ?: "未分类"}")
                        Text(record.note.ifBlank { "无备注" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = (if (record.type == RecordType.EXPENSE) "-" else "+") + "¥%.2f".format(record.amount),
                            color = if (record.type == RecordType.EXPENSE) MaterialTheme.colorScheme.error else Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { vm.deleteRecord(record.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryScreen(modifier: Modifier, vm: BillingViewModel) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<CategoryEntity?>(null) }
    var creating by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = { creating = true }) { Text("新增分类") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(categories, key = { it.id }) { category ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${category.emoji} ${category.name}")
                        Row {
                            AssistChip(onClick = { editing = category }, label = { Text("编辑") })
                            Spacer(Modifier.width(6.dp))
                            AssistChip(onClick = { vm.deleteCategory(category.id) }, label = { Text("删除") })
                        }
                    }
                }
            }
        }
    }

    if (creating) CategoryDialog(title = "新增分类", onDismiss = { creating = false }) { name, emoji ->
        vm.addCategory(name, emoji)
        creating = false
    }

    editing?.let { target ->
        CategoryDialog(title = "编辑分类", defaultName = target.name, defaultEmoji = target.emoji, onDismiss = { editing = null }) { name, emoji ->
            vm.updateCategory(target.copy(name = name, emoji = emoji))
            editing = null
        }
    }
}

@Composable
private fun AnalysisScreen(modifier: Modifier, vm: BillingViewModel) {
    var tab by remember { mutableStateOf(0) }
    val overview by vm.overview.collectAsStateWithLifecycle()
    val trend by vm.monthlyTrend.collectAsStateWithLifecycle()
    val chart by vm.chartData.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) { vm.refreshTrend() }

    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TabRow(selectedTabIndex = tab) {
            listOf("概览", "趋势", "分类").forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
            }
        }

        when (tab) {
            0 -> {
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("当日开支：¥%.2f".format(overview.todayExpense), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("当月日均开支：¥%.2f".format(overview.monthAvgExpense))
                    }
                }
            }
            1 -> {
                Text("本月每日支出（类似日历）", style = MaterialTheme.typography.titleSmall)
                LazyVerticalGrid(columns = GridCells.Fixed(7), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(LocalDate.now().lengthOfMonth()) { idx ->
                        val day = idx + 1
                        val amount = trend[LocalDate.now().withDayOfMonth(day)] ?: 0.0
                        Card(colors = CardDefaults.cardColors(containerColor = if (amount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.toString())
                                Text("¥%.0f".format(amount), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            else -> {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(TimeRange.entries.toList()) { option ->
                        AssistChip(onClick = { vm.setRange(option) }, label = { Text(option.label) })
                    }
                }

                val total = chart.sumOf { it.second }
                if (chart.isEmpty()) {
                    Card(Modifier.fillMaxWidth()) {
                        Text("该时间范围暂无支出数据", Modifier.padding(16.dp))
                    }
                }
                chart.forEach { (name, value) ->
                    val ratio = if (total == 0.0) 0f else (value / total).toFloat()
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("$name  ¥%.2f".format(value), fontWeight = FontWeight.SemiBold)
                            LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth())
                            Text("占比 %.1f%%".format(ratio * 100), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRecordSheet(vm: BillingViewModel, onDismiss: () -> Unit, onAdd: (Double, Long, RecordType, String) -> Unit) {
    val categories by vm.categories.collectAsStateWithLifecycle()
    var amount by remember { mutableStateOf("0") }
    var note by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Long?>(null) }
    var type by remember { mutableStateOf(RecordType.EXPENSE) }

    fun inputDigit(digit: String) {
        amount = if (amount == "0" && digit != ".") digit else amount + digit
    }

    fun backspace() {
        amount = amount.dropLast(1).ifBlank { "0" }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("记一笔", style = MaterialTheme.typography.titleLarge)

            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text("¥$amount", Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.End)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == RecordType.EXPENSE, onClick = { type = RecordType.EXPENSE }, label = { Text("支出") })
                FilterChip(selected = type == RecordType.INCOME, onClick = { type = RecordType.INCOME }, label = { Text("收入") })
            }

            OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth())

            Text("分类")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(categories, key = { it.id }) { category ->
                    FilterChip(
                        selected = selectedCategory == category.id,
                        onClick = { selectedCategory = category.id },
                        label = { Text("${category.emoji} ${category.name}") }
                    )
                }
            }

            NumberPad(
                onDigit = ::inputDigit,
                onDot = { if (!amount.contains('.')) amount += "." },
                onBackspace = ::backspace,
                onClear = { amount = "0" }
            )

            Button(
                onClick = {
                    val selected = selectedCategory ?: categories.firstOrNull()?.id ?: return@Button
                    onAdd(amount.toDoubleOrNull() ?: 0.0, selected, type, note)
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
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var name by remember { mutableStateOf(defaultName) }
    var emoji by remember { mutableStateOf(defaultEmoji) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") })
                OutlinedTextField(value = emoji, onValueChange = { emoji = it }, label = { Text("Emoji") })
            }
        },
        confirmButton = { Button(onClick = { onConfirm(name, emoji) }) { Text("确定") } },
        dismissButton = { Button(onClick = onDismiss) { Text("取消") } }
    )
}
