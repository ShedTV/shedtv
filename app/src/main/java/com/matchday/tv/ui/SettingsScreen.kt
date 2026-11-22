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
    Subs("Subscriptions", "⌂", "Your TV services and how ShedTV talks to them."),
    Tv("TV & guide", "▦", "Channel lists, the guide and the preview window."),
    Playback("Playback", "▶", "Rewind, subtitles and picture."),
    Saving("Recording & downloads", "●", "Recordings are live TV you've recorded. Downloads are films and episodes saved to watch offline."),
    Menu("Menu & start screen", "☰", "Which sections you see, their order, and where ShedTV opens."),
    Display("Display", "◐", "Text size, contrast, colour, clock and animations."),
    Lock("Parental lock", "🔒", "A PIN for the categories you choose."),
    Kids("Kids mode", "☺", "Only children's channels, films and series, with bedtime and time limits."),
    About("About & updates", "ⓘ", "Version, updates and licences."),
}

/** The explanation shown for whatever setting is highlighted. */
private class Help(val title: String, val body: String, val impact: String?)

private class SettingsUi(
    val vm: AppViewModel,
    /** TVs and big tablets: explanations sit in a panel on the right. Phones: an ⓘ opens them under the row. */
    val wide: Boolean,
) {
    var help by mutableStateOf<Help?>(null)
}

