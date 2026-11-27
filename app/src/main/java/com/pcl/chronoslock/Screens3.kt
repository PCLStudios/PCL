package com.pcl.chronoslock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import java.util.Locale

// ============================ PROGRAMS ============================

@Composable
fun FlowsScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenTitle("PROGRAMS", "Program what happens when something turns on or off. Steps run top to bottom.")
        Panel {
            Text2("WHAT YOU CAN DO", C.Cyan, 11, bold = true, spacing = 2)
            Spacer(Modifier.height(4.dp))
            Text2("Open apps, play songs, open links, press media keys, set volume, speak, flash the flashlight, wait, timers, notes, switch other modes, ask the A.I., search the web, navigate, dial, set an alarm, post a notification, copy text, open the camera, set the ringer, open settings, vibrate, or stop the rest of the program when a condition is met.", C.Soft, 12, mono = false)
            Spacer(Modifier.height(6.dp))
            Text2("Every step can have a wait, an IF (battery, charging, night, weekday, online, music playing…) and a chance. Start from a template with one tap.", C.Soft, 12, mono = false)
            Spacer(Modifier.height(6.dp))
            Text2("Tip: PLAY SONG uses Android's “play from search”, which Spotify and YouTube Music understand. If your player ignores it, use OPEN APP then MEDIA KEY play. Several lines in a box = she picks one at random.", C.Dim, 11, mono = false)
        }
        if (!Launcher.canOpenInBackground(LocalContext.current)) {
            val ctx = LocalContext.current
            Spacer(Modifier.height(10.dp))
            Panel(accent = C.Amber) {
                Text2("TO RUN PROGRAMS WHILE OTHER APPS ARE OPEN", C.Amber, 11, bold = true, spacing = 1)
                Spacer(Modifier.height(4.dp))
                Text2("Android blocks apps from opening other apps in the background. Allow “Display over other apps” once and your programs can open Spotify and others even when PCL isn't on screen. Without it, PCL posts a notification you tap instead.", C.Soft, 12, mono = false)
                Spacer(Modifier.height(8.dp))
                PillButton("ALLOW DISPLAY OVER OTHER APPS", C.Amber, filled = true, modifier = Modifier.fillMaxWidth()) {
                    runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + ctx.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        FlowBlock(Names.combat.uppercase(Locale.US), C.Red, "combat", "TURNED ON", "TURNED OFF")
        FlowBlock(Names.quantum.uppercase(Locale.US), C.Violet, "quantum", "TURNED ON", "TURNED OFF")
        FlowBlock("CHRONOS LOCK", C.Green, "lock", "ACTIVATED", "DEACTIVATED")
        FlowBlock(Names.eye.uppercase(Locale.US), C.Cyan, "eye", "OPENED", "CLOSED")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FlowBlock(title: String, accent: Color, base: String, onWord: String, offWord: String) {
    SectionLabel(title)
    SlotEditor("WHEN $onWord", accent, base + "_on")
    Spacer(Modifier.height(10.dp))
    SlotEditor("WHEN $offWord", accent, base + "_off")
}

@Composable
private fun SlotEditor(label: String, accent: Color, slot: String) {
    val steps by Flows.flow(slot).collectAsState()
    val off by Flows.disabled.collectAsState()
    val enabled = slot !in off
    val ctx = LocalContext.current
    var limit by remember { mutableStateOf(false) }
    Panel(accent = if (enabled) accent else C.Line) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text2(label, if (enabled) accent else C.Dim, 12, bold = true, spacing = 2, modifier = Modifier.weight(1f))
            Chip(if (enabled) "ON" else "OFF", enabled, accent) { Flows.setEnabled(slot, !enabled) }
        }
        if (steps.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text2("No steps. She only speaks.", C.Soft, 12, mono = false)
        }
        steps.forEachIndexed { i, s -> key(s.id) { StepEditor(slot, i + 1, steps.size, s, accent) } }
        Spacer(Modifier.height(12.dp))
        Text2("ADD A STEP", C.Dim, 10, spacing = 2)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Flows.TYPES.forEach { (t, l) -> Chip("+ $l", false, accent) { if (!Flows.add(slot, t)) limit = true } }
        }
        Spacer(Modifier.height(8.dp))
        Text2("TEMPLATES", C.Dim, 10, spacing = 2)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Flows.TEMPLATES.forEach { (n, _) -> Chip(n, false, C.Green) { if (!Flows.addTemplate(slot, n)) limit = true } }
        }
        if (limit && !Pass.active) {
            Spacer(Modifier.height(10.dp))
            PassNudge("Free programs hold ${Pass.FREE_STEPS} steps. Watch one short ad for a 24 hour Power Pass with unlimited steps.") { limit = false }
        }
        if (steps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            PillButton("▶ TEST THESE STEPS", accent, modifier = Modifier.fillMaxWidth()) { Flows.run(ctx, steps) }
        }
    }
}

