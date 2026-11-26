package com.pcl.chronoslock

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

const val REPO_URL = "https://github.com/mindofzexer-lang/pcl-chronos-lock"

private fun openUrl(ctx: android.content.Context, url: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

internal fun sharePcl(ctx: android.content.Context) {
    val text = "I'm using PCL (Project Chronos Lock): a voice assistant that listens for \"Chronos Lock\", runs my own automations, " +
        "keeps my music playing and has a built-in A.I. chatbot. Free and open source: $REPO_URL"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    runCatching { ctx.startActivity(Intent.createChooser(send, "Share PCL").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

@Composable
private fun Body(t: String) { Text2(Names.rewrite(t), C.Soft, 13, mono = false); Spacer(Modifier.height(6.dp)) }

@Composable
private fun StepLine(n: Int, t: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text2("$n", C.Cyan, 14, bold = true, modifier = Modifier.padding(end = 10.dp))
        Text2(Names.rewrite(t), C.Text, 13, mono = false)
    }
}

/** A section that opens and closes with a smooth animation. */
@Composable
private fun Fold(title: String, accent: Color, startOpen: Boolean = false, content: @Composable () -> Unit) {
    var open by remember { mutableStateOf(startOpen) }
    Box(Modifier.padding(bottom = 10.dp)) {
        Panel(accent = if (open) accent else C.Line, modifier = Modifier.animateContentSize(tween(250))) {
            Row(Modifier.fillMaxWidth().pressable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                Text2(title, if (open) accent else C.Text, 13, bold = true, spacing = 1, modifier = Modifier.weight(1f))
                Text2(if (open) "▾" else "▸", if (open) accent else C.Soft, 16)
            }
            AnimatedVisibility(open, enter = fadeIn(tween(200)) + expandVertically(tween(250)), exit = fadeOut(tween(120)) + shrinkVertically(tween(200))) {
                Column(Modifier.padding(top = 10.dp)) { content() }
            }
        }
    }
}

// ============================ GUIDE ============================

@Composable
fun GuideScreen() {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenTitle("GUIDE", "Everything for beginners, in plain words. Tap a title to open it.")

        Fold("1 · QUICK START", C.Green, startOpen = true) {
            StepLine(1, "Allow the microphone when Android asks.")
            StepLine(2, "Say “Chronos Lock”. The big button turns green: she is listening for commands.")
            StepLine(3, "Say a command, for example “what time is it”, “set a timer for five minutes” or “combat mode”. No wake word needed while it is green.")
            StepLine(4, "Say “disengage” (or tap the button) when you are done.")
            Spacer(Modifier.height(6.dp))
            Body("Tip: open COMMANDS in the menu to see everything she understands.")
        }

        Fold("2 · SET UP THE CHATBOT (THIRD EYE) · FOR TOTAL BEGINNERS", C.Violet) {
            Body("The chatbot is an online A.I. you can talk to, like ChatGPT. It runs on the internet, not on your phone, so it needs a free “API key”.")
            Text2("WHAT IS AN API KEY?", C.Violet, 12, bold = true, spacing = 2)
            Spacer(Modifier.height(4.dp))
            Body("A long password that tells the A.I. company “this request comes from me”. You get it free from their website and paste it into PCL once. It looks like gsk_AbC123… . Treat it like a password: never post it in public chats or screenshots.")
            Spacer(Modifier.height(4.dp))
            Text2("EASIEST OPTION: GROQ (free, fast, no card needed)", C.Violet, 12, bold = true, spacing = 2)
            Spacer(Modifier.height(6.dp))
            StepLine(1, "Tap the button below to open Groq's key page in your browser.")
            Row(Modifier.padding(vertical = 6.dp)) { PillButton("OPEN GROQ KEYS PAGE", C.Violet, filled = true) { openUrl(ctx, "https://console.groq.com/keys") } }
            StepLine(2, "Sign up or log in. “Continue with Google” is the fastest.")
            StepLine(3, "Tap “Create API Key”. Give it any name, like PCL, and confirm.")
            StepLine(4, "The key is shown ONCE. Tap the copy icon next to it (or press and hold the key and choose Copy).")
            StepLine(5, "Come back to PCL, open SYSTEM in the menu, and find “THIRD EYE · A.I. PROVIDER”.")
            StepLine(6, "Tap the “Groq” chip. The address and model fill in by themselves.")
            StepLine(7, "Tap the API KEY box and paste (press and hold, then Paste).")
            StepLine(8, "Tap SAVE, then TEST. You want a green “✓ Connected”.")
            StepLine(9, "Say “Chronos Lock”, then “open third eye”, and talk. Every sentence you say is answered out loud.")
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                PillButton("GO TO SYSTEM", C.Violet) { ChronosEngine.tab.value = Screen.SYSTEM }
                PillButton("OPEN CHATBOT", C.Cyan) { ChronosEngine.tab.value = Screen.EYE }
            }
            Body("Websites change their layout now and then. If a button has a different name, look for “API keys” or “Create key”.")
        }

        Fold("3 · OTHER FREE A.I. PROVIDERS", C.Violet) {
            Text2("GOOGLE GEMINI", C.Violet, 12, bold = true, spacing = 2)
            Body("Open aistudio.google.com/apikey, sign in with Google, tap “Create API key”, copy it. In SYSTEM tap the “Gemini” chip, paste, SAVE, TEST. If it says “high demand” just try again in a minute.")
            Row(Modifier.padding(bottom = 8.dp)) { PillButton("OPEN GEMINI KEYS", C.Violet) { openUrl(ctx, "https://aistudio.google.com/apikey") } }
            Text2("OPENROUTER", C.Violet, 12, bold = true, spacing = 2)
            Body("Open openrouter.ai/keys, sign in, create a key. Pick the “OpenRouter” chip. Free models have “:free” at the end of their name.")
            Row(Modifier.padding(bottom = 8.dp)) { PillButton("OPEN OPENROUTER KEYS", C.Violet) { openUrl(ctx, "https://openrouter.ai/keys") } }
            Text2("POLLINATIONS", C.Violet, 12, bold = true, spacing = 2)
            Body("Open enter.pollinations.ai, create a free key, pick the “Pollinations” chip.")
            Text2("YOUR OWN PC (ADVANCED)", C.Violet, 12, bold = true, spacing = 2)
            Body("Tools like Ollama can run an A.I. on your computer. Choose “Custom / local” and type your PC's address, for example http://192.168.0.10:11434/v1 . No key needed.")
        }

        Fold("4 · IT DOESN'T WORK? READ THE ERROR", C.Red) {
            Body("Open SYSTEM and tap TEST. It shows the exact reason:")
            Text2("HTTP 401 or 403", C.Red, 12, bold = true); Body("The key is wrong, has a space in it, or belongs to another provider. Copy it again and paste it fresh.")
            Text2("HTTP 429 or “limit”", C.Red, 12, bold = true); Body("You used up the free allowance. Wait a few minutes or switch provider.")
            Text2("HTTP 404", C.Red, 12, bold = true); Body("The model name is wrong. Tap the provider chip again to restore the default model.")
            Text2("DNS failed / timed out", C.Red, 12, bold = true); Body("No internet, a VPN, or Private DNS is blocking it. Try Wi-Fi vs mobile data.")
            Text2("“no key” message", C.Red, 12, bold = true); Body("Paste the key and tap SAVE first.")
            Body("Leaked a key by accident? Go back to the provider's website, delete that key and make a new one. It takes a minute.")
        }

        Fold("5 · KEEP MUSIC PLAYING WHILE SHE LISTENS", C.Green) {
            Body("Android normally pauses your music whenever a voice assistant listens. PCL fixes this with a music-safe microphone that does not touch your music at all.")
            StepLine(1, "Open VOICE in the menu.")
            StepLine(2, "Under MUSIC-SAFE MIC tap DOWNLOAD (about 40 MB, use Wi-Fi). It installs itself and switches on.")
            StepLine(3, "That's it. Wake word and commands now work while music keeps playing.")
            Spacer(Modifier.height(6.dp))
            Body("Good to know: it works offline. Whisper mode and the chatbot still use Google's recogniser (more accurate), which can pause music for a moment. Use headphones or turn the music down a little so she hears you over it.")
            Row(Modifier.padding(vertical = 6.dp)) { PillButton("GO TO VOICE", C.Green) { ChronosEngine.tab.value = Screen.VOICE } }
        }

        Fold("6 · PROGRAMS: MAKE MODES DO THINGS", C.Amber) {
            Body("A PROGRAM is a list of steps that runs when something switches on or off: combat mode, quantum focus, Chronos Lock, the chatbot or whisper mode.")
            StepLine(1, "Open PROGRAMS and pick what to program, e.g. combat mode, WHEN TURNED ON.")
            StepLine(2, "Tap a step type to add it: open an app, play a song, set the volume, say something, start a timer, flash the flashlight and more.")
            StepLine(3, "Optional: set a wait in seconds before a step, so music starts after the app opens.")
            StepLine(4, "Optional: add an IF, like “only at night” or “only if battery is below 20”. The step is skipped when the condition is false.")
            StepLine(5, "Optional: set a chance, so a step only happens some of the time.")
            StepLine(6, "Press TEST to try it without switching the mode.")
            Spacer(Modifier.height(6.dp))
            Body("Tap FOCUS, WORKOUT, GOOD NIGHT or MORNING to start from a ready-made template. In SAY, SAVE NOTE and ASK you can use {time}, {date} and {battery}.")
            Body("Opening other apps by voice with the screen off needs SYSTEM → ALLOW OVER OTHER APPS. Newer Android versions may still block it.")
        }

        Fold("7 · HOME WIDGETS, NAMES AND THEMES", C.Cyan) {
            Body("Every button on HOME is a widget. Tap EDIT, then ✕ removes it, ‹ › move it, and + ADD brings back more: flashlight, play/pause, volume, timer, battery and others.")
            Body("In STYLE you can rename Combat Mode, Quantum Focus and the chatbot to anything. Name Combat Mode “Engage Lol” and saying “Engage Lol” runs it, and she answers “Engage Lol activated”. Rename the chatbot “God” and “open god” connects it, and the A.I. introduces itself as God.")
            Body("STYLE also has dark / AMOLED / light, Material You wallpaper colours (Android 12+), accent colours, fonts, glass cards and an animations switch.")
            Row(Modifier.padding(vertical = 6.dp)) { PillButton("OPEN STYLE", C.Cyan) { ChronosEngine.tab.value = Screen.STYLE } }
        }

        Fold("8 · BETTER LISTENING WITH THE SCREEN OFF", C.Amber) {
            StepLine(1, "SYSTEM → ALLOW BACKGROUND (BATTERY): tap Allow so Android doesn't freeze PCL.")
            StepLine(2, "Install “Speech Services by Google” and download offline English in its settings.")
            StepLine(3, "Keep the PCL notification. It is what keeps the microphone alive.")
            StepLine(4, "Turn ALWAYS OFF on Home if you only want the mic during a session.")
        }

        Fold("9 · SHARE & SUPPORT", C.Green) {
            Body("PCL is free. If it helps you, the best thing you can do is tell a friend.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                PillButton("SHARE PCL", C.Green, filled = true) { sharePcl(ctx) }
                PillButton("GITHUB ★", C.Cyan) { openUrl(ctx, REPO_URL) }
            }
            Body("Found a bug or have an idea? Open an issue on GitHub. Tapping the star helps other people find the app.")
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ============================ FIRST-LAUNCH WELCOME ============================

@Composable
fun IntroDialog(onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val pages = listOf(
        Triple("WELCOME TO PCL", "A voice assistant that listens for “Chronos Lock”, runs commands and automations, keeps your music going, and has a built-in A.I. chatbot.", C.Cyan),
        Triple("TRY IT", "Say “Chronos Lock”. When the big button turns green, say “what time is it”. Open COMMANDS in the menu to see everything she can do.", C.Green),
        Triple("MAKE IT YOURS", "Open STYLE to rename modes, pick colours, fonts and glass. On HOME tap EDIT to rearrange widgets.", C.Violet),
        Triple("CHATBOT & MUSIC", "The chatbot needs a free key, and the music-safe mic needs a one-time download. The GUIDE shows both, step by step.", C.Amber)
    )
    val (title, text, accent) = pages[page]
    Dialog(onDismissRequest = onDone) {
        Panel(accent = accent) {
            Text2("${page + 1} / ${pages.size}", C.Dim, 11, spacing = 2)
            Spacer(Modifier.height(6.dp))
            Text2(title, accent, 16, bold = true, spacing = 3)
            Spacer(Modifier.height(8.dp))
            Text2(text, C.Text, 14, mono = false)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("SKIP", C.Soft, modifier = Modifier.weight(1f)) { onDone() }
                if (page < pages.lastIndex) PillButton("NEXT", accent, filled = true, modifier = Modifier.weight(1f)) { page++ }
                else PillButton("OPEN GUIDE", accent, filled = true, modifier = Modifier.weight(1f)) { ChronosEngine.tab.value = Screen.GUIDE; onDone() }
            }
        }
    }
}
