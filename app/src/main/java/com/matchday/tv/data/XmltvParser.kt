package com.matchday.tv.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/** Streams an XMLTV file, keeping only the channels and time window we need. */
object XmltvParser {

    private val withOffset = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
    private val utc = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private val fixtureTitle = Regex("\\s(?:v|vs|vs\\.|versus)\\s", RegexOption.IGNORE_CASE)

    /**
     * Keeps everything between [from] and [fullUntil]; after that, up to [to], only
     * programmes that look like fixtures ("X v Y"), so the Sport Hub can see the week ahead.
     */
    fun parse(file: File, ids: Set<String>, from: Long, to: Long, fullUntil: Long = to): Map<String, List<EpgEntry>> {
        val result = HashMap<String, MutableList<EpgEntry>>()
        open(file).use { input ->
            val p = Xml.newPullParser()
            p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            p.setInput(input, null)

            var channel: String? = null
            var start = 0L
            var stop = 0L
            var title: String? = null
            var desc: String? = null

            try {
                var event = p.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (p.name) {
                            "programme" -> {
                                val id = p.getAttributeValue(null, "channel")?.trim()?.lowercase()
                                channel = null
                                if (id != null && id in ids) {
                                    start = parseTime(p.getAttributeValue(null, "start"))
                                    stop = parseTime(p.getAttributeValue(null, "stop"))
                                    if (start > 0 && stop > start && stop >= from && start <= to) {
                                        channel = id
                                        title = null
                                        desc = null
                                    }
                                }
                            }
                            "title" -> if (channel != null && title == null) title = p.nextText()
                            "desc" -> if (channel != null && desc == null) desc = p.nextText()
                        }
                    } else if (event == XmlPullParser.END_TAG && p.name == "programme") {
                        val id = channel
                        if (id != null && (start <= fullUntil || fixtureTitle.containsMatchIn(title.orEmpty()))) {
                            result.getOrPut(id) { ArrayList() }.add(
                                EpgEntry(
                                    title = title?.trim().orEmpty().ifEmpty { "No information" },
                                    description = desc?.trim()?.take(500).orEmpty(),
                                    start = start,
                                    end = stop,
                                )
                            )
                        }
                        channel = null
                    }
                    event = p.next()
                }
            } catch (e: Exception) {
                // Many provider guides contain a broken entry somewhere; keep what parsed.
                if (result.isEmpty()) throw e
            }
        }
        return result.mapValues { (_, list) -> tidy(list) }
    }

    /** Sort and drop overlapping duplicates. */
    private fun tidy(list: MutableList<EpgEntry>): List<EpgEntry> {
        list.sortBy { it.start }
        val out = ArrayList<EpgEntry>(list.size)
        for (e in list) {
            val last = out.lastOrNull()
            if (last == null || e.start >= last.end - 60_000) out.add(e)
        }
        return out
    }

    private fun parseTime(raw: String?): Long {
        val s = raw?.trim() ?: return 0
        if (s.length < 14) return 0
        return try {
            if (s.length > 15) withOffset.parse(s)?.time ?: 0 else utc.parse(s.substring(0, 14))?.time ?: 0
        } catch (e: Exception) {
            try { utc.parse(s.substring(0, 14))?.time ?: 0 } catch (e2: Exception) { 0 }
        }
    }

    private fun open(file: File): InputStream {
        val buffered = BufferedInputStream(file.inputStream(), 64 * 1024)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered), 64 * 1024) else buffered
    }
}
