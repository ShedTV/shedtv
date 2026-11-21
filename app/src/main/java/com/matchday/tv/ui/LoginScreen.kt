@file:OptIn(ExperimentalTvMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.matchday.tv.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
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

    val narrow = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 700
    androidx.compose.foundation.layout.FlowRow(
        Modifier
            .fillMaxSize()
            .background(Palette.Background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (narrow) 20.dp else 64.dp, vertical = if (narrow) 24.dp else 48.dp),
        horizontalArrangement = Arrangement.spacedBy(64.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(if (narrow) Modifier.fillMaxWidth() else Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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

        Column(if (narrow) Modifier.fillMaxWidth() else Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Field("Name (e.g. Main sub, Backup sub)", name, { name = it }, Modifier.focusRequester(first), KeyboardType.Text) {
                focus.moveFocus(FocusDirection.Down)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { playlist = false }, modifier = Modifier.touchTap({ playlist = false })) {
                    Text((if (!playlist) "● " else "") + "Xtream login", fontSize = 15.sp, fontWeight = if (!playlist) FontWeight.Bold else FontWeight.Normal)
                }
                Button(onClick = { playlist = true }, modifier = Modifier.touchTap({ playlist = true })) {
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
                val useDemo = { name = name.ifBlank { "ShedTV demo" }; server = "https://shedtv.app/demo.m3u"; epgUrl = "" }
                Button(onClick = useDemo, modifier = Modifier.touchTap(useDemo)) {
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
                Button(onClick = { submit() }, modifier = Modifier.touchTap({ submit() })) {
                    Text("Save and connect", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 12.dp))
                }
                if (onCancel != null) {
                    Button(onClick = onCancel, modifier = Modifier.touchTap(onCancel)) {
                        Text("Cancel", fontSize = 18.sp, modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
            if (onTransfer != null) {
                Button(onClick = onTransfer, modifier = Modifier.touchTap(onTransfer)) {
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
    // The box itself is a Compose button; OK or a tap opens a small typing window. That window is a
    // plain Android one, so the keyboard always comes up and says what you're typing ("Username"...).
    // (A text box embedded in the screen didn't always get the OK press on Fire TV.)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val latestChange = androidx.compose.runtime.rememberUpdatedState(onChange)
    val latestAction = androidx.compose.runtime.rememberUpdatedState(onAction)
    val latestValue = androidx.compose.runtime.rememberUpdatedState(value)
    var focused by remember { mutableStateOf(false) }
    val open = {
        openTextEntry(ctx, label, latestValue.value, keyboardType, isPassword, imeAction) { text, next ->
            latestChange.value(text)
            if (next) latestAction.value()
        }
    }
    Column(modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = if (focused) Palette.Accent else Palette.TextMuted, fontSize = 13.sp)
        Box(
            Modifier.fillMaxWidth()
                .background(Palette.Card, RoundedCornerShape(10.dp))
                .border(if (focused) 2.dp else 1.dp, if (focused) Palette.Accent else Palette.TextMuted, RoundedCornerShape(10.dp))
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    val c = e.key.nativeKeyCode
                    val ok = c == android.view.KeyEvent.KEYCODE_DPAD_CENTER || c == android.view.KeyEvent.KEYCODE_ENTER ||
                        c == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER || c == android.view.KeyEvent.KEYCODE_BUTTON_A
                    if (ok && e.type == KeyEventType.KeyUp) open()
                    ok
                }
                .focusable()
                .touchTap(open)
                .padding(horizontal = 16.dp, vertical = 13.dp),
        ) {
            Text(
                when {
                    value.isEmpty() -> label
                    isPassword -> "•".repeat(value.length)
                    else -> value
                },
                color = if (value.isEmpty()) Palette.TextMuted else Palette.Text,
                fontSize = 18.sp, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/** A small typing window with the keyboard already up. [done] gets the text, and whether Next/Done was pressed. */
private fun openTextEntry(
    ctx: android.content.Context,
    label: String,
    value: String,
    keyboardType: KeyboardType,
    isPassword: Boolean,
    imeAction: ImeAction,
    done: (String, Boolean) -> Unit,
) {
    val edit = android.widget.EditText(ctx).apply {
        hint = label
        isSingleLine = true
        inputType = when {
            isPassword -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            keyboardType == KeyboardType.Uri -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            else -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        imeOptions = if (imeAction == ImeAction.Done) android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            else android.view.inputmethod.EditorInfo.IME_ACTION_NEXT
        setText(value)
        setSelection(text.length)
    }
    val pad = (20 * ctx.resources.displayMetrics.density).toInt()
    val box = android.widget.FrameLayout(ctx).apply { setPadding(pad, pad / 2, pad, 0); addView(edit) }
    var finished = false
    val dialog = android.app.AlertDialog.Builder(ctx, android.R.style.Theme_Material_Dialog_Alert)
        .setTitle(label)
        .setView(box)
        .setPositiveButton("OK") { _, _ -> if (!finished) { finished = true; done(edit.text.toString(), false) } }
        .setNegativeButton("Cancel", null)
        .create()
    edit.setOnEditorActionListener { _, _, _ ->
        if (!finished) { finished = true; done(edit.text.toString(), true) }
        dialog.dismiss()
        true
    }
    dialog.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    dialog.setOnShowListener {
        edit.requestFocus()
        edit.postDelayed({
            val imm = ctx.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(edit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 120)
    }
    dialog.show()
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
