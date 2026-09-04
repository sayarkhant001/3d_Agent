package com.threeDLedger.ui

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

// ── Colours ───────────────────────────────────────────────────────────────────
private val CCardBg     = Color(0xFF7B3F00)
private val CAccent     = Color(0xFF1BC47D)
private val CText       = Color.White
private val CDim        = Color.White.copy(alpha = 0.75f)
private val CNet        = Color(0xFF1BC47D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel         : MainViewModel,
    onNavigateBack    : () -> Unit,
    onNavigateToBetting : (Int) -> Unit,
    onNavigateToVouchers: (Int) -> Unit = {}   // lower-half tap → VouchersScreen
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val allVWB    by viewModel.vouchersWithBets.collectAsStateWithLifecycle()

    var showAdd         by remember { mutableStateOf(false) }
    var editCustomer    by remember { mutableStateOf<com.threeDLedger.data.Customer?>(null) }
    var searchQuery     by remember { mutableStateOf("") }

    // Pre-compute per-customer totals once, not inside items{}
    val customerTotals by remember(customers, allVWB) {
        derivedStateOf {
            customers.associate { c ->
                val cv = allVWB.filter { it.voucher.customerId == c.id }
                val total = cv.sumOf { it.voucher.totalAmount }
                val cut   = (total * c.commissionRate).toInt()
                val net   = total - cut - c.paidAmount.toInt()
                c.id to Triple(total, cut, net)  // id → (total, commCut, net)
            }
        }
    }

    // ── Use when{} instead of early returns ───────────────────────────────────
    when {
        editCustomer != null -> {
            EditCustomerFullScreen(
                viewModel = viewModel,
                customer  = editCustomer!!,
                onBack    = { editCustomer = null }
            )
        }

        showAdd -> {
            val nextId = (customers.maxOfOrNull { it.id } ?: 0) + 1
            AddCustomerFullScreen(
                viewModel = viewModel,
                nextId    = nextId,
                onBack    = { showAdd = false }
            )
        }

        else -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                "ကော်မရှင်များ",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = onNavigateBack) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    "Back",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAdd = true },
                        containerColor = CAccent
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
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))

                    val filtered = remember(customers, searchQuery) {
                        customers
                            .filter { it.name.contains(searchQuery, ignoreCase = true) }
                            .filter { it.name != "တင်ကွက် (Overflows)" }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "ကော်မရှင် မရှိသေးပါ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filtered, key = { it.id }) { customer ->
                                val (total, cut, net) = customerTotals[customer.id]
                                    ?: Triple(0, 0, 0)
                                val commPct = (customer.commissionRate * 100).toInt()
                                val voucherCount = allVWB.count {
                                    it.voucher.customerId == customer.id
                                }

                                CustomerCard(
                                    customer     = customer,
                                    totalAmount  = total,
                                    commCut      = cut,
                                    netAmount    = net,
                                    commPct      = commPct,
                                    voucherCount = voucherCount,
                                    onEditTap    = { editCustomer = customer },
                                    onBetsTap    = { onNavigateToVouchers(customer.id) },
                                    onAddBetTap  = { onNavigateToBetting(customer.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Stateless card composable ─────────────────────────────────────────────────
@Composable
private fun CustomerCard(
    customer     : com.threeDLedger.data.Customer,
    totalAmount  : Int,
    commCut      : Int,
    netAmount    : Int,
    commPct      : Int,
    voucherCount : Int,
    onEditTap    : () -> Unit,
    onBetsTap    : () -> Unit,
    onAddBetTap  : () -> Unit
) {
    Card(
        modifier  = Modifier.fillMaxWidth().padding(bottom = 10.dp),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = CCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column {
            // ── UPPER: tap to EDIT ────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEditTap)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ID: ${customer.id}",
                        color = CText,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        customer.name,
                        color = CText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
                // + Add-bet circle button
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(CAccent, CircleShape)
                        .clickable(onClick = onAddBetTap),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        "Add Bet",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // ── DIVIDER ───────────────────────────────────────────────────────
            HorizontalDivider(
                color = Color.White.copy(alpha = 0.25f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            // ── LOWER: tap to VIEW bets ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onBetsTap)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "စုစုပေါင်း: %,d".format(totalAmount),
                        color = CDim, fontSize = 13.sp
                    )
                    Text(
                        "ကော် ($commPct%%): %,d".format(commCut),
                        color = CDim, fontSize = 13.sp
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "ဘောင်ချာ: $voucherCount",
                        color = CDim, fontSize = 13.sp
                    )
                    Text(
                        "ပေးငွေ: %,d".format(customer.paidAmount.toInt()),
                        color = CDim, fontSize = 13.sp
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "နှုတ်ပြီးငွေ: %,d".format(netAmount),
                    color = CNet,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

// ── Add Customer ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerFullScreen(
    viewModel: MainViewModel,
    nextId   : Int,
    onBack   : () -> Unit
) {
    var name          by remember { mutableStateOf("") }
    var commissionStr by remember { mutableStateOf("") }
    var multiplierStr by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ကော်မရှင် အသစ် ထည့်မယ်",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("မလုပ်တော့ပါ") }

                Button(
                    onClick = {
                        val rate = (commissionStr.toDoubleOrNull() ?: 0.0) / 100.0
                        val mult = multiplierStr.toIntOrNull() ?: 80
                        viewModel.addCustomer(name, rate, mult)
                        onBack()
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("ထည့်မည်") }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CustomerFormField(label = "ID",                 value = "$nextId",      readOnly = true)  { }
            CustomerFormField(label = "အမည်",              value = name)            { name = it }
            CustomerFormField(label = "ကော်မရှင်ခ (%)",  value = commissionStr)   { commissionStr = it }
            CustomerFormField(label = "အဆ",               value = multiplierStr)   { multiplierStr = it }
        }
    }
}

// ── Edit Customer ─────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCustomerFullScreen(
    viewModel: MainViewModel,
    customer : com.threeDLedger.data.Customer,
    onBack   : () -> Unit
) {
    var name          by remember { mutableStateOf(customer.name) }
    var commissionStr by remember { mutableStateOf((customer.commissionRate * 100).toInt().toString()) }
    var multiplierStr by remember { mutableStateOf(customer.multiplier.toString()) }
    var paidStr       by remember { mutableStateOf(customer.paidAmount.toInt().toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ကော်မရှင် ပြင်ဆင်မယ်",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("မလုပ်တော့ပါ") }

                Button(
                    onClick = {
                        val rate = (commissionStr.toDoubleOrNull() ?: 0.0) / 100.0
                        val mult = multiplierStr.toIntOrNull() ?: 80
                        val paid = paidStr.toDoubleOrNull() ?: 0.0
                        viewModel.updateCustomer(
                            customer.copy(
                                name = name,
                                commissionRate = rate,
                                multiplier = mult,
                                paidAmount = paid
                            )
                        )
                        onBack()
                    },
                    modifier = Modifier.weight(1f).height(48.dp)
                ) { Text("ပြင်ဆင်မည်") }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CustomerFormField(label = "ID",                value = "${customer.id}", readOnly = true) { }
            CustomerFormField(label = "အမည်",             value = name)             { name = it }
            CustomerFormField(label = "ကော်မရှင်ခ (%)", value = commissionStr)    { commissionStr = it }
            CustomerFormField(label = "အဆ",              value = multiplierStr)    { multiplierStr = it }
            CustomerFormField(label = "ပေးငွေ",          value = paidStr)          { paidStr = it }
        }
    }
}

// ── Reusable form field ───────────────────────────────────────────────────────
@Composable
private fun CustomerFormField(
    label         : String,
    value         : String,
    readOnly      : Boolean = false,
    onValueChange : (String) -> Unit
) {
    OutlinedTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = { Text(label) },
        readOnly      = readOnly,
        singleLine    = true,
        modifier      = Modifier.fillMaxWidth()
    )
}

// ── FormRow kept for backward compatibility if referenced elsewhere ────────────
@Composable
fun FormRow(
    label         : String,
    value         : String,
    readOnly      : Boolean = false,
    placeholder   : String = "",
    onValueChange : (String) -> Unit
) {
    CustomerFormField(label = label, value = value, readOnly = readOnly, onValueChange = onValueChange)
}
