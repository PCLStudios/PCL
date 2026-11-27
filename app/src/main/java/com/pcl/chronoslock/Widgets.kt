package com.pcl.chronoslock

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.BatteryManager
import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the Home screen shows. Every button on Home is one of these widgets: add, remove, reorder them in EDIT mode. */
class WDef(val id: String, val glyph: String, val title: () -> String, val about: String)

class WState(val sub: String, val active: Boolean, val accent: Color)

object HomeLayout {
    val DEFAULT = listOf("lock", "torch", "combat", "quantum", "whisper", "eye")
    val ids = mutableStateListOf<String>()
    var editing by mutableStateOf(false)
    private var prefs: SharedPreferences? = null

    fun load(p: SharedPreferences) {
        prefs = p
        val saved = p.getString("home_widgets", null)
        val list = if (saved == null) DEFAULT else saved.split(",").filter { it.isNotBlank() && Widgets.find(it) != null }
        ids.clear(); ids.addAll(list)
    }
    private fun save() { prefs?.edit()?.putString("home_widgets", ids.joinToString(","))?.apply() }
    fun remove(id: String) { ids.remove(id); save() }
    /** @return false when the free limit of widgets is reached (a Power Pass lifts it). */
    fun add(id: String): Boolean {
        if (id in ids) return true
        if (ids.size >= Pass.FREE_WIDGETS && !Pass.active) return false
        ids.add(id); save(); return true
    }
    fun move(id: String, dir: Int) {
        val i = ids.indexOf(id); val j = i + dir
        if (i < 0 || j < 0 || j >= ids.size) return
        val t = ids[i]; ids[i] = ids[j]; ids[j] = t; save()
    }
    fun reset() { ids.clear(); ids.addAll(DEFAULT); save() }
}

object Widgets {
    val ALL = listOf(
        WDef("lock", "◎", { "CHRONOS LOCK" }, "Activate or deactivate Chronos Lock with one tap, like the big button."),
        WDef("combat", "◆", { Names.combat.uppercase(Locale.US) }, "Turns mode 1 on or off and runs its PROGRAM."),
        WDef("quantum", "★", { Names.quantum.uppercase(Locale.US) }, "Turns mode 2 on or off and runs its PROGRAM."),
        WDef("whisper", "◌", { "WHISPER" }, "Listen harder for quiet speech."),
        WDef("eye", "◉", { Names.eye.uppercase(Locale.US) }, "Open or close the online chatbot."),
        WDef("torch", "☼", { "FLASHLIGHT" }, "Phone flashlight."),
        WDef("play", "▶", { "PLAY / PAUSE" }, "Play or pause the current music."),
        WDef("next", "»", { "NEXT SONG" }, "Skip to the next track."),
        WDef("prev", "«", { "PREVIOUS" }, "Go back one track."),
        WDef("volup", "+", { "VOLUME UP" }, "Raise media volume."),
        WDef("voldown", "−", { "VOLUME DOWN" }, "Lower media volume."),
        WDef("mute", "∅", { "MUTE" }, "Mute or unmute media."),
        WDef("timer5", "◔", { "5 MIN TIMER" }, "Start a five minute timer."),
        WDef("hush", "■", { "HUSH" }, "Stop her talking."),
        WDef("keepmusic", "♪", { "KEEP MUSIC" }, "Try to keep music playing while she listens."),
        WDef("notes", "✎", { "NOTES" }, "Open your notes."),
        WDef("programs", "◈", { "PROGRAMS" }, "Open the program editor."),
        WDef("style", "✦", { "STYLE" }, "Themes, colours, fonts and names."),
        WDef("guide", "?", { "GUIDE" }, "Setup guide and help."),
        WDef("battery", "▮", { "BATTERY" }, "Battery level. Tap to hear it."),
        WDef("clock", "◷", { "TIME" }, "Clock. Tap to hear it.")
    )
    fun find(id: String) = ALL.firstOrNull { it.id == id }

    private fun batteryPct(ctx: Context): Int {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        return if (level < 0) -1 else level * 100 / scale.coerceAtLeast(1)
    }

    /** Live look of one widget. Collected here so tiles recolour the instant something changes. */
    @Composable
    fun state(id: String): WState {
        val ctx = LocalContext.current
        val locked by ChronosEngine.locked.collectAsState()
        val combat by ChronosEngine.combat.collectAsState()
        val quantum by ChronosEngine.quantum.collectAsState()
        val whisper by ChronosEngine.whisper.collectAsState()
        val eye by ChronosEngine.thirdEye.collectAsState()
        val torch by ChronosEngine.torch.collectAsState()
        val keep by ChronosEngine.keepMusic.collectAsState()
        var tick by remember { mutableStateOf(0) }
        if (id == "battery" || id == "clock") LaunchedEffect(id) { while (true) { delay(15_000); tick++ } }
        return when (id) {
            "lock" -> WState(if (locked) "ACTIVE" else "STANDBY", locked, C.Green)
            "combat" -> WState(if (combat) "ACTIVE" else if (locked) "OFFLINE" else "LOCKED OUT", combat, C.Red)
            "quantum" -> WState(if (quantum) "ACTIVE" else if (locked) "OFFLINE" else "LOCKED OUT", quantum, C.Violet)
            "whisper" -> WState(if (whisper) "ON" else "OFF", whisper, C.Violet)
            "eye" -> WState(if (eye) "LIVE" else "OFF", eye, C.Cyan)
            "torch" -> WState(if (torch) "ON" else "OFF", torch, C.Amber)
            "keepmusic" -> WState(if (keep) "ON" else "OFF", keep, C.Green)
            "battery" -> { tick; WState(batteryPct(ctx).let { if (it < 0) "—" else "$it%" }, false, C.Green) }
            "clock" -> { tick; WState(SimpleDateFormat("h:mm a", Locale.US).format(Date()), false, C.Cyan) }
            else -> WState("TAP", false, C.Cyan)
        }
    }

