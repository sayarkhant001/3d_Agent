package com.threeDLedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.data.Dine
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

data class OverflowSnapshot(
    val voucherId: Int,
    val items: List<Pair<String, Int>>,
    val total: Int,
    val timestamp: String,
    val batch: Int,
    val dineName: String = "",
    val voucherSerial: Int = 1,
    val commissionRate: Double = 15.0,
    val exactMultiplier: Int = 600,
    val tuwtMultiplier: Int = 10
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
    val winningNumber by viewModel.winningNumber.collectAsStateWithLifecycle()
    val allCustomers by viewModel.customers.collectAsStateWithLifecycle()
    val allDines by viewModel.allDines.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
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

    // Sort exposures — ascending by number (numeric)
    val overflowExposures = ledgerExposures.filter { it.overflowAmount > 0 }.sortedBy { it.number.toIntOrNull() ?: 0 }

    // Left table: numbers with bets, sorted ascending
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
    val commAmount = (totalBraked * (meCommRate / 100.0)).toLong()
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

    // 3D Emerald Theme Palette
    val emeraldPrimary = Color(0xFF047857)
    val emeraldDark = Color(0xFF065F46)
    val emeraldLight = Color(0xFFECFDF5)
    val orangeButton = Color(0xFFF97316)

    var showBrakeDialog by remember { mutableStateOf(false) }
    var showSelectDineDialog by remember { mutableStateOf(false) }
    var showManageDineDialog by remember { mutableStateOf(false) }

    // Dine Form State
    var newDineName by remember { mutableStateOf("") }
    var newDineComm by remember { mutableStateOf("15") }
    var newDineExactMult by remember { mutableStateOf("600") }
    var newDineTuwtMult by remember { mutableStateOf("10") }

    var overflowSnapshot by remember { mutableStateOf<OverflowSnapshot?>(null) }

    BackHandler {
        when {
            showBrakeDialog -> showBrakeDialog = false
            showSelectDineDialog -> showSelectDineDialog = false
            showManageDineDialog -> showManageDineDialog = false
            overflowSnapshot != null -> overflowSnapshot = null
            else -> onNavigateBack()
        }
    }

    // ── Brake Limit Dialog ──────────────────────────────────────────────────
    if (showBrakeDialog) {
        var brakeInput by remember { mutableStateOf(brakeLimit.toString()) }
        AlertDialog(
            onDismissRequest = { showBrakeDialog = false },
            title = { Text("ဘရိတ် ပြောင်းရန်", fontWeight = FontWeight.Bold, color = emeraldPrimary) },
            text = {
                OutlinedTextField(
                    value = brakeInput,
                    onValueChange = { brakeInput = it.filter { c -> c.isDigit() } },
                    label = { Text("ဘရိတ် ပမာဏ (ကျပ်)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        brakeInput.toIntOrNull()?.let { viewModel.saveBrakeLimit(it) }
                        showBrakeDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary)
                ) { Text("သိမ်းမည်", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { showBrakeDialog = false }) { Text("မလုပ်တော့ပါ") }
            }
        )
    }

    // ── Select Dine Dialog (Matching Screenshot 2 in 3D Emerald Style) ───────
    if (showSelectDineDialog) {
        AlertDialog(
            onDismissRequest = { showSelectDineDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ဒိုင် ရွေးချယ်ပါ",
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        color = emeraldPrimary
                    )
                    IconButton(onClick = { showManageDineDialog = true }) {
                        Icon(Icons.Default.GroupAdd, contentDescription = "ဒိုင်ထည့်ရန်", tint = emeraldPrimary)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (allDines.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "ဒိုင် မရှိသေးပါ။ အောက်ပါခလုတ်ဖြင့် ဒိုင်အသစ် ထည့်သွင်းပါ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                fontSize = 13.sp
                            )
                        }
                    } else {
                        allDines.forEach { dine ->
                            Surface(
                                onClick = {
                                    showSelectDineDialog = false
                                    val items = overflowExposures.map { it.number to it.overflowAmount }
                                    val total = overflowExposures.sumOf { it.overflowAmount }
                                    val ts = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date())
                                    viewModel.exportOverflowToDine(dine) { recordId, serial ->
                                        overflowSnapshot = OverflowSnapshot(
                                            voucherId = recordId,
                                            items = items,
                                            total = total,
                                            timestamp = ts,
                                            batch = currentBatch,
                                            dineName = dine.name,
                                            voucherSerial = serial,
                                            commissionRate = dine.commissionRate,
                                            exactMultiplier = dine.exactMultiplier,
                                            tuwtMultiplier = dine.tuwtMultiplier
                                        )
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, emeraldPrimary.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 6.dp)) {
                                        Text(
                                            text = dine.name,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "ကော်မရှင် ${dine.commissionRate}%",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = Color(0xFFD97706)
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = emeraldLight
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                text = "ဒဲ့: ${dine.exactMultiplier} ဆ",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = emeraldPrimary
                                            )
                                            Text(
                                                text = "တွတ်: ${dine.tuwtMultiplier} ဆ",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF4338CA)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = {
                            showManageDineDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        border = BorderStroke(1.dp, emeraldPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = emeraldPrimary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+ ဒိုင် အသစ်ထည့်မည်", color = emeraldPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSelectDineDialog = false }) {
                    Text("မလုပ်တော့ပါ")
                }
            }
        )
    }

    // ── Manage Dine Dialog (Matching Screenshot 3 in 3D Emerald Style) ───────
    if (showManageDineDialog) {
        Dialog(
            onDismissRequest = { showManageDineDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "ဒိုင် စီမံခန့်ခွဲမှု",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = emeraldPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )

                    // Add Form Card (Emerald container as in screenshot)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = emeraldDark)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ဒိုင် အိုင်ဒီ : ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("${allDines.size + 1}", color = Color(0xFFFFD93D), fontWeight = FontWeight.Black, fontSize = 15.sp)
                            }

                            OutlinedTextField(
                                value = newDineName,
                                onValueChange = { newDineName = it },
                                label = { Text("ဒိုင်အမည် ရိုက်ထည့်ပါ", color = Color.White.copy(alpha = 0.8f)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                                )
                            )

                            OutlinedTextField(
                                value = newDineComm,
                                onValueChange = { newDineComm = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("ကော်မရှင် %", color = Color.White.copy(alpha = 0.8f)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = newDineExactMult,
                                    onValueChange = { newDineExactMult = it.filter { c -> c.isDigit() } },
                                    label = { Text("ဒဲ့ ပေါက်ဆ", color = Color.White.copy(alpha = 0.8f)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                                    )
                                )
                                OutlinedTextField(
                                    value = newDineTuwtMult,
                                    onValueChange = { newDineTuwtMult = it.filter { c -> c.isDigit() } },
                                    label = { Text("တွတ် ပေါက်ဆ", color = Color.White.copy(alpha = 0.8f)) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White,
                                        focusedContainerColor = Color.White.copy(alpha = 0.15f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                                        focusedBorderColor = Color.White,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                    }

                    // Action Buttons (Cancel / Add)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                newDineName = ""
                                showManageDineDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("မလုပ်တော့ပါ", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                if (newDineName.isNotBlank()) {
                                    val comm = newDineComm.toDoubleOrNull() ?: 15.0
                                    val exact = newDineExactMult.toIntOrNull() ?: 600
                                    val tuwt = newDineTuwtMult.toIntOrNull() ?: 10
                                    viewModel.addDine(newDineName.trim(), comm, exact, tuwt)
                                    newDineName = ""
                                    android.widget.Toast.makeText(context, "ဒိုင် အသစ် သိမ်းဆည်းပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    android.widget.Toast.makeText(context, "ဒိုင်အမည် ရိုက်ထည့်ပေးပါ", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("ထည့်မည်", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Existing Dines List Table (Matching Screenshot 3)
                    Text("လက်ရှိ ဒိုင်များ စာရင်း", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Column {
                            // Table Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 8.dp, horizontal = 12.dp)
                            ) {
                                Text("အမည်", modifier = Modifier.weight(1.3f), fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                Text("ကော်မရှင်", modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("ဒဲ့/တွတ်", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("ဖျက်မည်", modifier = Modifier.width(40.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                            HorizontalDivider()

                            if (allDines.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("ဒိုင် မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                allDines.forEach { dine ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp, horizontal = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(dine.name, modifier = Modifier.weight(1.3f), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text("${dine.commissionRate}%", modifier = Modifier.weight(0.9f), textAlign = TextAlign.Center, fontSize = 12.5.sp)
                                        Text("${dine.exactMultiplier}/${dine.tuwtMultiplier}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp)
                                        IconButton(
                                            onClick = { viewModel.deleteDine(dine) },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "ဖျက်မည်", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                                        }
                                    }
                                    HorizontalDivider(thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Overflow Voucher Snapshot Dialog with Per-Dine Serial Copy ───────────
    val snapshot = overflowSnapshot
    if (snapshot != null) {
        val voucherText = buildString {
            appendLine("      တင်ကွက် ဘောင်ချာ    ")
            if (snapshot.dineName.isNotBlank()) {
                appendLine(" ဒိုင်      : ${snapshot.dineName}")
            }
            appendLine(" ဘောင်ချာ : #${snapshot.voucherSerial}")
            appendLine(" အကြိမ်   : ${snapshot.batch}")
            appendLine(" အချိန်   : ${snapshot.timestamp}")
            appendLine("------------------------")
            snapshot.items.forEachIndexed { idx, (num, amt) ->
                appendLine(" ${idx + 1}. $num = $amt")
            }
            appendLine("------------------------")
            appendLine(" စုစုပေါင်း : %,d ကျပ်".format(snapshot.total))
            val comm = (snapshot.total * (snapshot.commissionRate / 100.0)).toInt()
            if (comm > 0) {
                appendLine(" ကော်မရှင် (${snapshot.commissionRate}%) : %,d ကျပ်".format(comm))
                appendLine(" ပေးချေရန် : %,d ကျပ်".format(snapshot.total - comm))
            }
            appendLine(" ဒဲ့: ${snapshot.exactMultiplier}ဆ | တွတ်: ${snapshot.tuwtMultiplier}ဆ")
            appendLine("    * တင်ကွက် *    ")
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
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "တင်ကွက် ဘောင်ချာ",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = emeraldPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = emeraldLight,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ဘောင်ချာ : #${snapshot.voucherSerial}", fontWeight = FontWeight.Bold, color = emeraldDark)
                                Text("အကြိမ် : ${snapshot.batch}", fontWeight = FontWeight.SemiBold)
                            }
                            if (snapshot.dineName.isNotBlank()) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("ဒိုင် : ${snapshot.dineName}", fontWeight = FontWeight.Bold, color = emeraldPrimary)
                                    Text("ဒဲ့: ${snapshot.exactMultiplier}ဆ / တွတ်: ${snapshot.tuwtMultiplier}ဆ", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(snapshot.timestamp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Number List
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(emeraldPrimary)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("စဉ်", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.width(44.dp))
                        Text("ဂဏန်း", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("ပမာဏ (ကျပ်)", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .border(1.dp, emeraldPrimary.copy(alpha = 0.3f))
                    ) {
                        snapshot.items.forEachIndexed { index, (num, amt) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (index % 2 == 0) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${index + 1}", modifier = Modifier.width(44.dp), style = MaterialTheme.typography.bodySmall)
                                Text(num, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
                                Text("%,d".format(amt), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
                        }
                    }

                    // Total & Commission Info
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("စုစုပေါင်း တင်ငွေ", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                Text("%,d ကျပ်".format(snapshot.total), fontWeight = FontWeight.Black, fontSize = 15.sp, color = emeraldPrimary)
                            }
                            val comm = (snapshot.total * (snapshot.commissionRate / 100.0)).toInt()
                            if (comm > 0) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("ကော်မရှင် (${snapshot.commissionRate}%)", fontSize = 12.sp, color = Color(0xFFD97706))
                                    Text("%,d ကျပ်".format(comm), fontSize = 12.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("ပေးချေရမည့်ငွေ", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                                    Text("%,d ကျပ်".format(snapshot.total - comm), fontWeight = FontWeight.Black, fontSize = 14.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // Buttons (Copy / Done)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(voucherText))
                                android.widget.Toast.makeText(context, "ဘောင်ချာ အမှတ် #${snapshot.voucherSerial} ကော်ပီ ကူးပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, emeraldPrimary)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = emeraldPrimary, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("ကော်ပီ", color = emeraldPrimary, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                overflowSnapshot = null
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("ပြီးပါပြီ", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("တင်ကွက်", color = MaterialTheme.colorScheme.onBackground, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "နောက်သို့", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    IconButton(onClick = { showManageDineDialog = true }) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "ဒိုင်များ စီမံရန်", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onNavigateToExportHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "မှတ်တမ်းများ", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Winning Declared Warning Banner
            if (isWonDeclared) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFEE2E2),
                    border = BorderStroke(1.dp, Color(0xFFF87171))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(22.dp))
                        Text(
                            text = "ပေါက်ဂဏန်း ထွက်ပြီးပါပြီ။ အထက်ဒိုင်သို့ တင်ပို့၍ မရတော့ပါ။",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            color = Color(0xFFB91C1C)
                        )
                    }
                }
            }

            // Subheader Bar (Matching Screenshot 1: Batch & Brake)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(emeraldDark)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "အကြိမ် $currentBatch",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "ခေါင်းချိုးရန်",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Surface(
                        color = Color(0xFF1E3A8A).copy(alpha = 0.6f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { showBrakeDialog = true }
                    ) {
                        Text(
                            "%,d".format(brakeLimit),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // Subheader Bar 2 (Matching Screenshot 1: စုစုပေါင်း & ခေါင်းချိုး/ဖြတ်)
            val grossBatchTotal = totalBraked + totalOverflow
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("စုစုပေါင်း", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%,d".format(grossBatchTotal), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = emeraldPrimary, fontFamily = FontFamily.Monospace)
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("ခေါင်းချိုး/ဖြတ်", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "%,d / -%,d".format(totalBraked, totalOverflow),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFB45309),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Tables Section (Matching Image 1: 2-column layout)
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                // Left Column — Kept Bets (≤ brakeLimit)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, emeraldPrimary)
                        .padding(2.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(emeraldPrimary)
                            .padding(vertical = 5.dp, horizontal = 4.dp)
                    ) {
                        Text("ဂဏန်း", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("ပမာဏ", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    // Rows
                    LazyColumn(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface)) {
                        if (brakedExposures.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("ထိုးမှု မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                        }
                        items(brakedExposures, key = { it.number }) { exposure ->
                            val kept = keptAmount(exposure.totalBetAmount)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Text(exposure.number, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, fontFamily = FontFamily.Monospace)
                                Text("%,d".format(kept), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 13.5.sp, fontFamily = FontFamily.Monospace)
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                    // Subtotal
                    Row(modifier = Modifier.fillMaxWidth().background(emeraldLight).padding(vertical = 5.dp, horizontal = 4.dp)) {
                        Text("စုစုပေါင်း", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Text("%,d".format(totalBraked), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = emeraldPrimary)
                    }
                    // Action Buttons under Left Table (In 3D: No ရက်ချုပ်, + သိမ်းမည် and 📋 ကော်ပီ)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Button(
                            onClick = {
                                android.widget.Toast.makeText(context, "သိမ်းဆည်းထားပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = emeraldPrimary),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("+ သိမ်းမည်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = {
                                val keptText = brakedExposures.joinToString("\n") { "${it.number} = ${keptAmount(it.totalBetAmount)}" }
                                clipboardManager.setText(AnnotatedString(keptText))
                                android.widget.Toast.makeText(context, "ကော်ပီ ကူးပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth().height(34.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = emeraldDark),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("ကော်ပီ", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Right Column — Overflow Bets
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.dp, emeraldPrimary)
                        .padding(2.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(emeraldPrimary)
                            .padding(vertical = 5.dp, horizontal = 4.dp)
                    ) {
                        Text("ဂဏန်း", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("ပမာဏ", color = Color.White, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    // Rows
                    LazyColumn(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.surface)) {
                        if (overflowExposures.isEmpty()) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    Text("ကျော်ကွက် မရှိပါ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                                }
                            }
                        }
                        items(overflowExposures, key = { it.number }) { exposure ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Text(exposure.number, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, fontFamily = FontFamily.Monospace)
                                Text("%,d".format(exposure.overflowAmount), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Bold, fontSize = 13.5.sp, fontFamily = FontFamily.Monospace)
                            }
                            HorizontalDivider(thickness = 0.5.dp)
                        }
                    }
                    // Subtotal
                    Row(modifier = Modifier.fillMaxWidth().background(emeraldLight).padding(vertical = 5.dp, horizontal = 4.dp)) {
                        Text("စုစုပေါင်း", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
                        Text("%,d".format(totalOverflow), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontWeight = FontWeight.Black, fontSize = 12.sp, color = emeraldPrimary)
                    }
                    // Action Buttons under Right Table (+ တင်မည်, မှတ်တမ်းများ)
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isWonDeclared) {
                                    android.widget.Toast.makeText(context, "ပေါက်ဂဏန်း ထွက်ပြီးပါပြီ။ အထက်ဒိုင်သို့ တင်ပို့၍ မရတော့ပါ။", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (overflowExposures.isNotEmpty()) {
                                    showSelectDineDialog = true
                                } else {
                                    android.widget.Toast.makeText(context, "တင်ရန် ဘရိတ်ကျော် ဂဏန်း မရှိပါ", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            enabled = !isWonDeclared && overflowExposures.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isWonDeclared && overflowExposures.isNotEmpty()) emeraldPrimary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(if (isWonDeclared) Icons.Default.Lock else Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isWonDeclared) "တင်ပို့၍မရပါ" else "+ တင်မည်", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onNavigateToExportHistory,
                            modifier = Modifier.fillMaxWidth().height(34.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = orangeButton),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("မှတ်တမ်းများ", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
