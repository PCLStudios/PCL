package com.pcl.chronoslock

import android.Manifest
import android.app.Activity
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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChronosEngine.init(this)
        Ads.start(this)
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
    val view = LocalView.current
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
    // Material You: re-read the wallpaper colours whenever it is switched on or the light/dark mode changes
    LaunchedEffect(Pal.dynamic, Pal.mode) { Pal.applyDynamic(ctx) }
    // status / navigation bars follow the theme
    SideEffect {
        val w = (view.context as? Activity)?.window
        if (w != null) {
            w.statusBarColor = C.Bg.toArgb(); w.navigationBarColor = C.Bg.toArgb()
            val c = WindowCompat.getInsetsController(w, view)
            c.isAppearanceLightStatusBars = Pal.isLight; c.isAppearanceLightNavigationBars = Pal.isLight
        }
    }

    val tab by ChronosEngine.tab.collectAsState()
    val intro by ChronosEngine.showIntro.collectAsState()
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
        AppBackground {
            Column(Modifier.fillMaxSize()) {
                TopBar(tab.title) { scope.launch { drawer.open() } }
                Box(Modifier.fillMaxWidth().height(1.dp).background(C.Line.copy(alpha = 0.6f)))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = tab,
                        transitionSpec = {
                            if (Pal.anim) (fadeIn(tween(260, delayMillis = 60)) + slideInVertically(tween(300)) { it / 18 }) togetherWith fadeOut(tween(120))
                            else fadeIn(tween(1)) togetherWith fadeOut(tween(1))
                        },
                        label = "screen"
                    ) { t ->
                        when (t) {
                            Screen.HOME -> HomeScreen()
                            Screen.EYE -> EyeScreen()
                            Screen.FLOWS -> FlowsScreen()
                            Screen.NOTES -> NotesScreen()
                            Screen.HELP -> HelpScreen()
                            Screen.GUIDE -> GuideScreen()
                            Screen.STYLE -> StyleScreen()
                            Screen.VOICE -> VoiceScreen()
                            Screen.SYSTEM -> SystemScreen()
                            Screen.ABOUT -> AboutScreen()
                        }
                    }
                }
            }
        }
    }
    if (intro) IntroDialog { ChronosEngine.introDone() }
}

@Composable
private fun TopBar(title: String, onMenu: () -> Unit) {
    val locked by ChronosEngine.locked.collectAsState()
    val phase by ChronosEngine.phase.collectAsState()
    val eye by ChronosEngine.thirdEye.collectAsState()
    val accent = C.Cyan
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onMenu), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(22.dp)) {
                for (i in 0..2) {
                    val y = size.height * (0.15f + 0.35f * i)
                    drawLine(accent, Offset(0f, y), Offset(size.width, y), strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                }
            }
        }
        Text2("PCL", C.Cyan, 18, bold = true, spacing = 5, modifier = Modifier.padding(start = 6.dp))
        Text2("/  $title", C.Soft, 13, spacing = 2, modifier = Modifier.padding(start = 8.dp), maxLines = 1)
        Spacer(Modifier.weight(1f))
        val (txt, col) = when {
            phase == Phase.SPEAKING -> "SPEAKING" to C.Violet
            phase == Phase.THINKING -> "THINKING" to C.Amber
            eye -> Names.eye.uppercase(Locale.US).take(10) to C.Violet
            locked -> "ACTIVE" to C.Green
            else -> "STANDBY" to C.Dim
        }
        Box(Modifier.size(8.dp).clip(CircleShape).background(col))
        Text2(txt, col, 11, bold = true, spacing = 2, modifier = Modifier.padding(start = 6.dp, end = 12.dp), maxLines = 1)
    }
}

/** The slide-out side panel. */
@Composable
private fun SidePanel(current: Screen, onPick: (Screen) -> Unit) {
    val locked by ChronosEngine.locked.collectAsState()
    val whisper by ChronosEngine.whisper.collectAsState()
    val eye by ChronosEngine.thirdEye.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Spacer(Modifier.height(6.dp))
        Text2("PCL", C.Cyan, 34, bold = true, spacing = 8)
        Text2("PROJECT CHRONOS LOCK", C.Dim, 11, spacing = 4)
        Spacer(Modifier.height(14.dp))
        Panel(accent = if (locked) C.Green else C.Line) {
            Text2(if (locked) "● CHRONOS LOCK ACTIVE" else "○ STANDBY", if (locked) C.Green else C.Soft, 12, bold = true, spacing = 1)
            Spacer(Modifier.height(4.dp))
            Text2(if (locked) "Commands need no wake word." else "Say “Chronos Lock” to begin.", C.Soft, 12, mono = false)
        }
        Spacer(Modifier.height(12.dp))
        Screen.values().forEachIndexed { i, s ->
            val on = s == current
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp).appear(i).pressable { onPick(s) }.clip(RoundedCornerShape(14.dp))
                    .background(if (on) C.Cyan.copy(alpha = 0.14f) else Color.Transparent)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text2(s.glyph, if (on) C.Cyan else C.Soft, 18, modifier = Modifier.width(30.dp))
                Text2(s.title, if (on) C.Cyan else C.Text, 14, bold = on, spacing = 2, maxLines = 1)
                Spacer(Modifier.weight(1f))
                if (s == Screen.EYE && eye) Text2("LIVE", C.Violet, 10, bold = true, spacing = 1)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text2("QUICK", C.Dim, 11, spacing = 3)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("WHISPER", whisper, C.Violet) { ChronosEngine.toggleWhisper() }
            Chip(if (locked) "DEACTIVATE" else "ACTIVATE", locked, C.Green) { ChronosEngine.toggleLock() }
        }
        Spacer(Modifier.height(14.dp))
        Text2("v1.1.0 · ${Commands.all.size} commands", C.Dim, 11)
    }
}
