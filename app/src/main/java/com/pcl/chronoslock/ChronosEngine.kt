package com.pcl.chronoslock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

enum class Phase { STANDBY, LISTENING, SPEAKING }
data class VoiceOpt(val name: String, val label: String)

/** Wake word + commands + natural female TTS + app state. Call on the main thread. */
object ChronosEngine {

    private const val DEBOUNCE_MS = 600L
    private const val AWAIT_TIMEOUT_MS = 8000L
    private val WAKE = Regex("""\b(chronos|kronos|cronos|chrono|chronus)[\s-]*(lock|clock|luck|lark)\b""")
    private val FEMALE = listOf("female", "-sfg-", "-tpf-", "-tpc-", "-iob-")
    private val NORM = listOf("dis engage" to "disengage", "de activate" to "deactivate",
        "quantumn" to "quantum", "quantam" to "quantum", "com bat" to "combat")

    // ---- UI state ----
    val locked = MutableStateFlow(false)
    val combat = MutableStateFlow(false)
    val quantum = MutableStateFlow(false)
    val phase = MutableStateFlow(Phase.STANDBY)
    val lastHeard = MutableStateFlow("")
    val tab = MutableStateFlow(0)
    val voices = MutableStateFlow<List<VoiceOpt>>(emptyList())
    val voiceName = MutableStateFlow("")
    val pitch = MutableStateFlow(1.0f)   // 1.0 / 1.0 = natural
    val rate = MutableStateFlow(1.0f)

    // ---- internals ----
    private class Pending(val cmd: Cmd?)
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private lateinit var prefs: SharedPreferences
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var speaking = false
    private var awaitingCommand = false
    private var handled = false
    private var pending: Pending? = null

    private val fire = Runnable {
        val p = pending ?: return@Runnable
        pending = null
        if (!handled && !speaking) execute(p.cmd)
    }
    private val restartRun = Runnable { listen() }
    private val awaitTimeout = Runnable {
        awaitingCommand = false
        if (!speaking) phase.value = Phase.STANDBY
    }

