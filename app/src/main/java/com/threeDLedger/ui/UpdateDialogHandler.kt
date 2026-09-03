package com.threeDLedger.ui

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.threeDLedger.utils.GitHubUpdater
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun UpdateDialogHandler(owner: String, repo: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var updateInfo by remember { mutableStateOf<GitHubUpdater.UpdateInfo?>(null) }

    // Dialog states
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    var showProgressDialog by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    val currentVersion = remember {
        try {
            val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }

    // ── Check for updates on launch ─────────────────────────────────────────
    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val info = GitHubUpdater.checkForUpdates(owner, repo)
            if (info != null) {
                // Tag format: "v1.0.47" or "build-47"
                // versionName format: "1.0.47" where 47 = GITHUB_RUN_NUMBER
                // Extract the run number from the END of each version string
                val fetchedRunNumber = info.version
                    .trimStart('v')          // "v1.0.47" → "1.0.47"
                    .split(".", "-")         // ["1","0","47"] or ["build","47"]
                    .lastOrNull { it.all { c -> c.isDigit() } }
                    ?.toIntOrNull() ?: 0

                val currentRunNumber = currentVersion
                    .trimStart('v')
                    .split(".", "-")
                    .lastOrNull { it.all { c -> c.isDigit() } }
                    ?.toIntOrNull() ?: 0

                android.util.Log.d("UpdateChecker",
                    "tag=${info.version} fetchedRun=$fetchedRunNumber " +
                    "currentVer=$currentVersion currentRun=$currentRunNumber")

                if (fetchedRunNumber > currentRunNumber) {
                    updateInfo = info
                    showUpdateDialog = true
                }
            }
        }
    }

    // ── Helper: start download + install ────────────────────────────────────
    fun startDownloadAndInstall(info: GitHubUpdater.UpdateInfo) {
        showUpdateDialog = false
        showProgressDialog = true
        downloadProgress = 0
        downloadError = null

        coroutineScope.launch {
            val apkFile = GitHubUpdater.downloadApkToCache(context, info.downloadUrl) { progress ->
                downloadProgress = progress
            }

            if (apkFile != null) {
                // Small pause so user sees 100%
                delay(300)
                showProgressDialog = false
                GitHubUpdater.installApkFromCache(context, apkFile)
            } else {
                downloadError = "Download မအောင်မြင်ပါ။ Internet စစ်ဆေးပါ။"
            }
        }
    }

    // ── Update Available Dialog ──────────────────────────────────────────────
    if (showUpdateDialog && updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = { showUpdateDialog = false },
            icon = { Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    "Update ရှိနေပါသည်",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Version: ${info.version}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (info.releaseNotes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            info.releaseNotes.take(200).let { if (info.releaseNotes.length > 200) "$it…" else it },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (GitHubUpdater.canInstallUnknownApps(context)) {
                        startDownloadAndInstall(info)
                    } else {
                        showUpdateDialog = false
                        showPermissionDialog = true
                    }
                }) {
                    Text("Update လုပ်မည်")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUpdateDialog = false }) {
                    Text("နောက်မှ")
                }
            }
        )
    }

    // ── "Install Unknown Apps" Permission Dialog ─────────────────────────────
    if (showPermissionDialog && updateInfo != null) {
        val info = updateInfo!!
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("ခွင့်ပြုချက် လိုအပ်ပါသည်", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "\"Unknown Sources\" Install ခွင့်ပြုရန် Settings ဖွင့်မည်။\n\n" +
                    "ဖွင့်ပြီးနောက် ပြန်လာ၍ Update ထပ်ကြိုးစားပါ။"
                )
            },
            confirmButton = {
                Button(onClick = {
                    GitHubUpdater.openInstallUnknownAppsSettings(context)
                    showPermissionDialog = false
                    // Re-show update dialog after a delay so user can retry
                    coroutineScope.launch {
                        delay(2000)
                        showUpdateDialog = true
                    }
                }) {
                    Text("Settings ဖွင့်မည်")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("မလုပ်တော့")
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
            title = { Text("Download လုပ်နေသည်…", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (downloadProgress < 100) "$downloadProgress%" else "ပြီးပါပြီ၊ Installer ဖွင့်နေသည်…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Show error if download failed
                    if (downloadError != null) {
                        Text(
                            downloadError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        TextButton(onClick = {
                            showProgressDialog = false
                            downloadError = null
                        }) {
                            Text("ပိတ်မည်")
                        }
                    }
                }
            },
            confirmButton = {}
        )
    }
}
