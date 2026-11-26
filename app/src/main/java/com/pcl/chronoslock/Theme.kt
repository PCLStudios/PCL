package com.pcl.chronoslock

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The user's look: mode, accent, font, Material You, glass, animations. Compose state, so every screen updates live. */
object Pal {
    class Accent(val name: String, val color: Color)
    class Font(val name: String, val family: FontFamily)

    val MODES = listOf("dark" to "DARK", "amoled" to "AMOLED BLACK", "light" to "LIGHT")
    val ACCENTS = listOf(
        Accent("CYAN", Color(0xFF22E4FF)), Accent("SKY", Color(0xFF4DA3FF)), Accent("VIOLET", Color(0xFFA78BFA)),
        Accent("ROSE", Color(0xFFFF5C8A)), Accent("CORAL", Color(0xFFFF7A59)), Accent("AMBER", Color(0xFFFFB84D)),
        Accent("LIME", Color(0xFFB6F24D)), Accent("MINT", Color(0xFF5EEAD4))
    )
    val FONTS = listOf(
        Font("TECH MONO", FontFamily.Monospace),
        Font("CLEAN", FontFamily.SansSerif),
        Font("CONDENSED", FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))),
        Font("LIGHT", FontFamily(Typeface.create("sans-serif-light", Typeface.NORMAL))),
        Font("SERIF", FontFamily.Serif),
        Font("CASUAL", FontFamily(Typeface.create("casual", Typeface.NORMAL))),
        Font("SCRIPT", FontFamily.Cursive)
    )

    var mode by mutableStateOf("dark")
    var accentIdx by mutableIntStateOf(0)
    var fontIdx by mutableIntStateOf(0)
    var glass by mutableStateOf(false)
    var dynamic by mutableStateOf(false)
    var anim by mutableStateOf(true)

    // Material You palette (filled by applyDynamic on Android 12+)
    var dPrimary by mutableStateOf(Color.Unspecified)
    var dSurface by mutableStateOf(Color.Unspecified)
    var dSurface2 by mutableStateOf(Color.Unspecified)
    var dPanel by mutableStateOf(Color.Unspecified)
    var dLine by mutableStateOf(Color.Unspecified)
    var dText by mutableStateOf(Color.Unspecified)
    var dSoft by mutableStateOf(Color.Unspecified)

    private var prefs: SharedPreferences? = null
    val dynamicSupported get() = Build.VERSION.SDK_INT >= 31
    val isLight get() = mode == "light"

    fun init(p: SharedPreferences) {
        prefs = p
        mode = p.getString("th_mode", "dark") ?: "dark"
        accentIdx = p.getInt("th_accent", 0).coerceIn(0, ACCENTS.lastIndex)
        fontIdx = p.getInt("th_font", 0).coerceIn(0, FONTS.lastIndex)
        glass = p.getBoolean("th_glass", false)
        dynamic = p.getBoolean("th_dynamic", false)
        anim = p.getBoolean("th_anim", true)
    }

    private fun save() {
        prefs?.edit()?.putString("th_mode", mode)?.putInt("th_accent", accentIdx)?.putInt("th_font", fontIdx)
            ?.putBoolean("th_glass", glass)?.putBoolean("th_dynamic", dynamic)?.putBoolean("th_anim", anim)?.apply()
    }
    fun setMode(m: String) { mode = m; save() }
    fun setAccent(i: Int) { accentIdx = i; dynamic = false; save() }
    fun setFont(i: Int) { fontIdx = i; save() }
    fun setGlass(b: Boolean) { glass = b; save() }
    fun setDynamic(b: Boolean) { dynamic = b && dynamicSupported; save() }
    fun setAnim(b: Boolean) { anim = b; save() }

    /** Reads the wallpaper colours Android generated (Android 12+). */
    fun applyDynamic(ctx: Context) {
        if (!dynamic || !dynamicSupported) return
        runCatching {
            val cs = if (isLight) dynamicLightColorScheme(ctx) else dynamicDarkColorScheme(ctx)
            dPrimary = cs.primary; dSurface = cs.surface; dSurface2 = cs.surfaceContainer; dPanel = cs.surfaceContainerHigh
            dLine = cs.outlineVariant; dText = cs.onSurface; dSoft = cs.onSurfaceVariant
        }
    }

    internal val dyn get() = dynamic && dSurface != Color.Unspecified && mode != "amoled"
    fun font(): FontFamily = FONTS[fontIdx].family
    fun body(): FontFamily = if (fontIdx == 0) FontFamily.Default else FONTS[fontIdx].family
    fun accent(): Color {
        val base = if (dynamic && dPrimary != Color.Unspecified) dPrimary else ACCENTS[accentIdx].color
        return if (isLight) lerp(base, Color.Black, 0.4f) else base
    }
}

