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

/** Add or edit a subscription. Also the first-run sign-in screen. */
@Composable
fun LoginScreen(
    initial: com.matchday.tv.data.Subscription?,
    error: String?,
    title: String,
    onCancel: (() -> Unit)?,
    onSubmit: (String, Credentials) -> Unit,
    onTransfer: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var server by rememberSaveable { mutableStateOf(initial?.server.orEmpty()) }
    var username by rememberSaveable { mutableStateOf(initial?.username.orEmpty()) }
    var password by rememberSaveable { mutableStateOf(initial?.password.orEmpty()) }
    var playlist by rememberSaveable { mutableStateOf(initial?.playlist ?: false) }
    var epgUrl by rememberSaveable { mutableStateOf(initial?.epgUrl.orEmpty()) }
    if (onCancel != null) androidx.activity.compose.BackHandler { onCancel() }
    val focus = LocalFocusManager.current
    val first = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }

    fun submit() {
        if (playlist && server.isNotBlank()) {
            onSubmit(name.trim(), Credentials(server.trim(), "", "", playlist = true, epgUrl = epgUrl.trim()))
        } else if (!playlist && server.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
            onSubmit(name.trim(), Credentials(server.trim(), username.trim(), password))
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
            ShedMark(84.dp)
            ShedTvWordmark(40)
            Text(title, color = Palette.Accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(
                if (playlist) "Enter the address of an M3U playlist." else "Enter your provider's Xtream Codes details.",
                color = Palette.TextSoft,
                fontSize = 18.sp,
            )
            Text(
                "Your details are stored only on this device.",
                color = Palette.TextMuted,
                fontSize = 14.sp,
            )
        }

        Column(Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Name (e.g. Main sub, Backup sub)", name, { name = it }, Modifier.focusRequester(first), KeyboardType.Text) {
                focus.moveFocus(FocusDirection.Down)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { playlist = false }) {
                    Text((if (!playlist) "● " else "") + "Xtream login", fontSize = 15.sp, fontWeight = if (!playlist) FontWeight.Bold else FontWeight.Normal)
                }
                Button(onClick = { playlist = true }) {
                    Text((if (playlist) "● " else "") + "M3U playlist", fontSize = 15.sp, fontWeight = if (playlist) FontWeight.Bold else FontWeight.Normal)
                }
            }
            if (playlist) {
                Field("Playlist address (http://… .m3u)", server, { server = it }, Modifier, KeyboardType.Uri) {
                    focus.moveFocus(FocusDirection.Down)
                }
                Field("TV guide address (optional, XMLTV)", epgUrl, { epgUrl = it }, Modifier, KeyboardType.Uri, imeAction = ImeAction.Done) {
                    submit()
                }
                Button(onClick = { name = name.ifBlank { "ShedTV demo" }; server = "https://shedtv.app/demo.m3u"; epgUrl = "" }) {
                    Text("Use the ShedTV demo playlist", fontSize = 15.sp)
                }
            } else {
                Field("Server address", server, { server = it }, Modifier, KeyboardType.Uri) {
                    focus.moveFocus(FocusDirection.Down)
                }
                Field("Username", username, { username = it }, Modifier, KeyboardType.Text) {
                    focus.moveFocus(FocusDirection.Down)
                }
                Field("Password", password, { password = it }, Modifier, KeyboardType.Password, isPassword = true, imeAction = ImeAction.Done) {
                    submit()
                }
            }
            if (error != null) {
                Text(error, color = Palette.Live, fontSize = 15.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { submit() }) {
                    Text("Save and connect", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
                }
                if (onCancel != null) {
                    Button(onClick = onCancel) {
                        Text("Cancel", fontSize = 18.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
            if (onTransfer != null) {
                Button(onClick = onTransfer) {
                    Text("📱  Set up from my phone or another ShedTV", fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
                }
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
            ShedMark(72.dp)
            ShedTvWordmark(32)
            Text(message, color = Palette.TextMuted, fontSize = 18.sp)
        }
    }
}

@Composable
fun ShedTvWordmark(sizeSp: Int) {
    Row {
        Text("Shed", color = Palette.Text, fontSize = sizeSp.sp, fontWeight = FontWeight.ExtraBold)
        Text("TV", color = Palette.Accent, fontSize = sizeSp.sp, fontWeight = FontWeight.ExtraBold)
    }
}
