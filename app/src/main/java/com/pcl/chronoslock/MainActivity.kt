package com.pcl.chronoslock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChronosEngine.init(this)
        setContent { PclApp() }
    }

    // Android only lets PCL open other apps / settings while it is on screen: remember whether it is.
    override fun onStart() { super.onStart(); ChronosEngine.appVisible = true }
    override fun onStop() { ChronosEngine.appVisible = false; super.onStop() }
}

internal fun startChronosService(ctx: Context) =
    ContextCompat.startForegroundService(ctx, Intent(ctx, ChronosService::class.java))

@Composable
fun PclApp() {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) startChronosService(ctx)
    }
    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (granted) startChronosService(ctx) else {
            val perms = buildList {
                add(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
            }.toTypedArray()
            launcher.launch(perms)
        }
    }

    val tab by ChronosEngine.tab.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    BackHandler(enabled = drawer.isOpen || tab != Screen.HOME) {
        if (drawer.isOpen) scope.launch { drawer.close() } else ChronosEngine.tab.value = Screen.HOME
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = C.Bg2, modifier = Modifier.width(300.dp)) {
                SidePanel(tab) { s -> ChronosEngine.tab.value = s; scope.launch { drawer.close() } }
            }
        }
    ) {
        Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.Bg, C.Bg2)))) {
            TopBar(tab.label) { scope.launch { drawer.open() } }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    Screen.HOME -> HomeScreen()
                    Screen.EYE -> EyeScreen()
                    Screen.NOTES -> NotesScreen()
                    Screen.HELP -> HelpScreen()
                    Screen.VOICE -> VoiceScreen()
                    Screen.SYSTEM -> SystemScreen()
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onMenu: () -> Unit) {
    val locked by ChronosEngine.locked.collectAsState()
    val phase by ChronosEngine.phase.collectAsState()
    val eye by ChronosEngine.thirdEye.collectAsState()
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) {
                for (i in 0..2) {
                    val y = size.height * (0.15f + 0.35f * i)
                    drawLine(C.Cyan, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Text2("PCL", C.Cyan, 18, bold = true, spacing = 5, modifier = Modifier.padding(start = 6.dp))
        Text2("/  $title", C.Soft, 13, spacing = 2, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        val (txt, col) = when {
            phase == Phase.SPEAKING -> "SPEAKING" to C.Violet
            phase == Phase.THINKING -> "THINKING" to C.Amber
            eye -> "THIRD EYE" to C.Violet
            locked -> "ENGAGED" to C.Green
            else -> "STANDBY" to C.Dim
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(col))
        Text2(txt, col, 11, bold = true, spacing = 2, modifier = Modifier.padding(start = 6.dp, end = 12.dp))
    }
}

/** The slide-out side panel. */
@Composable
private fun SidePanel(current: Screen, onPick: (Screen) -> Unit) {
    val locked by ChronosEngine.locked.collectAsState()
    val whisper by ChronosEngine.whisper.collectAsState()
    val eye by ChronosEngine.thirdEye.collectAsState()
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Spacer(Modifier.height(10.dp))
        Text2("PCL", C.Cyan, 34, bold = true, spacing = 8)
        Text2("PROJECT CHRONOS LOCK", C.Dim, 11, spacing = 4)
        Spacer(Modifier.height(16.dp))
        Panel(accent = if (locked) C.Green else C.Line) {
            Text2(if (locked) "● CHRONOS LOCK ENGAGED" else "○ STANDBY", if (locked) C.Green else C.Soft, 12, bold = true, spacing = 1)
            Spacer(Modifier.height(4.dp))
            Text2(if (locked) "Commands need no wake word." else "Say “Chronos Lock” to begin.", C.Soft, 12, mono = false)
        }
        Spacer(Modifier.height(14.dp))
        Screen.values().forEach { s ->
            val on = s == current
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) C.Cyan.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable { onPick(s) }.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text2(s.glyph, if (on) C.Cyan else C.Soft, 18, modifier = Modifier.width(30.dp))
                Text2(s.label, if (on) C.Cyan else C.Text, 14, bold = on, spacing = 2)
                Spacer(Modifier.weight(1f))
                if (s == Screen.EYE && eye) Text2("LIVE", C.Violet, 10, bold = true, spacing = 1)
            }
        }
        Spacer(Modifier.weight(1f))
        Text2("QUICK", C.Dim, 11, spacing = 3)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Chip("WHISPER", whisper, C.Violet) { ChronosEngine.toggleWhisper() }
            Chip(if (locked) "DISENGAGE" else "ENGAGE", locked, C.Green) { ChronosEngine.toggleLock() }
        }
        Spacer(Modifier.height(14.dp))
        Text2("v0.3.1 · ${Commands.all.size} commands", C.Dim, 11)
    }
}
