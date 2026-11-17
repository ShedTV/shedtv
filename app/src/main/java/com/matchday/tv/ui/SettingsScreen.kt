@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val active = vm.activeSubscription
    val firstFocus = remember { FocusRequester() }
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    var customUa by rememberSaveable(active?.id) { mutableStateOf(active?.userAgent.orEmpty()) }
    LaunchedEffect(Unit) { delay(100); runCatching { firstFocus.requestFocus() } }
    BackHandler { vm.navigate(Screen.Sport) }

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        NavRail(vm, Screen.Settings)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 32.dp, end = 32.dp, top = 20.dp, bottom = 72.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("SETTINGS", color = Palette.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
            vm.error?.let { Text(it, color = Palette.Live, fontSize = 14.sp) }

            // ---- Subscriptions ----
            Heading("Subscriptions", "OK to switch  ·  Hold OK to edit")
            vm.subscriptions.forEachIndexed { i, sub ->
                val isActive = sub.id == active?.id
                FocusTile(
                    onClick = { vm.switchSubscription(sub) },
                    onLongClick = { vm.startEditSubscription(sub) },
                    selected = isActive, corner = 12.dp, focusedScale = 1.02f,
                    modifier = Modifier.width(620.dp).then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            Text("${sub.host}  ·  ${sub.username}" + (if (!isActive && vm.useBackups) vm.backups[sub.id]?.status?.let { "  ·  $it" }.orEmpty() else ""), fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
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

            if (vm.subscriptions.size > 1) {
                Spacer(Modifier.height(2.dp))
                Heading("Use other subscriptions as backup", "If a channel fails, try the same channel on your other subscriptions. Search also covers them.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("On", selected = vm.useBackups) { vm.turnBackups(true) }
                    Chip("Off", selected = !vm.useBackups) { vm.turnBackups(false) }
                }
            }

            if (active != null) {
                // ---- User agent ----
                Spacer(Modifier.height(2.dp))
                Heading("User agent for ${active.name}", "The name ShedTV gives your provider. Currently: ${UserAgents.labelFor(active.userAgent)}")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    UserAgents.presets.forEach { p ->
                        Chip(p.label, selected = active.userAgent == p.value) {
                            vm.updateActive(userAgent = p.value)
                            customUa = p.value
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = customUa, onValueChange = { customUa = it }, singleLine = true,
                        label = { androidx.compose.material3.Text("Custom user agent") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done, autoCorrectEnabled = false),
                        keyboardActions = KeyboardActions(onDone = { vm.updateActive(userAgent = customUa) }),
                        modifier = Modifier.width(520.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Palette.Text, unfocusedTextColor = Palette.Text,
                            focusedBorderColor = Palette.Accent, unfocusedBorderColor = Palette.TextMuted,
                            focusedLabelColor = Palette.Accent, unfocusedLabelColor = Palette.TextMuted, cursorColor = Palette.Accent,
                            focusedContainerColor = Palette.Card, unfocusedContainerColor = Palette.Card,
                        ),
                    )
                    Chip("Use this") { vm.updateActive(userAgent = customUa) }
                }
                Text("Applies to channel lists, the TV guide and streams. Takes effect on the next channel you open.", color = Palette.TextMuted, fontSize = 12.sp)

                // ---- Playback ----
                Spacer(Modifier.height(2.dp))
                Heading("Categories", "${vm.hiddenCategories.size} hidden. Hidden categories stay out of Live TV, the guide and search.")
                Chip("Manage categories") { vm.navigate(Screen.Categories) }

                Spacer(Modifier.height(2.dp))
                Heading("One entry per channel", "Hides UHD, FHD, HD and SD duplicates and plays the best one. Press ▶▶ while watching live to try another version.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("On", selected = vm.mergeQualities) { vm.setMergeQualitiesEnabled(true) }
                    Chip("Off", selected = !vm.mergeQualities) { vm.setMergeQualitiesEnabled(false) }
                }

                Spacer(Modifier.height(2.dp))
                Heading("Stream format", "Try HLS if some channels won't play")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("MPEG-TS", selected = vm.streamFormat == "ts") { vm.updateActive(streamFormat = "ts") }
                    Chip("HLS (m3u8)", selected = vm.streamFormat == "m3u8") { vm.updateActive(streamFormat = "m3u8") }
                }

                Spacer(Modifier.height(2.dp))
                Heading("Live rewind", "Pause and rewind live TV, up to about 30 minutes. Uses up to 1.5 GB of storage while watching. MPEG-TS format only.")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("On", selected = vm.liveRewind) { vm.setLiveRewindEnabled(true) }
                    Chip("Off", selected = !vm.liveRewind) { vm.setLiveRewindEnabled(false) }
                }

                Spacer(Modifier.height(2.dp))
                Heading("TV guide", vm.guideStatus ?: "Refreshes every 8 hours")
                Chip("Refresh guide now") { vm.loadGuide(force = true) }
            }

            // ---- Downloads ----
            Spacer(Modifier.height(2.dp))
            Heading("Downloads", "Films and episodes to watch offline. Hold OK on an episode, or use Download on a film or season.")
            val ctx = androidx.compose.ui.platform.LocalContext.current
            val picker = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
            ) { uri -> if (uri != null) vm.pickedDownloadFolder(uri) }
            val canPick = remember {
                android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).resolveActivity(ctx.packageManager) != null
            }
            val current = vm.downloadFolder()
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.matchday.tv.download.DownloadManager.volumes().forEach { v ->
                    Chip("${v.label} · %.1f GB free".format(v.freeBytes / 1073741824.0), selected = current == v.dir.path) { vm.setDownloadFolder(v.dir.path) }
                }
                if (current?.startsWith("content://") == true) {
                    Chip("Folder: " + com.matchday.tv.download.DownloadManager.folderName(current), selected = true) { }
                }
                if (canPick) Chip("Choose folder…") { runCatching { picker.launch(null) }.onFailure { vm.flash = "This device doesn't have a folder picker." } }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Wi-Fi only", selected = vm.downloadWifiOnly) { vm.turnDownloadWifiOnly(true) }
                Chip("Any connection", selected = !vm.downloadWifiOnly) { vm.turnDownloadWifiOnly(false) }
            }
            Text(
                "ShedTV's own folders are removed if ShedTV is uninstalled. A chosen folder keeps your files, and other apps such as VLC can play them. Downloads use one connection and pause while you watch on a one-connection subscription.",
                color = Palette.TextMuted, fontSize = 11.sp,
            )

            // ---- Recording ----
            Spacer(Modifier.height(2.dp))
            Heading("Recording", "Off by default. Records to this device or a USB drive, and keeps going through glitches by switching source.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("On", selected = vm.recordingEnabled) { vm.turnRecording(true) }
                Chip("Off", selected = !vm.recordingEnabled) { vm.turnRecording(false) }
            }
            if (vm.recordingEnabled) {
                val vols = com.matchday.tv.record.RecorderManager.volumes()
                val chosen = vm.recordingVolume()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    vols.forEach { v ->
                        Chip("${v.label} · %.1f GB free".format(v.freeBytes / 1073741824.0), selected = chosen?.dir == v.dir) { vm.chooseRecordingVolume(v) }
                    }
                }
                val free = chosen?.freeBytes ?: 0
                if (free < 16L * 1024 * 1024 * 1024) {
                    Text(
                        "⚠ Only %.1f GB free here: about %d hour(s) of HD sport at roughly 3 GB per hour. Fire Sticks have very little space; a Shield or box with a USB drive is much better for recording."
                            .format(free / 1073741824.0, free / com.matchday.tv.record.RecorderManager.BYTES_PER_HOUR),
                        color = Palette.Live, fontSize = 12.sp,
                    )
                }
                Text("Recordings on a USB drive are kept in ShedTV's own folder there, and are removed if ShedTV is uninstalled.", color = Palette.TextMuted, fontSize = 11.sp)
            }

            // ---- Preview and display ----
            Spacer(Modifier.height(2.dp))
            Heading("Preview window", "Plays the highlighted channel, muted, in Live TV and the guide. Uses your one connection, and carries on into full screen when you press OK.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("On", selected = vm.previewEnabled) { vm.turnPreview(true) }
                Chip("Off", selected = !vm.previewEnabled) { vm.turnPreview(false) }
            }
            Spacer(Modifier.height(2.dp))
            Heading("Stay at 50Hz", "Switches the TV to 50Hz once when ShedTV opens, so going full screen and back doesn't blank. 60fps channels still switch. Needs Match Original Frame Rate on in Fire TV settings.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("On", selected = vm.stay50Hz) { vm.setStay50HzEnabled(true) }
                Chip("Off", selected = !vm.stay50Hz) { vm.setStay50HzEnabled(false) }
            }

            // ---- Look ----
            Spacer(Modifier.height(2.dp))
            Heading("Animations", "Fades and slides between screens. Turn off if anything feels slow.")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("On", selected = vm.animations) { vm.setAnimationsEnabled(true) }
                Chip("Off", selected = !vm.animations) { vm.setAnimationsEnabled(false) }
            }

            // ---- App updates ----
            Spacer(Modifier.height(2.dp))
            Heading("App update", vm.updater.status ?: "ShedTV ${vm.updater.versionName}")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip(if (vm.updater.busy) "Working…" else "Check for updates") { vm.updater.check(silent = false) }
                if (vm.updater.available) Chip("Install build ${vm.updater.latestBuild}", selected = true) { vm.updater.install() }
            }
            if (!vm.updater.canInstall) {
                Text("Before the first in-app update, Fire TV needs to allow ShedTV to install apps. Install will open that switch for you.",
                    color = Palette.TextMuted, fontSize = 12.sp)
            }
            Text("Fonts: Barlow Condensed and Inter, used under the SIL Open Font License 1.1 (full text included in the app).",
                color = Palette.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        }
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
