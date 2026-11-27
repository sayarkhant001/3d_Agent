package com.threeDLedger

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import com.threeDLedger.logic.LicenseManager
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LicenseManagerSecurityTest {

    private lateinit var context: Context
    private lateinit var licenseManager: LicenseManager

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("license_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("time_integrity_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        licenseManager = LicenseManager(context)
    }

    private fun encodeUrl(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun buildSignedJwt(payload: JSONObject, secret: String = LicenseManager.getVerificationSecret()): String {
        val header = JSONObject().put("alg", "HS256").put("typ", "JWT")
        val headerPart = encodeUrl(header.toString().toByteArray(StandardCharsets.UTF_8))
        val payloadPart = encodeUrl(payload.toString().toByteArray(StandardCharsets.UTF_8))
        val signingInput = "$headerPart.$payloadPart"

        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val sig = encodeUrl(mac.doFinal(signingInput.toByteArray(StandardCharsets.US_ASCII)))
        return "$signingInput.$sig"
    }

    @Test
    fun rejectsMalformedAndTamperedTokens() {
        assertFalse(licenseManager.verifyToken(""))
        assertFalse(licenseManager.verifyToken("not.a.jwt.token"))

        val payload = JSONObject()
            .put("cd_key", "TEST-1234-5678")
            .put("device_fingerprint", licenseManager.getDeviceFingerprint())
            .put("exp", (System.currentTimeMillis() / 1000L) + 86_400L)
        val badSigToken = buildSignedJwt(payload, secret = "wrong-secret")

        assertFalse(licenseManager.verifyToken(badSigToken))
    }

    @Test
    fun rejectsHardwareMismatchAndExpiredTokens() {
        val futureExp = (System.currentTimeMillis() / 1000L) + 86_400L
        val wrongDeviceToken = buildSignedJwt(
            JSONObject()
                .put("cd_key", "TEST-1234-5678")
                .put("device_fingerprint", "other_device_fingerprint_xyz")
                .put("exp", futureExp)
        )
        assertFalse(licenseManager.verifyToken(wrongDeviceToken))

        val expiredToken = buildSignedJwt(
            JSONObject()
                .put("cd_key", "TEST-1234-5678")
                .put("device_fingerprint", licenseManager.getDeviceFingerprint())
                .put("exp", (System.currentTimeMillis() / 1000L) - 60L)
        )
        assertFalse(licenseManager.verifyToken(expiredToken))
    }

    @Test
    fun acceptsValidSignedTokenAndPersistsDeviceChangeableFlag() {
        val futureExp = (System.currentTimeMillis() / 1000L) + 86_400L * 30L
        val validToken = buildSignedJwt(
            JSONObject()
                .put("cd_key", "PRO-YEAR-9999")
                .put("device_fingerprint", licenseManager.getDeviceFingerprint())
                .put("device_changeable", true)
                .put("exp", futureExp)
        )

        assertTrue(licenseManager.verifyToken(validToken))
        assertTrue(licenseManager.isDeviceChangeable())
    }
}
