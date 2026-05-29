package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.TransactionEntity
import com.example.data.TransactionRepository
import com.example.ui.*
import com.example.ui.theme.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Repository
        val database = AppDatabase.getDatabase(this)
        val repository = TransactionRepository(database.transactionDao)
        
        // Define ViewModel with Builder Factory
        val viewModel: TransactionViewModel by viewModels { ViewModelFactory(repository) }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    MainDashboardScreen(
                        viewModel = viewModel,
                        onExportClick = { exportMonthToCsv(viewModel) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    private fun exportMonthToCsv(viewModel: TransactionViewModel) {
        val csvContent = viewModel.generateCsvContent()
        val fileName = viewModel.getExportFileName()
        try {
            val cacheFile = File(cacheDir, fileName)
            cacheFile.writeText(csvContent)

            val authority = "$packageName.fileprovider"
            val fileUri = FileProvider.getUriForFile(this, authority, cacheFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_SUBJECT, "Pocket Diary Monthly Report - ${viewModel.getExportFileName()}")
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(intent, "Download Excel/CSV Report")
            startActivity(chooser)
            
            // Traditional Hinglish/Hindi feedback toast
            Toast.makeText(this, "Excel file mil gaya! 📁 Backup saved.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export Failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}

// Gorgeous traditional formatting helper for Indian Rupee with comma groupings
fun formatIndianRupee(value: Double): String {
    val rounded = Math.abs(Math.round(value)).toString()
    val isNegative = value < 0
    val formatted = if (rounded.length <= 3) {
        rounded
    } else {
        val lastThree = rounded.substring(rounded.length - 3)
        val rest = rounded.substring(0, rounded.length - 3)
        val sb = java.lang.StringBuilder()
        var i = rest.length - 1
        var count = 0
        while (i >= 0) {
            sb.append(rest[i])
            count++
            if (count == 2 && i > 0) {
                sb.append(",")
                count = 0
            }
            i--
        }
        sb.reverse().toString() + "," + lastThree
    }
    return if (isNegative) "-₹$formatted" else "₹$formatted"
}

@Composable
fun MainDashboardScreen(
    viewModel: TransactionViewModel,
    onExportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedCal by viewModel.selectedCalendar.collectAsStateWithLifecycle()
    val transactions by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val dailyTrend by viewModel.dailyTrendData.collectAsStateWithLifecycle()
    val stats by viewModel.monthlyStats.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTypeFilter by remember { mutableStateOf("ALL") } // "ALL", "EARNING", "EXPENSE", "SAVING"
    var searchQuery by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf("HOME") } // "HOME" (Ledger Book), "PLAN" (Budgeting rules)

    // User Profile persistent state (mock authenticated pocket diary context)
    var profileName by remember { mutableStateOf("राज कुमार (Raj Kumar)") }
    var profileMobile by remember { mutableStateOf("+91 98765-43210") }
    var showProfileEdit by remember { mutableStateOf(false) }

    // Aggregate monthly planners (default settings)
    var kharchaLimit by remember { mutableStateOf(20000.0) }
    var bachatGoal by remember { mutableStateOf(5000.0) }

    val monthYearFormat = remember { SimpleDateFormat("MMMM yyyy", Locale.getDefault()) }

    // Compute Today's dynamic stats for the quick glance bar
    val todayCal = Calendar.getInstance()
    val todayTransactions = remember(transactions) {
        transactions.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
            txCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
            txCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
        }
    }
    val todayEarnings = todayTransactions.filter { it.type == "EARNING" }.sumOf { it.amount }
    val todayExpenses = todayTransactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val todaySavings = todayTransactions.filter { it.type == "SAVING" }.sumOf { it.amount }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // --- HEADER TITLE & ACCOUNT BADGE ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "मेनेजर बही खाता 📘",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                )
                Spacer(modifier = Modifier.height(2.dp))
                val netBalance = stats.earnings - stats.expenses
                Text(
                    text = formatIndianRupee(netBalance),
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                )
                Text(
                    text = "कुल जोड़ (Net Jod Balance)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // EXCEL DOWNLOAD EXPORT BUTTON
                IconButton(
                    onClick = onExportClick,
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f), CircleShape)
                        .testTag("download_excel_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Excel File Download - महीना वार",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // AVATAR FOR LOGGED IN USER
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(EarningsBg, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                        .clickable { showProfileEdit = !showProfileEdit },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "RK",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Bold,
                            color = EarningsText
                        )
                    )
                }
            }
        }

        // --- EDIT PROFILE COLLAPSIBLE CARD ---
        if (showProfileEdit) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderSlate),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "✍️ खाताधारक प्रोफाइल (Diary Profile)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    OutlinedTextField(
                        value = profileName,
                        onValueChange = { profileName = it },
                        label = { Text("खाताधारक नाम (Owner Name)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = profileMobile,
                        onValueChange = { profileMobile = it },
                        label = { Text("मोबाईल नं. (Mobile Phone)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "✅ मोबाईल OTP द्वारा सत्यापित (Verified)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = EarningsText,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        TextButton(onClick = { showProfileEdit = false }) {
                            Text("बंद करें (Close)")
                        }
                    }
                }
            }
        }

        // --- MONTH NAVIGATION BAR ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, BorderSlate),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp, horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { viewModel.changeMonth(-1) },
                    modifier = Modifier.testTag("prev_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Previous Month",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                Text(
                    text = monthYearFormat.format(selectedCal.time),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = 0.5.sp
                    ),
                    textAlign = TextAlign.Center
                )

                IconButton(
                    onClick = { viewModel.changeMonth(1) },
                    modifier = Modifier.testTag("next_month_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Next Month",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // --- SLIDING TABS (LEDGER VS BUDGETING) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .border(1.dp, BorderSlate, RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (activeTab == "HOME") MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { activeTab = "HOME" }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📘 बही खाता (Ledger)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (activeTab == "HOME") Color.White else MaterialTheme.colorScheme.onBackground
                    )
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (activeTab == "PLAN") MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(8.dp))
                    .clickable { activeTab = "PLAN" }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🎯 महीने का प्लान (Goal & Plan)",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (activeTab == "PLAN") Color.White else MaterialTheme.colorScheme.onBackground
                    )
                )
            }
        }

        // --- RENDER SELECTED TAB SCENE ---
        if (activeTab == "HOME") {
            // LEDGER VIEW WITH FILTERS, TODAYS GLANCE, CHARTS, AND TRANSACTION ITEMS
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // TODAY'S QUICK GLANCE ROW
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, BorderSlate)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "⚡ आज का हिसाब (Today's Quick Glance)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("आज कमाई", style = MaterialTheme.typography.labelSmall, color = EarningsText)
                                    Text(formatIndianRupee(todayEarnings), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = EarningsText)
                                }
                                Divider(modifier = Modifier.height(24.dp).width(1.dp), color = BorderSlate)
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("आज खर्चा", style = MaterialTheme.typography.labelSmall, color = ExpensesText)
                                    Text(formatIndianRupee(todayExpenses), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = ExpensesText)
                                }
                                Divider(modifier = Modifier.height(24.dp).width(1.dp), color = BorderSlate)
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                    Text("आज बचत", style = MaterialTheme.typography.labelSmall, color = SavingsText)
                                    Text(formatIndianRupee(todaySavings), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = SavingsText)
                                }
                            }
                        }
                    }
                }

                // DYNAMIC ADVICE BANNER: "Kya Haal Hai?" (How are you doing?)
                item {
                    val adviceText = when {
                        stats.expenses > stats.earnings -> "खर्चा ज्यादा! थोड़ा बचत करो ⚠️ (Expense high! Save a bit)"
                        stats.savings > (stats.earnings * 0.2) && stats.earnings > 0 -> "शाबाश! बढ़िया बचत ⭐ (Well done! Great savings)"
                        else -> "हिसाब बराबर! समझदारी से खर्च करें 📘 (Balanced accounts! Spend wisely)"
                    }
                    val adviceColor = when {
                        stats.expenses > stats.earnings -> ExpensesText
                        stats.savings > (stats.earnings * 0.2) && stats.earnings > 0 -> EarningsText
                        else -> SavingsText
                    }
                    val adviceBg = when {
                        stats.expenses > stats.earnings -> ExpensesBg
                        stats.savings > (stats.earnings * 0.2) && stats.earnings > 0 -> EarningsBg
                        else -> SavingsBg
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = adviceBg),
                        border = BorderStroke(1.dp, adviceColor.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("💬", fontSize = 24.sp)
                            Column {
                                Text(
                                    text = "क्या हाल है? (Kya Haal Hai?)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, color = adviceColor.copy(alpha = 0.7f))
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = adviceText,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, color = adviceColor)
                                )
                            }
                        }
                    }
                }

                // INTERACTIVE LINE TREND CHART
                item {
                    TrendDashboardChart(
                        dailyTrend = dailyTrend,
                        maxDays = selectedCal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    )
                }

                // SEARCH BAR & LEDGER TEXT QUERY CONTROLS
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("ledger_search_input"),
                        leadingIcon = {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "खोजें", tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        placeholder = { Text("रकम, श्रेणी, या नोट खोजें...") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }

                // FILTERS SELECTION CHIPS row
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChipItem(
                            label = "सब (All)",
                            selected = selectedTypeFilter == "ALL",
                            onClick = { selectedTypeFilter = "ALL" },
                            color = MaterialTheme.colorScheme.primary
                        )
                        FilterChipItem(
                            label = "कमाई (Kamaai)",
                            selected = selectedTypeFilter == "EARNING",
                            onClick = { selectedTypeFilter = "EARNING" },
                            color = FinanceEarning
                        )
                        FilterChipItem(
                            label = "खर्चा (Kharcha)",
                            selected = selectedTypeFilter == "EXPENSE",
                            onClick = { selectedTypeFilter = "EXPENSE" },
                            color = FinanceExpense
                        )
                        FilterChipItem(
                            label = "बचत (Bachat)",
                            selected = selectedTypeFilter == "SAVING",
                            onClick = { selectedTypeFilter = "SAVING" },
                            color = FinanceSaving
                        )
                    }
                }

                // MONTHLY KPI SUMMARY OVERVIEW CARDS
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricBlock(
                            title = "कुल कमाई",
                            subtitle = "Kamaai Earning",
                            value = stats.earnings,
                            containerColor = EarningsBg,
                            tintColor = EarningsText,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBlock(
                            title = "कुल खर्चा",
                            subtitle = "Kharcha Expense",
                            value = stats.expenses,
                            containerColor = ExpensesBg,
                            tintColor = ExpensesText,
                            modifier = Modifier.weight(1f)
                        )
                        MetricBlock(
                            title = "कुल बचत",
                            subtitle = "Bachat Saving",
                            value = stats.savings,
                            containerColor = SavingsBg,
                            tintColor = SavingsText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // RECENT TRANSACTIONS LEDGER RECORDS
                val filteredList = transactions.filter {
                    selectedTypeFilter == "ALL" || it.type == selectedTypeFilter
                }

                val finalQueryList = filteredList.filter { tx ->
                    searchQuery.isEmpty() ||
                    tx.category.contains(searchQuery, ignoreCase = true) ||
                    tx.description.contains(searchQuery, ignoreCase = true) ||
                    tx.party.contains(searchQuery, ignoreCase = true) ||
                    tx.amount.toString().contains(searchQuery)
                }

                if (finalQueryList.isEmpty()) {
                    item {
                        EmptyLedgerState()
                    }
                } else {
                    items(finalQueryList) { tx ->
                        TransactionRow(
                            transaction = tx,
                            onDeleteClick = { viewModel.deleteTransaction(tx) }
                        )
                    }
                }
            }
        } else {
            // "PLAN" TAB - PLANNING EXPENSE & SAVINGS GAUGE
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header details
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, BorderSlate)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "🎯 मासिक लक्ष्य और सीमा (Monthly Planning Advisor)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "पिछले ३ महीनों के औसत के आधार पर अपने बजट को सुव्यवस्थित करें।",
                                style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                            )
                        }
                    }
                }

                // 1. DYNAMIC EXPENSE TRACKER CARD
                item {
                    val expensePercent = if (kharchaLimit > 0) (stats.expenses / kharchaLimit).coerceIn(0.0, 1.0) else 0.0
                    val isLimitExceeded = stats.expenses > kharchaLimit
                    val barColor = when {
                        stats.expenses > kharchaLimit * 0.90 -> ExpensesText // Red if limit exceeded or almost used
                        stats.expenses > kharchaLimit * 0.80 -> Color(0xFFF57C00) // Orange Warning
                        else -> EarningsText // Green if healthy
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (isLimitExceeded) ExpensesText.copy(alpha = 0.2f) else BorderSlate)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "🚫 महीने का खर्चा सीमा",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Monthly Expense Limit",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    )
                                }
                                Text(
                                    text = formatIndianRupee(kharchaLimit),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Slider(
                                value = kharchaLimit.toFloat(),
                                onValueChange = { kharchaLimit = it.toDouble() },
                                valueRange = 5000f..50000f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { expensePercent.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(barColor.copy(alpha = 0.1f), RoundedCornerShape(5.dp)),
                                color = barColor,
                                strokeCap = StrokeCap.Round
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "खर्च हुआ: ${formatIndianRupee(stats.expenses)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "${(expensePercent * 100).toInt()}% सीमा उपयोग",
                                    style = MaterialTheme.typography.bodySmall.copy(color = barColor, fontWeight = FontWeight.Bold)
                                )
                            }
                        }
                    }
                }

                // 2. SAVINGS ACHIEVEMENTS TRACKER CARD
                item {
                    val savingPercent = if (bachatGoal > 0) (stats.savings / bachatGoal).coerceIn(0.0, 1.0) else 0.0
                    val isGoalAchieved = stats.savings >= bachatGoal

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, if (isGoalAchieved) EarningsText.copy(alpha = 0.2f) else BorderSlate)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "💰 महीने की बचत लक्ष्य",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                    )
                                    Text(
                                        text = "Monthly Saving Goal",
                                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    )
                                }
                                Text(
                                    text = formatIndianRupee(bachatGoal),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Slider(
                                value = bachatGoal.toFloat(),
                                onValueChange = { bachatGoal = it.toDouble() },
                                valueRange = 1000f..20000f,
                                steps = 19,
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { savingPercent.toFloat() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .background(SavingsText.copy(alpha = 0.1f), RoundedCornerShape(5.dp)),
                                color = if (isGoalAchieved) EarningsText else SavingsText,
                                strokeCap = StrokeCap.Round
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "बचत हुई: ${formatIndianRupee(stats.savings)}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = if (isGoalAchieved) "शाबाश! लक्ष्य पूर्ण 🎉" else "${(savingPercent * 100).toInt()}% पूरा",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isGoalAchieved) EarningsText else SavingsText,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        }
                    }
                }

                // 3. BACKUP & EXPORT PLANNER CALLOUT
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("🛡️", fontSize = 24.sp)
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Backup reminder (बैकअप रिमाइंडर)",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "सुरक्षित रहने के लिए हर महीने Excel रिपोर्ट फाइल डाउनलोड करें और सुरक्षित रखें।",
                                    style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = onExportClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("📥 अभी डाउनलोड करें (Download Now)")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- FAB FOR NEW TRANSACTION ---
    Box(
        modifier = Modifier
            .fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .padding(24.dp)
                .navigationBarsPadding()
                .testTag("add_transaction_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Naya Entry - Record Ledger Entry"
            )
        }
    }

    // --- ADD TRANSACTION DIALOG ("Naya Entry") ---
    if (showAddDialog) {
        AddTransactionDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { amount, type, category, dateMillis, description, party ->
                viewModel.addTransaction(amount, type, category, dateMillis, description, party)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun MetricBlock(
    title: String,
    subtitle: String,
    value: Double,
    containerColor: Color,
    tintColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, BorderSlate)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = tintColor
                )
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 8.sp,
                    color = tintColor.copy(alpha = 0.5f)
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatIndianRupee(value),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Black,
                    color = tintColor,
                    fontSize = 15.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun TrendDashboardChart(
    dailyTrend: Map<Int, DailyTrend>,
    maxDays: Int
) {
    var activeFocusDay by remember { mutableStateOf<Int?>(null) }
    var activeFocusData by remember { mutableStateOf<DailyTrend?>(null) }
    
    var showEarningAxis by remember { mutableStateOf(true) }
    var showExpenseAxis by remember { mutableStateOf(true) }
    var showSavingAxis by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, BorderSlate),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "दैनिक गतिविधि सूचक (Daily Trend)",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Touch any day step to inspect",
                        style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    )
                }
                
                if (activeFocusDay != null) {
                    Text(
                        text = "Clear Tooltip",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.clickable {
                            activeFocusDay = null
                            activeFocusData = null
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Graph representation using custom canvas
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                var maxDailyAmount = 2000.0 // Default baseline
                dailyTrend.values.forEach { trend ->
                    if (showEarningAxis && trend.earnings > maxDailyAmount) maxDailyAmount = trend.earnings
                    if (showExpenseAxis && trend.expenses > maxDailyAmount) maxDailyAmount = trend.expenses
                    if (showSavingAxis && trend.savings > maxDailyAmount) maxDailyAmount = trend.savings
                }

                val maxScaledAmount = maxDailyAmount * 1.15

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(dailyTrend, maxDays, maxScaledAmount) {
                            detectTapGestures(
                                onPress = { offset ->
                                    val sectionWidth = size.width / maxDays
                                    val calculatedDay = ((offset.x / sectionWidth).toInt() + 1).coerceIn(1, maxDays)
                                    activeFocusDay = calculatedDay
                                    activeFocusData = dailyTrend[calculatedDay]
                                }
                            )
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    val colWidth = width / maxDays

                    // Draw grid guidelines
                    val gridLinesCount = 3
                    for (i in 0..gridLinesCount) {
                        val y = (height / gridLinesCount) * i
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.15f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    val earningPoints = ArrayList<Offset>()
                    val expensePoints = ArrayList<Offset>()
                    val savingPoints = ArrayList<Offset>()

                    for (day in 1..maxDays) {
                        val trend = dailyTrend[day] ?: DailyTrend(0.0, 0.0, 0.0)
                        val x = (day - 0.5f) * colWidth

                        if (showEarningAxis) {
                            val yEarn = height - ((trend.earnings / maxScaledAmount) * height).toFloat()
                            earningPoints.add(Offset(x, yEarn))
                        }
                        if (showExpenseAxis) {
                            val yExp = height - ((trend.expenses / maxScaledAmount) * height).toFloat()
                            expensePoints.add(Offset(x, yExp))
                        }
                        if (showSavingAxis) {
                            val ySave = height - ((trend.savings / maxScaledAmount) * height).toFloat()
                            savingPoints.add(Offset(x, ySave))
                        }
                    }

                    fun drawTrendLine(points: List<Offset>, color: Color) {
                        if (points.size < 2) return
                        val path = Path().apply {
                            moveTo(points[0].x, points[0].y)
                            for (i in 1 until points.size) {
                                val prev = points[i - 1]
                                val curr = points[i]
                                val controlX = (prev.x + curr.x) / 2f
                                cubicTo(controlX, prev.y, controlX, curr.y, curr.x, curr.y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = color,
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }

                    if (showEarningAxis && earningPoints.isNotEmpty()) drawTrendLine(earningPoints, FinanceEarning)
                    if (showExpenseAxis && expensePoints.isNotEmpty()) drawTrendLine(expensePoints, FinanceExpense)
                    if (showSavingAxis && savingPoints.isNotEmpty()) drawTrendLine(savingPoints, FinanceSaving)

                    activeFocusDay?.let { fd ->
                        val focusX = (fd - 0.5f) * colWidth
                        drawLine(
                            color = Color.Gray.copy(alpha = 0.4f),
                            start = Offset(focusX, 0f),
                            end = Offset(focusX, height),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 5.dp.toPx(),
                            center = Offset(focusX, height / 2f),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = activeFocusDay != null,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                activeFocusDay?.let { day ->
                    val data = activeFocusData ?: DailyTrend(0.0, 0.0, 0.0)
                    Surface(
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.04f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "दिन (Day) $day का हिसाब:",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("कमाई", style = MaterialTheme.typography.labelSmall, color = FinanceEarning)
                                    Text(formatIndianRupee(data.earnings), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("खर्चा", style = MaterialTheme.typography.labelSmall, color = FinanceExpense)
                                    Text(formatIndianRupee(data.expenses), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("बचत", style = MaterialTheme.typography.labelSmall, color = FinanceSaving)
                                    Text(formatIndianRupee(data.savings), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Curve configuration labels toggler
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChartLegendToggle(label = "कमाई (Earning)", color = FinanceEarning, active = showEarningAxis, onClick = { showEarningAxis = !showEarningAxis })
                ChartLegendToggle(label = "खर्चा (Expense)", color = FinanceExpense, active = showExpenseAxis, onClick = { showExpenseAxis = !showExpenseAxis })
                ChartLegendToggle(label = "बचत (Saving)", color = FinanceSaving, active = showSavingAxis, onClick = { showSavingAxis = !showSavingAxis })
            }
        }
    }
}

@Composable
fun ChartLegendToggle(
    label: String,
    color: Color,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (active) color else Color.LightGray, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
            )
        )
    }
}

@Composable
fun FilterChipItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    color: Color
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) color.copy(alpha = 0.15f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        ),
        modifier = Modifier.padding(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun TransactionRow(
    transaction: TransactionEntity,
    onDeleteClick: () -> Unit
) {
    val bulletColor = when (transaction.type) {
        "EARNING" -> FinanceEarning
        "EXPENSE" -> FinanceExpense
        "SAVING" -> FinanceSaving
        else -> MaterialTheme.colorScheme.primary
    }

    val typePrefix = when (transaction.type) {
        "EARNING" -> "+"
        "EXPENSE" -> "-"
        "SAVING" -> "○"
        else -> ""
    }

    val dateString = remember(transaction.dateMillis) {
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        sdf.format(Date(transaction.dateMillis))
    }

    val categoryEmoji = remember(transaction.category) {
        val cat = transaction.category
        when {
            cat.contains("राशन") || cat.contains("Grocery") -> "🍛"
            cat.contains("बिजली") || cat.contains("Bills") -> "⚡"
            cat.contains("आना") || cat.contains("Travel") -> "🚗"
            cat.contains("दवाई") || cat.contains("Health") -> "🏥"
            cat.contains("पढ़ाई") || cat.contains("Education") -> "📚"
            cat.contains("पिग्गी") || cat.contains("Piggy") -> "🏦"
            cat.contains("एफडी") || cat.contains("FD") -> "💼"
            cat.contains("शेयर") || cat.contains("Stocks") -> "📈"
            cat.contains("जेवर") || cat.contains("Gold") -> "👑"
            cat.contains("तनख्वाह") || cat.contains("Salary") -> "💰"
            cat.contains("ठेका") || cat.contains("Freelance") -> "💻"
            cat.contains("किराया") || cat.contains("Rent") -> "🏠"
            cat.contains("ब्याज") || cat.contains("Interest") -> "💳"
            else -> "📝"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, BorderSlate),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular icon representation
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(bulletColor.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = categoryEmoji, fontSize = 18.sp)
                }

                Column(modifier = Modifier.weight(1f)) {
                    val displayTitle = if (transaction.description.isNotBlank()) {
                        transaction.description
                    } else {
                        transaction.category
                    }
                    
                    val metadataString = if (transaction.party.isNotBlank()) {
                        "लेन-देन: ${transaction.party}  •  $dateString"
                    } else {
                        "$dateString"
                    }

                    Text(
                        text = displayTitle,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${transaction.category}  |  $metadataString",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "$typePrefix${formatIndianRupee(transaction.amount)}",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp
                    ),
                    color = bulletColor
                )

                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete entry",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyLedgerState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.List,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "कोई लेन-देन नहीं मिला (No Records)",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            textAlign = TextAlign.Center
        )
        Text(
            text = "नयी प्रविष्टि जोड़ने के लिए '+' दबाएं।",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, type: String, category: String, dateMillis: Long, description: String, party: String) -> Unit
) {
    var amountString by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("EXPENSE") } // "EXPENSE", "SAVING", "EARNING"
    var selectedCategory by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var party by remember { mutableStateOf("") }
    
    var useTodayDate by remember { mutableStateOf(true) }
    var customDayOfMonth by remember { mutableStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH).toString()) }

    // Indian themed entries categories representation
    val categories = remember(selectedType) {
        when (selectedType) {
            "EARNING" -> listOf(
                "तनख्वाह (Salary)",
                "ठेका (Freelance)",
                "किराया (Rent)",
                "ब्याज (Interest)",
                "दूसरा (Other)"
            )
            "EXPENSE" -> listOf(
                "राशन (Grocery)",
                "बिजली-पानी (Bills)",
                "आना-जाना (Travel)",
                "दवाई (Health)",
                "पढ़ाई (Education)",
                "दूसरा (Other)"
            )
            "SAVING" -> listOf(
                "पिग्गी बैंक (Piggy Bank)",
                "एफडी (FD)",
                "शेयर (Stocks)",
                "जेवर (Gold)",
                "दूसरा (Other)"
            )
            else -> listOf("दूसरा (Other)")
        }
    }

    LaunchedEffect(selectedType) {
        selectedCategory = categories[0]
    }

    var showDropdownMenu by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            border = BorderStroke(1.dp, BorderSlate)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "✍️ नई प्रविष्टि बही (Naya Entry)",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                // AMOUNT TEXT INPUT
                OutlinedTextField(
                    value = amountString,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.toDoubleOrNull() != null || input == ".") {
                            amountString = input
                        }
                    },
                    label = { Text("रकम (Amount in ₹)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_amount_input")
                )

                // TRANSACTION TYPE SELECTOR PILLS (Kharcha, Bachat, Kamaai)
                Text("प्रकार (Type)", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TypeSelectorPill(
                        label = "💰 कमाई (Kamaai)",
                        selected = selectedType == "EARNING",
                        color = FinanceEarning,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedType = "EARNING" }
                    )
                    TypeSelectorPill(
                        label = "🍛 खर्चा (Kharcha)",
                        selected = selectedType == "EXPENSE",
                        color = FinanceExpense,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedType = "EXPENSE" }
                    )
                    TypeSelectorPill(
                        label = "🏦 बचत (Bachat)",
                        selected = selectedType == "SAVING",
                        color = FinanceSaving,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { selectedType = "SAVING" }
                    )
                }

                // CATEGORY DROPDOWN SELECTOR
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        label = { Text("श्रेणी (Category)") },
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showDropdownMenu = !showDropdownMenu },
                        trailingIcon = {
                            IconButton(onClick = { showDropdownMenu = !showDropdownMenu }) {
                                Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "Choose Category")
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = showDropdownMenu,
                        onDismissRequest = { showDropdownMenu = false },
                        modifier = Modifier.fillMaxWidth(0.8f)
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category) },
                                onClick = {
                                    selectedCategory = category
                                    showDropdownMenu = false
                                }
                            )
                        }
                    }
                }

                // "PAID TO / RECEIVED FROM" PARTY NAMES INPUT
                val partyLabel = when (selectedType) {
                    "EARNING" -> "किससे मिला? (Received From)"
                    "EXPENSE" -> "किसको दिया? (Paid To)"
                    "SAVING" -> "कहाँ जमा किया? (Deposited Destination)"
                    else -> "लेन-देन पक्षकार (Party Name)"
                }
                
                OutlinedTextField(
                    value = party,
                    onValueChange = { party = it },
                    label = { Text(partyLabel) },
                    singleLine = true,
                    placeholder = { Text("ई.g. किराना दुकान, गुप्ता जी, SBI") },
                    modifier = Modifier.fillMaxWidth()
                )

                // NOTES DETAILS PROFILES INPUT
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("विवरण / नोट (optional note)") },
                    singleLine = true,
                    placeholder = { Text("नोट लिखें जैसे: सब्जी ख़रीदा") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_desc_input")
                )

                // DATE SELECTOR CHIPS
                Text("तारीख (Transaction Date)", style = MaterialTheme.typography.labelSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        onClick = { useTodayDate = true },
                        shape = RoundedCornerShape(8.dp),
                        color = if (useTodayDate) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (useTodayDate) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "आज (Today)", 
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Surface(
                        onClick = { useTodayDate = false },
                        shape = RoundedCornerShape(8.dp),
                        color = if (!useTodayDate) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent,
                        border = BorderStroke(1.dp, if (!useTodayDate) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f))
                    ) {
                        Text(
                            "अन्य तारीख (Specific)", 
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    if (!useTodayDate) {
                        OutlinedTextField(
                            value = customDayOfMonth,
                            onValueChange = { dayInput ->
                                val intVal = dayInput.toIntOrNull()
                                if (dayInput.isEmpty() || (intVal != null && intVal in 1..31)) {
                                    customDayOfMonth = dayInput
                                }
                            },
                            label = { Text("दिन (1-31)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(90.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ACTIONS ROW BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("रद्द (Cancel)")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountString.toDoubleOrNull()
                            if (amt != null && amt > 0.0) {
                                val calendar = Calendar.getInstance().apply {
                                    if (!useTodayDate) {
                                        val day = customDayOfMonth.toIntOrNull() ?: 1
                                        set(Calendar.DAY_OF_MONTH, day)
                                    }
                                }
                                onConfirm(amt, selectedType, selectedCategory, calendar.timeInMillis, description, party)
                            }
                        },
                        enabled = amountString.isNotBlank() && amountString.toDoubleOrNull() != null && amountString.toDoubleOrNull()!! > 0.0,
                        modifier = Modifier.testTag("submit_transaction_button")
                    ) {
                        Text("लिखें (Save Record)")
                    }
                }
            }
        }
    }
}

@Composable
fun TypeSelectorPill(
    label: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) color.copy(alpha = 0.15f) else Color.Transparent,
        border = BorderStroke(
            1.dp,
            if (selected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
        )
    ) {
        Text(
            text = label,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall.copy(
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) color else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontSize = 11.sp
            ),
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 2.dp)
        )
    }
}
