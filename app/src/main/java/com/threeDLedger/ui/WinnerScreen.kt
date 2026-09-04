package com.threeDLedger.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

// ── Types ─────────────────────────────────────────────────────────────────────

enum class WinType { EXACT, PERMUTATION, NEAR }

data class WinnerResult(
    val customerName: String,
    val customerId: Int,
    val voucherId: Int,
    val betNumber: String,
    val betAmount: Int,
    val payoutAmount: Double,
    val winType: WinType
)

data class VoucherWinSummary(
    val voucherId: Int,
    val customerName: String,
    val customerId: Int,
    val bets: List<WinnerResult>,
    val totalPayout: Double
)

data class AgentWinSummary(
    val customerName: String,
    val customerId: Int,
    val vouchers: List<VoucherWinSummary>,
    val exactCount: Int,
    val permCount: Int,
    val nearCount: Int,
    val totalPayout: Double
)

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinnerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var winningNumber  by remember { mutableStateOf("") }
    val currentBatch   = viewModel.currentBatch.collectAsStateWithLifecycle().value
    var targetBatch    by remember { mutableStateOf(currentBatch.toString()) }
    var exactMult      by remember { mutableStateOf("600") }
    var permMult       by remember { mutableStateOf("100") }
    var nearMult       by remember { mutableStateOf("10") }
    var isFetching     by remember { mutableStateOf(false) }
    var fetchStatus    by remember { mutableStateOf("") }
    var isFinalResult  by remember { mutableStateOf(false) }
    var resultSession  by remember { mutableStateOf("") }
    var selectedTab    by remember { mutableIntStateOf(0) }  // 0=Agent, 1=Voucher

    val allBets      by viewModel.allBets.collectAsStateWithLifecycle()
    val allVWB       by viewModel.vouchersWithBets.collectAsStateWithLifecycle()
    val allCustomers by viewModel.customers.collectAsStateWithLifecycle()
    var results      by remember { mutableStateOf<List<WinnerResult>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    fun runCalc() {
        if (winningNumber.length != 3) return
        val batchInt = targetBatch.toIntOrNull() ?: currentBatch
        val perms    = com.threeDLedger.logic.NumberGenerator.permutations(winningNumber).toSet()
        val numInt   = winningNumber.toIntOrNull() ?: 0
        val minus1   = String.format("%03d", if (numInt == 0) 999 else numInt - 1)
        val plus1    = String.format("%03d", if (numInt == 999) 0 else numInt + 1)
        val near     = setOf(minus1, plus1)
        val eM = exactMult.toDoubleOrNull() ?: 0.0
        val pM = permMult.toDoubleOrNull()  ?: 0.0
        val nM = nearMult.toDoubleOrNull()  ?: 0.0

        results = allBets.mapNotNull { bet ->
            // Use allVWB (direct customerId field) to avoid @Relation null crash
            val vWB = allVWB.find { it.voucher.id == bet.voucherId } ?: return@mapNotNull null
            if (vWB.voucher.batchNumber != batchInt) return@mapNotNull null
            val customer = allCustomers.find { it.id == vWB.voucher.customerId } ?: return@mapNotNull null
            val (win, wt) = when {
                bet.number == winningNumber  -> Pair(bet.amount * eM, WinType.EXACT)
                perms.contains(bet.number)   -> Pair(bet.amount * pM, WinType.PERMUTATION)
                near.contains(bet.number)    -> Pair(bet.amount * nM, WinType.NEAR)
                else                          -> Pair(0.0, WinType.EXACT)
            }
            if (win <= 0) return@mapNotNull null
            WinnerResult(customer.name, customer.id, bet.voucherId, bet.number, bet.amount, win, wt)
        }.sortedWith(compareBy({ it.customerName }, { it.voucherId }))

        // Share the winning number with LedgerScreen (Numbers page)
        viewModel.saveWinningNumber(winningNumber)
    }

    // Auto-fetch on open — fills the number field only, NO calculation
    LaunchedEffect(Unit) {
        fetchWinningNumber(
            onStart  = { isFetching = true; fetchStatus = "checking" },
            onResult = { num, isFinal, session ->
                if (!num.isNullOrEmpty()) winningNumber = num   // fill field, don't calc
                isFinalResult = isFinal; resultSession = session
                fetchStatus = if (num.isNullOrEmpty()) "error" else "ok"
                isFetching = false
            },
            onError = { isFetching = false; fetchStatus = "error" }
        )
    }
    // Calculation only happens when user explicitly taps ပေါက်သီ ကြောညာသည်။

    // Derived grouped data
    val agentSummaries: List<AgentWinSummary> = remember(results) {
        results.groupBy { it.customerId }.map { (cid, rows) ->
            val voucherGroups = rows.groupBy { it.voucherId }.map { (vid, vRows) ->
                VoucherWinSummary(vid, vRows.first().customerName, cid, vRows, vRows.sumOf { it.payoutAmount })
            }.sortedBy { it.voucherId }
            AgentWinSummary(
                customerName = rows.first().customerName,
                customerId   = cid,
                vouchers     = voucherGroups,
                exactCount   = rows.count { it.winType == WinType.EXACT },
                permCount    = rows.count { it.winType == WinType.PERMUTATION },
                nearCount    = rows.count { it.winType == WinType.NEAR },
                totalPayout  = rows.sumOf { it.payoutAmount }
            )
        }.sortedByDescending { it.totalPayout }
    }

    val grandTotal = results.sumOf { it.payoutAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ပေါက်ဂဏန်း စာရင်း", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        if (results.isNotEmpty())
                            Text("${results.size} ကြိမ် ပေါက် — %,.0f Ks".format(grandTotal),
                                fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
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
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── Input card ─────────────────────────────────────────────────────
            item { InputCard(
                targetBatch = targetBatch, onBatchChange = { targetBatch = it },
                winningNumber = winningNumber, onNumberChange = { v -> if (v.length <= 3 && v.all { it.isDigit() }) { winningNumber = v; fetchStatus = "" } },
                exactMult = exactMult, onExactChange = { exactMult = it },
                permMult  = permMult,  onPermChange  = { permMult  = it },
                nearMult  = nearMult,  onNearChange  = { nearMult  = it },
                fetchStatus = fetchStatus, isFinalResult = isFinalResult, resultSession = resultSession, isFetching = isFetching,
                onFetch = { coroutineScope.launch { fetchWinningNumber(
                    onStart  = { isFetching = true; fetchStatus = "checking" },
                    onResult = { num, isFinal, session ->
                        if (!num.isNullOrEmpty()) winningNumber = num
                        isFinalResult = isFinal; resultSession = session
                        fetchStatus = if (num.isNullOrEmpty()) "error" else "ok"
                        isFetching = false
                    },
                    onError = { isFetching = false; fetchStatus = "error" }
                )}},
                onRecalc = { runCalc() }
            )}

            // ── Clear / Reset button — visible once winning number is set ─────
            if (winningNumber.length == 3) {
                item {
                    OutlinedButton(
                        onClick = {
                            viewModel.saveWinningNumber("")   // clears for ALL pages
                            winningNumber = ""
                            results = emptyList()
                            fetchStatus = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.5.dp, MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "ပေါက်ဂဏန်း ဖျက်မည် — ထိုးဆက်နိုင်မည်",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ── No results placeholder ─────────────────────────────────────────
            if (winningNumber.length == 3 && results.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.EmojiEvents, null, modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text("ဤ batch တွင် ပေါက်မှု မရှိပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("(Batch ${targetBatch}, 3D: $winningNumber)", fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (results.isNotEmpty()) {
                // ── Tab bar ────────────────────────────────────────────────────
                item {
                    TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            text = { Text("ကိုယ်စားလှယ် (${agentSummaries.size})", fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.Group, null, Modifier.size(16.dp)) }
                        )
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            text = { Text("ဘောင်ချာ (${agentSummaries.sumOf { it.vouchers.size }})", fontSize = 13.sp) },
                            icon = { Icon(Icons.Default.Receipt, null, Modifier.size(16.dp)) }
                        )
                    }
                }

                // ── Grand total bar ────────────────────────────────────────────
                item { GrandTotalBar(results, grandTotal) }

                if (selectedTab == 0) {
                    // ── Agent Summary ──────────────────────────────────────────
                    items(agentSummaries) { agent ->
                        AgentSummaryCard(agent)
                    }
                } else {
                    // ── Voucher Detail (all vouchers across agents) ────────────
                    val allVouchers2 = agentSummaries.flatMap { it.vouchers }
                    items(allVouchers2) { vs ->
                        VoucherDetailCard(vs, winningNumber)
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Input card ────────────────────────────────────────────────────────────────

@Composable
private fun InputCard(
    targetBatch: String, onBatchChange: (String) -> Unit,
    winningNumber: String, onNumberChange: (String) -> Unit,
    exactMult: String, onExactChange: (String) -> Unit,
    permMult: String,  onPermChange:  (String) -> Unit,
    nearMult: String,  onNearChange:  (String) -> Unit,
    fetchStatus: String, isFinalResult: Boolean, resultSession: String, isFetching: Boolean,
    onFetch: () -> Unit, onRecalc: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Batch
            OutlinedTextField(value = targetBatch, onValueChange = onBatchChange,
                label = { Text("Batch No / အကြိမ်") },
                leadingIcon = { Icon(Icons.Default.Numbers, null) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)

            // Winning number row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(value = winningNumber, onValueChange = onNumberChange,
                        label = { Text("ပေါက်ဂဏန်း (3D)") },
                        leadingIcon = { Icon(Icons.Default.Star, null, tint = Color(0xFFFFD93D)) },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)
                    if (fetchStatus.isNotEmpty()) {
                        Row(Modifier.padding(top = 3.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            val dotColor = when(fetchStatus) {
                                "ok"    -> if (isFinalResult) Color(0xFF43AA8B) else Color(0xFFFFD93D)
                                "error" -> Color(0xFFE63946)
                                else    -> Color.Gray
                            }
                            Box(Modifier.size(7.dp).clip(CircleShape).background(dotColor))
                            Spacer(Modifier.width(5.dp))
                            Text(when(fetchStatus) {
                                "checking" -> "Firebase မှ ယူနေသည်..."
                                "ok"       -> if (isFinalResult) "✓ Final ($resultSession)" else "⏳ Interim ($resultSession)"
                                else       -> "Firebase ချိတ်မရ — ကိုယ်တိုင် ရိုက်ထည့်ပါ"
                            }, fontSize = 11.sp, color = dotColor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Button(onClick = onFetch, enabled = !isFetching,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
                    if (isFetching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    else { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Fetch") }
                }
            }

            // Multipliers
            Text("အဆ (Multiplier)", fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MultiplierField("ပေါက်သီး", exactMult, Color(0xFF43AA8B), Modifier.weight(1f), onExactChange)
                MultiplierField("တွတ်",       permMult,  Color(0xFF6C63FF), Modifier.weight(1f), onPermChange)
                MultiplierField("တွတ်",       nearMult,  Color(0xFF4ECDC4), Modifier.weight(1f), onNearChange)
            }

            // ── Declare button ───────────────────────────────────────────────────
            Button(
                onClick = onRecalc,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = winningNumber.length == 3,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F),   // strong red — declaration is final
                    contentColor   = Color.White
                )
            ) {
                Icon(Icons.Default.Star, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "ပေါက်သီး ကြေညာသည်",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

// ── Grand total bar ───────────────────────────────────────────────────────────

@Composable
private fun GrandTotalBar(results: List<WinnerResult>, grandTotal: Double) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(4.dp)) {
        Row(modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            WinChip("ပေါက်သီး", "${results.count { it.winType == WinType.EXACT }}", Color(0xFF43AA8B))
            WinChip("တွတ်",       "${results.count { it.winType == WinType.PERMUTATION }}", Color(0xFF6C63FF))
            WinChip("တွတ်",       "${results.count { it.winType == WinType.NEAR }}", Color(0xFF4ECDC4))
            Column(horizontalAlignment = Alignment.End) {
                Text("ပေးရမည့် စုစုပေါင်း", fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                Text("%,.0f Ks".format(grandTotal), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

@Composable
private fun WinChip(label: String, count: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

// ── Agent summary card ────────────────────────────────────────────────────────

@Composable
private fun AgentSummaryCard(agent: AgentWinSummary) {
    var expanded by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Column {
            // Header row — tap to expand
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                // Avatar
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(agent.customerName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (agent.exactCount > 0) WinTypeBadge("ပေါက်သီး ×${agent.exactCount}", Color(0xFF43AA8B))
                        if (agent.permCount  > 0) WinTypeBadge("တွတ် ×${agent.permCount}",       Color(0xFF6C63FF))
                        if (agent.nearCount  > 0) WinTypeBadge("တွတ် ×${agent.nearCount}",       Color(0xFF4ECDC4))
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%,.0f Ks".format(agent.totalPayout), fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp, fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary)
                    Text("${agent.vouchers.size} ဘောင်ချာ", fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // Expanded voucher list
            AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(horizontal = 14.dp).padding(bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    agent.vouchers.forEach { vs -> VoucherDetailCard(vs, "", compact = true) }
                }
            }
        }
    }
}

// ── Voucher detail card ───────────────────────────────────────────────────────

@Composable
fun VoucherDetailCard(vs: VoucherWinSummary, winningNumber: String, compact: Boolean = false) {
    val bgColor = if (compact) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                  else MaterialTheme.colorScheme.surface
    Surface(shape = RoundedCornerShape(12.dp), color = bgColor,
        tonalElevation = if (compact) 0.dp else 2.dp,
        modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Voucher header
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center) {
                        Text("${vs.voucherId}", color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!compact)
                        Text(vs.customerName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Text("%,.0f Ks".format(vs.totalPayout), fontWeight = FontWeight.Bold,
                    fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(end = 8.dp))
            }
            Spacer(Modifier.height(8.dp))
            // Bet rows
            vs.bets.forEach { r -> BetResultRow(r) }
        }
    }
}

// ── Bet result row ────────────────────────────────────────────────────────────

@Composable
private fun BetResultRow(result: WinnerResult) {
    val (color, label) = when (result.winType) {
        WinType.EXACT       -> Pair(Color(0xFF43AA8B), "ပေါက်သီး")
        WinType.PERMUTATION -> Pair(Color(0xFF6C63FF), "တွတ်")
        WinType.NEAR        -> Pair(Color(0xFF4ECDC4), "တွတ်")
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically) {
        // Number badge
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center) {
            Text(result.betNumber, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp,
                fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(8.dp))
        WinTypeBadge(label, color)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("%,d Ks".format(result.betAmount), fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontFamily = FontFamily.Monospace)
            Text("→ %,.0f Ks".format(result.payoutAmount), fontWeight = FontWeight.Bold,
                fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = color)
        }
    }
}

// ── Small helpers ─────────────────────────────────────────────────────────────

@Composable
private fun WinTypeBadge(label: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f)) {
        Text(label, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@Composable
private fun MultiplierField(label: String, value: String, accent: Color, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label, fontSize = 10.sp) },
        modifier = modifier, shape = RoundedCornerShape(12.dp), singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent))
}

// ── Thai Lottery API fetch (GLO official primary → Rayriffy fallback) ────────
//
// 3D winning number = LAST 3 DIGITS of the Thai lottery first prize (6-digit number)
// Draws are held on the 1st and 16th of each month at ~16:00 ICT.
//
// Primary : https://www.glo.or.th/api/lottery/getLatestLottery  (POST, official GLO)
// Fallback: https://lotto.api.rayriffy.com/latest               (GET,  community)

private suspend fun fetchWinningNumber(
    onStart:  () -> Unit,
    onResult: (String?, Boolean, String) -> Unit,
    onError:  () -> Unit
) {
    onStart()  // called on Main dispatcher (LaunchedEffect is on Main)

    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        tryGloApi() ?: tryRayriffyApi()
    }

    if (result != null) {
        val (num3d, date) = result
        // Both APIs publish only after results are finalized — isFinal always true
        onResult(num3d, true, date)
    } else {
        onError()
    }
}

/** GLO official API — returns (last3digits, drawDate) or null on any failure */
private fun tryGloApi(): Pair<String, String>? {
    return try {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val mediaType   = okhttp3.MediaType.parse("application/json; charset=utf-8")
        val requestBody = okhttp3.RequestBody.create(mediaType, "{}".toByteArray())

        val request = okhttp3.Request.Builder()
            .url("https://www.glo.or.th/api/lottery/getLatestLottery")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val raw = response.body()?.string() ?: return null

        val json = org.json.JSONObject(raw)
        val resp = json.optJSONObject("response") ?: return null

        // Draw date — may be under "period.date" or directly "date"
        val date = resp.optJSONObject("period")?.optString("date", "")
            ?: resp.optString("date", "")

        // Prizes array — find prize with id "1" or name containing "ที่ 1"
        val prizes = resp.optJSONArray("prizes") ?: return null
        var firstPrizeNum: String? = null
        for (i in 0 until prizes.length()) {
            val p    = prizes.getJSONObject(i)
            val id   = p.optString("id", "")
            val name = p.optString("name", "")
            if (id == "1" || id == "first" || name.contains("ที่ 1")) {
                // Use "" as fallback (never null) — @NonNull parameter
                val numArr = p.optJSONArray("number")
                firstPrizeNum = if (numArr != null && numArr.length() > 0) {
                    numArr.optString(0, "").takeIf { it.isNotEmpty() }
                } else {
                    p.optString("number", "").takeIf { it.isNotEmpty() }
                }
                break
            }
        }

        val full = firstPrizeNum ?: return null
        if (full.length < 3) return null
        Pair(full.takeLast(3), date)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

/** Rayriffy community API — returns (last3digits, drawDate) or null on any failure */
private fun tryRayriffyApi(): Pair<String, String>? {
    return try {
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(8, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        val request = okhttp3.Request.Builder()
            .url("https://lotto.api.rayriffy.com/latest")
            .get()
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val raw = response.body()?.string() ?: return null

        val json = org.json.JSONObject(raw)
        if (json.optString("status", "") != "ok") return null

        val resp   = json.optJSONObject("response") ?: return null
        val date   = resp.optString("date", "")
        val prizes = resp.optJSONObject("prizes")   ?: return null
        val first  = prizes.optJSONObject("first")  ?: return null

        // "number" can be a JSONArray OR a bare String — use "" fallback (never null)
        val numArr = first.optJSONArray("number")
        val full = if (numArr != null && numArr.length() > 0) {
            numArr.optString(0, "")
        } else {
            first.optString("number", "")
        }

        if (full.length < 3) return null
        Pair(full.takeLast(3), date)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

