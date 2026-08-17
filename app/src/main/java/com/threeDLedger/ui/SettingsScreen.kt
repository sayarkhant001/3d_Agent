package com.threeDLedger.ui
import android.annotation.SuppressLint
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
// Removed duplicate import


import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.DialogProperties
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToWinner: () -> Unit
) {
    var showBannedDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showPrinterDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ဆက်တင်", color = MaterialTheme.colorScheme.onPrimary) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            
            SettingsItem(icon = Icons.Default.Block, text = "မရဂဏန်းများ", onClick = { showBannedDialog = true })
            SettingsItem(icon = Icons.Default.History, text = "မှတ်တမ်းများ (Archive)", onClick = onNavigateToArchive)
            SettingsItem(icon = Icons.Default.Star, text = "ထွက်ဂဏန်းများ (Winner)", onClick = onNavigateToWinner)
            SettingsItem(icon = Icons.Default.Print, text = "Printer Settings", onClick = { showPrinterDialog = true })
            SettingsItem(icon = Icons.Default.Lock, text = "Change Password", onClick = { showPasswordDialog = true })
            SettingsItem(icon = Icons.Default.Delete, text = "Reset", onClick = { showResetDialog = true })

            Spacer(modifier = Modifier.height(24.dp))

            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("အာမခံပေးသူ =", modifier = Modifier.padding(end = 8.dp))
                    OutlinedTextField(
                        value = viewModel.voucherFooterText.collectAsStateWithLifecycle().value,
                        onValueChange = { viewModel.updateVoucherFooterText(it) },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }
            }
        }
    }

    if (showBannedDialog) {
        BannedNumbersDialog(viewModel, onDismiss = { showBannedDialog = false })
    }
    if (showPasswordDialog) {
        ChangePasswordDialog(viewModel, onDismiss = { showPasswordDialog = false })
    }
    if (showResetDialog) {
        ResetDialog(viewModel, onDismiss = { showResetDialog = false })
    }
    if (showPrinterDialog) {
        PrinterSettingsDialog(viewModel, onDismiss = { showPrinterDialog = false })
    }
}

@Composable
fun SettingsItem(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(32.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, fontSize = 18.sp)
    }
}

