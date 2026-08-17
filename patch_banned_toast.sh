#!/bin/bash
sed -i '/fun BettingScreen(/a \
    val context = androidx.compose.ui.platform.LocalContext.current\
    LaunchedEffect(Unit) {\
        viewModel.bannedNumberEvent.collect {\
            android.widget.Toast.makeText(context, "ထိုးထားသော ဂဏန်းများထဲတွင် ပိတ်ထားသော ဂဏန်းများ ပါဝင်နေသဖြင့် ဖယ်ရှားလိုက်ပါသည်", android.widget.Toast.LENGTH_LONG).show()\
        }\
    }\
' app/src/main/java/com/example/ui/BettingScreen.kt
