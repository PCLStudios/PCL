package com.pcl.chronoslock

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.random.Random

/** One voice command. The Commands screen is generated from this list, so help is always accurate. */
class Cmd(val cat: String, val phrase: String, val help: String, val match: (String) -> Boolean, val run: (Context) -> String)

private val OFFW = setOf("off", "disengage", "disengaged", "deactivate", "deactivated", "stop", "cancel", "close", "exit", "end", "quit", "disable")
private fun String.has(vararg w: String) = w.any { contains(it) }
private fun String.hasW(vararg w: String): Boolean { val t = split(" "); return w.any { it in t } }
private fun String.off() = hasW(*OFFW.toTypedArray()) || has("stand down", "turn off", "shut down")

private val JOKES = listOf(
    "Why do programmers prefer dark mode? Because light attracts bugs.",
    "I would tell you a UDP joke, but you might not get it.",
    "There are ten kinds of people: those who understand binary, and those who don't.",
    "Why did the phone wear glasses? It lost its contacts.",
    "I asked my computer for a break. It sent me a vacation ad.",
    "Why was the computer cold? It left its Windows open.",
    "I would tell you a joke about time, but it's not the right moment.")
private val FACTS = listOf(
    "Honey never spoils. Edible honey has been found in ancient Egyptian tombs.",
    "A day on Venus is longer than a year on Venus.",
    "Octopuses have three hearts and blue blood.",
    "Bananas are slightly radioactive because of their potassium.",
    "Light from the Sun takes about eight minutes to reach Earth.",
    "There are more possible chess games than atoms in the observable universe.",
    "Sharks existed before trees did.")
private val QUOTES = listOf(
    "The best way to predict the future is to build it.",
    "Discipline is choosing between what you want now and what you want most.",
    "Small steps every day beat giant leaps once a year.",
    "You don't have to be great to start, but you have to start to be great.",
    "Focus is saying no to a thousand good ideas.",
    "Energy and persistence conquer all things.")
private val BALL = listOf("It is certain.", "Without a doubt.", "Yes, definitely.", "Most likely.", "Ask again later.",
    "Cannot predict now.", "Don't count on it.", "My sources say no.", "Very doubtful.", "Signs point to yes.")

internal fun torch(ctx: Context, on: Boolean): Boolean = try {
    val cm = ctx.getSystemService(CameraManager::class.java)
    val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    if (id == null) false else { cm.setTorchMode(id, on); ChronosEngine.torch.value = on; true }
} catch (e: Exception) { false }

private fun audio(ctx: Context) = ctx.getSystemService(AudioManager::class.java)
private fun vol(ctx: Context, dir: Int, ok: String): String {
    audio(ctx).adjustStreamVolume(AudioManager.STREAM_MUSIC, dir, 0); return ok
}
internal fun volTo(ctx: Context, pct: Int): String {
    val a = audio(ctx); val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    a.setStreamVolume(AudioManager.STREAM_MUSIC, (max * pct.coerceIn(0, 100) / 100f).toInt(), 0)
    return "Volume at ${pct.coerceIn(0, 100)} percent"
}
/** Media keys work even with the screen off. */
internal fun media(ctx: Context, key: Int, ok: String): String {
    val a = audio(ctx)
    a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
    a.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
    return ok
}
internal fun go(ctx: Context, i: Intent, ok: String): String {
    // Android silently blocks one app opening another from the background, so be honest instead of saying "Opening..."
    if (!ChronosEngine.appVisible && !Settings.canDrawOverlays(ctx)) return "I can only do that while PCL is open on screen. Open PCL and ask again"
    return try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); ok } catch (e: Exception) { "I couldn't open that" }
}

internal fun openApp(ctx: Context, name: String): String {
    val q = name.trim()
    if (q.isBlank()) return "Which app should I open?"
    val pm = ctx.packageManager
    val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
    val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase(Locale.US) == q }
        ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase(Locale.US).contains(q) }
        ?: return "I couldn't find an app called $q"
    val i = pm.getLaunchIntentForPackage(hit.activityInfo.packageName) ?: return "I couldn't open $q"
    return go(ctx, i, "Opening ${hit.loadLabel(pm)}")
}

