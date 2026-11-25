package com.pcl.chronoslock

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.random.Random

/** One programmable step. [delay] = seconds to wait after the previous step before this one runs. */
data class Step(val id: Long, val type: String, val arg: String = "", val app: String = "", val delay: Int = 0)

/**
 * User-programmable "what happens when this mode turns on/off". Four slots: combat_on, combat_off, quantum_on, quantum_off.
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

    val TYPES = listOf(OPEN_APP to "OPEN APP", PLAY to "PLAY SONG", URL to "OPEN LINK", KEY to "MEDIA KEY",
        VOLUME to "VOLUME", SAY to "SAY", TORCH to "FLASHLIGHT")
    val SLOTS = listOf("combat_on", "combat_off", "quantum_on", "quantum_off")

    private val flows: Map<String, MutableStateFlow<List<Step>>> = SLOTS.associateWith { MutableStateFlow<List<Step>>(emptyList()) }
    private var prefs: SharedPreferences? = null
    private var nextId = System.currentTimeMillis()
    private val main = Handler(Looper.getMainLooper())

    fun flow(slot: String): MutableStateFlow<List<Step>> = flows.getValue(slot)
    fun get(slot: String): List<Step> = flows.getValue(slot).value

    fun init(p: SharedPreferences) {
        prefs = p
        for (slot in SLOTS) flows.getValue(slot).value = runCatching { parse(p.getString("flow_$slot", "[]") ?: "[]") }.getOrDefault(emptyList())
    }

    private fun save(slot: String) { prefs?.edit()?.putString("flow_$slot", toJson(get(slot)))?.apply() }
    fun add(slot: String, type: String) { flows.getValue(slot).value = get(slot) + Step(nextId++, type); save(slot) }
    fun update(slot: String, s: Step) { flows.getValue(slot).value = get(slot).map { if (it.id == s.id) s else it }; save(slot) }
    fun remove(slot: String, id: Long) { flows.getValue(slot).value = get(slot).filter { it.id != id }; save(slot) }

    private fun toJson(l: List<Step>): String {
        val a = JSONArray()
        l.forEach { a.put(JSONObject().put("t", it.type).put("a", it.arg).put("p", it.app).put("d", it.delay)) }
        return a.toString()
    }

    private fun parse(s: String): List<Step> {
        val a = JSONArray(s)
        return List(a.length()) {
            val o = a.getJSONObject(it)
            Step(nextId++, o.optString("t"), o.optString("a"), o.optString("p"), o.optInt("d"))
        }
    }

    /** Runs the steps in order, honouring each step's wait. Never throws. */
    fun run(ctx: Context, steps: List<Step>) {
        var t = 0L
        for (s in steps) {
            t += s.delay.coerceIn(0, 600) * 1000L
            main.postDelayed({ runCatching { exec(ctx, s) } }, t)
        }
    }

    /** One line chosen at random: several lines = "pick one of these". */
    private fun pick(arg: String): String {
        val l = arg.lines().map { it.trim() }.filter { it.isNotEmpty() }
        return if (l.isEmpty()) "" else l[Random.nextInt(l.size)]
    }

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
            VOLUME -> volTo(ctx, s.arg.trim().toIntOrNull() ?: 50)
            SAY -> if (s.arg.isNotBlank()) ChronosEngine.announce(s.arg.trim())
            TORCH -> torch(ctx, !s.arg.trim().lowercase(Locale.US).startsWith("off"))
        }
    }
}
