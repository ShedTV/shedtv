package com.matchday.tv.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Downloads the provider's full XMLTV guide, caches it for a few hours, and parses it. */
class EpgRepository(private val context: Context) {

    suspend fun load(
        client: XtreamClient,
        channelIds: Set<String>,
        force: Boolean,
        progress: suspend (String) -> Unit,
    ): Map<String, List<EpgEntry>> = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "guide-${client.creds.username.hashCode()}.xml")
        val age = System.currentTimeMillis() - file.lastModified()
        val fresh = file.exists() && file.length() > 1024 && age < MAX_AGE_MS
        if (force || !fresh) {
            progress("Downloading TV guide…")
            download(client.xmltvUrl(), file)
        }
        progress("Reading TV guide…")
        val now = System.currentTimeMillis()
        XmltvParser.parse(file, channelIds, from = now - PAST_MS, to = now + WEEK_MS, fullUntil = now + FUTURE_MS)
    }

    private fun download(url: String, target: File) {
        val tmp = File(target.parentFile, target.name + ".part")
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        XtreamClient.http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("guide download failed (HTTP ${response.code})")
            val body = response.body ?: throw IOException("guide download was empty")
            tmp.outputStream().use { out -> body.byteStream().copyTo(out, 64 * 1024) }
        }
        if (tmp.length() < 100) throw IOException("provider sent an empty guide")
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    companion object {
        private const val MAX_AGE_MS = 8 * 60 * 60 * 1000L
        const val PAST_MS = 3 * 60 * 60 * 1000L
        const val FUTURE_MS = 36 * 60 * 60 * 1000L
        const val WEEK_MS = 7 * 24 * 60 * 60 * 1000L
    }
}
