package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun LockScreen(viewModel: MainViewModel, onUnlock: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Please enter password to unlock", style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = false },
            visualTransformation = PasswordVisualTransformation(),
            isError = error,
            label = { Text("Password") }
        )
        if (error) {
            Text("Incorrect password", color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = {
            if (password == viewModel.appPassword.value) {
                onUnlock()
            } else {
                error = true
            }
        }) {
            Text("Unlock")
        }
    }
}
