package com.threeDLedger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.threeDLedger.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

data class DineSettlement(
    val dineId: Int,
    val dineName: String,
    val commissionRate: Double,
    val exactMultiplier: Int,
    val tuwtMultiplier: Int,
    val totalExported: Int,
    val commissionAmount: Int,
    val netCost: Int,
    val exactWinBets: List<Pair<String, Int>>,
    val tuwtWinBets: List<Pair<String, Int>>,
    val exactPayout: Long,
    val tuwtPayout: Long,
    val winningPayout: Long,
    val netBalance: Long
)


data class BatchFinancialSummary3D(
    val totalSales: Long,
    val netBalance: Long,
    val commissionAmount: Long,
    val exportedAmount: Int,
    val winningPayout: Long,
    val voucherCount: Int,
    val customerCount: Int,
    val commissionCustomerCount: Int = 0,
    val directBettorCount: Int = 0,
    val isDeclared: Boolean,
    val winningNumber: String
)

class MainViewModel(private val repository: LotteryRepository, private val prefs: android.content.SharedPreferences) : ViewModel() {

    val fontScales = listOf(0.85f, 1.0f, 1.15f, 1.30f)
    private val _fontScaleIndex = MutableStateFlow(prefs.getInt("font_scale_index", 0).coerceIn(0, 3))
    val fontScaleIndex: StateFlow<Int> = _fontScaleIndex.asStateFlow()

    fun setFontScaleIndex(index: Int) {
        val safeIndex = index.coerceIn(0, 3)
        _fontScaleIndex.value = safeIndex
        prefs.edit().putInt("font_scale_index", safeIndex).apply()
    }


    val customers: StateFlow<List<Customer>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

val allDines: StateFlow<List<Dine>> = repository.allDines
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val winningHistory3D: StateFlow<List<ThreeDWinningHistory>> = repository.winningHistory3D
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isFetchingHistory = MutableStateFlow(false)

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
    val allBatches = MutableStateFlow<List<Int>>(loadInitialBatches())

    private fun loadInitialBatches(): List<Int> {
        val saved = prefs.getString("saved_batches", null)
        if (!saved.isNullOrBlank()) {
            val list = saved.split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .filter { it > 0 }
                .distinct()
                .sorted()
            if (list.isNotEmpty()) return list
        }
        val cur = prefs.getInt("currentBatch", 1)
        return listOf(if (cur > 0) cur else 1)
    }

    private fun saveBatchesList(list: List<Int>) {
        allBatches.value = list
        prefs.edit().putString("saved_batches", list.joinToString(",")).apply()
    }

    fun selectBatch(batch: Int) {
        currentBatch.value = batch
        if (!allBatches.value.contains(batch) && batch > 0) {
            val updated = (allBatches.value + batch).distinct().sorted()
            saveBatchesList(updated)
        }
    }

    /**
     * Create a new batch. If existing batches count >= 4, the earliest batch (lowest number)
     * is deleted along with all its data to maintain a maximum of 4 batches.
     */
    suspend fun createNewBatch(newBatch: Int): Boolean {
        if (newBatch <= 0) return false
        val currentList = allBatches.value.toMutableList()
        if (currentList.contains(newBatch)) return false

        // Keep maximum 4 batches: if we already have >= 4, delete earliest
        while (currentList.size >= 4) {
            val earliest = currentList.minOrNull() ?: break
            deleteBatchInternal(earliest)
            currentList.remove(earliest)
        }

        currentList.add(newBatch)
        val sorted = currentList.distinct().sorted()
        saveBatchesList(sorted)
        selectBatch(newBatch)
        return true
    }

