#!/bin/bash
sed -i '/var brakeLimit = MutableStateFlow/i \    val appPassword = MutableStateFlow("")\n    val voucherFooterText = MutableStateFlow("ထွက်လျော်မည်။")\n    val printerSettings = MutableStateFlow("")\n    val bannedNumberEvent = kotlinx.coroutines.flow.MutableSharedFlow<Boolean>()\n' app/src/main/java/com/example/ui/MainViewModel.kt
