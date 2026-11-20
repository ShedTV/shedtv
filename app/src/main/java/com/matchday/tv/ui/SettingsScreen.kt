@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.matchday.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.foundation.layout.size
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import com.matchday.tv.AppViewModel
import com.matchday.tv.Screen
import com.matchday.tv.data.UserAgents
import kotlinx.coroutines.delay

private enum class SettingsTab(val label: String, val icon: String, val blurb: String) {
    Subs("Subscriptions", "⌂", "Your TV services and how ShedTV talks to them"),
    Tv("TV & guide", "▦", "Channel lists, the guide and the preview window"),
    Playback("Playback", "▶", "Rewind, subtitles and picture"),
    Saving("Recording & downloads", "●", "Recordings are live TV you've recorded; downloads are films and episodes saved to watch offline"),
    Menu("Menu & start screen", "☰", "Which sections you see, their order, and where ShedTV opens"),
    Display("Display", "◐", "Text size, contrast, colour, clock and animations"),
    Lock("Parental lock", "🔒", "A PIN for the categories you choose"),
    Kids("Kids mode", "☺", "Only children's channels, films and series, with bedtime and time limits"),
    About("About & updates", "ⓘ", "Version, updates and licences"),
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val tab = SettingsTab.entries.getOrElse(vm.settingsTab) { SettingsTab.Subs }
    val tabFocus = remember { FocusRequester() }
    var inContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); runCatching { tabFocus.requestFocus() } }
    BackHandler {
        if (inContent) runCatching { tabFocus.requestFocus() } else vm.goHome()
    }

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        NavRail(vm, Screen.Settings)
        // Tabs
        Column(Modifier.width(250.dp).fillMaxHeight().padding(start = 24.dp, top = 22.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("SETTINGS", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
            SettingsTab.entries.forEach { t ->
                val sel = t == tab
                FocusTile(
                    onClick = { vm.settingsTab = t.ordinal },
                    selected = sel, corner = 10.dp, focusedScale = 1.03f,
                    modifier = Modifier.fillMaxWidth()
                        .then(if (sel) Modifier.focusRequester(tabFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) vm.settingsTab = t.ordinal },
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(t.icon, fontSize = 16.sp, modifier = Modifier.width(28.dp))
                        Text(t.label, fontSize = 15.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Text("Up and down to pick a section, then right to change its settings. Back returns here.", color = Palette.TextMuted, fontSize = 11.sp,
                modifier = Modifier.padding(start = 6.dp, bottom = 24.dp))
        }
        // Content
        Column(
            Modifier.fillMaxSize().onFocusChanged { inContent = it.hasFocus }
                .verticalScroll(rememberScrollState()).padding(start = 22.dp, end = 40.dp, top = 26.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column {
                Text(tab.label.uppercase(), color = Palette.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
                Text(tab.blurb, color = Palette.TextSoft, fontSize = 14.sp)
            }
            vm.error?.let { Text(it, color = Palette.Live, fontSize = 14.sp) }
            when (tab) {
                SettingsTab.Subs -> SubscriptionSettings(vm)
                SettingsTab.Tv -> TvSettings(vm)
                SettingsTab.Playback -> PlaybackSettings(vm)
                SettingsTab.Saving -> SavingSettings(vm)
                SettingsTab.Menu -> MenuSettings(vm)
                SettingsTab.Display -> DisplaySettings(vm)
                SettingsTab.Lock -> LockSettings(vm)
                SettingsTab.Kids -> KidsSettings(vm)
                SettingsTab.About -> AboutSettings(vm)
            }
        }
    }
}

/** One setting: a title, a plain-English explanation, what it costs (speed, storage, connections), then its controls. */
@Composable
private fun Setting(title: String, body: String, impact: String? = null, controls: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Palette.Card.copy(alpha = 0.55f), RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(body, color = Palette.TextSoft, fontSize = 13.sp)
        if (impact != null) Text("ⓘ  $impact", color = Palette.TextMuted, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        controls()
    }
}

@Composable
private fun OnOff(on: Boolean, onText: String = "On", offText: String = "Off", set: (Boolean) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Chip(onText, selected = on) { set(true) }
        Chip(offText, selected = !on) { set(false) }
    }
}

@Composable
private fun SubscriptionSettings(vm: AppViewModel) {
    val active = vm.activeSubscription
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    var editingUa by remember { mutableStateOf(false) }
    var customUa by rememberSaveable(active?.id) { mutableStateOf(active?.userAgent.orEmpty()) }

    Setting(
        "Your subscriptions",
        "The TV services ShedTV signs in to. OK switches to one; hold OK on one to edit it. Favourites, history, hidden categories and category order are kept separately for each.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.subscriptions.forEach { sub ->
                val isActive = sub.id == active?.id
                FocusTile(
                    onClick = { vm.switchSubscription(sub) },
                    onLongClick = { vm.startEditSubscription(sub) },
                    selected = isActive, corner = 12.dp, focusedScale = 1.02f,
                    modifier = Modifier.width(620.dp),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            val kind = if (sub.playlist) "M3U playlist" else "${sub.host}  ·  ${sub.username}"
                            Text(kind + (if (!isActive && vm.useBackups) vm.backups[sub.id]?.status?.let { "  ·  $it" }.orEmpty() else ""),
                                fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isActive) Text("IN USE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("+  Add subscription") { vm.startAddSubscription() }
                Chip("📱  Add from my phone / copy from another device") { vm.openReceive() }
                Chip("⇄  Send my setup to another device") { vm.navigate(Screen.SendSetup) }
                if (active != null) {
                    Chip("Edit ${active.name}") { vm.startEditSubscription(active) }
                    Chip(if (confirmRemove == active.id) "Press again to remove" else "Remove ${active.name}", danger = confirmRemove == active.id) {
                        if (confirmRemove == active.id) { confirmRemove = null; vm.removeSubscription(active) } else confirmRemove = active.id
                    }
                }
            }
        }
    }

    if (vm.subscriptions.size > 1) {
        Setting(
            "Use other subscriptions as backup",
            "If a channel won't play, ShedTV tries the same channel on your other subscriptions, and search shows their channels and films too.",
            "Their channel lists load in the background when ShedTV opens, which takes a little longer on slow boxes. No extra connections are used until a backup actually plays.",
        ) { OnOff(vm.useBackups) { vm.turnBackups(it) } }
    }

    if (active != null) {
        Setting(
            "User agent for ${active.name}",
            "The name ShedTV gives your provider when it connects, the way a web browser says which browser it is. Leave it on Android device unless your provider blocks apps or asks for a particular player. Currently: ${UserAgents.labelFor(active.userAgent)}.",
            "Takes effect from the next channel you open. No effect on speed.",
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                UserAgents.presets.forEach { p ->
                    Chip(p.label, selected = active.userAgent == p.value) { vm.updateActive(userAgent = p.value); customUa = p.value; editingUa = false }
                }
                Chip("Custom…", selected = UserAgents.labelFor(active.userAgent) == "Custom") { editingUa = true }
            }
            if (editingUa) {
                // The text box only appears when asked for, so scrolling past never pops up the keyboard.
                val field = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(80); runCatching { field.requestFocus() } }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customUa, onValueChange = { customUa = it }, singleLine = true,
                        label = { androidx.compose.material3.Text("Custom user agent") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onDone = { vm.updateActive(userAgent = customUa); editingUa = false }),
                        modifier = Modifier.width(480.dp).focusRequester(field),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
                            focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.TextMuted,
                            focusedLabelColor = Palette.Accent, unfocusedLabelColor = Palette.TextMuted, cursorColor = Palette.Accent,
                            focusedContainerColor = Palette.Card, unfocusedContainerColor = Palette.Card,
                        ),
                    )
                    Chip("Use this") { vm.updateActive(userAgent = customUa); editingUa = false }
                    Chip("Cancel") { editingUa = false; customUa = active.userAgent }
                }
            }
        }

        if (!active.playlist) {
            Setting(
                "Stream format",
                "How live channels are sent to ShedTV. MPEG-TS starts quickest and works with live rewind and recording, so it's the best choice for most people. Try HLS only if some channels won't play.",
                "With HLS, live rewind is unavailable. No difference in picture quality.",
            ) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("MPEG-TS (recommended)", selected = vm.streamFormat == "ts") { vm.updateActive(streamFormat = "ts") }
                    Chip("HLS (m3u8)", selected = vm.streamFormat == "m3u8") { vm.updateActive(streamFormat = "m3u8") }
                }
            }
        }
    }
}

