package com.threeDLedger.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

// ── Result types ──────────────────────────────────────────────────────────────

enum class WinType { EXACT, PERMUTATION, NEAR }

data class WinnerResult(
    val customerName: String,
    val betNumber: String,
    val betAmount: Int,
    val payoutAmount: Double,
    val winType: WinType
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinnerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var winningNumber by remember { mutableStateOf("") }
    val currentBatch = viewModel.currentBatch.collectAsStateWithLifecycle().value
    var targetBatch  by remember { mutableStateOf(currentBatch.toString()) }
    var exactMult    by remember { mutableStateOf("600") }
    var permMult     by remember { mutableStateOf("100") }
    var nearMult     by remember { mutableStateOf("10") }
    var isFetching   by remember { mutableStateOf(false) }
    var fetchStatus  by remember { mutableStateOf("") }   // "", "checking", "ok", "error"
    var isFinalResult by remember { mutableStateOf(false) }
    var resultSession by remember { mutableStateOf("") }

    val allBets      by viewModel.allBets.collectAsStateWithLifecycle()
    val allVouchers  by viewModel.vouchersWithCustomer.collectAsStateWithLifecycle()
    val allCustomers by viewModel.customers.collectAsStateWithLifecycle()
    var results      by remember { mutableStateOf<List<WinnerResult>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    // Auto-fetch on open
    LaunchedEffect(Unit) { fetchWinningNumber(
        onStart  = { isFetching = true; fetchStatus = "checking" },
        onResult = { num, isFinal, session ->
            if (!num.isNullOrEmpty()) winningNumber = num
            isFinalResult = isFinal; resultSession = session
            fetchStatus = if (num.isNullOrEmpty()) "error" else "ok"
            isFetching = false
        },
        onError  = { isFetching = false; fetchStatus = "error" }
    )}

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ပေါက်ဂဏန်း စာရင်း", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        if (results.isNotEmpty())
                            Text("${results.size} ကြိမ် ပေါက်", fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Inputs card ────────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                        OutlinedTextField(
                            value = targetBatch,
                            onValueChange = { targetBatch = it },
                            label = { Text("အကြိမ် (Batch No)") },
                            leadingIcon = { Icon(Icons.Default.Numbers, null) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true
                        )

                        // Winning number + fetch button
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = winningNumber,
                                    onValueChange = { winningNumber = it; fetchStatus = "" },
                                    label = { Text("ပေါက်ဂဏန်း") },
                                    leadingIcon = { Icon(Icons.Default.Star, null, tint = Color(0xFFFFD93D)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                // Status indicator
                                if (fetchStatus.isNotEmpty()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                                    ) {
                                        val dotColor = when (fetchStatus) {
                                            "ok"       -> if (isFinalResult) Color(0xFF43AA8B) else Color(0xFFFFD93D)
                                            "error"    -> MaterialTheme.colorScheme.error
                                            else       -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                        Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                                        Spacer(Modifier.width(5.dp))
                                        Text(
                                            when (fetchStatus) {
                                                "checking" -> "Firebase မှ ယူနေသည်..."
                                                "ok"       -> if (isFinalResult) "✓ အတည်ပြု ရလဒ် ($resultSession)"
                                                              else "⏳ ယာယီ ရလဒ် ($resultSession)"
                                                else       -> "Firebase ချိတ်ဆက်မရပါ — ကိုယ်တိုင် ရိုက်ထည့်ပါ"
                                            },
                                            fontSize = 11.sp, color = dotColor, fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        fetchWinningNumber(
                                            onStart  = { isFetching = true; fetchStatus = "checking" },
                                            onResult = { num, isFinal, session ->
                                                if (!num.isNullOrEmpty()) winningNumber = num
                                                isFinalResult = isFinal; resultSession = session
                                                fetchStatus = if (num.isNullOrEmpty()) "error" else "ok"
                                                isFetching = false
                                            },
                                            onError  = { isFetching = false; fetchStatus = "error" }
                                        )
                                    }
                                },
                                enabled = !isFetching,
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
                            ) {
                                if (isFetching)
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary)
                                else {
                                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Fetch")
                                }
                            }
                        }

                        // Multipliers
                        Text("အဆ သတ်မှတ်ခြင်း", fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MultiplierField("တိုက်ရိုက် (Exact)", exactMult, Color(0xFF43AA8B), Modifier.weight(1f)) { exactMult = it }
                            MultiplierField("တွတ် (Perm)", permMult, Color(0xFF6C63FF), Modifier.weight(1f)) { permMult = it }
                            MultiplierField("အနီး (Near)", nearMult, Color(0xFF4ECDC4), Modifier.weight(1f)) { nearMult = it }
                        }

                        // Calculate button
                        Button(
                            onClick = {
                                if (winningNumber.length == 3) {
                                    results = calculate(
                                        winningNumber   = winningNumber,
                                        targetBatchInt  = targetBatch.toIntOrNull() ?: currentBatch,
                                        exactMult       = exactMult.toDoubleOrNull() ?: 0.0,
                                        permMult        = permMult.toDoubleOrNull() ?: 0.0,
                                        nearMult        = nearMult.toDoubleOrNull() ?: 0.0,
                                        allBets         = allBets,
                                        allVouchers     = allVouchers,
                                        allCustomers    = allCustomers
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            enabled = winningNumber.length == 3
                        ) {
                            Icon(Icons.Default.Calculate, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("တွက်ချက်မည်", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ── Results summary ────────────────────────────────────────────────
            if (results.isNotEmpty()) {
                item {
                    ResultsSummaryBar(results)
                }

                // Group by customer
                val grouped = results.groupBy { it.customerName }
                grouped.forEach { (customer, rows) ->
                    item {
                        CustomerWinCard(customer, rows)
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Calculation logic ─────────────────────────────────────────────────────────

private fun calculate(
    winningNumber: String,
    targetBatchInt: Int,
    exactMult: Double,
    permMult: Double,
    nearMult: Double,
    allBets: List<com.threeDLedger.data.Bet>,
    allVouchers: List<com.threeDLedger.data.VoucherWithCustomer>,
    allCustomers: List<com.threeDLedger.data.Customer>
): List<WinnerResult> {
    val perms  = com.threeDLedger.logic.NumberGenerator.permutations(winningNumber).toSet()
    val numInt = winningNumber.toIntOrNull() ?: 0
    val minus1 = String.format("%03d", if (numInt == 0) 999 else numInt - 1)
    val plus1  = String.format("%03d", if (numInt == 999) 0 else numInt + 1)
    val near   = setOf(minus1, plus1)

    return allBets.mapNotNull { bet ->
        val (winAmount, winType) = when {
            bet.number == winningNumber          -> Pair(bet.amount * exactMult, WinType.EXACT)
            perms.contains(bet.number)           -> Pair(bet.amount * permMult,  WinType.PERMUTATION)
            near.contains(bet.number)            -> Pair(bet.amount * nearMult,  WinType.NEAR)
            else                                  -> Pair(0.0, WinType.EXACT)
        }
        if (winAmount <= 0) return@mapNotNull null

        val voucher  = allVouchers.find { it.voucher.id == bet.voucherId } ?: return@mapNotNull null
        val customer = allCustomers.find { it.id == voucher.customer.id } ?: return@mapNotNull null
        if (voucher.voucher.batchNumber != targetBatchInt) return@mapNotNull null

        WinnerResult(customer.name, bet.number, bet.amount, winAmount, winType)
    }.sortedWith(compareBy({ it.customerName }, { it.winType.ordinal }))
}

// ── Firebase fetch ─────────────────────────────────────────────────────────────

private suspend fun fetchWinningNumber(
    onStart: () -> Unit,
    onResult: (String?, Boolean, String) -> Unit,
    onError: () -> Unit
) {
    onStart()
    try {
        com.google.firebase.database.FirebaseDatabase.getInstance()
            .getReference("3d_live_results")
            .get()
            .addOnSuccessListener { snapshot ->
                val num     = snapshot.child("winning_number").getValue(String::class.java)
                val isFinal = snapshot.child("is_final").getValue(Boolean::class.java) ?: false
                val session = snapshot.child("result_time").getValue(String::class.java) ?: ""
                onResult(num, isFinal, session)
            }
            .addOnFailureListener { onError() }
    } catch (e: Exception) {
        e.printStackTrace()
        onError()
    }
}

// ── Helper composables ────────────────────────────────────────────────────────

@Composable
private fun MultiplierField(label: String, value: String, accentColor: Color, modifier: Modifier, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 10.sp) },
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = accentColor,
            focusedLabelColor  = accentColor
        )
    )
}

@Composable
private fun ResultsSummaryBar(results: List<WinnerResult>) {
    val totalPayout  = results.sumOf { it.payoutAmount }
    val exactCount   = results.count { it.winType == WinType.EXACT }
    val permCount    = results.count { it.winType == WinType.PERMUTATION }
    val nearCount    = results.count { it.winType == WinType.NEAR }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("ရလဒ် အနှစ်ချုပ်", fontWeight = FontWeight.Bold, fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                SummaryChip("တိုက်ရိုက်", "$exactCount ကြိမ်", Color(0xFF43AA8B))
                SummaryChip("တွတ်",       "$permCount ကြိမ်", Color(0xFF6C63FF))
                SummaryChip("အနီး",       "$nearCount ကြိမ်", Color(0xFF4ECDC4))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("ပေးရမည့် ငွေစုစုပေါင်း", fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                Text("%,.0f Ks".format(totalPayout), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun SummaryChip(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = color)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

@Composable
private fun CustomerWinCard(customerName: String, rows: List<WinnerResult>) {
    val totalPayout = rows.sumOf { it.payoutAmount }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Customer header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(customerName, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer) {
                    Text("%,.0f Ks".format(totalPayout),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        fontWeight = FontWeight.Bold, fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(Modifier.height(8.dp))

            // Each bet row
            rows.forEach { result ->
                BetResultRow(result)
                if (result != rows.last()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                }
            }
        }
    }
}

@Composable
private fun BetResultRow(result: WinnerResult) {
    val (typeBadgeColor, typeLabel) = when (result.winType) {
        WinType.EXACT       -> Pair(Color(0xFF43AA8B), "တိုက်ရိုက်")
        WinType.PERMUTATION -> Pair(Color(0xFF6C63FF), "တွတ်")
        WinType.NEAR        -> Pair(Color(0xFF4ECDC4), "အနီး")
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Number badge
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(result.betNumber, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            // Type tag
            Surface(shape = RoundedCornerShape(6.dp), color = typeBadgeColor.copy(alpha = 0.15f)) {
                Text(typeLabel,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = typeBadgeColor)
            }
            Spacer(Modifier.height(2.dp))
            Text("ထိုးငွေ: %,d Ks".format(result.betAmount), fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Text("%,.0f Ks".format(result.payoutAmount), fontWeight = FontWeight.Bold, fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary)
    }
}
