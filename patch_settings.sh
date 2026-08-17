#!/bin/bash
sed -i '/fun ChangePasswordDialog/,/^}/c \
@Composable\
fun ChangePasswordDialog(viewModel: MainViewModel, onDismiss: () -> Unit) {\
    val currentPassword by viewModel.appPassword.collectAsStateWithLifecycle()\
    var password by remember { mutableStateOf(currentPassword) }\
    var confirmPassword by remember { mutableStateOf(currentPassword) }\
    var errorMsg by remember { mutableStateOf("") }\
    AlertDialog(\
        onDismissRequest = onDismiss,\
        title = { Text("စကားဝှက် ပြောင်းမည်") },\
        text = {\
            Column {\
                OutlinedTextField(\
                    value = password,\
                    onValueChange = { password = it; errorMsg = "" },\
                    label = { Text("စကားဝှက် အသစ် (ဖျက်ရန် အလွတ်ထားပါ)") },\
                    singleLine = true,\
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()\
                )\
                Spacer(modifier = Modifier.height(8.dp))\
                OutlinedTextField(\
                    value = confirmPassword,\
                    onValueChange = { confirmPassword = it; errorMsg = "" },\
                    label = { Text("စကားဝှက် အသစ် (ထပ်ရိုက်ပါ)") },\
                    singleLine = true,\
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()\
                )\
                if (errorMsg.isNotEmpty()) {\
                    Text(errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)\
                }\
            }\
        },\
        confirmButton = {\
            TextButton(onClick = {\
                if (password != confirmPassword) {\
                    errorMsg = "စကားဝှက်များ မတူညီပါ"\
                } else {\
                    viewModel.updateAppPassword(password)\
                    onDismiss()\
                }\
            }) { Text("သိမ်းမည်") }\
        },\
        dismissButton = {\
            TextButton(onClick = onDismiss) { Text("မလုပ်တော့ပါ") }\
        }\
    )\
}\
' app/src/main/java/com/example/ui/SettingsScreen.kt
