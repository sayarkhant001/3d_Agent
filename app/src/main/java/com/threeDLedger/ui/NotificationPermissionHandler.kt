package com.threeDLedger.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging

@Composable
fun NotificationPermissionHandler() {
    val context = LocalContext.current
    var showRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            try { FirebaseMessaging.getInstance().subscribeToTopic("3d_alerts") } catch (e: Exception) { e.printStackTrace() }
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val status = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            if (status == PackageManager.PERMISSION_GRANTED) {
                try { FirebaseMessaging.getInstance().subscribeToTopic("3d_alerts") } catch (e: Exception) { e.printStackTrace() }
            } else {
                showRationale = true
            }
        } else {
            // Android 12 and below don't require runtime permission for notifications
            try { FirebaseMessaging.getInstance().subscribeToTopic("3d_alerts") } catch (e: Exception) { e.printStackTrace() }
        }
    }

    if (showRationale) {
        AlertDialog(
            onDismissRequest = { showRationale = false },
            title = { Text("အသိပေးချက် ဖွင့်ပေးပါ") },
            text = { Text("ထိုင်း 3D ထွက်ဂဏန်းများ ထွက်ရှိချိန်တွင် ချက်ချင်းသိရှိနိုင်ရန် အသိပေးချက် (Notification) ကို ခွင့်ပြုပေးပါ။") },
            confirmButton = {
                Button(onClick = {
                    showRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text("ခွင့်ပြုမည်")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text("နောက်မှ")
                }
            }
        )
    }
}
