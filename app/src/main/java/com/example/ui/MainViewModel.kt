package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class LedgerExposure(
    val number: String,
    val totalBetAmount: Int,
    val exportedAmount: Int,
    val netHeldAmount: Int,
    val overflowAmount: Int
)

class MainViewModel(private val repository: LotteryRepository) : ViewModel() {

    val customers: StateFlow<List<Customer>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val vouchersWithCustomer: StateFlow<List<VoucherWithCustomer>> = repository.allVouchersWithCustomer
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val vouchersWithBets: StateFlow<List<VoucherWithBets>> = repository.allVouchersWithBets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allBets: StateFlow<List<Bet>> = repository.allBets
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val numberExposures: StateFlow<List<NumberExposure>> = repository.numberExposures
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    val allExportRecords: StateFlow<List<ExportRecordWithNumbers>> = repository.allExportRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    var currentBatch = MutableStateFlow(15)
    var brakeLimit = MutableStateFlow(3000)

    val ledgerExposures: StateFlow<List<LedgerExposure>> = kotlinx.coroutines.flow.combine(
        vouchersWithBets,
        allExportRecords,
        currentBatch,
        brakeLimit
    ) { vouchers, exports, batch, brake ->
        val batchVouchers = vouchers.filter { it.voucher.batchNumber == batch }
        val batchExports = exports.filter { it.record.batchNumber == batch }

        val betMap = mutableMapOf<String, Int>()
        batchVouchers.forEach { vb ->
            vb.bets.forEach { bet ->
                betMap[bet.number] = (betMap[bet.number] ?: 0) + bet.amount
            }
        }

        val exportMap = mutableMapOf<String, Int>()
        batchExports.forEach { eb ->
            eb.numbers.forEach { num ->
                exportMap[num.number] = (exportMap[num.number] ?: 0) + num.amount
            }
        }

        val results = mutableListOf<LedgerExposure>()
        betMap.forEach { (number, grossAmount) ->
            val exported = exportMap[number] ?: 0
            val netHeld = grossAmount - exported
            val overflow = if (netHeld > brake) netHeld - brake else 0
            if (grossAmount > 0) {
                results.add(LedgerExposure(number, grossAmount, exported, netHeld, overflow))
            }
        }
        results.sortedByDescending { it.netHeldAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun exportOverflow() {
        val currentExposures = ledgerExposures.value
        val toExport = currentExposures.filter { it.overflowAmount > 0 }
        if (toExport.isEmpty()) return

        viewModelScope.launch {
            val totalAmount = toExport.sumOf { it.overflowAmount }
            val record = ExportRecord(batchNumber = currentBatch.value, type = "ဘရိတ်ကျော်ငွေ (Overflow)", totalAmount = totalAmount)
            val recordId = repository.insertExportRecord(record).toInt()
            
            val exportNumbers = toExport.map { 
                ExportedNumber(exportRecordId = recordId, number = it.number, amount = it.overflowAmount)
            }
            repository.insertExportedNumbers(exportNumbers)
        }
    }

    fun exportUnderBrake() {
        val currentExposures = ledgerExposures.value
        val toExport = currentExposures.filter { (it.netHeldAmount - it.overflowAmount) > 0 }
        if (toExport.isEmpty()) return

        viewModelScope.launch {
            val totalAmount = toExport.sumOf { it.netHeldAmount - it.overflowAmount }
            val record = ExportRecord(batchNumber = currentBatch.value, type = "ဘရိတ်အောက်ငွေ (Under-Brake)", totalAmount = totalAmount)
            val recordId = repository.insertExportRecord(record).toInt()
            
            val exportNumbers = toExport.map { 
                ExportedNumber(exportRecordId = recordId, number = it.number, amount = it.netHeldAmount - it.overflowAmount)
            }
            repository.insertExportedNumbers(exportNumbers)
        }
    }

    fun addCustomer(name: String, commissionRate: Double) {
        viewModelScope.launch {
            repository.insertCustomer(Customer(name = name, commissionRate = commissionRate))
        }
    }

    fun addVoucherAndBets(customerId: Int, time: String, rawInput: String) {
        viewModelScope.launch {
            val bets = parseBets(rawInput)
            if (bets.isNotEmpty()) {
                val totalAmount = bets.sumOf { it.amount }
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = dateFormat.format(Date())
                val voucher = Voucher(customerId = customerId, batchNumber = currentBatch.value, date = date, time = time, totalAmount = totalAmount)
                repository.insertVoucherWithBets(voucher, bets)
            }
        }
    }

    fun addVoucherWithBetList(customerId: Int, time: String, bets: List<Bet>) {
        viewModelScope.launch {
            if (bets.isNotEmpty()) {
                val totalAmount = bets.sumOf { it.amount }
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = dateFormat.format(Date())
                val voucher = Voucher(customerId = customerId, batchNumber = currentBatch.value, date = date, time = time, totalAmount = totalAmount)
                repository.insertVoucherWithBets(voucher, bets)
            }
        }
    }

    fun parseBets(input: String): List<Bet> {
        val bets = mutableListOf<Bet>()
        
        // Remove spaces and 'Ks' (case insensitive) first
        val cleanInput = input.replace(" ", "").replace(Regex("(?i)Ks"), "")
        
        // Split input by newlines to process line by line
        val lines = cleanInput.split("\n")
        
        for (line in lines) {
            if (line.isBlank()) continue
            
            // Normalize separators to '='
            var normalizedLine = line.replace("_", "=").replace("-", "=").replace("/", "=").replace(",", "=")
            
            // Handle R permutation (e.g. 434R1000)
            if (normalizedLine.contains("R", ignoreCase = true)) {
                val parts = normalizedLine.split(Regex("(?i)R"))
                if (parts.size == 2) {
                    val number = parts[0]
                    val amount = parts[1].replace("=", "").toIntOrNull() ?: continue
                    val permutations = com.example.logic.NumberGenerator.permutations(number)
                    for (p in permutations) {
                        bets.add(Bet(voucherId = 0, number = p, amount = amount))
                    }
                }
            } else if (normalizedLine.contains("=")) {
                val parts = normalizedLine.split("=")
                if (parts.size == 2) {
                    val numbersPart = parts[0]
                    val amount = parts[1].toIntOrNull() ?: continue
                    
                    // Split numbers by dot if they are multi-number bets (e.g. 434.502.601=1000)
                    val numbers = numbersPart.split(".")
                    for (n in numbers) {
                        if (n.length == 3 && n.all { it.isDigit() }) {
                            bets.add(Bet(voucherId = 0, number = n, amount = amount))
                        }
                    }
                }
            } else {
                // Check if it's a direct format like 434-1000 where they just typed 4341000? 
                // The regex logic should handle well enough.
            }
        }
        return bets
    }

}
