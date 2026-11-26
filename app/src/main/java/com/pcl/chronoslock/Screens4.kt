package com.pcl.chronoslock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

/** Small pop-up used on Home (EDIT mode) to rename a mode or the chatbot. */
@Composable
fun RenameDialog(which: String, onDismiss: () -> Unit) {
    val current = when (which) { "combat" -> Names.combat; "quantum" -> Names.quantum; else -> Names.eye }
    var text by remember { mutableStateOf(current) }
    var err by remember { mutableStateOf("") }
    val title = when (which) { "combat" -> "RENAME COMBAT MODE"; "quantum" -> "RENAME QUANTUM FOCUS"; else -> "RENAME THE CHATBOT" }
    Dialog(onDismissRequest = onDismiss) {
        Panel(accent = C.Cyan) {
            Text2(title, C.Cyan, 13, bold = true, spacing = 2)
            Spacer(Modifier.height(6.dp))
            Text2("Name it anything. Say that name and it happens, and she answers using it.", C.Soft, 12, mono = false)
            Spacer(Modifier.height(10.dp))
            Field(text, { text = it; err = "" }, "new name", Modifier.fillMaxWidth())
            if (err.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text2(err, C.Red, 12, mono = false) }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("CANCEL", C.Soft, modifier = Modifier.weight(1f)) { onDismiss() }
                PillButton("SAVE", C.Green, filled = true, modifier = Modifier.weight(1f)) {
                    val e = when (which) {
                        "combat" -> Names.saveAll(text, Names.quantum, Names.eye)
                        "quantum" -> Names.saveAll(Names.combat, text, Names.eye)
                        else -> Names.saveAll(Names.combat, Names.quantum, text)
                    }
                    if (e == null) onDismiss() else err = e
                }
            }
        }
    }
}

// ============================ STYLE ============================

