package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object CustomersRoute

@Serializable
object BettingRoute

@Serializable
object WinnerRoute

@Serializable
object LedgerRoute

@Serializable
object VouchersRoute

@Serializable
object ReceiptRoute

@Serializable
object ExportHistoryRoute

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier
    ) {
        composable<HomeRoute> {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToCustomers = { navController.navigate(CustomersRoute) },
                onNavigateToBetting = { navController.navigate(BettingRoute) },
                onNavigateToWinner = { navController.navigate(WinnerRoute) },
                onNavigateToLedger = { navController.navigate(LedgerRoute) },
                onNavigateToVouchers = { navController.navigate(VouchersRoute) },
                onNavigateToReceipt = { navController.navigate(ReceiptRoute) },
                onNavigateToExportHistory = { navController.navigate(ExportHistoryRoute) }
            )
        }
        composable<CustomersRoute> {
            CustomersScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<BettingRoute> {
            BettingScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<WinnerRoute> {
            WinnerScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<LedgerRoute> {
            LedgerScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<VouchersRoute> {
            VouchersScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<ReceiptRoute> {
            ReceiptScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<ExportHistoryRoute> {
            ExportHistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
