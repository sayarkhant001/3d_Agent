package com.threeDLedger.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.data.ExportRecordWithNumbers
import com.threeDLedger.logic.NumberGenerator
import com.threeDLedger.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportHistoryScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    BackHandler(onBack = onNavigateBack)

    val exportRecords by viewModel.allExportRecords.collectAsStateWithLifecycle()
    val allCustomers by viewModel.customers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val footerText by viewModel.voucherFooterText.collectAsStateWithLifecycle()

    val meCustomer = remember(allCustomers) {
        allCustomers.firstOrNull {
            it.name.contains("မိမိ") || it.name.contains("ကိုယ်တိုင်") || it.name.equals("me", ignoreCase = true)
        } ?: allCustomers.firstOrNull()
    }
    val defaultCommRate = meCustomer?.commissionRate ?: 0.15

    val batches = remember(exportRecords) {
        exportRecords.map { it.record.batchNumber }.distinct().sortedDescending()
    }
    var selectedBatchFilter by remember { mutableStateOf<Int?>(null) }
    val displayedRecords = remember(exportRecords, selectedBatchFilter) {
        if (selectedBatchFilter == null) exportRecords
        else exportRecords.filter { it.record.batchNumber == selectedBatchFilter }
    }

    val totalAmount = remember(displayedRecords) {
        displayedRecords.sumOf { it.record.totalAmount.toLong() }
    }
    val totalVouchers = displayedRecords.size
    val totalNumbers = remember(displayedRecords) {
        displayedRecords.sumOf { it.numbers.size }
    }

    val activeBatch = selectedBatchFilter ?: batches.firstOrNull() ?: viewModel.currentBatch.value
    val activeBatchWinningNumber = remember(activeBatch, viewModel) {
        viewModel.getWinningNumberForBatch(activeBatch)
    }
    val isWinningDeclared = activeBatchWinningNumber.length == 3
    var showUpperSettlementDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("တင်ကွက် မှတ်တမ်း", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        bottomBar = {
            if (exportRecords.isNotEmpty()) {
                ExportHistoryBottomBar(
                    totalAmount = totalAmount,
                    totalVouchers = totalVouchers,
                    totalNumbers = totalNumbers,
                    selectedBatch = selectedBatchFilter,
                    isWinningDeclared = isWinningDeclared,
                    onTapTotal = {
                        val currentWin = viewModel.getWinningNumberForBatch(activeBatch)
                        if (currentWin.length != 3) {
                            Toast.makeText(
                                context,
                                "ပေါက်ဂဏန်း မကြေညာရသေးပါ (ရှင်းတမ်း ကြည့်၍ မရသေးပါ)",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            showUpperSettlementDialog = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (batches.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedBatchFilter == null,
                            onClick = { selectedBatchFilter = null },
                            label = { Text("အားလုံး (${exportRecords.size})") }
                        )
                    }
                    items(batches) { batch ->
                        val count = exportRecords.count { it.record.batchNumber == batch }
                        FilterChip(
                            selected = selectedBatchFilter == batch,
                            onClick = { selectedBatchFilter = batch },
                            label = { Text("အကြိမ် $batch ($count)") }
                        )
                    }
                }
            }

            if (displayedRecords.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("မှတ်တမ်း မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    items(displayedRecords, key = { it.record.id }) { export ->
                        ExportRecordCard(
                            export = export,
                            footerText = footerText,
                            onCopy = { text ->
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("Export Record", text))
                                android.widget.Toast.makeText(context, "ကူးယူပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            onPrint = { export ->
                                coroutineScope.launch {
                                    try {
                                        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                        val paperSize = prefs.getString("paperSize", "58mm") ?: "58mm"
                                        val voucherData = com.threeDLedger.logic.BluetoothPrinter.VoucherData(
                                            batchNumber = export.record.batchNumber,
                                            voucherId = export.record.id,
                                            date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(export.record.timestamp)),
                                            customerName = export.record.type,
                                            bets = export.numbers.map { it.number to it.amount },
                                            totalAmount = export.record.totalAmount,
                                            footerText = footerText
                                        )
                                        val bitmap = com.threeDLedger.logic.BluetoothPrinter.createVoucherBitmap(voucherData, paperSize)
                                        com.threeDLedger.logic.BluetoothPrinter.printBitmap(bitmap, paperSize)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "ပရင်တာ error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showUpperSettlementDialog && activeBatchWinningNumber.length == 3) {
        val activeBatchRecords = remember(exportRecords, activeBatch) {
            exportRecords.filter { it.record.batchNumber == activeBatch }
        }
        val multipliers = remember(activeBatch) {
            viewModel.getMultipliersForBatch(activeBatch)
        }
        UpperAgentSettlementDialog(
            batchNumber = activeBatch,
            winningNumber = activeBatchWinningNumber,
            exportRecords = activeBatchRecords,
            multipliers = multipliers,
            defaultCommissionRate = defaultCommRate,
            onDismiss = { showUpperSettlementDialog = false }
        )
    }
}

@Composable
fun ExportHistoryBottomBar(
    totalAmount: Long,
    totalVouchers: Int,
    totalNumbers: Int,
    selectedBatch: Int? = null,
    isWinningDeclared: Boolean = false,
    onTapTotal: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = 10.dp,
        tonalElevation = 2.dp,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .then(if (onTapTotal != null) Modifier.clickable(onClick = onTapTotal) else Modifier)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectedBatch != null) "အကြိမ် ($selectedBatch) တင်ငွေ စုစုပေါင်း" else "အထက်ဒိုင် တင်ငွေ စုစုပေါင်း",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = "ဘောင်ချာ ($totalVouchers) စောင် • ($totalNumbers) ဂဏန်း",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isWinningDeclared) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = "နှိပ်၍ အထက်ဒိုင် ရှင်းတမ်း ကြည့်ရန် ▶",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldPrimary
                    )
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.error,
                shadowElevation = 2.dp,
                modifier = if (onTapTotal != null) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onTapTotal)
                } else Modifier
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "%,d Ks".format(totalAmount),
                        color = MaterialTheme.colorScheme.onError,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    if (isWinningDeclared) {
                        Text(
                            text = "ရှင်းတမ်း ▶",
                            color = MaterialTheme.colorScheme.onError.copy(alpha = 0.9f),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExportRecordCard(
    export: ExportRecordWithNumbers,
    footerText: String,
    onCopy: (String) -> Unit,
    onPrint: (ExportRecordWithNumbers) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val dateString = SimpleDateFormat("HH:mm:ss  dd/MM/yyyy", Locale.getDefault()).format(Date(export.record.timestamp))
    val isOverflow = export.record.type.contains("Overflow") || export.record.type.contains("တင်ကွက်")
    val headerColor = if (isOverflow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val sortedNumbers = export.numbers.sortedByDescending { it.amount }

    val voucherDate = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(export.record.timestamp))
    val voucherText = buildString {
        appendLine("      တင်ကွက် ဘောင်ချာ    ")
        appendLine(" ဘောင်ချာ : #${export.record.id}")
        appendLine(" အကြိမ်   : ${export.record.batchNumber}")
        appendLine(" အချိန်   : $voucherDate")
        appendLine("------------------------")
        sortedNumbers.forEachIndexed { idx, num ->
            appendLine(" ${idx + 1}. ${num.number} = ${num.amount}")
        }
        appendLine("------------------------")
        appendLine(" စုစုပေါင်း : %,d Ks".format(export.record.totalAmount))
        appendLine("   * အထက်ဒိုင် တင်ကွက် *  ")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            // ── Card header bar (Always visible, tap to expand/collapse) ────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(headerColor)
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(5.dp),
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f)
                        ) {
                            Text(
                                "ဘောင်ချာ #${export.record.id}",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "အကြိမ်: ${export.record.batchNumber}",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            dateString,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "%,d Ks".format(export.record.totalAmount),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            "${sortedNumbers.size} ဂဏန်း",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                            fontSize = 9.5.sp
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // ── Expanded Body ──────────────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    // Column headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "စဉ်",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(32.dp),
                            textAlign = TextAlign.Start,
                            fontSize = 10.5.sp
                        )
                        Text(
                            "ဂဏန်း",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 10.5.sp
                        )
                        Text(
                            "ပမာဏ",
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 10.5.sp
                        )
                    }

                    // Per-number rows
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, headerColor.copy(alpha = 0.25f))
                    ) {
                        sortedNumbers.forEachIndexed { index, num ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (index % 2 == 0) Color.Transparent
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}.",
                                    fontSize = 9.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(32.dp)
                                )
                                Text(
                                    num.number,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    "%,d Ks".format(num.amount),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isOverflow) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            if (index < sortedNumbers.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                            }
                        }
                    }

                    // Footer total
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(headerColor)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "စုစုပေါင်း (${sortedNumbers.size} ဂဏန်း)",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.5.sp
                        )
                        Text(
                            "%,d Ks".format(export.record.totalAmount),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onPrint(export) },
                            modifier = Modifier.weight(1f).height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = headerColor)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ပရင့်", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { onCopy(voucherText) },
                            modifier = Modifier.weight(1f).height(34.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = headerColor)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ကော်ပီ", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpperAgentSettlementDialog(
    batchNumber: Int,
    winningNumber: String,
    exportRecords: List<ExportRecordWithNumbers>,
    multipliers: Triple<Double, Double, Double>,
    defaultCommissionRate: Double,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val (exactMult, permMult, _) = multipliers

    val initialPercentStr = remember(defaultCommissionRate) {
        if (defaultCommissionRate > 0.0) {
            val p = defaultCommissionRate * 100
            if (p % 1.0 == 0.0) p.toInt().toString() else "%.1f".format(p)
        } else "15"
    }
    var commPercentText by remember { mutableStateOf(initialPercentStr) }

    val allSentBets = remember(exportRecords) { exportRecords.flatMap { it.numbers } }
    val totalSentBet = remember(exportRecords) { exportRecords.sumOf { it.record.totalAmount.toLong() } }

    val commRate = (commPercentText.toDoubleOrNull() ?: 0.0) / 100.0
    val commDeduction = (totalSentBet * commRate).toLong()
    val netSentBet = totalSentBet - commDeduction

    // Winning calculations
    val allPerms = remember(winningNumber) { NumberGenerator.permutations(winningNumber).toSet() }
    val permsOnly = remember(winningNumber, allPerms) { allPerms - setOf(winningNumber) }

    val exactHits = remember(allSentBets, winningNumber) {
        allSentBets.filter { it.number == winningNumber }
    }
    val exactBetAmt = remember(exactHits) { exactHits.sumOf { it.amount.toLong() } }
    val exactPayout = remember(exactBetAmt, exactMult) { (exactBetAmt * exactMult).toLong() }

    val tutHits = remember(allSentBets, permsOnly) {
        allSentBets.filter { it.number in permsOnly }
    }
    val tutBetAmt = remember(tutHits) { tutHits.sumOf { it.amount.toLong() } }
    val tutPayout = remember(tutBetAmt, permMult) { (tutBetAmt * permMult).toLong() }

    val tutBreakdown = remember(tutHits) {
        tutHits.groupBy { it.number }.mapValues { entry -> entry.value.sumOf { it.amount.toLong() } }
    }

    val totalUpperPayout = exactPayout + tutPayout
    val netBalance = totalUpperPayout - netSentBet

    fun buildSlip(): String = buildString {
        appendLine("========================")
        appendLine("   အထက်ဒိုင် ရှင်းတမ်း")
        appendLine("========================")
        appendLine("အကြိမ် = $batchNumber ( $winningNumber )")
        appendLine("------------------------")
        appendLine("တင်ငွေ စုစုပေါင်း = %,d Ks".format(totalSentBet))
        appendLine("ကော်မရှင် ($commPercentText%) = -%,d Ks".format(commDeduction))
        appendLine("နုတ်ပြီး တင်ငွေ = %,d Ks".format(netSentBet))
        appendLine("------------------------")
        if (exactBetAmt > 0) {
            appendLine("ဒဲ့ပေါက် ($winningNumber) = %,d Ks (x${exactMult.toInt()}) → %,d Ks".format(exactBetAmt, exactPayout))
        }
        if (tutBetAmt > 0) {
            appendLine("တွတ်ပေါက် = %,d Ks (x${permMult.toInt()}) → %,d Ks".format(tutBetAmt, tutPayout))
            tutBreakdown.forEach { (num, amt) ->
                appendLine("  • $num: %,d Ks → %,d Ks".format(amt, (amt * permMult).toLong()))
            }
        }
        if (totalUpperPayout == 0L) {
            appendLine("ပေါက်ကြေး = 0 Ks (ပေါက်ဂဏန်း မပါပါ)")
        } else {
            appendLine("စုစုပေါင်း ပေါက်ငွေ = %,d Ks".format(totalUpperPayout))
        }
        appendLine("------------------------")
        if (netBalance > 0) {
            appendLine("ရလဒ် = အထက်ဒိုင်မှ ရရန် (+%,d Ks)".format(netBalance))
        } else if (netBalance < 0) {
            appendLine("ရလဒ် = အထက်ဒိုင်သို့ ပေးရန် (-%,d Ks)".format(-netBalance))
        } else {
            appendLine("ရလဒ် = ကျေအေး (0 Ks)")
        }
        appendLine("========================")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(32.dp).clip(CircleShape).background(EmeraldPrimary),
                        Alignment.Center
                    ) {
                        Icon(Icons.Default.AccountBalance, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("အထက်ဒိုင် ရှင်းတမ်း", fontWeight = FontWeight.Bold, fontSize = 13.5.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("အကြိမ် $batchNumber", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 9.5.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = WinExactRed
                ) {
                    Text(
                        "ထွက်: $winningNumber",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Section 1: Sent Bets & Commission
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("တင်ငွေ & ကော်မရှင် တွက်ချက်မှု", fontWeight = FontWeight.Bold, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.primary)

                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("တင်ငွေ စုစုပေါင်း", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%,d Ks".format(totalSentBet), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    // Editable commission % row
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("ကော်မရှင်", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(5.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(0.8.dp, EmeraldPrimary.copy(alpha = 0.5f), RoundedCornerShape(5.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                BasicTextField(
                                    value = commPercentText,
                                    onValueChange = { input ->
                                        if (input.all { it.isDigit() || it == '.' } && input.length <= 4) {
                                            commPercentText = input
                                        }
                                    },
                                    modifier = Modifier.width(30.dp),
                                    textStyle = LocalTextStyle.current.copy(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        textAlign = TextAlign.Center
                                    ),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                                )
                                Text("%", fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimary)
                            }
                        }
                        Text(
                            "-%,d Ks".format(commDeduction),
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = GoldAccent
                        )
                    }

                    HorizontalDivider(color = CardBorderSubtle, thickness = 0.5.dp)

                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("နုတ်ပြီး တင်ငွေ", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "%,d Ks".format(netSentBet),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldPrimary
                        )
                    }
                }

                // Section 2: Winning Payouts from Upper
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (totalUpperPayout > 0) WinExactBg.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                        .border(
                            0.5.dp,
                            if (totalUpperPayout > 0) WinExactRed.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(10.dp)
                        )
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "အထက်ဒိုင် ပေါက်ကြေး (လျော်ငွေ)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        color = if (totalUpperPayout > 0) WinExactRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (exactBetAmt > 0) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Star, null, tint = WinExactRed, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("ဒဲ့ ($winningNumber)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = WinExactRed)
                            }
                            Text(
                                "%,d Ks (x${exactMult.toInt()}) → %,d Ks".format(exactBetAmt, exactPayout),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    if (tutBetAmt > 0) {
                        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, null, tint = GoldDark, modifier = Modifier.size(13.dp))
                                Spacer(Modifier.width(3.dp))
                                Text("တွတ် (${tutBreakdown.size} ကွက်)", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = GoldDark)
                            }
                            Text(
                                "%,d Ks (x${permMult.toInt()}) → %,d Ks".format(tutBetAmt, tutPayout),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        // Show tut won details
                        tutBreakdown.forEach { (num, amt) ->
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp),
                                Arrangement.SpaceBetween,
                                Alignment.CenterVertically
                            ) {
                                Text("• $num", fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("%,d Ks → %,d Ks".format(amt, (amt * permMult).toLong()), fontSize = 9.5.sp, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (totalUpperPayout == 0L) {
                        Text(
                            "ပေါက်ဂဏန်း မပါပါ (ပေါက်ငွေ 0 Ks)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    }

                    HorizontalDivider(color = CardBorderSubtle, thickness = 0.5.dp)

                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Text("စုစုပေါင်း ပေါက်ငွေ", fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "%,d Ks".format(totalUpperPayout),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = if (totalUpperPayout > 0) WinExactRed else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Section 3: Final Net Settlement Card
                val isToReceive = netBalance > 0
                val isToPay = netBalance < 0
                val resultBg = when {
                    isToReceive -> EmeraldLight
                    isToPay -> WinExactBg
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
                val resultBorder = when {
                    isToReceive -> EmeraldPrimary
                    isToPay -> WinExactRed
                    else -> Color.Gray
                }
                val resultColor = when {
                    isToReceive -> EmeraldDark
                    isToPay -> WinExactRed
                    else -> MaterialTheme.colorScheme.onSurface
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(resultBg)
                        .border(0.8.dp, resultBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = when {
                            isToReceive -> "အထက်ဒိုင်မှ ရရန်"
                            isToPay -> "အထက်ဒိုင်သို့ ပေးရန်"
                            else -> "ကျေအေး (ရှင်းပြီး)"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = resultColor
                    )
                    Text(
                        text = when {
                            isToReceive -> "+%,d Ks".format(netBalance)
                            isToPay -> "-%,d Ks".format(-netBalance)
                            else -> "0 Ks"
                        },
                        fontWeight = FontWeight.Black,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace,
                        color = resultColor
                    )
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val slip = buildSlip()
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, slip)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "အထက်ဒိုင် ရှင်းတမ်း မျှဝေမည်")
                        context.startActivity(shareIntent)
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("မျှဝေမည်", fontSize = 11.sp)
                }

                Button(
                    onClick = {
                        val slip = buildSlip()
                        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        cm.setPrimaryClip(android.content.ClipData.newPlainText("Upper Settlement", slip))
                        Toast.makeText(context, "ရှင်းတမ်း ကူးယူပြီးပါပြီ", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ကော်ပီ", fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(36.dp)
            ) {
                Text("ပိတ်မည်", fontSize = 11.sp)
            }
        }
    )
}
