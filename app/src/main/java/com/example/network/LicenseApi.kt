package com.example.network

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

@JsonClass(generateAdapter = true)
data class ActivationRequest(
    val cd_key: String,
    val device_fingerprint: String
)

@JsonClass(generateAdapter = true)
data class ActivationResponse(
    val token: String
)

interface LicenseApi {
    @POST("/")
    suspend fun activateLicense(@Body request: ActivationRequest): Response<ActivationResponse>
}
