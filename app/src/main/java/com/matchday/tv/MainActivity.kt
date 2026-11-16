package com.matchday.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import com.matchday.tv.ui.MovieDetailsScreen
import com.matchday.tv.ui.MoviesScreen
import com.matchday.tv.ui.SeriesDetailsScreen
import com.matchday.tv.ui.SeriesScreen
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
    when (vm.screen) {
        Screen.Login -> LoginScreen(
            initial = vm.savedCredentials,
            error = vm.error,
            onSubmit = { vm.connect(it) },
        )
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
