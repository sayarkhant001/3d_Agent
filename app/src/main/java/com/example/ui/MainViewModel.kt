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

class MainViewModel(private val repository: LotteryRepository, private val prefs: android.content.SharedPreferences) : ViewModel() {

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
        
    val bannedNumbers: StateFlow<List<BannedNumber>> = repository.allBannedNumbers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val archivedVouchers: StateFlow<List<VoucherWithCustomer>> = repository.archivedVouchers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExportRecords: StateFlow<List<ExportRecordWithNumbers>> = repository.allExportRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    var currentBatch = MutableStateFlow(15)
                
    
    val appPassword = MutableStateFlow("")
    val voucherFooterText = MutableStateFlow("ထွက်လျော်မည်။")
    val printerSettings = MutableStateFlow("")
    val bannedNumberEvent = kotlinx.coroutines.flow.MutableSharedFlow<Boolean>()
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

    init {
        appPassword.value = prefs.getString("appPassword", "") ?: ""
        voucherFooterText.value = prefs.getString("voucherFooterText", "ထွက်လျော်မည်။") ?: "ထွက်လျော်မည်။"
        printerSettings.value = prefs.getString("printerSettings", "") ?: ""
    }

    fun updateAppPassword(password: String) {
        appPassword.value = password
        prefs.edit().putString("appPassword", password).apply()
    }

    fun updateVoucherFooterText(text: String) {
        voucherFooterText.value = text
        prefs.edit().putString("voucherFooterText", text).apply()
    }

    fun updatePrinterSettings(text: String) {
        printerSettings.value = text
        prefs.edit().putString("printerSettings", text).apply()
    }

    fun addBannedNumber(number: String) {
        viewModelScope.launch {
            repository.insertBannedNumber(BannedNumber(number = number))
        }
    }
    fun deleteBannedNumber(bannedNumber: BannedNumber) {
        viewModelScope.launch {
            repository.deleteBannedNumber(bannedNumber)
        }
    }
    fun resetAndArchive() {
        viewModelScope.launch {
            repository.archiveAndReset(currentBatch.value - 2)
            currentBatch.value = currentBatch.value + 1
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

    fun updateCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
        }
    }

    fun addCustomer(name: String, commissionRate: Double, multiplier: Int) {
        viewModelScope.launch {
            repository.insertCustomer(Customer(name = name, commissionRate = commissionRate, multiplier = multiplier))
        }
    }

    fun addVoucherAndBets(customerId: Int, time: String, rawInput: String, remark: String = "") {
        viewModelScope.launch {
            val bets = parseBets(rawInput)
            val banned = bannedNumbers.value.map { it.number }
            val validBets = bets.filter { it.number !in banned }
            if (validBets.size < bets.size) {
                bannedNumberEvent.emit(true)
            }
            if (validBets.isNotEmpty()) {
                val totalAmount = validBets.sumOf { it.amount }
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = dateFormat.format(Date())
                val voucher = Voucher(customerId = customerId, batchNumber = currentBatch.value, date = date, time = time, totalAmount = totalAmount, remark = remark)
                repository.insertVoucherWithBets(voucher, validBets)
            }
        }
    }


    fun addVoucherWithBetList(customerId: Int, time: String, bets: List<Bet>, remark: String = "") {
        viewModelScope.launch {
            val banned = bannedNumbers.value.map { it.number }
            val validBets = bets.filter { it.number !in banned }
            if (validBets.size < bets.size) {
                bannedNumberEvent.emit(true)
            }
            if (validBets.isNotEmpty()) {
                val totalAmount = validBets.sumOf { it.amount }
                val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val date = dateFormat.format(Date())
                val voucher = Voucher(customerId = customerId, batchNumber = currentBatch.value, date = date, time = time, totalAmount = totalAmount, remark = remark)
                repository.insertVoucherWithBets(voucher, validBets)
            }
        }
    }


    fun parseBets(input: String): List<Bet> {
        val bets = mutableListOf<Bet>()
        
        // Remove 'ks' (case insensitive)
        var text = input.replace(Regex("(?i)ks"), "")
        // Remove spaces around separators
        text = text.replace(Regex("\\s*([.,/+\\-_=:])\\s*"), "$1")
        // Remove spaces around 'R' (case insensitive)
        text = text.replace(Regex("\\s*(?i)r\\s*"), "R")
        // Split by remaining spaces
        val blocks = text.split(Regex("\\s+"))
        
        for (block in blocks) {
            if (block.isEmpty()) continue
            
            var numbersStr = ""
            var amount1Str = ""
            var amount2Str = ""
            
            val tailRegex = Regex("([-:/.,_=]+)?(\\d+)(?:R(\\d+))?$")
            val match = tailRegex.find(block)
            
            if (match != null) {
                if (match.range.first == 0) {
                    val amt1 = match.groups[2]?.value ?: ""
                    val amt2 = match.groups[3]?.value ?: ""
                    if (amt2.isNotEmpty()) {
                        numbersStr = amt1 + "R"
                        amount1Str = amt2
                    } else {
                        numbersStr = amt1
                    }
                } else {
                    numbersStr = block.substring(0, match.range.first)
                    amount1Str = match.groups[2]?.value ?: ""
                    amount2Str = match.groups[3]?.value ?: ""
                }
            } else {
                numbersStr = block
            }
            
            val amount = amount1Str.toIntOrNull() ?: continue
            val rAmount = amount2Str.toIntOrNull()
            
            val chunks = numbersStr.split(Regex("[.,/+\\-_:]+"))
            for (chunk in chunks) {
                if (chunk.isEmpty()) continue
                val hasR = chunk.endsWith("R")
                val baseNum = if (hasR) chunk.dropLast(1) else chunk
                
                // Allow 2 or 3 digit numbers
                if (baseNum.length in 2..3 && baseNum.all { it.isDigit() }) {
                    // Base amount
                    bets.add(Bet(voucherId = 0, number = baseNum, amount = amount))
                    
                    if (rAmount != null && rAmount > 0) {
                        // Global R amount for this block
                        val perms = com.example.logic.NumberGenerator.permutations(baseNum)
                        for (p in perms) {
                            if (p != baseNum) {
                                bets.add(Bet(voucherId = 0, number = p, amount = rAmount))
                            }
                        }
                    } else if (hasR) {
                        // R attached to this specific number, no global R amount
                        val perms = com.example.logic.NumberGenerator.permutations(baseNum)
                        for (p in perms) {
                            if (p != baseNum) {
                                bets.add(Bet(voucherId = 0, number = p, amount = amount))
                            }
                        }
                    }
                }
            }
        }
        
        return bets
    }

}
