package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.TransactionEntity
import com.example.data.TransactionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TransactionViewModel(private val repository: TransactionRepository) : ViewModel() {

    // Selected year and month for active filtering (0-indexed month)
    private val _selectedCalendar = MutableStateFlow<Calendar>(Calendar.getInstance())
    val selectedCalendar: StateFlow<Calendar> = _selectedCalendar

    // All database transactions
    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Pre-populate database with beautiful sample transactions if empty
        viewModelScope.launch {
            repository.allTransactions.collect { list ->
                if (list.isEmpty()) {
                    populateSampleData()
                }
            }
        }
    }

    // Filtered transactions for the currently selected month and year
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        selectedCalendar
    ) { transactions, calendar ->
        val targetYear = calendar.get(Calendar.YEAR)
        val targetMonth = calendar.get(Calendar.MONTH)

        transactions.filter { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
            txCal.get(Calendar.YEAR) == targetYear && txCal.get(Calendar.MONTH) == targetMonth
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Daily summary for the selected month (Map of DayOfMonth to Triplet(Earnings, Expenses, Savings))
    val dailyTrendData: StateFlow<Map<Int, DailyTrend>> = combine(
        filteredTransactions,
        selectedCalendar
    ) { transactions, calendar ->
        val maxDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val trendMap = mutableMapOf<Int, DailyTrend>()
        
        // Initialize map for all days of the month
        for (i in 1..maxDays) {
            trendMap[i] = DailyTrend(earnings = 0.0, expenses = 0.0, savings = 0.0)
        }

        // Aggregate transactions by absolute day
        for (tx in transactions) {
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
            val day = txCal.get(Calendar.DAY_OF_MONTH)
            val current = trendMap[day] ?: DailyTrend(0.0, 0.0, 0.0)
            
            val updated = when (tx.type) {
                "EARNING" -> current.copy(earnings = current.earnings + tx.amount)
                "EXPENSE" -> current.copy(expenses = current.expenses + tx.amount)
                "SAVING" -> current.copy(savings = current.savings + tx.amount)
                else -> current
            }
            trendMap[day] = updated
        }
        trendMap
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyMap()
    )

    // Overall month statistics
    val monthlyStats: StateFlow<MonthlyStats> = filteredTransactions
        .combine(selectedCalendar) { transactions, _ ->
            var earnings = 0.0
            var expenses = 0.0
            var savings = 0.0

            for (tx in transactions) {
                when (tx.type) {
                    "EARNING" -> earnings += tx.amount
                    "EXPENSE" -> expenses += tx.amount
                    "SAVING" -> savings += tx.amount
                }
            }
            MonthlyStats(earnings, expenses, savings)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = MonthlyStats(0.0, 0.0, 0.0)
        )

    fun changeMonth(amount: Int) {
        val current = _selectedCalendar.value
        val newCal = Calendar.getInstance().apply {
            timeInMillis = current.timeInMillis
            add(Calendar.MONTH, amount)
        }
        _selectedCalendar.value = newCal
    }

    fun addTransaction(
        amount: Double,
        type: String,
        category: String,
        dateMillis: Long,
        description: String,
        party: String
    ) {
        viewModelScope.launch {
            val tx = TransactionEntity(
                amount = amount,
                type = type,
                category = category,
                dateMillis = dateMillis,
                description = description,
                party = party
            )
            repository.insert(tx)
        }
    }

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            repository.delete(tx)
        }
    }

    // Generate CSV string content
    fun generateCsvContent(): String {
        val transactions = filteredTransactions.value
        val csv = StringBuilder()
        // Standard UTF-8 BOM so Excel/Google Sheets reads Devanagari Hindi text correctly
        csv.append('\uFEFF')
        csv.append("तारीख (Date),प्रकार (Type),श्रेणी (Category),किससे / किसको (From/To),रकम (Amount),नोट (Note)\n")
        
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        for (tx in transactions) {
            val formattedDate = sdf.format(Date(tx.dateMillis))
            val localizedType = when (tx.type) {
                "EARNING" -> "कमाई (Kamaai)"
                "EXPENSE" -> "खर्चा (Kharcha)"
                "SAVING" -> "बचत (Bachat)"
                else -> tx.type
            }
            val safeCat = tx.category.replace("\"", "\"\"")
            val safeParty = tx.party.replace("\"", "\"\"")
            val safeDesc = tx.description.replace("\"", "\"\"")
            csv.append("$formattedDate,$localizedType,\"$safeCat\",\"$safeParty\",${tx.amount},\"$safeDesc\"\n")
        }
        return csv.toString()
    }

    fun getExportFileName(): String {
        val calendar = _selectedCalendar.value
        val sdf = SimpleDateFormat("MMMM_yyyy", Locale.getDefault())
        return "PocketDiary_${sdf.format(calendar.time)}.csv"
    }

    private suspend fun populateSampleData() {
        val calendar = Calendar.getInstance()
        val baseTime = calendar.timeInMillis
        val oneDayMillis = 86400000L

        // Current month indices with genuine Indian context
        val sampleTxs = listOf(
            TransactionEntity(
                amount = 45000.0,
                type = "EARNING",
                category = "तनख्वाह (Salary)",
                dateMillis = baseTime - (10 * oneDayMillis),
                description = "महीने की सैलरी",
                party = "सर्विस प्राइवेट लिमिटेड"
            ),
            TransactionEntity(
                amount = 12500.0,
                type = "EARNING",
                category = "ठेका (Freelance)",
                dateMillis = baseTime - (7 * oneDayMillis),
                description = "वेबसाइट डिजाइन काम",
                party = "राम कुमार भाई"
            ),
            TransactionEntity(
                amount = 3500.0,
                type = "EXPENSE",
                category = "राशन (Grocery)",
                dateMillis = baseTime - (5 * oneDayMillis),
                description = "महीने भर का सूखा राशन",
                party = "गुप्ता किराना स्टोर"
            ),
            TransactionEntity(
                amount = 1800.0,
                type = "EXPENSE",
                category = "बिजली-पानी (Bills)",
                dateMillis = baseTime - (4 * oneDayMillis),
                description = "बिजली और पानी बिल",
                party = "विद्युत वितरण बोर्ड"
            ),
            TransactionEntity(
                amount = 1200.0,
                type = "EXPENSE",
                category = "दवाई (Health)",
                dateMillis = baseTime - (3 * oneDayMillis),
                description = "दादी की बीपी दवाइयां",
                party = "श्री राम मेडिकल"
            ),
            TransactionEntity(
                amount = 500.0,
                type = "EXPENSE",
                category = "आना-जाना (Travel)",
                dateMillis = baseTime - (2 * oneDayMillis),
                description = "मेट्रो कार्ड रिचार्ज",
                party = "मेट्रो स्टेशन"
            ),
            TransactionEntity(
                amount = 2000.0,
                type = "SAVING",
                category = "पिग्गी बैंक (Piggy Bank)",
                dateMillis = baseTime - (5 * oneDayMillis),
                description = "दैनिक गुप्त बचत",
                party = "गुल्लक"
            ),
            TransactionEntity(
                amount = 5000.0,
                type = "SAVING",
                category = "एफडी (FD)",
                dateMillis = baseTime - (1 * oneDayMillis),
                description = "मासिक आवर्ती जमा",
                party = "भारतीय स्टेट बैंक (SBI)"
            ),
            TransactionEntity(
                amount = 3000.0,
                type = "SAVING",
                category = "जेवर (Gold)",
                dateMillis = baseTime,
                description = "सोने की छोटी बचत योजना",
                party = "तनिष्क ज्वेलर्स"
            )
        )

        for (tx in sampleTxs) {
            repository.insert(tx)
        }
    }
}

data class DailyTrend(
    val earnings: Double,
    val expenses: Double,
    val savings: Double
)

data class MonthlyStats(
    val earnings: Double,
    val expenses: Double,
    val savings: Double
)

class ViewModelFactory(private val repository: TransactionRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TransactionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TransactionViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
