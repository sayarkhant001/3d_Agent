#!/bin/bash
sed -i 's/class MainViewModelFactory(private val repository: LotteryRepository) : ViewModelProvider.Factory {/class MainViewModelFactory(private val repository: LotteryRepository, private val prefs: android.content.SharedPreferences) : ViewModelProvider.Factory {/g' app/src/main/java/com/example/ui/MainViewModel.kt
sed -i 's/return MainViewModel(repository) as T/return MainViewModel(repository, prefs) as T/g' app/src/main/java/com/example/ui/MainViewModel.kt

sed -i 's/class MainViewModel(private val repository: LotteryRepository) : ViewModel() {/class MainViewModel(private val repository: LotteryRepository, private val prefs: android.content.SharedPreferences) : ViewModel() {/g' app/src/main/java/com/example/ui/MainViewModel.kt

sed -i 's/val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(repository))/val prefs = getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)\n                    val viewModel: MainViewModel = viewModel(factory = MainViewModelFactory(repository, prefs))/g' app/src/main/java/com/example/MainActivity.kt
