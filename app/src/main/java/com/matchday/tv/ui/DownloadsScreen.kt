@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.matchday.tv.AppViewModel
import com.matchday.tv.Screen
import com.matchday.tv.download.DownloadItem
import com.matchday.tv.download.DownloadManager
import kotlinx.coroutines.delay

@Composable
fun DownloadsScreen(vm: AppViewModel) {
    val first = remember { FocusRequester() }
    var confirm by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { delay(120); runCatching { first.requestFocus() } }
    if (!vm.offline) BackHandler { vm.goHome() }
    // Episodes grouped under their series, newest first.
    val list = DownloadManager.items.sortedWith(
        compareByDescending<DownloadItem> { d -> DownloadManager.items.filter { it.parentId == d.parentId && d.parentId != null }.maxOfOrNull { it.added } ?: d.added }
            .thenBy { it.parentId ?: it.id }.thenBy { it.season }.thenBy { it.episode },
    )

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        if (!vm.offline) NavRail(vm, Screen.Downloads)
        Column(Modifier.fillMaxSize().padding(start = 28.dp, end = 32.dp, top = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (vm.offline) ShedMark(34.dp)
                Text("DOWNLOADS", color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, letterSpacing = 1.sp)
            }
            val sub = if (vm.offline) "You're offline, so only downloads are available." else
                vm.downloadLocationLabel() + (if (vm.downloadWifiOnly) "  ·  Wi-Fi only" else "") + "  ·  OK to play or pause  ·  hold OK to delete"
            Text(sub, color = if (vm.offline) Palette.Accent else Palette.TextMuted, fontSize = 13.sp)
            DownloadManager.waiting?.let { Text(it, color = Palette.Live, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.padding(top = 8.dp))
            if (vm.offline) {
                FocusTile(onClick = { vm.goOnline() }, corner = 10.dp, focusedScale = 1.04f, modifier = Modifier.padding(bottom = 8.dp)
                    .then(if (list.none { it.status == "done" }) Modifier.focusRequester(first) else Modifier)) {
                    Text("⟳  Back online? Connect", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.background(Color(0x1AFFFFFF), RoundedCornerShape(10.dp)).padding(horizontal = 18.dp, vertical = 9.dp))
                }
            }
            if (list.isEmpty()) {
                Text("Nothing downloaded yet. Open a film or series and choose Download.", color = Palette.TextMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 24.dp))
            }
            val focusIndex = if (vm.offline) list.indexOfFirst { it.status == "done" } else 0
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 40.dp, start = 4.dp, end = 4.dp)) {
                itemsIndexed(list, key = { _, d -> d.id }) { i, d ->
                    if (vm.offline && d.status != "done") return@itemsIndexed
                    FocusTile(
                        onClick = {
                            confirm = null
                            when (d.status) {
                                "done" -> vm.playDownload(d)
                                "paused", "failed" -> DownloadManager.resume(d.id)
                                else -> DownloadManager.pause(d.id)
                            }
                        },
                        onLongClick = { if (confirm == d.id) { DownloadManager.delete(d.id); confirm = null } else confirm = d.id },
                        corner = 12.dp, focusedScale = 1.02f,
                        modifier = Modifier.fillMaxWidth().then(if (i == focusIndex) Modifier.focusRequester(first) else Modifier),
                    ) {
                        Row(Modifier.fillMaxWidth().background(Palette.Card.copy(alpha = 0.5f)).padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(Modifier.width(54.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp)).background(Palette.CardRaised)) {
                                if (d.image != null) AsyncImage(model = d.image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(d.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (d.subtitle.isNotBlank()) Text(d.subtitle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current.copy(alpha = 0.75f))
                                val size = if (d.totalBytes > 0) DownloadManager.gb(d.doneBytes) + " of " + DownloadManager.gb(d.totalBytes) else DownloadManager.gb(d.doneBytes)
                                val line = when {
                                    confirm == d.id -> "Hold OK again to delete"
                                    d.status == "done" -> DownloadManager.gb(d.totalBytes.coerceAtLeast(d.doneBytes)) +
                                        (if (d.duration > 0 && d.position > 30_000) "  ·  watched ${(d.position * 100 / d.duration)}%" else "")
                                    d.message.isNotBlank() -> "$size  ·  ${d.message}"
                                    else -> size
                                }
                                Text(line, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (confirm == d.id || d.status == "failed") Palette.Live else LocalContentColor.current.copy(alpha = 0.7f))
                                if (d.status != "done") ProgressBar(d.progress, Modifier.fillMaxWidth().padding(top = 4.dp), height = 4.dp, track = Color(0x33FFFFFF))
                            }
                            val (label, colour) = when (d.status) {
                                "done" -> "✓ Ready" to Palette.Accent
                                "downloading" -> "${(d.progress * 100).toInt()}%" to Palette.Accent
                                "queued" -> "Queued" to Palette.TextSoft
                                "paused" -> "Paused" to Palette.TextSoft
                                else -> "Failed · OK to retry" to Palette.Live
                            }
                            Text(label, color = colour, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
