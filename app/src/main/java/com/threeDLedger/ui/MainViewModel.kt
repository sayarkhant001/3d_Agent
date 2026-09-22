package com.threeDLedger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threeDLedger.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    val archivedBatchSummaries: StateFlow<List<com.threeDLedger.data.ArchiveBatchSummary>> = repository.archivedBatchSummaries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExportRecords: StateFlow<List<ExportRecordWithNumbers>> = repository.allExportRecords
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        
    var currentBatch = MutableStateFlow(prefs.getInt("currentBatch", 1))

    val appPassword = MutableStateFlow("")
    val voucherFooterText = MutableStateFlow("ထွက်လျော်မည်။")
    val printerSettings = MutableStateFlow("")
    val bannedNumberEvent = kotlinx.coroutines.flow.MutableSharedFlow<Boolean>()
    val bannedLimitNotificationEvent = kotlinx.coroutines.flow.MutableSharedFlow<List<com.threeDLedger.data.BannedLimitRemoval>>()
    var brakeLimit = MutableStateFlow(3000)

    // Winning number declared for the current batch (persisted per batch key)
    val winningNumber = MutableStateFlow("")

    init {
        loadWinningNumber()
        brakeLimit.value = prefs.getInt("brakeLimit", 3000)
        viewModelScope.launch {
            currentBatch.collect { batch ->
                prefs.edit().putInt("currentBatch", batch).apply()
                loadWinningNumber()
            }
        }
        viewModelScope.launch {
            repository.purgeOverflowArtifacts()
            ensureDefaultCustomer()
        }
    }

    private suspend fun ensureDefaultCustomer() {
        try {
            val list = repository.allCustomers.first()
            val hasNormal = list.any { !it.name.contains("တင်ကွက်") && !it.name.contains("overflow", ignoreCase = true) }
            if (!hasNormal) {
                repository.insertCustomer(Customer(name = "မိမိ (ကိုယ်တိုင်)", commissionRate = 0.0, multiplier = 600))
            }
        } catch (_: Exception) {}
    }

    fun saveWinningNumber(number: String, batch: Int = currentBatch.value) {
        if (batch == currentBatch.value) {
            winningNumber.value = number
        }
        prefs.edit().putString("winningNumber_$batch", number).apply()
    }

    fun clearWinningNumber(batch: Int = currentBatch.value) {
        if (batch == currentBatch.value) {
            winningNumber.value = ""
        }
        prefs.edit().remove("winningNumber_$batch").apply()
    }

    fun isBatchDeclared(batch: Int = currentBatch.value): Boolean {
        val num = prefs.getString("winningNumber_$batch", "") ?: ""
        return num.length == 3
    }

    fun loadWinningNumber() {
        winningNumber.value = prefs.getString("winningNumber_${currentBatch.value}", "") ?: ""
    }

    fun saveBrakeLimit(value: Int) {
        brakeLimit.value = value
        prefs.edit().putInt("brakeLimit", value).apply()
    }

    // ── Per-batch multipliers (saved when ပေါက်သီး is declared) ──────────────
    val savedExactMult = MutableStateFlow(600.0)
    val savedPermMult  = MutableStateFlow(10.0)
    val savedNearMult  = MutableStateFlow(10.0)

    fun saveMultipliers(exact: Double, tuwt: Double, near: Double = tuwt, batch: Int = currentBatch.value) {
        if (batch == currentBatch.value) {
            savedExactMult.value = exact
            savedPermMult.value  = tuwt
            savedNearMult.value  = near
        }
        prefs.edit()
            .putFloat("exactMult_$batch", exact.toFloat())
            .putFloat("permMult_$batch",  tuwt.toFloat())
            .putFloat("nearMult_$batch",  near.toFloat())
            .apply()
    }

    fun getWinningNumberForBatch(batch: Int): String =
        prefs.getString("winningNumber_$batch", "") ?: ""

    fun getMultipliersForBatch(batch: Int): Triple<Double, Double, Double> = Triple(
        prefs.getFloat("exactMult_$batch", 600f).toDouble(),
        prefs.getFloat("permMult_$batch",  10f).toDouble(),
        prefs.getFloat("nearMult_$batch",  10f).toDouble()
    )

    // ── Per-customer per-batch paid amount (persisted) ────────────────────────
    fun getPaidForBatch(customerId: Int, batchNumber: Int): Double =
        prefs.getFloat("paid_${customerId}_$batchNumber", 0f).toDouble()

    fun setPaidForBatch(customerId: Int, batchNumber: Int, amount: Double) {
        prefs.edit().putFloat("paid_${customerId}_$batchNumber", amount.toFloat()).apply()
    }

    /** Flow of all vouchers (incl. archived) for a specific batch number */
    fun getVouchersWithBetsByBatch(batchNumber: Int): kotlinx.coroutines.flow.Flow<List<com.threeDLedger.data.VoucherWithBets>> =
        repository.getVouchersWithBetsByBatch(batchNumber)


    val ledgerExposures: StateFlow<List<LedgerExposure>> = kotlinx.coroutines.flow.combine(
        vouchersWithBets,
        allExportRecords,
        currentBatch,
        brakeLimit
    ) { vouchers, exports, batch, brake ->
        val batchVouchers = vouchers.filter { it.voucher.batchNumber == batch }
        val batchExports = exports.filter { it.record.batchNumber == batch }

        // Total bets per number (gross)
        val betMap = mutableMapOf<String, Int>()
        batchVouchers.forEach { vb ->
            vb.bets.forEach { bet ->
                betMap[bet.number] = (betMap[bet.number] ?: 0) + bet.amount
            }
        }

        // All exported amounts per number (both overflow and under-brake exports)
        val exportMap = mutableMapOf<String, Int>()
        batchExports.forEach { eb ->
            eb.numbers.forEach { num ->
                exportMap[num.number] = (exportMap[num.number] ?: 0) + num.amount
            }
        }

        // Overflow-specific exported amounts per number
        val overflowExportMap = mutableMapOf<String, Int>()
        batchExports
            .filter { it.record.type.contains("Overflow", ignoreCase = true) || it.record.type.contains("ဘရိတ်ကျော်") || it.record.type.contains("တင်ကွက်") }
            .forEach { eb ->
                eb.numbers.forEach { num ->
                    overflowExportMap[num.number] = (overflowExportMap[num.number] ?: 0) + num.amount
                }
            }

        val results = mutableListOf<LedgerExposure>()
        betMap.forEach { (number, grossAmount) ->
            val exported = exportMap[number] ?: 0
            val netHeld = grossAmount - exported
            // Remaining overflow = amount above brake that has NOT yet been exported to upper agent
            val alreadyExportedOverflow = overflowExportMap[number] ?: 0
            val rawOverflow = if (grossAmount > brake) grossAmount - brake else 0
            val overflow = maxOf(0, rawOverflow - alreadyExportedOverflow)
            if (grossAmount > 0) {
                results.add(LedgerExposure(number, grossAmount, exported, netHeld, overflow))
            }
        }
        results.sortedByDescending { it.netHeldAmount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun exportOverflow(onComplete: ((Int) -> Unit)? = null) {
        val currentExposures = ledgerExposures.value
        val toExport = currentExposures.filter { it.overflowAmount > 0 }
        if (toExport.isEmpty()) return

        viewModelScope.launch {
            val totalAmount = toExport.sumOf { it.overflowAmount }
            val record = ExportRecord(
                batchNumber = currentBatch.value,
                type = "ဘရိတ်ကျော် တင်ကွက်",
                totalAmount = totalAmount
            )
            val recordId = repository.insertExportRecord(record).toInt()

            val exportNumbers = toExport.map {
                ExportedNumber(exportRecordId = recordId, number = it.number, amount = it.overflowAmount)
            }
            repository.insertExportedNumbers(exportNumbers)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(recordId)
            }
            // NOTE: Do NOT insert a Voucher here — that would inflate betMap and
            // prevent overflowAmount from clearing after export.
        }
    }


    init {
        viewModelScope.launch {
            repository.purgeOverflowArtifacts()
        }
        appPassword.value = prefs.getString("appPassword", "") ?: ""
        voucherFooterText.value = prefs.getString("voucherFooterText", "ထွက်လျော်မည်။") ?: "ထွက်လျော်မည်။"
        printerSettings.value = prefs.getString("printerSettings", "") ?: ""
        brakeLimit.value = prefs.getInt("brakeLimit", 3000)
        winningNumber.value = prefs.getString("winningNumber_${currentBatch.value}", "") ?: ""
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

    fun addBannedNumber(number: String, amountLimit: Int = 0) {
        viewModelScope.launch {
            val existing = bannedNumbers.value.find { it.number == number }
            if (existing != null) {
                repository.updateBannedNumber(existing.copy(amountLimit = amountLimit))
            } else {
                repository.insertBannedNumber(BannedNumber(number = number, amountLimit = amountLimit))
            }
        }
    }

    fun updateBannedNumber(bannedNumber: BannedNumber) {
        viewModelScope.launch {
            repository.updateBannedNumber(bannedNumber)
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

    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
        }
    }

    fun getActiveBatchGrossBetsMap(): Map<String, Int> {
        val batch = currentBatch.value
        val map = mutableMapOf<String, Int>()
        vouchersWithBets.value
            .filter { it.voucher.batchNumber == batch && !it.voucher.isArchived }
            .forEach { vb ->
                vb.bets.forEach { b ->
                    map[b.number] = (map[b.number] ?: 0) + b.amount
                }
            }
        return map
    }

    fun validateAndFilterBetsWithBannedLimits(
        incomingBets: List<Bet>,
        pendingSessionAmounts: Map<String, Int> = emptyMap()
    ): Pair<List<Bet>, List<com.threeDLedger.data.BannedLimitRemoval>> {
        val bannedMap = bannedNumbers.value.associateBy { it.number }
        if (bannedMap.isEmpty()) {
            return Pair(incomingBets, emptyList())
        }

        val dbTotals = getActiveBatchGrossBetsMap()
        val accumulatedAmounts = dbTotals.toMutableMap()
        pendingSessionAmounts.forEach { (num, amt) ->
            accumulatedAmounts[num] = (accumulatedAmounts[num] ?: 0) + amt
        }

        val validBets = mutableListOf<Bet>()
        val removals = mutableListOf<com.threeDLedger.data.BannedLimitRemoval>()

        for (bet in incomingBets) {
            val banned = bannedMap[bet.number]
            if (banned == null) {
                validBets.add(bet)
                accumulatedAmounts[bet.number] = (accumulatedAmounts[bet.number] ?: 0) + bet.amount
                continue
            }

            val limit = banned.amountLimit
            val currentAccum = accumulatedAmounts[bet.number] ?: 0

            if (limit <= 0) {
                // Case 1: Completely Banned (0 Ks allowed)
                removals.add(
                    com.threeDLedger.data.BannedLimitRemoval(
                        number = bet.number,
                        attemptedAmount = bet.amount,
                        limitAmount = 0,
                        currentBetTotal = currentAccum,
                        acceptedAmount = 0,
                        removedAmount = bet.amount,
                        reason = "လုံးဝပိတ်ထားသော ဂဏန်းဖြစ်ပါသည်"
                    )
                )
            } else {
                // Case 2: Capped with Limit Amount
                val remainingAllowed = (limit - currentAccum).coerceAtLeast(0)
                if (remainingAllowed <= 0) {
                    // Limit already reached or exceeded
                    removals.add(
                        com.threeDLedger.data.BannedLimitRemoval(
                            number = bet.number,
                            attemptedAmount = bet.amount,
                            limitAmount = limit,
                            currentBetTotal = currentAccum,
                            acceptedAmount = 0,
                            removedAmount = bet.amount,
                            reason = "ကန့်သတ်ငွေ %,d ကျပ် ပြည့်သွားပါသည် (လက်ရှိ: %,d ကျပ်)".format(limit, currentAccum)
                        )
                    )
                } else if (bet.amount <= remainingAllowed) {
                    validBets.add(bet)
                    accumulatedAmounts[bet.number] = currentAccum + bet.amount
                } else {
                    val excess = bet.amount - remainingAllowed
                    validBets.add(bet.copy(amount = remainingAllowed))
                    accumulatedAmounts[bet.number] = currentAccum + remainingAllowed
                    removals.add(
                        com.threeDLedger.data.BannedLimitRemoval(
                            number = bet.number,
                            attemptedAmount = bet.amount,
                            limitAmount = limit,
                            currentBetTotal = currentAccum,
                            acceptedAmount = remainingAllowed,
                            removedAmount = excess,
                            reason = "ကန့်သတ်ငွေ %,d ကျပ် ပြည့်ရန် %,d ကျပ်သာ လက်ခံပြီး ပိုငွေ %,d ကျပ် ဖယ်ထုတ်လိုက်ပါသည်".format(
                                limit, remainingAllowed, excess
                            )
                        )
                    )
                }
            }
        }

        return Pair(validBets, removals)
    }

    fun addVoucherAndBets(customerId: Int, time: String, rawInput: String, remark: String = "") {
        viewModelScope.launch {
            val bets = parseBets(rawInput)
            val (validBets, removals) = validateAndFilterBetsWithBannedLimits(bets)
            if (removals.isNotEmpty()) {
                bannedNumberEvent.emit(true)
                bannedLimitNotificationEvent.emit(removals)
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
            val (validBets, removals) = validateAndFilterBetsWithBannedLimits(bets)
            if (removals.isNotEmpty()) {
                bannedNumberEvent.emit(true)
                bannedLimitNotificationEvent.emit(removals)
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


    private fun convertBurmeseToEnglishDigits(input: String): String { return input.map { char -> when (char) { '၀' -> '0'; '၁' -> '1'; '၂' -> '2'; '၃' -> '3'; '၄' -> '4'; '၅' -> '5'; '၆' -> '6'; '၇' -> '7'; '၈' -> '8'; '၉' -> '9'; else -> char } }.joinToString("") }

private val VM_KS_REGEX                = Regex("(?i)ks")
private val VM_SPACES_SEPARATORS_REGEX = Regex("\\s*([.,/+\\-_=:])\\s*")
private val VM_SPACES_R_REGEX          = Regex("\\s*(?i)r\\s*")
private val VM_SPACES_SPLIT_REGEX      = Regex("\\s+")
private val VM_BLOCK_TAIL_REGEX        = Regex("([-:/.,_=]+)?(\\d+)(?:R(\\d+))?$")
private val VM_NUMBER_CHUNKS_REGEX     = Regex("[.,/+\\-_:]+")

    fun parseBets(input: String): List<Bet> {
        val lines = input.lines().filter { it.isNotBlank() }
        val bets = mutableListOf<Bet>()
        for (line in lines) {
            val pairs = parsePastedLine(line)
            for ((num, amt) in pairs) {
                if (amt > 0) {
                    bets.add(Bet(voucherId = 0, number = num, amount = amt))
                }
            }
        }
        return bets
    }

}
