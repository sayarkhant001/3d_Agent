package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.logic.LicenseManager
import kotlinx.serialization.Serializable

@Serializable object ActivationRoute
@Serializable object LockRoute
@Serializable object HomeRoute
@Serializable object CustomersRoute
@Serializable data class BettingRoute(val customerId: Int? = null)
@Serializable object WinnerRoute
@Serializable object LedgerRoute
@Serializable object OverflowRoute
@Serializable data class VouchersRoute(val customerId: Int? = null)
@Serializable object ReceiptRoute
@Serializable object ArchiveRoute
@Serializable object ExportHistoryRoute
@Serializable object SettingsRoute

@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val licenseManager = remember { LicenseManager(context) }
    
    val startDestination: Any = if (!licenseManager.isActivated()) {
        ActivationRoute
    } else if (viewModel.appPassword.value.isNotEmpty()) {
        LockRoute
    } else {
        HomeRoute
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<ActivationRoute> {
            ActivationScreen(
                onActivated = {
                    val nextDest = if (viewModel.appPassword.value.isNotEmpty()) LockRoute else HomeRoute
                    navController.navigate(nextDest) {
                        popUpTo(ActivationRoute) { inclusive = true }
                    }
                }
            )
        }
        composable<LockRoute> {
            LockScreen(viewModel, onUnlock = { navController.navigate(HomeRoute) { popUpTo(LockRoute) { inclusive = true } } })
        }
        composable<HomeRoute> {
            HomeScreen(
                viewModel = viewModel,
                onNavigateToCustomers = { navController.navigate(CustomersRoute) },
                onNavigateToBetting = { navController.navigate(BettingRoute()) },
                onNavigateToWinner = { navController.navigate(WinnerRoute) },
                onNavigateToLedger = { navController.navigate(LedgerRoute) },
                onNavigateToVouchers = { navController.navigate(VouchersRoute()) },
                onNavigateToReceipt = { navController.navigate(ReceiptRoute) },
                onNavigateToArchive = { navController.navigate(ArchiveRoute) },
                onNavigateToOverflow = { navController.navigate(OverflowRoute) },
                onNavigateToSettings = { navController.navigate(SettingsRoute) }
            )
        }
        composable<CustomersRoute> {
            CustomersScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToBetting = { customerId -> navController.navigate(BettingRoute(customerId)) }
            )
        }
        composable<BettingRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<BettingRoute>()
            BettingScreen(
                viewModel = viewModel,
                initialCustomerId = route.customerId,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToCustomerVouchers = { customerId ->
                    navController.navigate(VouchersRoute(customerId = customerId))
                }
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
        
        composable<OverflowRoute> {
            OverflowScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToExportHistory = { navController.navigate(ExportHistoryRoute) }
            )
        }
        composable<VouchersRoute> { backStackEntry ->
            val route = backStackEntry.toRoute<VouchersRoute>()
            VouchersScreen(
                viewModel = viewModel,
                initialCustomerId = route.customerId,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<ReceiptRoute> {
            ReceiptScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<ArchiveRoute> {
            ArchiveScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<ExportHistoryRoute> {
            ExportHistoryScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable<SettingsRoute> {
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToArchive = { navController.navigate(ArchiveRoute) },
                onNavigateToWinner = { navController.navigate(WinnerRoute) }
            )
        }
    }
}