/** Colours. Every value reads live theme state, so changing the theme recolours the whole app at once. */
object C {
    val Bg: Color get() = when {
        Pal.mode == "amoled" -> Color.Black
        Pal.dyn -> Pal.dSurface
        Pal.isLight -> Color(0xFFF3F5FA)
        else -> Color(0xFF05070D)
    }
    val Bg2: Color get() = when {
        Pal.mode == "amoled" -> Color(0xFF05060A)
        Pal.dyn -> Pal.dSurface2
        Pal.isLight -> Color(0xFFE6EAF4)
        else -> Color(0xFF0B1220)
    }
    val Panel: Color get() = when {
        Pal.mode == "amoled" -> Color(0xFF0A0C12)
        Pal.dyn -> Pal.dPanel
        Pal.isLight -> Color(0xFFFFFFFF)
        else -> Color(0xFF0D1424)
    }
    val Line: Color get() = when {
        Pal.dyn -> Pal.dLine
        Pal.isLight -> Color(0xFFCBD3E4)
        Pal.mode == "amoled" -> Color(0xFF1A2236)
        else -> Color(0xFF1E2A44)
    }
    val Text: Color get() = when {
        Pal.dyn -> Pal.dText
        Pal.isLight -> Color(0xFF111827)
        else -> Color(0xFFE6ECFF)
    }
    val Soft: Color get() = when {
        Pal.dyn -> Pal.dSoft
        Pal.isLight -> Color(0xFF4B5675)
        else -> Color(0xFF8A97B5)
    }
    val Dim: Color get() = if (Pal.isLight) Color(0xFF8E99B3) else Color(0xFF3A4660)
    /** The accent colour (name kept from the old cyan-only look). */
    val Cyan: Color get() = Pal.accent()
    val Green: Color get() = if (Pal.isLight) Color(0xFF0E9F6E) else Color(0xFF38F5A8)
    val Red: Color get() = if (Pal.isLight) Color(0xFFD92D3F) else Color(0xFFFF4D5E)
    val Violet: Color get() = if (Pal.isLight) Color(0xFF7C5CE0) else Color(0xFFA78BFA)
    val Amber: Color get() = if (Pal.isLight) Color(0xFFC77700) else Color(0xFFFFB84D)
}

val Mono: FontFamily get() = Pal.font()

// ============================ motion helpers ============================

/** Soft spring "press" feedback + a light haptic tick. Put it FIRST in a modifier chain so the whole control scales. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val haptic = LocalHapticFeedback.current
    val s by animateFloatAsState(
        if (pressed && Pal.anim) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "press"
    )
    this.graphicsLayer { scaleX = s; scaleY = s }
        .clickable(src, null, enabled = enabled) { runCatching { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }; onClick() }
}

/** Fade + rise on first show. [index] staggers a list of items. */
fun Modifier.appear(index: Int = 0): Modifier = composed {
    val a = remember { Animatable(if (Pal.anim) 0f else 1f) }
    LaunchedEffect(Unit) { if (Pal.anim) a.animateTo(1f, tween(420, delayMillis = (index * 60).coerceAtMost(420), easing = FastOutSlowInEasing)) }
    this.graphicsLayer { alpha = a.value; translationY = (1f - a.value) * 30f }
}

/** Card surface: flat, or frosted glass when the user turned glass on. */
fun Modifier.card(shape: Shape, accent: Color = C.Line, tint: Float = 0.07f, borderAlpha: Float? = null): Modifier {
    val strong = accent != C.Line
    return if (Pal.glass) {
        val hi = if (Pal.isLight) 0.75f else 0.14f
        val lo = if (Pal.isLight) 0.35f else 0.04f
        val edge = if (Pal.isLight) 0.95f else 0.5f
        this.clip(shape)
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = hi), Color.White.copy(alpha = lo))))
            .background(accent.copy(alpha = if (strong) tint * 1.6f else 0f))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = edge), accent.copy(alpha = if (strong) 0.5f else 0.1f))), shape)
    } else {
        this.clip(shape).background(accent.copy(alpha = tint).compositeOver(C.Panel))
            .border(1.dp, accent.copy(alpha = borderAlpha ?: if (strong) 0.55f else 1f), shape)
    }
}

