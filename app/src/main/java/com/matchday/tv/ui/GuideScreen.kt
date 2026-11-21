@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
/** Set from Settings → Display (guide rows). */
private var ROW_HEIGHT = 42.dp
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
    ROW_HEIGHT = when (vm.guideRows) { "compact" -> 34.dp; "large" -> 54.dp; else -> 42.dp }
    val now = rememberNow()
    val channels = vm.visibleChannels
    // Catch-up channels let the guide go back as far as their archive.
    val archiveBack = remember(channels) { channels.filter { it.hasArchive }.maxOfOrNull { vm.archiveMs(it) } ?: 0L }
    val earliest = floorHalfHour(now) - maxOf(2 * 60 * MIN, archiveBack)
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
    // Past listings for catch-up channels: the highlighted one, and those in view once you look back.
    val lookingBack = windowStart < floorHalfHour(now)
    LaunchedEffect(row, lookingBack) {
        channels.getOrNull(row)?.let { vm.loadArchiveEpg(it) }
        if (lookingBack) channels.subList((row - 4).coerceAtLeast(0), (row + 8).coerceAtMost(channels.size)).forEach { vm.loadArchiveEpg(it) }
    }

    // Back: from earlier programmes to now, then to the categories, then out of the guide.
    BackHandler {
        when {
            windowStart < floorHalfHour(System.currentTimeMillis()) -> {
                windowStart = floorHalfHour(System.currentTimeMillis()); anchor = System.currentTimeMillis()
            }
            !catsOpen -> catsOpen = true
            else -> vm.goHome()
        }
    }
    fun lookBack() {
        windowStart = (floorHalfHour(System.currentTimeMillis()) - WINDOW).coerceAtLeast(earliest)
        anchor = windowStart + HALF_HOUR
        runCatching { gridFocus.requestFocus() }
    }
    PreviewFollower(vm, selectedChannel)

    // Options for the highlighted programme: hold OK (any remote) or ☰.
    var menuFor by remember { mutableStateOf<Pair<Channel, Block>?>(null) }
    var okDown by remember { mutableStateOf(false) }
    var swallowOk by remember { mutableStateOf(false) }
    fun closeMenu() { menuFor = null; runCatching { gridFocus.requestFocus() } }
    BackHandler(enabled = menuFor != null) { closeMenu() }
    fun record(ch: Channel, entry: com.matchday.tv.data.EpgEntry?) {
        val t = System.currentTimeMillis()
        when {
            vm.recordingFor(ch) != null -> vm.flash = "Already recording ${vm.channelLabel(ch)}."
            entry != null && entry.isOn(t) -> vm.startRecording(ch, entry.title, entry.end + 15 * 60_000)
            entry != null && entry.start > t -> vm.flash = "Recording future programmes is coming in the next update."
            else -> vm.flash = "Pick a programme that's on now to record it."
        }
    }

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            val code = e.key.nativeKeyCode
            if (swallowOk && (code == AndroidKeyEvent.KEYCODE_DPAD_CENTER || code == AndroidKeyEvent.KEYCODE_ENTER)) {
                if (e.type == KeyEventType.KeyUp) swallowOk = false
                true
            } else false
        },
    ) {
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
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
            FocusTile(onClick = { vm.navigate(Screen.Categories) }, corner = 8.dp, focusedScale = 1.04f, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)) {
                Text("⚙  Manage categories", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.background(Color(0x14FFFFFF), RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
            }
            val cats = vm.categoryList.filter { it.id == vm.selectedCategoryId || vm.channelCount(it.id) > 0 }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                modifier = Modifier.fillMaxHeight().enterAt(catFocus),
            ) {
                itemsIndexed(cats, key = { _, c -> c.id }) { _, c ->
                    val isSel = c.id == vm.selectedCategoryId
                    FocusTile(
                        onClick = { vm.selectCategory(c.id); runCatching { gridFocus.requestFocus() } },
                        onLongClick = { vm.navigate(Screen.Categories) },
                        selected = isSel, corner = 8.dp, focusedScale = 1.03f,
                        modifier = Modifier.fillMaxWidth()
                            .then(if (isSel) Modifier.focusRequester(catFocus) else Modifier)
                            .onFocusChanged { if (it.isFocused) vm.selectCategory(c.id, fromFocus = true) }
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_DPAD_RIGHT) {
                                    runCatching { gridFocus.requestFocus() }; true
                                } else false
                            },
                    ) {
                        Text(
                            (if (vm.categoryLockedSetting(c.id) && vm.pinSet) "🔒 " else "") + categoryLabel(c.name), fontSize = 13.sp, fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
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
                            if (channels.isEmpty()) return@onPreviewKeyEvent false
                            val ch = channels.getOrNull(row) ?: return@onPreviewKeyEvent false
                            val blocks = rowBlocks(ch)
                            val cur = selectedIn(blocks) ?: return@onPreviewKeyEvent false
                            val okKey = e.key.nativeKeyCode.let { it == AndroidKeyEvent.KEYCODE_DPAD_CENTER || it == AndroidKeyEvent.KEYCODE_ENTER }
                            if (okKey && e.type == KeyEventType.KeyDown) {
                                if (e.nativeKeyEvent.repeatCount == 0) okDown = true
                                else if (okDown) { okDown = false; swallowOk = true; menuFor = ch to cur }
                                return@onPreviewKeyEvent true
                            }
                            if (okKey && e.type == KeyEventType.KeyUp) {
                                if (okDown) {
                                    okDown = false
                                    vm.lastFocusedChannelId = ch.streamId
                                    val past = cur.entry
                                    if (past != null && cur.end <= System.currentTimeMillis() && vm.canCatchUp(ch, past)) vm.playCatchup(ch, past, Screen.Guide)
                                    else vm.play(ch, from = Screen.Guide)
                                }
                                return@onPreviewKeyEvent true
                            }
                            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
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
                                    val lookingBack = windowStart < floorHalfHour(now)
                                    when {
                                        // In the present, Left stops at the "now" line: anything that has
                                        // already finished is only reached on purpose (◀◀, or hold OK →
                                        // Earlier programmes), so Left always gets back to the categories.
                                        !lookingBack && (prev == null || prev.end <= now) -> { catsOpen = true; true }
                                        !lookingBack -> { anchor = maxOf(prev!!.start, now); true }
                                        cur.start < windowStart && windowStart > earliest -> {
                                            windowStart -= HALF_HOUR
                                            anchor = maxOf(cur.start, windowStart)
                                            true
                                        }
                                        prev != null && prev.end > earliest -> {
                                            // A long gap with no listings: step through it half an hour at a time.
                                            anchor = if (prev.filler && prev.end - prev.start > 3 * 60 * MIN) prev.end - HALF_HOUR else maxOf(prev.start, earliest)
                                            if (anchor < windowStart) windowStart = maxOf(earliest, floorHalfHour(anchor))
                                            true
                                        }
                                        else -> { catsOpen = true; true } // as far back as it goes
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
                                AndroidKeyEvent.KEYCODE_MENU -> { menuFor = ch to cur; true }
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
                        var dragX by remember { mutableStateOf(0f) }
                        LazyColumn(
                            state = listState, verticalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.fillMaxSize().pointerInput(earliest) {
                                // Swipe sideways to move through time, half an hour per step.
                                detectHorizontalDragGestures(onDragEnd = { dragX = 0f }) { _, dx ->
                                    dragX += dx
                                    val step = 110.dp.toPx()
                                    while (dragX <= -step) { dragX += step; windowStart += HALF_HOUR; anchor = maxOf(anchor, windowStart) }
                                    while (dragX >= step) {
                                        dragX -= step
                                        if (windowStart - HALF_HOUR >= earliest) { windowStart -= HALF_HOUR; anchor = minOf(anchor, windowStart + WINDOW - 1) }
                                    }
                                }
                            },
                        ) {
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
                                    onTapBlock = { b ->
                                        val already = index == row && selectedBlock?.start == b.start && gridFocused == 1
                                        row = index
                                        anchor = maxOf(b.start, windowStart)
                                        runCatching { gridFocus.requestFocus() }
                                        if (already) {
                                            vm.lastFocusedChannelId = ch.streamId
                                            val e = b.entry
                                            if (e != null && b.end <= System.currentTimeMillis() && vm.canCatchUp(ch, e)) vm.playCatchup(ch, e, Screen.Guide)
                                            else vm.play(ch, from = Screen.Guide)
                                        }
                                    },
                                    onHoldBlock = { b -> row = index; anchor = maxOf(b.start, windowStart); menuFor = ch to b },
                                    onTapChannel = { vm.lastFocusedChannelId = ch.streamId; vm.play(ch, from = Screen.Guide) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    androidx.compose.animation.AnimatedVisibility(
        visible = menuFor != null,
        enter = androidx.compose.animation.slideInHorizontally { it } + androidx.compose.animation.fadeIn(),
        exit = androidx.compose.animation.slideOutHorizontally { it } + androidx.compose.animation.fadeOut(),
        modifier = Modifier.align(Alignment.CenterEnd),
    ) {
        val target = menuFor
        if (target != null) {
            val (ch, block) = target
            val entry = block.entry
            val t = System.currentTimeMillis()
            val actions = buildList {
                vm.previewChannel()?.takeIf { it.uid != ch.uid }?.let { pc -> add(MenuAction("▶  Back to ${vm.channelLabel(pc)}") { vm.watchPreview() }) }
                if (entry != null && vm.canCatchUp(ch, entry, t)) {
                    add(MenuAction(if (entry.end > t) "⟲  Watch from the start" else "⟲  Watch") { vm.lastFocusedChannelId = ch.streamId; vm.playCatchup(ch, entry, Screen.Guide) })
                }
                if (vm.recordingEnabled) {
                    val active = vm.recordingFor(ch)
                    when {
                        active != null -> add(MenuAction("■  Stop recording") { com.matchday.tv.record.RecorderManager.stop(active.id) })
                        entry != null && entry.isOn(t) -> add(MenuAction("●  Record (to ${formatTime(entry.end + 15 * 60_000)})") { record(ch, entry) })
                        entry != null && entry.start > t -> add(MenuAction("●  Record (next update)") { record(ch, entry) })
                    }
                }
                if (ch.hasArchive && windowStart >= floorHalfHour(t)) add(MenuAction("⟲  Earlier programmes (catch-up)") { lookBack() })
                if (windowStart < floorHalfHour(t)) add(MenuAction("▶  Back to now") { windowStart = floorHalfHour(System.currentTimeMillis()); anchor = System.currentTimeMillis() })
                add(MenuAction(if (vm.isFavourite(ch)) "★  Remove from favourites" else "☆  Add to favourites") { vm.toggleFavourite(ch) })
                add(MenuAction("☰  Change category") { catsOpen = true })
            }
            OptionsPanel(
                title = entry?.title ?: block.title,
                subtitle = vm.channelLabel(ch) + if (entry != null) "  ·  ${formatTime(entry.start)}–${formatTime(entry.end)}" else "",
                actions = actions,
                onClose = { closeMenu() },
            )
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
            Text("◀ at now: categories  ·  hold OK: options" + if (channel?.hasArchive == true) " & catch-up" else "", color = Palette.TextMuted, fontSize = 11.sp)
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
    onTapBlock: (Block) -> Unit = {},
    onHoldBlock: (Block) -> Unit = {},
    onTapChannel: () -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
        Row(
            Modifier
                .touchTap(onTapChannel)
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
                        .touchTap(onTap = { onTapBlock(b) }, onLongPress = { onHoldBlock(b) })
                        .width(width)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(9.dp))
                        .background(bg)
                        .then(if (isSelected) Modifier.border(3.dp, Palette.Accent, RoundedCornerShape(9.dp)) else Modifier)
                        .padding(horizontal = 10.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    val archiveFrom = if (channel.hasArchive) now - (channel.archiveDays.takeIf { it > 0 } ?: 1).coerceAtMost(14) * 24 * 60 * 60 * 1000L else Long.MAX_VALUE
                    val catchable = past && !b.filler && b.entry != null && b.start > archiveFrom
                    val fg = when {
                        isSelected -> Palette.Background
                        catchable -> Palette.TextSoft
                        b.filler || past -> Palette.TextMuted
                        else -> Palette.Text
                    }
                    Text((if (catchable) "⟲ " else "") + b.title, color = fg, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
