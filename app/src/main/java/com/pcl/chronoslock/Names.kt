package com.pcl.chronoslock

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * User-renamable names for Combat Mode, Quantum Focus and the chatbot (Third Eye).
 * Voice commands match whatever name is set ("engage lol"), and her replies use it too.
 * Backed by Compose state, so the UI updates the moment a name changes.
 */
object Names {
    const val D_COMBAT = "Combat Mode"
    const val D_QUANTUM = "Quantum Focus"
    const val D_EYE = "Chatbot"

    var combat by mutableStateOf(D_COMBAT)
        private set
    var quantum by mutableStateOf(D_QUANTUM)
        private set
    var eye by mutableStateOf(D_EYE)
        private set

    private var prefs: SharedPreferences? = null
    private val EYE_ALIASES = listOf("third eye", "thirdeye", "3rd eye", "third i")
    private val OFFW = setOf("off", "disengage", "disengaged", "deactivate", "deactivated", "stop", "cancel", "close", "exit", "end", "quit", "disable")

    fun init(p: SharedPreferences) {
        prefs = p
        combat = p.getString("nameCombat", D_COMBAT)?.ifBlank { D_COMBAT } ?: D_COMBAT
        quantum = p.getString("nameQuantum", D_QUANTUM)?.ifBlank { D_QUANTUM } ?: D_QUANTUM
        eye = p.getString("nameEye", D_EYE)?.ifBlank { D_EYE } ?: D_EYE
    }

    /** Saves the three names. Returns an error message, or null when saved. */
    fun saveAll(c: String, q: String, e: String): String? {
        val cc = c.trim().take(24).ifBlank { D_COMBAT }
        val qq = q.trim().take(24).ifBlank { D_QUANTUM }
        val ee = e.trim().take(24).ifBlank { D_EYE }
        val a = clean(cc); val b = clean(qq); val d = clean(ee)
        if (a == b || a == d || b == d) return "Each name must be different, or I won't know which one you mean."
        combat = cc; quantum = qq; eye = ee
        prefs?.edit()?.putString("nameCombat", cc)?.putString("nameQuantum", qq)?.putString("nameEye", ee)?.apply()
        return null
    }

    fun isDefault(name: String, def: String) = name.equals(def, ignoreCase = true)

    /** Lower-case words only, so "Engage Lol!" and "engage lol" compare equal. */
    fun clean(s: String): String = s.lowercase(Locale.US).replace(Regex("""[^a-z0-9' ]"""), " ").replace(Regex("""\s+"""), " ").trim()

    fun has(utt: String, name: String): Boolean {
        val n = clean(name)
        return n.isNotEmpty() && " ${clean(utt)} ".contains(" $n ")
    }

    private fun strip(utt: String, name: String): String {
        val n = clean(name)
        return if (n.isEmpty()) utt else " ${clean(utt)} ".replace(" $n ", " ").trim()
    }

    fun isOff(s: String): Boolean {
        val t = s.split(" ")
        return t.any { it in OFFW } || s.contains("stand down") || s.contains("turn off") || s.contains("shut down")
    }

    // ---- does an utterance mention this thing? (custom name always; the old keywords only while the default name is kept) ----
    fun combat(s: String) = has(s, combat) || (isDefault(combat, D_COMBAT) && s.contains("combat"))
    fun quantum(s: String) = has(s, quantum) || (isDefault(quantum, D_QUANTUM) && (s.contains("quantum") || s.split(" ").contains("focus")))
    fun eye(s: String) = has(s, eye) || EYE_ALIASES.any { s.contains(it) }

    private fun offAfter(s: String, names: List<String>): Boolean {
        var r = clean(s)
        names.forEach { r = strip(r, it) }
        return isOff(r)
    }
    fun combatOff(s: String) = combat(s) && offAfter(s, listOf(combat, "combat"))
    fun quantumOff(s: String) = quantum(s) && offAfter(s, listOf(quantum, "quantum"))
    fun eyeOff(s: String) = eye(s) && offAfter(s, eyeNames())

    fun eyeNames(): List<String> = (listOf(eye) + EYE_ALIASES).sortedByDescending { it.length }

    /** The question after "ask [the] <name> ...". */
    fun askRemainder(body: String): String {
        var rest = clean(body).removePrefix("ask").trim().removePrefix("the").trim()
        for (n in eyeNames().map { clean(it) }) if (rest == n || rest.startsWith("$n ")) { rest = rest.removePrefix(n).trim(); break }
        return rest
    }
    fun askLeadIsEye(s: String): Boolean {
        val rest = clean(s).removePrefix("ask").trim().removePrefix("the").trim()
        return eyeNames().map { clean(it) }.any { rest == it || rest.startsWith("$it ") }
    }

    // ---- what she says ----
    fun combatReply(on: Boolean) =
        if (isDefault(combat, D_COMBAT)) (if (on) "Combat mode activated" else "Combat mode deactivated")
        else "$combat " + (if (on) "activated" else "deactivated")
    fun quantumReply(on: Boolean) =
        if (isDefault(quantum, D_QUANTUM)) (if (on) "Quantum focus activated" else "Quantum focus deactivated")
        else "$quantum " + (if (on) "activated" else "deactivated")

    /** Swaps the default names inside help text for the user's own. */
    fun rewrite(text: String): String {
        var t = text
        t = t.replace(Regex("combat mode", RegexOption.IGNORE_CASE), combat)
        t = t.replace(Regex("quantum focus", RegexOption.IGNORE_CASE), quantum)
        t = t.replace(Regex("third eye", RegexOption.IGNORE_CASE), eye)
        return t
    }
}
