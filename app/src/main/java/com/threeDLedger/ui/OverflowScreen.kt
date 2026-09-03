package com.threeDLedger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date

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
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Sort exposures — ascending by number string (000 → 999)
    val overflowExposures = ledgerExposures.filter { it.overflowAmount > 0 }.sortedBy { it.number }

    // Left table: ALL numbers with any bet, sorted ascending by number.
    // Display amount = min(totalBetAmount, brakeLimit) — i.e. the "kept" portion capped at brake.
    // If brake is 0 (not set), show full totalBetAmount.
    val brakedExposures = ledgerExposures
        .filter { it.totalBetAmount > 0 }
        .sortedBy { it.number }

    fun keptAmount(totalBetAmount: Int): Int =
        if (brakeLimit > 0) minOf(totalBetAmount, brakeLimit) else totalBetAmount

    val totalBraked = brakedExposures.sumOf { keptAmount(it.totalBetAmount) }
    val totalOverflow = overflowExposures.sumOf { it.overflowAmount }

    val orangeColor = MaterialTheme.colorScheme.tertiary
    val blueColor = MaterialTheme.colorScheme.primary

    var showBrakeDialog by remember { mutableStateOf(false) }

    // Overflow voucher dialog state — holds a snapshot taken at the moment "တင်မည်" was pressed
    data class OverflowSnapshot(val items: List<Pair<String, Int>>, val total: Int, val timestamp: String, val batch: Int)
    var overflowSnapshot by remember { mutableStateOf<OverflowSnapshot?>(null) }

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
                    label = { Text("ဘရိတ် (Brake Limit)") },
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
            appendLine("========================")
            appendLine("      တင်ကွက် VOUCHER    ")
            appendLine("========================")
            appendLine(" အကြိမ်   : ${snapshot.batch}")
            appendLine(" အချိန်   : ${snapshot.timestamp}")
            appendLine("------------------------")
            snapshot.items.forEach { (num, amt) ->
                appendLine(" ${num.padEnd(5)} = $amt")
            }
            appendLine("------------------------")
            appendLine(" စုစုပေါင်း : ${snapshot.total} Ks")
            appendLine("========================")
            appendLine("   *** Upper Agent ***  ")
            appendLine("========================")
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
                        "တင်ကွက် Voucher",
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
                                Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
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
                                            customerName = "Upper Agent (တင်ကွက်)",
                                            bets = snapshot.items,
                                            totalAmount = snapshot.total,
                                            footerText = "*** Upper Agent Overflow ***"
                                        )
                                        val bitmap = com.threeDLedger.logic.BluetoothPrinter.createVoucherBitmap(voucherData, paperSize)
                                        com.threeDLedger.logic.BluetoothPrinter.printBitmap(bitmap, paperSize)
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "ပရင်တာ error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondary)
                            Spacer(Modifier.width(6.dp))
                            Text("Print", color = MaterialTheme.colorScheme.onSecondary)
                        }

                        // Copy button
                        Button(
                            onClick = {
                                val clipboardManager = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                val clip = android.content.ClipData.newPlainText("Overflow Voucher", voucherText)
                                clipboardManager.setPrimaryClip(clip)
                                android.widget.Toast.makeText(context, "Copied!", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = blueColor)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                            Spacer(Modifier.width(6.dp))
                            Text("Copy", color = MaterialTheme.colorScheme.onPrimary)
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
                            // Snapshot BEFORE export so we can display what was sent
                            val items = overflowExposures.map { it.number to it.overflowAmount }
                            val total = overflowExposures.sumOf { it.overflowAmount }
                            val ts = SimpleDateFormat("dd/MM/yyyy HH:mm:ss").format(Date())
                            overflowSnapshot = OverflowSnapshot(items, total, ts, currentBatch)
                            // Record export in DB — overflowAmount will recalculate to 0 reactively
                            viewModel.exportOverflow()
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
                modifier = Modifier.fillMaxWidth().background(blueColor).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("အကြိမ် : $currentBatch", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(32.dp))
                Text(
                    "ဘရိတ် : $brakeLimit",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 16.sp,
                    modifier = Modifier
                        .clickable { showBrakeDialog = true }
                        .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.15f), MaterialTheme.shapes.small)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            // Tables
            Row(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                // Left Table — Bets within brake limit (≤ brakeLimit, > 0)
                Column(modifier = Modifier.weight(1f).border(1.dp, orangeColor).padding(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().background(orangeColor).padding(6.dp)) {
                        Text("ဂဏန်းများ", color = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("ဘရိတ်အတ်ွင်း", color = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    LazyColumn(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface)) {
                        if (brakedExposures.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("ထိုးမှု မရှိသောပါ", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                                }
                            }
                        }
                        items(brakedExposures) { exposure ->
                            val kept = keptAmount(exposure.totalBetAmount)
                            val isOverflowing = brakeLimit > 0 && exposure.totalBetAmount > brakeLimit
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 2.dp)) {
                                Text(
                                    exposure.number,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = if (isOverflowing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                                Text(
                                    "$kept",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = if (isOverflowing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().background(orangeColor).padding(6.dp)) {
                        Text("စုစုပေါင်း", color = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("$totalBraked", color = MaterialTheme.colorScheme.onTertiary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Right Table — Overflow only
                Column(modifier = Modifier.weight(1f).border(1.dp, MaterialTheme.colorScheme.error).padding(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error).padding(6.dp)) {
                        Text("ဂဏန်းများ", color = MaterialTheme.colorScheme.onError, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("ကျော်ပမာဏ", color = MaterialTheme.colorScheme.onError, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    LazyColumn(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface)) {
                        if (overflowExposures.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                                    Text("ကျော်မှု မရှိပါ", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
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
                                    fontSize = 11.sp
                                )
                                Text(
                                    "${exposure.overflowAmount}",
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth().background(
                        if (totalOverflow > 0) MaterialTheme.colorScheme.error else orangeColor
                    ).padding(6.dp)) {
                        Text("စုစုပေါင်း", color = MaterialTheme.colorScheme.onError, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Text("$totalOverflow", color = MaterialTheme.colorScheme.onError, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}
