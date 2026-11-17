package com.matchday.tv

import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import com.matchday.tv.data.USER_AGENT
import com.matchday.tv.data.XtreamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Checks the GitHub "latest" release for a newer build, downloads it and hands it to the installer. */
class Updater(private val app: Application, private val scope: CoroutineScope) {

    init {
        current = this
    }

    @Suppress("DEPRECATION")
    private val info = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()

    @Suppress("DEPRECATION")
    val currentBuild: Int = info?.versionCode ?: 0
    val versionName: String = info?.versionName ?: "?"

    private val repo: String = runCatching {
        app.packageManager.getApplicationInfo(app.packageName, PackageManager.GET_META_DATA).metaData?.getString("updateRepo")
    }.getOrNull()?.takeIf { it.contains('/') } ?: "blackandamber2020-jpg/matchday"

    var latestBuild by mutableStateOf<Int?>(null)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    private var apkUrl: String? = null
    /** The asset's own API address: unique per build, so no cache can hand back an older file. */
    private var assetApiUrl: String? = null

    val available: Boolean get() = (latestBuild ?: 0) > currentBuild

    /** [silent] checks (at start-up) say nothing unless there is an update. */
    fun check(silent: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            if (!silent) status = "Checking for updates…"
            try {
                val (build, urls) = withContext(Dispatchers.IO) { fetchLatest() }
                latestBuild = build
                apkUrl = urls.first
                assetApiUrl = urls.second
                status = when {
                    build > currentBuild -> "Build $build is available (you have build $currentBuild)."
                    silent -> status
                    else -> "You're up to date (build $currentBuild)."
                }
            } catch (e: Exception) {
                if (!silent) status = "Couldn't check for updates (${e.message}). A new build may be publishing; try again in a minute."
            }
            busy = false
        }
    }

    private fun fetchLatest(): Pair<Int, Pair<String, String?>> {
        val request = Request.Builder()
            .url("https://api.github.com/repos/$repo/releases/latest")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/vnd.github+json")
            .build()
        val body = XtreamClient.http.newCall(request).execute().use {
            if (!it.isSuccessful) throw IOException("HTTP ${it.code}")
            it.body?.string().orEmpty()
        }
        val root = Json.parseToJsonElement(body) as? JsonObject ?: throw IOException("unexpected reply")
        val name = (root["name"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val build = Regex("build (\\d+)").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: throw IOException("no build number")
        val asset = (root["assets"] as? JsonArray)?.mapNotNull { it as? JsonObject }?.firstOrNull { a ->
            (a["browser_download_url"] as? JsonPrimitive)?.contentOrNull?.endsWith(".apk") == true
        } ?: throw IOException("no download in the release")
        val url = (asset["browser_download_url"] as JsonPrimitive).content
        val api = (asset["url"] as? JsonPrimitive)?.contentOrNull
        return build to (url to api)
    }

    /** True when Fire TV lets ShedTV install apps (needed once, before the first in-app update). */
    val canInstall: Boolean
        get() = android.os.Build.VERSION.SDK_INT < 26 || app.packageManager.canRequestPackageInstalls()

    fun install() {
        if (apkUrl == null || busy) return
        if (!canInstall) {
            // The usual reason "nothing happens": open the switch for ShedTV directly.
            status = "One-time step: turn ShedTV ON on the screen that opens, press Back, then press Install again."
            val opened = runCatching {
                app.startActivity(
                    Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + app.packageName))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }.isSuccess
            if (!opened) {
                status = "One-time step: on the Fire TV go to Settings → My Fire TV → Developer Options → Install unknown apps, turn ShedTV ON, then press Install again."
            }
            return
        }
        scope.launch {
            busy = true
            try {
                val file = withContext(Dispatchers.IO) { download() }
                // Make sure we really have the newer build before handing it over.
                @Suppress("DEPRECATION")
                val info = app.packageManager.getPackageArchiveInfo(file.path, 0)
                    ?: throw IOException("the download isn't a valid app file")
                @Suppress("DEPRECATION")
                val code = info.versionCode
                if (info.packageName != app.packageName) throw IOException("the download is a different app (${info.packageName})")
                if (code <= currentBuild) throw IOException("the download was build $code, not newer than this one. Try again in a minute")
                status = "Installing build $code…"
                withContext(Dispatchers.IO) { installWithSession(file) }
            } catch (e: Exception) {
                status = "Update failed: ${e.message}."
                // Last resort: the older hand-off to the Fire TV installer.
                val file = File(File(app.cacheDir, "update"), "shedtv.apk")
                if (e !is IOException && file.exists()) runCatching { legacyInstall(file) }
            }
            busy = false
        }
    }

    private fun installWithSession(file: File) {
        val installer = app.packageManager.packageInstaller
        val params = android.content.pm.PackageInstaller.SessionParams(android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(app.packageName)
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("shedtv.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                (if (android.os.Build.VERSION.SDK_INT >= 31) android.app.PendingIntent.FLAG_MUTABLE else 0)
            val pending = android.app.PendingIntent.getBroadcast(app, id, Intent(app, InstallResultReceiver::class.java), flags)
            session.commit(pending.intentSender)
        }
    }

    private fun legacyInstall(file: File) {
        val uri = FileProvider.getUriForFile(app, app.packageName + ".files", file)
        app.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    companion object {
        @Volatile private var current: Updater? = null

        /** Install results arrive in a broadcast; show them in Settings. */
        fun report(message: String) {
            val u = current ?: return
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                u.status = message
                u.busy = false
            }
        }
    }

    private suspend fun download(): File {
        val dir = File(app.cacheDir, "update").apply { mkdirs() }
        val file = File(dir, "shedtv.apk")
        file.delete()
        val api = assetApiUrl
        val request = if (api != null) {
            Request.Builder().url(api).header("User-Agent", USER_AGENT).header("Accept", "application/octet-stream").build()
        } else {
            Request.Builder().url(apkUrl!! + "?t=" + System.currentTimeMillis()).header("User-Agent", USER_AGENT).build()
        }
        XtreamClient.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body ?: throw IOException("empty download")
            val total = body.contentLength()
            var done = 0L
            var shown = -1
            file.outputStream().use { out ->
                val input = body.byteStream()
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val pct = if (total > 0) (done * 100 / total).toInt() else -1
                    if (pct != shown) {
                        shown = pct
                        withContext(Dispatchers.Main) { status = if (pct >= 0) "Downloading update… $pct%" else "Downloading update…" }
                    }
                }
            }
        }
        if (file.length() < 1_000_000) throw IOException("download was incomplete")
        return file
    }
}
