package com.threeDLedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.logic.NumberGenerator
import com.threeDLedger.ui.theme.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BrakedWinRow(
    val number: String,
    val isExact: Boolean,
    val amount: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverflowScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToExportHistory: () -> Unit = {}
) {
    val ledgerExposures by viewModel.ledgerExposures.collectAsStateWithLifecycle()
    val currentBatch by viewModel.currentBatch.collectAsStateWithLifecycle()
    val brakeLimit by viewModel.brakeLimit.collectAsStateWithLifecycle()
    val footerText by viewModel.voucherFooterText.collectAsStateWithLifecycle()
    val winningNumber by viewModel.winningNumber.collectAsStateWithLifecycle()
    val allCustomers by viewModel.customers.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val batchWinningNumber = remember(currentBatch, winningNumber) { 
        viewModel.getWinningNumberForBatch(currentBatch) 
    }
    val isWonDeclared = batchWinningNumber.length == 3
    val (exactMult, permMult, nearMult) = remember(currentBatch) { 
        viewModel.getMultipliersForBatch(currentBatch) 
    }

    val meCustomer = remember(allCustomers) {
        allCustomers.firstOrNull { 
            it.name.contains("မိမိ") || it.name.contains("ကိုယ်တိုင်") || it.name.equals("me", ignoreCase = true) 
        } ?: allCustomers.firstOrNull()
    }
    val meCommRate = meCustomer?.commissionRate ?: 0.0

    val exposureMap = remember(ledgerExposures) { ledgerExposures.associateBy { it.number } }

    fun keptAmount(totalBetAmount: Int): Int =
        if (brakeLimit > 0) minOf(totalBetAmount, brakeLimit) else totalBetAmount

    // Sort exposures — ascending by number string (000 → 999)
    val overflowExposures = ledgerExposures.filter { it.overflowAmount > 0 }.sortedBy { it.number.toIntOrNull() ?: 0 }

    // Left table: ALL numbers with any bet, sorted ascending by number (numeric, not lexicographic).
    // Display amount = min(totalBetAmount, brakeLimit) — i.e. the "kept" portion capped at brake.
    // If brake is 0 (not set), show full totalBetAmount.
    val brakedExposures = ledgerExposures
        .filter { it.totalBetAmount > 0 }
        .sortedBy { it.number.toIntOrNull() ?: 0 }

    // When winning number is declared: show winning exact number and tut numbers only
    val brakedWinRows = remember(batchWinningNumber, exposureMap, brakeLimit) {
        if (batchWinningNumber.length != 3) return@remember emptyList<BrakedWinRow>()
        val allPerms = NumberGenerator.permutations(batchWinningNumber).toSet()
        val permsOnly = (allPerms - setOf(batchWinningNumber)).sorted()
        val winInt = batchWinningNumber.toIntOrNull() ?: 0
        val numMinus1 = String.format("%03d", if (winInt == 0) 999 else winInt - 1)
        val numPlus1  = String.format("%03d", if (winInt == 999) 0 else winInt + 1)
        val lastDigit = batchWinningNumber[2].digitToIntOrNull() ?: 0
        val lastMinus1 = "${batchWinningNumber.substring(0, 2)}${(lastDigit + 9) % 10}"
        val lastPlus1  = "${batchWinningNumber.substring(0, 2)}${(lastDigit + 1) % 10}"
        val nearOnly = (setOf(numMinus1, numPlus1, lastMinus1, lastPlus1) - setOf(batchWinningNumber) - allPerms).sorted()
        val tuwtNumbers = permsOnly + nearOnly

        val exactAmt = if (brakeLimit > 0) minOf(exposureMap[batchWinningNumber]?.totalBetAmount ?: 0, brakeLimit) else (exposureMap[batchWinningNumber]?.totalBetAmount ?: 0)
        listOf(BrakedWinRow(batchWinningNumber, isExact = true, amount = exactAmt)) +
            tuwtNumbers.map { num ->
                val amt = if (brakeLimit > 0) minOf(exposureMap[num]?.totalBetAmount ?: 0, brakeLimit) else (exposureMap[num]?.totalBetAmount ?: 0)
                BrakedWinRow(num, isExact = false, amount = amt)
            }
    }

    val totalBraked = brakedExposures.sumOf { keptAmount(it.totalBetAmount) }
    val commAmount = (totalBraked * meCommRate).toLong()
    val netAfterComm = totalBraked - commAmount

    val exactKeptAmt = if (isWonDeclared) keptAmount(exposureMap[batchWinningNumber]?.totalBetAmount ?: 0) else 0
    val exactPayout = (exactKeptAmt * exactMult).toLong()

    val tuwtKeptAmt = if (isWonDeclared) {
        brakedWinRows.filter { !it.isExact }.sumOf { it.amount }
    } else 0
    val tuwtPayout = (tuwtKeptAmt * permMult).toLong()

    val totalPayout = exactPayout + tuwtPayout
    val brakedProfit = netAfterComm - totalPayout

    val totalOverflow = overflowExposures.sumOf { it.overflowAmount }

    val orangeColor = MaterialTheme.colorScheme.primary
    val blueColor = MaterialTheme.colorScheme.primary

    var showBrakeDialog by remember { mutableStateOf(false) }

    // Overflow voucher dialog state — holds a snapshot taken at the moment "တင်မည်" was pressed
    data class OverflowSnapshot(val voucherId: Int, val items: List<Pair<String, Int>>, val total: Int, val timestamp: String, val batch: Int)
    var overflowSnapshot by remember { mutableStateOf<OverflowSnapshot?>(null) }

    BackHandler {
        when {
            showBrakeDialog -> showBrakeDialog = false
            overflowSnapshot != null -> overflowSnapshot = null
            else -> onNavigateBack()
        }
    }

    // ── Brake Limit Dialog ──────────────────────────────────────────────────
    if (showBrakeDialog) {
        var brakeInput by remember { mutableStateOf(brakeLimit.toString()) }
        AlertDialog(
            onDismissRequest = { showBrakeDialog = false },
            title = { Text("ဘရိတ် ပြောင်းရန်") },
            text = {
                OutlinedTextField(
                    value = brakeInput,
                    onValueChange = { brakeInput = it.filter { c -> c.isDigit() } },
                    label = { Text("ဘရိတ် ပမာဏ") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    brakeInput.toIntOrNull()?.let { viewModel.saveBrakeLimit(it) }
                    showBrakeDialog = false
                }) { Text("သိမ်းမည်") }
            },
            dismissButton = {
                TextButton(onClick = { showBrakeDialog = false }) { Text("မလုပ်တော့") }
            }
        )
    }

    // ── Overflow Voucher Dialog ─────────────────────────────────────────────
    val snapshot = overflowSnapshot
    if (snapshot != null) {
        val voucherText = buildString {
            appendLine("      တင်ကွက် ဘောင်ချာ    ")
            appendLine(" ဘောင်ချာ : #${snapshot.voucherId}")
            appendLine(" အကြိမ်   : ${snapshot.batch}")
            appendLine(" အချိန်   : ${snapshot.timestamp}")
            appendLine("------------------------")
            snapshot.items.forEachIndexed { idx, (num, amt) ->
                appendLine(" ${idx + 1}. $num = $amt")
            }
            appendLine("------------------------")
            appendLine(" စုစုပေါင်း : ${snapshot.total} Ks")
            appendLine("   * အထက်ဒိုင် တင်ကွက် *  ")
        }

        Dialog(
            onDismissRequest = {
                overflowSnapshot = null
                onNavigateToExportHistory()
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // ── Title ──
                    Text(
                        "တင်ကွက် ဘောင်ချာ",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = blueColor,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))

                    // ── Header info ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(blueColor.copy(alpha = 0.1f), MaterialTheme.shapes.small)
                            .padding(8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("အကြိမ် : ${snapshot.batch}", fontWeight = FontWeight.SemiBold)
                            Text(snapshot.timestamp, style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // ── Divider header ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(orangeColor)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "စဉ်",
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(44.dp),
                            textAlign = TextAlign.Start
                        )
                        Text(
                            "ဂဏန်း",
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            "ပမာဏ",
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                    }

                    // ── Number rows ──
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .border(1.dp, orangeColor.copy(alpha = 0.4f))
                    ) {
                        snapshot.items.forEachIndexed { index, (num, amt) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(44.dp)
                                )
                                Text(
                                    num,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    "$amt Ks",
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center
                                )
                            }
                            if (index < snapshot.items.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                            }
                        }
                    }

                    // ── Footer total ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(orangeColor)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("စုစုပေါင်း", color = MaterialTheme.colorScheme.onTertiary, fontWeight = FontWeight.Bold)
                        Text("${snapshot.total} Ks", color = MaterialTheme.colorScheme.onTertiary, fontWeight = FontWeight.Bold)
                    }

                    Spacer(Modifier.height(12.dp))

                    // ── Action buttons ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Print button
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                        val paperSize = prefs.getString("paperSize", "58mm") ?: "58mm"
                                        val voucherData = com.threeDLedger.logic.BluetoothPrinter.VoucherData(
                                            batchNumber = snapshot.batch,
                                            voucherId = 0,
                                            date = snapshot.timestamp,
                                            customerName = "အထက်ဒိုင် (တင်ကွက်)",
                                            bets = snapshot.items,
                                            totalAmount = snapshot.total,
                                            footerText = "*** အထက်ဒိုင် တင်ကွက် ***"
                                        )
                                        val bitmap = com.threeDLedger.logic.BluetoothPrinter.createVoucherBitmap(voucherData, paperSize)
                                        com.threeDLedger.logic.BluetoothPrinter.printBitmap(bitmap, paperSize)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "ပရင်တာ အမှား: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text("ပရင့်ထုတ်မည်", color = MaterialTheme.colorScheme.onSecondary)
                        }

                        // Copy button
                        Button(
                            onClick = {
                                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Overflow Voucher", voucherText)
                                clipboardManager.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "ကူးယူပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = blueColor)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("ကော်ပီကူးမည်", color = MaterialTheme.colorScheme.onPrimary)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Close / go to history
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { overflowSnapshot = null },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("ပိတ်မည်")
                        }
                        TextButton(
                            onClick = {
                                overflowSnapshot = null
                                onNavigateToExportHistory()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("မှတ်တမ်းကြည့်မည်")
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("တင်ကွက်များ", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = blueColor)
            )
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onNavigateToExportHistory,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("မှတ်တမ်းများ", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(
                    onClick = {
                        if (overflowExposures.isNotEmpty()) {
                            val items = overflowExposures.map { it.number to it.overflowAmount }
                            val total = overflowExposures.sumOf { it.overflowAmount }
                            val ts = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                            viewModel.exportOverflow { recordId ->
                                overflowSnapshot = OverflowSnapshot(recordId, items, total, ts, currentBatch)
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (overflowExposures.isNotEmpty()) blueColor else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("တင်မည်", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(blueColor)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "အကြိမ် : $currentBatch",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isWonDeclared) {
                    Surface(
                        color = WinExactRed,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        Text(
                            text = batchWinningNumber,
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
                Text(
                    "ဘရိတ် : $brakeLimit",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clickable { showBrakeDialog = true }
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f), MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Tables
            Row(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                // Left Table — Bets within brake limit (≤ brakeLimit, > 0) or Win/Tut breakdown
                Column(modifier = Modifier.weight(1f).border(1.dp, orangeColor).padding(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().background(orangeColor).padding(vertical = 6.dp, horizontal = 4.dp)) {
                        Text("ဂဏန်းများ", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(if (isWonDeclared) "ပမာဏ" else "ဘရိတ်အတွင်း", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    LazyColumn(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface)) {
                        if (isWonDeclared) {
                            items(brakedWinRows) { row ->
                                val rowBg = if (row.isExact) WinExactRed else Color.Transparent
                                val textColor = if (row.isExact) Color.White else MaterialTheme.colorScheme.onSurface
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(rowBg)
                                        .padding(vertical = 5.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = row.number,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        color = textColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    Text(
                                        text = "%,d".format(row.amount),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.End,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = textColor,
                                        fontWeight = if (row.isExact || row.amount > 0) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        } else {
                            if (brakedExposures.isEmpty()) {
                                item {
                                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                        Text("ထိုးမှု မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                    }
                                }
                            }
                            items(brakedExposures) { exposure ->
                                val kept = keptAmount(exposure.totalBetAmount)
                                val isOverflowing = brakeLimit > 0 && exposure.totalBetAmount > brakeLimit
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 4.dp)) {
                                    Text(
                                        exposure.number,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                        color = if (isOverflowing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
                                    Text(
                                        "%,d".format(kept),
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.End,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        color = if (isOverflowing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 14.sp
                                    )
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                            }
                        }
                    }
                    if (isWonDeclared) {
                        Column(modifier = Modifier.fillMaxWidth().background(orangeColor)) {
                            BrakeSummaryRow("စုစုပေါင်း", "%,d".format(totalBraked))
                            val commLabel = if (meCommRate > 0) "ကော် (${(meCommRate * 100).toInt()}%)" else "ကော်"
                            BrakeSummaryRow(commLabel, "%,d".format(commAmount))
                            BrakeSummaryRow("နုတ်ပြီး", "%,d".format(netAfterComm))
                            BrakeSummaryRow("ပေါက်သီး", "%,d".format(exactPayout))
                            BrakeSummaryRow("တွတ်", "%,d".format(tuwtPayout))
                            BrakeSummaryRow("အမြတ်ငွေ", "%,d".format(brakedProfit), isProfit = true)
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth().background(orangeColor).padding(vertical = 6.dp, horizontal = 4.dp)) {
                            Text("စုစုပေါင်း", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text("%,d".format(totalBraked), color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                        }
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Right Table — Overflow only
                Column(modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.error).padding(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error).padding(vertical = 6.dp, horizontal = 4.dp)) {
                        Text("ဂဏန်းများ", color = MaterialTheme.colorScheme.onError, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(if (isWonDeclared) "ပမာဏ" else "ကျော်ပမာဏ", color = MaterialTheme.colorScheme.onError, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    LazyColumn(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface)) {
                        if (overflowExposures.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("ကျော်ကွက် မရှိပါ", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        items(overflowExposures) { exposure ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f))
                                    .padding(vertical = 5.dp, horizontal = 2.dp)
                            ) {
                                Text(
                                    exposure.number,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    "%,d".format(exposure.overflowAmount),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                    val rightFooterBg = if (totalOverflow > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.surfaceVariant
                    val rightFooterFg = if (totalOverflow > 0) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onSurfaceVariant
                    Row(modifier = Modifier.fillMaxWidth().background(rightFooterBg).padding(vertical = 6.dp, horizontal = 4.dp)) {
                        Text("စုစုပေါင်း", color = rightFooterFg, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text("%,d".format(totalOverflow), color = rightFooterFg, modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
private fun BrakeSummaryRow(
    label: String,
    value: String,
    isProfit: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp
        )
        Box(
            modifier = Modifier
                .height(16.dp)
                .width(0.5.dp)
                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f))
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp),
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f), thickness = 0.5.dp)
}
