package com.threeDLedger.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

// ── Colours matching the app's orange/teal theme ──────────────────────────────
private val CardBg       = Color(0xFF7B3F00)   // dark brown from screenshot
private val AccentTeal   = Color(0xFF1BC47D)   // teal green from screenshot
private val TextOnCard   = Color.White
private val DimOnCard    = Color.White.copy(alpha = 0.75f)
private val NetAmtColor  = AccentTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBetting: (Int) -> Unit
) {
    val customers    by viewModel.customers.collectAsStateWithLifecycle()
    val allVWB       by viewModel.vouchersWithBets.collectAsStateWithLifecycle()

    var showAddDialog   by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<com.threeDLedger.data.Customer?>(null) }
    var viewingBetsCustomer by remember { mutableStateOf<com.threeDLedger.data.Customer?>(null) }
    var searchQuery     by remember { mutableStateOf("") }

    // ── Full-screen modes ─────────────────────────────────────────────────────
    if (editingCustomer != null) {
        EditCustomerFullScreen(viewModel, editingCustomer!!) { editingCustomer = null }
        return
    }
    if (showAddDialog) {
        val nextId = (customers.maxOfOrNull { it.id } ?: 0) + 1
        AddCustomerFullScreen(viewModel, nextId) { showAddDialog = false }
        return
    }
    if (viewingBetsCustomer != null) {
        val c = viewingBetsCustomer!!
        val customerVWB    = allVWB.filter { it.voucher.customerId == c.id }
        val totalAmount    = customerVWB.sumOf { it.voucher.totalAmount }
        val commissionCut  = (totalAmount * c.commissionRate).toInt()
        val netAmount      = totalAmount - commissionCut - c.paidAmount.toInt()

        // Aggregate bets: number → total amount (sorted ascending)
        val betsForCustomer = allVWB
            .filter { it.voucher.customerId == c.id }
            .flatMap { it.bets }
        val numberMap = mutableMapOf<String, Int>()
        betsForCustomer.forEach { bet ->
            numberMap[bet.number] = (numberMap[bet.number] ?: 0) + bet.amount
        }
        val sortedNumbers: List<Pair<String, Int>> = numberMap.entries
            .sortedBy { it.key.toIntOrNull() ?: 0 }
            .map { it.key to it.value }

        AgentBetsView(
            customerName   = c.name,
            batchNumber    = allVWB.firstOrNull { it.voucher.customerId == c.id }?.voucher?.batchNumber ?: 0,
            totalAmount    = totalAmount,
            commissionCut  = commissionCut,
            netAmount      = netAmount,
            paidAmount     = c.paidAmount.toInt(),
            commissionRate = c.commissionRate,
            sortedNumbers  = sortedNumbers,
            onBack         = { viewingBetsCustomer = null }
        )
        return
    }

    // ── Main list ─────────────────────────────────────────────────────────────
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ကော်မရှင်များ", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = AccentTeal
            ) {
                Icon(Icons.Default.Add, "Add", tint = Color.White)
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("ရှာဖွေရန်") },
                leadingIcon = { Icon(Icons.Default.Search, "Search") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val filtered = customers
                    .filter { it.name.contains(searchQuery, ignoreCase = true) }
                    .filter { it.name != "တင်ကွက် (Overflows)" }

                items(filtered) { customer ->
                    val customerVWB   = allVWB.filter { it.voucher.customerId == customer.id }
                    val totalAmount   = customerVWB.sumOf { it.voucher.totalAmount }
                    val commissionCut = (totalAmount * customer.commissionRate).toInt()
                    val netAmount     = totalAmount - commissionCut - customer.paidAmount.toInt()
                    val commPct       = (customer.commissionRate * 100).toInt()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column {
                            // ── UPPER HALF — tap to EDIT ──────────────────────
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { editingCustomer = customer }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "ကော် အိုင်ဒီ: ${customer.id}",
                                        color = TextOnCard,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        "အမည်: ${customer.name}",
                                        color = TextOnCard,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                }
                                // + (Add bet) circle button
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .background(AccentTeal, CircleShape)
                                        .clickable { onNavigateToBetting(customer.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Add,
                                        "Add Bet",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            // ── DIVIDER LINE ──────────────────────────────────
                            Divider(
                                color = Color.White.copy(alpha = 0.25f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )

                            // ── LOWER HALF — tap to VIEW bets ─────────────────
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewingBetsCustomer = customer }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "စုစုပေါင်း: %,d".format(totalAmount),
                                        color = DimOnCard,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "ကော် ($commPct%): %,d".format(commissionCut),
                                        color = DimOnCard,
                                        fontSize = 13.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "ဘောင်ချာ: ${customerVWB.size}",
                                        color = DimOnCard,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        "ပေးငွေ: %,d".format(customer.paidAmount.toInt()),
                                        color = DimOnCard,
                                        fontSize = 13.sp
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "နှုတ်ပြီးငွေ: %,d".format(netAmount),
                                    color = NetAmtColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Agent Bets View — full-screen with copy + print, numbers sorted ascending
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgentBetsView(
    customerName  : String,
    batchNumber   : Int,
    totalAmount   : Int,
    commissionCut : Int,
    netAmount     : Int,
    paidAmount    : Int,
    commissionRate: Double,
    sortedNumbers : List<Pair<String, Int>>,
    onBack        : () -> Unit
) {
    val context         = LocalContext.current
    val clipboardManager= LocalClipboardManager.current
    val coroutineScope  = rememberCoroutineScope()
    val commPct         = (commissionRate * 100).toInt()

    val Orange    = Color(0xFFF57C00)
    val OrangeRow = Color(0xFFFFF3E0)

    // Build printable/shareable text
    val maxAmt = sortedNumbers.maxOfOrNull { it.second } ?: 0
    val amtW   = "%,d".format(maxAmt).length.coerceAtLeast(6)
    val shareText = buildString {
        appendLine("========================")
        appendLine("  ကော်မရှင် စာရင်း")
        appendLine("========================")
        appendLine(" အကြိမ်  : $batchNumber")
        appendLine(" ကော်မရှင် : $customerName")
        appendLine(" ကော်မရှင်ခ: $commPct%")
        appendLine("------------------------")
        sortedNumbers.forEach { (num, amt) ->
            appendLine(" ${num.padStart(3)} = ${"  %,d".format(amt).padStart(amtW)} Ks")
        }
        appendLine("------------------------")
        appendLine(" စုစုပေါင်း  = %,d Ks".format(totalAmount))
        appendLine(" ကော် ($commPct%%) = %,d Ks".format(commissionCut))
        appendLine(" ပေးငွေ     = %,d Ks".format(paidAmount))
        appendLine("------------------------")
        appendLine(" နှုတ်ပြီးငွေ = %,d Ks".format(netAmount))
        appendLine("========================")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(customerName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text("အကြိမ် : $batchNumber", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Print
                    IconButton(onClick = {
                        coroutineScope.launch {
                            try {
                                val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                val paperSize = prefs.getString("paperSize", "58mm") ?: "58mm"
                                val bets = sortedNumbers.map { (num, amt) -> num to amt }
                                val voucherData = com.threeDLedger.logic.BluetoothPrinter.VoucherData(
                                    batchNumber  = batchNumber,
                                    voucherId    = 0,
                                    date         = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm").format(java.util.Date()),
                                    customerName = customerName,
                                    bets         = bets,
                                    totalAmount  = totalAmount,
                                    footerText   = "ကော်မရှင် ($commPct%) : %,d Ks | နှုတ်ပြီး : %,d Ks".format(commissionCut, netAmount)
                                )
                                val bitmap = com.threeDLedger.logic.BluetoothPrinter.createVoucherBitmap(voucherData, paperSize)
                                com.threeDLedger.logic.BluetoothPrinter.printBitmap(bitmap, paperSize)
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "ပရင်တာ error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.Print, "Print", tint = Color.White)
                    }
                    // Share
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    }) {
                        Icon(Icons.Default.Share, "Share", tint = Color.White)
                    }
                    // Copy
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(shareText))
                        android.widget.Toast.makeText(context, "Copied!", android.widget.Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Orange)
            )
        },
        bottomBar = {
            // Summary footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Orange)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("စုစုပေါင်း", color = Color.White, fontSize = 13.sp)
                    Text("%,d Ks".format(totalAmount), color = Color.White, fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ကော် ($commPct%)", color = Color.White, fontSize = 13.sp)
                    Text("%,d Ks".format(commissionCut), color = Color.White, fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("ပေးငွေ", color = Color.White, fontSize = 13.sp)
                    Text("%,d Ks".format(paidAmount), color = Color.White, fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace)
                }
                Divider(color = Color.White.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("နှုတ်ပြီးငွေ", color = AccentTeal, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("%,d Ks".format(netAmount), color = AccentTeal, fontSize = 15.sp,
                        fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Orange)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    "ဂဏန်းများ",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
                Text(
                    "ပမာဏ",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center
                )
            }

            if (sortedNumbers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("ထိုးမှု မရှိသေးပါ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(sortedNumbers) { (number, amount) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 11.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                number,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Orange,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                "%,d".format(amount),
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                        Divider(color = Orange.copy(alpha = 0.2f), thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add Customer full-screen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerFullScreen(
    viewModel: MainViewModel,
    nextId: Int,
    onBack: () -> Unit
) {
    var name          by remember { mutableStateOf("") }
    var commissionStr by remember { mutableStateOf("") }
    var multiplierStr by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ကော်မရှင် အသစ် ထည့်မယ်", color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("မလုပ်တော့ပါ", color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Button(
                    onClick = {
                        val rate = (commissionStr.toDoubleOrNull() ?: 0.0) / 100.0
                        val mult = multiplierStr.toIntOrNull() ?: 80
                        viewModel.addCustomer(name, rate, mult)
                        onBack()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("ထည့်မည်", color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Person, null, modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormRow("ကော် အိုင်ဒီ:", "$nextId", readOnly = true) {}
                    FormRow("ကော် အမည်:", name, placeholder = "ရိုက်ထည့်ပါ") { name = it }
                    FormRow("ကော်မရှင်ခ (%):", commissionStr, placeholder = "20") { commissionStr = it }
                    FormRow("အဆ:", multiplierStr, placeholder = "80") { multiplierStr = it }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Edit Customer full-screen
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCustomerFullScreen(
    viewModel: MainViewModel,
    customer: com.threeDLedger.data.Customer,
    onBack: () -> Unit
) {
    var name          by remember { mutableStateOf(customer.name) }
    var commissionStr by remember { mutableStateOf((customer.commissionRate * 100).toInt().toString()) }
    var multiplierStr by remember { mutableStateOf(customer.multiplier.toString()) }
    var paidAmountStr by remember { mutableStateOf(customer.paidAmount.toInt().toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ကော်မရှင် ပြင်ဆင်မယ်", color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back",
                            tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            Row(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("မလုပ်တော့ပါ", color = MaterialTheme.colorScheme.onPrimaryContainer) }
                Button(
                    onClick = {
                        val rate = (commissionStr.toDoubleOrNull() ?: 0.0) / 100.0
                        val mult = multiplierStr.toIntOrNull() ?: 80
                        val paid = (paidAmountStr.toDoubleOrNull() ?: 0.0)
                        viewModel.updateCustomer(customer.copy(
                            name = name, commissionRate = rate,
                            multiplier = mult, paidAmount = paid
                        ))
                        onBack()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(4.dp)
                ) { Text("ပြင်ဆင်မည်", color = MaterialTheme.colorScheme.onPrimaryContainer) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormRow("ကော် အိုင်ဒီ:", "${customer.id}", readOnly = true) {}
                    FormRow("ကော် အမည်:", name, placeholder = "ရိုက်ထည့်ပါ") { name = it }
                    FormRow("ကော်မရှင်ခ (%):", commissionStr, placeholder = "20") { commissionStr = it }
                    FormRow("အဆ:", multiplierStr, placeholder = "80") { multiplierStr = it }
                    FormRow("ပေးငွေ (Paid):", paidAmountStr, placeholder = "0") { paidAmountStr = it }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FormRow helper
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun FormRow(
    label: String,
    value: String,
    readOnly: Boolean = false,
    placeholder: String = "",
    onValueChange: (String) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1.2f))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .weight(2.8f)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            decorationBox = { inner ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(placeholder, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                inner()
            }
        )
    }
}
