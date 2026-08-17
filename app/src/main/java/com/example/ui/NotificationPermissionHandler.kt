package com.example.ui

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
            title = { Text("Enable Notifications") },
            text = { Text("Please enable notifications so we can instantly alert you when the Thai 3D winning numbers are drawn.") },
            confirmButton = {
                Button(onClick = {
                    showRationale = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }) {
                    Text("Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRationale = false }) {
                    Text("Maybe Later")
                }
            }
        )
    }
}
