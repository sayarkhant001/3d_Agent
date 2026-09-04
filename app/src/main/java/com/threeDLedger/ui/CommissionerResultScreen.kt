package com.threeDLedger.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

// ── Palette ────────────────────────────────────────────────────────────────────
private val CrTeal     = Color(0xFF00796B)
private val CrOrange   = Color(0xFFFF9800)
private val CrOrangeDk = Color(0xFFF57C00)
private val CrYellow   = Color(0xFFFFEE58)
private val CrRed      = Color(0xFFD32F2F)
private val CrGreen    = Color(0xFF43A047)

// ── Data model ─────────────────────────────────────────────────────────────────
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
    val remaining   : Long
)

private fun fmt(n: Long) = "%,d".format(n)

private fun buildClipText(s: AgentSettlement, batch: Int, winNum: String): String =
    buildString {
        appendLine("3D = $batch ( $winNum )")
        appendLine("အမည် = ${s.customer.name}")
        appendLine("ရောင်းကြေး = ${s.totalBet}")
        appendLine("ကော် = ${s.commission}")
        appendLine("နုတ်ပြီးငွေ = ${s.netAfterComm}")
        if (s.exactBetAmt > 0) appendLine("ဲ = ${s.exactBetAmt}")
        if (s.tuwtPayout  > 0) appendLine("တွတ် = ${s.tuwtPayout}")
        appendLine("လျော် = ${s.totalPayout}")
        appendLine("ကျန်ငွေ = ${s.balance}")
        appendLine("ပေးငွေ = ${s.paidAmount}")
        append("ကြွေးကျန် = ${s.remaining}")
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

    var dialogAgent  by remember { mutableStateOf<AgentSettlement?>(null) }
    var editAgent    by remember { mutableStateOf<AgentSettlement?>(null) }
    var editPaidText by remember { mutableStateOf("") }

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
            val agentVouchers = batchVouchers.filter { it.voucher.customerId == customer.id }
            if (agentVouchers.isEmpty()) return@mapNotNull null
            val bets     = agentVouchers.flatMap { it.bets }
            val totalBet = bets.sumOf { it.amount }.toLong()
            if (totalBet == 0L) return@mapNotNull null

            val commission   = (totalBet * customer.commissionRate / 100.0).toLong()
            val netAfterComm = totalBet - commission
            val exactBets    = bets.filter { it.number == winningNumber }
            val permBets     = bets.filter { it.number in permsOnly }
            val nearBets     = bets.filter { it.number in near }
            val exactBetAmt  = exactBets.sumOf { it.amount }.toLong()
            val exactPayout  = (exactBetAmt * exactMult).toLong()
            val permPayout   = (permBets.sumOf { it.amount } * permMult).toLong()
            val nearPayout   = (nearBets.sumOf { it.amount } * nearMult).toLong()
            val tuwtBetAmt   = (permBets + nearBets).sumOf { it.amount }.toLong()
            val tuwtPayout   = permPayout + nearPayout
            val totalPayout  = exactPayout + tuwtPayout
            val balance      = netAfterComm - totalPayout
            val paid         = (paidMap[customer.id] ?: 0.0).toLong()

            AgentSettlement(
                customer, totalBet, commission, netAfterComm,
                exactBetAmt, exactPayout, tuwtBetAmt, tuwtPayout,
                totalPayout, balance, paid, balance - paid
            )
        }.sortedBy { it.customer.id }
    }

    val grandTotal   = settlements.sumOf { it.totalBet }
    val grandComm    = settlements.sumOf { it.commission }
    val grandPayout  = settlements.sumOf { it.totalPayout }
    val grandBalance = settlements.sumOf { it.balance }

    // ── Scaffold ──────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ကော်မဆုင်ရာ ရလဒ်", color = Color.White, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = CrTeal)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF0F4F3))
        ) {
            // Page header
            Column(
                modifier = Modifier.fillMaxWidth().background(Color.White).padding(vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("အကြိမ် - $batchNumber", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color(0xFF212121))
                Spacer(Modifier.height(2.dp))
                if (winningNumber.length == 3) {
                    Text(
                        "ထွက်ဂဏန်း -  ( $winningNumber )",
                        color = CrRed, fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp, fontFamily = FontFamily.Monospace
                    )
                } else {
                    Text("ပေါက်ဂဏန်း မကြေညာရသေးပါ", color = Color.Gray, fontSize = 14.sp)
                }
            }
            HorizontalDivider(thickness = 2.dp, color = CrTeal)

            when {
                winningNumber.length != 3 ->
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        Text("ပေါက်ဂဏန်း ကြေညာပြီးမှ ကြည့်ရှုနိုင်ပါသည်", color = Color.Gray, textAlign = TextAlign.Center)
                    }
                settlements.isEmpty() ->
                    Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                        Text("Batch $batchNumber တွင် ထိုးမှု မရှိသေးပါ", color = Color.Gray, textAlign = TextAlign.Center)
                    }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                onEditPaid  = {
                                    editAgent    = s
                                    editPaidText = if (s.paidAmount == 0L) "" else s.paidAmount.toString()
                                }
                            )
                        }
                    }
                }
            }

            // Footer totals
            Row(
                modifier = Modifier.fillMaxWidth().background(CrOrangeDk).padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                FooterCol("စုပေါင်း", grandTotal)
                Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(0.3f)))
                FooterCol("ကော်မဆုင်ခ", grandComm)
                Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(0.3f)))
                FooterCol("လျော်ငွေ", grandPayout)
                Box(Modifier.width(1.dp).height(28.dp).background(Color.White.copy(0.3f)))
                FooterCol("လက်ကျန်", grandBalance)
            }
        }
    }

    // ── Detail dialog ──────────────────────────────────────────────────────────
    dialogAgent?.let { s ->
        AlertDialog(
            onDismissRequest = { dialogAgent = null },
            shape = RoundedCornerShape(20.dp),
            containerColor = Color(0xFFFAFAFA),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(CrTeal), Alignment.Center) {
                        Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(s.customer.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Batch $batchNumber • $winningNumber", color = Color.Gray, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    DialogRow("3D", "$batchNumber ( $winningNumber )")
                    DialogRow("အမည်", s.customer.name)
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    DialogRow("ရောင်းကြေး", fmt(s.totalBet))
                    DialogRow("ကော်", fmt(s.commission))
                    DialogRow("နုတ်ပြီးငွေ", fmt(s.netAfterComm))
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    if (s.exactBetAmt > 0) DialogRow("ဲ (ပေါက်သီး)", fmt(s.exactBetAmt), CrRed)
                    if (s.tuwtPayout  > 0) DialogRow("တွတ် လျော်ငွေ", fmt(s.tuwtPayout), CrTeal)
                    DialogRow("လျော် (စုစုပေါင်း)", fmt(s.totalPayout))
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                    DialogRow("ကျန်ငွေ", fmt(s.balance), if (s.balance < 0) CrRed else CrGreen, bold = true)
                    DialogRow("ပေးငွေ", fmt(s.paidAmount))
                    DialogRow("ကြွေးကျန်", fmt(s.remaining), if (s.remaining < 0) CrRed else CrGreen, bold = true)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFE8F5E9), RoundedCornerShape(8.dp)).padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Check, null, tint = CrGreen, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Clipboard ထဲ ကော်ပီ ပြုလုပ်ပြီး", color = CrGreen, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrTeal)
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Send")
                    }
                    Button(
                        onClick = { dialogAgent = null },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = CrTeal)
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
                    Icon(Icons.Default.Edit, null, tint = CrOrange, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("ပေးငွေ ထည့်မည်", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${s.customer.name} ၏ ပေးငွေ ပမာဏ ထည့်ပါ", fontSize = 14.sp, color = Color(0xFF555555))
                    Row(
                        Modifier.fillMaxWidth().background(Color(0xFFF5F5F5), RoundedCornerShape(10.dp)).padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SummaryPill("ကျန်ငွေ",   fmt(s.balance),   if (s.balance < 0) CrRed else CrGreen)
                        SummaryPill("ယခင်ပေးငွေ", fmt(s.paidAmount), CrTeal)
                        SummaryPill("ကြွေးကျန်", fmt(s.remaining), if (s.remaining < 0) CrRed else CrGreen)
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
                            focusedBorderColor = CrTeal,
                            focusedLabelColor  = CrTeal
                        ),
                        leadingIcon = { Text("K", fontWeight = FontWeight.Bold, color = CrTeal, modifier = Modifier.padding(start = 4.dp)) }
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
                        colors = ButtonDefaults.buttonColors(containerColor = CrTeal)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("သိမ်းမည်")
                    }
                }
            }
        )
    }
}

