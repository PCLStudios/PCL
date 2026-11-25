package com.pcl.chronoslock

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Bg1 = Color(0xFF05070D)
private val Bg2 = Color(0xFF0B1220)
private val Cyan = Color(0xFF22E4FF)
private val Red = Color(0xFFFF4D5E)
private val Violet = Color(0xFFA78BFA)
private val Dim = Color(0xFF3A4660)
private val Mono = FontFamily.Monospace

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChronosEngine.init(this)
        setContent { PclApp() }
    }
}

private fun startChronosService(ctx: Context) =
    ContextCompat.startForegroundService(ctx, Intent(ctx, ChronosService::class.java))

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

    val locked by ChronosEngine.locked.collectAsState()
    val combat by ChronosEngine.combat.collectAsState()
    val quantum by ChronosEngine.quantum.collectAsState()
    val phase by ChronosEngine.phase.collectAsState()
    val heard by ChronosEngine.lastHeard.collectAsState()

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Bg1, Bg2))).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        Text("PCL", color = Cyan, fontSize = 34.sp, fontWeight = FontWeight.Bold, fontFamily = Mono, letterSpacing = 8.sp)
        Text("PROJECT CHRONOS LOCK", color = Dim, fontSize = 11.sp, fontFamily = Mono, letterSpacing = 4.sp)

        Spacer(Modifier.weight(1f))
        LockButton(locked) { ChronosEngine.toggleLock() }
        Spacer(Modifier.height(28.dp))

        Text(
            when (phase) {
                Phase.SPEAKING -> "● SPEAKING"
                Phase.LISTENING -> "● LISTENING"
                Phase.STANDBY -> "○ SAY \"CHRONOS LOCK\""
            },
            color = if (phase == Phase.STANDBY) Dim else Cyan,
            fontFamily = Mono, fontSize = 13.sp, letterSpacing = 2.sp
        )
        Spacer(Modifier.height(6.dp))
        Text(if (heard.isBlank()) " " else "“$heard”", color = Dim, fontFamily = Mono, fontSize = 12.sp, maxLines = 1)

        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ModeCard("COMBAT MODE", Icons.Filled.Warning, Red, combat, Modifier.weight(1f)) { ChronosEngine.triggerCombat() }
            ModeCard("QUANTUM FOCUS", Icons.Filled.Star, Violet, quantum, Modifier.weight(1f)) { ChronosEngine.triggerQuantum() }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun LockButton(locked: Boolean, onClick: () -> Unit) {
    val color = if (locked) Cyan else Dim
    val t = rememberInfiniteTransition(label = "fx")
    val pulse by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart), label = "pulse")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14000, easing = LinearEasing), RepeatMode.Restart), label = "spin")

    Box(
        Modifier.size(270.dp).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            if (locked) {
                drawCircle(color.copy(alpha = (1f - pulse) * 0.35f), radius = r * (0.62f + 0.38f * pulse), style = Stroke(2.dp.toPx()))
            }
            rotate(if (locked) spin else 0f, Offset(center.x, center.y)) {
                drawCircle(
                    color.copy(alpha = 0.8f), radius = r * 0.84f,
                    style = Stroke(3.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 14f)))
                )
            }
            drawCircle(brush = Brush.radialGradient(listOf(color.copy(alpha = if (locked) 0.28f else 0.08f), Color.Transparent)), radius = r * 0.72f)
            drawCircle(color, radius = r * 0.6f, style = Stroke(4.dp.toPx()))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, tint = color, modifier = Modifier.size(64.dp))
            Spacer(Modifier.height(8.dp))
            Text(if (locked) "ENGAGED" else "DISENGAGED", color = color, fontFamily = Mono, fontSize = 12.sp, letterSpacing = 3.sp)
        }
    }
}

@Composable
private fun ModeCard(label: String, icon: ImageVector, accent: Color, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = if (active) accent else Dim
    Column(
        modifier.clip(RoundedCornerShape(18.dp))
            .background(c.copy(alpha = 0.08f))
            .border(1.dp, c.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, null, tint = c, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(8.dp))
        Text(label, color = c, fontFamily = Mono, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Text(if (active) "ACTIVE" else "OFFLINE", color = c.copy(alpha = 0.7f), fontFamily = Mono, fontSize = 10.sp)
    }
}
