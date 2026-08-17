package com.example.utils

import android.content.Context
// import com.scottyab.rootbeer.RootBeer
// import androidx.security.crypto.EncryptedSharedPreferences
// import androidx.security.crypto.MasterKeys

object SecurityUtils {
    
    fun checkRoot(context: Context): Boolean {
        // val rootBeer = RootBeer(context)
        // return rootBeer.isRooted
        return false 
    }

    /*
    fun saveJwtToken(context: Context, token: String) {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        val sharedPreferences = EncryptedSharedPreferences.create(
            "secret_shared_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        sharedPreferences.edit().putString("auth_token", token).apply()
    }
    */
}