@Composable
private fun TvSettings(vm: AppViewModel) {
    Setting(
        "Categories",
        "Hide categories you never use and put the rest in the order you like. Hidden categories also stay out of the guide and search. ${vm.hiddenCategories.size} hidden at the moment. Shortcut: hold OK on any category in Live TV or the guide.",
        "Hiding categories you don't watch makes the guide and channel lists quicker on slower boxes.",
    ) { Chip("Manage categories") { vm.navigate(Screen.Categories) } }

    Setting(
        "One entry per channel",
        "Many providers list each channel several times (UHD, FHD, HD and SD). With this on you see each channel once, ShedTV plays the best version, and it switches to another version by itself if one fails. Press ▶▶ while watching to try the next version.",
        "No effect on speed; channel lists are shorter and easier to scroll.",
    ) { OnOff(vm.mergeQualities) { vm.setMergeQualitiesEnabled(it) } }

    Setting(
        "What OK does in Live TV",
        "Full screen: OK on a channel goes straight to full screen. Preview first: the first OK plays it in the preview window so you can check what's on, and a second OK goes full screen.",
        "Preview first needs the preview window (below) turned on.",
    ) { OnOff(vm.okAction == "play", "Full screen", "Preview first") { vm.chooseOkAction(if (it) "play" else "preview") } }

    Setting(
        "Preview window",
        "Plays the highlighted channel, muted, in a small window in Live TV and the guide. Press OK and it carries on full screen without restarting.",
        "Uses your connection while you browse, and on a Fire Stick moving between channels can feel a little slower. On one-connection subscriptions, downloads pause while it plays.",
    ) {
        OnOff(vm.previewEnabled) { vm.turnPreview(it) }
        if (vm.previewEnabled) {
            Text("While you browse, the preview shows:", color = Palette.TextSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            OnOff(vm.previewMode == "follow", "The highlighted channel", "What I was watching") { vm.choosePreviewMode(if (it) "follow" else "stay") }
            Text("\"What I was watching\" is handy in ad breaks: look around, then tap the preview (or use Back to … at the top of the channel list) to carry on.",
                color = Palette.TextMuted, fontSize = 12.sp)
        }
    }

    if (vm.activeSubscription != null) {
        Setting(
            "TV guide",
            (vm.guideStatus ?: "The guide is up to date.") + " It refreshes itself every 8 hours; refresh it now if listings look out of date.",
            "A refresh downloads the whole guide, which can take up to a minute on big providers. You can keep watching while it does.",
        ) { Chip("Refresh guide now") { vm.loadGuide(force = true) } }
    }
}

@Composable
private fun PlaybackSettings(vm: AppViewModel) {
    Setting(
        "Live rewind",
        "Keeps up to the last 30 minutes of the channel you're watching, so you can pause live TV and rewind. It starts from the moment you tune in.",
        "Uses up to 1.5 GB of storage while you watch, freed when you stop. On a nearly full Fire Stick, leave it off. MPEG-TS stream format only.",
    ) { OnOff(vm.liveRewind) { vm.setLiveRewindEnabled(it) } }

    Setting(
        "Subtitles",
        "Shows subtitles when a channel, film or episode includes them. You can also change this while watching: hold OK (or press ☰) for subtitles and audio.",
        "No effect on speed. Not every channel broadcasts subtitles.",
    ) { OnOff(vm.subtitlesEnabled, "On when available", "Off") { vm.setSubtitlePref(it, null) } }

    Setting(
        "Stay at 50Hz",
        "UK TV runs at 50 frames a second. Many TVs blank for a second whenever the frame rate changes, for example going full screen. With this on, ShedTV switches the TV to 50Hz once when it opens, so it doesn't blank again. Channels at 60 frames a second still switch.",
        "No effect on speed. Needs \"Match original frame rate\" (or similar) turned on in your device's display settings.",
    ) { OnOff(vm.stay50Hz) { vm.setStay50HzEnabled(it) } }
}

@Composable
private fun SavingSettings(vm: AppViewModel) {
    Setting(
        "Recording",
        "Record live TV from the player or the guide (hold OK, or press ☰). If the stream glitches, ShedTV keeps going: it reconnects, tries another version of the channel, then your other subscriptions, and notes any gaps.",
        "Uses about 3 GB per hour of HD. Recording uses a connection, so on one-connection subscriptions you can only watch what's being recorded. Works best on a Shield or box with a USB drive.",
    ) {
        OnOff(vm.recordingEnabled) { vm.turnRecording(it) }
        if (vm.recordingEnabled) {
            val chosen = vm.recordingVolume()
            Text("Save recordings to:", color = Palette.TextSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                com.matchday.tv.record.RecorderManager.volumes().forEach { v ->
                    Chip("${v.label} · %.1f GB free".format(v.freeBytes / 1073741824.0), selected = chosen?.dir == v.dir) { vm.chooseRecordingVolume(v) }
                }
            }
            val free = chosen?.freeBytes ?: 0
            if (free < 16L * 1024 * 1024 * 1024) {
                Text("⚠ Only %.1f GB free here: about %d hour(s) of HD.".format(free / 1073741824.0, free / com.matchday.tv.record.RecorderManager.BYTES_PER_HOUR),
                    color = Palette.Live, fontSize = 12.sp)
            }
            Text("Recordings are kept in ShedTV's own folder and are removed if ShedTV is uninstalled.", color = Palette.TextMuted, fontSize = 12.sp)
        }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) vm.pickedDownloadFolder(uri) }
    val canPick = remember { android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).resolveActivity(ctx.packageManager) != null }
    val current = vm.downloadFolder()
    Setting(
        "Downloads",
        "Save films and episodes to watch without the internet, such as on a plane. Use Download on a film or season, or hold OK on an episode. With no internet, ShedTV opens straight to your downloads.",
        "One download at a time, using one connection; on one-connection subscriptions downloads pause while you watch. A film is usually 1 to 4 GB.",
    ) {
        Text("Save downloads to:", color = Palette.TextSoft, fontSize = 13.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            com.matchday.tv.download.DownloadManager.volumes().forEach { v ->
                Chip("${v.label} · %.1f GB free".format(v.freeBytes / 1073741824.0), selected = current == v.dir.path) { vm.setDownloadFolder(v.dir.path) }
            }
            if (current?.startsWith("content://") == true) Chip("Folder: " + com.matchday.tv.download.DownloadManager.folderName(current), selected = true) { }
            if (canPick) Chip("Choose folder…") { runCatching { picker.launch(null) }.onFailure { vm.flash = "This device doesn't have a folder picker." } }
        }
        Text("ShedTV's own folders are removed if ShedTV is uninstalled. A folder you choose keeps your files, and other apps such as VLC can play them.",
            color = Palette.TextMuted, fontSize = 12.sp)
        Text("Download over:", color = Palette.TextSoft, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        OnOff(vm.downloadWifiOnly, "Wi-Fi only", "Any connection") { vm.turnDownloadWifiOnly(it) }
        Text("Wi-Fi only stops phones using mobile data. TV boxes are always treated as Wi-Fi.", color = Palette.TextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun MenuSettings(vm: AppViewModel) {
    Setting(
        "Sections in the menu",
        "Choose which sections appear in the menu on the left, and their order. OK shows or hides a section. To move one: hold OK to pick it up, move it with up and down, then press OK to put it down. Hidden sections keep everything in them: show them again and it's all still there. Settings always stays at the bottom.",
        "No effect on speed. Recordings appears once recording is turned on (Recording & downloads).",
    ) {
        var moving by remember { mutableStateOf<String?>(null) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.orderedMenu().forEach { id -> androidx.compose.runtime.key(id) {
                val shown = id !in vm.menuHidden
                val isMoving = moving == id
                FocusTile(
                    onClick = { if (isMoving) moving = null else vm.setMenuShown(id, !shown) },
                    onLongClick = { moving = if (isMoving) null else id },
                    selected = shown || isMoving, corner = 10.dp, focusedScale = 1.02f,
                    modifier = Modifier.width(440.dp).onFocusChanged { if (!it.isFocused && moving == id) moving = null }.onPreviewKeyEvent { e ->
                        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (e.key.nativeKeyCode) {
                            android.view.KeyEvent.KEYCODE_DPAD_UP -> if (isMoving) { vm.moveMenu(id, -1); true } else false
                            android.view.KeyEvent.KEYCODE_DPAD_DOWN -> if (isMoving) { vm.moveMenu(id, 1); true } else false
                            android.view.KeyEvent.KEYCODE_BACK -> if (isMoving) { moving = null; true } else false
                            android.view.KeyEvent.KEYCODE_MEDIA_REWIND, android.view.KeyEvent.KEYCODE_CHANNEL_UP -> { vm.moveMenu(id, -1); true }
                            android.view.KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> { vm.moveMenu(id, 1); true }
                            else -> false
                        }
                    },
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text((if (isMoving) "↕  " else "") + menuLabel(id), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(if (isMoving) "Up/down to move · OK to drop" else if (shown) "Shown" else "Hidden", fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.7f))
                    }
                }
            } }
            Chip("Reset to the usual menu") { vm.resetMenu() }
        }
    }
    Setting(
        "Open ShedTV on",
        "The screen you see when ShedTV starts. Back from anywhere also brings you here, and Back on this screen closes ShedTV.",
        "No effect on speed.",
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("Home", selected = vm.startScreen == "home") { vm.chooseStart("home") }
            Chip("Live TV", selected = vm.startScreen == "live") { vm.chooseStart("live") }
            Chip("Guide", selected = vm.startScreen == "guide") { vm.chooseStart("guide") }
            Chip("Wherever I was last", selected = vm.startScreen == "last") { vm.chooseStart("last") }
        }
    }
    Setting(
        "Watchlist",
        "Things ShedTV looks for in your guide (teams, shows, events) to show on Home, with a reminder just before they start.",
    ) { Chip("Edit watchlist (${vm.watchlist.items.size})") { vm.navigate(Screen.Watchlist) } }
}

@Composable
private fun KidsSettings(vm: AppViewModel) {
    Setting(
        "Kids mode",
        if (vm.kidsMode) "Kids mode is on. Only the categories you chose are visible, and Settings needs your PIN. ${vm.kidsLive.size} live, ${vm.kidsMovies.size} film and ${vm.kidsSeries.size} series categories are allowed."
        else "One step to make ShedTV child-friendly: only children's channels, films and series are shown, and everything else (including anything your provider adds later) stays hidden. Settings, subscriptions and turning it off need your PIN.",
        "No effect on speed. It filters by category name, so check the suggested list once; it's a filter, not a guarantee.",
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!vm.kidsMode) {
                Chip("Set up Kids mode", selected = true) { if (vm.pinSet) vm.navigate(Screen.KidsSetup) else vm.setNewPinThen { vm.navigate(Screen.KidsSetup) } }
            } else {
                Chip("Change what's allowed") { vm.navigate(Screen.KidsSetup) }
                Chip("Turn Kids mode off", danger = true) { vm.stopKids() }
            }
        }
    }
    if (vm.kidsMode) Setting("Bedtime, limits and Home", "Changes take effect straight away.") { KidsExtras(vm) }
    MakeItStick()
}