private val LocalSettingsUi = androidx.compose.runtime.staticCompositionLocalOf<SettingsUi> { error("Settings only") }

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val tab = SettingsTab.entries.getOrElse(vm.settingsTab) { SettingsTab.Subs }
    val tabFocus = remember { FocusRequester() }
    var inContent by remember { mutableStateOf(false) }
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
    val ui = remember(wide) { SettingsUi(vm, wide) }
    LaunchedEffect(Unit) { if (vm.settingsFocus == null) { delay(100); runCatching { tabFocus.requestFocus() } } }
    BackHandler {
        if (inContent) runCatching { tabFocus.requestFocus() } else vm.goHome()
    }
    // While you're in the list, the section names shrink to icons to make room for the explanations.
    val slimTabs = wide && inContent

    androidx.compose.runtime.CompositionLocalProvider(LocalSettingsUi provides ui) {
        Row(Modifier.fillMaxSize().background(Palette.Background)) {
            NavRail(vm, Screen.Settings)
            Column(
                Modifier.enterAt(tabFocus).width(if (slimTabs) 64.dp else 230.dp).fillMaxHeight().verticalScroll(rememberScrollState())
                    .padding(start = if (slimTabs) 8.dp else 20.dp, top = 22.dp, end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(if (slimTabs) "" else "SETTINGS", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp,
                    maxLines = 1, modifier = Modifier.padding(start = 6.dp, bottom = 10.dp))
                SettingsTab.entries.forEach { t ->
                    val sel = t == tab
                    FocusTile(
                        onClick = { vm.settingsTab = t.ordinal },
                        selected = sel, corner = 10.dp, focusedScale = 1.03f,
                        modifier = Modifier.fillMaxWidth()
                            .then(if (sel) Modifier.focusRequester(tabFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused) { vm.settingsTab = t.ordinal; ui.help = null } },
                    ) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = if (slimTabs) 0.dp else 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = if (slimTabs) Arrangement.Center else Arrangement.Start) {
                            Text(t.icon, fontSize = 16.sp, modifier = if (slimTabs) Modifier else Modifier.width(28.dp))
                            if (!slimTabs) Text(t.label, fontSize = 15.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (!slimTabs) {
                    Spacer(Modifier.height(16.dp))
                    Text("Right to change settings. Back returns here.", color = Palette.TextMuted, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 6.dp, bottom = 24.dp))
                }
            }
            // The settings: one line each.
            Column(
                Modifier.weight(1f).fillMaxHeight().onFocusChanged { inContent = it.hasFocus }
                    .verticalScroll(rememberScrollState()).padding(start = 16.dp, end = if (wide) 12.dp else 24.dp, top = 24.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(tab.label.uppercase(), color = Palette.Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
                if (!wide || !inContent) Text(tab.blurb, color = Palette.TextMuted, fontSize = 13.sp)
                vm.error?.let { Text(it, color = Palette.Live, fontSize = 14.sp) }
                Spacer(Modifier.height(4.dp))
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
            // The explanation for whatever is highlighted.
            // Only while you're on a setting, and only as tall as what it says.
            val h = ui.help
            if (wide && inContent && h != null) {
                Column(
                    Modifier.width(300.dp).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 64.dp, bottom = 24.dp, end = 24.dp),
                ) {
                    Column(
                        Modifier.fillMaxWidth().background(Palette.Card, RoundedCornerShape(14.dp)).padding(horizontal = 18.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(h.title, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text(h.body, color = Palette.TextSoft, fontSize = 14.sp, lineHeight = 20.sp)
                        h.impact?.let { Text("ⓘ  $it", color = Palette.TextMuted, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
        }
    }
}

private val TagColour = Color(0xFFFFB648)

/** A short warning next to a setting that really affects something ("Uses storage"). */
@Composable
private fun Tag(text: String) {
    Text(
        text, color = TagColour, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.background(TagColour.copy(alpha = 0.14f), RoundedCornerShape(6.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** Phones: the explanation opens under the row. */
@Composable
private fun InlineHelp(h: Help) {
    Column(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 6.dp)
            .background(Palette.Card, RoundedCornerShape(10.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(h.body, color = Palette.TextSoft, fontSize = 13.sp)
        h.impact?.let { Text("ⓘ  $it", color = Palette.TextMuted, fontSize = 12.sp) }
    }
}

@Composable
private fun InfoButton(open: Boolean, onClick: () -> Unit) {
    FocusTile(onClick = onClick, selected = open, corner = 18.dp, focusedScale = 1.05f, modifier = Modifier.padding(start = 6.dp)) {
        Text("ⓘ", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

/**
 * One setting on one line: name, an optional warning tag, and the current value. OK (or a tap) changes it.
 * The full explanation shows in the panel on the right while it's highlighted.
 */
@Composable
private fun SettingRow(
    title: String,
    body: String,
    impact: String? = null,
    tag: String? = null,
    value: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val ui = LocalSettingsUi.current
    val h = Help(title, body, impact)
    val focus = remember { FocusRequester() }
    var open by remember { mutableStateOf(false) }
    // A change that rebuilds the screen (colour, text size) lands back on this row.
    LaunchedEffect(Unit) {
        if (ui.vm.settingsFocus == title) { delay(150); runCatching { focus.requestFocus() }; ui.vm.settingsFocus = null }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FocusTile(
                onClick = { ui.vm.settingsFocus = title; onClick(); ui.help = h },
                onLongClick = onLongClick,
                corner = 10.dp, focusedScale = 1.01f,
                modifier = Modifier.weight(1f).background(Palette.Card.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .focusRequester(focus).onFocusChanged { if (it.isFocused) ui.help = h },
            ) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        if (tag != null) Tag(tag)
                    }
                    if (value != null) Text(value + "  ›", fontSize = 14.sp, maxLines = 1, color = LocalContentColor.current.copy(alpha = 0.75f))
                }
            }
            if (!ui.wide) InfoButton(open) { open = !open }
        }
        if (!ui.wide && open) InlineHelp(h)
    }
}

/** On/off on one line: OK flips it. */
@Composable
private fun ToggleRow(title: String, body: String, impact: String? = null, tag: String? = null, on: Boolean, onText: String = "On", offText: String = "Off", set: (Boolean) -> Unit) =
    SettingRow(title, body, impact, tag, value = if (on) onText else offText) { set(!on) }

/** A few choices on one line: OK steps to the next one. */
@Composable
private fun <T> ChoiceRow(title: String, body: String, impact: String? = null, tag: String? = null, options: List<Pair<T, String>>, current: T, choose: (T) -> Unit) {
    val i = options.indexOfFirst { it.first == current }
    SettingRow(title, body, impact, tag, value = options.getOrNull(i)?.second ?: options.first().second) {
        choose(options[(i + 1).mod(options.size)].first)
    }
}

/** A setting with its own little list or buttons (subscriptions, menu order...): a one-line heading, then the controls. */
@Composable
private fun SettingBlock(title: String, body: String, impact: String? = null, tag: String? = null, controls: @Composable () -> Unit) {
    val ui = LocalSettingsUi.current
    val h = Help(title, body, impact)
    var open by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxWidth()
            .onFocusChanged { if (it.hasFocus) ui.help = h }
            .background(Palette.Card.copy(alpha = 0.5f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Palette.TextSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp).weight(1f, fill = false))
            if (tag != null) Tag(tag)
            if (!ui.wide) { Spacer(Modifier.weight(1f)); InfoButton(open) { open = !open } }
        }
        if (!ui.wide && open) InlineHelp(h)
        controls()
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
}

@Composable
private fun SubscriptionSettings(vm: AppViewModel) {
    val active = vm.activeSubscription
    var confirmRemove by remember { mutableStateOf<String?>(null) }
    var editingUa by remember { mutableStateOf(false) }
    var customUa by rememberSaveable(active?.id) { mutableStateOf(active?.userAgent.orEmpty()) }

    SettingBlock(
        "Your subscriptions",
        "The TV services ShedTV signs in to. OK switches to one; hold OK on one to edit it. Favourites, history, hidden categories and category order are kept separately for each.",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            vm.subscriptions.forEach { sub ->
                val isActive = sub.id == active?.id
                FocusTile(
                    onClick = { vm.switchSubscription(sub) },
                    onLongClick = { vm.startEditSubscription(sub) },
                    selected = isActive, corner = 12.dp, focusedScale = 1.01f,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(sub.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            val kind = if (sub.playlist) "M3U playlist" else "${sub.host}  ·  ${sub.username}"
                            Text(kind + (if (!isActive && vm.useBackups) vm.backups[sub.id]?.status?.let { "  ·  $it" }.orEmpty() else ""),
                                fontSize = 12.sp, color = LocalContentColor.current.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (isActive) Text("IN USE", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    }
                }
            }
            ChipRow {
                Chip("+  Add") { vm.startAddSubscription() }
                if (active != null) {
                    Chip("Edit") { vm.startEditSubscription(active) }
                    Chip(if (confirmRemove == active.id) "Press again to remove" else "Remove", danger = confirmRemove == active.id) {
                        if (confirmRemove == active.id) { confirmRemove = null; vm.removeSubscription(active) } else confirmRemove = active.id
                    }
                }
            }
        }
    }

    SettingRow(
        "Copy a setup to this device",
        "Type your subscription details on your phone instead of with the remote, or copy everything from another ShedTV: subscriptions, favourites, settings and the parental lock.",
        value = "Open",
    ) { vm.openReceive() }
    SettingRow(
        "Send my setup to another device",
        "Copies this ShedTV's subscriptions, favourites, settings and parental lock to another ShedTV on the same Wi-Fi. You'll get a 6-digit code to type on the other device.",
        value = "Open",
    ) { vm.navigate(Screen.SendSetup) }

    if (vm.subscriptions.size > 1) {
        ToggleRow(
            "Use other subscriptions as backup",
            "If a channel won't play, ShedTV tries the same channel on your other subscriptions, and search shows their channels and films too.",
            "Their channel lists load in the background when ShedTV opens, which takes a little longer on slow boxes. No extra connections are used until a backup actually plays.",
            tag = "Slower start", on = vm.useBackups,
        ) { vm.turnBackups(it) }
    }

    if (active != null) {
        if (!active.playlist) {
            ChoiceRow(
                "Stream format",
                "How live channels are sent to ShedTV. MPEG-TS starts quickest and works with live rewind and recording, so it's the best choice for most people. Try HLS only if some channels won't play.",
                "With HLS, live rewind is unavailable. No difference in picture quality.",
                options = listOf("ts" to "MPEG-TS", "m3u8" to "HLS"), current = vm.streamFormat,
            ) { vm.updateActive(streamFormat = it) }
        }

        SettingBlock(
            "User agent for ${active.name}",
            "The name ShedTV gives your provider when it connects, the way a web browser says which browser it is. Leave it on Android device unless your provider blocks apps or asks for a particular player. Currently: ${UserAgents.labelFor(active.userAgent)}.",
            "Takes effect from the next channel you open. No effect on speed.",
        ) {
            ChipRow {
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
                        modifier = Modifier.weight(1f).focusRequester(field),
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
    }
}

@Composable
private fun TvSettings(vm: AppViewModel) {
    SettingRow(
        "Categories",
        "Hide categories you never use and put the rest in the order you like. Hidden categories also stay out of the guide and search. Shortcut: hold OK on any category in Live TV or the guide.",
        "Hiding categories you don't watch makes the guide and channel lists quicker on slower boxes.",
        value = if (vm.hiddenCategories.isEmpty()) "Manage" else "${vm.hiddenCategories.size} hidden",
    ) { vm.navigate(Screen.Categories) }

    ToggleRow(
        "One entry per channel",
        "Many providers list each channel several times (UHD, FHD, HD and SD). With this on you see each channel once, ShedTV plays the best version, and it switches to another version by itself if one fails. Press ▶▶ while watching to try the next version.",
        "No effect on speed; channel lists are shorter and easier to scroll.",
        on = vm.mergeQualities,
    ) { vm.setMergeQualitiesEnabled(it) }

    ChoiceRow(
        "What OK does in Live TV",
        "Full screen: OK on a channel goes straight to full screen. Preview first: the first OK plays it in the preview window so you can check what's on, and a second OK goes full screen.",
        "Preview first needs the preview window turned on.",
        options = listOf("play" to "Full screen", "preview" to "Preview first"), current = vm.okAction,
    ) { vm.chooseOkAction(it) }

    ToggleRow(
        "Preview window",
        "Plays the highlighted channel, muted, in a small window in Live TV and the guide. Press OK and it carries on full screen without restarting.",
        "Uses your connection while you browse, and on a Fire Stick moving between channels can feel a little slower. On one-connection subscriptions, downloads pause while it plays.",
        tag = "Uses data", on = vm.previewEnabled,
    ) { vm.turnPreview(it) }

    if (vm.previewEnabled) {
        ChoiceRow(
            "Preview shows",
            "What the preview window plays while you browse. \"What I was watching\" is handy in ad breaks: look around, then tap the preview (or use Back to … at the top of the channel list) to carry on.",
            options = listOf("follow" to "Highlighted channel", "stay" to "What I was watching"), current = vm.previewMode,
        ) { vm.choosePreviewMode(it) }
    }

    if (vm.activeSubscription != null) {
        SettingRow(
            "TV guide",
            (vm.guideStatus ?: "The guide is up to date.") + " It refreshes itself every 8 hours; OK refreshes it now if listings look out of date.",
            "A refresh downloads the whole guide, which can take up to a minute on big providers. You can keep watching while it does.",
            value = "Refresh now",
        ) { vm.loadGuide(force = true) }
    }
}

@Composable
private fun PlaybackSettings(vm: AppViewModel) {
    ToggleRow(
        "Live rewind",
        "Keeps up to the last 30 minutes of the channel you're watching, so you can pause live TV and rewind. It starts from the moment you tune in.",
        "Uses up to 1.5 GB of storage while you watch, freed when you stop. On a nearly full Fire Stick, leave it off. MPEG-TS stream format only.",
        tag = "Uses storage", on = vm.liveRewind,
    ) { vm.setLiveRewindEnabled(it) }

    ToggleRow(
        "Subtitles",
        "Shows subtitles when a channel, film or episode includes them. You can also change this while watching: hold OK (or press ☰) for subtitles and audio.",
        "No effect on speed. Not every channel broadcasts subtitles.",
        on = vm.subtitlesEnabled, onText = "When available",
    ) { vm.setSubtitlePref(it, null) }

    ToggleRow(
        "Stay at 50Hz",
        "UK TV runs at 50 frames a second. Many TVs blank for a second whenever the frame rate changes, for example going full screen. With this on, ShedTV switches the TV to 50Hz once when it opens, so it doesn't blank again. Channels at 60 frames a second still switch.",
        "No effect on speed. Needs \"Match original frame rate\" (or similar) turned on in your device's display settings.",
        on = vm.stay50Hz,
    ) { vm.setStay50HzEnabled(it) }
}

@Composable
private fun SavingSettings(vm: AppViewModel) {
    ToggleRow(
        "Recording",
        "Record live TV from the player or the guide (hold OK, or press ☰). If the stream glitches, ShedTV keeps going: it reconnects, tries another version of the channel, then your other subscriptions, and notes any gaps.",
        "Uses about 3 GB per hour of HD. Recording uses a connection, so on one-connection subscriptions you can only watch what's being recorded. Works best on a Shield or box with a USB drive.",
        tag = "Uses storage", on = vm.recordingEnabled,
    ) { vm.turnRecording(it) }

    if (vm.recordingEnabled) {
        val chosen = vm.recordingVolume()
        SettingBlock(
            "Save recordings to",
            "Where recordings are kept. A USB drive is best: recordings are big. Recordings are kept in ShedTV's own folder and are removed if ShedTV is uninstalled.",
            "About 3 GB per hour of HD.",
        ) {
            ChipRow {
                com.matchday.tv.record.RecorderManager.volumes().forEach { v ->
                    Chip("${v.label} · %.1f GB free".format(v.freeBytes / 1073741824.0), selected = chosen?.dir == v.dir) { vm.chooseRecordingVolume(v) }
                }
            }
            val free = chosen?.freeBytes ?: 0
            if (free < 16L * 1024 * 1024 * 1024) {
                Text("⚠ Only %.1f GB free here: about %d hour(s) of HD.".format(free / 1073741824.0, free / com.matchday.tv.record.RecorderManager.BYTES_PER_HOUR),
                    color = Palette.Live, fontSize = 12.sp)
            }
        }
    }

    val ctx = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> if (uri != null) vm.pickedDownloadFolder(uri) }
    val canPick = remember { android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE).resolveActivity(ctx.packageManager) != null }
    val current = vm.downloadFolder()
    SettingBlock(
        "Save downloads to",
        "Save films and episodes to watch without the internet, such as on a plane. Use Download on a film or season, or hold OK on an episode. With no internet, ShedTV opens straight to your downloads. ShedTV's own folders are removed if ShedTV is uninstalled; a folder you choose keeps your files, and other apps such as VLC can play them.",
        "One download at a time, using one connection; on one-connection subscriptions downloads pause while you watch. A film is usually 1 to 4 GB.",
        tag = "Uses storage",
    ) {
        ChipRow {
            com.matchday.tv.download.DownloadManager.volumes().forEach { v ->
                Chip("${v.label} · %.1f GB free".format(v.freeBytes / 1073741824.0), selected = current == v.dir.path) { vm.setDownloadFolder(v.dir.path) }
            }
            if (current?.startsWith("content://") == true) Chip("Folder: " + com.matchday.tv.download.DownloadManager.folderName(current), selected = true) { }
            if (canPick) Chip("Choose folder…") { runCatching { picker.launch(null) }.onFailure { vm.flash = "This device doesn't have a folder picker." } }
        }
    }

    ToggleRow(
        "Download over",
        "Wi-Fi only stops phones using mobile data for downloads. TV boxes are always treated as Wi-Fi.",
        on = vm.downloadWifiOnly, onText = "Wi-Fi only", offText = "Any connection",
    ) { vm.turnDownloadWifiOnly(it) }
}

@Composable
private fun MenuSettings(vm: AppViewModel) {
    SettingBlock(
        "Sections in the menu",
        "Choose which sections appear in the menu on the left, and their order. OK shows or hides a section. To move one: hold OK to pick it up, move it with up and down, then press OK to put it down. Hidden sections keep everything in them: show them again and it's all still there. Settings always stays at the bottom.",
        "No effect on speed. Recordings appears once recording is turned on (Recording & downloads).",
    ) {
        var moving by remember { mutableStateOf<String?>(null) }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            vm.orderedMenu().forEach { id -> androidx.compose.runtime.key(id) {
                val shown = id !in vm.menuHidden
                val isMoving = moving == id
                FocusTile(
                    onClick = { if (isMoving) moving = null else vm.setMenuShown(id, !shown) },
                    onLongClick = { moving = if (isMoving) null else id },
                    selected = isMoving, corner = 10.dp, focusedScale = 1.01f,
                    modifier = Modifier.fillMaxWidth().onFocusChanged { if (!it.isFocused && moving == id) moving = null }.onPreviewKeyEvent { e ->
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
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text((if (isMoving) "↕  " else "") + menuLabel(id), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                            color = if (shown || isMoving) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.45f))
                        Text(if (isMoving) "Up/down to move · OK to drop" else if (shown) "✓ Shown" else "Hidden", fontSize = 13.sp, color = LocalContentColor.current.copy(alpha = 0.75f))
                    }
                }
            } }
            Chip("Reset to the usual menu") { vm.resetMenu() }
        }
    }

    ChoiceRow(
        "Open ShedTV on",
        "The screen you see when ShedTV starts. Back from anywhere also brings you here, and Back on this screen closes ShedTV.",
        "No effect on speed.",
        options = listOf("home" to "Home", "live" to "Live TV", "guide" to "Guide", "last" to "Wherever I was last"), current = vm.startScreen,
    ) { vm.chooseStart(it) }

    SettingRow(
        "Watchlist",
        "Things ShedTV looks for in your guide (teams, shows, events) to show on Home, with a reminder just before they start.",
        value = "${vm.watchlist.items.size} item" + if (vm.watchlist.items.size == 1) "" else "s",
    ) { vm.navigate(Screen.Watchlist) }
}

@Composable
private fun KidsSettings(vm: AppViewModel) {
    SettingBlock(
        "Kids mode",
        if (vm.kidsMode) "Kids mode is on. Only the categories you chose are visible, and Settings needs your PIN. ${vm.kidsLive.size} live, ${vm.kidsMovies.size} film and ${vm.kidsSeries.size} series categories are allowed."
        else "One step to make ShedTV child-friendly: only children's channels, films and series are shown, and everything else (including anything your provider adds later) stays hidden. Settings, subscriptions and turning it off need your PIN.",
        "No effect on speed. It filters by category name, so check the suggested list once; it's a filter, not a guarantee.",
    ) {
        ChipRow {
            if (!vm.kidsMode) {
                Chip("Set up Kids mode", selected = true) { if (vm.pinSet) vm.navigate(Screen.KidsSetup) else vm.setNewPinThen { vm.navigate(Screen.KidsSetup) } }
            } else {
                Chip("Change what's allowed") { vm.navigate(Screen.KidsSetup) }
                Chip("Turn Kids mode off", danger = true) { vm.stopKids() }
            }
        }
    }
    if (vm.kidsMode) SettingBlock("Bedtime, limits and Home", "Bedtime stops ShedTV at a set time, a daily limit stops it after so long, and the kids' Home keeps things simple. Changes take effect straight away.") { KidsExtras(vm) }
    SettingRow(
        "Stop kids uninstalling ShedTV",
        "Kids mode keeps ShedTV child-friendly, but it can't stop someone uninstalling ShedTV, clearing its data or opening other apps. Use your device's own controls as well:\n\n" +
            "•  Fire TV: Settings → Preferences → Parental Controls. Turn them on and set a PIN.\n" +
            "•  Google TV, Chromecast and Google TV Streamer: add a Kids profile for your child and allow ShedTV in it. Children can't uninstall apps from a kids profile.\n" +
            "•  Shield and other Android TV boxes: Settings → Device Preferences → Security & restrictions → Create restricted profile, then allow ShedTV.",
        "Menu names may differ slightly on your device. Use a different PIN from your ShedTV one. More help: shedtv.app/kids",
        value = "How",
    ) { }
}

@Composable
private fun LockSettings(vm: AppViewModel) {
    SettingBlock(
        "PIN",
        if (vm.pinSet) "A PIN is set. Locked categories are hidden from Live TV, the guide, search and Home until someone enters it; once entered they stay open until ShedTV is closed or you lock them again."
        else "Set a 4-digit PIN, then choose which categories need it. Locked categories are hidden from every list until the PIN is entered.",
        "No effect on speed. If you forget the PIN, clearing ShedTV's data (in your device's app settings) removes it, along with your other settings.",
    ) {
        ChipRow {
            if (!vm.pinSet) Chip("Set a PIN") { vm.setNewPin() }
            else {
                Chip("Change PIN") { vm.askPin("Enter your current PIN") { vm.setNewPin() } }
                if (vm.unlocked) Chip("Lock again now") { vm.lockAgain() }
                Chip("Turn the lock off") { vm.removePin() }
            }
        }
    }
    if (vm.pinSet) {
        ToggleRow(
            "Lock adult categories automatically",
            "Locks any category whose name suggests adult content (for example Adult, XXX or 18+), including new ones your provider adds later.",
            on = vm.lockAdult,
        ) { vm.turnLockAdult(it) }

        SettingBlock(
            "Locked categories",
            "OK locks or unlocks a category. You'll be asked for the PIN first if the lock is closed.",
        ) {
            val cats = vm.allCategories
            androidx.compose.foundation.lazy.LazyColumn(Modifier.height(320.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(cats.size) { i ->
                    val c = cats[i]
                    val locked = vm.categoryLockedSetting(c.id)
                    FocusTile(onClick = { vm.askPin("Enter your PIN to change locks") { vm.toggleCategoryLock(c.id) } }, selected = locked, corner = 8.dp, focusedScale = 1.01f, modifier = Modifier.fillMaxWidth()) {
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
    ChoiceRow(
        "Text size",
        "Makes all the writing in ShedTV bigger, for easier reading from the sofa.",
        "No effect on speed. At the larger sizes some long names are shortened with \"…\" to fit.",
        options = listOf(1f to "Normal", 1.15f to "Large", 1.3f to "Extra large"),
        current = when { vm.textScale < 1.05f -> 1f; vm.textScale <= 1.2f -> 1.15f; else -> 1.3f },
    ) { vm.chooseTextScale(it) }

    ToggleRow(
        "High contrast",
        "Pure black backgrounds, brighter text and stronger outlines, which helps with low vision or a bright room.",
        "No effect on speed.",
        on = vm.highContrast,
    ) { vm.turnHighContrast(it) }

    ChoiceRow(
        "Highlight colour",
        "The colour used for the highlight, buttons and progress bars. Yellow and blue are easiest to tell apart with colour blindness. Press OK to try the next colour.",
        "No effect on speed.",
        options = Palette.accents.map { (name, argb) -> argb to name }, current = vm.accent,
    ) { vm.chooseAccent(it) }

    ChoiceRow(
        "TV guide rows",
        "Compact fits more channels on screen at once; large makes each row taller and easier to read.",
        "No effect on speed.",
        options = listOf("compact" to "Compact", "normal" to "Normal", "large" to "Large"), current = vm.guideRows,
    ) { vm.chooseGuideRows(it) }

    ChoiceRow(
        "Clock",
        "A small clock in the top-right corner of the screen. Choose whether it also shows while you're watching.",
        "No effect on speed.",
        options = listOf("off" to "Off", "menus" to "Menus only", "always" to "Everywhere"), current = vm.clockMode,
    ) { vm.chooseClock(it) }

    ToggleRow(
        "Animations",
        "Gentle fades and slides when moving between screens.",
        "Turning them off makes screen changes instant, which can feel snappier on a Fire Stick or older box.",
        tag = if (vm.animations) "Slower on Fire Stick" else null, on = vm.animations,
    ) { vm.setAnimationsEnabled(it) }
}

@Composable
private fun AboutSettings(vm: AppViewModel) {
    SettingRow(
        "App update",
        (vm.updater.status ?: "You have ShedTV ${vm.updater.versionName} (build ${vm.updater.currentBuild}).") +
            " ShedTV checks for new versions when it opens; OK checks now. Updates keep all your settings, subscriptions and favourites.",
        if (!vm.updater.canInstall) "The first time, your device needs to allow ShedTV to install apps. Install opens that switch for you." else null,
        value = if (vm.updater.busy) "Checking…" else "Build ${vm.updater.currentBuild}",
    ) { vm.updater.check(silent = false) }
    if (vm.updater.available) {
        SettingRow(
            "Install build ${vm.updater.latestBuild}",
            "A newer ShedTV is ready. Installing keeps all your settings, subscriptions and favourites.",
            value = "Install",
        ) { vm.updater.install() }
    }
    SettingRow(
        "About ShedTV",
        "ShedTV is a free player. It doesn't provide any channels or content: you sign in with a TV service you're entitled to use. Install guides and news: shedtv.app",
        "Fonts: Barlow Condensed and Inter, used under the SIL Open Font License 1.1 (full text included in the app).",
        value = "shedtv.app",
    ) { }
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
