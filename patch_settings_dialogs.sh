#!/bin/bash
sed -i 's/var password by remember { mutableStateOf(viewModel.appPassword.collectAsStateWithLifecycle().value) }/val currentPassword by viewModel.appPassword.collectAsStateWithLifecycle()\n    var password by remember { mutableStateOf(currentPassword) }/g' app/src/main/java/com/example/ui/SettingsScreen.kt

sed -i 's/var printer by remember { mutableStateOf(viewModel.printerSettings.collectAsStateWithLifecycle().value) }/val currentPrinter by viewModel.printerSettings.collectAsStateWithLifecycle()\n    var printer by remember { mutableStateOf(currentPrinter) }/g' app/src/main/java/com/example/ui/SettingsScreen.kt
