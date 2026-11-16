@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.matchday.tv.data.Channel
import com.matchday.tv.data.nowAndNext
import kotlinx.coroutines.delay

@Composable
fun LiveTvScreen(vm: AppViewModel) {
    val channels = vm.visibleChannels
    var focused by remember { mutableStateOf<Channel?>(null) }
    val restoreFocus = remember { FocusRequester() }
    val firstCategory = remember { FocusRequester() }
    val channelListState = rememberLazyListState()

    // Return focus to the last channel watched, or start on the category list.
    LaunchedEffect(Unit) {
        vm.ensureCategoryHasChannels()
        val idx = channels.indexOfFirst { it.streamId == vm.lastFocusedChannelId }
        if (idx >= 0) {
            channelListState.scrollToItem((idx - 3).coerceAtLeast(0))
            delay(80)
            if (runCatching { restoreFocus.requestFocus() }.isSuccess) return@LaunchedEffect
        }
        delay(80)
        runCatching { firstCategory.requestFocus() }
    }

    // Fetch now/next for the focused channel once focus settles.
    LaunchedEffect(focused?.streamId) {
        val ch = focused ?: return@LaunchedEffect
        delay(250)
        vm.loadEpg(ch)
    }

    androidx.activity.compose.BackHandler { vm.navigate(com.matchday.tv.Screen.Sport) }
    Row(Modifier.fillMaxSize().background(Palette.Background)) {
    NavRail(vm, com.matchday.tv.Screen.Live)
    Row(
        Modifier
            .fillMaxSize()
            .padding(start = 20.dp, top = 28.dp, end = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // Categories
        Column(Modifier.width(200.dp).fillMaxHeight()) {
            Text("LIVE TV", color = Palette.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp, letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 6.dp, top = 2.dp))
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
            ) {
                itemsIndexed(vm.categoryList, key = { _, c -> c.id }) { index, cat ->
                    val selected = cat.id == vm.selectedCategoryId
                    FocusTile(
                        onClick = { vm.selectCategory(cat.id) },
                        selected = selected,
                        corner = 10.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstCategory) else Modifier)
                            .onFocusChanged { if (it.isFocused) vm.selectCategory(cat.id) },
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                cat.name,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                vm.channelCount(cat.id).toString(),
                                fontSize = 12.sp,
                                color = LocalContentColor.current.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
            Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Format: ${vm.streamFormat.uppercase()}") { vm.toggleStreamFormat() }
            }
        }

        // Channels
        Column(Modifier.width(320.dp).fillMaxHeight()) {
            Text(
                vm.categoryList.firstOrNull { it.id == vm.selectedCategoryId }?.name.orEmpty(),
                color = Palette.TextMuted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 16.dp),
            )
            if (channels.isEmpty()) {
                Text(
                    if (vm.selectedCategoryId == com.matchday.tv.CATEGORY_FAVOURITES)
                        "No favourites yet.\nHold OK on any channel to add it."
                    else "No channels in this category.",
                    color = Palette.TextMuted, fontSize = 15.sp, modifier = Modifier.padding(6.dp),
                )
            }
            LazyColumn(
                state = channelListState,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 6.dp),
            ) {
                itemsIndexed(channels, key = { _, c -> c.streamId }) { _, ch ->
                    ChannelRow(
                        channel = ch,
                        favourite = vm.isFavourite(ch),
                        modifier = Modifier
                            .then(if (ch.streamId == vm.lastFocusedChannelId) Modifier.focusRequester(restoreFocus) else Modifier)
                            .onFocusChanged {
                                if (it.isFocused) {
                                    focused = ch
                                    vm.lastFocusedChannelId = ch.streamId
                                }
                            },
                        onClick = { vm.play(ch) },
                        onLongClick = { vm.toggleFavourite(ch) },
                    )
                }
            }
        }

        // Details
        ChannelDetails(
            channel = focused,
            vm = vm,
            modifier = Modifier.weight(1f).fillMaxHeight().padding(top = 6.dp, bottom = 28.dp),
        )
    }
    }
}

@Composable
private fun SmallButton(label: String, onClick: () -> Unit) {
    FocusTile(onClick = onClick, corner = 8.dp, focusedScale = 1.05f) {
        Text(
            label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(Color(0x14FFFFFF), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    favourite: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    FocusTile(onClick = onClick, onLongClick = onLongClick, modifier = modifier.fillMaxWidth(), corner = 10.dp) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                channel.num.toString(),
                fontSize = 13.sp,
                color = LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier.width(34.dp),
            )
            ChannelLogo(channel, 36)
            Text(
                channel.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (favourite) Text("★", fontSize = 15.sp, color = Palette.Accent)
        }
    }
}

@Composable
fun ChannelLogo(channel: Channel, sizeDp: Int) {
    Box(
        Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape((sizeDp / 4).dp))
            .background(Palette.CardRaised),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            channel.name.filter { it.isLetterOrDigit() }.take(2).uppercase(),
            fontSize = (sizeDp / 3).sp, fontWeight = FontWeight.Bold, color = Palette.TextMuted,
        )
        if (channel.logo != null) {
            AsyncImage(
                model = channel.logo,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().background(Palette.CardRaised).padding(3.dp),
            )
        }
    }
}

@Composable
private fun ChannelDetails(channel: Channel?, vm: AppViewModel, modifier: Modifier) {
    val now = rememberNow()
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Card)
            .padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (channel == null) {
            Text("Pick a channel", color = Palette.TextMuted, fontSize = 18.sp)
            return@Column
        }
        val (current, next) = vm.programmesFor(channel).nowAndNext(now)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ChannelLogo(channel, 56)
            Column {
                Text("${channel.num}  ·  ${channel.name}", color = Palette.TextSoft, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (channel.hasArchive) Text("Catch-up available", color = Palette.Accent, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "NOW", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.background(Palette.Live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text("${formatTime(current.start)} – ${formatTime(current.end)}", color = Palette.TextMuted, fontSize = 14.sp)
            }
            Text(current.title, color = Palette.Text, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            ProgressBar(current.progress(now), Modifier.fillMaxWidth(0.7f), height = 4.dp)
            if (current.description.isNotBlank()) {
                Text(current.description, color = Palette.TextSoft, fontSize = 14.sp, lineHeight = 20.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Text("No guide information", color = Palette.TextMuted, fontSize = 18.sp)
        }
        if (next != null) {
            Spacer(Modifier.height(4.dp))
            Text("NEXT  ${formatTime(next.start)}", color = Palette.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Text(next.title, color = Palette.TextSoft, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.weight(1f))
        Text("OK to watch  ·  Hold OK to add or remove a favourite", color = Palette.TextMuted, fontSize = 12.sp)
    }
}
