package com.matchday.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.matchday.tv.ui.Palette
import com.matchday.tv.ui.formatTime
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchday.tv.ui.MovieDetailsScreen
import com.matchday.tv.ui.MoviesScreen
import com.matchday.tv.ui.SeriesDetailsScreen
import com.matchday.tv.ui.SeriesScreen
import com.matchday.tv.ui.SettingsScreen
import com.matchday.tv.ui.VodPlayerScreen
import com.matchday.tv.ui.GuideScreen
import com.matchday.tv.ui.LiveTvScreen
import com.matchday.tv.ui.LoadingScreen
import com.matchday.tv.ui.LoginScreen
import com.matchday.tv.ui.MatchdayTheme
import com.matchday.tv.ui.PlayerScreen

class MainActivity : ComponentActivity() {
    private val appVm: AppViewModel by viewModels()

    override fun onStop() {
        super.onStop()
        // Home button or the TV going to sleep: close the provider connection.
        appVm.live.stop()
    }

    /** Phones and tablets: no status bar or navigation bar over the picture (swipe from an edge to show them briefly). */
    private fun hideSystemBars() {
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        hideSystemBars()
        setContent {
            val vm: AppViewModel = viewModel()
            LaunchedEffect(vm.exitRequested) { if (vm.exitRequested) { vm.exitRequested = false; finish() } }
            // Rebuild everything when contrast or colour changes, so every screen picks it up.
            androidx.compose.runtime.key(vm.highContrast, vm.accent, vm.guideRows) {
                val d = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * vm.textScale),
                ) {
                    MatchdayTheme {
                        App(vm)
                    }
                }
            }
        }
    }
}

