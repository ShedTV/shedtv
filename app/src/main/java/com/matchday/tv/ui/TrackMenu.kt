@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.matchday.tv.AppViewModel
import kotlinx.coroutines.delay
import java.util.Locale

/** Readable subtitles on a TV: white text on a soft dark box. Hidden in the small preview. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
fun PlayerView.styleSubtitles(show: Boolean = true) {
    val view = subtitleView ?: return
    if (!show) {
        view.visibility = android.view.View.GONE
        return
    }
    view.setApplyEmbeddedStyles(false)
    view.setFractionalTextSize(0.058f)
    view.setStyle(
        CaptionStyleCompat(
            android.graphics.Color.WHITE, android.graphics.Color.argb(170, 0, 0, 0), android.graphics.Color.TRANSPARENT,
            CaptionStyleCompat.EDGE_TYPE_NONE, android.graphics.Color.BLACK, null,
        )
    )
}

private fun trackLabel(f: Format, index: Int): String {
    val lang = f.language?.takeIf { it.isNotBlank() && it != "und" }?.let { Locale.forLanguageTag(it).displayLanguage.ifBlank { it } }
    val base = f.label?.takeIf { it.isNotBlank() } ?: lang ?: "Track ${index + 1}"
    val extra = when (f.sampleMimeType) {
        MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_CEA708 -> " (CC)"
        MimeTypes.APPLICATION_DVBSUBS -> " (broadcast)"
        else -> ""
    }
    val channels = if (f.channelCount >= 6) " · ${f.channelCount - 1}.1" else ""
    return base + (if (lang != null && f.label != null && !f.label!!.contains(lang, true)) " · $lang" else "") + extra + channels
}

data class MenuAction(val label: String, val action: () -> Unit)

/** A short list of actions in the same style as the player's options (used by the guide). */
@Composable
fun OptionsPanel(title: String, subtitle: String, actions: List<MenuAction>, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }
    Column(
        Modifier.fillMaxHeight().width(380.dp)
            .background(Brush.horizontalGradient(listOf(Color(0x0007090C), Color(0xE607090C), Color(0xF507090C))))
            .padding(start = 40.dp, end = 24.dp, top = 28.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("OPTIONS", color = Palette.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
        Text(title, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(subtitle + "  ·  Back to close", color = Palette.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 8.dp))
        actions.forEachIndexed { i, a ->
            MenuItem(a.label, false, if (i == 0) Modifier.focusRequester(first) else Modifier) { onClose(); a.action() }
        }
    }
}

/** Options while watching: subtitles, audio and (for live TV) last channel. */
@Composable
fun TrackMenu(vm: AppViewModel, player: Player, onLastChannel: (() -> Unit)?, recordChannel: com.matchday.tv.data.Channel? = null, onClose: () -> Unit) {
    var tracks by remember { mutableStateOf(player.currentTracks) }
    var nonce by remember { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(t: Tracks) { tracks = t }
        }
        player.addListener(l)
        onDispose { player.removeListener(l) }
    }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(60); runCatching { first.requestFocus() } }

    nonce // re-read selection state after a change
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val textOff = C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes || text.none { it.isSelected }
    var firstUsed = false
    fun firstMod(): Modifier = if (!firstUsed) { firstUsed = true; Modifier.focusRequester(first) } else Modifier

    Column(
        Modifier.fillMaxHeight().width(380.dp)
            .background(Brush.horizontalGradient(listOf(Color(0x0007090C), Color(0xE607090C), Color(0xF507090C))))
            .padding(start = 40.dp, end = 24.dp, top = 28.dp, bottom = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("OPTIONS", color = Palette.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
        Text("Back to close", color = Palette.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 8.dp))

        if (onLastChannel != null) {
            MenuItem("Last channel", false, firstMod()) { onLastChannel(); onClose() }
        }
        if (recordChannel != null && vm.recordingEnabled) {
            val active = vm.recordingFor(recordChannel)
            if (active != null) {
                MenuItem("■  Stop recording", false, Modifier) { com.matchday.tv.record.RecorderManager.stop(active.id); onClose() }
            } else {
                val now = System.currentTimeMillis()
                val prog = vm.programmesFor(recordChannel).firstOrNull { it.isOn(now) }
                if (prog != null) {
                    MenuItem("●  Record this programme (to ${formatTime(prog.end + 15 * 60_000)})", false, Modifier) {
                        vm.startRecording(recordChannel, prog.title, prog.end + 15 * 60_000); onClose()
                    }
                }
                MenuItem("●  Record for 1 hour", false, Modifier) {
                    vm.startRecording(recordChannel, (prog?.title ?: vm.channelLabel(recordChannel)), now + 60 * 60_000); onClose()
                }
            }
        }

        Section("SUBTITLES")
        if (text.isEmpty()) {
            Note("None in this stream")
        } else {
            MenuItem("Off", textOff, firstMod()) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                vm.setSubtitlePref(false, null)
                nonce++
            }
            text.forEach { g ->
                for (i in 0 until g.length) {
                    if (!g.isTrackSupported(i)) continue
                    val f = g.getTrackFormat(i)
                    MenuItem(trackLabel(f, i), !textOff && g.isTrackSelected(i), Modifier) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i))
                            .build()
                        vm.setSubtitlePref(true, f.language)
                        nonce++
                    }
                }
            }
        }

        Section("AUDIO")
        val audioTracks = audio.sumOf { g -> (0 until g.length).count { g.isTrackSupported(it) } }
        if (audioTracks <= 1) {
            Note("Only one audio track")
        } else {
            audio.forEach { g ->
                for (i in 0 until g.length) {
                    if (!g.isTrackSupported(i)) continue
                    MenuItem(trackLabel(g.getTrackFormat(i), i), g.isTrackSelected(i), firstMod()) {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setOverrideForType(TrackSelectionOverride(g.mediaTrackGroup, i)).build()
                        nonce++
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
}

@Composable
private fun Note(text: String) {
    Text(text, color = Palette.TextMuted, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
}

@Composable
private fun MenuItem(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    FocusTile(onClick = onClick, selected = selected, corner = 10.dp, focusedScale = 1.03f, modifier = modifier.fillMaxWidth()) {
        Text(
            (if (selected) "✓  " else "     ") + label, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}
