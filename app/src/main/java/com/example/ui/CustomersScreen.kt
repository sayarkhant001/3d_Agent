package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val allVouchers by viewModel.vouchersWithCustomer.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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
                    val netAmount = totalAmount - commissionCut
                    
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "ကော် အိုင်ဒီ: ${customer.id}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(text = "အမည်: ${customer.name}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Divider()
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "စုစုပေါင်း: $totalAmount", style = MaterialTheme.typography.bodyMedium)
                                Text(text = "ကော် (${customer.commissionRate * 100}%): $commissionCut", style = MaterialTheme.typography.bodyMedium)
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

        if (showAddDialog) {
            var name by remember { mutableStateOf("") }
            var commissionStr by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showAddDialog = false },
                title = { Text("ကော်မရှင် အသစ်ထည့်ရန်") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("အမည် (Name)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = commissionStr,
                            onValueChange = { commissionStr = it },
                            label = { Text("ကော်မရှင် (e.g. 0.10 for 10%)") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val rate = commissionStr.toDoubleOrNull() ?: 0.0
                        viewModel.addCustomer(name, rate)
                        showAddDialog = false
                    }) {
                        Text("သိမ်းမည်")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showAddDialog = false }) {
                        Text("ပယ်ဖျက်မည်")
                    }
                }
            )
        }
    }
}
