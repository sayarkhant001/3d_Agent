#!/bin/bash
sed -i '/object LedgerRoute/a @Serializable\nobject OverflowRoute' app/src/main/java/com/example/ui/AppNavigation.kt

sed -i '/onNavigateToExportHistory = { navController.navigate(ExportHistoryRoute) }/a \                onNavigateToOverflow = { navController.navigate(OverflowRoute) }' app/src/main/java/com/example/ui/HomeScreen.kt

sed -i '/composable<LedgerRoute>/a \        composable<OverflowRoute> {\n            OverflowScreen(\n                viewModel = viewModel,\n                onNavigateBack = { navController.popBackStack() },\n                onNavigateToExportHistory = { navController.navigate(ExportHistoryRoute) }\n            )\n        }' app/src/main/java/com/example/ui/AppNavigation.kt
