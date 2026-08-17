#!/bin/bash
sed -i 's/Color.Black/MaterialTheme.colorScheme.onSurface/g' app/src/main/java/com/example/ui/BettingScreen.kt
sed -i 's/Color(0xFFEEEEEE)/MaterialTheme.colorScheme.surfaceVariant/g' app/src/main/java/com/example/ui/BettingScreen.kt
sed -i 's/Color.White/MaterialTheme.colorScheme.surface/g' app/src/main/java/com/example/ui/BettingScreen.kt
sed -i 's/Color(0xFFFFF9C4)/MaterialTheme.colorScheme.secondaryContainer/g' app/src/main/java/com/example/ui/BettingScreen.kt
