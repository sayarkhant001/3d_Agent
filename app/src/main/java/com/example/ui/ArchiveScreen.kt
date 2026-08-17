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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val archivedVouchers by viewModel.archivedVouchers.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("မှတ်တမ်းဟောင်းများ (Archive)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary, titleContentColor = MaterialTheme.colorScheme.onPrimary, navigationIconContentColor = MaterialTheme.colorScheme.onPrimary)
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            items(archivedVouchers) { item ->
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Batch: ${item.voucher.batchNumber}", fontWeight = FontWeight.Bold)
                        Text("Customer: ${item.customer.name}")
                        Text("Date: ${item.voucher.date} ${item.voucher.time}")
                        Text("Total Amount: ${item.voucher.totalAmount} Ks", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
