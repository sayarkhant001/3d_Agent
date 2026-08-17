package com.threeDLedger.ui

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.threeDLedger.utils.GitHubUpdater
import kotlinx.coroutines.launch

@Composable
fun UpdateDialogHandler(owner: String, repo: String) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var updateInfo by remember { mutableStateOf<GitHubUpdater.UpdateInfo?>(null) }
    var showDialog by remember { mutableStateOf(false) }

    val currentVersion = remember {
        try {
            val pInfo: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0"
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope.launch {
            val info = GitHubUpdater.checkForUpdates(owner, repo)
            if (info != null) {
                // simple string compare v1.0.1 vs v1.0.2
                val cleanCurrent = currentVersion.replace("v", "")
                val cleanFetched = info.version.replace("v", "")
                if (cleanFetched != cleanCurrent) {
                    updateInfo = info
                    showDialog = true
                }
            }
        }
    }

    if (showDialog && updateInfo != null) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Update Available: ${updateInfo!!.version}") },
            text = { Text(updateInfo!!.releaseNotes) },
            confirmButton = {
                Button(onClick = {
                    showDialog = false
                    GitHubUpdater.downloadApk(context, updateInfo!!.downloadUrl, updateInfo!!.version)
                }) {
                    Text("Update Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Later")
                }
            }
        )
    }
}
