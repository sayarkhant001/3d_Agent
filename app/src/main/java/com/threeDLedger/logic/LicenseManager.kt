package com.threeDLedger.logic

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.threeDLedger.network.*
import org.json.JSONObject
import java.nio.charset.StandardCharsets

sealed class ActivationResult {
    data class Success(val token: String) : ActivationResult()
    data class Pending(val message: String) : ActivationResult()
    data class Error(val message: String) : ActivationResult()
}

class LicenseManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("license_prefs", Context.MODE_PRIVATE)

    @SuppressLint("HardwareIds")
    fun getDeviceFingerprint(): String {
        return Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    fun getDeviceModel(): String {
        val manufacturer = Build.MANUFACTURER?.replaceFirstChar { it.uppercase() } ?: ""
        val model = Build.MODEL ?: "Android"
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    fun isActivated(): Boolean {
        val token = prefs.getString("jwt_token", null) ?: return false
        return !isTokenExpired(token)
    }

    fun getActiveCdKey(): String? = prefs.getString("active_cd_key", null)
    fun getPendingCdKey(): String? = prefs.getString("pending_cd_key", null)

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
                        prefs.edit().remove("jwt_token").remove("active_cd_key").apply()
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false // If decoding fails or no exp field, assume valid (lifetime)
    }

    suspend fun activateLicense(cdKey: String): ActivationResult {
        return try {
            val request = ActivationRequest(
                cd_key = cdKey,
                device_fingerprint = getDeviceFingerprint(),
                device_model = getDeviceModel()
            )
            val response = NetworkClient.licenseApi.activateLicense(request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.status == "activated" && !body.token.isNullOrBlank()) {
                    prefs.edit()
                        .putString("jwt_token", body.token)
                        .putString("active_cd_key", cdKey)
                        .remove("pending_cd_key")
                        .apply()
                    ActivationResult.Success(body.token)
                } else if (body.status == "pending_approval") {
                    prefs.edit().putString("pending_cd_key", cdKey).apply()
                    ActivationResult.Pending(body.message ?: "Admin ၏ အတည်ပြုချက်ကို စောင့်ဆိုင်းနေပါသည်")
                } else {
                    ActivationResult.Error(body.error ?: body.message ?: "အသုံးပြုခွင့် ဖွင့်လှစ်ခြင်း မအောင်မြင်ပါ")
                }
            } else {
                val errJson = try {
                    response.errorBody()?.string()?.let { JSONObject(it).optString("error") }
                } catch (_: Exception) { null }

                val errorMsg = errJson ?: when (response.code()) {
                    400 -> "CD-Key ပုံစံ မှားယွင်းနေပါသည်။ စစ်ဆေးပြီး ပြန်လည်ရိုက်ထည့်ပါ။"
                    404 -> "CD-Key မတွေ့ရှိပါ။ မှန်ကန်သော ကုတ်နံပါတ်ကို ထည့်ပေးပါ။"
                    403 -> "ဤ CD-Key အား အခြားဖုန်းတွင် သို့မဟုတ် ပိတ်သိမ်းထားပြီး ဖြစ်ပါသည်။"
                    500 -> "ဆာဗာ အမှားအယွင်း ဖြစ်ပေါ်နေပါသည်။ ခေတ္တစောင့်ပြီး ပြန်လည်ကြိုးစားပါ။"
                    else -> "အသုံးပြုခွင့် ဖွင့်လှစ်ခြင်း မအောင်မြင်ပါ။"
                }
                ActivationResult.Error(errorMsg)
            }
        } catch (e: java.net.UnknownHostException) {
            ActivationResult.Error("အင်တာနက် ချိတ်ဆက်မှု မရှိပါ။ ကျေးဇူးပြု၍ ကွန်ရက် စစ်ဆေးပါ။")
        } catch (e: java.net.SocketTimeoutException) {
            ActivationResult.Error("ဆာဗာ တုံ့ပြန်မှု အချိန်ကျော်လွန်သွားပါသည်။ ပြန်လည်ကြိုးစားပါ။")
        } catch (e: Exception) {
            ActivationResult.Error(e.message ?: "ချိတ်ဆက်မှု အမှားအယွင်း ဖြစ်ပေါ်နေပါသည်။")
        }
    }

    suspend fun checkPendingStatus(cdKey: String): ActivationResult {
        return try {
            val request = CheckStatusRequest(cdKey, getDeviceFingerprint())
            val response = NetworkClient.licenseApi.checkStatus(request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                when (body.status) {
                    "activated" -> {
                        if (!body.token.isNullOrBlank()) {
                            prefs.edit()
                                .putString("jwt_token", body.token)
                                .putString("active_cd_key", cdKey)
                                .remove("pending_cd_key")
                                .apply()
                            ActivationResult.Success(body.token)
                        } else {
                            ActivationResult.Error("Token မရရှိပါ")
                        }
                    }
                    "pending_approval" -> {
                        ActivationResult.Pending(body.message ?: "Admin ၏ အတည်ပြုချက်ကို စောင့်ဆိုင်းနေဆဲ ဖြစ်ပါသည်")
                    }
                    "rejected" -> {
                        prefs.edit().remove("pending_cd_key").apply()
                        ActivationResult.Error(body.message ?: "Admin မှ ခွင့်ပြုချက် ငြင်းပယ်ခဲ့ပါသည်")
                    }
                    "revoked" -> {
                        prefs.edit().remove("pending_cd_key").remove("jwt_token").apply()
                        ActivationResult.Error("ဤလိုင်စင်ကုတ်အား ပိတ်သိမ်းထားပါသည်")
                    }
                    else -> {
                        ActivationResult.Pending("စောင့်ဆိုင်းနေဆဲ ဖြစ်ပါသည်...")
                    }
                }
            } else {
                ActivationResult.Pending("အခြေအနေ စစ်ဆေးနေဆဲ ဖြစ်ပါသည်...")
            }
        } catch (e: Exception) {
            ActivationResult.Pending("ချိတ်ဆက်မှု စစ်ဆေးနေပါသည်...")
        }
    }

    suspend fun verifyCurrentLicense(): Boolean {
        val cdKey = getActiveCdKey() ?: return isActivated()
        return try {
            val request = VerifyLicenseRequest(cdKey, getDeviceFingerprint())
            val response = NetworkClient.licenseApi.verifyLicense(request)
            if (response.isSuccessful && response.body() != null) {
                val valid = response.body()!!.valid
                if (!valid) {
                    // Revoked by Admin! Clear local state immediately.
                    prefs.edit().remove("jwt_token").remove("active_cd_key").apply()
                    return false
                }
                true
            } else {
                // If offline or network error, keep local offline status
                isActivated()
            }
        } catch (_: Exception) {
            isActivated()
        }
    }
}
