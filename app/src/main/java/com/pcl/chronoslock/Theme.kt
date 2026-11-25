package com.pcl.chronoslock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared look: dark glass panels, cyan accent, monospace labels. */
object C {
    val Bg = Color(0xFF05070D)
    val Bg2 = Color(0xFF0B1220)
    val Panel = Color(0xFF0D1424)
    val Line = Color(0xFF1E2A44)
    val Cyan = Color(0xFF22E4FF)
    val Green = Color(0xFF38F5A8)
    val Red = Color(0xFFFF4D5E)
    val Violet = Color(0xFFA78BFA)
    val Amber = Color(0xFFFFB84D)
    val Dim = Color(0xFF3A4660)
    val Soft = Color(0xFF8A97B5)
    val Text = Color(0xFFE6ECFF)
}
val Mono = FontFamily.Monospace

@Composable
fun ScreenTitle(title: String, sub: String? = null) {
    Column(Modifier.padding(bottom = 12.dp)) {
        Text2(title, C.Cyan, 22, bold = true, spacing = 4)
        if (sub != null) { Spacer(Modifier.height(4.dp)); Text2(sub, C.Soft, 13, mono = false) }
    }
}

/** Tiny wrapper so call sites stay short. */
@Composable
fun Text2(text: String, color: Color, size: Int, bold: Boolean = false, spacing: Int = 0, mono: Boolean = true, modifier: Modifier = Modifier, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    androidx.compose.material3.Text(
        text, modifier, color = color, fontSize = size.sp, fontFamily = if (mono) Mono else FontFamily.Default,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, letterSpacing = spacing.sp, textAlign = align, maxLines = maxLines
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, accent: Color = C.Line, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(accent.copy(alpha = 0.07f).compositeOver(C.Panel))
            .border(1.dp, accent.copy(alpha = if (accent == C.Line) 1f else 0.55f), shape).padding(14.dp),
        content = content
    )
}

@Composable
fun PillButton(text: String, accent: Color = C.Cyan, filled: Boolean = false, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier.clip(shape).background(if (filled) accent.copy(alpha = 0.22f) else Color.Transparent)
            .border(1.dp, accent, shape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) { Text2(text, accent, 13, bold = true, spacing = 1, align = TextAlign.Center) }
}

@Composable
fun Chip(text: String, on: Boolean, accent: Color = C.Cyan, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    val c = if (on) accent else C.Soft
    Box(
        Modifier.clip(shape).background(if (on) accent.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, if (on) accent else C.Dim, shape).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
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
        modifier = modifier.clip(shape).background(C.Panel).border(1.dp, C.Line, shape).padding(12.dp),
        textStyle = TextStyle(color = C.Text, fontFamily = Mono, fontSize = 13.sp),
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
