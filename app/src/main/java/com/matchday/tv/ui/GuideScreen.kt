@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.matchday.tv.AppViewModel
import com.matchday.tv.Screen
import com.matchday.tv.data.Channel
import com.matchday.tv.data.EpgEntry
import kotlinx.coroutines.launch

private const val MIN = 60_000L
private const val HALF_HOUR = 30 * MIN
private const val WINDOW = 120 * MIN
private val ROW_HEIGHT = 42.dp
private val CHANNEL_COL = 200.dp

private fun floorHalfHour(t: Long) = t - (t % HALF_HOUR)

/** A block in a guide row; [filler] blocks stand in where there is no listing. */
private data class Block(val start: Long, val end: Long, val title: String, val entry: EpgEntry?, val filler: Boolean = false)

private fun blocksFor(programmes: List<EpgEntry>, from: Long, to: Long): List<Block> {
    val out = ArrayList<Block>()
    var cursor = from
    for (p in programmes) {
        if (p.end <= from) continue
        if (p.start >= to) break
        if (p.start > cursor) out += Block(cursor, p.start, "No information", null, filler = true)
        out += Block(p.start, p.end, p.title, p)
        cursor = maxOf(cursor, p.end)
    }
    if (cursor < to) out += Block(cursor, to, "No information", null, filler = true)
    return out
}

