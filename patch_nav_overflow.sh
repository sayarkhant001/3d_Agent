#!/bin/bash
sed -i '84,89c\
        composable<OverflowRoute> {\
            OverflowScreen(\
                viewModel = viewModel,\
                onNavigateBack = { navController.popBackStack() },\
                onNavigateToExportHistory = { navController.navigate(ExportHistoryRoute) }\
            )\
        }\
' app/src/main/java/com/example/ui/AppNavigation.kt
