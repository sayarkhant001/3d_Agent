#!/bin/bash
sed -i '/fun addBannedNumber/i \
    init {\
        appPassword.value = prefs.getString("appPassword", "") ?: ""\
        voucherFooterText.value = prefs.getString("voucherFooterText", "ထွက်လျော်မည်။") ?: "ထွက်လျော်မည်။"\
        printerSettings.value = prefs.getString("printerSettings", "") ?: ""\
    }\
\
    fun updateAppPassword(password: String) {\
        appPassword.value = password\
        prefs.edit().putString("appPassword", password).apply()\
    }\
\
    fun updateVoucherFooterText(text: String) {\
        voucherFooterText.value = text\
        prefs.edit().putString("voucherFooterText", text).apply()\
    }\
\
    fun updatePrinterSettings(text: String) {\
        printerSettings.value = text\
        prefs.edit().putString("printerSettings", text).apply()\
    }\
' app/src/main/java/com/example/ui/MainViewModel.kt
