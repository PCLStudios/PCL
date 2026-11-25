package com.pcl.chronoslock

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

private fun open(ctx: android.content.Context, i: Intent) {
    runCatching { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

// ============================ COMMANDS ============================

@Composable
fun HelpScreen() {
    var q by remember { mutableStateOf("") }
    val shown = Commands.all.filter {
        q.isBlank() || it.phrase.contains(q, true) || it.help.contains(q, true) || it.cat.contains(q, true)
    }
    val groups = shown.groupBy { it.cat }
    val locked by ChronosEngine.locked.collectAsState()

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            ScreenTitle("COMMANDS", "${Commands.all.size} commands. Say “Chronos Lock” once, then just say them — no wake word needed.")
            Panel(accent = if (locked) C.Green else C.Amber) {
                Text2(
                    if (locked) "● ENGAGED — say any command below directly." else "○ NOT ENGAGED — say “Chronos Lock” first (or “Chronos Lock, <command>” in one go).",
                    if (locked) C.Green else C.Amber, 12, bold = true
                )
            }
            Spacer(Modifier.height(10.dp))
            Field(q, { q = it }, "Search commands…", Modifier.fillMaxWidth())
        }
        groups.forEach { (cat, cmds) ->
            item { SectionLabel(cat, cmds.size) }
            items(cmds) { c ->
                Panel {
                    Text2("“${c.phrase}”", C.Cyan, 13, bold = true)
                    Spacer(Modifier.height(4.dp))
                    Text2(c.help, C.Soft, 12, mono = false)
                }
            }
        }
        if (shown.isEmpty()) item { Text2("Nothing matches “$q”.", C.Dim, 13, mono = false) }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

// ============================ VOICE ============================

@Composable
fun VoiceScreen() {
    val ctx = LocalContext.current
    val voices by ChronosEngine.voices.collectAsState()
    val sel by ChronosEngine.voiceName.collectAsState()
    val pitch by ChronosEngine.pitch.collectAsState()
    val rate by ChronosEngine.rate.collectAsState()
    val whisper by ChronosEngine.whisper.collectAsState()
    val colors = SliderDefaults.colors(thumbColor = C.Cyan, activeTrackColor = C.Cyan, inactiveTrackColor = C.Dim)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenTitle("VOICE", "How she listens and how she sounds.")

        Panel(accent = if (whisper) C.Violet else C.Line) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text2("WHISPER MODE", if (whisper) C.Violet else C.Text, 14, bold = true, spacing = 2)
                    Spacer(Modifier.height(4.dp))
                    Text2("Uses online recognition, waits longer for quiet speech, forgives badly-heard wake words, and she answers softly. Needs internet.", C.Soft, 12, mono = false)
                }
                Spacer(Modifier.padding(6.dp))
                Chip(if (whisper) "ON" else "OFF", whisper, C.Violet) { ChronosEngine.setWhisper(!whisper) }
            }
        }

        SectionLabel("VOICE", voices.size)
        Text2("Tap a voice to hear it. ♀ marks likely female voices; “online” ones are Google's neural voices and sound the most human. You can also say “change voice”.", C.Soft, 12, mono = false)
        Spacer(Modifier.height(8.dp))
        voices.forEach { v ->
            Row(Modifier.fillMaxWidth().clickable { ChronosEngine.setVoice(v.name) }.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text2(v.label, if (v.name == sel) C.Cyan else C.Soft, 12, modifier = Modifier.weight(1f))
                if (v.name == sel) Text2("●", C.Cyan, 12)
            }
        }
        if (voices.isEmpty()) Text2("No voices found yet — install Google Speech Services below.", C.Soft, 12, mono = false)

        SectionLabel("TUNING")
        Text2("PITCH  %.2f".format(java.util.Locale.US, pitch), C.Soft, 12)
        Slider(pitch, { ChronosEngine.setPitch(it) }, valueRange = 0.7f..1.4f, colors = colors, onValueChangeFinished = { ChronosEngine.preview() })
        Text2("SPEED  %.2f".format(java.util.Locale.US, rate), C.Soft, 12)
        Slider(rate, { ChronosEngine.setRate(it) }, valueRange = 0.7f..1.3f, colors = colors, onValueChangeFinished = { ChronosEngine.preview() })

        Spacer(Modifier.height(6.dp))
        PillButton("▶  TEST VOICE", modifier = Modifier.fillMaxWidth()) { ChronosEngine.preview() }
        Spacer(Modifier.height(8.dp))
        PillButton("RESET TO NATURAL (1.00 / 1.00)", modifier = Modifier.fillMaxWidth()) { ChronosEngine.resetVoice() }
        Spacer(Modifier.height(8.dp))
        PillButton("GET MORE NATURAL VOICES", modifier = Modifier.fillMaxWidth()) { open(ctx, Intent("com.android.settings.TTS_SETTINGS")) }
        Spacer(Modifier.height(24.dp))
    }
}