// ── Card ───────────────────────────────────────────────────────────────────────
@Composable
private fun AgentSettlementCard(
    settlement : AgentSettlement,
    onTapDetail: () -> Unit,
    onEditPaid : () -> Unit
) {
    val s = settlement
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(4.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column {
            // Teal header
            Row(
                modifier = Modifier.fillMaxWidth().background(CrTeal).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(46.dp).clip(CircleShape).background(CrOrange), Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("ကော် အိုင်ဒီ : ${s.customer.id}", color = Color.White.copy(0.8f), fontSize = 11.sp)
                    Text("အမည် : ${s.customer.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (s.customer.commissionRate > 0)
                        Text("ကော်မဆုင် ${s.customer.commissionRate}%", color = CrYellow, fontSize = 10.sp)
                }
                // Edit paid-amount button
                IconButton(
                    onClick = onEditPaid,
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(0.15f))
                ) {
                    Icon(Icons.Default.Edit, "ပေးငွေ ပြင်ဆင်မည်", tint = CrYellow, modifier = Modifier.size(18.dp))
                }
            }

            // Orange body (tappable)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CrOrange)
                    .clickable(onClick = onTapDetail)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    SmallCol("စုပေါင်း",  fmt(s.totalBet))
                    SmallCol("ကော်",      fmt(s.commission))
                    SmallCol("နုတ်ပြီး",  fmt(s.netAfterComm))
                }
                HorizontalDivider(color = Color.White.copy(0.3f), thickness = 0.5.dp)
                if (s.exactBetAmt > 0) WinRow(Icons.Default.Star,  "ဲ",    fmt(s.exactBetAmt), fmt(s.exactPayout), CrYellow)
                if (s.tuwtBetAmt  > 0) WinRow(Icons.Default.Info,  "တွတ်", fmt(s.tuwtBetAmt),  fmt(s.tuwtPayout),  Color.White)
                HorizontalDivider(color = Color.White.copy(0.3f), thickness = 0.5.dp)
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("လျော်ငွေ = ${fmt(s.totalPayout)}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("ရငွေ = ${fmt(s.balance)}", color = if (s.balance < 0) CrYellow else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                    Text("ပေးငွေ = ${fmt(s.paidAmount)}", color = Color.White, fontSize = 12.sp)
                    Text("ကြွေးကျန် = ${fmt(s.remaining)}", color = if (s.remaining < 0) CrYellow else Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), Arrangement.Center, Alignment.CenterVertically) {
                    Icon(Icons.Default.ContentCopy, null, tint = Color.White.copy(0.55f), modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(3.dp))
                    Text("နှိပ်၍ ကော်ပီ + အသေးစိတ်", color = Color.White.copy(0.55f), fontSize = 9.sp)
                }
            }
        }
    }
}

@Composable private fun SmallCol(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(0.75f), fontSize = 9.sp)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable private fun WinRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, bet: String, payout: String, iconTint: Color) {
    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Text("$bet  /  $payout", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

@Composable private fun DialogRow(label: String, value: String, valueColor: Color = Color(0xFF212121), bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color(0xFF666666), fontWeight = FontWeight.SemiBold)
        Text(value, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal, color = valueColor)
    }
}

@Composable private fun SummaryPill(label: String, value: String, valueColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = Color.Gray)
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = valueColor, fontFamily = FontFamily.Monospace)
    }
}

@Composable private fun FooterCol(label: String, value: Long) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.White.copy(0.8f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Text(fmt(value), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
    }
}