/** App background: gradient, plus slow drifting colour glows behind the glass when glass is on. */
@Composable
fun AppBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val glow = Pal.glass
    val t = rememberInfiniteTransition(label = "bg")
    val drift by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse), label = "drift")
    val a1 = C.Cyan
    val a2 = C.Violet
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(C.Bg, C.Bg2)))) {
        if (glow) Canvas(Modifier.fillMaxSize()) {
            val r = size.maxDimension * 0.55f
            val c1 = Offset(size.width * (0.15f + 0.25f * drift), size.height * (0.12f + 0.1f * drift))
            val c2 = Offset(size.width * (0.9f - 0.25f * drift), size.height * (0.62f - 0.12f * drift))
            drawCircle(Brush.radialGradient(listOf(a1.copy(alpha = 0.32f), Color.Transparent), center = c1, radius = r), radius = r, center = c1)
            drawCircle(Brush.radialGradient(listOf(a2.copy(alpha = 0.26f), Color.Transparent), center = c2, radius = r), radius = r, center = c2)
        }
        content()
    }
}

// ============================ components ============================

@Composable
fun ScreenTitle(title: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 12.dp).appear()) {
        Text2(title, C.Cyan, 22, bold = true, spacing = 4)
        if (sub != null) { Spacer(Modifier.height(4.dp)); Text2(sub, C.Soft, 13, mono = false) }
    }
}

/** Tiny wrapper so call sites stay short. mono=false is the readable body font. */
@Composable
fun Text2(text: String, color: Color, size: Int, bold: Boolean = false, spacing: Int = 0, mono: Boolean = true, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    androidx.compose.material3.Text(
        text, modifier, color = color, fontSize = size.sp, fontFamily = if (mono) Pal.font() else Pal.body(),
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, letterSpacing = spacing.sp, textAlign = align, maxLines = maxLines
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, accent: Color = C.Line, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val a by animateColorAsState(accent, tween(300), label = "panelAccent")
    Column(modifier.fillMaxWidth().card(shape, a).padding(14.dp), content = content)
}

@Composable
fun PillButton(text: String, accent: Color = C.Cyan, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val a by animateColorAsState(accent, tween(300), label = "pillAccent")
    Box(
        modifier.pressable(onClick = onClick).clip(shape).background(if (filled) a.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, a, shape).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text2(text, a, 13, bold = true, spacing = 1, align = TextAlign.Center) }
}

@Composable
fun Chip(text: String, on: Boolean, accent: Color = C.Cyan, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val c by animateColorAsState(if (on) accent else C.Soft, tween(250), label = "chipC")
    val bg by animateColorAsState(if (on) accent.copy(alpha = 0.18f) else Color.Transparent, tween(250), label = "chipBg")
    val bd by animateColorAsState(if (on) accent else C.Dim, tween(250), label = "chipBd")
    Box(
        Modifier.pressable(onClick = onClick).clip(shape).background(bg).border(1.dp, bd, shape).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text2(text, c, 12, bold = on, spacing = 1) }
}

@Composable
fun SectionLabel(text: String, count: Int? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text2(text, C.Cyan, 12, bold = true, spacing = 3)
        if (count != null) { Spacer(Modifier.padding(start = 8.dp)); Text2("$count", C.Dim, 11) }
        Box(Modifier.padding(start = 10.dp).weight(1f).height(1.dp).background(C.Line))
    }
}

@Composable
fun Field(
    value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier,
    password: Boolean = false, singleLine: Boolean = true, imeAction: ImeAction = ImeAction.Done, onSubmit: (() -> Unit)? = null
) {
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value, onChange,
        modifier = modifier.clip(shape).background(if (Pal.glass) C.Text.copy(alpha = 0.06f) else C.Panel).border(1.dp, C.Line, shape).padding(12.dp),
        textStyle = TextStyle(color = C.Text, fontFamily = Pal.font(), fontSize = 13.sp),
        cursorBrush = SolidColor(C.Cyan), singleLine = singleLine,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onSend = { onSubmit?.invoke() }, onDone = { onSubmit?.invoke() }),
        decorationBox = { inner ->
            Box { if (value.isEmpty()) Text2(hint, C.Dim, 13); inner() }
        }
    )
}

@Composable
fun Row2(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically, content = content)
