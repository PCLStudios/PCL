package com.pcl.chronoslock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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

// ============================ PROGRAMS ============================

@Composable
fun FlowsScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenTitle("PROGRAMS", "Program what happens when a mode turns on or off. Steps run top to bottom.")
        Panel {
            Text2("EXAMPLES", C.Cyan, 11, bold = true, spacing = 2)
            Spacer(Modifier.height(4.dp))
            Text2("Combat ON:  OPEN APP “Metrolist”, then 4 s later MEDIA KEY “play”.", C.Soft, 12, mono = false)
            Spacer(Modifier.height(3.dp))
            Text2("Quantum ON:  PLAY SONG with several lines (lofi beats / deep focus / brown noise) and an app name: she picks one at random.", C.Soft, 12, mono = false)
            Spacer(Modifier.height(3.dp))
            Text2("Quantum OFF:  MEDIA KEY “pause”.", C.Soft, 12, mono = false)
            Spacer(Modifier.height(6.dp))
            Text2("Tip: PLAY SONG uses Android's “play from search”, which Spotify and YouTube Music understand. If your player ignores it, use OPEN APP then MEDIA KEY play, or OPEN LINK with the app name.", C.Dim, 11, mono = false)
        }
        FlowBlock("COMBAT MODE", C.Red, "combat")
        FlowBlock("QUANTUM FOCUS", C.Violet, "quantum")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FlowBlock(title: String, accent: Color, base: String) {
    SectionLabel(title)
    SlotEditor("WHEN TURNED ON", accent, base + "_on")
    Spacer(Modifier.height(10.dp))
    SlotEditor("WHEN TURNED OFF", accent, base + "_off")
}

@Composable
private fun SlotEditor(label: String, accent: Color, slot: String) {
    val steps by Flows.flow(slot).collectAsState()
    val ctx = LocalContext.current
    Panel(accent = accent) {
        Text2(label, accent, 12, bold = true, spacing = 2)
        if (steps.isEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text2("No steps. She only speaks.", C.Soft, 12, mono = false)
        }
        steps.forEachIndexed { i, s -> key(s.id) { StepEditor(slot, i + 1, s) } }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("+ ADD STEP", accent, modifier = Modifier.weight(1f)) { Flows.add(slot, Flows.OPEN_APP) }
            if (steps.isNotEmpty()) PillButton("▶ TEST", accent, modifier = Modifier.weight(1f)) { Flows.run(ctx, steps) }
        }
    }
}

private fun hintFor(type: String) = when (type) {
    Flows.OPEN_APP -> "app name, e.g. Metrolist"
    Flows.PLAY -> "song or search. Several lines = random pick"
    Flows.URL -> "link. Several lines = random pick"
    Flows.KEY -> "play, pause, next, previous or playpause"
    Flows.VOLUME -> "0 to 100"
    Flows.SAY -> "what she says"
    else -> "on or off"
}

@Composable
private fun StepEditor(slot: String, n: Int, s: Step) {
    var arg by remember { mutableStateOf(s.arg) }
    var app by remember { mutableStateOf(s.app) }
    var delay by remember { mutableStateOf(if (s.delay == 0) "" else s.delay.toString()) }
    fun push(type: String = s.type) = Flows.update(slot, s.copy(type = type, arg = arg, app = app, delay = delay.toIntOrNull() ?: 0))

    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text2("STEP $n", C.Dim, 10, spacing = 2, modifier = Modifier.weight(1f))
        Text2("✕", C.Red, 16, modifier = Modifier.clickable { Flows.remove(slot, s.id) }.padding(start = 12.dp, top = 4.dp, bottom = 4.dp))
    }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Flows.TYPES.forEach { (t, l) -> Chip(l, s.type == t, C.Cyan) { push(t) } }
    }
    Spacer(Modifier.height(6.dp))
    val multi = s.type == Flows.PLAY || s.type == Flows.URL
    Field(arg, { arg = it; push() }, hintFor(s.type), Modifier.fillMaxWidth(), singleLine = !multi,
        imeAction = if (multi) ImeAction.Default else ImeAction.Done)
    if (multi) {
        Spacer(Modifier.height(6.dp))
        Field(app, { app = it; push() }, "app to use (optional), e.g. Metrolist or Spotify", Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(6.dp))
    Field(delay, { delay = it.filter { c -> c.isDigit() }.take(3); push() }, "wait before this step, seconds (optional)", Modifier.fillMaxWidth())
}