    // ---- lifecycle ----
    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        prefs = ctx!!.getSharedPreferences("pcl", Context.MODE_PRIVATE)
        pitch.value = prefs.getFloat("pitch", 1.0f)
        rate.value = prefs.getFloat("rate", 1.0f)
        tts = TextToSpeech(ctx) { s -> if (s == TextToSpeech.SUCCESS) onTtsReady() }
    }

    fun start() { running = true; main.post { if (!speaking) listen() } }

    fun stop() {
        running = false
        pending = null
        main.removeCallbacksAndMessages(null)
        destroyRecognizer()
        phase.value = Phase.STANDBY
    }

    // ---- button actions ----
    fun toggleLock() {
        locked.value = !locked.value
        say(if (locked.value) "Chronos Lock Engaged" else "Chronos Lock Disengaged")
    }
    fun toggleCombat() {
        combat.value = !combat.value
        say(if (combat.value) "Combat mode has been engaged" else "Combat mode has been disengaged")
    }
    fun toggleQuantum() {
        quantum.value = !quantum.value
        say(if (quantum.value) "Quantum focus has been approved" else "Quantum focus has been disengaged")
    }
    fun preview() = say("Hello. I'm Chronos Lock. This is how I sound.")

    // ---- voice settings ----
    private fun female(v: Voice) = FEMALE.any { v.name.contains(it, true) }

    private fun label(v: Voice): String {
        val net = if (v.isNetworkConnectionRequired) " (online)" else ""
        return (if (female(v)) "♀ " else "") + v.locale.displayCountry.ifBlank { v.locale.language } +
            " · " + v.name.removePrefix("en-").take(20) + net
    }

    private fun onTtsReady() {
        val t = tts ?: return
        t.language = Locale.US
        val en = t.voices.orEmpty().filter { it.locale.language == "en" }.sortedWith(
            compareByDescending<Voice> { female(it) }
                .thenByDescending { it.locale == Locale.US }
                .thenByDescending { it.quality })
        voices.value = en.map { VoiceOpt(it.name, label(it)) }
        val saved = prefs.getString("voice", null)
        (en.firstOrNull { it.name == saved } ?: en.firstOrNull())?.let { applyVoice(it) }
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post { finishSpeaking() } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post { finishSpeaking() } }
        })
        ttsReady = true
    }

    private fun applyVoice(v: Voice) {
        tts?.voice = v
        voiceName.value = v.name
        prefs.edit().putString("voice", v.name).apply()
    }

    fun setVoice(name: String) {
        tts?.voices?.firstOrNull { it.name == name }?.let { applyVoice(it); preview() }
    }
    fun setPitch(v: Float) { pitch.value = v; prefs.edit().putFloat("pitch", v).apply() }
    fun setRate(v: Float) { rate.value = v; prefs.edit().putFloat("rate", v).apply() }
    fun resetVoice() { setPitch(1.0f); setRate(1.0f); preview() }

    // ---- speaking ----
    private fun say(text: String) {
        handled = true
        awaitingCommand = false
        main.removeCallbacks(fire)
        main.removeCallbacks(awaitTimeout)
        speak(text)
    }

    private fun speak(text: String) {
        main.removeCallbacks(fire)
        main.removeCallbacks(restartRun)
        destroyRecognizer()
        speaking = true
        phase.value = Phase.SPEAKING
        val t = tts
        if (t == null || !ttsReady) { finishSpeaking(); return }
        t.setPitch(pitch.value)
        t.setSpeechRate(rate.value)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pcl")
    }

    private fun finishSpeaking() {
        speaking = false
        phase.value = if (awaitingCommand) Phase.LISTENING else Phase.STANDBY
        if (awaitingCommand) {
            main.removeCallbacks(awaitTimeout)
            main.postDelayed(awaitTimeout, AWAIT_TIMEOUT_MS)
        }
        listen()
    }

    // ---- command handling ----
    private fun norm(s: String) = NORM.fold(s.lowercase(Locale.US)) { a, (f, t) -> a.replace(f, t) }

    private fun execute(cmd: Cmd?) {
        val reply = if (cmd == null) "Listening" else ctx?.let { cmd.run(it) } ?: "Done"
        handled = true
        main.removeCallbacks(fire)
        main.removeCallbacks(awaitTimeout)
        awaitingCommand = cmd == null
        speak(reply)
    }

    private fun process(text: String?, final: Boolean) {
        if (text.isNullOrBlank() || handled || speaking) return
        lastHeard.value = text
        val t = norm(text)
        val wake = WAKE.find(t)
        val body = when {
            wake != null -> t.substring(wake.range.last + 1)
            awaitingCommand -> t
            else -> return
        }
        val cmd = Commands.all.firstOrNull { it.match(body) }

        if (cmd == null && awaitingCommand && wake == null) {
            if (final) {
                main.removeCallbacks(awaitTimeout)
                if (body.isBlank()) { awaitingCommand = false; phase.value = Phase.STANDBY }
                else say("Command not recognized")
            }
            return
        }
        pending = Pending(cmd)
        main.removeCallbacks(fire)
        if (final) fire.run() else main.postDelayed(fire, DEBOUNCE_MS)
    }

    // ---- recognition loop ----
    private val listener = object : RecognitionListener {
        override fun onPartialResults(b: Bundle?) = process(first(b), false)
        override fun onResults(b: Bundle?) {
            process(first(b), true)
            if (!handled) restartSoon(150)
        }
        override fun onError(error: Int) {
            if (speaking || handled) return
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> running = false
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> restartSoon(1500)
                else -> restartSoon(200)
            }
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        private fun first(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
    }

    private fun listen() {
        val c = ctx ?: return
        if (!running || speaking) return
        if (!SpeechRecognizer.isRecognitionAvailable(c)) return
        destroyRecognizer()
        handled = false
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
        }
        recognizer = SpeechRecognizer.createSpeechRecognizer(c).also {
            it.setRecognitionListener(listener)
            it.startListening(intent)
        }
    }

    private fun restartSoon(delayMs: Long) {
        main.removeCallbacks(restartRun)
        main.postDelayed(restartRun, delayMs)
    }

    private fun destroyRecognizer() {
        recognizer?.let { runCatching { it.cancel(); it.destroy() } }
        recognizer = null
    }
}
