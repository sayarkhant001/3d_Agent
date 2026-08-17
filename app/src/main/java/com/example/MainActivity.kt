package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.LotteryRepository
import com.example.ui.AppNavigation
import com.example.ui.MainViewModel
import com.example.ui.MainViewModelFactory
import com.example.ui.theme.MyApplicationTheme

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
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
