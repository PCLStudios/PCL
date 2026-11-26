package com.pcl.chronoslock

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

// ============================ HOME ============================

@Composable
fun HomeScreen() {
    val locked by ChronosEngine.locked.collectAsState()
    val whisper by ChronosEngine.whisper.collectAsState()
    val eye by ChronosEngine.thirdEye.collectAsState()
    val alwaysOn by ChronosEngine.alwaysOn.collectAsState()
    val phase by ChronosEngine.phase.collectAsState()
    val heard by ChronosEngine.lastHeard.collectAsState()
    val reply by ChronosEngine.lastReply.collectAsState()
    val timers by ChronosEngine.timers.collectAsState()
    var renaming by remember { mutableStateOf<String?>(null) }
    var widgetLimit by remember { mutableStateOf(false) }
    val editing = HomeLayout.editing
    val ids = HomeLayout.ids.toList()

    renaming?.let { RenameDialog(it) { renaming = null } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().appear(0)) {
            Box(Modifier.align(Alignment.Center)) { LockButton(locked, phase) { ChronosEngine.toggleLock() } }
            AlwaysOnButton(alwaysOn, Modifier.align(Alignment.TopEnd)) { ChronosEngine.setAlwaysOn(!alwaysOn) }
        }
        Spacer(Modifier.height(10.dp))

        val eyeName = Names.eye.uppercase(java.util.Locale.US)
        val (status, col) = when {
            phase == Phase.SPEAKING -> "SPEAKING" to C.Violet
            phase == Phase.THINKING -> "THINKING…" to C.Amber
            eye -> "$eyeName · TALK TO ME" to C.Violet
            locked && whisper -> "ACTIVE · WHISPER MODE" to C.Green
            locked -> "ACTIVE · SAY A COMMAND" to C.Green
            !alwaysOn -> "MIC OFF · TAP THE BUTTON TO LISTEN" to C.Soft
            whisper -> "STANDBY · WHISPER “CHRONOS LOCK”" to C.Soft
            else -> "STANDBY · SAY “CHRONOS LOCK”" to C.Soft
        }
        Crossfade(targetState = status to col, animationSpec = tween(250), label = "status") { (st, cl) ->
            Text2(st, cl, 13, bold = true, spacing = 2, align = TextAlign.Center)
        }
        Spacer(Modifier.height(10.dp))
        WaveBars(phase, if (locked) C.Green else C.Cyan)
        Spacer(Modifier.height(10.dp))

        Box(Modifier.appear(1)) {
            Panel {
                Text2("HEARD", C.Dim, 10, spacing = 3)
                Text2(if (heard.isBlank()) "—" else "“$heard”", C.Text, 13, maxLines = 2)
                if (reply.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text2("REPLY", C.Dim, 10, spacing = 3)
                    Text2(reply, C.Soft, 13, mono = false, maxLines = 3)
                }
                if (phase == Phase.SPEAKING) {
                    Spacer(Modifier.height(10.dp))
                    PillButton("■  HUSH", C.Violet, modifier = Modifier.fillMaxWidth()) { ChronosEngine.hush() }
                }
            }
        }

        // ---- music-safe mic prompt (the fix for media being paused) ----
        val vst by VoskMic.state.collectAsState()
        val vprog by VoskMic.progress.collectAsState()
        val vmsg by VoskMic.message.collectAsState()
        val hideCard by ChronosEngine.hideVoskCard.collectAsState()
        if (vst != VoskMic.State.READY && !hideCard) {
            Box(Modifier.padding(top = 14.dp).appear(2)) {
                Panel(accent = C.Amber) {
                    Text2("MUSIC OR VIDEOS STOPPING?", C.Amber, 12, bold = true, spacing = 2)
                    Spacer(Modifier.height(4.dp))
                    Text2("Android pauses your media every time a voice assistant listens. Install the music-safe mic once (about 40 MB, works offline) and PCL never touches your music again.", C.Soft, 12, mono = false)
                    Spacer(Modifier.height(8.dp))
                    if (vst == VoskMic.State.DOWNLOADING) {
                        Text2(vmsg, C.Amber, 12)
                        Spacer(Modifier.height(6.dp))
                        androidx.compose.material3.LinearProgressIndicator(progress = { vprog }, modifier = Modifier.fillMaxWidth(), color = C.Amber, trackColor = C.Dim)
                    } else {
                        if (vst == VoskMic.State.ERROR && vmsg.isNotBlank()) { Text2(vmsg, C.Red, 12, mono = false); Spacer(Modifier.height(6.dp)) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PillButton(if (vst == VoskMic.State.ERROR) "RETRY" else "INSTALL (USE WI-FI)", C.Amber, filled = true, modifier = Modifier.weight(1f)) { ChronosEngine.downloadVosk() }
                            PillButton("HIDE", C.Soft) { ChronosEngine.hideVoskCard() }
                        }
                    }
                }
            }
        }

        // ---- widgets: every button here can be removed, re-ordered or added ----
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text2("WIDGETS", C.Cyan, 12, bold = true, spacing = 3)
            Box(Modifier.weight(1f).height(1.dp).background(C.Line))
            if (editing) Chip("RESET", false, C.Amber) { HomeLayout.reset() }
            Chip(if (editing) "DONE" else "EDIT", editing, C.Green) { HomeLayout.editing = !editing }
        }
        if (ids.isEmpty()) Text2("No widgets. Tap EDIT, then add some.", C.Dim, 13, mono = false)
        ids.chunked(2).forEachIndexed { r, pair ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { id ->
                    key(id) { WidgetTile(id, ids.indexOf(id), ids.size, Modifier.weight(1f).appear(r + 2)) { renaming = it } }
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        AnimatedVisibility(editing, enter = fadeIn(tween(250)) + expandVertically(tween(300)), exit = fadeOut(tween(150)) + shrinkVertically(tween(200))) {
            Column(Modifier.fillMaxWidth()) {
                val spare = Widgets.ALL.filter { it.id !in ids }
                if (widgetLimit && !Pass.active) {
                    PassNudge("Free home screens hold ${Pass.FREE_WIDGETS} widgets. Watch one short ad for a 24 hour Power Pass with unlimited widgets.") { widgetLimit = false }
                    Spacer(Modifier.height(10.dp))
                }
                Text2(if (spare.isEmpty()) "Every widget is on your home screen." else "ADD A WIDGET", C.Soft, 11, spacing = 2)
                Spacer(Modifier.height(8.dp))
                spare.forEach { w ->
                    Box(Modifier.padding(bottom = 8.dp)) {
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text2(w.glyph, C.Cyan, 20, modifier = Modifier.padding(end = 12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text2(w.title(), C.Text, 12, bold = true, spacing = 1)
                                    Text2(w.about, C.Soft, 11, mono = false)
                                }
                                Chip("+ ADD", false, C.Green) { if (!HomeLayout.add(w.id)) widgetLimit = true }
                            }
                        }
                    }
                }
            }
        }

        if (timers.isNotEmpty()) {
            SectionLabel("TIMERS", timers.size)
            var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
            LaunchedEffect(timers) { while (true) { now = SystemClock.elapsedRealtime(); delay(500) } }
            Panel(accent = C.Amber) {
                timers.forEach { t ->
                    val s = ((t.endAt - now) / 1000).coerceAtLeast(0)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text2("%02d:%02d".format(java.util.Locale.US, s / 60, s % 60), C.Amber, 18, bold = true)
                        Text2(t.label.ifBlank { "timer" }, C.Soft, 13, mono = false, modifier = Modifier.padding(start = 12.dp).weight(1f), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(6.dp))
                PillButton("CANCEL ALL", C.Amber, modifier = Modifier.fillMaxWidth()) { ChronosEngine.cancelTimer() }
            }
        }

        SectionLabel("HOW IT WORKS")
        Panel {
            Text2("1.  Say “Chronos Lock” once. The button turns green.", C.Soft, 13, mono = false)
            Spacer(Modifier.height(4.dp))
            Text2("2.  Now just say commands: “${Names.combat.lowercase(java.util.Locale.US)}”, “set a timer for five minutes”, “what time is it”.", C.Soft, 13, mono = false)
            Spacer(Modifier.height(4.dp))
            Text2("3.  Say “open ${Names.eye.lowercase(java.util.Locale.US)}” to chat with the A.I. Say “close ${Names.eye.lowercase(java.util.Locale.US)}” to leave.", C.Soft, 13, mono = false)
            Spacer(Modifier.height(4.dp))
            Text2("4.  Speaking quietly? Say “whisper mode”.", C.Soft, 13, mono = false)
            Spacer(Modifier.height(4.dp))
            Text2("5.  Tap EDIT above to rearrange or add widgets. Open STYLE to rename modes and change the look.", C.Soft, 13, mono = false)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LockButton(locked: Boolean, phase: Phase, onClick: () -> Unit) {
    val color = if (locked) C.Green else C.Dim
    val t = rememberInfiniteTransition(label = "fx")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "spin")
    val fast by t.animateFloat(360f, 0f, infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart), label = "fast")

    Box(
        Modifier.size(250.dp).pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            if (locked) {
                drawCircle(color.copy(alpha = (1f - pulse) * 0.35f), radius = r * (0.62f + 0.38f * pulse), style = Stroke(2.dp.toPx()))
                if (phase == Phase.SPEAKING || phase == Phase.THINKING)
                    drawCircle(C.Violet.copy(alpha = 0.25f * (1f - pulse)), radius = r * (0.7f + 0.3f * pulse), style = Stroke(6.dp.toPx()))
            }
            rotate(if (locked) spin else 0f, Offset(center.x, center.y)) {
                drawCircle(color.copy(alpha = 0.8f), radius = r * 0.88f,
                    style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f))))
            }
            rotate(if (locked) fast else 0f, Offset(center.x, center.y)) {
                drawCircle(color.copy(alpha = 0.5f), radius = r * 0.74f,
                    style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 22f))))
            }
            drawCircle(brush = Brush.radialGradient(listOf(color.copy(alpha = if (locked) 0.38f else 0.08f), Color.Transparent)), radius = r * 0.72f)
            drawCircle(color, radius = r * 0.6f, style = Stroke(if (locked) 5.dp.toPx() else 3.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, tint = color, modifier = Modifier.size(48.dp))
            Spacer(Modifier.height(8.dp))
            Text2(if (locked) "ACTIVE" else "INACTIVE", color, 12, bold = true, spacing = 2)
            Text2(if (locked) "tap to deactivate" else "tap to activate", C.Soft, 10, mono = false)
        }
    }
}

