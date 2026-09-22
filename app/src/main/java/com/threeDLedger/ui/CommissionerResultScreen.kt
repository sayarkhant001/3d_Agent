package com.threeDLedger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import com.threeDLedger.data.Customer
import com.threeDLedger.logic.NumberGenerator
import com.threeDLedger.ui.theme.*

// ── Palette (Harmonized Emerald-Gold) ──────────────────────────────────────────
private val ResPrimary     = EmeraldPrimary
private val ResDark        = EmeraldDark
private val ResMintBg      = EmeraldSoftBg
private val ResGold        = GoldAccent
private val ResGoldBg      = GoldContainer
private val ResRed         = WinExactRed
private val ResRedBg       = WinExactBg
private val ResGreen       = Color(0xFF059669)
private val ResGreenBg     = EmeraldLight

// ── Data model ─────────────────────────────────────────────────────────────────
data class TutWinDetail(
    val number: String,
    val amount: Long,
    val payout: Long
)

data class AgentSettlement(
    val customer    : Customer,
    val totalBet    : Long,
    val commission  : Long,
    val netAfterComm: Long,
    val exactBetAmt : Long,
    val exactPayout : Long,
    val tuwtBetAmt  : Long,
    val tuwtPayout  : Long,
    val totalPayout : Long,
    val balance     : Long,
    val paidAmount  : Long,
    val remaining   : Long,
    val tuwtDetails : List<TutWinDetail> = emptyList(),
    val exactDetails: List<TutWinDetail> = emptyList()
)

private fun fmt(n: Long) = "%,d".format(n)

private fun buildClipText(s: AgentSettlement, batch: Int, winNum: String): String =
    buildString {
        appendLine("========================")
        appendLine("   3D ကော်မရှင် ရလဒ်   ")
        appendLine("========================")
        appendLine("အကြိမ် = $batch ( $winNum )")
        appendLine("အမည် = ${s.customer.name}")
        appendLine("ရောင်းကြေး = ${fmt(s.totalBet)} Ks")
        appendLine("ကော်မရှင် = ${fmt(s.commission)} Ks")
        appendLine("နုတ်ပြီးငွေ = ${fmt(s.netAfterComm)} Ks")
        if (s.exactBetAmt > 0) appendLine("ဒဲ့ ထိုးငွေ = ${fmt(s.exactBetAmt)} Ks (လျော် = ${fmt(s.exactPayout)} Ks)")
        if (s.tuwtPayout  > 0) appendLine("တွတ် လျော်ငွေ = ${fmt(s.tuwtPayout)} Ks")
        appendLine("စုစုပေါင်း လျော်ငွေ = ${fmt(s.totalPayout)} Ks")
        appendLine("------------------------")
        val balTag = if (s.balance < 0) "(ပေးရန်)" else "(ရရန်)"
        val remTag = if (s.remaining < 0) "(ပေးရန်)" else "(ရရန်)"
        appendLine("ကျန်ငွေ $balTag = ${fmt(s.balance)} Ks")
        appendLine("ပေးငွေ = ${fmt(s.paidAmount)} Ks")
        appendLine("ကြွေးကျန် $remTag = ${fmt(s.remaining)} Ks")
        appendLine("========================")
    }

