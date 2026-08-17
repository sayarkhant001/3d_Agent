package com.example.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
// import com.google.firebase.database.FirebaseDatabase
// import com.google.firebase.database.DataSnapshot
// import com.google.firebase.database.DatabaseError
// import com.google.firebase.database.ValueEventListener

class LotteryViewModel : ViewModel() {
    private val _lotteryState = MutableStateFlow("waiting")
    val lotteryState: StateFlow<String> = _lotteryState

    private val _winningNumber = MutableStateFlow("")
    val winningNumber: StateFlow<String> = _winningNumber

    init {
        // Narrow Listeners Example
        /*
        val db = FirebaseDatabase.getInstance()
        
        db.getReference("3d_lottery_status/state")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _lotteryState.value = snapshot.getValue(String::class.java) ?: "waiting"
                }
                override fun onCancelled(error: DatabaseError) {}
            })

        db.getReference("3d_live_results/winning_number")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    _winningNumber.value = snapshot.getValue(String::class.java) ?: ""
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        */
    }
}
