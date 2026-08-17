#!/bin/bash
sed -i '31,37d' app/src/main/java/com/example/ui/BettingScreen.kt
sed -i '/var selectedCustomerId/i \    val context = androidx.compose.ui.platform.LocalContext.current\n    LaunchedEffect(Unit) {\n        viewModel.bannedNumberEvent.collect {\n            android.widget.Toast.makeText(context, "ထိုးထားသော ဂဏန်းများထဲတွင် ပိတ်ထားသော ဂဏန်းများ ပါဝင်နေသဖြင့် ဖယ်ရှားလိုက်ပါသည်", android.widget.Toast.LENGTH_LONG).show()\n        }\n    }\n' app/src/main/java/com/example/ui/BettingScreen.kt
