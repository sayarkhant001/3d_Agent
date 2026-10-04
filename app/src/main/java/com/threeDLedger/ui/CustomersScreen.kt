package com.threeDLedger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.data.Customer
import com.threeDLedger.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel           : MainViewModel,
    onNavigateBack      : () -> Unit,
    onNavigateToBetting : (Int) -> Unit,
    onNavigateToVouchers: (Int) -> Unit = {}
) {
    val customers    by viewModel.customers.collectAsStateWithLifecycle()
    val allVWB       by viewModel.vouchersWithBets.collectAsStateWithLifecycle()
    val currentBatch by viewModel.currentBatch.collectAsStateWithLifecycle()

    var showAdd         by remember { mutableStateOf(false) }
    var editCustomer    by remember { mutableStateOf<Customer?>(null) }
    var viewingCustomer by remember { mutableStateOf<Customer?>(null) }
    var searchQuery     by remember { mutableStateOf("") }

    // Pre-compute per-customer totals once for active batch (excluding overflow vouchers)
    val customerTotals by remember(customers, allVWB, currentBatch) {
        derivedStateOf {
            customers.associate { c ->
                val cv = allVWB.filter { 
                    it.voucher.batchNumber == currentBatch &&
                    it.voucher.customerId == c.id && 
                    !it.voucher.isArchived &&
                    !it.voucher.remark.contains("တင်ကွက်") && 
                    !it.voucher.remark.contains("overflow", ignoreCase = true) &&
                    !it.voucher.remark.contains("upper", ignoreCase = true) &&
                    !it.voucher.remark.contains("အထက်ဒိုင်")
                }
                val total = cv.sumOf { it.voucher.totalAmount }
                val rate  = if (c.commissionRate > 1.0) c.commissionRate / 100.0 else c.commissionRate
                val cut   = (total * rate).toInt()
                val paid  = viewModel.getPaidForBatch(c.id, currentBatch)
                val net   = total - cut - paid.toInt()
                c.id to Triple(total, cut, net)
            }
        }
    }

    BackHandler {
        when {
            viewingCustomer != null -> viewingCustomer = null
            editCustomer != null -> editCustomer = null
            showAdd -> showAdd = false
            searchQuery.isNotEmpty() -> searchQuery = ""
            else -> onNavigateBack()
        }
    }

    when {
        viewingCustomer != null -> {
            val c = viewingCustomer!!
            val (total, cut, net) = customerTotals[c.id] ?: Triple(0, 0, 0)
            AgentNumbersView(
                customer     = c,
                allVWB       = allVWB,
                currentBatch = currentBatch,
                totalAmount  = total,
                commCut      = cut,
                netAmount    = net,
                paidAmount   = viewModel.getPaidForBatch(c.id, currentBatch).toInt(),
                onBack       = { viewingCustomer = null }
            )
        }

        editCustomer != null -> {
            EditCustomerFullScreen(
                viewModel    = viewModel,
                customer     = editCustomer!!,
                currentBatch = currentBatch,
                onBack       = { editCustomer = null }
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
            val isDark = isSystemInDarkTheme()
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    Surface(
                        color = if (isDark) MaterialTheme.colorScheme.surface else EmeraldPrimary,
                        modifier = Modifier.fillMaxWidth(),
                        shadowElevation = if (isDark) 0.dp else 2.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .statusBarsPadding()
                                .padding(bottom = 12.dp)
                        ) {
                            // Header Row: Arrow, Title, Batch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "ကော်မရှင်များ",
                                    color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 19.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDark) MaterialTheme.colorScheme.primaryContainer else Color.White.copy(alpha = 0.18f),
                                    modifier = Modifier.padding(end = 14.dp)
                                ) {
                                    Text(
                                        "အကြိမ် : $currentBatch",
                                        color = if (isDark) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.5.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            // Embedded Search Bar
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 2.dp)
                                    .height(44.dp),
                                shape = RoundedCornerShape(22.dp),
                                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color.White,
                                border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outlineVariant else Color.Transparent),
                                shadowElevation = if (isDark) 0.dp else 1.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Search,
                                        contentDescription = "Search",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                "ကော်မရှင် အမည် ရှာရန်...",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                fontSize = 14.sp
                                            )
                                        }
                                        BasicTextField(
                                            value = searchQuery,
                                            onValueChange = { searchQuery = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            ),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(
                                            onClick = { searchQuery = "" },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Clear",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = { showAdd = true },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = CircleShape,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "ကော်မရှင်အသစ်ထည့်ရန်",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(MaterialTheme.colorScheme.background)
                ) {
                    val filtered = remember(customers, searchQuery) {
                        customers
                            .filter { it.name.contains(searchQuery, ignoreCase = true) }
                            .filter { 
                                !it.name.contains("တင်ကွက်") && !it.name.contains("overflow", ignoreCase = true) &&
                                !it.name.contains("upper", ignoreCase = true) && !it.name.contains("အထက်ဒိုင်")
                            }
                    }

                    if (filtered.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.PeopleOutline,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "ကော်မရှင် မရှိသေးပါ",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { customer ->
                                val (total, cut, net) = customerTotals[customer.id]
                                    ?: Triple(0, 0, 0)
                                val commPct = if (customer.commissionRate > 1.0) customer.commissionRate.toInt() else (customer.commissionRate * 100).toInt()
                                val voucherCount = allVWB.count {
                                    it.voucher.batchNumber == currentBatch &&
                                    it.voucher.customerId == customer.id &&
                                    !it.voucher.isArchived &&
                                    !it.voucher.remark.contains("တင်ကွက်") &&
                                    !it.voucher.remark.contains("overflow", ignoreCase = true)
                                }

                                CustomerCard(
                                    customer     = customer,
                                    totalAmount  = total,
                                    commCut      = cut,
                                    netAmount    = net,
                                    commPct      = commPct,
                                    voucherCount = voucherCount,
                                    onEditTap    = { editCustomer = customer },
                                    onBetsTap    = { viewingCustomer = customer },
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

// ── Premium App-Harmonized Customer Card ──────────────────────────────────────────
@Composable
fun CustomerCard(
    customer     : Customer,
    totalAmount  : Int,
    commCut      : Int,
    netAmount    : Int,
    commPct      : Int = 0,
    voucherCount : Int,
    onEditTap    : () -> Unit,
    onBetsTap    : () -> Unit,
    onAddBetTap  : () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onBetsTap),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // ── TOP HEADER: Avatar + Name + ID/Comm Pills + Action Buttons ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Circular mint avatar with emerald icon
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Commissioner ID & Name
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        customer.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "ကော် အိုင်ဒီ: ${customer.id}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                "ဒဲ့ ${customer.multiplier}ဆ / တွတ် ${customer.tuwtMultiplier}ဆ",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        if (commPct > 0) {
                            val isDark = isSystemInDarkTheme()
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isDark) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f) else GoldContainer,
                                border = BorderStroke(0.5.dp, if (isDark) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else Color.Transparent)
                            ) {
                                Text(
                                    "ကော် $commPct%",
                                    color = if (isDark) MaterialTheme.colorScheme.secondary else GoldDark,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }

                // Action 1: "ထိုးမည်" (Enlarged and isolated to avoid accidental touch)
                FilledTonalButton(
                    onClick = onAddBetTap,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ထိုးမည်", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            }

            // ── FINANCIAL METRICS ROW (Structured 3-Col FinTech Grid) ──────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1: စုစုပေါင်း (Total)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        "စုစုပေါင်း",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "%,d ကျပ်".format(totalAmount),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Column 2: ကော်မရှင် (Commission)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "ကော်မရှင်",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "%,d ကျပ်".format(commCut),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Column 3: နုတ်ပြီးငွေ (Net Amount)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "နုတ်ပြီးငွေ",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "%,d ကျပ်".format(netAmount),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // ── FOOTER ROW: Vouchers & Paid summary + Navigation Cue ───────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Voucher count and Paid amount
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        "ဘောင်ချာ : $voucherCount စောင်",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    if (customer.paidAmount > 0) {
                        Text("•", fontSize = 10.sp, color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            "ပေးငွေ : %,d ကျပ်".format(customer.paidAmount.toInt()),
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Right: Clear navigation cue + Safe Edit customer button in footer
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        onClick = onEditTap,
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.height(26.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "ပြင်ဆင်မည်",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "ပြင်မည်",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            "ဘောင်ချာများ ကြည့်ရန်",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false
                        )
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ── Add Customer Screen ───────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerFullScreen(
    viewModel: MainViewModel,
    nextId   : Int,
    onBack   : () -> Unit
) {
    BackHandler(onBack = onBack)

    var name              by remember { mutableStateOf("") }
    var commissionStr     by remember { mutableStateOf("15") }
    var multiplierStr     by remember { mutableStateOf("600") }
    var tuwtMultiplierStr by remember { mutableStateOf("10") }

    val isDark = isSystemInDarkTheme()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ကော်မရှင် အသစ် ထည့်သွင်းရန်",
                        color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) MaterialTheme.colorScheme.surface else EmeraldPrimary
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("မလုပ်တော့ပါ") }

                Button(
                    onClick = {
                        val rate = (commissionStr.toDoubleOrNull() ?: 0.0) / 100.0
                        val mult = multiplierStr.toIntOrNull() ?: 600
                        val tuwt = tuwtMultiplierStr.toIntOrNull() ?: 10
                        viewModel.addCustomer(name, rate, mult, tuwt)
                        onBack()
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("ထည့်မည်", fontWeight = FontWeight.Bold) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CustomerFormField(label = "အမှတ်စဉ်",           value = "$nextId",          readOnly = true)  { }
            CustomerFormField(label = "အမည်",              value = name)                { name = it }
            CustomerFormField(label = "ကော်မရှင်ခ (%)",  value = commissionStr)       { commissionStr = it }
            CustomerFormField(label = "ဒဲ့ အဆ",           value = multiplierStr)       { multiplierStr = it }
            CustomerFormField(label = "တွတ် အဆ",          value = tuwtMultiplierStr)   { tuwtMultiplierStr = it }
        }
    }
}

// ── Edit Customer Screen ──────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCustomerFullScreen(
    viewModel   : MainViewModel,
    customer    : Customer,
    currentBatch: Int = viewModel.currentBatch.value,
    onBack      : () -> Unit
) {
    BackHandler(onBack = onBack)

    var name          by remember { mutableStateOf(customer.name) }
    var commissionStr by remember { 
        val p = if (customer.commissionRate > 1.0) customer.commissionRate else customer.commissionRate * 100
        mutableStateOf(p.toInt().toString()) 
    }
    var multiplierStr     by remember { mutableStateOf(customer.multiplier.toString()) }
    var tuwtMultiplierStr by remember { mutableStateOf(customer.tuwtMultiplier.toString()) }
    var paidStr       by remember(customer.id, currentBatch) { 
        val batchPaid = viewModel.getPaidForBatch(customer.id, currentBatch)
        val initialPaid = if (batchPaid > 0) batchPaid else customer.paidAmount
        mutableStateOf(initialPaid.toInt().toString()) 
    }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("ကော်မရှင် ဖျက်မည်") },
            text  = {
                Text("${customer.name} ကို အပြီးတိုင် ဖျက်မည်လား? \nဤလုပ်ဆောင်ချက်ကို ပြန်မပြင်နိုင်ပါ။")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(customer)
                        showDeleteDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("ဖျက်မည်") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("မဖျက်တော့ပါ")
                }
            }
        )
    }

    val isDark = isSystemInDarkTheme()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "ကော်မရှင် ပြင်ဆင်ရန်",
                        color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) MaterialTheme.colorScheme.surface else EmeraldPrimary
                ),
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            Icons.Default.Delete,
                            "ဖျက်မည်",
                            tint = if (isDark) MaterialTheme.colorScheme.error else Color.White
                        )
                    }
                }
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("မလုပ်တော့ပါ") }

                Button(
                    onClick = {
                        val rate = (commissionStr.toDoubleOrNull() ?: 0.0) / 100.0
                        val mult = multiplierStr.toIntOrNull() ?: 600
                        val tuwt = tuwtMultiplierStr.toIntOrNull() ?: 10
                        val paid = paidStr.toDoubleOrNull() ?: 0.0
                        viewModel.setPaidForBatch(customer.id, currentBatch, paid)
                        viewModel.updateCustomer(
                            customer.copy(
                                name = name,
                                commissionRate = rate,
                                multiplier = mult,
                                tuwtMultiplier = tuwt,
                                paidAmount = paid
                            )
                        )
                        onBack()
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) { Text("ပြင်ဆင်မည်", fontWeight = FontWeight.Bold) }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CustomerFormField(label = "အမှတ်စဉ်",        value = "${customer.id}", readOnly = true) { }
            CustomerFormField(label = "အမည်",             value = name)             { name = it }
            CustomerFormField(label = "ကော်မရှင်ခ (%)", value = commissionStr)    { commissionStr = it }
            CustomerFormField(label = "ဒဲ့ အဆ",           value = multiplierStr)    { multiplierStr = it }
            CustomerFormField(label = "တွတ် အဆ",          value = tuwtMultiplierStr) { tuwtMultiplierStr = it }
            CustomerFormField(label = "ပေးငွေ",           value = paidStr)          { paidStr = it }
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
        shape         = RoundedCornerShape(12.dp),
        colors        = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            focusedLabelColor = MaterialTheme.colorScheme.primary,
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier      = Modifier.fillMaxWidth()
    )
}

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

