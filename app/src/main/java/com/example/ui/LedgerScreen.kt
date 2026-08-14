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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val ledgerExposures by viewModel.ledgerExposures.collectAsStateWithLifecycle()
    val currentBatch by viewModel.currentBatch.collectAsStateWithLifecycle()
    val brakeLimit by viewModel.brakeLimit.collectAsStateWithLifecycle()
    var brakeInput by remember { mutableStateOf(brakeLimit.toString()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဂဏန်းများ", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            // Header with Brake input
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("အကြိမ် : $currentBatch", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = brakeInput,
                        onValueChange = { 
                            brakeInput = it
                            it.toIntOrNull()?.let { limit -> viewModel.brakeLimit.value = limit }
                        },
                        label = { Text("ဘရိတ် (Brake Limit)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Export Actions
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { viewModel.exportOverflow() }, modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                    Text("ကျော်ငွေ လွှဲရန်")
                }
                Button(onClick = { viewModel.exportUnderBrake() }, modifier = Modifier.weight(1f).padding(start = 4.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                    Text("အောက်ငွေ လွှဲရန်")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ဂဏန်း", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("စုစုပေါင်း", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                Text("လက်ကျန်", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                Text("ကျော်ငွေ", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
            }
            Divider()
            LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                items(ledgerExposures) { exposure ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(exposure.number, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        Text("${exposure.totalBetAmount}", modifier = Modifier.weight(1.5f))
                        Text("${exposure.netHeldAmount}", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.5f))
                        Text("${exposure.overflowAmount}", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f))
                    }
                    Divider()
                }
            }
        }
    }
}
