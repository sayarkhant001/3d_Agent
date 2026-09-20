package com.threeDLedger.ui

import com.threeDLedger.ui.theme.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// ── Types ─────────────────────────────────────────────────────────────────────

enum class WinType { EXACT, TUWT }

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
    val totalPayout: Double,
    val totalBetAmount: Int = 0
)

data class AgentWinSummary(
    val customerName: String,
    val customerId: Int,
    val vouchers: List<VoucherWinSummary>,
    val exactCount: Int,
    val tuwtCount: Int,
    val totalPayout: Double,
    val totalBet: Int = 0
)

data class OverflowWinResult(
    val exportRecordId: Int,
    val type: String,
    val number: String,
    val amount: Int,
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
    var winningNumber  by remember { mutableStateOf("") }
    val currentBatch   = viewModel.currentBatch.collectAsStateWithLifecycle().value
    var targetBatch    by remember { mutableStateOf(currentBatch.toString()) }
    var exactMult      by remember { mutableStateOf("600") }
    var tuwtMult       by remember { mutableStateOf("100") }
    var isFetching     by remember { mutableStateOf(false) }
    var fetchStatus    by remember { mutableStateOf("") }
    var isFinalResult  by remember { mutableStateOf(false) }
    var resultSession  by remember { mutableStateOf("") }
    var selectedTab    by remember { mutableIntStateOf(0) }  // 0=Agent, 1=Voucher, 2=Overflow

    val allBets          by viewModel.allBets.collectAsStateWithLifecycle()
    val allVWB           by viewModel.vouchersWithBets.collectAsStateWithLifecycle()
    val allCustomers     by viewModel.customers.collectAsStateWithLifecycle()
    val allExportRecords by viewModel.allExportRecords.collectAsStateWithLifecycle()
    var results          by remember { mutableStateOf<List<WinnerResult>>(emptyList()) }
    var overflowResults  by remember { mutableStateOf<List<OverflowWinResult>>(emptyList()) }
    val coroutineScope   = rememberCoroutineScope()

    fun runCalc() {
        if (winningNumber.length != 3) return
        val batchInt = targetBatch.toIntOrNull() ?: currentBatch
        val allPerms = com.threeDLedger.logic.NumberGenerator.permutations(winningNumber).toSet() - setOf(winningNumber)
        val numInt   = winningNumber.toIntOrNull() ?: 0
        val minus1   = String.format("%03d", if (numInt == 0) 999 else numInt - 1)
        val plus1    = String.format("%03d", if (numInt == 999) 0 else numInt + 1)
        val near     = setOf(minus1, plus1) - setOf(winningNumber)
        val tuwtSet  = allPerms + near
        val eM = exactMult.toDoubleOrNull() ?: 0.0
        val tM = tuwtMult.toDoubleOrNull()  ?: 0.0

        // Lower-agent winnings (strictly excluding overflow vouchers and overflow customers)
        results = allBets.mapNotNull { bet ->
            val vWB = allVWB.find { it.voucher.id == bet.voucherId } ?: return@mapNotNull null
            if (vWB.voucher.batchNumber != batchInt) return@mapNotNull null
            val customer = allCustomers.find { it.id == vWB.voucher.customerId } ?: return@mapNotNull null
            if (customer.name.contains("တင်ကွက်") || customer.name.contains("overflow", ignoreCase = true) ||
                customer.name.contains("upper", ignoreCase = true) || customer.name.contains("အထက်ဒိုင်") ||
                vWB.voucher.remark.contains("တင်ကွက်") || vWB.voucher.remark.contains("overflow", ignoreCase = true) ||
                vWB.voucher.remark.contains("upper", ignoreCase = true) || vWB.voucher.remark.contains("အထက်ဒိုင်")) {
                return@mapNotNull null
            }
            val (win, wt) = when {
                bet.number == winningNumber -> Pair(bet.amount * eM, WinType.EXACT)
                bet.number in tuwtSet       -> Pair(bet.amount * tM, WinType.TUWT)
                else                        -> Pair(0.0, WinType.EXACT)
            }
            if (win <= 0) return@mapNotNull null
            WinnerResult(customer.name, customer.id, bet.voucherId, bet.number, bet.amount, win, wt)
        }.sortedWith(compareBy({ it.customerName }, { it.voucherId }))

        // Upper-agent / Overflow winnings (winnings recovered from upper bookmaker)
        overflowResults = allExportRecords
            .filter { it.record.batchNumber == batchInt }
            .flatMap { exp ->
                exp.numbers.mapNotNull { en ->
                    val (win, wt) = when {
                        en.number == winningNumber -> Pair(en.amount * eM, WinType.EXACT)
                        en.number in tuwtSet       -> Pair(en.amount * tM, WinType.TUWT)
                        else                       -> Pair(0.0, WinType.EXACT)
                    }
                    if (win <= 0) return@mapNotNull null
                    OverflowWinResult(exp.record.id, exp.record.type, en.number, en.amount, win, wt)
                }
            }

        // Persist winning number and multipliers for this batch
        viewModel.saveWinningNumber(winningNumber)
        viewModel.saveMultipliers(eM, tM, tM)
    }

    // Auto-fetch on open — loads saved winning number if available, or fetches from live API
    LaunchedEffect(Unit) {
        val saved = viewModel.winningNumber.value
        if (saved.length == 3) {
            winningNumber = saved
            runCalc()
        } else {
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
    }
    // Calculation only happens when user explicitly taps ပေါက်သီး ကြေညာသည် or auto-restored

    // Derived grouped data — include ALL commissioners who placed bets in this batch
    // Derived grouped data — include ALL commissioners whether they won or not
    val batchInt = targetBatch.toIntOrNull() ?: currentBatch
    val eligibleCustomers = remember(allCustomers) {
        allCustomers.filter {
            !it.name.contains("တင်ကွက်") && !it.name.contains("overflow", ignoreCase = true) &&
            !it.name.contains("upper", ignoreCase = true) && !it.name.contains("အထက်ဒိုင်")
        }
    }
    val batchCustomerVouchers = remember(allVWB, batchInt, eligibleCustomers) {
        val validIds = eligibleCustomers.map { it.id }.toSet()
        allVWB.filter { vwb ->
            vwb.voucher.batchNumber == batchInt &&
            validIds.contains(vwb.voucher.customerId) &&
            !vwb.voucher.remark.contains("တင်ကွက်") &&
            !vwb.voucher.remark.contains("overflow", ignoreCase = true) &&
            !vwb.voucher.remark.contains("upper", ignoreCase = true) &&
            !vwb.voucher.remark.contains("အထက်ဒိုင်")
        }
    }

    val agentSummaries: List<AgentWinSummary> = remember(results, batchCustomerVouchers, winningNumber, eligibleCustomers) {
        if (winningNumber.length != 3) {
            emptyList()
        } else {
            eligibleCustomers.map { cust ->
                val vwbList = batchCustomerVouchers.filter { it.voucher.customerId == cust.id }
                val voucherSummaries = vwbList.map { vwb ->
                    val vWinningBets = results.filter { it.voucherId == vwb.voucher.id }
                    val vPayout = vWinningBets.sumOf { it.payoutAmount }
                    val vTotalBet = vwb.bets.sumOf { it.amount }
                    VoucherWinSummary(
                        voucherId = vwb.voucher.id,
                        customerName = cust.name,
                        customerId = cust.id,
                        bets = vWinningBets,
                        totalPayout = vPayout,
                        totalBetAmount = vTotalBet
                    )
                }.sortedWith(
                    compareByDescending<VoucherWinSummary> { it.totalPayout }
                        .thenByDescending { it.totalBetAmount }
                        .thenBy { it.voucherId }
                )

                val totalPayout = voucherSummaries.sumOf { it.totalPayout }
                val exactCount = voucherSummaries.sumOf { v -> v.bets.count { it.winType == WinType.EXACT } }
                val tuwtCount = voucherSummaries.sumOf { v -> v.bets.count { it.winType == WinType.TUWT } }
                val totalBet = voucherSummaries.sumOf { it.totalBetAmount }

                AgentWinSummary(
                    customerName = cust.name,
                    customerId = cust.id,
                    vouchers = voucherSummaries,
                    exactCount = exactCount,
                    tuwtCount = tuwtCount,
                    totalPayout = totalPayout,
                    totalBet = totalBet
                )
            }.sortedWith(
                compareByDescending<AgentWinSummary> { it.totalPayout }
                    .thenByDescending { it.totalBet }
                    .thenBy { it.customerName }
            )
        }
    }

    val allVouchers2 = remember(agentSummaries) {
        agentSummaries.flatMap { it.vouchers }
            .sortedWith(
                compareByDescending<VoucherWinSummary> { it.totalPayout }
                    .thenByDescending { it.totalBetAmount }
                    .thenBy { it.voucherId }
            )
    }

    val grandTotal = results.sumOf { it.payoutAmount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ပေါက်ဂဏန်း စာရင်း", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        if (winningNumber.length == 3) {
                            if (results.isNotEmpty()) {
                                Text("${results.size} ကြိမ် ပေါက် — %,.0f Ks".format(grandTotal),
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                            } else {
                                Text("ပေါက်သူ မရှိပါ (အကြိမ်: $targetBatch)",
                                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                            }
                        }
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
                tuwtMult  = tuwtMult,  onTuwtChange  = { tuwtMult  = it },
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
                            "ဖျက်မည်။ ဆက်ထိုးနိုင်သည်။",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            val overflowWonTotal = overflowResults.sumOf { it.payoutAmount }

            // ── Grand total bar & Tabs (Visible whenever winningNumber has 3 digits) ────
            if (winningNumber.length == 3) {
                // If no one won anything, show informational banner
                if (results.isEmpty() && overflowResults.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Info, null, modifier = Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text("ဤ အကြိမ်တွင် ပေါက်သီး မရှိပါ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("(အကြိမ်: ${targetBatch}, ပေါက်ဂဏန်း: $winningNumber)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }

                // ── Tab bar ────────────────────────────────────────────────────
                item {
                    TabRow(selectedTabIndex = selectedTab, containerColor = MaterialTheme.colorScheme.surface) {
                        Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                            text = { Text("ကိုယ်စားလှယ် (${agentSummaries.size})", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Group, null, Modifier.size(16.dp)) }
                        )
                        Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                            text = { Text("ဘောင်ချာ (${agentSummaries.sumOf { it.vouchers.size }})", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Receipt, null, Modifier.size(16.dp)) }
                        )
                        Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                            text = { Text("တင်ကွက် (${overflowResults.size})", fontSize = 12.sp) },
                            icon = { Icon(Icons.Default.Payment, null, Modifier.size(16.dp)) }
                        )
                    }
                }

                // ── Grand total bar ────────────────────────────────────────────
                item { GrandTotalBar(results, grandTotal, overflowResults, overflowWonTotal) }

                when (selectedTab) {
                    0 -> {
                        // ── Agent Summary ──────────────────────────────────────────
                        if (agentSummaries.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("ဤ အကြိမ်တွင် ကိုယ်စားလှယ် မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(agentSummaries) { agent ->
                                AgentSummaryCard(agent)
                            }
                        }
                    }
                    1 -> {
                        if (allVouchers2.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("ဤ အကြိမ်တွင် ဘောင်ချာ မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            items(allVouchers2) { vs ->
                                VoucherDetailCard(vs, winningNumber)
                            }
                        }
                    }
                    2 -> {
                        // ── Overflow Upper-Agent Detail ────────────────────────────
                        if (overflowResults.isEmpty()) {
                            val batchOverflowCount = allExportRecords.count { it.record.batchNumber == (targetBatch.toIntOrNull() ?: currentBatch) }
                            item {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        if (batchOverflowCount == 0) "တင်ကွက် မှတ်တမ်း မရှိပါ"
                                        else "တင်ကွက်တွင် ပေါက်ဂဏန်း မရှိပါ ($batchOverflowCount တင်ကွက်)",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(overflowResults) { ov ->
                                OverflowWinCard(ov)
                            }
                        }
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
    tuwtMult: String,  onTuwtChange:  (String) -> Unit,
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
                label = { Text("အကြိမ်") },
                leadingIcon = { Icon(Icons.Default.Numbers, null) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true)

            // Winning number row
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    OutlinedTextField(value = winningNumber, onValueChange = onNumberChange,
                        label = { Text("ပေါက်ဂဏန်း") },
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
                                "checking" -> "ရလဒ် စစ်ဆေးနေပါသည်..."
                                "ok"       -> if (isFinalResult) "✓ အတည် ($resultSession)" else "⏳ စောင့်ဆိုင်းဆဲ ($resultSession)"
                                else       -> "ချိတ်ဆက်မရပါ — ကိုယ်တိုင် ရိုက်ထည့်ပါ"
                            }, fontSize = 11.sp, color = dotColor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Button(onClick = onFetch, enabled = !isFetching,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
                    if (isFetching) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary)
                    else { Icon(Icons.Default.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("ရယူမည်") }
                }
            }

            // Multipliers
            Text("အဆနှုန်းထားများ", fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MultiplierField("ပေါက်သီး (ဒဲ့)", exactMult, Color(0xFF43AA8B), Modifier.weight(1f), onExactChange)
                MultiplierField("တွတ်",       tuwtMult,  Color(0xFF6C63FF), Modifier.weight(1f), onTuwtChange)
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
private fun GrandTotalBar(
    results: List<WinnerResult>,
    grandTotal: Double,
    overflowResults: List<OverflowWinResult>,
    overflowWonTotal: Double
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                WinChip("ပေါက်သီး", "${results.count { it.winType == WinType.EXACT }}", Color(0xFF43AA8B))
                WinChip("တွတ်",       "${results.count { it.winType == WinType.TUWT }}", Color(0xFF6C63FF))
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = CardBorderSubtle, thickness = 0.5.dp)
            Spacer(Modifier.height(10.dp))

            // To Pay (Red)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("အောက်ဒိုင်သို့ လျော်ရန်", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "- %,.0f Ks".format(grandTotal),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFDC2626)
                )
            }

            // To Get from Upper Agent / Overflow (Green)
            if (overflowWonTotal > 0) {
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("အထက်ဒိုင်မှ ရရန် (တင်ကွက်)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "+ %,.0f Ks".format(overflowWonTotal),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF059669)
                    )
                }
            }

            // Net position
            val netBalance = overflowWonTotal - grandTotal
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val netLabel = if (netBalance >= 0) "အသားတင် ကျန်ငွေ (ရရန်)" else "အသားတင် ကျန်ငွေ (ပေးရန်)"
                val netColor = if (netBalance >= 0) Color(0xFF059669) else Color(0xFFDC2626)
                Text(netLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(
                    "%,.0f Ks".format(netBalance),
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    color = netColor
                )
            }
        }
    }
}

