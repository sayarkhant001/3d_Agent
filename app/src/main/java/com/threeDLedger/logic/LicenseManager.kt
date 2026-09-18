package com.threeDLedger.logic

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Base64
import com.threeDLedger.network.*
import com.threeDLedger.security.SecurityGuard
import com.threeDLedger.security.SecurityReport
import org.json.JSONObject
import java.nio.charset.StandardCharsets

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

sealed class ActivationResult {
    data class Success(
        val token: String,
        val message: String? = null,
        val deviceMigrated: Boolean = false,
        val remainingDays: Int? = null
    ) : ActivationResult()
    data class Pending(val message: String) : ActivationResult()
    data class Error(val message: String) : ActivationResult()
}

class LicenseManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("license_prefs", Context.MODE_PRIVATE)

    companion object {
        private val SECRET_PART_1 = byteArrayOf(0x33, 0x64, 0x2d, 0x6c, 0x65, 0x64, 0x67, 0x65, 0x72) // "3d-ledger"
        private val SECRET_PART_2 = byteArrayOf(0x2d, 0x6a, 0x77, 0x74, 0x2d, 0x73, 0x65, 0x63, 0x72, 0x65, 0x74) // "-jwt-secret"
        private val SECRET_PART_3 = byteArrayOf(0x2d, 0x32, 0x30, 0x32, 0x36) // "-2026"

        fun getVerificationSecret(): String {
            return String(SECRET_PART_1) + String(SECRET_PART_2) + String(SECRET_PART_3)
        }
    }

    fun checkSecurityIntegrity(): Boolean {
        val report = SecurityGuard.checkIntegrity(context)
        if (!report.isSecure) {
            val violationDetails = report.violations.joinToString(", ")
            prefs.edit()
                .remove("jwt_token")
                .remove("active_cd_key")
                .putString("expired_warning", "လုံခြုံရေး ချိုးဖောက်မှု စစ်ဆေးတွေ့ရှိရပါသည် (Security Violation): $violationDetails")
                .apply()
            return false
        }
        return true
    }

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
        if (!checkSecurityIntegrity()) return false
        val token = prefs.getString("jwt_token", null) ?: return false
        return verifyToken(token)
    }

    fun assertLicenseActive() {
        if (!isActivated()) {
            throw SecurityException("Access Denied: 3D Ledger license is invalid, expired, or tampered.")
        }
    }

    fun getActiveCdKey(): String? = prefs.getString("active_cd_key", null)
    fun getPendingCdKey(): String? = prefs.getString("pending_cd_key", null)
    fun getExpiredWarning(): String? = prefs.getString("expired_warning", null)
    fun clearExpiredWarning() {
        prefs.edit().remove("expired_warning").apply()
    }

    private fun clearActivation(warning: String) {
        prefs.edit()
            .remove("jwt_token")
            .remove("active_cd_key")
            .putString("expired_warning", warning)
            .apply()
    }

    fun verifyToken(token: String): Boolean {
        try {
            val parts = token.split(".")
            if (parts.size != 3) return false

            // 1. Cryptographic HMAC-SHA256 Signature Verification
            val headerAndPayload = "${parts[0]}.${parts[1]}".toByteArray(StandardCharsets.US_ASCII)
            val mac = Mac.getInstance("HmacSHA256")
            val key = SecretKeySpec(getVerificationSecret().toByteArray(StandardCharsets.UTF_8), "HmacSHA256")
            mac.init(key)
            val computedSigBytes = mac.doFinal(headerAndPayload)
            val expectedSig = Base64.encodeToString(computedSigBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).trim()

            if (!MessageDigest.isEqual(expectedSig.toByteArray(StandardCharsets.US_ASCII), parts[2].trim().toByteArray(StandardCharsets.US_ASCII))) {
                clearActivation("လုံခြုံရေး လက်မှတ် ချိုးဖောက်မှု စစ်ဆေးတွေ့ရှိရပါသည် (Token Signature Tampered)")
                return false
            }

            // 2. Decode payload & verify device hardware binding
            val payloadStr = String(Base64.decode(parts[1], Base64.URL_SAFE), StandardCharsets.UTF_8)
            val json = JSONObject(payloadStr)

            val boundDevice = json.optString("device_fingerprint", "")
            val currentDevice = getDeviceFingerprint()
            if (boundDevice.isNotEmpty() && boundDevice != currentDevice) {
                clearActivation("ဤလိုင်စင်သည် အခြားဖုန်းအတွက် ထုတ်ပေးထားခြင်း ဖြစ်ပါသည် (Hardware Mismatch)")
                return false
            }

            // 3. Expiration verification
            if (json.has("exp") && !json.isNull("exp")) {
                val expSec = json.getLong("exp")
                val nowSec = System.currentTimeMillis() / 1000
                if (nowSec >= expSec) {
                    clearActivation("လိုင်စင် သက်တမ်း ကုန်ဆုံးသွားပါပြီ။ ဆက်လက်အသုံးပြုရန် လိုင်စင် အသစ် ဝယ်ယူပါ")
                    return false
                }
            }

            return true
        } catch (e: Exception) {
            return false
        }
    }

    suspend fun activateLicense(cdKey: String): ActivationResult {
        if (!checkSecurityIntegrity()) {
            return ActivationResult.Error("လုံခြုံရေး စစ်ဆေးချက် မအောင်မြင်ပါ (Security Violation Detected)")
        }
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
                        .remove("expired_warning")
                        .apply()
                    ActivationResult.Success(
                        token = body.token,
                        message = body.message,
                        deviceMigrated = body.device_migrated == true,
                        remainingDays = body.remaining_days
                    )
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
                    403 -> "ဤ CD-Key အား အခြားဖုန်းတွင် သို့မဟုတ် သက်တမ်းကုန်ဆုံး/ပိတ်သိမ်းထားပြီး ဖြစ်ပါသည်။"
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
                                .remove("expired_warning")
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
                        prefs.edit()
                            .remove("pending_cd_key")
                            .remove("jwt_token")
                            .putString("expired_warning", "ဤလိုင်စင်ကုတ်အား ပိတ်သိမ်းထားပါသည်")
                            .apply()
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
        if (!checkSecurityIntegrity()) return false
        val cdKey = getActiveCdKey() ?: return isActivated()
        return try {
            val request = VerifyLicenseRequest(cdKey, getDeviceFingerprint())
            val response = NetworkClient.licenseApi.verifyLicense(request)
            if (response.isSuccessful && response.body() != null) {
                val resBody = response.body()!!
                if (!resBody.valid) {
                    val warningMsg = resBody.message ?: when (resBody.reason) {
                        "device_transferred" -> "ဤလိုင်စင်ကုတ်အား အခြားဖုန်းသို့ ပြောင်းရွှေ့အသုံးပြုလိုက်ပါပြီ။ ဆက်လက်အသုံးပြုရန် လိုင်စင် အသစ် ဝယ်ယူပါ"
                        "expired" -> "လိုင်စင် သက်တမ်း ကုန်ဆုံးသွားပါပြီ။ ဆက်လက်အသုံးပြုရန် လိုင်စင် အသစ် ဝယ်ယူပါ"
                        "revoked" -> "ဤလိုင်စင်ကုတ်အား Admin မှ ပိတ်သိမ်းထားပါသည်"
                        else -> "လိုင်စင် အသုံးပြုခွင့် သက်တမ်း ကုန်ဆုံးပါပြီ။ ဆက်လက်အသုံးပြုရန် လိုင်စင် အသစ် ဝယ်ယူပါ"
                    }
                    prefs.edit()
                        .remove("jwt_token")
                        .remove("active_cd_key")
                        .putString("expired_warning", warningMsg)
                        .apply()
                    return false
                }
                true
            } else {
                // If offline or network error, verify with local token exp
                isActivated()
            }
        } catch (_: Exception) {
            isActivated()
        }
    }
}
