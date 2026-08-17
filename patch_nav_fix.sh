#!/bin/bash
sed -i 's/onNavigateToArchive = { navController.navigate(ArchiveRoute) }/onNavigateToExportHistory = { navController.navigate(ExportHistoryRoute) }/g' app/src/main/java/com/example/ui/AppNavigation.kt

sed -i 's/onNavigateToExportHistory = { navController.navigate(ExportHistoryRoute) },/onNavigateToArchive = { navController.navigate(ArchiveRoute) },/g' app/src/main/java/com/example/ui/SettingsScreen.kt