// ── Screen ─────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommissionerResultScreen(
    viewModel     : MainViewModel,
    batchNumber   : Int,
    onNavigateBack: () -> Unit
) {
    val context       = LocalContext.current
    val allCustomers  by viewModel.customers.collectAsStateWithLifecycle()
    val batchVouchers by viewModel
        .getVouchersWithBetsByBatch(batchNumber)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val winningNumber                   = remember(batchNumber) { viewModel.getWinningNumberForBatch(batchNumber) }
    val (exactMult, permMult, nearMult) = remember(batchNumber) { viewModel.getMultipliersForBatch(batchNumber) }

    // Local paid-amount map — updates immediately after edit
    var paidMap by remember { mutableStateOf(mapOf<Int, Double>()) }
    LaunchedEffect(allCustomers, batchNumber) {
        paidMap = allCustomers.associate { c -> c.id to viewModel.getPaidForBatch(c.id, batchNumber) }
    }

    var dialogAgent         by remember { mutableStateOf<AgentSettlement?>(null) }
    var editAgent           by remember { mutableStateOf<AgentSettlement?>(null) }
    var editPaidText        by remember { mutableStateOf("") }
    var tutBreakdownAgent   by remember { mutableStateOf<AgentSettlement?>(null) }
    var exactBreakdownAgent by remember { mutableStateOf<AgentSettlement?>(null) }

    // ── Compute settlements ──────────────────────────────────────────────────
    val settlements: List<AgentSettlement> = remember(
        allCustomers, batchVouchers, winningNumber, exactMult, permMult, nearMult, paidMap
    ) {
        if (winningNumber.length != 3) return@remember emptyList()
        val allPerms = NumberGenerator.permutations(winningNumber).toSet()
        val permsOnly = allPerms - setOf(winningNumber)
        val winInt   = winningNumber.toIntOrNull() ?: return@remember emptyList()
        val near     = setOf(
            String.format("%03d", if (winInt == 0) 999 else winInt - 1),
            String.format("%03d", if (winInt == 999) 0 else winInt + 1)
        ) - setOf(winningNumber)

        allCustomers.mapNotNull { customer ->
            if (customer.name.contains("တင်ကွက်") || customer.name.contains("overflow", ignoreCase = true) ||
                customer.name.contains("upper", ignoreCase = true) || customer.name.contains("အထက်ဒိုင်")) return@mapNotNull null
            val agentVouchers = batchVouchers.filter { 
                it.voucher.customerId == customer.id &&
                !it.voucher.remark.contains("တင်ကွက်") &&
                !it.voucher.remark.contains("overflow", ignoreCase = true) &&
                !it.voucher.remark.contains("upper", ignoreCase = true) &&
                !it.voucher.remark.contains("အထက်ဒိုင်")
            }
            val bets     = agentVouchers.flatMap { it.bets }
            val totalBet = bets.sumOf { it.amount }.toLong()

            // Commission calculation: commissionRate is decimal fraction (0.15 = 15%)
            val commission   = (totalBet * customer.commissionRate).toLong()
            val netAfterComm = totalBet - commission
            val exactBets    = bets.filter { it.number == winningNumber }
            val tuwtBets     = bets.filter { it.number in permsOnly || it.number in near }
            val exactBetAmt  = exactBets.sumOf { it.amount }.toLong()
            val exactPayout  = (exactBetAmt * exactMult).toLong()
            val tuwtBetAmt   = tuwtBets.sumOf { it.amount }.toLong()
            val tuwtPayout   = (tuwtBetAmt * permMult).toLong()
            val totalPayout  = exactPayout + tuwtPayout
            val balance      = netAfterComm - totalPayout
            val paid         = (paidMap[customer.id] ?: 0.0).toLong()
            val remaining    = if (balance >= 0) balance - paid else balance + paid

            val tuwtDetails = tuwtBets
                .groupBy { it.number }
                .map { (num, list) ->
                    val amt = list.sumOf { it.amount }.toLong()
                    TutWinDetail(num, amt, (amt * permMult).toLong())
                }
                .sortedWith(compareByDescending<TutWinDetail> { it.amount }.thenBy { it.number })

            val exactDetails = exactBets
                .groupBy { it.number }
                .map { (num, list) ->
                    val amt = list.sumOf { it.amount }.toLong()
                    TutWinDetail(num, amt, (amt * exactMult).toLong())
                }
                .sortedWith(compareByDescending<TutWinDetail> { it.amount }.thenBy { it.number })

            AgentSettlement(
                customer, totalBet, commission, netAfterComm,
                exactBetAmt, exactPayout, tuwtBetAmt, tuwtPayout,
                totalPayout, balance, paid, remaining,
                tuwtDetails, exactDetails
            )
        }.sortedWith(
            compareByDescending<AgentSettlement> { it.totalPayout }
                .thenByDescending { it.totalBet }
                .thenBy { it.customer.name }
        )
    }

    val grandTotal   = settlements.sumOf { it.totalBet }
    val grandComm    = settlements.sumOf { it.commission }
    val grandPayout  = settlements.sumOf { it.totalPayout }
    val grandBalance = settlements.sumOf { it.balance }

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("ကော်မရှင်ဆိုင်ရာ ရလဒ်", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("အကြိမ် $batchNumber ရှင်းတမ်း", color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f), fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = ResPrimary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(ResMintBg)
        ) {
            // Page header banner
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("အကြိမ်", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$batchNumber", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = ResPrimary)
                    }

                    if (winningNumber.length == 3) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ResRed,
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("ထွက်ဂဏန်း: ", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.95f))
                                Text(
                                    winningNumber,
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 22.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 2.sp
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "ပေါက်ဂဏန်း မကြေညာရသေးပါ",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            when {
                winningNumber.length != 3 ->
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Info, null, tint = ResPrimary.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("ပေါက်ဂဏန်း ကြေညာပြီးမှ ရှင်းတမ်း ကြည့်ရှုနိုင်ပါသည်", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                settlements.isEmpty() ->
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.FolderOpen, null, tint = ResPrimary.copy(alpha = 0.4f), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("အကြိမ် $batchNumber တွင် ထိုးမှု မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                    }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(settlements, key = { it.customer.id }) { s ->
                            AgentSettlementCard(
                                settlement  = s,
                                onTapDetail = {
                                    dialogAgent = s
                                    val text = buildClipText(s, batchNumber, winningNumber)
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("settlement", text))
                                },
                                onTapTut    = {
                                    tutBreakdownAgent = s
                                },
                                onTapExact  = {
                                    exactBreakdownAgent = s
                                },
                                onEditPaid  = {
                                    editAgent    = s
                                    editPaidText = if (s.paidAmount == 0L) "" else s.paidAmount.toString()
                                }
                            )
                        }
                    }
                }
            }

            // Footer totals with polished Emerald-Gold styling
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = ResPrimary,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FooterCol("စုပေါင်း", grandTotal)
                    Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(0.2f)))
                    FooterCol("ကော်မရှင်ခ", grandComm)
                    Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(0.2f)))
                    FooterCol("လျော်ငွေ", grandPayout)
                    Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(0.2f)))
                    FooterCol(
                        if (grandBalance < 0) "လက်ကျန်(ပေး)" else "လက်ကျန်(ရ)",
                        grandBalance,
                        if (grandBalance < 0) Color(0xFFFCA5A5) else Color(0xFF6EE7B7)
                    )
                }
            }
        }
    }

    // ── Detail dialog ──────────────────────────────────────────────────────────
    dialogAgent?.let { s ->
        AlertDialog(
            onDismissRequest = { dialogAgent = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(ResPrimary),
                        Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(s.customer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                        Text("အကြိမ် $batchNumber • ထွက်ဂဏန်း: $winningNumber", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DialogRow("3D အကြိမ်", "$batchNumber ( $winningNumber )")
                    DialogRow("ထိုးသူ အမည်", s.customer.name)
                    HorizontalDivider(color = CardBorderSubtle)
                    DialogRow("ရောင်းကြေး", fmt(s.totalBet))
                    DialogRow("ကော်မရှင်", fmt(s.commission))
                    DialogRow("နုတ်ပြီးငွေ", fmt(s.netAfterComm))
                    HorizontalDivider(color = CardBorderSubtle)
                    if (s.exactBetAmt > 0) DialogRow("ဒဲ့ (ပေါက်သီး)", fmt(s.exactBetAmt), ResRed)
                    if (s.tuwtPayout  > 0) DialogRow("တွတ် လျော်ငွေ", fmt(s.tuwtPayout), ResGold)
                    DialogRow("လျော် (စုစုပေါင်း)", fmt(s.totalPayout))
                    HorizontalDivider(color = CardBorderSubtle)
                    val balLabel = if (s.balance < 0) "ကျန်ငွေ (ပေးရန်)" else "ကျန်ငွေ (ရရန်)"
                    val remLabel = if (s.remaining < 0) "ကြွေးကျန် (ပေးရန်)" else "ကြွေးကျန် (ရရန်)"
                    DialogRow(balLabel, fmt(s.balance), if (s.balance < 0) ResRed else ResGreen, bold = true)
                    DialogRow("ပေးငွေ", fmt(s.paidAmount))
                    DialogRow(remLabel, fmt(s.remaining), if (s.remaining < 0) ResRed else ResGreen, bold = true)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().background(ResGreenBg, RoundedCornerShape(8.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, null, tint = ResGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ကော်ပီ ကူးယူပြီးပါပြီ", color = ResGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent.createChooser(
                                Intent(Intent.ACTION_SEND).apply {
                                    putExtra(Intent.EXTRA_TEXT, buildClipText(s, batchNumber, winningNumber))
                                    type = "text/plain"
                                }, "Send to..."
                            ))
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = ResPrimary)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("ပေးပို့မည်")
                    }
                    Button(
                        onClick = { dialogAgent = null },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ResPrimary)
                    ) { Text("ပိတ်မည်") }
                }
            }
        )
    }

    // ── Edit paid amount dialog ────────────────────────────────────────────────
    editAgent?.let { s ->
        AlertDialog(
            onDismissRequest = { editAgent = null },
            shape = RoundedCornerShape(20.dp),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, null, tint = ResGold, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ပေးငွေ ထည့်မည်", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${s.customer.name} ၏ ပေးငွေ ပမာဏ ထည့်ပါ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)).padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SummaryPill("ကျန်ငွေ",   fmt(s.balance),   if (s.balance < 0) ResRed else ResGreen)
                        SummaryPill("ယခင်ပေးငွေ", fmt(s.paidAmount), ResPrimary)
                        SummaryPill("ကြွေးကျန်", fmt(s.remaining), if (s.remaining < 0) ResRed else ResGreen)
                    }
                    OutlinedTextField(
                        value = editPaidText,
                        onValueChange = { editPaidText = it.filter { c -> c.isDigit() } },
                        label = { Text("ပေးငွေ (Kyat)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ResPrimary,
                            focusedLabelColor  = ResPrimary
                        ),
                        leadingIcon = { Text("K", fontWeight = FontWeight.Bold, color = ResPrimary, modifier = Modifier.padding(start = 6.dp)) }
                    )
                }
            },
            confirmButton = {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
                ) {
                    TextButton(onClick = { editAgent = null }) { Text("မလုပ်တော့ပါ") }
                    Button(
                        onClick = {
                            val amount = editPaidText.toLongOrNull() ?: 0L
                            viewModel.setPaidForBatch(s.customer.id, batchNumber, amount.toDouble())
                            paidMap = paidMap.toMutableMap().also { it[s.customer.id] = amount.toDouble() }
                            editAgent = null
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ResPrimary)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("သိမ်းမည်")
                    }
                }
            }
        )
    }

    // ── Tut Breakdown Dialog ──────────────────────────────────────────────────
    tutBreakdownAgent?.let { s ->
        WinBreakdownDialog(
            title = "တွတ် ဂဏန်းများ",
            icon = Icons.Default.Info,
            iconTint = GoldDark,
            agentName = s.customer.name,
            batchNumber = batchNumber,
            winningNumber = winningNumber,
            multiplier = permMult,
            details = s.tuwtDetails,
            totalBet = s.tuwtBetAmt,
            totalPayout = s.tuwtPayout,
            onDismiss = { tutBreakdownAgent = null }
        )
    }

    // ── Exact Breakdown Dialog ────────────────────────────────────────────────
    exactBreakdownAgent?.let { s ->
        WinBreakdownDialog(
            title = "ဒဲ့ ဂဏန်းများ",
            icon = Icons.Default.Star,
            iconTint = ResRed,
            agentName = s.customer.name,
            batchNumber = batchNumber,
            winningNumber = winningNumber,
            multiplier = exactMult,
            details = s.exactDetails,
            totalBet = s.exactBetAmt,
            totalPayout = s.exactPayout,
            onDismiss = { exactBreakdownAgent = null }
        )
    }
}