@Composable
fun StyleScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenTitle("STYLE", "Make PCL yours: names, colours, fonts and glass.")

        // ---- names ----
        SectionLabel("NAMES")
        var c by remember { mutableStateOf(Names.combat) }
        var q by remember { mutableStateOf(Names.quantum) }
        var e by remember { mutableStateOf(Names.eye) }
        var msg by remember { mutableStateOf("") }
        var ok by remember { mutableStateOf(false) }
        Panel {
            Text2("Rename anything. Whatever you type is what you say out loud, and she replies with it too. Example: name Combat Mode “Engage Lol”, then say “Engage Lol” and your combat program runs.", C.Soft, 12, mono = false)
            Spacer(Modifier.height(10.dp))
            Text2("COMBAT MODE", C.Dim, 10, spacing = 2)
            Field(c, { c = it; msg = "" }, Names.D_COMBAT, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text2("QUANTUM FOCUS", C.Dim, 10, spacing = 2)
            Field(q, { q = it; msg = "" }, Names.D_QUANTUM, Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Text2("CHATBOT (default “Chatbot”, “third eye” always works too)", C.Dim, 10, spacing = 2)
            Field(e, { e = it; msg = "" }, Names.D_EYE, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(if (ok) "✓ SAVED" else "SAVE NAMES", C.Green, filled = true, modifier = Modifier.weight(1f)) {
                    val r = Names.saveAll(c, q, e)
                    ok = r == null; msg = r ?: ""
                    if (ok) { c = Names.combat; q = Names.quantum; e = Names.eye }
                }
                PillButton("DEFAULTS", C.Amber, modifier = Modifier.weight(1f)) {
                    c = Names.D_COMBAT; q = Names.D_QUANTUM; e = Names.D_EYE
                    ok = Names.saveAll(c, q, e) == null; msg = ""
                }
            }
            if (msg.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text2(msg, C.Red, 12, mono = false) }
            Spacer(Modifier.height(8.dp))
            Text2("Tip: pick names the speech recogniser hears clearly. After saving, glance at HEARD on Home to see how she writes what you say.", C.Dim, 11, mono = false)
        }

        // ---- theme ----
        SectionLabel("THEME")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pal.MODES.forEach { (id, label) -> Chip(label, Pal.mode == id, C.Cyan) { Pal.chooseMode(id) } }
        }

        SectionLabel("MATERIAL YOU")
        Panel(accent = if (Pal.dynamic) C.Green else C.Line) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text2("DYNAMIC COLOUR", if (Pal.dynamic) C.Green else C.Text, 13, bold = true, spacing = 1)
                    Spacer(Modifier.height(4.dp))
                    Text2(if (Pal.dynamicSupported) "Colours the whole app from your wallpaper, like Google's own apps." else "Needs Android 12 or newer. Your phone is older, so pick an accent colour below instead.", C.Soft, 12, mono = false)
                }
                Spacer(Modifier.padding(6.dp))
                if (Pal.dynamicSupported) Chip(if (Pal.dynamic) "ON" else "OFF", Pal.dynamic, C.Green) { Pal.chooseDynamic(!Pal.dynamic) }
            }
        }

        SectionLabel("ACCENT COLOUR")
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Pal.ACCENTS.forEachIndexed { i, a ->
                val sel = !Pal.dynamic && Pal.accentIdx == i
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(a.color)
                        .border(if (sel) 3.dp else 1.dp, if (sel) C.Text else C.Line, CircleShape)
                        .clickable { Pal.setAccent(i) },
                    contentAlignment = Alignment.Center
                ) { if (sel) Text2("✓", Color.Black, 18, bold = true) }
            }
        }

        SectionLabel("FONT")
        Pal.FONTS.forEachIndexed { i, f ->
            val sel = Pal.fontIdx == i
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                    .background(if (sel) C.Cyan.copy(alpha = 0.12f) else Color.Transparent)
                    .border(1.dp, if (sel) C.Cyan else C.Line, RoundedCornerShape(12.dp))
                    .clickable { Pal.setFont(i) }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text(f.name, color = if (sel) C.Cyan else C.Text, fontFamily = f.family, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                androidx.compose.material3.Text("Chronos Lock engaged", color = C.Soft, fontFamily = f.family, fontSize = 13.sp)
            }
        }

        SectionLabel("EFFECTS")
        Panel(accent = if (Pal.glass) C.Cyan else C.Line) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text2("GLASS UI", if (Pal.glass) C.Cyan else C.Text, 13, bold = true, spacing = 1)
                    Spacer(Modifier.height(4.dp))
                    Text2("Frosted, see-through cards with soft glowing light behind them.", C.Soft, 12, mono = false)
                }
                Spacer(Modifier.padding(6.dp))
                Chip(if (Pal.glass) "ON" else "OFF", Pal.glass, C.Cyan) { Pal.chooseGlass(!Pal.glass) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Panel(accent = if (Pal.anim) C.Green else C.Line) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text2("ANIMATIONS", if (Pal.anim) C.Green else C.Text, 13, bold = true, spacing = 1)
                    Spacer(Modifier.height(4.dp))
                    Text2("Springy presses, fades and slides. Turn off for a calmer app or to save battery.", C.Soft, 12, mono = false)
                }
                Spacer(Modifier.padding(6.dp))
                Chip(if (Pal.anim) "ON" else "OFF", Pal.anim, C.Green) { Pal.chooseAnim(!Pal.anim) }
            }
        }

        SectionLabel("PREVIEW")
        Panel(accent = C.Cyan) {
            Text2("CHRONOS LOCK", C.Cyan, 16, bold = true, spacing = 3)
            Spacer(Modifier.height(4.dp))
            Text2("This is how cards and text look with your choices.", C.Soft, 13, mono = false)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("BUTTON", C.Cyan, filled = true) {}
                Chip("CHIP", true, C.Green) {}
                Chip("OFF", false) {}
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
