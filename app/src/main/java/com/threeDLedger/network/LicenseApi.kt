package com.threeDLedger.network

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class ActivationRequest(
    val cd_key: String,
    val device_fingerprint: String,
    val device_model: String? = null
)

@JsonClass(generateAdapter = true)
data class ActivationResponse(
    val status: String? = null,
    val token: String? = null,
    val expires_at: Long? = null,
    val device_migrated: Boolean? = null,
    val remaining_days: Int? = null,
    val message: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class CheckStatusRequest(
    val cd_key: String,
    val device_fingerprint: String
)

@JsonClass(generateAdapter = true)
data class CheckStatusResponse(
    val status: String? = null,
    val token: String? = null,
    val expires_at: Long? = null,
    val message: String? = null,
    val error: String? = null
)

@JsonClass(generateAdapter = true)
data class VerifyLicenseRequest(
    val cd_key: String,
    val device_fingerprint: String
)

@JsonClass(generateAdapter = true)
data class VerifyLicenseResponse(
    val valid: Boolean = false,
    val reason: String? = null,
    val message: String? = null,
    val expires_at: Long? = null
)

interface LicenseApi {
    @POST("/activate")
    suspend fun activateLicense(@Body request: ActivationRequest): Response<ActivationResponse>

    @POST("/check-status")
    suspend fun checkStatus(@Body request: CheckStatusRequest): Response<CheckStatusResponse>

    @POST("/verify")
    suspend fun verifyLicense(@Body request: VerifyLicenseRequest): Response<VerifyLicenseResponse>
}
