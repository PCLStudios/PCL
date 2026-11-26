package com.pcl.chronoslock

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.provider.Settings
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.random.Random

/**
 * One programmable step. [delay] = seconds to wait before it runs. [cond]/[condArg] = only run IF this is true when its turn comes.
 * [chance] = percent chance that it runs at all (100 = always).
 */
data class Step(
    val id: Long, val type: String, val arg: String = "", val app: String = "", val delay: Int = 0,
    val cond: String = "", val condArg: String = "", val chance: Int = 100
)

/**
 * User-programmable "what happens when X turns on/off". Slots: combat, quantum, lock, eye (chatbot), whisper, each with _on and _off.
 * Each slot is a list of steps run top to bottom. Stored as JSON in SharedPreferences.
 */
object Flows {
    const val OPEN_APP = "open_app"
    const val PLAY = "play"
    const val URL = "url"
    const val KEY = "key"
    const val VOLUME = "volume"
    const val SAY = "say"
    const val TORCH = "torch"
    const val WAIT = "wait"
    const val TIMER = "timer"
    const val NOTE = "note"
    const val MODE = "mode"
    const val SCREEN = "screen"
    const val VIBRATE = "vibrate"
    const val SETTINGS = "settings"
    const val ASK = "ask"
    const val SEARCH = "search"

    val TYPES = listOf(
        OPEN_APP to "OPEN APP", PLAY to "PLAY SONG", URL to "OPEN LINK", KEY to "MEDIA KEY", VOLUME to "VOLUME", SAY to "SAY",
        TORCH to "FLASHLIGHT", WAIT to "WAIT", TIMER to "START TIMER", NOTE to "SAVE NOTE", MODE to "SWITCH MODE", ASK to "ASK A.I.",
        SEARCH to "WEB SEARCH", SETTINGS to "OPEN SETTINGS", SCREEN to "GO TO SCREEN", VIBRATE to "VIBRATE"
    )

    /** One-tap values for steps whose argument is a fixed choice. */
    val CHOICES: Map<String, List<String>> = mapOf(
        KEY to listOf("play", "pause", "playpause", "next", "previous"),
        TORCH to listOf("on", "off", "blink"),
        MODE to listOf("combat on", "combat off", "quantum on", "quantum off", "whisper on", "whisper off", "lock on", "lock off", "chatbot on", "chatbot off"),
        SETTINGS to listOf("wifi", "bluetooth", "display", "sound", "battery saver", "apps", "all settings"),
        SCREEN to listOf("home", "notes", "programs", "voice", "system", "style", "guide", "commands", "chatbot"),
        VOLUME to listOf("0", "25", "50", "75", "100")
    )

    val CONDS = listOf(
        "" to "ALWAYS", "batlow" to "BATTERY BELOW %", "bathigh" to "BATTERY ABOVE %", "charging" to "CHARGING", "unplugged" to "NOT CHARGING",
        "night" to "AT NIGHT (22-6)", "day" to "DAYTIME (6-22)", "after" to "AFTER HOUR", "before" to "BEFORE HOUR",
        "weekday" to "WEEKDAY", "weekend" to "WEEKEND", "online" to "ONLINE", "offline" to "OFFLINE", "music" to "MUSIC PLAYING", "nomusic" to "NO MUSIC"
    )
    fun condNeedsNumber(c: String) = c == "batlow" || c == "bathigh" || c == "after" || c == "before"

    val SLOTS = listOf("combat_on", "combat_off", "quantum_on", "quantum_off", "lock_on", "lock_off", "eye_on", "eye_off", "whisper_on", "whisper_off")

    val TEMPLATES: List<Pair<String, List<Step>>> = listOf(
        "FOCUS" to listOf(
            Step(0, SAY, "Focus activated. Silencing distractions."), Step(0, VOLUME, "35"),
            Step(0, PLAY, "lofi focus beats\ndeep focus music\nbrown noise", delay = 2)
        ),
        "WORKOUT" to listOf(
            Step(0, SAY, "Let's go. Time to move."), Step(0, VOLUME, "80"), Step(0, PLAY, "workout motivation\ngym hype mix\nrun playlist", delay = 2),
            Step(0, TIMER, "30", "Workout finished", delay = 3)
        ),
        "GOOD NIGHT" to listOf(
            Step(0, SAY, "Good night. It is {time}. Battery is {battery} percent."), Step(0, KEY, "pause", delay = 3),
            Step(0, VOLUME, "20"), Step(0, TORCH, "off")
        ),
        "MORNING" to listOf(
            Step(0, SAY, "Good morning. It is {time} on {date}. Battery is {battery} percent."), Step(0, VOLUME, "50", cond = "day"),
            Step(0, PLAY, "morning chill\nupbeat morning songs", delay = 2, cond = "day")
        )
    )

    private val flows: Map<String, MutableStateFlow<List<Step>>> = SLOTS.associateWith { MutableStateFlow<List<Step>>(emptyList()) }
    val disabled = MutableStateFlow<Set<String>>(emptySet())
    private var prefs: SharedPreferences? = null
    private var nextId = System.currentTimeMillis()
    private val main = Handler(Looper.getMainLooper())

