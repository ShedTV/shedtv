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
import com.matchday.tv.ui.TeamBadge
import com.matchday.tv.ui.formatTime
import kotlinx.coroutines.delay
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchday.tv.ui.MovieDetailsScreen
import com.matchday.tv.ui.MoviesScreen
import com.matchday.tv.ui.SeriesDetailsScreen
import com.matchday.tv.ui.SeriesScreen
import com.matchday.tv.ui.SettingsScreen
import com.matchday.tv.ui.SportHubScreen
import com.matchday.tv.ui.TeamPickerScreen
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MatchdayTheme {
                App()
            }
        }
    }
}

@Composable
private fun App(vm: AppViewModel = viewModel()) {
    val alert = vm.sport.alert
    val quiet = vm.screen == Screen.Login || vm.screen == Screen.Loading || vm.screen == Screen.EditSubscription
    val alreadyWatching = vm.screen == Screen.Player && alert != null &&
        vm.sourcesFor(alert.channel).any { it.streamId == vm.currentChannel?.streamId }
    val showAlert = alert != null && !quiet && !alreadyWatching
    LaunchedEffect(alert) {
        if (alert != null) {
            delay(30_000)
            vm.sport.dismissAlert()
        }
    }
    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (showAlert && alert != null && e.type == KeyEventType.KeyDown && e.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_MENU) {
                vm.sport.dismissAlert()
                vm.jumpToChannel(alert.channel)
                true
            } else false
        }
    ) {
        Screens(vm)
        if (showAlert && alert != null) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 20.dp)
                    .background(Color(0xF207090C), RoundedCornerShape(14.dp))
                    .border(2.dp, Palette.Accent, RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TeamBadge(vm.sport, alert.homeTeam, alert.home, 34.dp)
                TeamBadge(vm.sport, alert.awayTeam, alert.away, 34.dp)
                Column {
                    Text("KICK-OFF  ·  ${formatTime(alert.entry.start)}", color = Palette.Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(alert.title, color = Palette.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text("${vm.channelLabel(alert.channel)}  ·  press ☰ Menu to watch", color = Palette.TextSoft, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun Screens(vm: AppViewModel) {
    LaunchedEffect(vm.screen, vm.previewEnabled) { vm.onScreenShown(vm.screen) }
    val animate = vm.animations
    AnimatedContent(
        targetState = vm.screen,
        transitionSpec = { screenTransition(initialState, targetState, animate) },
        label = "screens",
    ) { screen -> ScreenFor(vm, screen) }
}

private val instantScreens = setOf(Screen.Player, Screen.VodPlayer, Screen.Loading)
private val pageScreens = setOf(Screen.MovieDetails, Screen.SeriesDetails, Screen.Teams, Screen.Categories, Screen.EditSubscription)

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
        Screen.Loading -> LoadingScreen(vm.loadingMessage)
        Screen.Sport -> SportHubScreen(vm)
        Screen.Teams -> TeamPickerScreen(vm)
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
