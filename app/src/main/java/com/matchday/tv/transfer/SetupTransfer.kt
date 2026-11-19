package com.matchday.tv.transfer

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** What can be copied from one ShedTV to another. */
enum class SetupPart(val label: String, val detail: String) {
    Subscriptions("Subscriptions", "Logins, user agents and stream formats"),
    Lists("Favourites & categories", "Favourites, hidden categories and category order"),
    Watchlist("Watchlist", "Everything on your watchlist"),
    Settings("Settings", "Menu, start screen, display and playback settings"),
    History("Watch history", "Continue watching and resume points"),
    Lock("Parental lock", "PIN and locked categories"),
}

/**
 * Copies ShedTV's setup between devices on the same Wi-Fi, with no server in between.
 * The new device listens and shows a 6-digit code; the old one finds it on the network,
 * and sends its setup encrypted with that code. A phone can also fill in a subscription
 * through a small web form the new device serves (opened from a QR code).
 */
object SetupTransfer {
    const val SERVICE = "_shedtv._tcp."
    private val main = Handler(Looper.getMainLooper())
    private val random = SecureRandom()

    // ---------- What gets copied ----------

    private const val PREFS = "matchday"
    private val settingsKeys = setOf(
        "accent", "animations", "clock", "downloadWifiOnly", "guideRows", "highContrast", "liveRewind", "menuHidden", "menuOrder",
        "mergeQualities", "okAction", "preview", "recording", "startScreen", "stay50Hz", "subtitleLanguage", "subtitles", "textScale", "useBackups",
    )

    private fun partOf(key: String): SetupPart? = when {
        key == "subscriptions" || key == "activeSubscription" -> SetupPart.Subscriptions
        key.startsWith("favourites") || key.startsWith("hidden_") || key.startsWith("catOrder_") -> SetupPart.Lists
        key == "pin" || key == "lockAdult" || key.startsWith("locked_") -> SetupPart.Lock
        key in settingsKeys -> SetupPart.Settings
        else -> null // device-specific (storage folders, last screen) stays put
    }

    /** The chosen parts of this device's setup, as JSON. */
    fun export(context: Context, parts: Set<SetupPart>): String = buildJsonObject {
        put("v", 1)
        put("prefs", dump(context, PREFS) { partOf(it) in parts })
        if (SetupPart.Watchlist in parts) put("watchlist", dump(context, "shedtv_watchlist") { true })
        if (SetupPart.History in parts) put("history", dump(context, "matchday_history") { true })
    }.toString()

    private fun dump(context: Context, file: String, keep: (String) -> Boolean) = buildJsonObject {
        context.getSharedPreferences(file, Context.MODE_PRIVATE).all.forEach { (k, v) ->
            if (!keep(k) || v == null) return@forEach
            put(k, when (v) {
                is String -> buildJsonObject { put("t", "s"); put("v", v) }
                is Boolean -> buildJsonObject { put("t", "b"); put("v", v) }
                is Int -> buildJsonObject { put("t", "i"); put("v", v) }
                is Long -> buildJsonObject { put("t", "l"); put("v", v) }
                is Float -> buildJsonObject { put("t", "f"); put("v", v) }
                is Set<*> -> buildJsonObject { put("t", "set"); put("v", buildJsonArray { v.forEach { add(JsonPrimitive(it.toString())) } }) }
                else -> return@forEach
            })
        }
    }

    /** Write a received setup into this device's settings. */
    fun import(context: Context, json: String) {
        val root = Json.parseToJsonElement(json) as JsonObject
        (root["prefs"] as? JsonObject)?.let { load(context, PREFS, it) }
        (root["watchlist"] as? JsonObject)?.let { load(context, "shedtv_watchlist", it) }
        (root["history"] as? JsonObject)?.let { load(context, "matchday_history", it) }
    }

