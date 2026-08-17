package com.threeDLedger.ui
import androidx.compose.material.icons.filled.Edit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToBetting: (Int) -> Unit
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val allVouchers by viewModel.vouchersWithCustomer.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<com.threeDLedger.data.Customer?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    if (editingCustomer != null) {
        EditCustomerFullScreen(viewModel, editingCustomer!!) { editingCustomer = null }
        return
    }

    if (showAddDialog) {
        val nextId = (customers.maxOfOrNull { it.id } ?: 0) + 1
        AddCustomerFullScreen(viewModel, nextId) { showAddDialog = false }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ကော်မရှင်များ", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Customer")
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
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                val filteredCustomers = customers.filter { it.name.contains(searchQuery, ignoreCase = true) }
                items(filteredCustomers) { customer ->
                    
                    val customerVouchers = allVouchers.filter { it.customer.id == customer.id }
                    val totalAmount = customerVouchers.sumOf { it.voucher.totalAmount }
                    val commissionCut = totalAmount * customer.commissionRate
                    val netAmount = totalAmount - commissionCut - customer.paidAmount
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { editingCustomer = customer },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = "ကော် အိုင်ဒီ: ${customer.id}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                    Text(text = "အမည်: ${customer.name}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                                IconButton(onClick = { onNavigateToBetting(customer.id) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Bet", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "စုစုပေါင်း: $totalAmount", style = MaterialTheme.typography.bodyMedium)
                                Text(text = "ကော် (${(customer.commissionRate * 100).toInt()}%): $commissionCut", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "ဘောင်ချာ: ${customerVouchers.size}", style = MaterialTheme.typography.bodyMedium)
                                Text(text = "ပေးငွေ: ${customer.paidAmount}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "နှုတ်ပြီးငွေ: $netAmount", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomerFullScreen(
    viewModel: MainViewModel,
    nextId: Int,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var commissionStr by remember { mutableStateOf("") }
    var multiplierStr by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ကော်မရှင် အသစ် ထည့်မယ်", color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("မလုပ်တော့ပါ", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
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
                ) {
                    Text("ထည့်မည်", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Orange Banner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .background(Color(0xFFFFA000), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .background(Color(0xFFFFF3E0), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(100.dp),
                            tint = Color(0xFFFFA000)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.Gray, shape = CircleShape)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Teal Form
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormRow("ကော် အိုင်ဒီ:", "$nextId", readOnly = true) {}
                    FormRow("ကော် အမည်:", name, placeholder = "ရိုက်ထည့်ပါ", onValueChange = { name = it })
                    FormRow("ကော်မရှင်ခ:", commissionStr, placeholder = "00", onValueChange = { commissionStr = it })
                    FormRow("အဆ:", multiplierStr, placeholder = "000", onValueChange = { multiplierStr = it })
                }
            }
        }
    }
}

@Composable
fun FormRow(label: String, value: String, readOnly: Boolean = false, placeholder: String = "", onValueChange: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1.2f))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            readOnly = readOnly,
            textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface),
            modifier = Modifier
                .weight(2.8f)
                .background(Color.White, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 12.dp),
            decorationBox = { innerTextField ->
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(text = placeholder, color = Color.Gray)
                }
                innerTextField()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCustomerFullScreen(
    viewModel: MainViewModel,
    customer: com.threeDLedger.data.Customer,
    onBack: () -> Unit
) {
    var name by remember { mutableStateOf(customer.name) }
    var commissionStr by remember { mutableStateOf((customer.commissionRate * 100).toInt().toString()) }
    var multiplierStr by remember { mutableStateOf(customer.multiplier.toString()) }
    var paidAmountStr by remember { mutableStateOf(customer.paidAmount.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ကော်မရှင် ပြင်ဆင်မယ်", color = MaterialTheme.colorScheme.onSurface) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onBack,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("မလုပ်တော့ပါ", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Button(
                    onClick = {
                        val rate = (commissionStr.toDoubleOrNull() ?: 0.0) / 100.0
                        val mult = multiplierStr.toIntOrNull() ?: 80
                        val paid = paidAmountStr.toDoubleOrNull() ?: 0.0
                        viewModel.updateCustomer(customer.copy(name = name, commissionRate = rate, multiplier = mult, paidAmount = paid))
                        onBack()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text("ပြင်ဆင်မည်", color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FormRow("ကော် အိုင်ဒီ:", "${customer.id}", readOnly = true) {}
                    FormRow("ကော် အမည်:", name, placeholder = "ရိုက်ထည့်ပါ", onValueChange = { name = it })
                    FormRow("ကော်မရှင်ခ:", commissionStr, placeholder = "00", onValueChange = { commissionStr = it })
                    FormRow("အဆ:", multiplierStr, placeholder = "000", onValueChange = { multiplierStr = it })
                    FormRow("ပေးငွေ (Paid Amount):", paidAmountStr, placeholder = "0.0", onValueChange = { paidAmountStr = it })
                }
            }
        }
    }
}
