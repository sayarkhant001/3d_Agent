#!/bin/bash
sed -i '/\/\/ Customer Selector Row/i \
            val bannedNumbers by viewModel.bannedNumbers.collectAsStateWithLifecycle()\
            if (bannedNumbers.isNotEmpty()) {\
                Card(\
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),\
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)\
                ) {\
                    Text(\
                        text = "ပိတ်ထားသော ဂဏန်းများ: ${bannedNumbers.joinToString(", ") { it.number }}",\
                        modifier = Modifier.padding(8.dp),\
                        color = MaterialTheme.colorScheme.onErrorContainer,\
                        fontWeight = FontWeight.Bold\
                    )\
                }\
            }\
' app/src/main/java/com/example/ui/BettingScreen.kt
