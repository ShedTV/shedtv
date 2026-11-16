@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.matchday.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.matchday.tv.data.Credentials

@Composable
fun LoginScreen(
    initial: Credentials?,
    error: String?,
    onSubmit: (Credentials) -> Unit,
) {
    var server by rememberSaveable { mutableStateOf(initial?.server.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(initial?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(initial?.password.orEmpty()) }
    val focus = LocalFocusManager.current
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun submit() {
        if (server.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
            onSubmit(Credentials(server.trim(), username.trim(), password))
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .padding(horizontal = 64.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(Palette.Accent, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("M", color = Palette.Background, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            Text("MATCHDAY", color = Palette.Text, fontSize = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(
                "Sign in with your provider's Xtream Codes details.",
                color = Palette.TextSoft,
                fontSize = 18.sp,
            )
            Text(
                "Your details are stored only on this device.",
                color = Palette.TextMuted,
                fontSize = 14.sp,
            )
        }

        Column(Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Field("Server address", server, { server = it }, Modifier.focusRequester(first), KeyboardType.Uri) {
                focus.moveFocus(FocusDirection.Down)
            }
            Field("Username", username, { username = it }, Modifier, KeyboardType.Text) {
                focus.moveFocus(FocusDirection.Down)
            }
            Field("Password", password, { password = it }, Modifier, KeyboardType.Password, isPassword = true, imeAction = ImeAction.Done) {
                submit()
            }
            if (error != null) {
                Text(error, color = Palette.Live, fontSize = 15.sp)
            }
            Spacer(Modifier.height(4.dp))
            Button(onClick = { submit() }) {
                Text("Sign in", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
    keyboardType: KeyboardType,
    isPassword: Boolean = false,
    imeAction: ImeAction = ImeAction.Next,
    onAction: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { androidx.compose.material3.Text(label) },
        singleLine = true,
        modifier = modifier.width(440.dp),
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction, autoCorrectEnabled = false),
        keyboardActions = KeyboardActions(onNext = { onAction() }, onDone = { onAction() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Palette.Text,
            unfocusedTextColor = Palette.Text,
            focusedBorderColor = Palette.Accent,
            unfocusedBorderColor = Palette.TextMuted,
            focusedLabelColor = Palette.Accent,
            unfocusedLabelColor = Palette.TextMuted,
            cursorColor = Palette.Accent,
            focusedContainerColor = Palette.Card,
            unfocusedContainerColor = Palette.Card,
        ),
    )
}

@Composable
fun LoadingScreen(message: String) {
    Box(Modifier.fillMaxSize().background(Palette.Background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("MATCHDAY", color = Palette.Text, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(message, color = Palette.TextMuted, fontSize = 18.sp)
        }
    }
}
