package com.example

import android.app.Application
// import com.google.firebase.database.FirebaseDatabase

class LotteryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Ensure disk caching is active before UI loads
        // Uncomment once Firebase is added to dependencies
        // FirebaseDatabase.getInstance().setPersistenceEnabled(true)
    }
}