    fun flow(slot: String): MutableStateFlow<List<Step>> = flows.getValue(slot)
    fun get(slot: String): List<Step> = flows.getValue(slot).value

    fun init(p: SharedPreferences) {
        prefs = p
        for (slot in SLOTS) flows.getValue(slot).value = runCatching { parse(p.getString("flow_$slot", "[]") ?: "[]") }.getOrDefault(emptyList())
        disabled.value = (p.getString("flows_off", "") ?: "").split(",").filter { it.isNotBlank() }.toSet()
    }

    private fun save(slot: String) { prefs?.edit()?.putString("flow_$slot", toJson(get(slot)))?.apply() }
    /** @return false when the free limit of steps is reached (a Power Pass lifts it). */
    fun add(slot: String, type: String): Boolean {
        if (get(slot).size >= Pass.FREE_STEPS && !Pass.active) return false
        flows.getValue(slot).value = get(slot) + Step(nextId++, type); save(slot); return true
    }
    fun update(slot: String, s: Step) { flows.getValue(slot).value = get(slot).map { if (it.id == s.id) s else it }; save(slot) }
    fun remove(slot: String, id: Long) { flows.getValue(slot).value = get(slot).filter { it.id != id }; save(slot) }
    fun move(slot: String, id: Long, dir: Int) {
        val l = get(slot).toMutableList()
        val i = l.indexOfFirst { it.id == id }
        val j = i + dir
        if (i < 0 || j < 0 || j >= l.size) return
        val t = l[i]; l[i] = l[j]; l[j] = t
        flows.getValue(slot).value = l; save(slot)
    }
    fun addTemplate(slot: String, name: String): Boolean {
        val t = TEMPLATES.firstOrNull { it.first == name }?.second ?: return true
        if (get(slot).size + t.size > Pass.FREE_STEPS && !Pass.active) return false
        flows.getValue(slot).value = get(slot) + t.map { it.copy(id = nextId++) }; save(slot); return true
    }
    fun setEnabled(slot: String, on: Boolean) {
        disabled.value = if (on) disabled.value - slot else disabled.value + slot
        prefs?.edit()?.putString("flows_off", disabled.value.joinToString(","))?.apply()
    }

