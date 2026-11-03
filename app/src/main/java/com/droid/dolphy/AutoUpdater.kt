package com.droid.dolphy

import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class DeadcowReleaseInfo(
    val versionCode: Long,
    val versionName: String,
    val tagName: String,
    val apkUrl: String,
    val releaseNotes: String = ""
)

sealed class AutoUpdateState {
    object Idle : AutoUpdateState()
    object Checking : AutoUpdateState()
    data class Available(val release: DeadcowReleaseInfo) : AutoUpdateState()
    data class Downloading(val release: DeadcowReleaseInfo, val progressPercent: Int) : AutoUpdateState()
    data class Installing(val release: DeadcowReleaseInfo) : AutoUpdateState()
    data class ReadyToInstall(val release: DeadcowReleaseInfo, val apkFile: File) : AutoUpdateState()
    object UpToDate : AutoUpdateState()
    data class Error(val message: String) : AutoUpdateState()
}

object AutoUpdater {
    private const val TAG = "DeadcowAutoUpdater"
    private const val GITHUB_RELEASES_API = "https://api.github.com/repos/zitk9/Deadcow-App/releases"
    private const val VERSION_JSON_GHPAGES = "https://zitk9.github.io/Deadcow-App/version.json"
    private const val BUILD_GRADLE_RAW = "https://raw.githubusercontent.com/zitk9/Deadcow-App/main/app/build.gradle.kts"
    private const val PREFS_NAME = "DeadcowUpdaterPrefs"
    private const val KEY_AUTO_UPDATE_ENABLED = "auto_update_enabled"
    private const val KEY_DISMISSED_VERSION_CODE = "dismissed_version_code"
    private const val NOTIFICATION_CHANNEL_ID = "deadcow_updates"
    private const val NOTIFICATION_ID = 202611

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<AutoUpdateState>(AutoUpdateState.Idle)
    val state: StateFlow<AutoUpdateState> = _state.asStateFlow()

    @Volatile
    private var hasCheckedThisSession = false

