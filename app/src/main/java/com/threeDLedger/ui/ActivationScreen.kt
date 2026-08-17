package com.threeDLedger.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.threeDLedger.logic.LicenseManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivationScreen(
    onActivated: () -> Unit
) {
    val context = LocalContext.current
    val licenseManager = remember { LicenseManager(context) }
    val coroutineScope = rememberCoroutineScope()

    var cdKey by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (licenseManager.isActivated()) {
            onActivated()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Activation") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Enter your CD-Key to activate the application.",
                style = MaterialTheme.typography.bodyLarge
            )
            
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = cdKey,
                onValueChange = { 
                    cdKey = it.uppercase()
                    errorMessage = null 
                },
                label = { Text("CD-Key (32-character key)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = errorMessage != null
            )

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp).align(Alignment.Start)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = {
                    if (cdKey.isBlank()) {
                        errorMessage = "Please enter a CD-Key."
                        return@Button
                    }
                    
                    isLoading = true
                    errorMessage = null
                    
                    coroutineScope.launch {
                        val result = licenseManager.activateLicense(cdKey)
                        isLoading = false
                        if (result.isSuccess) {
                            onActivated()
                        } else {
                            errorMessage = result.exceptionOrNull()?.message ?: "Activation failed"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Activate", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
