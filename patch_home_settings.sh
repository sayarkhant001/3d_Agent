#!/bin/bash
sed -i '/Row(/,/MenuButton(/ {
  /title = "ဆက်တင်"/,/onClick = onNavigateToSettings/ {
    /Row(/d
    /MenuButton(/d
    /title = "ဆက်တင်"/d
    /icon = Icons.Default.Settings/d
    /onClick = onNavigateToSettings/d
  }
}' app/src/main/java/com/example/ui/HomeScreen.kt