// ── Settlement Card ────────────────────────────────────────────────────────────
@Composable
fun AgentSettlementCard(
    settlement : AgentSettlement,
    onTapDetail: () -> Unit,
    onEditPaid : () -> Unit,
    onTapTut   : (() -> Unit)? = null,
    onTapExact : (() -> Unit)? = null
) {
    val s = settlement
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border    = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle)
    ) {
        Column {
            // Emerald header with customer info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ResPrimary)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(42.dp).clip(CircleShape).background(ResMintBg),
                    Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, tint = ResPrimary, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        s.customer.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("အမှတ်စဉ်: ${s.customer.id}", color = Color.White.copy(0.75f), fontSize = 11.sp)
                        if (s.customer.commissionRate > 0) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = ResGoldBg
                            ) {
                                Text(
                                    "ကော် ${(s.customer.commissionRate * 100).toInt()}%",
                                    color = GoldDark,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
                // Edit paid-amount button
                IconButton(
                    onClick = onEditPaid,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(0.18f))
                ) {
                    Icon(Icons.Default.Edit, "ပေးငွေ ပြင်ဆင်မည်", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            // Card body (tappable for detail)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onTapDetail)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Row 1: Sales / Commission / Net
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SmallCol("ရောင်းကြေး", fmt(s.totalBet), MaterialTheme.colorScheme.onSurface)
                    SmallCol("ကော်မရှင်", fmt(s.commission), ResGold)
                    SmallCol("နုတ်ပြီးငွေ", fmt(s.netAfterComm), ResPrimary)
                }

                // Row 2: Winning breakdown if any
                if (s.exactBetAmt > 0 || s.tuwtBetAmt > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ResRedBg.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (s.exactBetAmt > 0) {
                            WinRow(
                                icon = Icons.Default.Star,
                                label = "ဒဲ့ (ပေါက်)",
                                bet = fmt(s.exactBetAmt),
                                payout = fmt(s.exactPayout),
                                iconTint = ResRed,
                                onClick = onTapExact,
                                showDetailHint = true
                            )
                        }
                        if (s.tuwtBetAmt > 0) {
                            WinRow(
                                icon = Icons.Default.CheckCircle,
                                label = "တွတ် (လျော်)",
                                bet = fmt(s.tuwtBetAmt),
                                payout = fmt(s.tuwtPayout),
                                iconTint = GoldDark,
                                onClick = onTapTut,
                                showDetailHint = true
                            )
                        }
                    }
                }

                HorizontalDivider(color = CardBorderSubtle, thickness = 0.5.dp)

                // Row 3: Totals & Balance
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(
                        "လျော်ငွေ = ${fmt(s.totalPayout)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    val balLabel = if (s.balance < 0) "ကျန်ငွေ (ပေးရန်)" else "ကျန်ငွေ (ရရန်)"
                    Text(
                        "$balLabel = ${fmt(s.balance)}",
                        color = if (s.balance < 0) ResRed else ResGreen,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text(
                        "ပေးငွေ = ${fmt(s.paidAmount)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    val remLabel = if (s.remaining < 0) "ကြွေးကျန် (ပေးရန်)" else "ကြွေးကျန် (ရရန်)"
                    Text(
                        "$remLabel = ${fmt(s.remaining)}",
                        color = if (s.remaining < 0) ResRed else ResGreen,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.ContentCopy, null, tint = ResPrimary.copy(alpha = 0.6f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("နှိပ်၍ အသေးစိတ်ကြည့်ရန် & ကော်ပီကူးရန်", color = ResPrimary.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SmallCol(label: String, value: String, valueColor: Color = Color.Unspecified) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        Text(
            value,
            color = valueColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun WinRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    bet: String,
    payout: String,
    iconTint: Color,
    onClick: (() -> Unit)? = null,
    showDetailHint: Boolean = false
) {
    val containerModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(iconTint.copy(alpha = 0.08f))
            .border(0.6.dp, iconTint.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 3.dp)
    }

    Row(
        modifier = containerModifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = iconTint, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            if (showDetailHint) {
                Spacer(Modifier.width(6.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = iconTint.copy(alpha = 0.18f)
                ) {
                    Text(
                        "အသေးစိတ်",
                        color = iconTint,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$bet Ks  →  $payout Ks",
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
            if (onClick != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = "Details",
                    tint = iconTint.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun DialogRow(label: String, value: String, valueColor: Color = Color(0xFF1A1A1A), bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Medium, color = valueColor)
    }
}

@Composable
private fun SummaryPill(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = valueColor, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun FooterCol(label: String, value: Long, valueColor: Color = Color.White) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
        Text(fmt(value), color = valueColor, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

// ── Winning Breakdown Dialog (Tut & Exact) ────────────────────────────────────
@Composable
fun WinBreakdownDialog(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    agentName: String,
    batchNumber: Int,
    winningNumber: String,
    multiplier: Double,
    details: List<TutWinDetail>,
    totalBet: Long,
    totalPayout: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var copiedToast by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(iconTint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, null, tint = iconTint, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            title,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = iconTint.copy(alpha = 0.15f)
                        ) {
                            Text(
                                "×${multiplier.toInt()} ဆ",
                                color = iconTint,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "$agentName • အကြိမ် $batchNumber (ထွက်ဂဏန်း: $winningNumber)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Summary stat container
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ပေါက်ကွက်", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${details.size} ကွက်", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = iconTint)
                        }
                        Box(Modifier.width(1.dp).height(24.dp).background(CardBorderSubtle))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("ထိုးငွေ စုစုပေါင်း", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${fmt(totalBet)} Ks", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        }
                        Box(Modifier.width(1.dp).height(24.dp).background(CardBorderSubtle))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("လျော်ငွေ စုစုပေါင်း", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${fmt(totalPayout)} Ks", fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = iconTint, fontFamily = FontFamily.Monospace)
                        }
                    }
                }

                if (details.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ပေါက်ဂဏန်း မရှိပါ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    // Table Header
                    Surface(
                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                        color = iconTint.copy(alpha = 0.12f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("စဉ်", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = iconTint, modifier = Modifier.width(28.dp))
                            Text("ဂဏန်း", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = iconTint, modifier = Modifier.width(60.dp))
                            Text("ထိုးငွေ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = iconTint, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                            Text("လျော်ငွေ", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = iconTint, modifier = Modifier.weight(1.1f), textAlign = TextAlign.End)
                        }
                    }

                    // Scrollable Table Rows
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp)
                            .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                            .border(1.dp, CardBorderSubtle, RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                    ) {
                        itemsIndexed(details) { index, item ->
                            val isEven = index % 2 == 0
                            val rowBg = if (isEven) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(rowBg)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(28.dp)
                                )
                                // Number badge
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = iconTint.copy(alpha = 0.15f),
                                    border = androidx.compose.foundation.BorderStroke(0.5.dp, iconTint.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        item.number,
                                        color = iconTint,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(Modifier.width(6.dp))
                                // Bet format: =>> amount
                                Text(
                                    "=>> ${fmt(item.amount)}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.End
                                )
                                // Payout
                                Text(
                                    "${fmt(item.payout)} Ks",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = iconTint,
                                    modifier = Modifier.weight(1.1f),
                                    textAlign = TextAlign.End
                                )
                            }
                            if (index < details.lastIndex) {
                                HorizontalDivider(color = CardBorderSubtle.copy(alpha = 0.5f), thickness = 0.5.dp)
                            }
                        }
                    }
                }

                // Quick copy feedback banner
                if (copiedToast) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ResGreenBg, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Check, null, tint = ResGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("ဂဏန်းအသေးစိတ် ကူးယူပြီးပါပြီ", color = ResGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Copy button (supporting both legacy num=>>amt text and full summary)
                OutlinedButton(
                    onClick = {
                        val textToCopy = buildString {
                            appendLine("ℹ️ $title - $agentName")
                            appendLine("အကြိမ် $batchNumber (ထွက်: $winningNumber) [×${multiplier.toInt()} ဆ]")
                            appendLine("------------------------")
                            details.forEach {
                                appendLine("${it.number}=>>${it.amount}")
                            }
                            appendLine("------------------------")
                            appendLine("စုစုပေါင်း ထိုးငွေ = ${fmt(totalBet)} Ks")
                            appendLine("စုစုပေါင်း လျော်ငွေ = ${fmt(totalPayout)} Ks")
                        }
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("WinDetails", textToCopy))
                        copiedToast = true
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ResPrimary)
                ) {
                    Icon(Icons.Default.ContentCopy, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ကူးယူမည်", fontSize = 12.sp)
                }

                // Dismiss button: "ကောင်းပြီ" matching Screenshot 2
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ResPrimary)
                ) {
                    Text("ကောင်းပြီ", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    )
}

