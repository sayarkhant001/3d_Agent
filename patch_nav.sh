#!/bin/bash
sed -i 's/object BettingRoute/data class BettingRoute(val customerId: Int? = null)/g' app/src/main/java/com/example/ui/AppNavigation.kt
sed -i 's/navController.navigate(BettingRoute)/navController.navigate(BettingRoute())/g' app/src/main/java/com/example/ui/AppNavigation.kt
