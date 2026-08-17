#!/bin/bash
sed -i 's/onNavigateToExportHistory: () -> Unit,/onNavigateToArchive: () -> Unit,/g' app/src/main/java/com/example/ui/HomeScreen.kt
