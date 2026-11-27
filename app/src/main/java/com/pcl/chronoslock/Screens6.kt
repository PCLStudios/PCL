package com.pcl.chronoslock

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private const val SOLANA = "5RXdE64oP5yZkzY7jaTZRasg8bfG6YV1oSiccmv4YATH"
private const val ETHEREUM = "0x20f03f587ba7ca0b198e53cb4ae3d8e7e0e272a7"

@Composable
private fun LinkRow(title: String, sub: String, accent: androidx.compose.ui.graphics.Color, url: String) {
    val ctx = LocalContext.current
    Panel(modifier = Modifier.pressable { openUrl(ctx, url) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text2(title, accent, 13, bold = true, spacing = 1)
                Text2(sub, C.Soft, 12, mono = false)
            }
            Text2("↗", accent, 18)
        }
    }
}

@Composable
private fun CryptoRow(name: String, note: String, address: String) {
    val ctx = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    Spacer(Modifier.height(12.dp))
    Text2(name, C.Cyan, 12, bold = true, spacing = 2)
    Text2(note, C.Dim, 11, mono = false)
    Spacer(Modifier.height(4.dp))
    Text2(address, C.Text, 12, modifier = Modifier.clickable {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(name, address)); copied = true
    })
    Spacer(Modifier.height(6.dp))
    PillButton(if (copied) "✓ ADDRESS COPIED" else "COPY ADDRESS", C.Cyan, filled = copied, modifier = Modifier.fillMaxWidth()) {
        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(name, address)); copied = true
    }
}

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val act = ctx.findActivity()
    var taps by remember { mutableStateOf(0) }
    var hint by remember { mutableStateOf("") }
    var privacy by remember { mutableStateOf(false) }
    if (privacy) PrivacyDialog { privacy = false }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        ScreenTitle("ABOUT", "Made by PCL Studios.")
        Panel(accent = C.Cyan) {
            // tap the title a few times, like Android's build number, to reveal developer options
            Text2("PROJECT CHRONOS LOCK", C.Cyan, 16, bold = true, spacing = 3, modifier = Modifier.clickable(enabled = Ads.enabled) {
                if (Ads.devUnlocked) hint = "Developer options are already on."
                else {
                    taps++
                    val left = 7 - taps
                    if (left <= 0) { Ads.unlockDev(true); hint = "You are now a developer." }
                    else if (taps >= 3) hint = "$left more taps to turn on developer options"
                }
            })
            Spacer(Modifier.height(4.dp))
            Text2("v1.1.0 · ${Commands.all.size} commands", C.Dim, 11)
            if (hint.isNotBlank()) { Spacer(Modifier.height(4.dp)); Text2(hint, C.Amber, 11, mono = false) }
            Spacer(Modifier.height(8.dp))
            Text2("A voice-first assistant for your phone: say “Chronos Lock”, run commands and automations, keep your music playing and talk to an A.I. chatbot you can rename. No account, no sign-up, open source.", C.Soft, 13, mono = false)
        }

        SectionLabel("FIND US")
        LinkRow("GITHUB", "github.com/PCLStudios", C.Cyan, GITHUB_URL)
        Spacer(Modifier.height(8.dp))
        LinkRow("X (TWITTER)", "@pclglobal", C.Cyan, TWITTER_URL)

        SectionLabel("SUPPORT PCL")
        Panel(accent = C.Amber) {
            Text2("PCL is free and made by a small team. If it helps you, a coffee keeps updates coming.", C.Soft, 13, mono = false)
            Spacer(Modifier.height(10.dp))
            PillButton("BUY ME A COFFEE", C.Amber, filled = true, modifier = Modifier.fillMaxWidth()) { openUrl(ctx, COFFEE_URL) }
            Spacer(Modifier.height(6.dp))
            Text2("OR SEND CRYPTO", C.Dim, 10, spacing = 3)
            CryptoRow("SOLANA (SOL)", "Send SOL on the Solana network only.", SOLANA)
            CryptoRow("ETHEREUM (ETH)", "Send ETH on the Ethereum network only.", ETHEREUM)
            Spacer(Modifier.height(10.dp))
            Text2("Always compare the first and last characters of the address after pasting. Crypto sent to the wrong network can't be recovered.", C.Dim, 11, mono = false)
        }

        if (Ads.enabled) {
        SectionLabel("POWER PASS")
        if (Pass.active) {
            Panel(accent = C.Green) {
                Text2("● ACTIVE until ${Pass.endsAt()}", C.Green, 13, bold = true)
                Spacer(Modifier.height(4.dp))
                Text2("Unlimited home widgets, unlimited program steps and no banner ads.", C.Soft, 12, mono = false)
            }
            Spacer(Modifier.height(8.dp))
        }
        PassNudge("Free PCL: ${Pass.FREE_WIDGETS} widgets and ${Pass.FREE_STEPS} steps per program, plus a small banner on this page, Guide and Style. Watch one short ad for 24 hours of unlimited widgets, unlimited steps and no banners. Themes, voices, the chatbot and every command always stay free.") {}
        }

        SectionLabel("LEGAL")
        Panel {
            Text2("PCL listens through your microphone only while it is running, and speech is handled on your phone unless you use Google's recogniser or the chatbot. The privacy policy explains what is shared, including how ads work.", C.Soft, 12, mono = false)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("PRIVACY POLICY", C.Cyan) { privacy = true }
                PillButton("SHARE PCL", C.Green) { sharePcl(ctx) }
            }
        }

        if (Ads.devUnlocked) {
            SectionLabel("DEVELOPER OPTIONS")
            Panel(accent = C.Amber) {
                Text2("Ad mode. Use TEST ADS while you try the app on your own phone. Never tap live ads on your own device: Google can ban the ad account for it.", C.Soft, 12, mono = false)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("TEST ADS", Ads.testMode, C.Amber) { act?.let { Ads.setTestMode(true, it) } }
                    Chip("LIVE ADS", !Ads.testMode, C.Green) { act?.let { Ads.setTestMode(false, it) } }
                }
                Spacer(Modifier.height(10.dp))
                Text2(Credits.signature(), C.Dim, 10, mono = false)
                Spacer(Modifier.height(8.dp))
                Chip("HIDE DEVELOPER OPTIONS", false, C.Soft) { Ads.unlockDev(false); taps = 0; hint = "" }
            }
        }
        BannerAd()
        Spacer(Modifier.height(12.dp))
        Text2("© PCL Studios", C.Dim, 11, modifier = Modifier.fillMaxWidth(), align = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
    }
}


