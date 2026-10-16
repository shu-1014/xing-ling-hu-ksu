package com.xinglinghu.manager.ui.screen.assistant

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xinglinghu.manager.R
import com.xinglinghu.manager.ui.navigation3.LocalNavigator
import com.xinglinghu.manager.ui.theme.isInDarkTheme
import com.xinglinghu.manager.ui.util.execKsud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Offline assistant chat, laid out after the reference design.
 *
 * The screen owns no network code and no provider key: [LocalAssistant] answers
 * everything from local state, and the only commands that can change the device
 * are the ones the user taps to confirm. Nothing typed here leaves the phone,
 * which is the point — the app already runs as root, so a chat box that phoned
 * home would be a far bigger risk than the feature is worth.
 *
 * The palette is hard-coded rather than taken from the active theme. The chat
 * keeps one identity in both Miuix and Material modes, and the greeting gradient
 * stays readable in light and dark alike. Wrapping the tree in its own
 * [MaterialTheme] keeps the Material3 pieces (text field, progress, icons)
 * consistent even under the Miuix theme, which does not install one.
 */
private val BRAND_START = Color(0xFF2BB3A3)
private val BRAND_END = Color(0xFF3F7FC1)
private val BUBBLE_ASSISTANT_LIGHT = Color(0xFFF2F5F8)
private val BUBBLE_ASSISTANT_DARK = Color(0xFF1E2A33)
private val BUBBLE_USER = Color(0xFF1F8F87)
private val CARD_BG_LIGHT = Color(0xFFFFF8E9)
private val CARD_BG_DARK = Color(0xFF322A1B)
private val CARD_LINE = Color(0xFFE8D9B5)
private val PAGE_LIGHT = Color(0xFFF6F8FA)
private val PAGE_DARK = Color(0xFF0E1419)

/** One rendered chat turn. */
private data class Turn(
    val text: String,
    val fromUser: Boolean,
    val action: LocalAssistant.Action? = null,
)

@Composable
fun AssistantScreen() {
    val dark = isInDarkTheme()
    val scheme = if (dark) {
        darkColorScheme(
            primary = BRAND_END,
            background = PAGE_DARK,
            surface = Color(0xFF141C22),
            onSurface = Color(0xFFDCE6ED),
        )
    } else {
        lightColorScheme(
            primary = BRAND_END,
            background = PAGE_LIGHT,
            surface = Color.White,
            onSurface = Color(0xFF1B2A35),
        )
    }
    MaterialTheme(colorScheme = scheme) {
        AssistantContent(dark)
    }
}

@Composable
private fun AssistantContent(dark: Boolean) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val turns = remember { mutableStateListOf<Turn>() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var jokeIndex by remember { mutableIntStateOf(LocalAssistant.nextJoke(context)) }

    // Seed the transcript with the greeting, mirroring the reference layout,
    // which opens on a welcome line rather than an empty list.
    LaunchedEffect(Unit) {
        if (turns.isEmpty()) {
            turns += Turn(LocalAssistant.greeting(context), fromUser = false)
        }
    }

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || busy) return
        turns += Turn(trimmed, fromUser = true)
        input = ""
        busy = true
        scope.launch {
            // Kernel probes and ksud calls both block, so keep them off the UI thread.
            val reply = withContext(Dispatchers.IO) { LocalAssistant.reply(context, trimmed) }
            turns += Turn(reply.text, fromUser = false, action = reply.action)
            busy = false
        }
    }

    fun runAction(action: LocalAssistant.Action) {
        if (busy) return
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { execKsud(action.command) }
            val message = if (ok) {
                context.getString(R.string.assistant_action_done, action.label)
            } else {
                context.getString(R.string.assistant_action_failed, action.label)
            }
            turns += Turn(message, fromUser = false)
            busy = false
        }
    }

    LaunchedEffect(turns.size, busy) {
        val last = turns.lastIndex + if (busy) 1 else 0
        if (last >= 0) listState.animateScrollToItem(last)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (dark) PAGE_DARK else PAGE_LIGHT)
            // safeDrawing already folds in the IME, so the composer rides above
            // the keyboard without a second, doubled bottom inset.
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        AssistantTopBar(
            title = stringResource(R.string.assistant_title),
            subtitle = stringResource(R.string.assistant_subtitle_offline),
            onBack = { navigator.pop() },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                GreetingCard(
                    title = stringResource(R.string.assistant_greeting_title),
                    body = stringResource(R.string.assistant_greeting_body),
                )
            }
            items(turns) { turn ->
                MessageBubble(
                    turn = turn,
                    dark = dark,
                    onAction = ::runAction,
                    onCopy = { text ->
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("🐾星棱狐🐾", text))
                    },
                )
            }
            if (busy) {
                item { TypingBubble(dark) }
            }
            item {
                JokeCard(
                    title = LocalAssistant.jokeCardTitle(context),
                    body = LocalAssistant.joke(jokeIndex),
                    button = stringResource(R.string.assistant_joke_again),
                    dark = dark,
                    onAgain = { jokeIndex = LocalAssistant.nextJoke(context) },
                )
            }
            item {
                Text(
                    text = LocalAssistant.disclaimer(context),
                    fontSize = 11.sp,
                    color = Color(0xFF8A97A3),
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                )
            }
        }

        Composer(
            value = input,
            onValueChange = { input = it },
            hint = stringResource(R.string.assistant_input_hint),
            sendDescription = stringResource(R.string.assistant_send),
            busy = busy,
            dark = dark,
            onSend = { send(input) },
        )
    }
}

