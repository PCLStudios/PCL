package com.pcl.chronoslock

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Bg = Color(0xFF05070D)
private val Cyan = Color(0xFF22E4FF)
private val Dim = Color(0xFF3A4660)
private val Soft = Color(0xFF8A97B5)
private val Mono = FontFamily.Monospace

@Composable
fun PclApp() {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) startChronosService(ctx)
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) startChronosService(ctx) else {
            val perms = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
            launcher.launch(perms)
        }
    }
    val tab by ChronosEngine.tab.collectAsState()
    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(Modifier.weight(1f)) {
            when (tab) { 0 -> HomeScreen(); 1 -> HelpScreen(); else -> SettingsScreen() }
        }
        Row(Modifier.fillMaxWidth().background(Color(0xFF080C16)).padding(vertical = 6.dp)) {
            listOf("HOME", "HELP", "VOICE").forEachIndexed { i, l ->
                Text(
                    l, Modifier.weight(1f).clickable { ChronosEngine.tab.value = i }.padding(12.dp),
                    color = if (i == tab) Cyan else Dim, textAlign = TextAlign.Center,
                    fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp
                )
            }
        }
    }
}

@Composable
fun HelpScreen() {
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(24.dp))
            Text("COMMANDS", color = Cyan, fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                "Say “Chronos Lock” and wait for “Listening”, then give a command — or say it all at once, " +
                    "like “Chronos Lock, combat mode”. ${Commands.all.size} commands active.",
                color = Soft, fontSize = 13.sp
            )
        }
        items(Commands.all) { c ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(Cyan.copy(alpha = 0.05f))
                    .border(1.dp, Dim, RoundedCornerShape(14.dp)).padding(14.dp)
            ) {
                Text("“Chronos Lock, ${c.phrase}”", color = Cyan, fontFamily = Mono, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(c.help, color = Soft, fontSize = 12.sp)
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun Action(text: String, onClick: () -> Unit) {
    Text(
        text, Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(14.dp))
            .border(1.dp, Cyan, RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(14.dp),
        color = Cyan, textAlign = TextAlign.Center, fontFamily = Mono, fontSize = 13.sp, letterSpacing = 1.sp
    )
}

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val voices by ChronosEngine.voices.collectAsState()
    val sel by ChronosEngine.voiceName.collectAsState()
    val pitch by ChronosEngine.pitch.collectAsState()
    val rate by ChronosEngine.rate.collectAsState()
    val colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan, inactiveTrackColor = Dim)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Spacer(Modifier.height(4.dp))
        Text("VOICE", color = Cyan, fontFamily = Mono, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 4.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Tap a voice to hear it. ♀ marks likely female voices; “online” ones are Google's neural voices " +
                "and sound the most human.", color = Soft, fontSize = 13.sp
        )
        Spacer(Modifier.height(10.dp))
        voices.forEach { v ->
            Row(
                Modifier.fillMaxWidth().clickable { ChronosEngine.setVoice(v.name) }.padding(vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(v.label, Modifier.weight(1f), color = if (v.name == sel) Cyan else Soft, fontFamily = Mono, fontSize = 12.sp)
                if (v.name == sel) Text("●", color = Cyan)
            }
        }
        if (voices.isEmpty()) Text("No voices found yet — install Google Speech Services below.", color = Soft, fontSize = 12.sp)

        Spacer(Modifier.height(14.dp))
        Text("PITCH  %.2f".format(pitch), color = Soft, fontFamily = Mono, fontSize = 12.sp)
        Slider(pitch, { ChronosEngine.setPitch(it) }, valueRange = 0.7f..1.4f, colors = colors,
            onValueChangeFinished = { ChronosEngine.preview() })
        Text("SPEED  %.2f".format(rate), color = Soft, fontFamily = Mono, fontSize = 12.sp)
        Slider(rate, { ChronosEngine.setRate(it) }, valueRange = 0.7f..1.3f, colors = colors,
            onValueChangeFinished = { ChronosEngine.preview() })

        Action("▶  TEST VOICE") { ChronosEngine.preview() }
        Action("RESET TO NATURAL (1.00 / 1.00)") { ChronosEngine.resetVoice() }
        Action("GET MORE NATURAL VOICES") {
            runCatching {
                ctx.startActivity(Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}