@Composable
fun GuideScreen(vm: AppViewModel) {
    val now = rememberNow()
    val channels = vm.visibleChannels
    val earliest = floorHalfHour(now) - 2 * 60 * MIN
    val latest = floorHalfHour(now) + 34 * 60 * MIN

    var windowStart by remember { mutableLongStateOf(floorHalfHour(System.currentTimeMillis())) }
    var anchor by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var row by remember(vm.selectedCategoryId) {
        mutableIntStateOf(channels.indexOfFirst { it.streamId == vm.lastFocusedChannelId }.coerceAtLeast(0))
    }
    var gridFocused by remember { mutableIntStateOf(0) }
    // Categories slide away while you're in the grid; Left at the start of "now" brings them back.
    var catsOpen by remember { mutableStateOf(false) }
    val catFocus = remember { FocusRequester() }
    LaunchedEffect(catsOpen) {
        if (catsOpen) {
            kotlinx.coroutines.delay(60)
            runCatching { catFocus.requestFocus() }
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val gridFocus = remember { FocusRequester() }

    val windowEnd = windowStart + WINDOW
    // Blocks span the whole loaded range so navigation can move beyond the visible window.
    fun rowBlocks(ch: Channel) = blocksFor(vm.programmesFor(ch), earliest, latest + WINDOW)
    fun selectedIn(blocks: List<Block>) = blocks.firstOrNull { anchor >= it.start && anchor < it.end } ?: blocks.lastOrNull()

    val selectedChannel = channels.getOrNull(row)
    val selectedBlock = selectedChannel?.let { selectedIn(rowBlocks(it)) }

    LaunchedEffect(Unit) {
        vm.ensureCategoryHasChannels()
        if (row > 3) listState.scrollToItem(row - 2)
        runCatching { gridFocus.requestFocus() }
    }
    LaunchedEffect(row, channels.size) {
        val info = listState.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull()?.index ?: 0
        val visible = (info.visibleItemsInfo.size - 1).coerceAtLeast(1)
        if (row < first) listState.animateScrollToItem(row)
        else if (row >= first + visible) listState.animateScrollToItem((row - visible + 1).coerceAtLeast(0))
    }
    // Fetch the short EPG for channels the full guide doesn't cover, as they come into view.
    LaunchedEffect(row, vm.guide) {
        channels.subList((row - 6).coerceAtLeast(0), (row + 10).coerceAtMost(channels.size)).forEach { vm.loadEpg(it) }
    }

    BackHandler { vm.navigate(Screen.Sport) }
    PreviewFollower(vm, selectedChannel)

    Row(Modifier.fillMaxSize().background(Palette.Background)) {
        NavRail(vm, Screen.Guide)
        // Categories: moving onto one shows it in the grid; OK or Right goes into the grid.
        androidx.compose.animation.AnimatedVisibility(
            visible = catsOpen,
            enter = androidx.compose.animation.expandHorizontally() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkHorizontally() + androidx.compose.animation.fadeOut(),
        ) {
        Column(Modifier.width(170.dp).fillMaxHeight().padding(start = 8.dp, top = 22.dp)) {
            Text("GUIDE", color = Palette.Text, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily, fontSize = 22.sp, letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
            val cats = vm.categoryList.filter { it.id == vm.selectedCategoryId || vm.channelCount(it.id) > 0 }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxHeight(),
            ) {
                itemsIndexed(cats, key = { _, c -> c.id }) { _, c ->
                    val isSel = c.id == vm.selectedCategoryId
                    FocusTile(
                        onClick = { vm.selectCategory(c.id); runCatching { gridFocus.requestFocus() } },
                        selected = isSel, corner = 8.dp, focusedScale = 1.03f,
                        modifier = Modifier.fillMaxWidth()
                            .then(if (isSel) Modifier.focusRequester(catFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused) vm.selectCategory(c.id) }
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT) {
                                    runCatching { gridFocus.requestFocus() }; true
                                } else false
                            },
                    ) {
                        Text(
                            categoryLabel(c.name), fontSize = 13.sp, fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
        }
        Column(
            Modifier.fillMaxSize().padding(start = 16.dp, end = 24.dp, top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GuideHeader(vm, selectedChannel, selectedBlock, now)

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val timelineWidth = maxWidth - CHANNEL_COL
                val dpPerMs = timelineWidth.value / WINDOW
                fun x(t: Long): Dp = ((t - windowStart).coerceIn(0, WINDOW) * dpPerMs).dp

                Column(
                    Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { e ->
                            if (e.type != KeyEventType.KeyDown || channels.isEmpty()) return@onPreviewKeyEvent false
                            val ch = channels.getOrNull(row) ?: return@onPreviewKeyEvent false
                            val blocks = rowBlocks(ch)
                            val cur = selectedIn(blocks) ?: return@onPreviewKeyEvent false
                            when (e.key.nativeKeyCode) {
                                AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    val next = blocks.firstOrNull { it.start >= cur.end }
                                    if (cur.end > windowEnd && windowStart + HALF_HOUR <= latest) {
                                        // Long programme running past the edge: scroll first.
                                        windowStart += HALF_HOUR
                                        anchor = maxOf(anchor, windowStart)
                                    } else if (next != null) {
                                        anchor = next.start
                                        while (anchor >= windowStart + WINDOW - HALF_HOUR / 2 && windowStart + HALF_HOUR <= latest) {
                                            windowStart += HALF_HOUR
                                        }
                                    }
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                                    val prev = blocks.lastOrNull { it.end <= cur.start }
                                    when {
                                        // At the left edge of the present: step out to the category list.
                                        cur.start <= windowStart && windowStart <= floorHalfHour(now) -> { catsOpen = true; true }
                                        cur.start < windowStart && windowStart > earliest -> {
                                            windowStart -= HALF_HOUR
                                            anchor = maxOf(cur.start, windowStart)
                                            true
                                        }
                                        prev != null && prev.end > earliest -> {
                                            anchor = maxOf(prev.start, earliest)
                                            if (anchor < windowStart) windowStart = maxOf(earliest, floorHalfHour(anchor))
                                            true
                                        }
                                        else -> false // let focus move out to the menu
                                    }
                                }
                                // Up from the top row moves to the category switcher.
                                AndroidKeyEvent.KEYCODE_DPAD_UP -> { if (row > 0) row--; true }
                                AndroidKeyEvent.KEYCODE_DPAD_DOWN -> { if (row < channels.lastIndex) row++; true }
                                AndroidKeyEvent.KEYCODE_CHANNEL_UP, AndroidKeyEvent.KEYCODE_PAGE_UP -> { row = (row - 8).coerceAtLeast(0); true }
                                AndroidKeyEvent.KEYCODE_CHANNEL_DOWN, AndroidKeyEvent.KEYCODE_PAGE_DOWN -> { row = (row + 8).coerceAtMost(channels.lastIndex); true }
                                AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                    windowStart = (windowStart + WINDOW).coerceAtMost(latest)
                                    anchor = windowStart
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
                                    windowStart = (windowStart - WINDOW).coerceAtLeast(earliest)
                                    anchor = windowStart
                                    true
                                }
                                AndroidKeyEvent.KEYCODE_MENU -> { vm.cycleCategory(); row = 0; true }
                                AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER -> {
                                    vm.lastFocusedChannelId = ch.streamId
                                    vm.play(ch, from = Screen.Guide)
                                    true
                                }
                                else -> false
                            }
                        }
                        .focusRequester(gridFocus)
                        .onFocusChanged {
                            gridFocused = if (it.isFocused) 1 else 0
                            if (it.isFocused) catsOpen = false
                        }
                        .focusable(),
                ) {
                    // Time header
                    Box(Modifier.fillMaxWidth().height(24.dp)) {
                        if (now in windowStart until windowEnd) {
                            // Marker for "now" above the grid; the line itself runs behind the programmes.
                            Box(
                                Modifier.align(Alignment.BottomStart).offset(x = CHANNEL_COL + x(now) - 5.dp)
                                    .size(10.dp).background(Palette.Accent, androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                        Text(
                            dayLabel(windowStart, now), color = Palette.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.CenterStart),
                        )
                        for (i in 0 until 4) {
                            val t = windowStart + i * HALF_HOUR
                            Text(
                                formatTime(t), color = Palette.TextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.align(Alignment.CenterStart).offset(x = CHANNEL_COL + x(t) + 6.dp),
                            )
                        }
                    }

                    Box(Modifier.fillMaxSize()) {
                        if (channels.isEmpty()) {
                            Text("No channels in this category. Press ◀ to pick another category.", color = Palette.TextMuted, fontSize = 15.sp)
                        }
                        // "Now" line, drawn behind the programmes so it never cuts through their titles
                        if (now in windowStart until windowEnd) {
                            Box(
                                Modifier
                                    .offset(x = CHANNEL_COL + x(now) - 1.dp)
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(Palette.Accent)
                            )
                        }
                        LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxSize()) {
                            itemsIndexed(channels, key = { _, c -> c.streamId }) { index, ch ->
                                val shortEpg = vm.epg[ch.uid]
                                val visible = remember(ch.streamId, vm.guide, shortEpg, windowStart, earliest) {
                                    rowBlocks(ch).filter { it.end > windowStart && it.start < windowEnd }
                                }
                                GuideRow(
                                    channel = ch,
                                    blocks = visible,
                                    selected = if (index == row && gridFocused == 1) selectedBlock else null,
                                    rowSelected = index == row,
                                    now = now,
                                    x = ::x,
                                    windowStart = windowStart,
                                    windowEnd = windowEnd,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideHeader(vm: AppViewModel, channel: Channel?, block: Block?, now: Long) {
    Row(Modifier.fillMaxWidth().height(if (vm.previewEnabled) 104.dp else 84.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (vm.previewEnabled) {
            LivePreview(vm, Modifier.fillMaxHeight().aspectRatio(16f / 9f))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (channel != null) {
                Text(
                    "${channel.num}  ·  ${channel.name}" + (block?.let { "  ·  ${formatTime(it.start)} – ${formatTime(it.end)}" } ?: ""),
                    color = Palette.TextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (block?.entry?.isOn(now) == true) {
                        Text(
                            "LIVE", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                            modifier = Modifier.background(Palette.Live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        block?.title ?: channel.name, color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontFamily = DisplayFamily,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                val desc = block?.entry?.description.orEmpty()
                if (desc.isNotBlank()) {
                    Text(desc, color = Palette.TextSoft, fontSize = 13.sp, lineHeight = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(formatTime(now), color = Palette.Text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, fontFamily = DisplayFamily)
            val cat = vm.categoryList.firstOrNull { it.id == vm.selectedCategoryId }?.name.orEmpty()
            Text(categoryLabel(cat), color = Palette.Accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            vm.guideStatus?.let { Text(it, color = Palette.TextMuted, fontSize = 12.sp, maxLines = 2) }
            Text("◀ at now: categories  ·  ◀◀ ▶▶ 2 hrs", color = Palette.TextMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    blocks: List<Block>,
    selected: Block?,
    rowSelected: Boolean,
    now: Long,
    x: (Long) -> Dp,
    windowStart: Long,
    windowEnd: Long,
) {
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
        Row(
            Modifier
                .width(CHANNEL_COL)
                .fillMaxHeight()
                .padding(end = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (rowSelected) Palette.CardRaised else Color.Transparent)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(channel.num.toString(), color = Palette.TextMuted, fontSize = 11.sp, modifier = Modifier.width(26.dp))
            ChannelLogo(channel, 28)
            Text(
                channel.name, color = if (rowSelected) Palette.Accent else Palette.Text, fontSize = 12.sp, lineHeight = 13.sp,
                fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            blocks.forEach { b ->
                val left = x(maxOf(b.start, windowStart))
                val width = (x(minOf(b.end, windowEnd)) - left - 3.dp).coerceAtLeast(2.dp)
                val isSelected = selected != null && b.start == selected.start && b.end == selected.end
                val past = b.end <= now
                val onNow = b.start <= now && b.end > now
                val bg = when {
                    isSelected -> Palette.Text
                    b.filler -> Color(0xFF0B0F13)
                    past -> Color(0xFF0C1015)
                    onNow -> Palette.CardRaised
                    else -> Palette.Card
                }
                Column(
                    Modifier
                        .offset(x = left)
                        .width(width)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(bg)
                        .then(if (isSelected) Modifier.border(3.dp, Palette.Accent, RoundedCornerShape(9.dp)) else Modifier)
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val fg = when {
                        isSelected -> Palette.Background
                        b.filler || past -> Palette.TextMuted
                        else -> Palette.Text
                    }
                    Text(b.title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!b.filler && width > 90.dp) {
                        Text(
                            "${formatTime(b.start)} – ${formatTime(b.end)}",
                            color = if (isSelected) Color(0xFF3A4654) else Palette.TextMuted, fontSize = 11.sp, maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

private fun dayLabel(t: Long, now: Long): String {
    val day = 24 * 60 * MIN
    val tz = java.util.TimeZone.getDefault()
    val d1 = (t + tz.getOffset(t)) / day
    val d0 = (now + tz.getOffset(now)) / day
    return when (d1 - d0) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> java.text.SimpleDateFormat("EEE d MMM", java.util.Locale.UK).format(java.util.Date(t))
    }
}
