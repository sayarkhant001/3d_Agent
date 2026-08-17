#!/bin/bash
sed -i 's/onNavigateToExportHistory = { navController.navigate(ExportHistoryRoute) }/onNavigateToArchive = { navController.navigate(ArchiveRoute) }/g' app/src/main/java/com/example/ui/AppNavigation.kt