private var stopwatchStart = 0L

object Commands {
    /** The words after the wake phrase; set by the engine just before a command runs. */
    var body = ""

    private fun on(b: Boolean) = if (b) "engaged" else "offline"
    private fun battery(ctx: Context): String {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        return "Battery is at ${bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)} percent" + if (bm.isCharging) " and charging" else ""
    }
    private fun clock() = SimpleDateFormat("h:mm a", Locale.US).format(Date())
    private fun network(ctx: Context): String {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        return when {
            caps == null -> "You are offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Connected to Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Connected through mobile data"
            else -> "You are online"
        }
    }
    private fun gb(b: Long) = "%.1f".format(Locale.US, b / 1_073_741_824.0)
    private fun storage(): String {
        val s = StatFs(Environment.getDataDirectory().path)
        return "${gb(s.availableBytes)} gigabytes free of ${gb(s.totalBytes)}"
    }
    private fun uptime(): String = human(SystemClock.elapsedRealtime())
    private fun after(vararg keys: String): String {
        for (k in keys) { val i = body.indexOf(k); if (i >= 0) return body.substring(i + k.length).trim() }
        return ""
    }

    /** Only short phrases count, so a long question that happens to contain "stop" or "that is all" isn't mistaken for "close". */
    fun isEyeClose(s: String): Boolean {
        if (s.split(" ").size > 6) return false
        return Names.eyeOff(s) || s.has("goodbye", "that is all", "close the eye", "end chat")
    }

    /** Commands that carry a free-form payload may be longer than the short-phrase limit. */
    fun openEnded(s: String) = listOf("ask", "take a note", "make a note", "add a note", "note", "remember", "remind me", "search for",
        "look up", "say", "repeat after me", "spell", "calculate", "what is", "open", "set").any { s == it || s.startsWith("$it ") }

    /** Commands that carry free text (a note, a message to say...) win over keyword commands, so "take a note about the timer" is a note. */
    private val PAYLOAD_LEADS = listOf("ask ", "say ", "spell ", "note ", "take a note", "make a note", "add a note", "remember that",
        "remind me", "search for", "look up", "repeat after me")
    private val PAYLOAD_PHRASES = setOf("ask <anything>", "say <anything>", "spell <word>", "take a note <anything>",
        "remind me in ten minutes to <task>", "search for  <anything>")

    fun match(body: String): Cmd? {
        if (PAYLOAD_LEADS.any { body.startsWith(it) })
            all.firstOrNull { it.phrase in PAYLOAD_PHRASES && it.match(body) }?.let { return it }
        return all.firstOrNull { it.match(body) }
    }