/** Small round switch beside the big button: ON = always listening for "Chronos Lock"; OFF = mic only while engaged. */
@Composable
private fun AlwaysOnButton(on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = if (on) C.Cyan else C.Soft
    Column(
        modifier.size(62.dp).clip(CircleShape).background(c.copy(alpha = if (on) 0.16f else 0.05f))
            .border(1.5.dp, c, CircleShape).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        Text2("ALWAYS", c, 8, bold = true, spacing = 1)
        Text2(if (on) "ON" else "OFF", c, 13, bold = true)
    }
}

/** Live voice bars: follow the mic while listening, a gentle wave while she talks. */
@Composable
private fun WaveBars(phase: Phase, color: Color) {
    val level by ChronosEngine.level.collectAsState()
    val t = rememberInfiniteTransition(label = "wave")
    val ph by t.animateFloat(0f, 6.2832f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "ph")
    Canvas(Modifier.fillMaxWidth().height(44.dp)) {
        val n = 31
        val gap = size.width / n
        for (i in 0 until n) {
            val edge = 1f - abs(i - n / 2) / (n / 2f) * 0.75f
            val amp = when (phase) {
                Phase.LISTENING -> 0.06f + level * 0.94f * (0.55f + 0.45f * sin(ph + i * 0.6f))
                Phase.SPEAKING -> 0.25f + 0.55f * abs(sin(ph * 1.3f + i * 0.45f))
                Phase.THINKING -> 0.1f + 0.5f * abs(sin(ph - i * 0.35f)).let { it * it }
                Phase.STANDBY -> 0.05f
            }
            val h = (size.height * amp * edge).coerceAtLeast(3.dp.toPx())
            val x = gap * (i + 0.5f)
            drawLine(color.copy(alpha = 0.35f + 0.65f * edge), Offset(x, size.height / 2 - h / 2), Offset(x, size.height / 2 + h / 2),
                strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        }
    }
}

// ============================ THIRD EYE ============================

@Composable
fun EyeScreen() {
    val eye by ChronosEngine.thirdEye.collectAsState()
    val chat by ChronosEngine.chat.collectAsState()
    val phase by ChronosEngine.phase.collectAsState()
    val key by ChronosEngine.aiKey.collectAsState()
    val url by ChronosEngine.aiUrl.collectAsState()
    val model by ChronosEngine.aiModel.collectAsState()
    val list = rememberLazyListState()
    var text by remember { mutableStateOf("") }
    val provider = ThirdEyeAI.providerFor(url)

    LaunchedEffect(chat.size, phase) { if (chat.isNotEmpty()) list.animateScrollToItem(chat.size - 1) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text2(if (eye) "● CONNECTED" else "○ OFFLINE", if (eye) C.Violet else C.Dim, 13, bold = true, spacing = 2)
                Text2("${provider?.name ?: "Custom"} · $model", C.Soft, 11)
            }
            if (chat.isNotEmpty()) { Chip("CLEAR", false) { ChronosEngine.clearChat() }; Spacer(Modifier.size(8.dp)) }
            PillButton(if (eye) "DISCONNECT" else "CONNECT", if (eye) C.Red else C.Violet, filled = !eye) { ChronosEngine.toggleEye() }
        }

        if (key.isBlank() && provider?.needsKey == true) {
            Panel(accent = C.Amber, modifier = Modifier.padding(bottom = 10.dp)) {
                Text2("NEEDS A FREE A.I. KEY", C.Amber, 12, bold = true, spacing = 1)
                Spacer(Modifier.height(4.dp))
                Text2("${Names.eye} talks to an online chatbot. Getting a free key takes about two minutes. The guide walks you through it step by step.", C.Soft, 12, mono = false)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("STEP-BY-STEP GUIDE", C.Amber, filled = true) { ChronosEngine.tab.value = Screen.GUIDE }
                    PillButton("OPEN SYSTEM", C.Amber) { ChronosEngine.tab.value = Screen.SYSTEM }
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (chat.isEmpty()) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(28.dp))
                    Text2("◉", C.Violet, 54)
                    Spacer(Modifier.height(8.dp))
                    Text2(Names.eye.uppercase(java.util.Locale.US), C.Violet, 18, bold = true, spacing = 5)
                    Spacer(Modifier.height(6.dp))
                    Text2("Connect, then just talk — or type below. Every question is independent; nothing is remembered.", C.Soft, 13, mono = false, align = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        listOf("Who are you?", "Explain black holes simply", "Give me a healthy dinner idea", "Tell me a fun fact about space").forEach {
                            Chip(it, false, C.Violet) { ChronosEngine.askTyped(it) }
                        }
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize(), state = list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(chat) { i, m -> Box(Modifier.appear(0)) { Bubble(m) } }
                    if (phase == Phase.THINKING) item { Text2("● thinking…", C.Amber, 12, modifier = Modifier.padding(start = 4.dp)) }
                }
            }
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val send = { if (text.isNotBlank()) { ChronosEngine.askTyped(text); text = "" } }
            Field(text, { text = it }, "Type a question…", Modifier.weight(1f), imeAction = ImeAction.Send, onSubmit = send)
            PillButton("SEND", C.Violet, filled = true) { send() }
        }
    }
}

