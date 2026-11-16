package com.matchday.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
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
        Screen.Live -> LiveTvScreen(vm)
        Screen.Player -> PlayerScreen(vm)
    }
}
