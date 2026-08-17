package com.threeDLedger.logic

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Base64
import com.threeDLedger.network.ActivationRequest
import com.threeDLedger.network.NetworkClient
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class LicenseManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("license_prefs", Context.MODE_PRIVATE)

    @SuppressLint("HardwareIds")
    fun getDeviceFingerprint(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    fun isActivated(): Boolean {
        val token = prefs.getString("jwt_token", null) ?: return false
        return !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean {
        try {
            val parts = token.split(".")
            if (parts.size == 3) {
                val payload = String(Base64.decode(parts[1], Base64.URL_SAFE), StandardCharsets.UTF_8)
                val json = JSONObject(payload)
                if (json.has("exp")) {
                    val exp = json.getLong("exp") // JWT exp is in seconds
                    val currentTime = System.currentTimeMillis() / 1000
                    if (currentTime >= exp) {
                        // Expired! Clear it from SharedPreferences.
                        prefs.edit().remove("jwt_token").apply()
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false // If decoding fails or no exp field, assume valid (lifetime)
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
