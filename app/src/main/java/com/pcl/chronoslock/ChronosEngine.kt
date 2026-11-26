package com.pcl.chronoslock

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import org.json.JSONArray
import java.util.Locale

enum class Phase { STANDBY, LISTENING, SPEAKING, THINKING }
enum class Screen(val label: String, val glyph: String) {
    HOME("HOME", "◎"), EYE("CHATBOT", "◉"), FLOWS("PROGRAMS", "⚡"), NOTES("NOTES", "✎"), HELP("COMMANDS", "☰"),
    GUIDE("GUIDE", "?"), STYLE("STYLE", "✦"), VOICE("VOICE", "♪"), SYSTEM("SYSTEM", "⚙");
    /** The chatbot screen shows the user's own name for it. */
    val title: String get() = if (this == EYE) Names.eye.uppercase(Locale.US) else label
}
data class VoiceOpt(val name: String, val label: String)
data class Msg(val fromUser: Boolean, val text: String)
data class TimerItem(val id: Int, val endAt: Long, val label: String)

/**
 * Brain of PCL. Rules:
 *  - Saying "Chronos Lock" (fuzzy matched, see Wake) engages the session. While engaged, commands work
 *    WITHOUT the wake word (short phrases only, so chatter isn't mistaken for commands).
 *  - While disengaged, everything is ignored except the wake word (optionally followed by a command).
 *  - Third Eye mode sends every spoken sentence to the online chatbot (stateless: no history is sent).
 *  - Whisper mode: online recognition, longer silence window, looser wake matching, quiet replies.
 * Call on the main thread.
 */
object ChronosEngine {

    private const val MAX_WORDS = 8          // un-prefixed commands must be short, so chatter isn't mistaken for commands
    private val FEMALE = listOf("female", "-sfg-", "-tpf-", "-tpc-", "-iob-")
    private val NORM = listOf("dis engage" to "disengage", "de activate" to "deactivate", "quantumn" to "quantum",
        "quantam" to "quantum", "quantom" to "quantum", "com bat" to "combat", "fird eye" to "third eye",
        "turd eye" to "third eye", "thirdeye" to "third eye", "third i " to "third eye ", "flash light" to "flashlight",
        "whisperer" to "whisper", "wi-fi" to "wifi", "wi fi" to "wifi")

    // ---- UI state ----
    val locked = MutableStateFlow(false)
    val combat = MutableStateFlow(false)
    val quantum = MutableStateFlow(false)
    val whisper = MutableStateFlow(false)
    val alwaysOn = MutableStateFlow(true)           // false = the mic only opens while a session is engaged (big button)
    val keepMusic = MutableStateFlow(true)          // try to resume music the recogniser pauses
    val thirdEye = MutableStateFlow(false)
    val torch = MutableStateFlow(false)
    val phase = MutableStateFlow(Phase.STANDBY)
    val level = MutableStateFlow(0f)               // mic loudness 0..1 for the visualiser
    val lastHeard = MutableStateFlow("")
    val lastReply = MutableStateFlow("")
    val tab = MutableStateFlow(Screen.HOME)
    val chat = MutableStateFlow<List<Msg>>(emptyList())
    val notes = MutableStateFlow<List<String>>(emptyList())
    val timers = MutableStateFlow<List<TimerItem>>(emptyList())
    val voices = MutableStateFlow<List<VoiceOpt>>(emptyList())
    val voiceName = MutableStateFlow("")
    val pitch = MutableStateFlow(1.0f)
    val rate = MutableStateFlow(1.0f)
    val sessionMin = MutableStateFlow(0)           // auto-disengage after N idle minutes; 0 = never
    val aiUrl = MutableStateFlow(ThirdEyeAI.DEFAULT_URL)
    val aiModel = MutableStateFlow(ThirdEyeAI.DEFAULT_MODEL)
    val aiKey = MutableStateFlow("")
    val persona = MutableStateFlow("")             // user's own persona text; blank = automatic (uses the chatbot's name)
    val voskOn = MutableStateFlow(true)            // use the music-safe offline mic when it is installed
    val showIntro = MutableStateFlow(false)        // first-launch welcome
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var voskRun: VoskMic.Run? = null
    private var voskBroken = false