    fun isAutoUpdateEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_AUTO_UPDATE_ENABLED, true)
    }

    fun setAutoUpdateEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTO_UPDATE_ENABLED, enabled)
            .apply()
    }

    fun getDismissedVersionCode(context: Context): Long {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(KEY_DISMISSED_VERSION_CODE, 0L)
    }

    fun setDismissedVersionCode(context: Context, code: Long) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_DISMISSED_VERSION_CODE, code)
            .apply()
    }

    fun checkAndAutoUpdateOnLaunch(context: Context) {
        if (hasCheckedThisSession) return
        if (!isAutoUpdateEnabled(context)) return
        hasCheckedThisSession = true
        checkForUpdates(context.applicationContext, manualTrigger = false)
    }

    fun checkForUpdates(context: Context, manualTrigger: Boolean = true) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                _state.value = AutoUpdateState.Checking
                val currentInfo = getCurrentPackageVersion(appContext)
                val remoteRelease = fetchLatestReleaseInfo()

                if (remoteRelease == null) {
                    _state.value = AutoUpdateState.Idle
                    if (manualTrigger) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                appContext,
                                "Impossible de vérifier les mises à jour GitHub pour le moment.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    return@launch
                }

                val isNewer = remoteRelease.versionCode > currentInfo.first ||
                    (remoteRelease.versionCode == currentInfo.first &&
                        isSemanticVersionNewer(remoteRelease.versionName, currentInfo.second))

                if (!isNewer) {
                    _state.value = AutoUpdateState.UpToDate
                    if (manualTrigger) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                appContext,
                                "Deadcow est déjà à jour (v${currentInfo.second})",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    return@launch
                }

                // If this is an auto-check at startup and the user already dismissed this specific version, do not prompt repeatedly in an infinite loop
                val dismissedCode = getDismissedVersionCode(appContext)
                if (!manualTrigger && remoteRelease.versionCode <= dismissedCode) {
                    _state.value = AutoUpdateState.Idle
                    return@launch
                }

                showUpdateNotification(appContext, remoteRelease)
                _state.value = AutoUpdateState.Available(remoteRelease)
            } catch (e: Exception) {
                Log.e(TAG, "Auto-update check failed", e)
                _state.value = AutoUpdateState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    fun showUpdateNotification(context: Context, release: DeadcowReleaseInfo) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Mises à jour Deadcow",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifications des nouvelles versions et correctifs Deadcow"
                }
                nm.createNotificationChannel(channel)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                ) {
                    return
                }
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pi = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notes = release.releaseNotes.takeIf { it.isNotBlank() }
                ?: "Une nouvelle version de Deadcow (v${release.versionName}) est disponible avec les correctifs Bluetooth et Samsung Watch !"

            val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Mise à jour v${release.versionName} disponible")
                .setContentText("Nouveaux correctifs Deadcow disponibles")
                .setStyle(NotificationCompat.BigTextStyle().bigText("Deadcow v${release.versionName} est disponible !\n\n$notes"))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()

            nm.notify(NOTIFICATION_ID, notification)
        }.onFailure {
            Log.e(TAG, "Failed to show update notification", it)
        }
    }

    fun dismissPrompt(context: Context? = null, release: DeadcowReleaseInfo? = null) {
        if (context != null && release != null) {
            setDismissedVersionCode(context, release.versionCode)
        }
        _state.value = AutoUpdateState.Idle
    }

    fun launchBrowserDownload(context: Context, release: DeadcowReleaseInfo? = null) {
        runCatching {
            val url = release?.apkUrl?.takeIf { it.isNotBlank() } ?: "https://github.com/zitk9/Deadcow-App/releases/latest"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            Log.e(TAG, "Failed to launch browser download", it)
            Toast.makeText(context, "Impossible d'ouvrir le navigateur", Toast.LENGTH_SHORT).show()
        }
    }

    fun downloadViaSystemDownloadManager(context: Context, release: DeadcowReleaseInfo) {
        runCatching {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm == null) {
                launchBrowserDownload(context, release)
                return
            }
            val uri = Uri.parse(release.apkUrl)
            val fileName = "Deadcow-v${release.versionName}-b${release.versionCode}.apk"
            val request = DownloadManager.Request(uri)
                .setTitle("Deadcow v${release.versionName}")
                .setDescription("Téléchargement de la mise à jour Deadcow...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
                .setMimeType("application/vnd.android.package-archive")
            dm.enqueue(request)
            Toast.makeText(context, "Téléchargement lancé en arrière-plan !", Toast.LENGTH_LONG).show()
            dismissPrompt(context, release)
        }.onFailure {
            Log.e(TAG, "DownloadManager failed", it)
            launchBrowserDownload(context, release)
        }
    }

    fun startDownloadAndInstall(context: Context, release: DeadcowReleaseInfo) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                _state.value = AutoUpdateState.Downloading(release, 0)
                val apkFile = downloadApk(appContext, release)
                if (apkFile == null || !apkFile.exists() || apkFile.length() < 100_000L) {
                    _state.value = AutoUpdateState.Error("Échec du téléchargement de l'APK")
                    return@launch
                }

                _state.value = AutoUpdateState.Installing(release)
                val installedSilently = trySilentInstall(appContext, apkFile)
                if (!installedSilently) {
                    _state.value = AutoUpdateState.ReadyToInstall(release, apkFile)
                    withContext(Dispatchers.Main) {
                        launchPackageInstaller(appContext, apkFile)
                    }
                } else {
                    _state.value = AutoUpdateState.UpToDate
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download and install failed", e)
                _state.value = AutoUpdateState.Error(e.message ?: "Erreur d'installation")
            }
        }
    }

    private fun getCurrentPackageVersion(context: Context): Pair<Long, String> {
        return runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pi.versionCode.toLong()
            }
            val name = pi.versionName?.takeIf { it.isNotBlank() } ?: "1.0"
            code to name
        }.getOrDefault(1L to "1.0")
    }

    private fun isSemanticVersionNewer(remote: String, local: String): Boolean {
        val rParts = remote.removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }
        val lParts = local.removePrefix("v").split(".", "-").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(rParts.size, lParts.size)
        for (i in 0 until maxLen) {
            val r = rParts.getOrElse(i) { 0 }
            val l = lParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    private fun fetchLatestReleaseInfo(): DeadcowReleaseInfo? {
        // 1. Check GitHub Releases API first (checks both version.json asset and Deadcow-release.apk asset)
        runCatching {
            val jsonText = httpGetText(GITHUB_RELEASES_API)
            val releases = JSONArray(jsonText)
            for (i in 0 until releases.length()) {
                val rel = releases.getJSONObject(i)
                val tagName = rel.optString("tag_name", "")
                val body = rel.optString("body", "")
                val assets = rel.optJSONArray("assets") ?: continue

                var apkUrl: String? = null
                var versionJsonUrl: String? = null

                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    val name = asset.optString("name", "")
                    val url = asset.optString("browser_download_url", "")
                    if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                        apkUrl = url
                    } else if (name == "version.json" && url.isNotBlank()) {
                        versionJsonUrl = url
                    }
                }

                if (apkUrl != null) {
                    if (versionJsonUrl != null) {
                        runCatching {
                            val vJson = JSONObject(httpGetText(versionJsonUrl))
                            return DeadcowReleaseInfo(
                                versionCode = vJson.optLong("versionCode", parseVersionCodeFromTagOrBody(tagName, body)),
                                versionName = vJson.optString("versionName", parseVersionNameFromTag(tagName)),
                                tagName = tagName,
                                apkUrl = apkUrl,
                                releaseNotes = body
                            )
                        }
                    }

                    val gradlePair = fetchGradleVersionFromMain()
                    val parsedCode = parseVersionCodeFromTagOrBody(tagName, body)
                        .takeIf { it > 0L }
                        ?: gradlePair?.first
                        ?: 0L
                    val parsedName = parseVersionNameFromTag(tagName)
                        .takeIf { it.isNotBlank() }
                        ?: gradlePair?.second
                        ?: "1.2"

                    return DeadcowReleaseInfo(
                        versionCode = parsedCode,
                        versionName = parsedName,
                        tagName = tagName,
                        apkUrl = apkUrl,
                        releaseNotes = body
                    )
                }
            }
        }.onFailure {
            Log.w(TAG, "GitHub Releases API lookup failed: ${it.message}")
        }

        // 2. Fallback to gh-pages version.json if API is rate-limited
        runCatching {
            val vJson = JSONObject(httpGetText(VERSION_JSON_GHPAGES))
            val apkUrl = vJson.optString("apkUrl", "")
            if (apkUrl.isNotBlank()) {
                return DeadcowReleaseInfo(
                    versionCode = vJson.optLong("versionCode", 0L),
                    versionName = vJson.optString("versionName", "1.2"),
                    tagName = vJson.optString("tagName", "latest"),
                    apkUrl = apkUrl
                )
            }
        }

        return null
    }

    private fun fetchGradleVersionFromMain(): Pair<Long, String>? {
        return runCatching {
            val gradleText = httpGetText(BUILD_GRADLE_RAW)
            val code = Regex("""versionCode\s*=\s*(\d+)""")
                .find(gradleText)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return null
            val name = Regex("""versionName\s*=\s*"([^"]+)"""")
                .find(gradleText)?.groupValues?.getOrNull(1) ?: return null
            code to name
        }.getOrNull()
    }

    private fun parseVersionCodeFromTagOrBody(tag: String, body: String): Long {
        Regex("""-b(\d+)""").find(tag)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return it }
        Regex("""versionCode\s*=\s*(\d+)""").find(body)?.groupValues?.getOrNull(1)?.toLongOrNull()?.let { return it }
        return 0L
    }

    private fun parseVersionNameFromTag(tag: String): String {
        return tag
            .removePrefix("v")
            .replace(Regex("""-b\d+$"""), "")
            .replace(Regex("""-pre$"""), "")
            .trim()
    }

    private fun httpGetText(urlString: String): String {
        var currentUrl = urlString
        var redirects = 0
        while (redirects < 5) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json, application/json, text/plain, */*")
                setRequestProperty("User-Agent", "Deadcow-Android-Updater")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location") ?: break
                conn.disconnect()
                currentUrl = loc
                redirects++
                continue
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        }
        throw IllegalStateException("Too many redirects for $urlString")
    }

    private fun downloadApk(context: Context, release: DeadcowReleaseInfo): File? {
        val updateDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, "updates").apply { mkdirs() }
        val targetFile = File(updateDir, "Deadcow-v${release.versionName}-b${release.versionCode}.apk")
        if (targetFile.exists()) targetFile.delete()

        return runCatching {
            var currentUrl = release.apkUrl
            var redirects = 0
            var conn: HttpURLConnection? = null
            while (redirects < 6) {
                val c = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 25_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Deadcow-Android-Updater")
                }
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: break
                    c.disconnect()
                    currentUrl = loc
                    redirects++
                    continue
                }
                conn = c
                break
            }

            val activeConn = conn ?: return null
            val responseCode = activeConn.responseCode
            if (responseCode !in 200..299) {
                activeConn.disconnect()
                throw java.io.IOException("HTTP error code $responseCode")
            }

            val totalBytes = activeConn.contentLengthLong
            var downloadedBytes = 0L
            var lastReportedPercent = -1

            _state.value = AutoUpdateState.Downloading(release, 0)

            activeConn.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (totalBytes > 0) {
                            val pct = ((downloadedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (pct != lastReportedPercent) {
                                lastReportedPercent = pct
                                _state.value = AutoUpdateState.Downloading(release, pct)
                            }
                        }
                    }
                    output.flush()
                }
            }
            activeConn.disconnect()
            targetFile
        }.onFailure {
            Log.e(TAG, "Failed to download APK", it)
        }.getOrNull()
    }

    private fun trySilentInstall(context: Context, apkFile: File): Boolean {
        val remoteTmpPath = "/data/local/tmp/deadcow_auto_update.apk"

        // 1. Try silent install via Shizuku if running and granted
        runCatching {
            if (Shizuku.pingBinder() &&
                Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                val cmd = "cp '${apkFile.absolutePath}' '$remoteTmpPath' && chmod 644 '$remoteTmpPath' && pm install -r '$remoteTmpPath' && rm -f '$remoteTmpPath'"
                if (ShizukuHelper.runShellCommand(cmd)) {
                    Log.i(TAG, "Silent update succeeded via Shizuku")
                    return true
                }
            }
        }

        // 2. Try silent install via Root (`su -c`) if rooted
        runCatching {
            val cmd = "cp '${apkFile.absolutePath}' '$remoteTmpPath' && chmod 644 '$remoteTmpPath' && pm install -r '$remoteTmpPath' && rm -f '$remoteTmpPath'"
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            val exitCode = proc.waitFor()
            if (exitCode == 0) {
                Log.i(TAG, "Silent update succeeded via Root")
                return true
            }
        }

        return false
    }

    fun launchPackageInstaller(context: Context, apkFile: File) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                !context.packageManager.canRequestPackageInstalls()
            ) {
                Toast.makeText(
                    context,
                    "Autorisez l'installation de mises à jour pour Deadcow",
                    Toast.LENGTH_LONG
                ).show()
                val permIntent = Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(permIntent)
                return
            }

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        }.onFailure { e ->
            Log.e(TAG, "Failed to launch package installer", e)
            Toast.makeText(
                context,
                "Erreur lors du lancement de la mise à jour : ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
}