/** The privacy policy, shown inside the app so it always works. */
@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Panel(accent = C.Cyan) {
            Text2("PRIVACY POLICY", C.Cyan, 14, bold = true, spacing = 3)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.height(380.dp).verticalScroll(rememberScrollState())) {
                val parts = listOf(
                    "WHAT PCL IS" to "A voice assistant for Android by PCL Studios. No accounts, no PCL server, and we don't collect your name, email or location.",
                    "MICROPHONE" to "PCL listens only while its listening service runs (shown by a notification). With the music-safe mic (optional download) speech is recognised on your phone, offline. Whisper mode, the chatbot and the fallback use Android's / Google's speech recogniser, so Google's privacy policy applies to that audio.",
                    "CHATBOT" to "What you say to the chatbot is sent as text to the A.I. provider you choose (Groq, Google, OpenRouter, etc.) with your own API key. Their policies apply. Your key stays on your phone and is never included in backups.",
                    "STORED ON YOUR PHONE" to "Settings, names, theme, widgets, programs, notes and your API key stay in PCL's private storage.",
                    "ADS" to "PCL shows ads through Google AdMob: a small banner and optional rewarded ads. AdMob may use your device's advertising ID to serve and measure ads. Where required (EU/UK and others) you are asked for consent first. A 24 hour Power Pass hides banners. See policies.google.com/technologies/ads",
                    "DONATIONS" to "Handled by Buy Me a Coffee or sent to the public crypto addresses in About. PCL does not process payments.",
                    "CHILDREN" to "PCL is not directed to children under 13.",
                    "CONTACT" to "github.com/PCLStudios or @pclglobal on X."
                )
                parts.forEach { (h, t) ->
                    Text2(h, C.Soft, 10, bold = true, spacing = 2)
                    Spacer(Modifier.height(2.dp))
                    Text2(t, C.Text, 12, mono = false)
                    Spacer(Modifier.height(10.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            PillButton("CLOSE", C.Cyan, filled = true, modifier = Modifier.fillMaxWidth()) { onDismiss() }
        }
    }
}
