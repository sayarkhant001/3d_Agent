package com.threeDLedger.logic

import android.content.Context
import com.threeDLedger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AppUpdateInfo(
    val hasUpdate: Boolean,
    val latestVersionName: String,
    val latestVersionCode: Int,
    val releaseNotes: String,
    val downloadUrl: String? = null,
    val telegramBotUrl: String = "https://t.me/threed_ledger_bot?start=download"
)

object AppUpdateChecker {
    private const val UPDATE_API_URL = "https://3d-scraper-worker.khaingkhantkyaw001.workers.dev/api/app/latest"

    suspend fun checkForUpdate(context: Context): AppUpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(UPDATE_API_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                if (root.optString("status") == "ok" && !root.isNull("release")) {
                    val release = root.getJSONObject("release")
                    val remoteVersionCode = release.optInt("version_code", 0)
                    val remoteVersionName = release.optString("version_name", "Latest")
                    val releaseNotes = release.optString("release_notes", "စနစ် စွမ်းဆောင်ရည်နှင့် လုံခြုံရေး မြှင့်တင်မှုများ")
                    val downloadUrl = release.optString("download_url", "").ifBlank { null }

                    val currentVersionCode = BuildConfig.VERSION_CODE
                    val hasUpdate = remoteVersionCode > currentVersionCode

                    return@withContext AppUpdateInfo(
                        hasUpdate = hasUpdate,
                        latestVersionName = remoteVersionName,
                        latestVersionCode = remoteVersionCode,
                        releaseNotes = releaseNotes,
                        downloadUrl = downloadUrl
                    )
                }
            }
        } catch (_: Exception) {
            // Silently ignore network failures during background update check
        }
        return@withContext null
    }
}
