#!/bin/bash
sed -i '89,139d' app/src/main/java/com/example/ui/HomeScreen.kt
sed -i '/MenuButton(title = "ဂဏန်းများ"/a \
            }\n            Spacer(modifier = Modifier.height(16.dp))\n            Row(\n                modifier = Modifier.fillMaxWidth(),\n                horizontalArrangement = Arrangement.SpaceEvenly\n            ) {\n                MenuButton(\n                    title = "ဘောင်ချာ",\n                    icon = Icons.Default.Receipt,\n                    onClick = onNavigateToVouchers\n                )\n                MenuButton(\n                    title = "တင်ကွက်များ",\n                    icon = Icons.Default.Payment,\n                    onClick = onNavigateToOverflow\n                )\n            }\n' app/src/main/java/com/example/ui/HomeScreen.kt
