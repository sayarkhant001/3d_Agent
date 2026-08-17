package com.example.logic

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.example.network.ActivationRequest
import com.example.network.NetworkClient

class LicenseManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("license_prefs", Context.MODE_PRIVATE)

    @SuppressLint("HardwareIds")
    fun getDeviceFingerprint(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    fun isActivated(): Boolean {
        return prefs.getString("jwt_token", null) != null
    }

    suspend fun activateLicense(cdKey: String): Result<String> {
        return try {
            val request = ActivationRequest(cdKey, getDeviceFingerprint())
            val response = NetworkClient.licenseApi.activateLicense(request)
            
            if (response.isSuccessful && response.body() != null) {
                val token = response.body()!!.token
                prefs.edit().putString("jwt_token", token).apply()
                Result.success(token)
            } else {
                Result.failure(Exception("Activation failed: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
