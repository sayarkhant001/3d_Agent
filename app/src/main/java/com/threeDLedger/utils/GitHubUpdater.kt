package com.threeDLedger.utils

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object GitHubUpdater {

    data class UpdateInfo(val version: String, val releaseNotes: String, val downloadUrl: String)

    // ── Check GitHub for the latest release ─────────────────────────────────
    suspend fun checkForUpdates(owner: String, repo: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().readText()
                val jsonObject = JSONObject(response)

                val tagName = jsonObject.getString("tag_name")
                val body = try { jsonObject.getString("body") } catch (e: Exception) { "" }
                val assets = jsonObject.getJSONArray("assets")

                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (apkUrl.isNotEmpty()) {
                    return@withContext UpdateInfo(tagName, body, apkUrl)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext null
    }

    // ── Check if we can install unknown apps ────────────────────────────────
    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true // Below Android 8 no per-app permission needed
        }
    }

    // ── Open the "Install unknown apps" settings for this app ───────────────
    fun openInstallUnknownAppsSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(context, "ဆက်တင်ကိုဖွင့်မရပါ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Download APK to internal cache (no storage permission needed on any API level).
     * Reports progress via [onProgress] (0–100).
     * Returns the local File on success, null on failure.
     */
    suspend fun downloadApkToCache(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            connection.instanceFollowRedirects = true
            connection.connect()

            val total = connection.contentLength
            val outFile = File(context.cacheDir, "update.apk")
            if (outFile.exists()) outFile.delete()

            var downloaded = 0
            connection.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytes: Int
                    while (input.read(buffer).also { bytes = it } != -1) {
                        output.write(buffer, 0, bytes)
                        downloaded += bytes
                        if (total > 0) {
                            onProgress((downloaded * 100 / total).coerceIn(0, 99))
                        }
                    }
                }
            }
            onProgress(100)
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Launch the system APK installer for a file in the app's cache dir.
     * Requires canInstallUnknownApps() == true before calling.
     */
    fun installApkFromCache(context: Context, apkFile: File) {
        if (!apkFile.exists()) {
            Toast.makeText(context, "APK ဖိုင် မတွေ့ပါ", Toast.LENGTH_SHORT).show()
            return
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Installer ဖွင့်မရပါ: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