@Composable
private fun App(vm: AppViewModel) {
    val alert = vm.watchlist.alert
    val quiet = vm.screen == Screen.Login || vm.screen == Screen.Loading || vm.screen == Screen.EditSubscription
    val alreadyWatching = vm.screen == Screen.Player && alert != null &&
        vm.sourcesFor(alert.channel).any { it.streamId == vm.currentChannel?.streamId }
    val showAlert = alert != null && !quiet && !alreadyWatching
    LaunchedEffect(alert) {
        if (alert != null) {
            delay(30_000)
            vm.watchlist.dismissAlert()
        }
    }
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (showAlert && alert != null && e.type == KeyEventType.KeyDown && e.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_MENU) {
                vm.watchlist.dismissAlert()
                vm.jumpToChannel(alert.channel)
                true
            } else false
        }
    ) {
        Screens(vm)
        val playing = vm.screen == Screen.Player || vm.screen == Screen.VodPlayer || vm.screen == Screen.Catchup || vm.screen == Screen.RecordingPlayer
        if (vm.clockMode == "always" || (vm.clockMode == "menus" && !playing)) {
            com.matchday.tv.ui.ScreenClock(Modifier.align(Alignment.TopEnd).padding(top = 14.dp, end = 22.dp))
        }
        vm.kidsStop?.let { com.matchday.tv.ui.KidsStopOverlay(vm, it) }
        vm.pinRequest?.let { req -> com.matchday.tv.ui.PinPad(req.title, onCancel = { vm.pinRequest = null }, onDone = req.onDone) }
        val flash = vm.flash
        LaunchedEffect(flash) { if (flash != null) { delay(5_000); vm.flash = null } }
        if (flash != null) {
            Text(
                flash, color = Palette.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
                    .background(Color(0xF207090C), RoundedCornerShape(12.dp))
                    .border(1.dp, Palette.Accent, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
        if (showAlert && alert != null) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
                    .background(Color(0xF207090C), RoundedCornerShape(14.dp))
                    .border(2.dp, Palette.Accent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                com.matchday.tv.ui.WatchMark(alert.item, 34.dp)
                Column {
                    Text("STARTING  ·  ${formatTime(alert.entry.start)}", color = Palette.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(alert.entry.title, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${vm.channelLabel(alert.channel)}  ·  press ☰ Menu to watch, or find it on Home", color = Palette.TextSoft, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Screens(vm: AppViewModel) {
    // "Stay at 50Hz": switch once, as the app opens (or the setting is turned on), not when going full screen.
    val activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity
    LaunchedEffect(vm.stay50Hz) {
        if (vm.stay50Hz) com.matchday.tv.player.FrameRateMatcher.match(activity, 50f)
        else if (vm.screen != Screen.Player && vm.screen != Screen.VodPlayer) com.matchday.tv.player.FrameRateMatcher.reset(activity)
    }
    LaunchedEffect(vm.screen, vm.previewEnabled) { vm.onScreenShown(vm.screen) }
    val animate = vm.animations
    AnimatedContent(
        targetState = vm.screen,
        transitionSpec = { screenTransition(initialState, targetState, animate) },
        label = "screens",
    ) { screen -> ScreenFor(vm, screen) }
}

private val instantScreens = setOf(Screen.Player, Screen.VodPlayer, Screen.Loading, Screen.RecordingPlayer, Screen.Catchup)
private val pageScreens = setOf(Screen.MovieDetails, Screen.SeriesDetails, Screen.Watchlist, Screen.Categories, Screen.EditSubscription)

/** Quick fades between sections, a short slide for pages opened from them; video always cuts straight in. */
private fun screenTransition(from: Screen, to: Screen, animate: Boolean): ContentTransform = when {
    !animate || from in instantScreens || to in instantScreens -> EnterTransition.None togetherWith ExitTransition.None
    to in pageScreens -> (slideInHorizontally(tween(220)) { it / 14 } + fadeIn(tween(220))) togetherWith fadeOut(tween(120))
    from in pageScreens -> (slideInHorizontally(tween(200)) { -it / 14 } + fadeIn(tween(200))) togetherWith fadeOut(tween(120))
    else -> fadeIn(tween(200, delayMillis = 40)) togetherWith fadeOut(tween(140))
}

@Composable
private fun ScreenFor(vm: AppViewModel, screen: Screen) {
    when (screen) {
        Screen.Login -> LoginScreen(
            initial = vm.editingSubscription,
            error = vm.error,
            title = "Add your subscription",
            onCancel = null,
            onTransfer = { vm.openReceive() },
            onSubmit = { name, creds -> vm.saveSubscription(name, creds) },
        )
        Screen.EditSubscription -> LoginScreen(
            initial = vm.editingSubscription,
            error = vm.error,
            title = if (vm.editingSubscription == null) "Add a subscription" else "Edit subscription",
            onCancel = if (vm.subscriptions.isEmpty()) null else ({ vm.cancelEdit() }),
            onSubmit = { name, creds -> vm.saveSubscription(name, creds) },
        )
        Screen.Settings -> SettingsScreen(vm)
        Screen.Search -> com.matchday.tv.ui.SearchScreen(vm)
        Screen.Categories -> com.matchday.tv.ui.CategoriesScreen(vm)
        Screen.Recordings -> com.matchday.tv.ui.RecordingsScreen(vm)
        Screen.RecordingPlayer -> com.matchday.tv.ui.RecordingPlayerScreen(vm)
        Screen.Downloads -> com.matchday.tv.ui.DownloadsScreen(vm)
        Screen.Catchup -> com.matchday.tv.ui.CatchupScreen(vm)
        Screen.ReceiveSetup -> com.matchday.tv.ui.ReceiveSetupScreen(vm)
        Screen.SendSetup -> com.matchday.tv.ui.SendSetupScreen(vm)
        Screen.KidsSetup -> com.matchday.tv.ui.KidsSetupScreen(vm)
        Screen.Loading -> LoadingScreen(vm.loadingMessage)
        Screen.Home -> if (vm.kidsMode && vm.kidsSimpleHome) com.matchday.tv.ui.KidsHome(vm) else com.matchday.tv.ui.HomeScreen(vm)
        Screen.Watchlist -> com.matchday.tv.ui.WatchlistScreen(vm)
        Screen.Live -> LiveTvScreen(vm)
        Screen.Guide -> GuideScreen(vm)
        Screen.Movies -> MoviesScreen(vm)
        Screen.Series -> SeriesScreen(vm)
        Screen.MovieDetails -> MovieDetailsScreen(vm)
        Screen.SeriesDetails -> SeriesDetailsScreen(vm)
        Screen.VodPlayer -> VodPlayerScreen(vm)
        Screen.Player -> PlayerScreen(vm)
    }
}
