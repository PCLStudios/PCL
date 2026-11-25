package com.pcl.chronoslock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Locale

enum class Phase { STANDBY, LISTENING, SPEAKING }
private enum class Cmd { WAKE_ONLY, COMBAT, QUANTUM, ENGAGE, DISENGAGE }

/**
 * Core brain of PCL: wake word, voice commands, robotic female TTS, app state.
 * All methods must be called on the main thread.
 */
object ChronosEngine {

    // ---- Tunables ----------------------------------------------------------
    private const val VOICE_PITCH = 0.85f       // lower = more robotic
    private const val VOICE_RATE = 0.92f
    private const val WAKE_DEBOUNCE_MS = 700L   // wait for "combat mode" after "chronos lock"
    private const val AWAIT_TIMEOUT_MS = 8000L  // wait for a command after "Listening"
    private val WAKE = Regex("""\b(chronos|kronos|cronos|chrono|chronus)[\s-]*(lock|clock|luck|lark)\b""")
    private val FEMALE_HINTS = listOf("female", "-sfg-", "-tpf-", "-tpc-", "-iob-")

    // ---- Observable state (UI) ---------------------------------------------
    val locked = MutableStateFlow(false)
    val combat = MutableStateFlow(false)
    val quantum = MutableStateFlow(false)
    val phase = MutableStateFlow(Phase.STANDBY)
    val lastHeard = MutableStateFlow("")

    // ---- Internals -----------------------------------------------------------
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var speaking = false
    private var awaitingCommand = false
    private var handled = false

    private val wakeFire = Runnable { if (!handled && !speaking) execute(Cmd.WAKE_ONLY) }
    private val restartRun = Runnable { listen() }
    private val awaitTimeout = Runnable {
        awaitingCommand = false
        if (!speaking) phase.value = Phase.STANDBY
    }

    // ---- Lifecycle -----------------------------------------------------------
    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        tts = TextToSpeech(ctx) { status -> if (status == TextToSpeech.SUCCESS) onTtsReady() }
    }

    fun start() { running = true; main.post { if (!speaking) listen() } }

    fun stop() {
        running = false
        main.removeCallbacksAndMessages(null)
        destroyRecognizer()
        phase.value = Phase.STANDBY
    }

    // ---- Public actions (buttons + voice share these) ----------------------------
    fun toggleLock() = execute(if (locked.value) Cmd.DISENGAGE else Cmd.ENGAGE)
    fun triggerCombat() = execute(Cmd.COMBAT)
    fun triggerQuantum() = execute(Cmd.QUANTUM)

    // ---- Voice ----------------------------------------------------------------
    private fun onTtsReady() {
        val t = tts ?: return
        t.language = Locale.US
        val voices = t.voices.orEmpty().filter { it.locale.language == "en" && !it.isNetworkConnectionRequired }
        val pick = voices.firstOrNull { v -> FEMALE_HINTS.any { v.name.contains(it, true) } && v.locale == Locale.US }
            ?: voices.firstOrNull { v -> FEMALE_HINTS.any { v.name.contains(it, true) } }
        if (pick != null) t.voice = pick
        t.setPitch(VOICE_PITCH)
        t.setSpeechRate(VOICE_RATE)
        t.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { main.post { finishSpeaking() } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post { finishSpeaking() } }
        })
        ttsReady = true
    }

    private fun speak(text: String) {
        main.removeCallbacks(wakeFire)
        main.removeCallbacks(restartRun)
        destroyRecognizer()
        speaking = true
        phase.value = Phase.SPEAKING
        val t = tts
        if (t == null || !ttsReady) { finishSpeaking(); return }
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

    // ---- Command handling ----------------------------------------------------------
    private fun parse(s: String): Cmd? = when {
        "disengage" in s -> Cmd.DISENGAGE
        "engage" in s -> Cmd.ENGAGE
        "combat" in s -> Cmd.COMBAT
        "quantum" in s || "quantam" in s || "focus" in s -> Cmd.QUANTUM
        else -> null
    }

    private fun execute(cmd: Cmd) {
        handled = true
        main.removeCallbacks(wakeFire)
        main.removeCallbacks(awaitTimeout)
        awaitingCommand = cmd == Cmd.WAKE_ONLY
        when (cmd) {
            Cmd.WAKE_ONLY -> speak("Listening")
            Cmd.COMBAT -> { combat.value = true; speak("Combat mode has been engaged") }
            Cmd.QUANTUM -> { quantum.value = true; speak("Quantum focus has been approved") }
            Cmd.ENGAGE -> { locked.value = true; speak("Chronos Lock Engaged") }
            Cmd.DISENGAGE -> { locked.value = false; speak("Chronos Lock Disengaged") }
        }
    }

    private fun process(text: String?, final: Boolean) {
        if (text.isNullOrBlank() || handled || speaking) return
        lastHeard.value = text
        val t = text.lowercase(Locale.US)
        val wake = WAKE.find(t)

        if (awaitingCommand) {
            val c = parse(if (wake != null) t.substring(wake.range.last + 1) else t)
            when {
                c != null -> execute(c)
                final -> { awaitingCommand = false; main.removeCallbacks(awaitTimeout); phase.value = Phase.STANDBY }
            }
            return
        }
        if (wake == null) return
        val c = parse(t.substring(wake.range.last + 1))
        when {
            c != null -> execute(c)
            final -> execute(Cmd.WAKE_ONLY)
            else -> { main.removeCallbacks(wakeFire); main.postDelayed(wakeFire, WAKE_DEBOUNCE_MS) }
        }
    }

    // ---- Speech recognition loop -------------------------------------------------------
    private val listener = object : RecognitionListener {
        override fun onPartialResults(b: Bundle?) = process(first(b), false)
        override fun onResults(b: Bundle?) {
            process(first(b), true)
            if (!handled) restartSoon(150)
        }
        override fun onError(error: Int) {
            if (speaking || handled) return
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { running = false }
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
