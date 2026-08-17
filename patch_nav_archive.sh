#!/bin/bash
sed -i '/@Serializable object ExportHistoryRoute/i \@Serializable object ArchiveRoute' app/src/main/java/com/example/ui/AppNavigation.kt

sed -i '/composable<ExportHistoryRoute>/i \
        composable<ArchiveRoute> {\
            ArchiveScreen(\
                viewModel = viewModel,\
                onNavigateBack = { navController.popBackStack() }\
            )\
        }\
' app/src/main/java/com/example/ui/AppNavigation.kt

sed -i 's/onNavigateToExportHistory = { navController.navigate(ExportHistoryRoute) },/onNavigateToArchive = { navController.navigate(ArchiveRoute) },/g' app/src/main/java/com/example/ui/AppNavigation.kt

sed -i 's/onNavigateToExportHistory: () -> Unit,/onNavigateToArchive: () -> Unit,/g' app/src/main/java/com/example/ui/SettingsScreen.kt

sed -i 's/SettingsItem(icon = Icons.Default.History, text = "မှတ်တမ်းများ (Archive)", onClick = onNavigateToExportHistory)/SettingsItem(icon = Icons.Default.History, text = "မှတ်တမ်းများ (Archive)", onClick = onNavigateToArchive)/g' app/src/main/java/com/example/ui/SettingsScreen.kt