private fun hintFor(type: String) = when (type) {
    Flows.OPEN_APP -> "app name, e.g. Metrolist"
    Flows.PLAY -> "song or search. Several lines = random pick"
    Flows.URL -> "link. Several lines = random pick"
    Flows.KEY -> "play, pause, next, previous or playpause"
    Flows.VOLUME -> "0 to 100"
    Flows.SAY -> "what she says. {time} {date} {battery} work. Several lines = random"
    Flows.TORCH -> "on, off or blink"
    Flows.WAIT -> "seconds to wait"
    Flows.TIMER -> "minutes, e.g. 25"
    Flows.NOTE -> "note text. {time} {date} {battery} work"
    Flows.MODE -> "e.g. mode 1 on, mode 2 off, lock on"
    Flows.NAVIGATE -> "address or place to navigate to"
    Flows.DIAL -> "phone number (opens the dialer)"
    Flows.ALARM -> "time like 7:30 or 19:05. Label below is optional"
    Flows.NOTIFY -> "text of the notification. {time} {date} {battery} work"
    Flows.COPY -> "text to copy to the clipboard"
    Flows.CAMERA -> "photo or video"
    Flows.RINGER -> "normal or vibrate"
    Flows.STOP -> "nothing to type. Add an IF below: if it is true, the steps after this one are skipped"
    Flows.SCREEN -> "home, notes, programs, voice, system, style, guide"
    Flows.VIBRATE -> "milliseconds, e.g. 400"
    Flows.SETTINGS -> "wifi, bluetooth, display, sound, battery saver, apps"
    Flows.ASK -> "question for the A.I. (she speaks the answer)"
    Flows.SEARCH -> "what to search on Google"
    else -> ""
}

@Composable
private fun StepEditor(slot: String, n: Int, total: Int, s: Step, accent: Color) {
    var arg by remember { mutableStateOf(s.arg) }
    var app by remember { mutableStateOf(s.app) }
    var delay by remember { mutableStateOf(if (s.delay == 0) "" else s.delay.toString()) }
    var condArg by remember { mutableStateOf(s.condArg) }
    fun push(type: String = s.type) = Flows.update(slot, s.copy(type = type, arg = arg, app = app, delay = delay.toIntOrNull() ?: 0, condArg = condArg))

    Spacer(Modifier.height(14.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text2("STEP $n", C.Dim, 10, spacing = 2, modifier = Modifier.weight(1f))
        if (n > 1) Text2("▲", C.Soft, 16, modifier = Modifier.clickable { Flows.move(slot, s.id, -1) }.padding(horizontal = 10.dp, vertical = 4.dp))
        if (n < total) Text2("▼", C.Soft, 16, modifier = Modifier.clickable { Flows.move(slot, s.id, 1) }.padding(horizontal = 10.dp, vertical = 4.dp))
        Text2("✕", C.Red, 16, modifier = Modifier.clickable { Flows.remove(slot, s.id) }.padding(start = 10.dp, top = 4.dp, bottom = 4.dp))
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Flows.TYPES.forEach { (t, l) -> Chip(l, s.type == t, accent) { push(t) } }
    }
    Spacer(Modifier.height(6.dp))
    Flows.CHOICES[s.type]?.let { opts ->
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            opts.forEach { o -> Chip(o, arg.trim().equals(o, true), C.Green) { arg = o; push() } }
        }
        Spacer(Modifier.height(6.dp))
    }
    run {
        val multi = s.type == Flows.PLAY || s.type == Flows.URL || s.type == Flows.SAY || s.type == Flows.ASK
        Field(arg, { arg = it; push() }, hintFor(s.type), Modifier.fillMaxWidth(), singleLine = !multi,
            imeAction = if (multi) ImeAction.Default else ImeAction.Done)
    }
    if (s.type == Flows.PLAY || s.type == Flows.URL) {
        Spacer(Modifier.height(6.dp))
        Field(app, { app = it; push() }, "app to use (optional), e.g. Metrolist or Spotify", Modifier.fillMaxWidth())
    }
    if (s.type == Flows.TIMER) {
        Spacer(Modifier.height(6.dp))
        Field(app, { app = it; push() }, "what she says when it ends (optional)", Modifier.fillMaxWidth())
    }
    if (s.type == Flows.ALARM || s.type == Flows.NOTIFY) {
        Spacer(Modifier.height(6.dp))
        Field(app, { app = it; push() }, if (s.type == Flows.ALARM) "alarm label (optional)" else "notification title (optional)", Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(6.dp))
    Field(delay, { delay = it.filter { c -> c.isDigit() }.take(3); push() }, "wait before this step, seconds (optional)", Modifier.fillMaxWidth())

    // ---- conditions ----
    Spacer(Modifier.height(8.dp))
    Text2("ONLY IF", C.Dim, 10, spacing = 2)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Flows.CONDS.forEach { (c, l) -> Chip(l, s.cond == c, C.Amber) { Flows.update(slot, s.copy(arg = arg, app = app, delay = delay.toIntOrNull() ?: 0, condArg = condArg, cond = c)) } }
    }
    if (Flows.condNeedsNumber(s.cond)) {
        Spacer(Modifier.height(6.dp))
        Field(condArg, { condArg = it.filter { c -> c.isDigit() }.take(3); push() },
            if (s.cond == "after" || s.cond == "before") "hour 0 to 23" else "percent 0 to 100", Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(8.dp))
    Text2("CHANCE IT HAPPENS", C.Dim, 10, spacing = 2)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(100, 75, 50, 25, 10).forEach { p ->
            Chip(if (p == 100) "ALWAYS" else "$p%", s.chance == p, C.Amber) {
                Flows.update(slot, s.copy(arg = arg, app = app, delay = delay.toIntOrNull() ?: 0, condArg = condArg, chance = p))
            }
        }
    }
}
