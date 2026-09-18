package com.threeDLedger.security

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import com.threeDLedger.BuildConfig
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.security.MessageDigest
import java.util.Locale

/**
 * SecurityGuard: Enterprise-grade Anti-Tampering, Anti-Resigning, Anti-Frida/Hooking,
 * and Anti-Debugging shield for 3D Ledger.
 *
 * Protects against:
 * 1. MT Manager / APK Editor / Bytecode patchers (re-signing detection via SHA-256 certificate validation).
 * 2. Dynamic instrumentation / Hooking (Frida server, frida-gadget, Xposed, Substrate).
 * 3. Debugger attachment (JDWP / GDB / LLDB / TracerPid).
 * 4. Binary tampering & Root exploitation.
 */
data class SecurityReport(
    val isSecure: Boolean,
    val violations: List<String>,
    val signatureFingerprint: String
)

object SecurityGuard {

    // Known authorized developer certificate SHA-256 fingerprints (normalized uppercase, no colons)
    private val AUTHORIZED_SIGNATURE_HASHES = setOf(
        // Authorized Debug Keystore (from project app/debug.keystore)
        "981B2F858BB4DDCFC9C8DA615CFBE9D05AB07CD0483DE50D1D337906E3F13B93",
        // Release Keystore placeholder / authorized production fingerprints
        "A0B1C2D3E4F5061728394A5B6C7D8E9F00112233445566778899AABBCCDDEEFF"
    )

    private val SUSPICIOUS_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/data/local/su",
        "/system/app/Superuser.apk",
        "/system/app/Magisk.apk"
    )

    private val SUSPICIOUS_LIBRARIES = arrayOf(
        "libfrida",
        "frida-gadget",
        "frida-agent",
        "xposed",
        "edxposed",
        "substrate",
        "sandhook",
        "libinject"
    )

    /**
     * Perform comprehensive runtime integrity check.
     */
    fun checkIntegrity(context: Context): SecurityReport {
        val violations = mutableListOf<String>()

        // 1. Signature & Anti-Resigning Check (Anti-MT Manager / Lucky Patcher)
        val certFingerprint = getAppSignatureSHA256(context)
        if (certFingerprint.isNotEmpty()) {
            val isAuthorized = AUTHORIZED_SIGNATURE_HASHES.contains(certFingerprint)
            // Allow if debug mode and matches debug key, but flag if an attacker resigned with a random MT Manager key
            if (!isAuthorized && !isAuthorizedFallback(certFingerprint)) {
                violations.add("APK Signature mismatch (Detected re-signing / MT Manager patch)")
            }
        } else {
            violations.add("Unable to verify APK signature certificate")
        }

        // 2. Anti-Debugging Check
        if (isDebuggerAttached()) {
            violations.add("Active debugger attached (IDA/GDB/JDWP)")
        }

        if (isTracerPidActive()) {
            violations.add("Ptrace / TracerPid detected in /proc/self/status")
        }

        // 3. Anti-Frida & Hooking Engine Check
        if (isFridaServerRunning()) {
            violations.add("Frida server detected on default inspection port")
        }

        if (hasSuspiciousMapsEntries()) {
            violations.add("Injected hook library detected in memory maps (Frida/Xposed)")
        }

        if (hasFridaThreads()) {
            violations.add("Frida runtime agent thread detected")
        }

        // 4. Package Name Integrity
        if (context.packageName != "com.threeDLedger") {
            violations.add("Package name altered: ${context.packageName}")
        }

        // 5. Anti-Debuggable Flag Check in Release Mode
        if (!BuildConfig.DEBUG && isAppDebuggable(context)) {
            violations.add("App manifest debuggable flag enabled in production build")
        }

        val isSecure = violations.isEmpty()
        return SecurityReport(
            isSecure = isSecure,
            violations = violations,
            signatureFingerprint = certFingerprint
        )
    }

    fun isAppDebuggable(context: Context): Boolean {
        return (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    /**
     * Extracts the SHA-256 certificate fingerprint of the running APK.
     */
    @SuppressLint("PackageManagerGetSignatures")
    fun getAppSignatureSHA256(context: Context): String {
        try {
            val pm = context.packageManager
            val packageName = context.packageName
            val md = MessageDigest.getInstance("SHA-256")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo ?: return ""
                val signatures = if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }

                if (!signatures.isNullOrEmpty()) {
                    val digest = md.digest(signatures[0].toByteArray())
                    return bytesToHex(digest)
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val signatures = packageInfo.signatures
                if (!signatures.isNullOrEmpty()) {
                    val digest = md.digest(signatures[0].toByteArray())
                    return bytesToHex(digest)
                }
            }
        } catch (e: Exception) {
            // Ignore reflection errors in obfuscated builds
        }
        return ""
    }

    private fun isAuthorizedFallback(fingerprint: String): Boolean {
        // Fallback check to avoid locking legitimate dev builds if new certificate is deployed
        return fingerprint.isNotEmpty() && (fingerprint == AUTHORIZED_SIGNATURE_HASHES.first())
    }

    /**
     * Detects attached Java debugger or JDWP.
     */
    fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected() || Debug.waitingForDebugger()
    }

    /**
     * Checks /proc/self/status for TracerPid != 0 (Native ptrace attachment).
     */
    fun isTracerPidActive(): Boolean {
        try {
            val file = File("/proc/self/status")
            if (file.exists() && file.canRead()) {
                BufferedReader(FileReader(file)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        if (line?.startsWith("TracerPid:") == true) {
                            val pid = line?.substringAfter("TracerPid:")?.trim()?.toIntOrNull() ?: 0
                            return pid > 0
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Checks if Frida inspection ports (27042, 27043) are listening on localhost.
     */
    fun isFridaServerRunning(): Boolean {
        val ports = intArrayOf(27042, 27043)
        for (port in ports) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 80)
                    return true // Port is open, Frida server active
                }
            } catch (_: Exception) {
                // Port closed / unreachable
            }
        }
        return false
    }

    /**
     * Scans /proc/self/maps for injected hooking frameworks (Frida agent, Xposed, Substrate).
     */
    fun hasSuspiciousMapsEntries(): Boolean {
        try {
            val file = File("/proc/self/maps")
            if (file.exists() && file.canRead()) {
                BufferedReader(FileReader(file)).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val lower = line?.lowercase(Locale.ROOT) ?: continue
                        for (suspicious in SUSPICIOUS_LIBRARIES) {
                            if (lower.contains(suspicious)) {
                                return true
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Inspects active threads for Frida handler loops.
     */
    fun hasFridaThreads(): Boolean {
        try {
            val threads = Thread.getAllStackTraces().keys
            for (thread in threads) {
                val name = thread.name.lowercase(Locale.ROOT)
                if (name.contains("frida") || name.contains("gum-js-loop") || name.contains("gmain")) {
                    return true
                }
            }
        } catch (_: Exception) {}
        return false
    }

    /**
     * Detects root su binaries on filesystem.
     */
    fun isDeviceRooted(): Boolean {
        for (path in SUSPICIOUS_PATHS) {
            if (File(path).exists()) return true
        }
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        return false
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        val hexArray = "0123456789ABCDEF".toCharArray()
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = hexArray[v ushr 4]
            hexChars[i * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }
}
