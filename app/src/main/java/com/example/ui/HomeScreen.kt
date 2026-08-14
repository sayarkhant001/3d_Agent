package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    onNavigateToExportHistory: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
    val currentDate = dateFormat.format(Date())
    val currentBatch by viewModel.currentBatch.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("3D စာရင်း ဆော့ဝဲလ်", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ယနေ့ - $currentDate",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MenuButton(
                    title = "ကော်မရှင်",
                    icon = Icons.Default.People,
                    onClick = onNavigateToCustomers
                )
                MenuButton(
                    title = "ဂဏန်းများ",
                    icon = Icons.Default.List,
                    onClick = onNavigateToLedger
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MenuButton(
                    title = "ဘောင်ချာ",
                    icon = Icons.Default.Receipt,
                    onClick = onNavigateToVouchers
                )
                MenuButton(
                    title = "တင်ကွက်",
                    icon = Icons.Default.PostAdd,
                    onClick = onNavigateToBetting
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MenuButton(
                    title = "ပေါက်ဂဏန်း",
                    icon = Icons.Default.Calculate,
                    onClick = onNavigateToWinner
                )
                MenuButton(
                    title = "မှတ်တမ်း",
                    icon = Icons.Default.History,
                    onClick = onNavigateToExportHistory
                )
            }
            
            Spacer(modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                Text("အကြိမ် ရွေးပါ (Batch) - ", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = "$currentBatch",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().width(120.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        (1..24).forEach { batch ->
                            DropdownMenuItem(
                                text = { Text("$batch") },
                                onClick = {
                                    viewModel.currentBatch.value = batch
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MenuButton(title: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.size(140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