    private suspend fun deleteBatchInternal(batchToDelete: Int) {
        repository.deleteBatchData(batchToDelete)
        val editor = prefs.edit()
        editor.remove("winningNumber_$batchToDelete")
        editor.remove("exactMult_$batchToDelete")
        editor.remove("permMult_$batchToDelete")
        editor.remove("nearMult_$batchToDelete")
        prefs.all.keys.filter { it.endsWith("_$batchToDelete") && it.startsWith("paid_") }.forEach { key ->
            editor.remove(key)
        }
        editor.apply()
    }

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
            try {
                val vList = repository.allVouchersWithBets.first()
                val eList = repository.allExportRecords.first()
                val fromDb = (vList.map { it.voucher.batchNumber } + eList.map { it.record.batchNumber } + listOf(currentBatch.value))
                    .filter { it > 0 }
                    .distinct()
                    .sorted()
                val currentList = allBatches.value
                val merged = (currentList + fromDb).distinct().sorted()
                val trimmed = if (merged.size > 4) merged.takeLast(4) else merged
                if (trimmed != currentList) {
                    saveBatchesList(trimmed)
                }
            } catch (_: Exception) {}
        }
        viewModelScope.launch {
            repository.purgeOverflowArtifacts()
            ensureDefaultCustomer()
            ensureDefaultDines()
            ensureDefault3DHistory()
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

    private suspend fun ensureDefaultDines() {
        try {
            val list = repository.allDines.first()
            if (list.isEmpty()) {
                repository.insertDine(Dine(name = "မညစ်", commissionRate = 15.0, exactMultiplier = 600, tuwtMultiplier = 10))
                repository.insertDine(Dine(name = "ကျော်မဲ", commissionRate = 20.0, exactMultiplier = 550, tuwtMultiplier = 10))
            }
        } catch (_: Exception) {}
    }

    fun addDine(name: String, commissionRate: Double, exactMultiplier: Int = 600, tuwtMultiplier: Int = 10) {
        viewModelScope.launch {
            repository.insertDine(Dine(name = name, commissionRate = commissionRate, exactMultiplier = exactMultiplier, tuwtMultiplier = tuwtMultiplier))
        }
    }

    fun updateDine(dine: Dine) {
        viewModelScope.launch {
            repository.updateDine(dine)
        }
    }

    fun deleteDine(dine: Dine) {
        viewModelScope.launch {
            repository.deleteDine(dine)
        }
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
        }
    }

    fun exportOverflowToDine(dine: Dine, onComplete: ((recordId: Int, voucherSerial: Int) -> Unit)? = null) {
        val currentExposures = ledgerExposures.value
        val toExport = currentExposures.filter { it.overflowAmount > 0 }
        if (toExport.isEmpty()) return

        viewModelScope.launch {
            val totalAmount = toExport.sumOf { it.overflowAmount }
            // Serial number for this specific Dine in current batch
            val existingForDine = allExportRecords.value.filter {
                it.record.batchNumber == currentBatch.value && it.record.dineId == dine.id
            }
            val voucherSerial = existingForDine.size + 1

            val record = ExportRecord(
                batchNumber = currentBatch.value,
                type = "ဘရိတ်ကျော် တင်ကွက်",
                totalAmount = totalAmount,
                dineId = dine.id,
                dineName = dine.name,
                voucherSerial = voucherSerial
            )
            val recordId = repository.insertExportRecord(record).toInt()

            val exportNumbers = toExport.map {
                ExportedNumber(exportRecordId = recordId, number = it.number, amount = it.overflowAmount)
            }
            repository.insertExportedNumbers(exportNumbers)
            withContext(Dispatchers.Main) {
                onComplete?.invoke(recordId, voucherSerial)
            }
        }
    }

    fun getDineSettlementsForBatch(batch: Int = currentBatch.value): List<DineSettlement> {
        val batchExports = allExportRecords.value.filter { it.record.batchNumber == batch && !it.record.isArchived }
        val winningNum = getWinningNumberForBatch(batch)
        val dinesMap = allDines.value.associateBy { it.id }

        val (savedExact, savedTuwt, _) = getMultipliersForBatch(batch)
        val defaultExact = savedExact.toInt()
        val defaultTuwt = savedTuwt.toInt()

        val allPerms = if (winningNum.length == 3) {
            com.threeDLedger.logic.NumberGenerator.permutations(winningNum).toSet() - setOf(winningNum)
        } else emptySet()
        val numInt = winningNum.toIntOrNull() ?: 0
        val minus1 = String.format("%03d", if (numInt == 0) 999 else numInt - 1)
        val plus1 = String.format("%03d", if (numInt == 999) 0 else numInt + 1)
        val near = if (winningNum.length == 3) (setOf(minus1, plus1) - setOf(winningNum)) else emptySet()
        val tuwtSet = allPerms + near

        val groupedByDine = batchExports.groupBy { it.record.dineId }
        val settlements = mutableListOf<DineSettlement>()

        val allDineIds = (groupedByDine.keys + dinesMap.keys).filter { it > 0 }.distinct()

        for (dId in allDineIds) {
            val dine = dinesMap[dId] ?: Dine(id = dId, name = groupedByDine[dId]?.firstOrNull()?.record?.dineName ?: "ဒိုင် #$dId", commissionRate = 15.0, exactMultiplier = defaultExact, tuwtMultiplier = defaultTuwt)
            val exportsForDine = groupedByDine[dId] ?: emptyList()

            val totalExported = exportsForDine.sumOf { it.record.totalAmount }
            if (totalExported <= 0 && exportsForDine.isEmpty()) continue

            val commissionAmount = (totalExported * (dine.commissionRate / 100.0)).toInt()
            val netCost = totalExported - commissionAmount

            val exactWinBets = mutableListOf<Pair<String, Int>>()
            val tuwtWinBets = mutableListOf<Pair<String, Int>>()
            var exactWinTotal = 0
            var tuwtWinTotal = 0

            if (winningNum.length == 3) {
                exportsForDine.forEach { exp ->
                    exp.numbers.forEach { en ->
                        if (en.number == winningNum) {
                            exactWinBets.add(en.number to en.amount)
                            exactWinTotal += en.amount
                        } else if (en.number in tuwtSet) {
                            tuwtWinBets.add(en.number to en.amount)
                            tuwtWinTotal += en.amount
                        }
                    }
                }
            }
            val exactPayout = exactWinTotal.toLong() * dine.exactMultiplier
            val tuwtPayout = tuwtWinTotal.toLong() * dine.tuwtMultiplier
            val winningPayout = exactPayout + tuwtPayout
            val netBalance = winningPayout - netCost

            settlements.add(
                DineSettlement(
                    dineId = dine.id,
                    dineName = dine.name,
                    commissionRate = dine.commissionRate,
                    exactMultiplier = dine.exactMultiplier,
                    tuwtMultiplier = dine.tuwtMultiplier,
                    totalExported = totalExported,
                    commissionAmount = commissionAmount,
                    netCost = netCost,
                    exactWinBets = exactWinBets,
                    tuwtWinBets = tuwtWinBets,
                    exactPayout = exactPayout,
                    tuwtPayout = tuwtPayout,
                    winningPayout = winningPayout,
                    netBalance = netBalance
                )
            )
        }

        val unassigned = groupedByDine[0] ?: emptyList()
        if (unassigned.isNotEmpty()) {
            val totalExported = unassigned.sumOf { it.record.totalAmount }
            val commissionAmount = (totalExported * 0.15).toInt()
            val netCost = totalExported - commissionAmount

            val exactWinBets = mutableListOf<Pair<String, Int>>()
            val tuwtWinBets = mutableListOf<Pair<String, Int>>()
            var exactWinTotal = 0
            var tuwtWinTotal = 0

            if (winningNum.length == 3) {
                unassigned.forEach { exp ->
                    exp.numbers.forEach { en ->
                        if (en.number == winningNum) {
                            exactWinBets.add(en.number to en.amount)
                            exactWinTotal += en.amount
                        } else if (en.number in tuwtSet) {
                            tuwtWinBets.add(en.number to en.amount)
                            tuwtWinTotal += en.amount
                        }
                    }
                }
            }
            val exactPayout = exactWinTotal.toLong() * defaultExact
            val tuwtPayout = tuwtWinTotal.toLong() * defaultTuwt
            val winningPayout = exactPayout + tuwtPayout
            val netBalance = winningPayout - netCost

            settlements.add(
                DineSettlement(
                    dineId = 0,
                    dineName = "အထွေထွေ ဒိုင်",
                    commissionRate = 15.0,
                    exactMultiplier = defaultExact,
                    tuwtMultiplier = defaultTuwt,
                    totalExported = totalExported,
                    commissionAmount = commissionAmount,
                    netCost = netCost,
                    exactWinBets = exactWinBets,
                    tuwtWinBets = tuwtWinBets,
                    exactPayout = exactPayout,
                    tuwtPayout = tuwtPayout,
                    winningPayout = winningPayout,
                    netBalance = netBalance
                )
            )
        }

        return settlements
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
            val nextBatch = currentBatch.value + 1
            currentBatch.value = nextBatch
            if (!allBatches.value.contains(nextBatch)) {
                val updated = (allBatches.value + nextBatch).distinct().sorted()
                val trimmed = if (updated.size > 4) updated.takeLast(4) else updated
                saveBatchesList(trimmed)
            }
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


    private suspend fun ensureDefault3DHistory() {
        try {
            val list = repository.winningHistory3D.first()
            if (list.isEmpty()) {
                val defaultHistory = listOf(
                    ThreeDWinningHistory(drawDate = "16/09/2026", drawDateFormatted = "၁၆ စက်တင်ဘာ ၂၀၂၆", winningNumber = "640", firstPrize6D = "360640"),
                    ThreeDWinningHistory(drawDate = "01/09/2026", drawDateFormatted = "၁ စက်တင်ဘာ ၂၀၂၆", winningNumber = "341", firstPrize6D = "199341"),
                    ThreeDWinningHistory(drawDate = "16/08/2026", drawDateFormatted = "၁၆ ဩဂုတ် ၂၀၂၆", winningNumber = "941", firstPrize6D = "046941"),
                    ThreeDWinningHistory(drawDate = "01/08/2026", drawDateFormatted = "၁ ဩဂုတ် ၂၀၂၆", winningNumber = "756", firstPrize6D = "407756"),
                    ThreeDWinningHistory(drawDate = "16/07/2026", drawDateFormatted = "၁၆ ဇူလိုင် ၂၀၂၆", winningNumber = "533", firstPrize6D = "518533"),
                    ThreeDWinningHistory(drawDate = "01/07/2026", drawDateFormatted = "၁ ဇူလိုင် ၂၀၂၆", winningNumber = "032", firstPrize6D = "922032"),
                    ThreeDWinningHistory(drawDate = "16/06/2026", drawDateFormatted = "၁၆ ဇွန် ၂၀၂၆", winningNumber = "092", firstPrize6D = "516092"),
                    ThreeDWinningHistory(drawDate = "01/06/2026", drawDateFormatted = "၁ ဇွန် ၂၀၂၆", winningNumber = "593", firstPrize6D = "530593"),
                    ThreeDWinningHistory(drawDate = "16/05/2026", drawDateFormatted = "၁၆ မေ ၂၀၂၆", winningNumber = "903", firstPrize6D = "205903"),
                    ThreeDWinningHistory(drawDate = "02/05/2026", drawDateFormatted = "၂ မေ ၂၀၂၆", winningNumber = "884", firstPrize6D = "980884"),
                    ThreeDWinningHistory(drawDate = "16/04/2026", drawDateFormatted = "၁၆ ဧပြီ ၂၀၂၆", winningNumber = "873", firstPrize6D = "943873"),
                    ThreeDWinningHistory(drawDate = "01/04/2026", drawDateFormatted = "၁ ဧပြီ ၂၀၂၆", winningNumber = "720", firstPrize6D = "803720"),
                    ThreeDWinningHistory(drawDate = "16/03/2026", drawDateFormatted = "၁၆ မတ် ၂၀၂၆", winningNumber = "503", firstPrize6D = "997503"),
                    ThreeDWinningHistory(drawDate = "01/03/2026", drawDateFormatted = "၁ မတ် ၂၀၂၆", winningNumber = "603", firstPrize6D = "253603"),
                    ThreeDWinningHistory(drawDate = "16/02/2026", drawDateFormatted = "၁၆ ဖေဖော်ဝါရီ ၂၀၂၆", winningNumber = "395", firstPrize6D = "094395"),
                    ThreeDWinningHistory(drawDate = "01/02/2026", drawDateFormatted = "၁ ဖေဖော်ဝါရီ ၂၀၂၆", winningNumber = "063", firstPrize6D = "607063"),
                    ThreeDWinningHistory(drawDate = "17/01/2026", drawDateFormatted = "၁၇ ဇန်နဝါရီ ၂၀၂၆", winningNumber = "979", firstPrize6D = "105979"),
                    ThreeDWinningHistory(drawDate = "30/12/2025", drawDateFormatted = "၃၀ ဒီဇင်ဘာ ၂၀၂၅", winningNumber = "955", firstPrize6D = "444955"),
                    ThreeDWinningHistory(drawDate = "16/12/2025", drawDateFormatted = "၁၆ ဒီဇင်ဘာ ၂၀၂၅", winningNumber = "757", firstPrize6D = "356757"),
                    ThreeDWinningHistory(drawDate = "01/12/2025", drawDateFormatted = "၁ ဒီဇင်ဘာ ၂၀၂၅", winningNumber = "097", firstPrize6D = "843097"),
                    ThreeDWinningHistory(drawDate = "16/11/2025", drawDateFormatted = "၁၆ နိုဝင်ဘာ ၂၀၂၅", winningNumber = "361", firstPrize6D = "187361"),
                    ThreeDWinningHistory(drawDate = "01/11/2025", drawDateFormatted = "၁ နိုဝင်ဘာ ၂၀၂၅", winningNumber = "444", firstPrize6D = "741444"),
                    ThreeDWinningHistory(drawDate = "16/10/2025", drawDateFormatted = "၁၆ အောက်တိုဘာ ၂၀၂၅", winningNumber = "286", firstPrize6D = "429286"),
                    ThreeDWinningHistory(drawDate = "01/10/2025", drawDateFormatted = "၁ အောက်တိုဘာ ၂၀၂၅", winningNumber = "202", firstPrize6D = "880202")
                )
                repository.insert3DWinningHistory(defaultHistory)
            }
        } catch (_: Exception) {}
    }

    fun getBatchFinancialSummary(batch: Int = currentBatch.value): BatchFinancialSummary3D {
        val batchVouchers = vouchersWithBets.value.filter { it.voucher.batchNumber == batch && !it.voucher.isArchived }
        val totalSales = batchVouchers.sumOf { it.voucher.totalAmount.toLong() }
        val custMap = customers.value.associateBy { it.id }
        val commTotal = batchVouchers.sumOf { vwb ->
            val cust = custMap[vwb.voucher.customerId]
            val rate = cust?.commissionRate ?: 0.0
            val effectiveRate = if (rate > 1.0) rate / 100.0 else rate
            (vwb.voucher.totalAmount * effectiveRate).toLong()
        }
        val batchExports = allExportRecords.value.filter { it.record.batchNumber == batch && !it.record.isArchived }
        val exportedAmt = batchExports.sumOf { it.record.totalAmount }
        val wonDeclared = isBatchDeclared(batch)
        val winningNum = getWinningNumberForBatch(batch)
        val (exactM, tuwtM, _) = getMultipliersForBatch(batch)

        var payoutTotal = 0L
        if (wonDeclared && winningNum.length == 3) {
            val allPerms = com.threeDLedger.logic.NumberGenerator.permutations(winningNum).toSet() - setOf(winningNum)
            val numInt = winningNum.toIntOrNull() ?: 0
            val minus1 = String.format("%03d", if (numInt == 0) 999 else numInt - 1)
            val plus1 = String.format("%03d", if (numInt == 999) 0 else numInt + 1)
            val near = setOf(minus1, plus1) - setOf(winningNum)
            val tuwtSet = allPerms + near

            batchVouchers.forEach { vwb ->
                vwb.bets.forEach { bet ->
                    if (bet.number == winningNum) {
                        payoutTotal += (bet.amount * exactM).toLong()
                    } else if (bet.number in tuwtSet) {
                        payoutTotal += (bet.amount * tuwtM).toLong()
                    }
                }
            }
        }

        val netBal = if (wonDeclared) {
            totalSales - commTotal - payoutTotal
        } else {
            totalSales - commTotal - exportedAmt
        }

        val distinctCustIds = batchVouchers.map { it.voucher.customerId }.distinct()
        val custCount = distinctCustIds.size
        val commCustCount = distinctCustIds.count { (custMap[it]?.commissionRate ?: 0.0) > 0.0 }
        val directCustCount = distinctCustIds.count { (custMap[it]?.commissionRate ?: 0.0) == 0.0 }

        return BatchFinancialSummary3D(
            totalSales = totalSales,
            netBalance = netBal,
            commissionAmount = commTotal,
            exportedAmount = exportedAmt,
            winningPayout = payoutTotal,
            voucherCount = batchVouchers.size,
            customerCount = custCount,
            commissionCustomerCount = commCustCount,
            directBettorCount = directCustCount,
            isDeclared = wonDeclared,
            winningNumber = winningNum
        )
    }

    fun fetch3DHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isFetchingHistory.value = true
                ensureDefault3DHistory()
            } finally {
                isFetchingHistory.value = false
            }
        }
    }
}
