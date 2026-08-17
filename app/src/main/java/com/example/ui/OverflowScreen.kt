package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverflowScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToExportHistory: () -> Unit = {}
) {
    val ledgerExposures by viewModel.ledgerExposures.collectAsStateWithLifecycle()
    val currentBatch by viewModel.currentBatch.collectAsStateWithLifecycle()
    val brakeLimit by viewModel.brakeLimit.collectAsStateWithLifecycle()
    
    // Sort exposures
    val overflowExposures = ledgerExposures.filter { it.overflowAmount > 0 }.sortedByDescending { it.overflowAmount }
    val allExposures = ledgerExposures.filter { it.totalBetAmount > 0 }.sortedByDescending { it.totalBetAmount }

    val totalAll = allExposures.sumOf { it.totalBetAmount }
    val totalOverflow = overflowExposures.sumOf { it.overflowAmount }

    val orangeColor = MaterialTheme.colorScheme.tertiary
    val blueColor = MaterialTheme.colorScheme.primary
    
    var showBrakeDialog by remember { mutableStateOf(false) }

    if (showBrakeDialog) {
        var brakeInput by remember { mutableStateOf(brakeLimit.toString()) }
        AlertDialog(
            onDismissRequest = { showBrakeDialog = false },
            title = { Text("ဘရိတ် ပြောင်းရန်") },
            text = {
                OutlinedTextField(
                    value = brakeInput,
                    onValueChange = { brakeInput = it },
                    label = { Text("ဘရိတ် (Brake Limit)") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    brakeInput.toIntOrNull()?.let { viewModel.brakeLimit.value = it }
                    showBrakeDialog = false
                }) { Text("သိမ်းမည်") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("တင်ကွက်များ", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = blueColor)
            )
        },
        bottomBar = {
            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onNavigateToExportHistory,
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.AutoMirrored.Filled.List, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("မှတ်တမ်းများ", color = MaterialTheme.colorScheme.onPrimary)
                }
                Button(
                    onClick = { 
                        viewModel.exportOverflow()
                        onNavigateToExportHistory() 
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = blueColor),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("တင်မည်", color = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().background(blueColor).padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("အကြိမ် : $currentBatch", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp)
                Spacer(modifier = Modifier.width(32.dp))
                Text("ဘရိတ် : $brakeLimit", color = MaterialTheme.colorScheme.onPrimary, fontSize = 16.sp, modifier = Modifier.clickable { showBrakeDialog = true }.padding(4.dp))
            }

            // Tables
            Row(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                // Left Table
                Column(modifier = Modifier.weight(1f).border(1.dp, orangeColor).padding(2.dp)) {
                    // Header
                    Row(modifier = Modifier.fillMaxWidth().background(orangeColor).padding(8.dp)) {
                        Text("ဂဏန်းများ", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("ပမာဏ", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                    LazyColumn(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.onPrimary)) {
                        items(allExposures) { exposure ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(exposure.number, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("${exposure.totalBetAmount}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            }
                            Divider(color = Color.LightGray, thickness = 0.5.dp)
                        }
                    }
                    // Footer
                    Row(modifier = Modifier.fillMaxWidth().background(orangeColor).padding(8.dp)) {
                        Text("စုစုပေါင်း", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("$totalAll", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Right Table
                Column(modifier = Modifier.weight(1f).border(1.dp, orangeColor).padding(2.dp)) {
                    // Header
                    Row(modifier = Modifier.fillMaxWidth().background(orangeColor).padding(8.dp)) {
                        Text("ဂဏန်းများ", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("ပမာဏ", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                    LazyColumn(modifier = Modifier.weight(1f).background(MaterialTheme.colorScheme.onPrimary)) {
                        items(overflowExposures) { exposure ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                                Text(exposure.number, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                Text("${exposure.overflowAmount}", modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                            }
                            Divider(color = Color.LightGray, thickness = 0.5.dp)
                        }
                    }
                    // Footer
                    Row(modifier = Modifier.fillMaxWidth().background(orangeColor).padding(8.dp)) {
                        Text("စုစုပေါင်း", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        Text("$totalOverflow", color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}