@Composable
private fun AssistantTopBar(title: String, subtitle: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.horizontalGradient(listOf(BRAND_START, BRAND_END)))
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White,
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("狐", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun GreetingCard(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(BRAND_START, BRAND_END)))
            .padding(18.dp),
    ) {
        Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Color.White.copy(alpha = 0.92f), fontSize = 13.sp)
    }
}

@Composable
private fun MessageBubble(
    turn: Turn,
    dark: Boolean,
    onAction: (LocalAssistant.Action) -> Unit,
    onCopy: (String) -> Unit,
) {
    val bubbleColor = when {
        turn.fromUser -> BUBBLE_USER
        dark -> BUBBLE_ASSISTANT_DARK
        else -> BUBBLE_ASSISTANT_LIGHT
    }
    val textColor = when {
        turn.fromUser -> Color.White
        dark -> Color(0xFFDCE6ED)
        else -> Color(0xFF1B2A35)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (turn.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                // Long-press-free copy: a tap puts the answer on the clipboard,
                // which is the only interaction a bubble needs here.
                .clickable { onCopy(turn.text) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(turn.text, color = textColor, fontSize = 14.sp)
            turn.action?.let { action ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.assistant_run_action, action.label),
                    color = if (dark) Color(0xFF6FD3C8) else Color(0xFF0F6F68),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onAction(action) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TypingBubble(dark: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Text(
            text = "···",
            color = if (dark) Color(0xFF9FB4C2) else Color(0xFF7A8A96),
            fontSize = 16.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(if (dark) BUBBLE_ASSISTANT_DARK else BUBBLE_ASSISTANT_LIGHT)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun JokeCard(
    title: String,
    body: String,
    button: String,
    dark: Boolean,
    onAgain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) CARD_BG_DARK else CARD_BG_LIGHT)
            .border(1.dp, CARD_LINE, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(
            title,
            color = if (dark) Color(0xFFE3C88E) else Color(0xFF8A6A22),
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(body, color = if (dark) Color(0xFFEFE2C6) else Color(0xFF4A3B1C), fontSize = 15.sp)
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(if (dark) Color(0xFF4A3D22) else Color(0xFFF0DFB4))
                .clickable(onClick = onAgain)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                button,
                color = if (dark) Color(0xFFF2E4C4) else Color(0xFF6B5217),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    sendDescription: String,
    busy: Boolean,
    dark: Boolean,
    onSend: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (dark) Color(0xFF141C22) else Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp, max = 120.dp),
            placeholder = { Text(hint, fontSize = 13.sp) },
            shape = RoundedCornerShape(14.dp),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = !busy && value.isNotBlank(),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = sendDescription,
                    tint = BRAND_END,
                )
            }
        }
    }
}