@Composable
private fun LockSettings(vm: AppViewModel) {
    Setting(
        "PIN",
        if (vm.pinSet) "A PIN is set. Locked categories are hidden from Live TV, the guide, search and Home until someone enters it; once entered they stay open until ShedTV is closed or you lock them again."
        else "Set a 4-digit PIN, then choose which categories need it. Locked categories are hidden from every list until the PIN is entered.",
        "No effect on speed. If you forget the PIN, clearing ShedTV's data (in your device's app settings) removes it, along with your other settings.",
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!vm.pinSet) Chip("Set a PIN") { vm.setNewPin() }
            else {
                Chip("Change PIN") { vm.askPin("Enter your current PIN") { vm.setNewPin() } }
                if (vm.unlocked) Chip("Lock again now") { vm.lockAgain() }
                Chip("Turn the lock off") { vm.removePin() }
            }
        }
    }
    if (vm.pinSet) {
        Setting(
            "Lock adult categories automatically",
            "Locks any category whose name suggests adult content (for example Adult, XXX or 18+), including new ones your provider adds later.",
        ) { OnOff(vm.lockAdult) { vm.turnLockAdult(it) } }

        Setting(
            "Locked categories",
            "OK locks or unlocks a category. You'll be asked for the PIN first if the lock is closed.",
        ) {
            val cats = vm.allCategories
            androidx.compose.foundation.lazy.LazyColumn(Modifier.height(320.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(cats.size) { i ->
                    val c = cats[i]
                    val locked = vm.categoryLockedSetting(c.id)
                    FocusTile(onClick = { vm.askPin("Enter your PIN to change locks") { vm.toggleCategoryLock(c.id) } }, selected = locked, corner = 8.dp, focusedScale = 1.02f, modifier = Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text((if (locked) "🔒  " else "      ") + categoryLabel(c.name), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplaySettings(vm: AppViewModel) {
    Setting(
        "Text size",
        "Makes all the writing in ShedTV bigger, for easier reading from the sofa.",
        "No effect on speed. At the larger sizes some long names are shortened with \"…\" to fit.",
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("Normal", selected = vm.textScale < 1.05f) { vm.chooseTextScale(1f) }
            Chip("Large", selected = vm.textScale in 1.05f..1.2f) { vm.chooseTextScale(1.15f) }
            Chip("Extra large", selected = vm.textScale > 1.2f) { vm.chooseTextScale(1.3f) }
        }
    }
    Setting(
        "High contrast",
        "Pure black backgrounds, brighter text and stronger outlines, which helps with low vision or a bright room.",
        "No effect on speed.",
    ) { OnOff(vm.highContrast) { vm.turnHighContrast(it) } }
    Setting(
        "Highlight colour",
        "The colour used for the highlight, buttons and progress bars. Yellow and blue are easiest to tell apart with colour blindness.",
        "No effect on speed.",
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Palette.accents.forEach { (name, argb) ->
                FocusTile(onClick = { vm.chooseAccent(argb) }, selected = vm.accent == argb, corner = 10.dp, focusedScale = 1.06f) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.foundation.layout.Box(Modifier.size(16.dp).background(Color(argb), RoundedCornerShape(8.dp)))
                        Text(name, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
    Setting(
        "TV guide rows",
        "Compact fits more channels on screen at once; large makes each row taller and easier to read.",
        "No effect on speed.",
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("Compact", selected = vm.guideRows == "compact") { vm.chooseGuideRows("compact") }
            Chip("Normal", selected = vm.guideRows == "normal") { vm.chooseGuideRows("normal") }
            Chip("Large", selected = vm.guideRows == "large") { vm.chooseGuideRows("large") }
        }
    }
    Setting(
        "Clock",
        "A small clock in the top-right corner of the screen. Choose whether it also shows while you're watching.",
        "No effect on speed.",
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip("Off", selected = vm.clockMode == "off") { vm.chooseClock("off") }
            Chip("Menus only", selected = vm.clockMode == "menus") { vm.chooseClock("menus") }
            Chip("Everywhere, including while watching", selected = vm.clockMode == "always") { vm.chooseClock("always") }
        }
    }
    Setting(
        "Animations",
        "Gentle fades and slides when moving between screens.",
        "Turning them off makes screen changes instant, which can feel snappier on a Fire Stick or older box.",
    ) { OnOff(vm.animations) { vm.setAnimationsEnabled(it) } }
}

@Composable
private fun AboutSettings(vm: AppViewModel) {
    Setting(
        "App update",
        (vm.updater.status ?: "You have ShedTV ${vm.updater.versionName} (build ${vm.updater.currentBuild}).") +
            " ShedTV checks for new versions when it opens; you can also check now. Updates keep all your settings, subscriptions and favourites.",
        if (!vm.updater.canInstall) "The first time, your device needs to allow ShedTV to install apps. Install opens that switch for you." else null,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Chip(if (vm.updater.busy) "Working…" else "Check for updates") { vm.updater.check(silent = false) }
            if (vm.updater.available) Chip("Install build ${vm.updater.latestBuild}", selected = true) { vm.updater.install() }
        }
    }
    Setting(
        "About ShedTV",
        "ShedTV is a free player. It doesn't provide any channels or content: you sign in with a TV service you're entitled to use. Install guides and news: shedtv.app",
    ) {
        Text("Fonts: Barlow Condensed and Inter, used under the SIL Open Font License 1.1 (full text included in the app).",
            color = Palette.TextMuted, fontSize = 11.sp)
    }
}

@Composable
private fun Heading(title: String, subtitle: String) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(title, color = Palette.Text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Palette.TextMuted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Chip(label: String, selected: Boolean = false, danger: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusTile(onClick = onClick, selected = selected, corner = 10.dp, focusedScale = 1.05f, modifier = modifier) {
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (danger) Palette.Live else LocalContentColor.current,
            modifier = Modifier
                .background(if (selected) Color.Transparent else Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}