    private fun load(context: Context, file: String, values: JsonObject) {
        val e = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit()
        values.forEach { (k, el) ->
            val o = el as? JsonObject ?: return@forEach
            val v = o["v"] ?: return@forEach
            when ((o["t"] as? JsonPrimitive)?.contentOrNull) {
                "s" -> e.putString(k, (v as JsonPrimitive).contentOrNull)
                "b" -> e.putBoolean(k, (v as JsonPrimitive).booleanOrNull ?: false)
                "i" -> e.putInt(k, (v as JsonPrimitive).content.toInt())
                "l" -> e.putLong(k, (v as JsonPrimitive).content.toLong())
                "f" -> e.putFloat(k, (v as JsonPrimitive).content.toFloat())
                "set" -> e.putStringSet(k, (v as JsonArray).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet())
            }
        }
        e.commit()
    }

    // ---------- Encryption (key made from the 6-digit code) ----------

    private fun key(code: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(("shedtv:" + code).toCharArray(), salt, 60_000, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun seal(code: String, plain: String): String {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(code, salt), GCMParameterSpec(128, iv)) }
        val out = salt + iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /** Null when the code is wrong (or the data was damaged). */
    fun open(code: String, sealed: String): String? = runCatching {
        val b = Base64.decode(sealed, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(code, b.copyOfRange(0, 16)), GCMParameterSpec(128, b.copyOfRange(16, 28))) }
        String(c.doFinal(b.copyOfRange(28, b.size)))
    }.getOrNull()

    fun newCode(): String = (100000 + random.nextInt(900000)).toString()