@Composable
private fun Bubble(m: Msg) {
    val c = if (m.fromUser) C.Cyan else C.Violet
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape).background(c.copy(alpha = 0.12f)).border(1.dp, c.copy(alpha = 0.5f), shape).padding(12.dp)
        ) {
            Text2(if (m.fromUser) "YOU" else Names.eye.uppercase(java.util.Locale.US), c, 10, bold = true, spacing = 2)
            Spacer(Modifier.height(3.dp))
            Text2(m.text, C.Text, 14, mono = false)
        }
    }
}

// ============================ NOTES ============================

@Composable
fun NotesScreen() {
    val notes by ChronosEngine.notes.collectAsState()
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        ScreenTitle("NOTES", "Say “take a note buy milk” and it lands here. “Read my notes” reads them out.")
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val add = { if (text.isNotBlank()) { ChronosEngine.addNote(text.trim()); text = "" } }
            Field(text, { text = it }, "Add a note…", Modifier.weight(1f), onSubmit = add)
            PillButton("ADD", C.Cyan, filled = true) { add() }
        }
        Spacer(Modifier.height(12.dp))
        if (notes.isEmpty()) Text2("No notes yet.", C.Dim, 13, mono = false)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(notes) { i, n ->
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text2(n, C.Text, 14, mono = false, modifier = Modifier.weight(1f))
                        Text2("✕", C.Red, 16, modifier = Modifier.clickable { ChronosEngine.deleteNote(i) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
                    }
                }
            }
        }
        if (notes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            PillButton("CLEAR ALL", C.Red, modifier = Modifier.fillMaxWidth()) { ChronosEngine.clearNotes() }
        }
        Spacer(Modifier.height(16.dp))
    }
}
