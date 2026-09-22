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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    private fun handleActivationSuccess(
        cdKey: String,
        status: String?,
        token: String?,
        message: String?,
        error: String?,
        deviceMigrated: Boolean?,
        remainingDays: Int?
    ): ActivationResult {
        return if (status == "activated" && !token.isNullOrBlank()) {
            prefs.edit()
                .putString("jwt_token", token)
                .putString("active_cd_key", cdKey)
                .remove("pending_cd_key")
                .remove("expired_warning")
                .apply()
            ActivationResult.Success(
                token = token,
                message = message,
                deviceMigrated = deviceMigrated == true,
                remainingDays = remainingDays
            )
        } else if (status == "pending_approval") {
            prefs.edit().putString("pending_cd_key", cdKey).apply()
            ActivationResult.Pending(message ?: "Admin ၏ အတည်ပြုချက်ကို စောင့်ဆိုင်းနေပါသည်")
        } else {
            ActivationResult.Error(error ?: message ?: "အသုံးပြုခွင့် ဖွင့်လှစ်ခြင်း မအောင်မြင်ပါ")
        }
    }

    private fun handleActivationHttpError(code: Int, errorBody: String?): ActivationResult {
        val errJson = try {
            errorBody?.let { JSONObject(it).optString("error") }
        } catch (_: Exception) { null }

        val errorMsg = errJson ?: when (code) {
            400 -> "CD-Key ပုံစံ မှားယွင်းနေပါသည်။ စစ်ဆေးပြီး ပြန်လည်ရိုက်ထည့်ပါ။"
            404 -> "CD-Key မတွေ့ရှိပါ။ မှန်ကန်သော ကုတ်နံပါတ်ကို ထည့်ပေးပါ။"
            403 -> "ဤ CD-Key အား အခြားဖုန်းတွင် သို့မဟုတ် သက်တမ်းကုန်ဆုံး/ပိတ်သိမ်းထားပြီး ဖြစ်ပါသည်။"
            500 -> "ဆာဗာ အမှားအယွင်း ဖြစ်ပေါ်နေပါသည်။ ခေတ္တစောင့်ပြီး ပြန်လည်ကြိုးစားပါ။"
            else -> "အသုံးပြုခွင့် ဖွင့်လှစ်ခြင်း မအောင်မြင်ပါ။"
        }
        return ActivationResult.Error(errorMsg)
    }

    private suspend fun activateLicenseDirect(
        cdKey: String,
        deviceFingerprint: String,
        deviceModel: String
    ): ActivationResult = withContext(Dispatchers.IO) {
        try {
            val jsonPayload = JSONObject().apply {
                put("cd_key", cdKey)
                put("device_fingerprint", deviceFingerprint)
                put("device_model", deviceModel)
            }.toString()

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val request = okhttp3.Request.Builder()
                .url("${NetworkClient.BASE_URL}/activate")
                .post(jsonPayload.toRequestBody(mediaType))
                .build()

            val response = NetworkClient.okHttpClient.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()

            if (response.isSuccessful && responseBody.isNotBlank()) {
                val json = JSONObject(responseBody)
                val status = json.optString("status")
                val token = json.optString("token")
                val message = if (json.has("message") && !json.isNull("message")) json.getString("message") else null
                val error = if (json.has("error") && !json.isNull("error")) json.getString("error") else null
                val deviceMigrated = if (json.has("device_migrated")) json.optBoolean("device_migrated") else null
                val remainingDays = if (json.has("remaining_days")) json.optInt("remaining_days") else null

                handleActivationSuccess(cdKey, status, token, message, error, deviceMigrated, remainingDays)
            } else {
                handleActivationHttpError(response.code, responseBody)
            }
        } catch (e: Exception) {
            ActivationResult.Error(e.message ?: "အသုံးပြုခွင့် ဖွင့်လှစ်ခြင်း မအောင်မြင်ပါ")
        }
    }

    suspend fun activateLicense(cdKey: String): ActivationResult {
        if (!checkSecurityIntegrity()) {
            return ActivationResult.Error("လုံခြုံရေး စစ်ဆေးချက် မအောင်မြင်ပါ (Security Violation Detected)")
        }
        val deviceFingerprint = getDeviceFingerprint()
        val deviceModel = getDeviceModel()

        // 1. Try Retrofit with Moshi
        return try {
            val request = ActivationRequest(
                cd_key = cdKey,
                device_fingerprint = deviceFingerprint,
                device_model = deviceModel
            )
            val response = NetworkClient.licenseApi.activateLicense(request)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                handleActivationSuccess(
                    cdKey = cdKey,
                    status = body.status,
                    token = body.token,
                    message = body.message,
                    error = body.error,
                    deviceMigrated = body.device_migrated,
                    remainingDays = body.remaining_days
                )
            } else {
                handleActivationHttpError(response.code(), response.errorBody()?.string())
            }
        } catch (e: Exception) {
            // If Retrofit or converter encounters any issue, seamlessly execute direct OkHttp + JSONObject
            try {
                return activateLicenseDirect(cdKey, deviceFingerprint, deviceModel)
            } catch (_: Exception) {}

            if (e is java.net.UnknownHostException) {
                ActivationResult.Error("အင်တာနက် ချိတ်ဆက်မှု မရှိပါ။ ကျေးဇူးပြု၍ ကွန်ရက် စစ်ဆေးပါ။")
            } else if (e is java.net.SocketTimeoutException) {
                ActivationResult.Error("ဆာဗာ တုံ့ပြန်မှု အချိန်ကျော်လွန်သွားပါသည်။ ပြန်လည်ကြိုးစားပါ။")
            } else {
                ActivationResult.Error(e.message ?: "ချိတ်ဆက်မှု အမှားအယွင်း ဖြစ်ပေါ်နေပါသည်။")
            }
        }
    }

    suspend fun checkPendingStatus(cdKey: String): ActivationResult {
        val deviceFingerprint = getDeviceFingerprint()
        return try {
            val request = CheckStatusRequest(cdKey, deviceFingerprint)
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
            // Direct fallback
            try {
                val jsonPayload = JSONObject().apply {
                    put("cd_key", cdKey)
                    put("device_fingerprint", deviceFingerprint)
                }.toString()
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val req = okhttp3.Request.Builder()
                    .url("${NetworkClient.BASE_URL}/check-status")
                    .post(jsonPayload.toRequestBody(mediaType))
                    .build()
                val resp = NetworkClient.okHttpClient.newCall(req).execute()
                val respBody = resp.body?.string().orEmpty()
                if (resp.isSuccessful && respBody.isNotBlank()) {
                    val json = JSONObject(respBody)
                    val status = json.optString("status")
                    val token = json.optString("token")
                    if (status == "activated" && token.isNotBlank()) {
                        prefs.edit().putString("jwt_token", token).putString("active_cd_key", cdKey).remove("pending_cd_key").apply()
                        return ActivationResult.Success(token)
                    } else if (status == "pending_approval") {
                        return ActivationResult.Pending("Admin ၏ အတည်ပြုချက်ကို စောင့်ဆိုင်းနေဆဲ ဖြစ်ပါသည်")
                    }
                }
            } catch (_: Exception) {}

            ActivationResult.Pending("ချိတ်ဆက်မှု စစ်ဆေးနေပါသည်...")
        }
    }

    suspend fun verifyCurrentLicense(): Boolean {
        if (!checkSecurityIntegrity()) return false
        val cdKey = getActiveCdKey() ?: return isActivated()
        val deviceFingerprint = getDeviceFingerprint()
        return try {
            val request = VerifyLicenseRequest(cdKey, deviceFingerprint)
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
                isActivated()
            }
        } catch (_: Exception) {
            isActivated()
        }
    }

    suspend fun autoRestoreLicense(): Boolean = withContext(Dispatchers.IO) {
        if (isActivated()) return@withContext true
        val deviceFingerprint = getDeviceFingerprint()
        val deviceModel = getDeviceModel()

        // 1. Try Retrofit with Moshi
        try {
            val request = RestoreLicenseRequest(
                device_fingerprint = deviceFingerprint,
                device_model = deviceModel
            )
            val response = NetworkClient.licenseApi.restoreLicense(request)
            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!
                if (body.status == "activated" && !body.token.isNullOrBlank()) {
                    if (verifyToken(body.token)) {
                        prefs.edit()
                            .putString("jwt_token", body.token)
                            .putString("active_cd_key", body.cd_key ?: "")
                            .remove("pending_cd_key")
                            .remove("expired_warning")
                            .apply()
                        return@withContext true
                    }
                } else if (body.status == "expired") {
                    prefs.edit()
                        .putString("expired_warning", body.message ?: "လိုင်စင် သက်တမ်း ကုန်ဆုံးသွားပါပြီ။ ဆက်လက်အသုံးပြုရန် လိုင်စင် အသစ် ဝယ်ယူပါ")
                        .apply()
                }
            }
        } catch (_: Exception) {
            // Fallback to direct OkHttp
        }

        // 2. Direct OkHttp + JSONObject fallback
        try {
            val jsonPayload = JSONObject().apply {
                put("device_fingerprint", deviceFingerprint)
                put("device_model", deviceModel)
            }.toString()
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val req = okhttp3.Request.Builder()
                .url("${NetworkClient.BASE_URL}/restore")
                .header("User-Agent", "3DLedger-App/1.0")
                .post(jsonPayload.toRequestBody(mediaType))
                .build()
            val resp = NetworkClient.okHttpClient.newCall(req).execute()
            val respBody = resp.body?.string().orEmpty()
            if (resp.isSuccessful && respBody.isNotBlank()) {
                val json = JSONObject(respBody)
                if (json.optString("status") == "activated") {
                    val token = json.optString("token")
                    val cdKey = json.optString("cd_key")
                    if (token.isNotBlank() && verifyToken(token)) {
                        prefs.edit()
                            .putString("jwt_token", token)
                            .putString("active_cd_key", cdKey)
                            .remove("pending_cd_key")
                            .remove("expired_warning")
                            .apply()
                        return@withContext true
                    }
                } else if (json.optString("status") == "expired") {
                    prefs.edit()
                        .putString("expired_warning", json.optString("message", "လိုင်စင် သက်တမ်း ကုန်ဆုံးသွားပါပြီ။ ဆက်လက်အသုံးပြုရန် လိုင်စင် အသစ် ဝယ်ယူပါ"))
                        .apply()
                }
            }
        } catch (_: Exception) {}

        false
    }
}

