package com.threeDLedger.ui

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.utils.GitHubUpdater
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToArchive: () -> Unit,
    onNavigateToWinner: () -> Unit
) {
    var showBannedDialog     by remember { mutableStateOf(false) }
    var showPasswordDialog   by remember { mutableStateOf(false) }
    var showResetDialog      by remember { mutableStateOf(false) }
    var showPrinterDialog    by remember { mutableStateOf(false) }

    val context        = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var updateCheckStatus      by remember { mutableStateOf("") }
    var updateInfoManual       by remember { mutableStateOf<GitHubUpdater.UpdateInfo?>(null) }
    var showManualUpdateDialog by remember { mutableStateOf(false) }
    var showDownloadDialog     by remember { mutableStateOf(false) }
    var downloadProgress       by remember { mutableIntStateOf(0) }
    var downloadError          by remember { mutableStateOf<String?>(null) }
    var isInstalling           by remember { mutableStateOf(false) }

    val currentVersion = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?" }
        catch (e: PackageManager.NameNotFoundException) { "?" }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface    = MaterialTheme.colorScheme.onSurface
    val errorColor   = MaterialTheme.colorScheme.error

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ဆက်တင်", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimary)
                        Text("3D Ledger App", fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = primaryColor)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { SettingsSectionHeader("မီနူး", Icons.Default.Menu) }
            item {
                SettingsCard {
                    SettingsRow(Icons.Default.Block,    Color(0xFFFF6B6B), "မရဂဏန်းများ",         "Ban လုပ်ထားသော ဂဏန်းများ") { showBannedDialog = true }
                    SettingsDivider()
                    SettingsRow(Icons.Default.History,  Color(0xFF4ECDC4), "မှတ်တမ်းများ",         "Archive သမိုင်းများ",      onClick = onNavigateToArchive)
                    SettingsDivider()
                    SettingsRow(Icons.Default.Star,     Color(0xFFFFD93D), "ထွက်ဂဏန်းများ",        "Winner ဂဏန်းများ",         onClick = onNavigateToWinner)
                }
            }

            item { SettingsSectionHeader("ဖွဲ့စည်းပုံ", Icons.Default.Settings) }
            item {
                SettingsCard {
                    SettingsRow(Icons.Default.Print, Color(0xFF6C63FF), "Printer Settings", "Bluetooth ပရင်တာ ချိတ်ဆက်မည်") { showPrinterDialog = true }
                    SettingsDivider()
                    SettingsRow(Icons.Default.Lock,  Color(0xFF2EC4B6), "Change Password",  "စကားဝှက် ပြောင်းလဲမည်")         { showPasswordDialog = true }
                }
            }

            item { SettingsSectionHeader("ဘောင်ချာ", Icons.Default.Receipt) }
            item {
                SettingsCard {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF43AA8B)),
                                contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Edit, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("အာမခံပေးသူ", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = onSurface)
                                Text("ဘောင်ချာ အောက်ဆုံး ဖော်ပြမည့် နာမည်", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = viewModel.voucherFooterText.collectAsStateWithLifecycle().value,
                            onValueChange = { viewModel.updateVoucherFooterText(it) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("နာမည် ရိုက်ထည့်ပါ…") },
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                            )
                        )
                    }
                }
            }

            item { SettingsSectionHeader("App Update", Icons.Default.SystemUpdate) }
            item {
                UpdateCard(
                    currentVersion    = currentVersion,
                    updateCheckStatus = updateCheckStatus,
                    onCheckUpdate = {
                        updateCheckStatus = "checking"
                        coroutineScope.launch {
                            val info = GitHubUpdater.checkForUpdates("sayarkhant001", "3d_Agent")
                            if (info == null) {
                                updateCheckStatus = "error"
                            } else {
                                val fetchedRun = info.version.trimStart('v').split(".", "-")
                                    .lastOrNull { s -> s.all { c -> c.isDigit() } }?.toIntOrNull() ?: 0
                                val currentRun = currentVersion.trimStart('v').split(".", "-")
                                    .lastOrNull { s -> s.all { c -> c.isDigit() } }?.toIntOrNull() ?: 0
                                if (fetchedRun > currentRun) {
                                    updateInfoManual = info; updateCheckStatus = "available"; showManualUpdateDialog = true
                                } else { updateCheckStatus = "uptodate" }
                            }
                        }
                    }
                )
            }

            item { SettingsSectionHeader("Danger Zone", Icons.Default.Warning, headerColor = errorColor) }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = errorColor.copy(alpha = 0.08f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, errorColor.copy(alpha = 0.3f))
                ) {
                    SettingsRow(Icons.Default.Delete, errorColor, "Reset Data",
                        "Data အားလုံး ဖျက်ပြီး Archive သိမ်းမည်", trailingColor = errorColor) { showResetDialog = true }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (showBannedDialog)   BannedNumbersDialog(viewModel)  { showBannedDialog   = false }
    if (showPasswordDialog) ChangePasswordDialog(viewModel) { showPasswordDialog  = false }
    if (showResetDialog)    ResetDialog(viewModel)           { showResetDialog    = false }
    if (showPrinterDialog)  PrinterSettingsDialog(viewModel) { showPrinterDialog  = false }

    if (showManualUpdateDialog && updateInfoManual != null) {
        val info = updateInfoManual!!
        AlertDialog(
            onDismissRequest = { showManualUpdateDialog = false },
            icon = { Icon(Icons.Default.SystemUpdate, null, tint = primaryColor) },
            title = { Text("Update ${info.version} ရှိနေပါသည်", fontWeight = FontWeight.Bold) },
            text  = { Text("ယခု Download လုပ်ပြီး Install လုပ်မည်လား?") },
            confirmButton = {
                Button(onClick = {
                    showManualUpdateDialog = false
                    if (!GitHubUpdater.canInstallUnknownApps(context)) {
                        GitHubUpdater.openInstallUnknownAppsSettings(context)
                    } else {
                        showDownloadDialog = true; downloadProgress = 0; downloadError = null; isInstalling = false
                        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                            val apk = GitHubUpdater.downloadApkToCache(context, info.downloadUrl) { progress ->
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { downloadProgress = progress }
                            }
                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                if (apk != null) { downloadProgress = 100; kotlinx.coroutines.delay(400); isInstalling = true }
                                else { downloadError = "Download မအောင်မြင်ပါ။ Internet စစ်ဆေးပါ။" }
                            }
                            if (apk != null) {
                                kotlinx.coroutines.delay(200)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { GitHubUpdater.installApkFromCache(context, apk) }
                                kotlinx.coroutines.delay(1500)
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { isInstalling = false; showDownloadDialog = false }
                            }
                        }
                    }
                }) { Text("Install လုပ်မည်") }
            },
            dismissButton = { TextButton(onClick = { showManualUpdateDialog = false }) { Text("နောက်မှ") } }
        )
    }

    if (showDownloadDialog) {
        val animatedProgress by animateFloatAsState(targetValue = downloadProgress / 100f, label = "dl")
        AlertDialog(
            onDismissRequest = {},
            title = { Text(if (isInstalling) "Install လုပ်နေသည်…" else "Download လုပ်နေသည်…", fontWeight = FontWeight.Bold) },
            text = {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (downloadError != null) {
                        Text(downloadError!!, color = errorColor, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = { showDownloadDialog = false; downloadError = null }, Modifier.fillMaxWidth()) { Text("ပိတ်မည်") }
                    } else if (isInstalling) {
                        CircularProgressIndicator(Modifier.size(48.dp), color = primaryColor)
                        Text("Installer ဖွင့်နေသည်…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LinearProgressIndicator({ animatedProgress }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                            color = primaryColor, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                        Text("$downloadProgress%", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = primaryColor)
                        Text("ကျေးဇူးပြု၍ စောင့်ပါ…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {}
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector, headerColor: Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Icon(icon, null, tint = headerColor, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = headerColor, letterSpacing = 0.8.sp)
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(modifier = Modifier.weight(1f), color = headerColor.copy(alpha = 0.25f), thickness = 1.dp)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp), content = content)
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(start = 64.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
}

@Composable
private fun SettingsRow(
    icon: ImageVector, iconBg: Color, label: String, sublabel: String,
    trailingColor: Color = MaterialTheme.colorScheme.onSurfaceVariant, onClick: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(iconBg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(sublabel, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight, null, tint = trailingColor.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun UpdateCard(currentVersion: String, updateCheckStatus: String, onCheckUpdate: () -> Unit) {
    val statusColor = when (updateCheckStatus) {
        "available" -> Color(0xFF43AA8B); "error" -> MaterialTheme.colorScheme.error
        "uptodate"  -> Color(0xFF4ECDC4); else    -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF6C63FF)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.SystemUpdate, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Update စစ်ဆေးရန်", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                Text("လက်ရှိ Version : $currentVersion", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (updateCheckStatus.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).clip(CircleShape).background(statusColor))
                        Spacer(Modifier.width(5.dp))
                        Text(when (updateCheckStatus) {
                            "checking"  -> "စစ်ဆေးနေသည်..."
                            "uptodate"  -> "နောက်ဆုံး Version ဖြစ်နေပါသည်"
                            "available" -> "Update ရှိနေပါသည်! ↓ နှိပ်ပါ"
                            else        -> "Network error — Internet စစ်ဆေးပါ"
                        }, fontSize = 11.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Button(onClick = onCheckUpdate, enabled = updateCheckStatus != "checking",
                shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
                if (updateCheckStatus == "checking")
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                else Text("စစ်ဆေး", fontSize = 12.sp)
            }
        }
    }
}

// ── Dialogs ───────────────────────────────────────────────────────────────────

@Composable
fun BannedNumbersDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    var newNumber by remember { mutableStateOf("") }
    val bannedNumbers by viewModel.bannedNumbers.collectAsStateWithLifecycle()
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("မရဂဏန်းများ", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 300.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = newNumber, onValueChange = { newNumber = it },
                        modifier = Modifier.weight(1f), label = { Text("ဂဏန်းထည့်ပါ") },
                        shape = RoundedCornerShape(12.dp), singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { if (newNumber.isNotEmpty()) { viewModel.addBannedNumber(newNumber); newNumber = "" } },
                        shape = RoundedCornerShape(10.dp)) { Text("ထည့်") }
                }
                Spacer(Modifier.height(8.dp))
                LazyColumn {
                    items(bannedNumbers) { banned ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(banned.number, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.deleteBannedNumber(banned) }) {
                                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
fun ChangePasswordDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val currentPassword by viewModel.appPassword.collectAsStateWithLifecycle()
    var password        by remember { mutableStateOf(currentPassword) }
    var confirmPassword by remember { mutableStateOf(currentPassword) }
    var errorMsg        by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("စကားဝှက် ပြောင်းမည်", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(value = password, onValueChange = { password = it; errorMsg = "" },
                    label = { Text("စကားဝှက် အသစ် (ဖျက်ရန် အလွတ်ထားပါ)") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(value = confirmPassword, onValueChange = { confirmPassword = it; errorMsg = "" },
                    label = { Text("စကားဝှက် အသစ် (ထပ်ရိုက်ပါ)") }, singleLine = true,
                    shape = RoundedCornerShape(12.dp), visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth())
                if (errorMsg.isNotEmpty()) { Spacer(Modifier.height(6.dp)); Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (password != confirmPassword) errorMsg = "စကားဝှက်များ မတူညီပါ"
                else { viewModel.updateAppPassword(password); onDismiss() }
            }, shape = RoundedCornerShape(10.dp)) { Text("သိမ်းမည်") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") } }
    )
}

@Composable
fun ResetDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp)) },
        title = { Text("Reset Data", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
        text  = { Text("This will archive current data and delete archives older than 2 batches. Only commissions will remain active.\n\nAre you sure?") },
        confirmButton = {
            Button(onClick = { viewModel.resetAndArchive(); onDismiss() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                shape = RoundedCornerShape(10.dp)) { Text("Yes, Reset") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@SuppressLint("MissingPermission")
@Composable
fun PrinterSettingsDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs   = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
    var macAddress    by remember { mutableStateOf(prefs.getString("printerMac", "") ?: "") }
    var paperSize     by remember { mutableStateOf(prefs.getString("paperSize", "58mm") ?: "58mm") }
    var pairedDevices by remember { mutableStateOf<List<android.bluetooth.BluetoothDevice>>(emptyList()) }
    var isConnecting  by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.entries.all { it.value })
            try { pairedDevices = com.threeDLedger.logic.BluetoothPrinter.getPairedDevices() } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val c = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT)
            val s = androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN)
            if (c == android.content.pm.PackageManager.PERMISSION_GRANTED && s == android.content.pm.PackageManager.PERMISSION_GRANTED)
                try { pairedDevices = com.threeDLedger.logic.BluetoothPrinter.getPairedDevices() } catch (_: Exception) {}
            else permissionLauncher.launch(arrayOf(android.Manifest.permission.BLUETOOTH_CONNECT, android.Manifest.permission.BLUETOOTH_SCAN))
        } else try { pairedDevices = com.threeDLedger.logic.BluetoothPrinter.getPairedDevices() } catch (_: Exception) {}
    }

    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("ပရင်တာ ဆက်တင်", fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("စက္ကူ အရွယ်အစား", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("58mm", "80mm").forEach { size ->
                        val sel = paperSize == size
                        OutlinedButton(onClick = { paperSize = size }, shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent),
                            border = androidx.compose.foundation.BorderStroke(if (sel) 2.dp else 1.dp,
                                if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(0.5f))
                        ) { Text(size, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal) }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text("Bluetooth ပရင်တာ", fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (pairedDevices.isEmpty()) {
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(0.4f)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BluetoothDisabled, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("တွဲထားသော ပရင်တာ မတွေ့ပါ", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                } else {
                    Card(shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(0.5f))) {
                        LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                            items(pairedDevices.size) { i ->
                                val device = pairedDevices[i]; val sel = macAddress == device.address
                                Row(Modifier.fillMaxWidth().clickable { macAddress = device.address }
                                    .background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (sel) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth, null,
                                        tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(device.name ?: "Unknown", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                        Text(device.address, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (sel) { Spacer(Modifier.weight(1f)); Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
                                }
                                if (i < pairedDevices.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(0.4f))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = {
                    isConnecting = true
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        val ok = com.threeDLedger.logic.BluetoothPrinter.connect(macAddress)
                        isConnecting = false
                        android.widget.Toast.makeText(context, if (ok) "ချိတ်ဆက်မှု အောင်မြင်ပါသည်" else "ချိတ်ဆက်မှု မအောင်မြင်ပါ", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), enabled = macAddress.isNotEmpty() && !isConnecting) {
                    if (isConnecting) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary); Spacer(Modifier.width(8.dp)) }
                    Text(if (isConnecting) "ချိတ်ဆက်နေသည်..." else "စမ်းသပ် ချိတ်ဆက်မည်")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        val text = "========================\n      3D VOUCHER\n========================\n Test Print Successful\n========================"
                        val bmp = com.threeDLedger.logic.BluetoothPrinter.createBitmapFromText(text, if (paperSize == "80mm") 576 else 384)
                        val ok  = com.threeDLedger.logic.BluetoothPrinter.printBitmap(bmp, paperSize)
                        android.widget.Toast.makeText(context, if (ok) "ပရင်ထုတ်ခြင်း အောင်မြင်ပါသည်" else "ပရင်ထုတ်ခြင်း မအောင်မြင်ပါ", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp), enabled = macAddress.isNotEmpty()) {
                    Icon(Icons.Default.Print, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("စမ်းသပ် ပရင်ထုတ်မည်")
                }
            }
        },
        confirmButton = {
            Button(onClick = { prefs.edit().putString("printerMac", macAddress).putString("paperSize", paperSize).apply(); onDismiss() },
                shape = RoundedCornerShape(10.dp)) { Text("သိမ်းမည်") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") } }
    )
}
