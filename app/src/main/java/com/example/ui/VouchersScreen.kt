package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VouchersScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val vouchers by viewModel.vouchersWithCustomer.collectAsStateWithLifecycle()
    val allVouchersWithBets by viewModel.vouchersWithBets.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဘောင်ချာများ", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            items(allVouchersWithBets) { voucherWithBets ->
                val customerName = vouchers.find { it.voucher.id == voucherWithBets.voucher.id }?.customer?.name ?: "Unknown"
                
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("No.${voucherWithBets.voucher.id} (အကြိမ်: ${voucherWithBets.voucher.batchNumber})", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text("အမည်: $customerName")
                        
                        val dateString = SimpleDateFormat("yyyy.MM.dd/HH:mm:ss").format(Date(voucherWithBets.voucher.timestamp))
                        Text("အချိန်: $dateString", style = MaterialTheme.typography.bodySmall)
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Column(modifier = Modifier.fillMaxWidth()) {
                            voucherWithBets.bets.forEach { bet ->
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(bet.number)
                                    Text("= ${bet.amount}")
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Divider()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("စုစုပေါင်း: ${voucherWithBets.voucher.totalAmount} Ks", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