    fun tap(ctx: Context, id: String) {
        val am = ctx.getSystemService(AudioManager::class.java)
        when (id) {
            "lock" -> ChronosEngine.toggleLock()
            "combat" -> ChronosEngine.toggleCombat()
            "quantum" -> ChronosEngine.toggleQuantum()
            "whisper" -> ChronosEngine.toggleWhisper()
            "eye" -> ChronosEngine.toggleEye()
            "torch" -> ChronosEngine.toggleTorch()
            "play" -> media(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, "")
            "next" -> media(ctx, KeyEvent.KEYCODE_MEDIA_NEXT, "")
            "prev" -> media(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "")
            "volup" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
            "voldown" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            "mute" -> am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            "timer5" -> { ChronosEngine.setTimer(5 * 60_000L, ""); ChronosEngine.announce("Timer set for five minutes") }
            "hush" -> ChronosEngine.hush()
            "keepmusic" -> ChronosEngine.setKeepMusic(!ChronosEngine.keepMusic.value)
            "notes" -> ChronosEngine.tab.value = Screen.NOTES
            "programs" -> ChronosEngine.tab.value = Screen.FLOWS
            "style" -> ChronosEngine.tab.value = Screen.STYLE
            "guide" -> ChronosEngine.tab.value = Screen.GUIDE
            "battery" -> ChronosEngine.announce("Battery is at ${batteryPct(ctx)} percent")
            "clock" -> ChronosEngine.announce("The time is " + SimpleDateFormat("h:mm a", Locale.US).format(Date()))
        }
    }
}

@Composable
private fun wiggle(on: Boolean): Float {
    if (!on || !Pal.anim) return 0f
    val t = rememberInfiniteTransition(label = "wiggle")
    val v by t.animateFloat(-1.1f, 1.1f, infiniteRepeatable(tween(140), RepeatMode.Reverse), label = "w")
    return v
}

/** One widget tile. In edit mode it wiggles and shows remove / move controls. */
@Composable
fun WidgetTile(id: String, index: Int, count: Int, modifier: Modifier, onRename: ((String) -> Unit)?) {
    val def = Widgets.find(id) ?: return
    val ctx = LocalContext.current
    val st = Widgets.state(id)
    val editing = HomeLayout.editing
    val c by animateColorAsState(if (st.active) st.accent else C.Dim, tween(300), label = "tileC")
    val shape = RoundedCornerShape(20.dp)
    val glow by animateFloatAsState(if (st.active) 14f else 0f, tween(400), label = "glow")
    val rot = wiggle(editing)
    val renamable = id == "combat" || id == "quantum" || id == "eye"
    val unnamed = (id == "combat" && Names.isDefault(Names.combat, Names.D_COMBAT)) || (id == "quantum" && Names.isDefault(Names.quantum, Names.D_QUANTUM))
    Box(modifier.graphicsLayer { rotationZ = rot }.shadow(glow.dp, shape, clip = false, ambientColor = c, spotColor = c)) {
        Column(
            Modifier.fillMaxWidth()
                .pressable { if (editing) { if (renamable) onRename?.invoke(id) } else Widgets.tap(ctx, id) }
                .card(shape, c, tint = if (st.active) 0.18f else 0.06f, borderAlpha = 0.7f)
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text2(def.glyph, c, 26)
            Spacer(Modifier.height(6.dp))
            Text2(def.title(), c, 11, bold = true, spacing = 1, maxLines = 1)
            Text2(if (editing && renamable) "TAP TO RENAME" else if (unnamed && !st.active) "NAME IT ✎" else st.sub, c.copy(alpha = 0.7f), 10)
            if (editing) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniBtn("‹", index > 0) { HomeLayout.move(id, -1) }
                    MiniBtn("›", index < count - 1) { HomeLayout.move(id, 1) }
                }
            }
        }
        if (unnamed && !editing) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(28.dp).clip(CircleShape).border(1.dp, c, CircleShape)
                    .clickable { onRename?.invoke(id) },
                contentAlignment = Alignment.Center
            ) { Text2("✎", c, 14, bold = true) }
        }
        if (editing) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(26.dp).clip(CircleShape).background(C.Red)
                    .clickable { HomeLayout.remove(id) },
                contentAlignment = Alignment.Center
            ) { Text2("✕", Color.White, 13, bold = true) }
        }
    }
}

@Composable
private fun MiniBtn(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).border(1.dp, if (enabled) C.Soft else C.Dim, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text2(glyph, if (enabled) C.Text else C.Dim, 16, bold = true) }
}