// ============================ SYSTEM ============================

@Composable
fun SystemScreen() {
    val ctx = LocalContext.current
    val sessionMin by ChronosEngine.sessionMin.collectAsState()
    val url0 by ChronosEngine.aiUrl.collectAsState()
    val model0 by ChronosEngine.aiModel.collectAsState()
    val key0 by ChronosEngine.aiKey.collectAsState()
    val persona0 by ChronosEngine.persona.collectAsState()

    var url by remember(url0) { mutableStateOf(url0) }
    var model by remember(model0) { mutableStateOf(model0) }
    var key by remember(key0) { mutableStateOf(key0) }
    var persona by remember(persona0) { mutableStateOf(persona0) }
    var saved by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf("") }
    val provider = ThirdEyeAI.providerFor(url)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenTitle("SYSTEM", "Session, Third Eye A.I. and phone setup.")

        SectionLabel("SESSION TIMEOUT")
        Text2("Auto-disengage Chronos Lock after this much silence. Off = stays engaged until you say “disengage”.", C.Soft, 12, mono = false)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0 to "OFF", 2 to "2 MIN", 5 to "5 MIN", 15 to "15 MIN", 30 to "30 MIN", 60 to "1 HOUR").forEach { (m, l) ->
                Chip(l, sessionMin == m, C.Green) { ChronosEngine.setSessionMin(m) }
            }
        }

        SectionLabel("THIRD EYE · A.I. PROVIDER")
        Text2("Any OpenAI-compatible chatbot works. Pick a preset, paste its key, save.", C.Soft, 12, mono = false)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThirdEyeAI.PROVIDERS.forEach { p ->
                Chip(p.name, provider == p, C.Violet) { url = p.url; model = p.model; saved = false }
            }
        }
        if (provider != null) {
            Spacer(Modifier.height(8.dp))
            Text2(provider.note, C.Soft, 12, mono = false)
            if (provider.keyUrl.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                PillButton("GET A FREE KEY", C.Violet) { open(ctx, Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyUrl))) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text2("ENDPOINT URL", C.Dim, 10, spacing = 2)
        Field(url, { url = it; saved = false }, "https://…/v1", Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text2("MODEL", C.Dim, 10, spacing = 2)
        Field(model, { model = it; saved = false }, "model name", Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text2("API KEY", C.Dim, 10, spacing = 2)
        Field(key, { key = it; saved = false }, "paste key here", Modifier.fillMaxWidth(), password = true)
        Spacer(Modifier.height(8.dp))
        Text2("PERSONA  (who Third Eye believes she is)", C.Dim, 10, spacing = 2)
        Field(persona, { persona = it; saved = false }, "persona", Modifier.fillMaxWidth(), singleLine = false, imeAction = ImeAction.Default)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(if (saved) "✓ SAVED" else "SAVE", C.Green, filled = true, modifier = Modifier.weight(1f)) {
                ChronosEngine.setAi(url, model, key, persona); saved = true
            }
            PillButton(if (testing) "TESTING…" else "TEST", C.Cyan, modifier = Modifier.weight(1f)) {
                if (!testing) {
                    ChronosEngine.setAi(ThirdEyeAI.cleanUrl(url), model, key, persona); saved = true
                    testing = true; testResult = ""
                    ChronosEngine.testAi { testResult = it; testing = false }
                }
            }
            PillButton("RESET PERSONA", C.Amber, modifier = Modifier.weight(1f)) { persona = ThirdEyeAI.DEFAULT_PERSONA; saved = false }
        }
        if (testResult.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            val ok = testResult.startsWith("✓")
            Panel(accent = if (ok) C.Green else C.Red) { Text2(testResult, if (ok) C.Green else C.Red, 12, mono = false) }
        }

        SectionLabel("PHONE SETUP")
        Text2("For rock-solid listening with the screen off, exclude PCL from battery optimisation and install Speech Services by Google (with offline English downloaded).", C.Soft, 12, mono = false)
        Spacer(Modifier.height(8.dp))
        PillButton("ALLOW BACKGROUND (BATTERY)", modifier = Modifier.fillMaxWidth()) {
            // one-tap system dialog; with the screen off Android otherwise cuts the network and the mic
            open(ctx, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:" + ctx.packageName)))
        }
        Spacer(Modifier.height(8.dp))
        PillButton("APP PERMISSIONS", modifier = Modifier.fillMaxWidth()) {
            open(ctx, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)))
        }

        SectionLabel("RESET")
        PillButton("DISENGAGE EVERYTHING", C.Red, modifier = Modifier.fillMaxWidth()) { ChronosEngine.allOff() }
        Spacer(Modifier.height(16.dp))
        Text2("PCL v0.3.1 · ${Commands.all.size} commands", C.Dim, 11)
        Spacer(Modifier.height(24.dp))
    }
}
