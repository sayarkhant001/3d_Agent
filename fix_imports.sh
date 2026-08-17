#!/bin/bash
sed -i '1i import android.annotation.SuppressLint\nimport androidx.compose.foundation.background\nimport kotlinx.coroutines.launch\nimport androidx.compose.ui.graphics.Color\n' app/src/main/java/com/example/ui/SettingsScreen.kt
sed -i '/@Composable/ {
  N
  /\n@Composable/ {
    s/@Composable\n//
  }
}' app/src/main/java/com/example/ui/SettingsScreen.kt

sed -i '1i import androidx.compose.runtime.rememberCoroutineScope\nimport kotlinx.coroutines.launch\n' app/src/main/java/com/example/ui/VouchersScreen.kt
