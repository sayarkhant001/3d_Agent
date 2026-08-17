#!/bin/bash
cat << 'INNEREOF' > app/src/main/java/com/example/ui/HomeScreen.kt
package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MenuItem(val title: String, val icon: ImageVector, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigateToCustomers: () -> Unit,
    onNavigateToBetting: () -> Unit,
    onNavigateToWinner: () -> Unit,
    onNavigateToLedger: () -> Unit,
    onNavigateToVouchers: () -> Unit,
    onNavigateToReceipt: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToOverflow: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    val currentDate = dateFormat.format(Date())
    val currentBatch by viewModel.currentBatch.collectAsStateWithLifecycle()
    val bannedNumbers by viewModel.bannedNumbers.collectAsStateWithLifecycle()

    val menuItems = listOf(
        MenuItem("ကော်မရှင်", Icons.Default.People, onNavigateToCustomers),
        MenuItem("ဂဏန်းများ", Icons.AutoMirrored.Filled.List, onNavigateToLedger),
        MenuItem("တင်ကွက်များ", Icons.Default.Payment, onNavigateToOverflow),
        MenuItem("ဘောင်ချာ", Icons.Default.Receipt, onNavigateToVouchers),
        MenuItem("ပေါက်ဂဏန်း", Icons.Default.Calculate, onNavigateToWinner),
        MenuItem("မှတ်တမ်း", Icons.Default.History, onNavigateToArchive)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("3D စာရင်း", fontWeight = FontWeight.Bold)
                        Text(currentDate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 800.dp)
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                // Batch Selector Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("အကြိမ် ရွေးပါ (Batch No)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        var batchText by remember { mutableStateOf(currentBatch.toString()) }
                        LaunchedEffect(currentBatch) {
                            batchText = currentBatch.toString()
                        }
                        OutlinedTextField(
                            value = batchText,
                            onValueChange = { newText ->
                                batchText = newText
                                newText.toIntOrNull()?.let { num ->
                                    if (num in 1..1000) viewModel.currentBatch.value = num
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp).height(56.dp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (bannedNumbers.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Text(
                            text = "ပိတ်ထားသော ဂဏန်းများ: ${bannedNumbers.joinToString(", ") { it.number }}",
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Grid Menu
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) {
                    items(menuItems) { item ->
                        MenuButton(title = item.title, icon = item.icon, onClick = item.onClick)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun MenuButton(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.aspectRatio(1f).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
INNEREOF
