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

    val available: Boolean get() = (latestBuild ?: 0) > currentBuild

    /** [silent] checks (at start-up) say nothing unless there is an update. */
    fun check(silent: Boolean) {
        if (busy) return
        scope.launch {
            busy = true
            if (!silent) status = "Checking for updates…"
            try {
                val (build, url) = withContext(Dispatchers.IO) { fetchLatest() }
                latestBuild = build
                apkUrl = url
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

    private fun fetchLatest(): Pair<Int, String> {
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
        val url = (root["assets"] as? JsonArray)?.firstNotNullOfOrNull { a ->
            ((a as? JsonObject)?.get("browser_download_url") as? JsonPrimitive)?.contentOrNull?.takeIf { it.endsWith(".apk") }
        } ?: throw IOException("no download in the release")
        return build to url
    }

    fun install() {
        val url = apkUrl ?: return
        if (busy) return
        scope.launch {
            busy = true
            try {
                val file = withContext(Dispatchers.IO) { download(url) }
                val uri = FileProvider.getUriForFile(app, app.packageName + ".files", file)
                val intent = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                app.startActivity(intent)
                status = "Follow the prompt to install. If nothing appears, allow ShedTV under Fire TV Settings → My Fire TV → Developer Options → Install unknown apps, then try again."
            } catch (e: Exception) {
                status = "Update failed (${e.message})."
            }
            busy = false
        }
    }

    private suspend fun download(url: String): File {
        val dir = File(app.cacheDir, "update").apply { mkdirs() }
        val file = File(dir, "shedtv.apk")
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
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
