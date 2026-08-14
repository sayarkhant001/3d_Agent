package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.Bet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WinnerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    var winningNumber by remember { mutableStateOf("") }
    var exactMultiplier by remember { mutableStateOf("600") }
    var permutationMultiplier by remember { mutableStateOf("10") }
    
    val allBets by viewModel.allBets.collectAsStateWithLifecycle()
    val allVouchers by viewModel.vouchersWithCustomer.collectAsStateWithLifecycle()
    val allCustomers by viewModel.customers.collectAsStateWithLifecycle()
    
    var results by remember { mutableStateOf<List<WinnerResult>>(emptyList()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ပေါက်ဂဏန်း စာရင်း", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = winningNumber,
                onValueChange = { winningNumber = it },
                label = { Text("ပေါက်ဂဏန်း (Winning Number)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = exactMultiplier,
                    onValueChange = { exactMultiplier = it },
                    label = { Text("တိုက်ရိုက် အဆ (Exact)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = permutationMultiplier,
                    onValueChange = { permutationMultiplier = it },
                    label = { Text("ခွေ/အနီး အဆ (Perm/Near)") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (winningNumber.length == 3) {
                        val exact = exactMultiplier.toDoubleOrNull() ?: 0.0
                        val perm = permutationMultiplier.toDoubleOrNull() ?: 0.0
                        
                        val permutations = com.example.logic.NumberGenerator.permutations(winningNumber).toSet()
                        val numInt = winningNumber.toIntOrNull() ?: 0
                        
                        val minus1 = String.format("%03d", if (numInt == 0) 999 else numInt - 1)
                        val plus1 = String.format("%03d", if (numInt == 999) 0 else numInt + 1)
                        
                        val adjacent = setOf(minus1, plus1)
                        
                        val winningBets = mutableListOf<WinnerResult>()
                        
                        allBets.forEach { bet ->
                            var winAmount = 0.0
                            if (bet.number == winningNumber) {
                                winAmount = bet.amount * exact
                            } else if (permutations.contains(bet.number) || adjacent.contains(bet.number)) {
                                winAmount = bet.amount * perm
                            }
                            
                            if (winAmount > 0) {
                                val voucher = allVouchers.find { it.voucher.id == bet.voucherId }
                                val customer = allCustomers.find { it.id == voucher?.customer?.id }
                                if (voucher != null && customer != null) {
                                    winningBets.add(WinnerResult(customer.name, bet.number, bet.amount, winAmount))
                                }
                            }
                        }
                        
                        results = winningBets.sortedBy { it.customerName }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("တွက်ချက်မည် (Calculate)")
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn {
                items(results) { result ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("အမည်: ${result.customerName}", fontWeight = FontWeight.Bold)
                            Text("ဂဏန်း: ${result.betNumber} (ပမာဏ: ${result.betAmount})")
                            Text("ရငွေ: ${result.payoutAmount}", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

data class WinnerResult(val customerName: String, val betNumber: String, val betAmount: Int, val payoutAmount: Double)
