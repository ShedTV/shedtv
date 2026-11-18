@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
    Saving("Recording & downloads", "●", "Recording live TV and saving films for offline"),
    Display("Display", "◐", "Clock and animations"),
    About("About & updates", "ⓘ", "Version, updates and licences"),
}

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var tab by rememberSaveable { mutableStateOf(SettingsTab.Subs) }
    val tabFocus = remember { FocusRequester() }
    var inContent by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(100); runCatching { tabFocus.requestFocus() } }
    BackHandler {
        if (inContent) runCatching { tabFocus.requestFocus() } else vm.navigate(Screen.Sport)
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
                    onClick = { tab = t; },
                    selected = sel, corner = 10.dp, focusedScale = 1.03f,
                    modifier = Modifier.fillMaxWidth()
                        .then(if (sel) Modifier.focusRequester(tabFocus) else Modifier)
                        .onFocusChanged { if (it.isFocused) tab = t },
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
                SettingsTab.Display -> DisplaySettings(vm)
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
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("+  Add subscription") { vm.startAddSubscription() }
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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        "Preview window",
        "Plays the highlighted channel, muted, in a small window in Live TV and the guide. Press OK and it carries on full screen without restarting.",
        "Uses your connection while you browse, and on a Fire Stick moving between channels can feel a little slower. On one-connection subscriptions, downloads pause while it plays.",
    ) { OnOff(vm.previewEnabled) { vm.turnPreview(it) } }

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
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
private fun DisplaySettings(vm: AppViewModel) {
    Setting(
        "Clock",
        "A small clock in the top-right corner of the screen. Choose whether it also shows while you're watching.",
        "No effect on speed.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
private fun Chip(label: String, selected: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
    FocusTile(onClick = onClick, selected = selected, corner = 10.dp, focusedScale = 1.05f) {
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            color = if (danger) Palette.Live else LocalContentColor.current,
            modifier = Modifier
                .background(if (selected) Color.Transparent else Color(0x1AFFFFFF), RoundedCornerShape(10.dp))
                .padding(horizontal = 16.dp, vertical = 9.dp),
        )
    }
}
