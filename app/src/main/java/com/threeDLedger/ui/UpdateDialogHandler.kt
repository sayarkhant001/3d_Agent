package com.threeDLedger.ui

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.threeDLedger.ui.theme.EmeraldPrimary
import com.threeDLedger.ui.theme.GoldAccent
import com.threeDLedger.ui.theme.rememberResponsiveDimens
import com.threeDLedger.utils.GitHubUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UpdateDialogHandler(
    owner: String = "sayarkhant001",
    repo: String = "3d_Agent",
    viewModel: MainViewModel? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val rDimens = rememberResponsiveDimens()

    var updateInfo by remember { mutableStateOf<GitHubUpdater.UpdateInfo?>(null) }

    // Dialog states
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    var isInstalling by remember { mutableStateOf(false) }

    val currentVersion = remember {
        try {
            val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }

    // Listen to ViewModel manual trigger if provided
    if (viewModel != null) {
        val triggerOpen by viewModel.showUpdateDialogTrigger.collectAsStateWithLifecycle()
        val vmUpdateInfo by viewModel.availableUpdate.collectAsStateWithLifecycle()

        LaunchedEffect(triggerOpen, vmUpdateInfo) {
            if (triggerOpen && vmUpdateInfo != null) {
                updateInfo = vmUpdateInfo
                showUpdateDialog = true
                viewModel.triggerUpdateDialog(false)
            }
        }
    }

    // ── Check for updates on launch ─────────────────────────────────────────
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            // 1. Check Cloudflare Worker / Telegram Bot Release Pipeline first
            try {
                val cfUpdate = com.threeDLedger.logic.AppUpdateChecker.checkForUpdate(context)
                if (cfUpdate != null && cfUpdate.hasUpdate) {
                    val info = GitHubUpdater.UpdateInfo(
                        version = cfUpdate.latestVersionName,
                        releaseNotes = cfUpdate.releaseNotes,
                        downloadUrl = cfUpdate.downloadUrl ?: cfUpdate.telegramBotUrl,
                        releaseUrl = cfUpdate.releaseUrl
                    )
                    updateInfo = info
                    viewModel?.setAvailableUpdate(info)
                    showUpdateDialog = true
                    return@launch
                }
            } catch (_: Exception) {}

            // 2. Check GitHub Releases
            val info = GitHubUpdater.checkForUpdates(owner, repo)
            if (info != null) {
                val fetchedRunNumber = info.version
                    .trimStart('v')
                    .split(".", "-")
                    .lastOrNull { it.all { c -> c.isDigit() } }
                    ?.toIntOrNull() ?: 0

                val currentRunNumber = currentVersion
                    .trimStart('v')
                    .split(".", "-")
                    .lastOrNull { it.all { c -> c.isDigit() } }
                    ?.toIntOrNull() ?: 0

                if (fetchedRunNumber > currentRunNumber) {
                    updateInfo = info
                    viewModel?.setAvailableUpdate(info)
                    showUpdateDialog = true
                }
            }
        }
    }

    // ── Helper: start download + install ────────────────────────────────────
    fun startDownloadAndInstall(info: GitHubUpdater.UpdateInfo) {
        if (info.downloadUrl.startsWith("https://t.me/")) {
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.downloadUrl)).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {}
            showUpdateDialog = false
            return
        }

        showUpdateDialog = false
        showProgressDialog = true
        downloadProgress = 0
        downloadError = null
        isInstalling = false

        coroutineScope.launch(Dispatchers.IO) {
            try {
                val apkFile = GitHubUpdater.downloadApkToCache(context, info.downloadUrl) { progress ->
                    coroutineScope.launch(Dispatchers.Main) {
                        downloadProgress = progress
                    }
                }

                withContext(Dispatchers.Main) {
                    if (apkFile != null) {
                        downloadProgress = 100
                        delay(300)
                        isInstalling = true
                    }
                }

                if (apkFile != null) {
                    delay(200)
                    withContext(Dispatchers.Main) {
                        val ok = GitHubUpdater.installApkFromCache(context, apkFile)
                        if (!ok && !GitHubUpdater.canInstallUnknownApps(context)) {
                            showPermissionDialog = true
                        }
                    }
                    delay(1200)
                    withContext(Dispatchers.Main) {
                        isInstalling = false
                        showProgressDialog = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        downloadError = "Download မအောင်မြင်ပါ။ Internet ဆက်သွယ်မှု စစ်ဆေးပါ။"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    downloadError = "Error: ${e.localizedMessage ?: "မအောင်မြင်ပါ"}"
                }
            }
        }
    }

    // ── Update Available Dialog (Clean, High-Contrast & Eye-Catching) ────────
    if (showUpdateDialog && updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            icon = {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(EmeraldPrimary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = EmeraldPrimary,
                        modifier = Modifier.size(30.dp)
                    )
                }
            },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "ဗားရှင်းအသစ် ရရှိပါပြီ",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFFEF3C7),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B))
                    ) {
                        Text(
                            text = "GitHub Releases",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Current vs New Version Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("လက်ရှိ ဗားရှင်း", fontSize = 11.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(currentVersion, fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                            }
                            Text("➜", fontSize = 16.sp, color = EmeraldPrimary, fontWeight = FontWeight.Bold)
                            Column(horizontalAlignment = Alignment.End) {
                                Text("ဗားရှင်း အသစ်", fontSize = 11.5.sp, color = EmeraldPrimary, fontWeight = FontWeight.SemiBold)
                                Text(info.version, fontWeight = FontWeight.Black, fontSize = 15.sp, color = EmeraldPrimary, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }

                    // Release notes
                    if (info.releaseNotes.isNotBlank()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                "ပါဝင်သော အချက်များ :",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = info.releaseNotes.take(300).let { if (info.releaseNotes.length > 300) "$it…" else it },
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontSize = 13.sp,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.padding(10.dp)
                                )
                            }
                        }
                    }

                    Text(
                        "GitHub သို့မဟုတ် Telegram Bot မှ ရယူနိုင်ပါသည်။",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            },
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rDimens.compactButtonHeight),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldPrimary,
                            contentColor = Color.White
                        ),
                        onClick = {
                            startDownloadAndInstall(info)
                        }
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("GitHub မှ ဒေါင်းလုဒ်လုပ်မည်", fontWeight = FontWeight.Bold, fontSize = 14.5.sp)
                    }

                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rDimens.compactButtonHeight),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            val tgUrl = "https://t.me/threed_ledger_bot?start=download"
                            try {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(tgUrl)).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                android.widget.Toast.makeText(context, "Telegram အက်ပ် ဖွင့်မရပါ", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Telegram Bot မှ ရယူမည်", fontWeight = FontWeight.SemiBold, fontSize = 13.5.sp)
                    }

                    if (info.releaseUrl != null) {
                        TextButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp),
                            onClick = {
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(info.releaseUrl)).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    android.widget.Toast.makeText(context, "Browser ဖွင့်မရပါ", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        ) {
                            Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("GitHub Release စာမျက်နှာ ဖွင့်မည်", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    TextButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp),
                        onClick = { showUpdateDialog = false }
                    ) {
                        Text("နောက်မှ", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            dismissButton = null
        )
    }

    // ── "Install Unknown Apps" Permission Dialog ─────────────────────────────
    if (showPermissionDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = {
                Icon(Icons.Default.Settings, contentDescription = null, tint = EmeraldPrimary, modifier = Modifier.size(36.dp))
            },
            title = {
                Text(
                    "ခွင့်ပြုချက် လိုအပ်ပါသည်",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Text(
                    "အက်ပ် အသစ်ကို ထည့်သွင်းနိုင်ရန် ဖုန်းဆက်တင်ထဲတွင် \"Unknown Sources\" အား ခွင့်ပြုပေးရန် လိုအပ်ပါသည်။\n\nဆက်တင်ဖွင့်ပြီး ဤအက်ပ်အား ခွင့်ပြုပေးပြီးပါက ပြန်လာ၍ Update ထပ်မံ ပြုလုပ်နိုင်ပါသည်။",
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp
                )
            },
            confirmButton = {
                Button(
                    modifier = Modifier.fillMaxWidth().height(rDimens.compactButtonHeight),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimary),
                    onClick = {
                        GitHubUpdater.openInstallUnknownAppsSettings(context)
                        showPermissionDialog = false
                        coroutineScope.launch {
                            delay(2000)
                            showUpdateDialog = true
                        }
                    }
                ) {
                    Text("ဆက်တင် ဖွင့်မည်", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showPermissionDialog = false }
                ) {
                    Text("မလုပ်တော့ပါ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }

    // ── Download Progress Dialog ─────────────────────────────────────────────
    if (showProgressDialog) {
        val animatedProgress by animateFloatAsState(
            targetValue = downloadProgress / 100f,
            label = "download_progress"
        )

        AlertDialog(
            onDismissRequest = { /* Not dismissible during download */ },
            title = {
                Text(
                    if (isInstalling) "Installer ဖွင့်နေသည်…" else "GitHub မှ ဒေါင်းလုဒ်လုပ်နေသည်…",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (downloadError != null) {
                        Text(
                            downloadError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val tgUrl = "https://t.me/threed_ledger_bot?start=download"
                                    try {
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(tgUrl)).apply {
                                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        android.widget.Toast.makeText(context, "Telegram အက်ပ် ဖွင့်မရပါ", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(rDimens.compactButtonHeight),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Telegram Bot မှ ဒေါင်းလုဒ်လုပ်မည်")
                            }
                            Button(
                                onClick = {
                                    showProgressDialog = false
                                    downloadError = null
                                },
                                modifier = Modifier.fillMaxWidth().height(rDimens.compactButtonHeight),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("ပိတ်မည်")
                            }
                        }
                    } else if (isInstalling) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(54.dp),
                            color = EmeraldPrimary,
                            strokeWidth = 4.dp
                        )
                        Text(
                            "ဖုန်းစနစ် Installer ဖွင့်နေပါသည်… ခေတ္တစောင့်ပါ",
                            style = MaterialTheme.typography.bodyMedium,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(5.dp)),
                            color = EmeraldPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Text(
                            "$downloadProgress%",
                            fontWeight = FontWeight.Black,
                            fontSize = 24.sp,
                            fontFamily = FontFamily.Monospace,
                            color = EmeraldPrimary
                        )
                        Text(
                            "ဖိုင်ဒေါင်းလုဒ် လုပ်နေပါသည်… ကျေးဇူးပြု၍ စောင့်ပါ",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {}
        )
    }
}