@Composable
fun BannedNumbersDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var newNumber by remember { mutableStateOf("") }
    val bannedNumbers by viewModel.bannedNumbers.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("မရဂဏန်းများ") },
        text = {
            Column(modifier = Modifier.heightIn(max = 300.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newNumber,
                        onValueChange = { newNumber = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("ဂဏန်းထည့်ပါ") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { 
                        if(newNumber.isNotEmpty()) {
                            viewModel.addBannedNumber(newNumber)
                            newNumber = ""
                        }
                    }) {
                        Text("Add")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn {
                    items(bannedNumbers) { banned ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(banned.number, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.deleteBannedNumber(banned) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}

@Composable
fun ChangePasswordDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val currentPassword by viewModel.appPassword.collectAsStateWithLifecycle()
    var password by remember { mutableStateOf(currentPassword) }
    var confirmPassword by remember { mutableStateOf(currentPassword) }
    var errorMsg by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("စကားဝှက် ပြောင်းမည်") },
        text = {
            Column {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMsg = "" },
                    label = { Text("စကားဝှက် အသစ် (ဖျက်ရန် အလွတ်ထားပါ)") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; errorMsg = "" },
                    label = { Text("စကားဝှက် အသစ် (ထပ်ရိုက်ပါ)") },
                    singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                )
                if (errorMsg.isNotEmpty()) {
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (password != confirmPassword) {
                    errorMsg = "စကားဝှက်များ မတူညီပါ"
                } else {
                    viewModel.updateAppPassword(password)
                    onDismiss()
                }
            }) { Text("သိမ်းမည်") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") }
        }
    )
}


@Composable
fun ResetDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Data") },
        text = { Text("This will archive current data and delete archives older than 2 batches. Only commissions will remain active. Are you sure?") },
        confirmButton = {
            TextButton(onClick = { viewModel.resetAndArchive(); onDismiss() }) { Text("Yes, Reset") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
@SuppressLint("MissingPermission")
@Composable
fun PrinterSettingsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    
    var macAddress by remember { mutableStateOf(prefs.getString("printerMac", "") ?: "") }
    var paperSize by remember { mutableStateOf(prefs.getString("paperSize", "58mm") ?: "58mm") }
    
    var pairedDevices by remember { mutableStateOf<List<android.bluetooth.BluetoothDevice>>(emptyList()) }
    var isConnecting by remember { mutableStateOf(false) }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            try { pairedDevices = com.threeDLedger.logic.BluetoothPrinter.getPairedDevices() } catch (e: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val connectStatus = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
            val scanStatus = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN)
            if (connectStatus == android.content.pm.PackageManager.PERMISSION_GRANTED && scanStatus == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                try { pairedDevices = com.threeDLedger.logic.BluetoothPrinter.getPairedDevices() } catch (e: Exception) {}
            } else {
                permissionLauncher.launch(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN))
            }
        } else {
            try { pairedDevices = com.threeDLedger.logic.BluetoothPrinter.getPairedDevices() } catch (e: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ပရင်တာ ဆက်တင်") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("စက္ကူ အရွယ်အစား ရွေးပါ", fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { paperSize = "58mm" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (paperSize == "58mm") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    ) { Text("58mm") }
                    Button(
                        onClick = { paperSize = "80mm" },
                        colors = ButtonDefaults.buttonColors(containerColor = if (paperSize == "80mm") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                    ) { Text("80mm") }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text("Bluetooth ပရင်တာ ရွေးပါ", fontWeight = FontWeight.Bold)
                if (pairedDevices.isEmpty()) {
                    Text("တွဲထားသော (Paired) ပရင်တာ မတွေ့ပါ", color = MaterialTheme.colorScheme.error)
                } else {
                    LazyColumn(modifier = Modifier.height(200.dp)) {
                        items(pairedDevices.size) { i ->
                            val device = pairedDevices[i]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { macAddress = device.address }
                                    .padding(8.dp)
                                    .background(if (macAddress == device.address) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(device.name ?: "Unknown", fontWeight = FontWeight.Bold)
                                    Text(device.address, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        isConnecting = true
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            val success = com.threeDLedger.logic.BluetoothPrinter.connect(macAddress)
                            isConnecting = false
                            android.widget.Toast.makeText(context, if (success) "ချိတ်ဆက်မှု အောင်မြင်ပါသည်" else "ချိတ်ဆက်မှု မအောင်မြင်ပါ", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = macAddress.isNotEmpty() && !isConnecting
                ) {
                    Text(if (isConnecting) "ချိတ်ဆက်နေသည်..." else "စမ်းသပ် ချိတ်ဆက်မည်")
                }
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                            val textToPrint = "========================\n      3D VOUCHER\n========================\n Test Print Successful\n========================"
                            val width = if (paperSize == "80mm") 576 else 384
                            val bitmap = com.threeDLedger.logic.BluetoothPrinter.createBitmapFromText(textToPrint, width)
                            val success = com.threeDLedger.logic.BluetoothPrinter.printBitmap(bitmap, paperSize)
                            android.widget.Toast.makeText(context, if (success) "ပရင်ထုတ်ခြင်း အောင်မြင်ပါသည်" else "ပရင်ထုတ်ခြင်း မအောင်မြင်ပါ", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = macAddress.isNotEmpty()
                ) {
                    Text("စမ်းသပ် ပရင်ထုတ်မည်")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.edit().putString("printerMac", macAddress).putString("paperSize", paperSize).apply()
                onDismiss()
            }) { Text("သိမ်းမည်") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") }
        }
    )
}
