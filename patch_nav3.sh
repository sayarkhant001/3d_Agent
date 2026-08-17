#!/bin/bash
sed -i '/@Serializable object HomeRoute/i \@Serializable object LockRoute' app/src/main/java/com/example/ui/AppNavigation.kt
sed -i 's/startDestination = HomeRoute/startDestination = if (viewModel.appPassword.value.isNotEmpty()) LockRoute else HomeRoute/g' app/src/main/java/com/example/ui/AppNavigation.kt

sed -i '/composable<HomeRoute>/i \        composable<LockRoute> {\n            LockScreen(viewModel, onUnlock = { navController.navigate(HomeRoute) { popUpTo(LockRoute) { inclusive = true } } })\n        }' app/src/main/java/com/example/ui/AppNavigation.kt
