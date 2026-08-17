#!/bin/bash
sed -i 's/onNavigateBack = { navController.popBackStack() }/onNavigateBack = { navController.popBackStack() },\n                onNavigateToExportHistory = { navController.navigate(ExportHistoryRoute) },\n                onNavigateToWinner = { navController.navigate(WinnerRoute) }/g' app/src/main/java/com/example/ui/AppNavigation.kt
