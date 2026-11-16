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
