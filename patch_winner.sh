#!/bin/bash
sed -i '/var winningNumber by remember { mutableStateOf("") }/a \
    val currentBatch = viewModel.currentBatch.collectAsStateWithLifecycle().value\n    var targetBatch by remember { mutableStateOf(currentBatch.toString()) }\n' app/src/main/java/com/example/ui/WinnerScreen.kt

sed -i '/OutlinedTextField(/i \
            OutlinedTextField(\n                value = targetBatch,\n                onValueChange = { targetBatch = it },\n                label = { Text("အကြိမ် (Batch No)") },\n                modifier = Modifier.fillMaxWidth()\n            )\n            Spacer(modifier = Modifier.height(8.dp))' app/src/main/java/com/example/ui/WinnerScreen.kt

sed -i '/val exact = exactMultiplier.toDoubleOrNull() ?: 0.0/a \
                        val targetBatchInt = targetBatch.toIntOrNull() ?: currentBatch' app/src/main/java/com/example/ui/WinnerScreen.kt

sed -i 's/if (voucher != null && customer != null) {/if (voucher != null \&\& customer != null \&\& voucher.voucher.batchNumber == targetBatchInt) {/g' app/src/main/java/com/example/ui/WinnerScreen.kt
