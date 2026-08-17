package com.threeDLedger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.threeDLedger.data.AppDatabase
import com.threeDLedger.data.LotteryRepository
import com.threeDLedger.ui.AppNavigation
import com.threeDLedger.ui.NotificationPermissionHandler
import com.threeDLedger.ui.UpdateDialogHandler
import com.threeDLedger.ui.MainViewModel
import com.threeDLedger.ui.MainViewModelFactory
import com.threeDLedger.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val database = AppDatabase.getDatabase(this)
        val repository = LotteryRepository(database.lotteryDao())
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(repository, prefs))
                    
                    NotificationPermissionHandler()
                    UpdateDialogHandler(owner = "your_github_owner", repo = "your_github_repo")
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