// ── Polished AgentNumbersView ─────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentNumbersView(
    customer    : Customer,
    allVWB      : List<com.threeDLedger.data.VoucherWithBets>,
    currentBatch: Int,
    totalAmount : Int,
    commCut     : Int,
    netAmount   : Int,
    paidAmount  : Int = 0,
    onBack      : () -> Unit
) {
    BackHandler(onBack = onBack)

    val commPct = if (customer.commissionRate > 1.0) customer.commissionRate.toInt() else (customer.commissionRate * 100).toInt()

    val batchVouchers = remember(allVWB, customer.id, currentBatch) {
        allVWB.filter { 
            it.voucher.batchNumber == currentBatch &&
            it.voucher.customerId == customer.id && 
            !it.voucher.isArchived &&
            !it.voucher.remark.contains("တင်ကွက်") && 
            !it.voucher.remark.contains("overflow", ignoreCase = true) 
        }
    }

    val numberTotals: List<Pair<String, Int>> = remember(batchVouchers) {
        val map = mutableMapOf<String, Int>()
        batchVouchers
            .flatMap { it.bets }
            .forEach { bet -> map[bet.number] = (map[bet.number] ?: 0) + bet.amount }
        map.entries
            .sortedBy { it.key.toIntOrNull() ?: 0 }
            .map { it.key to it.value }
    }

    val isDark = isSystemInDarkTheme()
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            customer.name,
                            color = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Text(
                            "အကြိမ်: #$currentBatch  |  ကော်မရှင်: $commPct%  |  ဘောင်ချာ: ${batchVouchers.size} စောင်",
                            color = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "Back",
                            tint = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = if (isDark) MaterialTheme.colorScheme.surface else EmeraldPrimary)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isDark) MaterialTheme.colorScheme.surface else EmeraldPrimary,
                border = if (isDark) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                shadowElevation = 8.dp
            ) {
                val barTextColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color.White
                val barTextSubColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.85f)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("စုစုပေါင်း", color = barTextSubColor, fontSize = 13.sp)
                        Text(
                            "%,d Ks".format(totalAmount),
                            color = barTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ကော်မရှင် ($commPct%)", color = barTextSubColor, fontSize = 13.sp)
                        Text(
                            "%,d Ks".format(commCut),
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ပေးငွေ", color = barTextSubColor, fontSize = 13.sp)
                        Text(
                            "%,d Ks".format(paidAmount),
                            color = barTextSubColor,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    HorizontalDivider(
                        color = if (isDark) MaterialTheme.colorScheme.outlineVariant else Color.White.copy(alpha = 0.25f),
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    val isToGet = netAmount >= 0
                    val netColor = if (isToGet) {
                        if (isDark) MaterialTheme.colorScheme.primary else Color.White
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                    val netLabel = if (isToGet) "နှုတ်ပြီးငွေ (ရရန်)" else "နှုတ်ပြီးငွေ (ပေးရန်)"
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            netLabel,
                            color = barTextColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "%,d Ks".format(netAmount),
                            color = netColor,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Table header
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        "စဉ်   ဂဏန်း",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "ထိုးငွေ ပမာဏ",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.End
                    )
                }
            }

            if (numberTotals.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "ထိုးမှု မရှိသေးပါ",
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    itemsIndexed(numberTotals) { idx, (number, amount) ->
                        val isEven = idx % 2 == 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isEven) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${idx + 1}.   $number",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                "%,d Ks".format(amount),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.End
                            )
                        }
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}