    /** Order matters: specific commands first, general ones last. */
    val all: List<Cmd> = listOf(
        // ---- MODES (they answer to whatever names the user chose in STYLE) ----
        Cmd("MODES", "disengage combat mode", "Turns combat mode off.", { Names.combatOff(it) }) { ChronosEngine.setCombat(false); Names.combatReply(false) },
        Cmd("MODES", "disengage quantum focus", "Turns quantum focus off.", { Names.quantumOff(it) }) { ChronosEngine.setQuantum(false); Names.quantumReply(false) },
        Cmd("MODES", "stand down  /  all clear", "Turns off combat mode and quantum focus together.", { it.has("stand down", "all clear", "all modes off") }) { ChronosEngine.setCombat(false); ChronosEngine.setQuantum(false); "All modes have been disengaged" },
        Cmd("MODES", "red alert", "Instantly engages combat mode.", { it.has("red alert") }) { ChronosEngine.setCombat(true); "Red alert. " + Names.combatReply(true) },
        Cmd("MODES", "combat mode", "Engages combat mode.", { Names.combat(it) }) { ChronosEngine.setCombat(true); Names.combatReply(true) },
        Cmd("MODES", "quantum focus", "Approves and activates quantum focus.", { Names.quantum(it) }) { ChronosEngine.setQuantum(true); Names.quantumReply(true) },
        Cmd("MODES", "stop whispering  /  normal mode", "Returns listening to normal.", { (it.has("whisper") && it.off()) || it.has("normal mode", "stop whispering") }) { ChronosEngine.setWhisper(false); "Whisper mode disabled" },
        Cmd("MODES", "whisper mode", "Listens harder for quiet speech (online recognition, waits longer) and answers quietly.", { it.has("whisper") }) { ChronosEngine.setWhisper(true); "Whisper mode enabled. I am listening closely" },
        // ---- THIRD EYE ----
        Cmd("THIRD EYE (A.I.)", "close third eye", "Leaves chatbot mode and returns to commands.", { Names.eyeOff(it) }) { ChronosEngine.closeEye() },
        Cmd("THIRD EYE (A.I.)", "ask <anything>", "One quick question to the A.I. without staying in chatbot mode.", { it.startsWith("ask ") && (!Names.eye(it) || Names.askLeadIsEye(it)) }) {
            val q = Names.askRemainder(body)
            if (q.isBlank()) "What would you like to ask?" else { ChronosEngine.ask(q); "" }
        },
        Cmd("THIRD EYE (A.I.)", "open third eye", "Connects to the online chatbot. Then just talk — every sentence goes to her.", { Names.eye(it) }) { ChronosEngine.openEye() },
        // ---- SYSTEM ----
        Cmd("SYSTEM", "flashlight off", "Turns the phone flashlight off.", { it.has("flashlight", "flash light", "torch") && it.off() }) { if (torch(it, false)) "Flashlight off" else "Flashlight unavailable" },
        Cmd("SYSTEM", "flashlight on", "Turns the phone flashlight on.", { it.has("flashlight", "flash light", "torch") }) { if (torch(it, true)) "Flashlight on" else "Flashlight unavailable" },
        Cmd("SYSTEM", "good night", "Disengages everything and goes to standby.", { it.has("good night", "goodnight") }) { ChronosEngine.allOff(); "Good night. All systems standing by" },
        Cmd("SYSTEM", "disengage", "Disengages Chronos Lock. Afterwards you must say “Chronos Lock” again.", { it.has("disengage", "deactivate") }) { ChronosEngine.locked.value = false; ChronosEngine.thirdEye.value = false; "Chronos Lock Disengaged" },
        Cmd("SYSTEM", "engage", "Engages Chronos Lock (same as the big button).", { it.has("engage", "activate") }) { ChronosEngine.locked.value = true; "Chronos Lock Engaged" },
        Cmd("SYSTEM", "phone settings", "Opens Android settings (PCL must be on screen).", { it.has("phone settings", "device settings", "android settings") }) { go(it, Intent(Settings.ACTION_SETTINGS), "Opening phone settings") },
        Cmd("SYSTEM", "wifi settings", "Opens the Wi-Fi settings.", { it.has("wifi settings", "wi-fi settings", "wi fi settings") }) { go(it, Intent(Settings.ACTION_WIFI_SETTINGS), "Opening Wi-Fi settings") },
        Cmd("SYSTEM", "bluetooth settings", "Opens the Bluetooth settings.", { it.has("bluetooth") }) { go(it, Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Opening Bluetooth settings") },
        Cmd("SYSTEM", "display settings", "Opens the display and brightness settings.", { it.has("display settings", "brightness") }) { go(it, Intent(Settings.ACTION_DISPLAY_SETTINGS), "Opening display settings") },
        // ---- MEDIA ----
        Cmd("MEDIA", "pause music  /  stop music", "Pauses whatever is playing. Works with the screen off.", { it.has("music", "song", "playback", "video") && (it.off() || it.has("pause")) || it == "pause" }) { media(it, KeyEvent.KEYCODE_MEDIA_PAUSE, "Paused") },
        Cmd("MEDIA", "play music  /  resume", "Resumes playback.", { it.has("play music", "resume", "play the music", "continue music") || it == "play" }) { media(it, KeyEvent.KEYCODE_MEDIA_PLAY, "Playing") },
        Cmd("MEDIA", "next song  /  skip", "Skips to the next track.", { it.has("next song", "next track", "next music") || it.hasW("skip") }) { media(it, KeyEvent.KEYCODE_MEDIA_NEXT, "Next track") },
        Cmd("MEDIA", "previous song", "Goes back one track.", { it.has("previous song", "previous track", "last song", "go back a song") }) { media(it, KeyEvent.KEYCODE_MEDIA_PREVIOUS, "Previous track") },
        // ---- TIMERS & ALARMS ----
        Cmd("TIMERS & ALARMS", "cancel timer", "Cancels every running timer and reminder.", { it.has("timer", "reminder") && it.off() }) { ChronosEngine.cancelTimer(); "Timers cancelled" },
        Cmd("TIMERS & ALARMS", "how much time is left", "Reads the remaining time on the next timer.", { it.has("time left", "time is left", "time remaining", "how long left") || (it.has("timer") && it.has("left", "remaining", "how much")) }) {
            val ms = ChronosEngine.timerLeftMs(); if (ms <= 0) "No timer is running" else "${human(ms)} remaining"
        },
        Cmd("TIMERS & ALARMS", "remind me in ten minutes to <task>", "Speaks your reminder when the time is up.", { it.has("remind me") }) {
            val ms = timerMs(body); val what = body.substringAfter(" to ", "").trim()
            if (ms == null) "Tell me when, for example, remind me in ten minutes to stretch"
            else { ChronosEngine.setTimer(ms, what); "Okay. I will remind you in ${human(ms)}" }
        },
        Cmd("TIMERS & ALARMS", "set a timer for five minutes", "Any number of seconds, minutes or hours. Speaks when finished. Run several at once.", { it.has("timer") }) {
            val ms = timerMs(body)
            if (ms == null) "Tell me how long, for example, set a timer for five minutes" else { ChronosEngine.setTimer(ms, ""); "Timer set for ${human(ms)}" }
        },
        Cmd("TIMERS & ALARMS", "start stopwatch  /  stop stopwatch", "Counts up until you stop it.", { it.has("stopwatch") }) {
            if (body.has("start", "begin", "go") || stopwatchStart == 0L) { stopwatchStart = SystemClock.elapsedRealtime(); "Stopwatch started" }
            else { val t = SystemClock.elapsedRealtime() - stopwatchStart; stopwatchStart = 0L; "Stopwatch stopped at ${human(t)}" }
        },
        Cmd("TIMERS & ALARMS", "show alarms", "Opens your alarm list (PCL must be on screen).", { it.has("alarm") && it.has("show", "list", "cancel", "delete", "open") }) { go(it, Intent(AlarmClock.ACTION_SHOW_ALARMS), "Here are your alarms") },
        Cmd("TIMERS & ALARMS", "set an alarm for seven thirty a m", "Creates a real alarm in your clock app (PCL must be on screen).", { it.has("alarm") }) {
            val t = parseClock(body)
            if (t == null) "Tell me the time, for example, set an alarm for seven thirty a m"
            else go(it, Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, t.first).putExtra(AlarmClock.EXTRA_MINUTES, t.second).putExtra(AlarmClock.EXTRA_SKIP_UI, true), "Alarm set for ${clockText(t.first, t.second)}")
        },
        // ---- NOTES ----
        Cmd("NOTES", "clear notes", "Deletes every saved note.", { it.has("clear notes", "delete notes", "erase notes", "clear my notes", "delete my notes", "erase my notes", "delete all notes") }) { ChronosEngine.clearNotes(); "All notes deleted" },
        Cmd("NOTES", "read my notes", "Reads your saved notes aloud.", { it.has("read my notes", "read notes", "my notes", "what are my notes", "read the notes") }) {
            val n = ChronosEngine.notes.value
            if (n.isEmpty()) "You have no notes" else "You have ${n.size} note" + (if (n.size == 1) "" else "s") + ". " + n.takeLast(5).joinToString(". ")
        },
        Cmd("NOTES", "take a note <anything>", "Saves a note. They also appear on the Notes screen.", { it.has("take a note", "make a note", "add a note", "note that", "remember that") || it.startsWith("note ") }) {
            val n = after("take a note", "make a note", "add a note", "note that", "remember that", "note")
            if (n.isBlank()) "What should I note?" else { ChronosEngine.addNote(n); "Noted" }
        },
        // ---- INFO ----
        Cmd("INFO", "what is five plus three", "Does arithmetic: plus, minus, times, divided by.", { calc(it) != null }) { calc(body) ?: "I couldn't calculate that" },
        Cmd("INFO", "what time is it", "Tells you the time.", { it.has("what time", "the time", "time is it", "current time") }) { "The time is ${clock()}" },
        Cmd("INFO", "what is the date", "Tells you today's date.", { it.has("what day", "the date", "today's date", "what is today", "what's today") }) { "Today is " + SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date()) },
        Cmd("INFO", "battery status", "Reads battery level and charging state.", { it.has("battery", "power level") }) { battery(it) },
        Cmd("INFO", "network status", "Tells you if you are on Wi-Fi, mobile data or offline.", { it.has("network", "internet", "am i online", "connection") }) { network(it) },
        Cmd("INFO", "storage space", "How much storage is free.", { it.has("storage", "free space", "disk space") }) { storage() },
        Cmd("INFO", "phone info", "Model and Android version.", { it.has("phone info", "what phone", "which phone", "device info") }) { "This is a ${Build.MANUFACTURER} ${Build.MODEL} running Android ${Build.VERSION.RELEASE}" },
        Cmd("INFO", "uptime", "How long the phone has been on.", { it.has("uptime", "how long has the phone") }) { "The phone has been running for ${uptime()}" },
        Cmd("INFO", "diagnostics", "Full system check: modes, battery, network, time.", { it.has("diagnostic", "system check") || it.hasW("scan") }) { "Diagnostics complete. ${battery(it)}. ${network(it)}. The time is ${clock()}. All systems nominal" },
        Cmd("INFO", "status report", "Reads out the state of every system.", { it.hasW("status", "report") }) {
            "Chronos Lock is ${on(ChronosEngine.locked.value)}. ${Names.combat} is ${on(ChronosEngine.combat.value)}. ${Names.quantum} is ${on(ChronosEngine.quantum.value)}. Whisper mode is ${on(ChronosEngine.whisper.value)}."
        },
        Cmd("INFO", "good morning / afternoon / evening", "A greeting that fits the time of day.", { it.has("good morning", "good afternoon", "good evening") }) {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            (if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening") + ". It is ${clock()}. ${battery(it)}"
        },
        // ---- FUN ----
        Cmd("EXTRAS", "tell me a joke", "A random joke.", { it.hasW("joke") || it.has("make me laugh") }) { JOKES.random() },
        Cmd("EXTRAS", "tell me a fact", "A random fun fact.", { it.hasW("fact") || it.has("did you know") }) { FACTS.random() },
        Cmd("EXTRAS", "motivate me", "A short motivational quote.", { it.has("motivate", "inspire me") || it.hasW("quote") }) { QUOTES.random() },
        Cmd("EXTRAS", "eight ball  /  should I …", "Shake the magic eight ball.", { it.has("eight ball", "8 ball", "magic ball") || it.startsWith("should i") }) { BALL.random() },
        Cmd("EXTRAS", "rock paper scissors", "She picks one.", { it.has("rock paper") }) { "I choose " + listOf("rock", "paper", "scissors").random() },
        Cmd("EXTRAS", "flip a coin", "Heads or tails.", { it.hasW("coin") }) { if (Random.nextBoolean()) "Heads" else "Tails" },
        Cmd("EXTRAS", "roll a dice", "Rolls a six-sided die.", { it.has("dice", "roll a die") }) { "You rolled a ${Random.nextInt(1, 7)}" },
        Cmd("EXTRAS", "random number", "A number from 1 to 100.", { it.has("random number") }) { "Your number is ${Random.nextInt(1, 101)}" },
        Cmd("EXTRAS", "spell <word>", "Spells a word letter by letter.", { it.startsWith("spell ") }) {
            val w = body.removePrefix("spell").trim().replace(" ", "")
            if (w.isBlank()) "Which word?" else w.uppercase(Locale.US).toList().joinToString(", ")
        },
        Cmd("EXTRAS", "say <anything>", "She repeats whatever you say after “say”.", { it.startsWith("say ") && !it.has("say that again") || it.startsWith("repeat after me") }) {
            body.removePrefix("repeat after me").removePrefix("say").trim().ifBlank { "Say what?" }
        },
        Cmd("EXTRAS", "repeat that", "Says her last reply again.", { it.has("repeat", "say that again") }) { ChronosEngine.lastReply.value.ifBlank { "I have nothing to repeat" } },
        Cmd("EXTRAS", "quiet  /  silence", "Stops her talking.", { it.has("quiet", "silence", "hush", "stop talking", "be quiet") }) { ChronosEngine.hush(); "" },
        Cmd("EXTRAS", "hello  /  thank you", "Small talk.", { it.hasW("hello", "hi", "thanks") || it.has("thank you", "how are you") }) { "Hello. Chronos Lock is online and ready" },
        // ---- VOICE ----
        Cmd("VOICE", "speak faster", "Raises her speaking speed.", { it.has("faster", "speed up") }) { ChronosEngine.setRate((ChronosEngine.rate.value + 0.1f).coerceAtMost(1.3f)); "Speaking faster" },
        Cmd("VOICE", "speak slower", "Lowers her speaking speed.", { it.has("slower", "slow down") }) { ChronosEngine.setRate((ChronosEngine.rate.value - 0.1f).coerceAtLeast(0.7f)); "Speaking slower" },
        Cmd("VOICE", "reset voice", "Back to natural pitch and speed.", { it.has("reset voice", "natural voice") }) { ChronosEngine.setRate(1f); ChronosEngine.setPitch(1f); "Voice reset to natural" },
        Cmd("VOICE", "change voice", "Cycles to the next installed voice — keep saying it until you like one.", { it.has("change voice", "different voice", "next voice") }) { ChronosEngine.nextVoice() },
        // ---- VOLUME ----
        Cmd("VOLUME", "set volume to fifty percent", "Sets media volume to an exact percentage.", { it.has("volume") && Regex("""\d+""").containsMatchIn(digitize(it)) }) {
            volTo(it, Regex("""\d+""").find(digitize(body))?.value?.toInt() ?: 50)
        },
        Cmd("VOLUME", "unmute", "Unmutes media volume.", { it.has("unmute") }) { vol(it, AudioManager.ADJUST_UNMUTE, "Unmuted") },
        Cmd("VOLUME", "mute", "Mutes media volume.", { it.hasW("mute") }) { vol(it, AudioManager.ADJUST_MUTE, "Muted") },
        Cmd("VOLUME", "volume max", "Sets media volume to maximum.", { it.has("volume") && it.has("max", "full") }) { volTo(it, 100) },
        Cmd("VOLUME", "volume half", "Sets media volume to fifty percent.", { it.has("volume") && it.has("half", "middle") }) { volTo(it, 50) },
        Cmd("VOLUME", "volume up  /  louder", "Raises media volume one step.", { (it.has("volume") && it.has("up", "increase", "raise")) || it.has("louder") }) { vol(it, AudioManager.ADJUST_RAISE, "Volume increased") },
        Cmd("VOLUME", "volume down  /  quieter", "Lowers media volume one step.", { (it.has("volume") && it.has("down", "decrease", "lower")) || it.has("quieter") }) { vol(it, AudioManager.ADJUST_LOWER, "Volume decreased") },
        // ---- APPS (Android only allows these while PCL is on screen) ----
        Cmd("APPS", "open camera", "Opens the camera (PCL must be on screen).", { it.has("open camera", "take a photo", "take a picture") }) { go(it, Intent(MediaStore.ACTION_IMAGE_CAPTURE), "Opening camera") },
        Cmd("APPS", "open youtube", "Opens YouTube (PCL must be on screen).", { it.has("youtube") }) { go(it, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")), "Opening YouTube") },
        Cmd("APPS", "open maps", "Opens maps (PCL must be on screen).", { it.has("open maps", "open map", "navigation") }) { go(it, Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=")), "Opening maps") },
        Cmd("APPS", "open browser", "Opens the web browser (PCL must be on screen).", { it.has("open browser", "open google", "open chrome") }) { go(it, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com")), "Opening browser") },
        Cmd("APPS", "search for  <anything>", "Searches Google (PCL must be on screen).", { it.has("search for", "look up") }) {
            val q = body.substringAfter("search for", "").ifBlank { body.substringAfter("look up", "") }.trim()
            if (q.isBlank()) "What should I search for?" else go(it, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))), "Searching for $q")
        },
        // ---- NAVIGATION ----
        Cmd("APP", "show commands  /  help", "Opens the command list.", { it.hasW("help", "commands") || it.has("what can you do") }) { ChronosEngine.tab.value = Screen.HELP; "Here are my commands" },
        Cmd("APP", "show notes", "Opens the Notes screen.", { it.has("show notes", "open notes") }) { ChronosEngine.tab.value = Screen.NOTES; "Here are your notes" },
        Cmd("APP", "open voice settings", "Opens the voice screen.", { it.has("voice settings", "voice menu") }) { ChronosEngine.tab.value = Screen.VOICE; "Opening voice settings" },
        Cmd("APP", "show programs", "Opens the PROGRAMS screen where you build automations.", { it.has("show programs", "open programs", "programs screen", "automations") }) { ChronosEngine.tab.value = Screen.FLOWS; "Here are your programs" },
        Cmd("APP", "open guide  /  setup guide", "Opens the beginner guide (chatbot setup, music, tips).", { it.has("open guide", "setup guide", "show guide", "beginner guide") }) { ChronosEngine.tab.value = Screen.GUIDE; "Here is the guide" },
        Cmd("APP", "dark mode  /  light mode  /  amoled mode", "Switches the app's theme.", { it.has("dark mode", "light mode", "day mode", "night mode", "amoled mode", "black mode") }) {
            val m = if (body.has("light", "day")) "light" else if (body.has("amoled", "black")) "amoled" else "dark"
            Pal.setMode(m); "Switching to $m theme"
        },
        Cmd("APP", "open style  /  themes", "Opens the STYLE screen: colours, fonts, glass and names.", { it.has("theme", "style screen", "open style", "customize", "customise") }) { ChronosEngine.tab.value = Screen.STYLE; "Opening style" },
        Cmd("APP", "open system  /  settings", "Opens the system screen (A.I. key, timeout, whisper).", { it.has("settings", "system screen", "open system") }) { ChronosEngine.tab.value = Screen.SYSTEM; "Opening system settings" },
        Cmd("APP", "go home", "Returns to the main screen.", { it.has("go home", "home screen", "main menu") }) { ChronosEngine.tab.value = Screen.HOME; "Returning home" },
        Cmd("APP", "who are you", "She introduces herself.", { it.has("who are you", "your name", "what are you") }) { "I am Chronos Lock, your personal A.I. assistant. Say open ${Names.eye} to talk with my network core" },
        Cmd("APP", "how many commands", "Counts what she can do.", { it.has("how many commands") }) { "I understand ${all.size} commands" },
        // ---- generic app launcher: must stay last of the 'open' family ----
        Cmd("APPS", "open <any app name>", "Opens any installed app by name, e.g. “open spotify” (PCL must be on screen).", { it.startsWith("open ") || it.startsWith("launch ") || it.startsWith("start ") }) {
            openApp(it, body.removePrefix("open").removePrefix("launch").removePrefix("start").trim().removePrefix("the").trim())
        },
    )
}