    // ---- internals ----
    private sealed class Act {
        object Wake : Act()
        class Run(val cmd: Cmd?, val body: String) : Act()
        class Ask(val q: String) : Act()
        object CloseEye : Act()
    }
    private val main = Handler(Looper.getMainLooper())
    private var ctx: Context? = null
    private lateinit var prefs: SharedPreferences
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var recognizer: SpeechRecognizer? = null
    private var running = false
    private var speaking = false
    private var thinking = false
    private var handled = false
    private var pending: Act? = null
    private var session = 0                 // ignores callbacks from recognisers we already replaced
    private var utt = 0
    private var errorStreak = 0
    @Volatile var appVisible = false            // set by MainActivity: Android only lets apps open other apps while on screen
    private var lastSpoken = ""                 // normalised text of her last reply (to ignore her own echo)
    private var lastSpeechEnd = 0L
    private var onlineBroken = false        // online recognition failed (no network): fall back to offline
    private var offlineMissing = false      // offline model not installed: use online
    private var nextTimerId = 1
    private val timerRuns = HashMap<Int, Runnable>()

    private val fire = Runnable {
        val a = pending ?: return@Runnable
        pending = null
        if (!handled && !speaking && !thinking) execute(a)
    }
    private val restartRun = Runnable { listen() }
    private val watchdog = Runnable { if (running && !speaking && !thinking) listen() }   // recogniser hung: restart it
    private val speakGuard = Runnable { if (speaking) finishSpeaking() }                 // TTS never called back
    private val idleRun = Runnable {
        if (locked.value) {
            locked.value = false; thirdEye.value = false
            speak("Chronos Lock timed out. Standing by")
        }
    }

    // ---- lifecycle ----
    fun init(context: Context) {
        if (ctx != null) return
        ctx = context.applicationContext
        prefs = ctx!!.getSharedPreferences("pcl", Context.MODE_PRIVATE)
        loadPrefs()
        VoskMic.init(ctx!!)
        showIntro.value = !prefs.getBoolean("intro_done", false)
        // programs that run when these switch on/off
        locked.drop(1).onEach { c -> ctx?.let { Flows.runSlot(it, if (c) "lock_on" else "lock_off") } }.launchIn(scope)
        thirdEye.drop(1).onEach { c -> ctx?.let { Flows.runSlot(it, if (c) "eye_on" else "eye_off") } }.launchIn(scope)
        whisper.drop(1).onEach { c -> ctx?.let { Flows.runSlot(it, if (c) "whisper_on" else "whisper_off") } }.launchIn(scope)
        tts = TextToSpeech(ctx) { s -> if (s == TextToSpeech.SUCCESS) onTtsReady() }
    }

