package com.threeDLedger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.threeDLedger.data.LotteryRepository

class MainViewModelFactory(
    private val repository: LotteryRepository,
    private val prefs: android.content.SharedPreferences
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, prefs) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