@Composable
private fun WinChip(label: String, count: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = color)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(agent.customerName, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (agent.exactCount > 0) WinTypeBadge("ဒဲ့ ×${agent.exactCount}", Color(0xFF43AA8B))
                        if (agent.tuwtCount  > 0) WinTypeBadge("တွတ် ×${agent.tuwtCount}", Color(0xFF6C63FF))
                        if (agent.exactCount == 0 && agent.tuwtCount == 0) {
                            Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Text(
                                    "မပေါက်ပါ",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (agent.totalPayout > 0) {
                        Text("%,.0f Ks".format(agent.totalPayout), fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp, fontFamily = FontFamily.Monospace,
                            color = Color(0xFFDC2626))  // Red for payout to give
                    } else {
                        Text("0 Ks", fontWeight = FontWeight.Bold,
                            fontSize = 15.sp, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        "${agent.vouchers.size} ဘောင်ချာ  •  ထိုးငွေ %,d Ks".format(agent.totalBet),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

// ── Overflow Upper-Agent Winning Card ─────────────────────────────────────────

@Composable
private fun OverflowWinCard(item: OverflowWinResult) {
    val (color, label) = when (item.winType) {
        WinType.EXACT -> Pair(Color(0xFF43AA8B), "ပေါက်သီး")
        WinType.TUWT  -> Pair(Color(0xFF6C63FF), "တွတ်")
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    item.number,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    fontFamily = FontFamily.Monospace,
                    color = color
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WinTypeBadge(label, color)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        item.type,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    "တင်ငွေ: %,d Ks".format(item.amount),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "အထက်ဒိုင်မှ ရရန်",
                    fontSize = 10.sp,
                    color = Color(0xFF059669),
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "+%,.0f Ks".format(item.payoutAmount),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF059669)  // Green for receive
                )
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center) {
                        Text("${vs.voucherId}", color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    if (!compact)
                        Text(vs.customerName, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (vs.totalPayout > 0) {
                        Text("%,.0f Ks".format(vs.totalPayout), fontWeight = FontWeight.Bold,
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.padding(end = 4.dp))
                    } else {
                        Text("0 Ks", fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp))
                    }
                    Text("ထိုးငွေ: %,d Ks".format(vs.totalBetAmount),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(8.dp))
            // Bet rows or no win notice
            if (vs.bets.isNotEmpty()) {
                vs.bets.forEach { r -> BetResultRow(r) }
            } else {
                Text(
                    "ပေါက်ဂဏန်း မရှိပါ",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
                )
            }
        }
    }
}

// ── Bet result row ────────────────────────────────────────────────────────────

@Composable
private fun BetResultRow(result: WinnerResult) {
    val (color, label) = when (result.winType) {
        WinType.EXACT -> Pair(Color(0xFF43AA8B), "ပေါက်သီး")
        WinType.TUWT  -> Pair(Color(0xFF6C63FF), "တွတ်")
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

    val result = withContext(Dispatchers.IO) {
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
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        val requestBody = "{}".toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url("https://www.glo.or.th/api/lottery/getLatestLottery")
            .post(requestBody)
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val raw = response.body?.string() ?: return null

        val json = JSONObject(raw)
        val resp = json.optJSONObject("response") ?: return null

        // Draw date
        val date = resp.optString("date", "").ifEmpty {
            resp.optJSONObject("period")?.optString("date", "") ?: ""
        }

        // Check official GLO modern structure: response.data.first.number[0].value
        var firstPrizeNum: String? = null
        val dataObj = resp.optJSONObject("data")
        val firstObj = dataObj?.optJSONObject("first")
        val numberArr = firstObj?.optJSONArray("number")
        if (numberArr != null && numberArr.length() > 0) {
            firstPrizeNum = numberArr.getJSONObject(0).optString("value", "").trim()
        }

        // If not found in data.first, check n3.straight3
        if (firstPrizeNum.isNullOrEmpty()) {
            val straight3 = resp.optJSONObject("n3")?.optJSONObject("straight3")?.optJSONArray("number")
            if (straight3 != null && straight3.length() > 0) {
                val s3Val = straight3.getJSONObject(0).optString("value", "").trim()
                if (s3Val.length == 3) {
                    return Pair(s3Val, date)
                }
            }
        }

        // Fallback: Check prizes array if present
        if (firstPrizeNum.isNullOrEmpty()) {
            val prizes = resp.optJSONArray("prizes")
            if (prizes != null) {
                for (i in 0 until prizes.length()) {
                    val p    = prizes.getJSONObject(i)
                    val id   = p.optString("id", "")
                    val name = p.optString("name", "")
                    if (id == "1" || id == "first" || name.contains("ที่ 1")) {
                        val numArr = p.optJSONArray("number")
                        firstPrizeNum = if (numArr != null && numArr.length() > 0) {
                            numArr.optString(0, "").takeIf { it.isNotEmpty() }
                        } else {
                            p.optString("number", "").takeIf { it.isNotEmpty() }
                        }
                        break
                    }
                }
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
        val client = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url("https://lotto.api.rayriffy.com/latest")
            .get()
            .addHeader("Accept", "application/json")
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) return null
        val raw = response.body?.string() ?: return null

        val json = JSONObject(raw)
        if (json.optString("status", "") != "ok") return null

        val resp   = json.optJSONObject("response") ?: return null
        val date   = resp.optString("date", "")
        val prizes = resp.optJSONObject("prizes")   ?: return null
        val first  = prizes.optJSONObject("first")  ?: return null

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

