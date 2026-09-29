package com.threeDLedger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
            val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
            val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(repository, prefs))
            val fontScaleIndex by viewModel.fontScaleIndex.collectAsStateWithLifecycle()
            val currentFontScale = viewModel.fontScales.getOrElse(fontScaleIndex) { 0.85f }

            MyApplicationTheme(fontScale = currentFontScale) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NotificationPermissionHandler()
                    UpdateDialogHandler(owner = "sayarkhant001", repo = "3d_Agent")
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