    private fun toJson(l: List<Step>): String {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.type).put("a", it.arg).put("p", it.app).put("d", it.delay).put("c", it.cond).put("ca", it.condArg).put("ch", it.chance)) }
        return a.toString()
    }

    private fun parse(s: String): List<Step> {
        val a = JSONArray(s)
        return List(a.length()) {
            val o = a.getJSONObject(it)
            Step(nextId++, o.optString("t"), o.optString("a"), o.optString("p"), o.optInt("d"), o.optString("c"), o.optString("ca"), o.optInt("ch", 100))
        }
    }

    /** Runs a slot (unless the user switched it off). */
    fun runSlot(ctx: Context, slot: String) { if (slot !in disabled.value) run(ctx, get(slot)) }

    /** Runs the steps in order, honouring each step's wait. Conditions are checked when the step's turn comes. Never throws. */
    fun run(ctx: Context, steps: List<Step>) {
        var t = 0L
        for (s in steps) {
            t += s.delay.coerceIn(0, 600) * 1000L
            if (s.type == WAIT) { t += (s.arg.filter { it.isDigit() }.toIntOrNull() ?: 1).coerceIn(0, 600) * 1000L; continue }
            main.postDelayed({ runCatching { if (passes(ctx, s)) exec(ctx, s) } }, t)
        }
    }

    // ---- conditions ----
    private fun batteryPct(ctx: Context) = ctx.getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    private fun passes(ctx: Context, s: Step): Boolean {
        if (s.chance < 100 && Random.nextInt(100) >= s.chance) return false
        val n = s.condArg.filter { it.isDigit() }.toIntOrNull()
        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return when (s.cond) {
            "batlow" -> batteryPct(ctx) < (n ?: 20)
            "bathigh" -> batteryPct(ctx) > (n ?: 50)
            "charging" -> ctx.getSystemService(BatteryManager::class.java).isCharging
            "unplugged" -> !ctx.getSystemService(BatteryManager::class.java).isCharging
            "night" -> hour >= 22 || hour < 6
            "day" -> hour in 6..21
            "after" -> hour >= (n ?: 0)
            "before" -> hour < (n ?: 24)
            "weekday" -> dow != Calendar.SATURDAY && dow != Calendar.SUNDAY
            "weekend" -> dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
            "online" -> ChronosEngine.isOnline()
            "offline" -> !ChronosEngine.isOnline()
            "music" -> ctx.getSystemService(android.media.AudioManager::class.java).isMusicActive
            "nomusic" -> !ctx.getSystemService(android.media.AudioManager::class.java).isMusicActive
            else -> true
        }
    }

    /** One line chosen at random: several lines = "pick one of these". */
    private fun pick(arg: String): String {
        val l = arg.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (l.isEmpty()) "" else l[Random.nextInt(l.size)]
    }

    /** {time} {date} {battery} placeholders for SAY / NOTE / ASK. */
    private fun expand(ctx: Context, text: String): String = text
        .replace("{time}", SimpleDateFormat("h:mm a", Locale.US).format(Date()))
        .replace("{date}", SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date()))
        .replace("{battery}", runCatching { batteryPct(ctx).toString() }.getOrDefault("unknown"))

    private fun packageOf(ctx: Context, name: String): String? {
        val q = name.trim().lowercase(Locale.US)
        if (q.isEmpty()) return null
        val pm = ctx.packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase(Locale.US) == q }
            ?: apps.firstOrNull { it.loadLabel(pm).toString().lowercase(Locale.US).contains(q) }
        return hit?.activityInfo?.packageName
    }

    private fun exec(ctx: Context, s: Step) {
        when (s.type) {
            OPEN_APP -> openApp(ctx, s.arg.trim().lowercase(Locale.US))
            PLAY -> {
                val q = pick(s.arg)
                if (q.isNotEmpty()) {
                    val i = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
                        .putExtra(SearchManager.QUERY, q)
                        .putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*")
                    packageOf(ctx, s.app)?.let { i.setPackage(it) }
                    go(ctx, i, "")
                }
            }
            URL -> {
                val u = pick(s.arg)
                if (u.isNotEmpty()) {
                    val i = Intent(Intent.ACTION_VIEW, Uri.parse(u))
                    packageOf(ctx, s.app)?.let { i.setPackage(it) }
                    go(ctx, i, "")
                }
            }
            KEY -> {
                val code = when (s.arg.trim().lowercase(Locale.US)) {
                    "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
                    "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                    "previous", "back" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                    "playpause", "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    else -> KeyEvent.KEYCODE_MEDIA_PLAY
                }
                media(ctx, code, "")
            }
            VOLUME -> volTo(ctx, s.arg.filter { it.isDigit() }.toIntOrNull() ?: 50)
            SAY -> pick(s.arg).let { if (it.isNotBlank()) ChronosEngine.announce(expand(ctx, it)) }
            TORCH -> {
                val a = s.arg.trim().lowercase(Locale.US)
                if (a.startsWith("blink")) for (i in 0..5) main.postDelayed({ torch(ctx, i % 2 == 0) }, i * 220L)
                else torch(ctx, !a.startsWith("off"))
            }
            TIMER -> {
                val min = s.arg.filter { it.isDigit() }.toIntOrNull() ?: 1
                ChronosEngine.setTimer(min.coerceIn(1, 600) * 60_000L, s.app.trim())
            }
            NOTE -> if (s.arg.isNotBlank()) ChronosEngine.addNote(expand(ctx, s.arg.trim()))
            MODE -> switchMode(s.arg.lowercase(Locale.US))
            SCREEN -> {
                val a = s.arg.lowercase(Locale.US)
                ChronosEngine.tab.value = when {
                    a.contains("note") -> Screen.NOTES
                    a.contains("program") -> Screen.FLOWS
                    a.contains("voice") -> Screen.VOICE
                    a.contains("system") -> Screen.SYSTEM
                    a.contains("style") || a.contains("theme") -> Screen.STYLE
                    a.contains("guide") || a.contains("help") -> Screen.GUIDE
                    a.contains("command") -> Screen.HELP
                    a.contains("chat") || a.contains("eye") || a.contains(Names.clean(Names.eye)) -> Screen.EYE
                    else -> Screen.HOME
                }
            }
            VIBRATE -> {
                val ms = (s.arg.filter { it.isDigit() }.toLongOrNull() ?: 300L).coerceIn(50L, 3000L)
                ctx.getSystemService(Vibrator::class.java)?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
            SETTINGS -> {
                val a = s.arg.lowercase(Locale.US)
                val action = when {
                    a.contains("wifi") || a.contains("wi-fi") -> Settings.ACTION_WIFI_SETTINGS
                    a.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
                    a.contains("display") || a.contains("bright") -> Settings.ACTION_DISPLAY_SETTINGS
                    a.contains("sound") || a.contains("volume") -> Settings.ACTION_SOUND_SETTINGS
                    a.contains("battery") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
                    a.contains("app") -> Settings.ACTION_APPLICATION_SETTINGS
                    else -> Settings.ACTION_SETTINGS
                }
                go(ctx, Intent(action), "")
            }
            ASK -> if (s.arg.isNotBlank()) ChronosEngine.ask(expand(ctx, pick(s.arg)))
            SEARCH -> pick(s.arg).let { if (it.isNotBlank()) go(ctx, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(expand(ctx, it)))), "") }
        }
    }

    private fun switchMode(a: String) {
        val on = !(a.contains("off") || a.contains("disengage") || a.contains("deactivate") || a.contains("stop"))
        when {
            a.contains("combat") -> ChronosEngine.setCombat(on)
            a.contains("quantum") -> ChronosEngine.setQuantum(on)
            a.contains("whisper") -> ChronosEngine.setWhisper(on)
            a.contains("lock") -> { ChronosEngine.locked.value = on; if (!on) ChronosEngine.thirdEye.value = false }
            a.contains("chat") || a.contains("eye") -> { if (on) ChronosEngine.openEye() else ChronosEngine.closeEye() }
        }
    }
}
