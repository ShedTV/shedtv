@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.matchday.tv.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.matchday.tv.AppViewModel
import com.matchday.tv.Screen
import com.matchday.tv.data.Credentials
import com.matchday.tv.transfer.SetupPart
import com.matchday.tv.transfer.SetupTransfer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun qr(text: String, size: Int = 360): ImageBitmap {
    val m = com.google.zxing.qrcode.QRCodeWriter().encode(text, com.google.zxing.BarcodeFormat.QR_CODE, size, size, mapOf(com.google.zxing.EncodeHintType.MARGIN to 1))
    val px = IntArray(size * size) { i -> if (m.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    return android.graphics.Bitmap.createBitmap(px, size, size, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** Reload everything after a setup has been copied in. */
private fun restart(activity: ComponentActivity) {
    activity.viewModelStore.clear()
    activity.recreate()
}

/** The new device: shows a code and QR, and waits for another ShedTV or a phone. */
@Composable
fun ReceiveSetupScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val code = remember { SetupTransfer.newCode() }
    var status by remember { mutableStateOf<String?>(null) }
    var port by remember { mutableStateOf(0) }
    val back = remember { FocusRequester() }
    BackHandler { vm.leaveReceive() }
    LaunchedEffect(Unit) { delay(100); runCatching { back.requestFocus() } }
    DisposableEffect(Unit) {
        val r = SetupTransfer.Receiver(context, code, object : SetupTransfer.Listener {
            override fun onSetup(json: String) {
                status = "Received. Setting up…"
                runCatching { SetupTransfer.import(context, json) }
                    .onSuccess { restart(activity) }
                    .onFailure { status = "Something went wrong reading the setup. Please try again." }
            }
            override fun onPhoneSubscription(name: String, playlist: Boolean, server: String, username: String, password: String, epg: String) {
                vm.saveSubscription(name.trim(), Credentials(server.trim(), username.trim(), password, playlist = playlist, epgUrl = epg.trim()))
            }
            override fun onWrongCode(attempts: Int) {
                status = if (attempts >= 5) "Too many wrong codes, so this has stopped. Go back and start again." else "Someone entered the wrong code ($attempts of 5)."
            }
        })
        runCatching { r.start(); port = r.port }.onFailure { status = "Couldn't start listening on this network." }
        onDispose { r.stop() }
    }
    val ip = remember(port) { SetupTransfer.localAddress(context) }
    val link = if (ip != null && port > 0) "http://$ip:$port/?k=$code" else null

    Column(Modifier.fillMaxSize().background(Palette.Background).padding(horizontal = 56.dp, vertical = 36.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("COPY A SETUP TO THIS DEVICE", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("From another ShedTV", color = Palette.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("On the device you already use, go to Settings → Subscriptions → Send my setup, pick this device, and enter this code:", color = Palette.TextSoft, fontSize = 15.sp)
                Text(code.chunked(3).joinToString(" "), color = Palette.Text, fontSize = 64.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 6.sp)
                Spacer(Modifier.height(6.dp))
                Text("Or from your phone", color = Palette.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Scan the code with your phone's camera and type your subscription details there instead of with the remote.", color = Palette.TextSoft, fontSize = 15.sp)
                if (link == null) Text("Connect this device to Wi-Fi or a network cable to use your phone.", color = Palette.Live, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text("Both devices need to be on the same home Wi-Fi. Everything goes directly between them, never over the internet, and the code scrambles it on the way.",
                    color = Palette.TextMuted, fontSize = 12.sp)
                status?.let { Text(it, color = Palette.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
                FocusTile(onClick = { vm.leaveReceive() }, corner = 10.dp, focusedScale = 1.05f, modifier = Modifier.focusRequester(back)) {
                    Text("Back", fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(androidx.compose.ui.graphics.Color(0x1AFFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 9.dp))
                }
            }
            if (link != null) {
                val img = remember(link) { runCatching { qr(link) }.getOrNull() }
                if (img != null) Image(img, contentDescription = "QR code for phone setup", modifier = Modifier.size(300.dp).background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp)).padding(10.dp))
            }
        }
    }
}

/** The device you already use: finds the new one and sends the chosen parts of the setup. */
@Composable
fun SendSetupScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf<List<SetupTransfer.Found>>(emptyList()) }
    var chosen by remember { mutableStateOf<SetupTransfer.Found?>(null) }
    var parts by remember { mutableStateOf(SetupPart.entries.toSet()) }
    var askCode by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    BackHandler { if (askCode) askCode = false else vm.navigate(Screen.Settings) }
    LaunchedEffect(Unit) { delay(100); runCatching { first.requestFocus() } }
    DisposableEffect(Unit) {
        val f = SetupTransfer.Finder(context) { devices = it }
        f.start()
        onDispose { f.stop() }
    }

    Column(
        Modifier.fillMaxSize().background(Palette.Background).verticalScroll(rememberScrollState()).padding(horizontal = 56.dp, vertical = 36.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("SEND MY SETUP", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
        Text("On the new device, open ShedTV and choose \"Set up from my phone or another ShedTV\" (or Settings → Subscriptions → Copy setup from another device). It will appear below.",
            color = Palette.TextSoft, fontSize = 15.sp)

        Text("1.  CHOOSE THE NEW DEVICE", color = Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
        if (devices.isEmpty()) {
            Text("Looking on your Wi-Fi…  Make sure the other device is showing its code and is on the same network.", color = Palette.TextMuted, fontSize = 14.sp,
                modifier = Modifier.focusRequester(first))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            devices.forEachIndexed { i, d ->
                Chip(d.name, selected = chosen == d, modifier = if (i == 0) Modifier.focusRequester(first) else Modifier) { chosen = d }
            }
        }

        Text("2.  CHOOSE WHAT TO COPY", color = Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SetupPart.entries.forEach { p ->
                val on = p in parts
                FocusTile(onClick = { parts = if (on) parts - p else parts + p }, selected = on, corner = 10.dp, focusedScale = 1.04f, modifier = Modifier.width(300.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Text((if (on) "✓  " else "     ") + p.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(p.detail, fontSize = 11.sp, color = LocalContentColor.current.copy(alpha = 0.7f))
                    }
                }
            }
        }
        Text("Copied parts replace what's on the new device. Recordings and downloads stay where they are.", color = Palette.TextMuted, fontSize = 12.sp)

        Text("3.  SEND", color = Palette.Accent, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
        Chip(if (busy) "Sending…" else "Enter the code and send") {
            when {
                chosen == null -> status = "Choose the new device first."
                parts.isEmpty() -> status = "Choose at least one thing to copy."
                else -> askCode = true
            }
        }
        status?.let { Text(it, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
    }

    if (askCode) {
        PinPad("Enter the code shown on the new device", length = 6, onCancel = { askCode = false }) { code ->
            askCode = false
            val target = chosen ?: return@PinPad
            busy = true
            status = "Sending…"
            scope.launch {
                val json = SetupTransfer.export(context, parts)
                val err = withContext(Dispatchers.IO) { SetupTransfer.send(target, code, json) }
                busy = false
                status = err ?: "Done! The other device is setting itself up now."
            }
        }
    }
}