    /** This device's address on the local network, for the phone QR code. */
    fun localAddress(context: Context): String? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val lp = cm.getLinkProperties(cm.activeNetwork ?: return null) ?: return null
        return lp.linkAddresses.map { it.address }.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }?.hostAddress
    }

    // ---------- The new device: listen ----------

    /** Results come back on the main thread. */
    interface Listener {
        fun onSetup(json: String)
        fun onPhoneSubscription(name: String, playlist: Boolean, server: String, username: String, password: String, epg: String)
        fun onWrongCode(attempts: Int)
    }

    class Receiver(private val context: Context, val code: String, private val listener: Listener) {
        private var server: ServerSocket? = null
        private var nsd: NsdManager.RegistrationListener? = null
        private var wrong = 0
        @Volatile private var closed = false
        var port = 0
            private set

        fun start() {
            val s = ServerSocket()
            s.reuseAddress = true
            s.bind(InetSocketAddress(0))
            server = s
            port = s.localPort
            Thread({
                while (!closed) {
                    val sock = runCatching { s.accept() }.getOrNull() ?: break
                    Thread({ runCatching { handle(sock) }; runCatching { sock.close() } }, "shedtv-transfer-conn").start()
                }
            }, "shedtv-transfer").start()
            // Let other ShedTV devices find this one.
            val info = NsdServiceInfo().apply {
                serviceName = "ShedTV on " + (Build.MODEL ?: "Android")
                serviceType = SERVICE
                port = this@Receiver.port
            }
            val l = object : NsdManager.RegistrationListener {
                override fun onServiceRegistered(i: NsdServiceInfo) {}
                override fun onRegistrationFailed(i: NsdServiceInfo, e: Int) {}
                override fun onServiceUnregistered(i: NsdServiceInfo) {}
                override fun onUnregistrationFailed(i: NsdServiceInfo, e: Int) {}
            }
            nsd = l
            runCatching { (context.getSystemService(Context.NSD_SERVICE) as NsdManager).registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
        }

        fun stop() {
            closed = true
            runCatching { server?.close() }
            nsd?.let { l -> runCatching { (context.getSystemService(Context.NSD_SERVICE) as NsdManager).unregisterService(l) } }
            nsd = null
        }

        private fun handle(sock: Socket) {
            sock.soTimeout = 15_000
            val input = BufferedInputStream(sock.getInputStream())
            val requestLine = readLine(input) ?: return
            val headers = HashMap<String, String>()
            while (true) {
                val h = readLine(input) ?: break
                if (h.isEmpty()) break
                val i = h.indexOf(':')
                if (i > 0) headers[h.substring(0, i).trim().lowercase()] = h.substring(i + 1).trim()
            }
            val len = headers["content-length"]?.toIntOrNull()?.coerceAtMost(2_000_000) ?: 0
            val body = ByteArray(len).also { var r = 0; while (r < len) { val n = input.read(it, r, len - r); if (n < 0) break; r += n } }
            val (method, target) = requestLine.split(' ').let { (it.getOrNull(0) ?: "") to (it.getOrNull(1) ?: "/") }
            val path = target.substringBefore('?')
            val query = parseForm(target.substringAfter('?', ""))
            val out = sock.getOutputStream()
            fun reply(status: String, type: String, text: String) {
                val bytes = text.toByteArray()
                out.write("HTTP/1.1 $status\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                out.write(bytes); out.flush()
            }
            when {
                closed -> reply("410 Gone", "text/plain", "closed")
                method == "POST" && path == "/transfer" -> {
                    val plain = open(code, String(body))
                    if (plain == null) { badCode(); reply("403 Forbidden", "text/plain", "wrong code") }
                    else { reply("200 OK", "text/plain", "ok"); main.post { listener.onSetup(plain) } }
                }
                method == "GET" && path == "/" -> {
                    if (query["k"] != code) reply("403 Forbidden", "text/html", page("This link has expired. Open the code shown on your TV again.", null))
                    else reply("200 OK", "text/html", page(null, code))
                }
                method == "POST" && path == "/phone" -> {
                    val f = parseForm(String(body))
                    if (f["k"] != code) { badCode(); reply("403 Forbidden", "text/html", page("That code isn't right. Scan the QR code on your TV again.", null)) }
                    else if (f["server"].isNullOrBlank()) reply("200 OK", "text/html", page("Please fill in the server or playlist address.", code))
                    else {
                        reply("200 OK", "text/html", page("Sent! Look at your TV: ShedTV is connecting now. You can close this page.", null))
                        main.post {
                            listener.onPhoneSubscription(
                                f["name"].orEmpty(), f["kind"] == "m3u", f["server"].orEmpty(), f["username"].orEmpty(), f["password"].orEmpty(), f["epg"].orEmpty(),
                            )
                        }
                    }
                }
                else -> reply("404 Not Found", "text/plain", "not found")
            }
        }

        private fun badCode() {
            wrong++
            val n = wrong
            main.post { listener.onWrongCode(n) }
            if (n >= 5) stop() // stop guessing
        }
    }

    private fun readLine(input: BufferedInputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = input.read()
            if (c < 0) return if (b.size() == 0) null else b.toString()
            if (c == '\n'.code) return b.toString().trimEnd('\r')
            if (b.size() > 8192) return null
            b.write(c)
        }
    }

    private fun parseForm(s: String): Map<String, String> = s.split('&').filter { it.contains('=') }.associate {
        URLDecoder.decode(it.substringBefore('='), "UTF-8") to URLDecoder.decode(it.substringAfter('='), "UTF-8")
    }

    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace("\"", "&quot;")

    /** The phone form. */
    private fun page(message: String?, code: String?): String = """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Set up ShedTV</title><style>
body{background:#07090C;color:#F2F5F8;font:16px system-ui,sans-serif;margin:0;padding:22px}h1{font-size:24px;margin:0 0 6px}p{color:#C9D3DE}
label{display:block;margin:14px 0 4px;color:#C9D3DE;font-size:14px}input,select{width:100%;box-sizing:border-box;padding:12px;border-radius:10px;border:1px solid #2A3540;background:#10151B;color:#F2F5F8;font-size:16px}
button{margin-top:20px;width:100%;padding:14px;border:0;border-radius:12px;background:#44C470;color:#04130A;font-weight:700;font-size:16px}.m{background:#10151B;border:1px solid #44C470;border-radius:12px;padding:12px;color:#F2F5F8}
.x{display:none}</style></head><body><h1>Set up ShedTV</h1>""" +
        (message?.let { "<p class=\"m\">${esc(it)}</p>" } ?: "") +
        (if (code == null) "" else """<p>Type your subscription details here and they'll appear on your TV. This page only works on your home Wi-Fi.</p>
<form method="post" action="/phone"><input type="hidden" name="k" value="${esc(code)}">
<label>Type</label><select name="kind" onchange="document.querySelectorAll('.xt').forEach(e=>e.style.display=this.value=='m3u'?'none':'block');document.querySelectorAll('.pl').forEach(e=>e.style.display=this.value=='m3u'?'block':'none')">
<option value="xtream">Xtream login (server, username, password)</option><option value="m3u">M3U playlist address</option></select>
<label>Name (optional)</label><input name="name" placeholder="e.g. Main subscription">
<label>Server or playlist address</label><input name="server" inputmode="url" autocapitalize="off" autocorrect="off" placeholder="http://…">
<div class="xt"><label>Username</label><input name="username" autocapitalize="off" autocorrect="off">
<label>Password</label><input name="password" type="password"></div>
<div class="pl x"><label>TV guide address (optional)</label><input name="epg" inputmode="url" autocapitalize="off" autocorrect="off"></div>
<button type="submit">Send to my TV</button></form>""") + "</body></html>"

    // ---------- The old device: find and send ----------

    data class Found(val name: String, val host: String, val port: Int)

    class Finder(private val context: Context, private val onChange: (List<Found>) -> Unit) {
        private val found = LinkedHashMap<String, Found>()
        private var listener: NsdManager.DiscoveryListener? = null
        private var lock: WifiManager.MulticastLock? = null
        private val nsd get() = context.getSystemService(Context.NSD_SERVICE) as NsdManager

        fun start() {
            lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .createMulticastLock("shedtv-find").apply { setReferenceCounted(false); runCatching { acquire() } }
            val l = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(t: String) {}
                override fun onDiscoveryStopped(t: String) {}
                override fun onStartDiscoveryFailed(t: String, e: Int) {}
                override fun onStopDiscoveryFailed(t: String, e: Int) {}
                override fun onServiceLost(i: NsdServiceInfo) {
                    main.post { found.remove(i.serviceName); onChange(found.values.toList()) }
                }
                @Suppress("DEPRECATION")
                override fun onServiceFound(i: NsdServiceInfo) {
                    runCatching {
                        nsd.resolveService(i, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(s: NsdServiceInfo, e: Int) {}
                            override fun onServiceResolved(s: NsdServiceInfo) {
                                val host = s.host?.hostAddress ?: return
                                main.post { found[s.serviceName] = Found(s.serviceName, host, s.port); onChange(found.values.toList()) }
                            }
                        })
                    }
                }
            }
            listener = l
            runCatching { nsd.discoverServices(SERVICE, NsdManager.PROTOCOL_DNS_SD, l) }
        }

        fun stop() {
            listener?.let { l -> runCatching { nsd.stopServiceDiscovery(l) } }
            listener = null
            runCatching { lock?.release() }
        }
    }

    /** Send the setup; returns null on success, else a message. Call off the main thread. */
    fun send(target: Found, code: String, json: String): String? = runCatching {
        val body = seal(code, json)
        val conn = java.net.URL("http://${target.host}:${target.port}/transfer").openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "POST"; conn.doOutput = true; conn.connectTimeout = 8_000; conn.readTimeout = 20_000
        conn.setRequestProperty("Content-Type", "text/plain")
        conn.outputStream.use { it.write(body.toByteArray()) }
        when (conn.responseCode) {
            200 -> null
            403 -> "That code isn't right. Check the code on the other screen."
            else -> "The other device said no (${conn.responseCode}). Is it still showing the code?"
        }
    }.getOrElse { "Couldn't reach the other device. Are both on the same Wi-Fi?" }
}