    /** Reads every saved setting into the live state (also used after importing a backup). */
    private fun loadPrefs() {
        pitch.value = prefs.getFloat("pitch", 1.0f)
        rate.value = prefs.getFloat("rate", 1.0f)
        whisper.value = prefs.getBoolean("whisper", false)
        alwaysOn.value = prefs.getBoolean("alwaysOn", true)
        keepMusic.value = prefs.getBoolean("keepMusic", true)
        voskOn.value = prefs.getBoolean("voskOn", true)
        Flows.init(prefs)
        Names.init(prefs)
        Pal.init(prefs)
        HomeLayout.load(prefs)
        sessionMin.value = prefs.getInt("sessionMin", 0)
        aiUrl.value = prefs.getString("aiUrl", ThirdEyeAI.DEFAULT_URL) ?: ThirdEyeAI.DEFAULT_URL
        aiModel.value = prefs.getString("aiModel", ThirdEyeAI.DEFAULT_MODEL) ?: ThirdEyeAI.DEFAULT_MODEL
        aiKey.value = prefs.getString("aiKey", "") ?: ""
        val p = prefs.getString("persona", "") ?: ""
        persona.value = if (p == ThirdEyeAI.DEFAULT_PERSONA) "" else p       // the old built-in text counts as "automatic"
        notes.value = runCatching {
            val a = JSONArray(prefs.getString("notes", "[]")); List(a.length()) { a.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun introDone() { showIntro.value = false; prefs.edit().putBoolean("intro_done", true).apply() }

    /** Settings as JSON text (the API key is left out on purpose so a shared backup never leaks it). */
    fun exportSettings(): String {
        val o = JSONObject()
        for ((k, v) in prefs.all) { if (k == "aiKey" || k == "intro_done") continue; o.put(k, v) }
        return o.toString()
    }

    /** @return null if imported, otherwise what went wrong. */
    fun importSettings(text: String): String? {
        val o = try { JSONObject(text.trim()) } catch (e: Exception) { return "That isn't a PCL backup." }
        if (o.length() == 0) return "The backup is empty."
        val ed = prefs.edit()
        for (k in o.keys()) {
            if (k == "aiKey") continue
            if (k == "pitch" || k == "rate") { ed.putFloat(k, o.optDouble(k, 1.0).toFloat()); continue }
            when (val v = o.get(k)) {
                is Boolean -> ed.putBoolean(k, v)
                is Int -> ed.putInt(k, v)
                is Long -> ed.putLong(k, v)
                is Double -> ed.putFloat(k, v.toFloat())
                is String -> ed.putString(k, v)
            }
        }
        ed.apply()
        loadPrefs()
        if (running && !speaking && !thinking) restartSoon(100)
        return null
    }

    fun start() {
        if (running) return                       // the activity re-sends start every time it opens: don't restart the mic mid-sentence
        running = true
        main.post { if (!speaking && !thinking) listen() }
    }

    fun stop() {
        running = false
        pending = null
        main.removeCallbacksAndMessages(null)
        timerRuns.clear(); timers.value = emptyList()
        destroyRecognizer()
        runCatching { tts?.stop() }
        speaking = false; thinking = false; handled = false     // stale flags would block listening after a restart
        phase.value = Phase.STANDBY
    }

    // ---- button actions ----
    private fun needLock(): Boolean {
        if (locked.value) return false
        say("Chronos Lock is not engaged. Say Chronos Lock first"); return true
    }
    fun toggleLock() {
        if (locked.value) {
            locked.value = false; thirdEye.value = false
            main.removeCallbacks(idleRun)
            say("Chronos Lock Disengaged")
        } else { locked.value = true; bumpIdle(); say("Chronos Lock Engaged") }
    }
    /** Mode switches run the user's programmed steps (see Flows / PROGRAMS screen). */
    fun setCombat(on: Boolean) {
        if (combat.value == on) return
        combat.value = on
        ctx?.let { Flows.runSlot(it, if (on) "combat_on" else "combat_off") }
    }
    fun setQuantum(on: Boolean) {
        if (quantum.value == on) return
        quantum.value = on
        ctx?.let { Flows.runSlot(it, if (on) "quantum_on" else "quantum_off") }
    }
    /** Lets a programmed step speak. */
    fun announce(text: String) { say(text) }

    fun setAlwaysOn(on: Boolean) {
        alwaysOn.value = on
        prefs.edit().putBoolean("alwaysOn", on).apply()
        if (running && !speaking && !thinking) restartSoon(100)      // opens or closes the mic right away
    }
    fun setKeepMusic(on: Boolean) { keepMusic.value = on; prefs.edit().putBoolean("keepMusic", on).apply() }
    fun setVoskOn(on: Boolean) {
        voskOn.value = on; prefs.edit().putBoolean("voskOn", on).apply(); voskBroken = false
        if (running && !speaking && !thinking) restartSoon(100)
    }
    /** Downloads the offline speech model for the music-safe mic, then switches it on. */
    fun downloadVosk() { ctx?.let { c -> VoskMic.download(c) { ok -> if (ok) setVoskOn(true) } } }
    fun deleteVosk() { destroyRecognizer(); ctx?.let { VoskMic.delete(it) }; if (running && !speaking && !thinking) restartSoon(100) }
    /** The system-prompt the chatbot actually receives. */
    fun effectivePersona(): String = ThirdEyeAI.buildPersona(Names.eye, persona.value)

    fun toggleCombat() {
        if (needLock()) return
        bumpIdle(); setCombat(!combat.value)
        say(Names.combatReply(combat.value))
    }
    fun toggleQuantum() {
        if (needLock()) return
        bumpIdle(); setQuantum(!quantum.value)
        say(Names.quantumReply(quantum.value))
    }
    fun toggleWhisper() {
        setWhisper(!whisper.value)
        say(if (whisper.value) "Whisper mode enabled. I am listening closely" else "Whisper mode disabled")
    }
    fun toggleTorch() {
        val c = ctx ?: return
        if (!com.pcl.chronoslock.torch(c, !torch.value)) say("Flashlight unavailable")
    }
    fun toggleEye() { if (thirdEye.value) say(closeEye()) else say(openEye()) }
    fun allOff() {
        locked.value = false; thirdEye.value = false; main.removeCallbacks(idleRun)
        setCombat(false); setQuantum(false)
        if (running && !speaking && !thinking) restartSoon(100)
    }
    fun preview() = say("Hello. I'm Chronos Lock. This is how I sound.")

    // ---- settings ----
    fun setWhisper(on: Boolean) {
        whisper.value = on
        prefs.edit().putBoolean("whisper", on).apply()
        if (running && !speaking && !thinking) restartSoon(100)   // re-open the mic with the new settings
    }
    fun setSessionMin(m: Int) { sessionMin.value = m; prefs.edit().putInt("sessionMin", m).apply(); bumpIdle() }
    fun setAi(url: String, model: String, key: String, persona: String) {
        aiUrl.value = ThirdEyeAI.cleanUrl(url); aiModel.value = model.trim(); aiKey.value = key.trim()
        val auto = ThirdEyeAI.defaultPersona(Names.eye)
        this.persona.value = if (persona.isBlank() || persona.trim() == auto.trim() || persona == ThirdEyeAI.DEFAULT_PERSONA) "" else persona.trim()
        prefs.edit().putString("aiUrl", aiUrl.value).putString("aiModel", aiModel.value)
            .putString("aiKey", aiKey.value).putString("persona", this.persona.value).apply()
    }
    fun setPitch(v: Float) { pitch.value = v; prefs.edit().putFloat("pitch", v).apply() }
    fun setRate(v: Float) { rate.value = v; prefs.edit().putFloat("rate", v).apply() }
    fun resetVoice() { setPitch(1.0f); setRate(1.0f); preview() }

    // ---- voices ----
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
            override fun onDone(id: String?) { main.post { if (id == "pcl$utt" && speaking) finishSpeaking() } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { main.post { if (id == "pcl$utt" && speaking) finishSpeaking() } }
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

    fun nextVoice(): String {
        val l = voices.value
        if (l.isEmpty()) return "No other voices are installed"
        val n = (l.indexOfFirst { it.name == voiceName.value } + 1) % l.size
        tts?.voices?.firstOrNull { it.name == l[n].name }?.let { applyVoice(it) }
        return "Voice ${n + 1} of ${l.size}. How do I sound?"
    }

    // ---- speaking ----
    /** Speak and (afterwards) go back to listening. */
    private fun say(text: String) { handled = true; speak(text) }

    private fun speak(text: String) {
        main.removeCallbacks(fire); main.removeCallbacks(restartRun); main.removeCallbacks(watchdog)
        destroyRecognizer()
        if (text.isNotBlank()) { lastReply.value = text; lastSpoken = norm(text) }
        speaking = true
        phase.value = Phase.SPEAKING
        val t = tts
        if (t == null || !ttsReady || text.isBlank()) { finishSpeaking(); return }
        t.setPitch(pitch.value)
        t.setSpeechRate(rate.value)
        val b = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, if (whisper.value) 0.35f else 1f) }
        utt++
        main.removeCallbacks(speakGuard)
        main.postDelayed(speakGuard, 8000L + text.length * 90L)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, b, "pcl$utt")
    }

    private fun finishSpeaking() {
        main.removeCallbacks(speakGuard)
        if (!speaking) return
        speaking = false
        lastSpeechEnd = SystemClock.elapsedRealtime()
        phase.value = Phase.STANDBY
        restartSoon(350)                          // let the speaker tail die out so she doesn't hear herself
    }

    /** "quiet" command / stop button. */
    fun hush() {
        tts?.stop()
        if (speaking) finishSpeaking()
    }

    // ---- notes ----
    fun addNote(text: String) { notes.value = notes.value + text; saveNotes() }
    fun deleteNote(i: Int) { notes.value = notes.value.filterIndexed { n, _ -> n != i }; saveNotes() }
    fun clearNotes() { notes.value = emptyList(); saveNotes() }
    private fun saveNotes() { prefs.edit().putString("notes", JSONArray(notes.value).toString()).apply() }

    // ---- timers & reminders (several at once) ----
    fun setTimer(ms: Long, label: String) {
        val id = nextTimerId++
        timers.value = timers.value + TimerItem(id, SystemClock.elapsedRealtime() + ms, label)
        val r = Runnable {
            timers.value = timers.value.filter { it.id != id }; timerRuns.remove(id)
            speak(if (label.isBlank()) "Your timer is finished" else "Reminder. $label")
        }
        timerRuns[id] = r
        main.postDelayed(r, ms)
    }
    fun cancelTimer() { timerRuns.values.forEach { main.removeCallbacks(it) }; timerRuns.clear(); timers.value = emptyList() }
    fun timerLeftMs(): Long { val t = timers.value.minOfOrNull { it.endAt } ?: return 0L; return t - SystemClock.elapsedRealtime() }

    // ---- idle auto-disengage ----
    private fun bumpIdle() {
        main.removeCallbacks(idleRun)
        val m = sessionMin.value
        if (m > 0 && locked.value) main.postDelayed(idleRun, m * 60_000L)
    }

    // ---- Third Eye (online chatbot) ----
    fun openEye(): String {
        locked.value = true; thirdEye.value = true; tab.value = Screen.EYE; bumpIdle()
        return "${Names.eye} connected. I'm listening. Ask me anything"
    }
    fun closeEye(): String {
        thirdEye.value = false
        if (tab.value == Screen.EYE) tab.value = Screen.HOME
        return "${Names.eye} closed. Back to commands"
    }
    fun clearChat() { chat.value = emptyList() }

    /** Typed question from the Third Eye screen. */
    fun askTyped(q: String) {
        if (q.isBlank()) return
        if (!thirdEye.value) { locked.value = true; thirdEye.value = true }
        ask(q.trim())
    }

    /** One stateless question: nothing but the persona and this question is sent. Also used by "ask ...". */
    fun ask(q: String) {
        if (thinking) return                                   // one question at a time
        bumpIdle()
        handled = true
        main.removeCallbacks(fire); main.removeCallbacks(restartRun); main.removeCallbacks(watchdog)
        destroyRecognizer()
        thinking = true
        phase.value = Phase.THINKING
        chat.value = (chat.value + Msg(true, q)).takeLast(60)
        if (!isOnline()) {
            // no point trying: say so straight away
            val msg = "You are offline. Turn on Wi-Fi or mobile data."
            thinking = false
            chat.value = (chat.value + Msg(false, msg)).takeLast(60)
            speak(msg); return
        }
        ThirdEyeAI.ask(aiUrl.value, aiModel.value, aiKey.value, effectivePersona(), q) { r ->
            thinking = false
            val spoken = r.fold({ cleanForSpeech(it).ifBlank { "I have no answer for that" } }, { aiError(it) })
            // the chat shows the real technical reason too, so a failure can actually be diagnosed
            val shown = r.fold({ spoken }, { e -> spoken + ((e as? AiException)?.detail?.let { "\n⚠ $it" } ?: "") })
            chat.value = (chat.value + Msg(false, shown)).takeLast(60)
            speak(spoken)
        }
    }

    /** True if Android reports a connection that can reach the internet. */
    fun isOnline(): Boolean {
        val c = ctx ?: return true
        return runCatching {
            val cm = c.getSystemService(android.net.ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            caps != null && caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
    }

    /** Connection test for the System screen: shows the exact result as text, no speech, no chat. */
    fun testAi(onResult: (String) -> Unit) {
        if (!isOnline()) { onResult("✗ Phone reports no internet connection."); return }
        val t0 = SystemClock.elapsedRealtime()
        ThirdEyeAI.ask(aiUrl.value, aiModel.value, aiKey.value, effectivePersona(), "Reply with the single word: ready") { r ->
            val ms = SystemClock.elapsedRealtime() - t0
            onResult(r.fold(
                { "✓ Connected in ${ms} ms. Reply: " + it.take(80) },
                { e -> "✗ " + ((e as? AiException)?.let { "${it.kind}: ${it.detail}" } ?: (e.message ?: "failed")) }
            ))
        }
    }

    private fun cleanForSpeech(s: String) = s.replace(Regex("""https?://\S+"""), "").replace(Regex("""[*_#`>~|]"""), "")
        .replace(Regex("""\s+"""), " ").trim()

    private fun aiError(e: Throwable): String = when ((e as? AiException)?.kind) {
        AiException.Kind.NO_KEY -> "My network core needs an A.I. key. Open System, get a free key, and paste it in."
        AiException.Kind.BAD_KEY -> "My network core rejected the key. Check it in System."
        AiException.Kind.LIMIT -> "I have hit my usage limit. Try again shortly, or switch provider in System."
        AiException.Kind.SERVER -> "My network core returned an error. Please try again."
        AiException.Kind.BAD_URL -> "The A.I. address looks wrong. Check the endpoint and model in System."
        AiException.Kind.BAD_REPLY -> "My network core sent a reply I couldn't read. Try again, or try another model."
        else -> "I can't reach my network core. Check your internet connection."
    }

    // ---- command handling ----
    private fun norm(s: String): String {
        var t = s.lowercase(Locale.US).replace("a.m.", "am").replace("p.m.", "pm")
        t = t.replace(Regex("""(?<!\d)\.|\.(?!\d)"""), " ").replace(Regex("""[,!?;:"“”]"""), " ")
        t = NORM.fold(t) { a, (f, r) -> a.replace(f, r) }
        t = t.replace(Regex("""\bthird i\b"""), "third eye")
        return t.replace(Regex("""\s+"""), " ").trim()
    }

    private fun decide(raw: List<String>, alts: List<String>, final: Boolean): Act? {
        if (thirdEye.value) {
            if (alts.any { Commands.isEyeClose(it) }) return Act.CloseEye
            return if (final) Act.Ask(raw.first().trim()) else null     // questions go out only when the sentence is complete
        }
        for (alt in alts) {
            val toks = alt.split(" ")
            val end = Wake.find(toks, whisper.value)
            if (end >= 0) {
                val body = toks.drop(end + 1).joinToString(" ")
                return if (body.isBlank()) Act.Wake else Act.Run(Commands.match(body), body)
            }
        }
        if (!locked.value) return null                                  // not engaged: ignore everything
        val pick = alts.firstOrNull { Commands.match(it) != null } ?: return null
        if (pick.split(" ").size > MAX_WORDS && !Commands.openEnded(pick)) return null
        return Act.Run(Commands.match(pick), pick)
    }

    /** @return true if something was understood. */
    private fun process(raw: List<String>, final: Boolean): Boolean {
        if (raw.isEmpty() || handled || speaking || thinking) return false
        lastHeard.value = raw.first()
        val alts = raw.map { norm(it) }.filter { it.isNotBlank() }
        if (alts.isEmpty()) return false
        // just finished talking and this is part of what she said: it's her own echo, not the user
        if (SystemClock.elapsedRealtime() - lastSpeechEnd < 1500 && lastSpoken.isNotEmpty() && lastSpoken.contains(alts.first())) return false
        val a = decide(raw, alts, final) ?: return false
        pending = a
        main.removeCallbacks(fire)
        if (final) fire.run()
        else {
            val slow = whisper.value
            // a bare "Chronos Lock" waits a little longer in case a command follows in the same breath
            val d = if (a is Act.Wake) (if (slow) 1200L else 850L) else (if (slow) 800L else 550L)
            main.postDelayed(fire, d)
        }
        return true
    }

    private fun execute(a: Act) {
        val c = ctx ?: return
        main.removeCallbacks(fire); pending = null
        handled = true
        bumpIdle()
        when (a) {
            Act.Wake -> {
                val was = locked.value
                locked.value = true; bumpIdle()
                speak(if (was) "Yes? I'm listening" else "Chronos Lock Engaged")
            }
            is Act.Run -> {
                val was = locked.value
                locked.value = true                                    // a spoken wake word engages the session
                val cmd = a.cmd
                if (cmd == null) {
                    bumpIdle()
                    speak(if (was) "Command not recognized" else "Chronos Lock Engaged. Waiting for your command")
                    return
                }
                Commands.body = a.body
                val reply = runCatching { cmd.run(c) }.getOrElse { "That command failed" }
                bumpIdle()
                if (thinking) return                                   // an A.I. question took over
                if (reply.isBlank()) restartSoon(100) else speak(reply)
            }
            is Act.Ask -> ask(a.q)
            Act.CloseEye -> speak(closeEye())
        }
    }

    // ---- recognition loop ----
    private fun makeListener(id: Int) = object : RecognitionListener {
        private fun all(b: Bundle?) = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
        override fun onPartialResults(b: Bundle?) { if (id == session) process(all(b), false) }
        override fun onResults(b: Bundle?) {
            if (id != session) return
            errorStreak = 0; onlineBroken = false
            if (!process(all(b), true)) {
                main.removeCallbacks(fire); pending = null
                if (!handled && !speaking && !thinking) restartSoon(80)
            }
        }
        override fun onError(error: Int) {
            if (id != session || speaking || handled || thinking) return
            errorStreak++
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> { running = false; lastHeard.value = "Microphone permission needed"; phase.value = Phase.STANDBY }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> { errorStreak = 0; restartSoon(60) }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY, SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> restartSoon(1200)
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> { offlineMissing = true; restartSoon(300) }
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> { onlineBroken = true; restartSoon(1500) }
                else -> restartSoon((200L * errorStreak).coerceAtMost(3000L))   // back off so a broken recogniser can't spin
            }
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) { if (id == session) level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f) }
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { level.value = 0f }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun listen() {
        val c = ctx ?: return
        if (!running || speaking || thinking) return
        if (!alwaysOn.value && !locked.value && !thirdEye.value) {
            // "Always on" is off and no session is engaged: the mic stays closed until the big button is pressed
            destroyRecognizer(); main.removeCallbacks(watchdog); handled = false
            phase.value = Phase.STANDBY
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(c)) {
            lastHeard.value = "No speech recogniser. Install Speech Services by Google"; phase.value = Phase.STANDBY; return
        }
        destroyRecognizer()
        handled = false
        if (voskOn.value && VoskMic.ready() && !voskBroken && !thirdEye.value && !whisper.value) {
            // Music-safe path: reads the mic directly (no audio focus), so music keeps playing
            val vid = ++session
            phase.value = Phase.LISTENING
            main.removeCallbacks(watchdog)
            voskRun = VoskMic.start(c,
                onPartial = { t -> if (vid == session) process(listOf(t), false) },
                onFinal = { t -> if (vid == session) process(listOf(t), true) },
                onLevel = { l -> if (vid == session) level.value = l },
                onFail = { msg ->
                    if (vid == session) {
                        voskBroken = true                                  // fall back to the normal recogniser
                        lastHeard.value = "Music-safe mic failed: $msg"
                        restartSoon(200)
                    }
                })
            return
        }
        val eye = thirdEye.value
        val w = whisper.value
        // Online is more accurate (needed for whispers and full sentences); offline is the fallback.
        val online = (w || eye || offlineMissing) && !onlineBroken
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)           // alternatives: wake word is matched against all of them
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, c.packageName)
            if (!online) putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, if (w) 2200L else if (eye) 1600L else 1100L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, if (w) 1800L else if (eye) 1300L else 900L)
            if (w) putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
        }
        val am = c.getSystemService(android.media.AudioManager::class.java)
        val musicWas = keepMusic.value && runCatching { am.isMusicActive }.getOrDefault(false)
        val id = ++session
        phase.value = Phase.LISTENING
        recognizer = SpeechRecognizer.createSpeechRecognizer(c).also {
            it.setRecognitionListener(makeListener(id))
            it.startListening(intent)
        }
        if (musicWas) main.postDelayed({
            // Android pauses other players while the recogniser listens; if that happened, press play again
            if (running && !speaking && !thinking && id == session && runCatching { !am.isMusicActive }.getOrDefault(false)) {
                am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                am.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
            }
        }, 900)
        main.removeCallbacks(watchdog)
        main.postDelayed(watchdog, 30_000L)
    }

    private fun restartSoon(delayMs: Long) {
        main.removeCallbacks(restartRun)
        main.postDelayed(restartRun, delayMs)
    }

    private fun destroyRecognizer() {
        session++                                  // anything the old recogniser still says is ignored
        voskRun?.stop = true; voskRun = null
        recognizer?.let { runCatching { it.cancel(); it.destroy() } }
        recognizer = null
        level.value = 0f
    }
}
