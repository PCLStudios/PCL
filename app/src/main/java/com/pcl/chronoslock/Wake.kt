package com.pcl.chronoslock

/**
 * Fuzzy "Chronos Lock" detection. Speech recognisers mangle made-up words, so instead of one exact phrase
 * this accepts sound-alikes ("kronos luck", "cronus clock", "chrono slack", a merged "chronoslock", or just
 * a clear "chronos"). Pure Kotlin, no Android dependencies.
 */
object Wake {
    private val CHRONOS = setOf("chronos", "kronos", "cronos", "chrono", "kronus", "cronus", "chronus", "krono", "crono",
        "chronis", "kronis", "chronas", "kronas", "cronas", "chronoss", "chronose", "kronoss", "cronys", "chronys")
    private val NOT = setOf("chronic", "chronicle", "chronicles", "chronically", "chronicled", "crooner", "crowns")
    private val LOCKS = setOf("lock", "locks", "locked", "luck", "lark", "look", "lack", "clock", "clocks", "lok", "loch",
        "lurk", "lick", "lug", "lak", "lek", "log", "block", "lot", "lord", "luke", "lax")
    /** Words that are normally something else, but "<word> lock" in a row is almost certainly the wake phrase. */
    private val PAIR_ONLY = setOf("chronic", "crowns", "crown", "crowns", "crooner", "cronies", "chronicle")

    fun lev(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** A word that sounds enough like "chronos" to be half of the wake phrase. */
    fun isChronos(w: String, loose: Boolean): Boolean {
        if (w.length < 4 || w in NOT) return false
        if (w in CHRONOS || CHRONOS.any { lev(w, it) <= 1 }) return true
        return w.length >= 5 && (w[0] == 'c' || w[0] == 'k') && lev(w, "chronos") <= if (loose) 3 else 2
    }

    /** Clear enough to wake on its own, without "lock". */
    private fun strong(w: String) = w in CHRONOS || (w.length >= 6 && w !in NOT && CHRONOS.any { lev(w, it) <= 1 }) ||
        (w.length >= 6 && w !in NOT && (w[0] == 'c' || w[0] == 'k') && w.contains("ron") && lev(w, "chronos") <= 2)

    fun isLock(w: String) = w in LOCKS || (w.length in 3..5 && lev(w, "lock") <= 1)

    /**
     * Index of the last token of the wake phrase, or -1. [loose] (whisper mode) tolerates more distortion.
     */
    fun find(tokens: List<String>, loose: Boolean): Int {
        val t = tokens.map { it.replace("'", "") }
        val maxD = if (loose) 3 else 2
        for (i in t.indices) {
            // merged or split: "chronoslock", "chrono slock", "kron os lock"
            for (span in 1..3) {
                if (i + span > t.size) break
                val joined = t.subList(i, i + span).joinToString("")
                if (joined.length >= 8 && lev(joined, "chronoslock") <= maxD) return i + span - 1
            }
            if (i + 1 < t.size && (isChronos(t[i], loose) || t[i] in PAIR_ONLY) && isLock(t[i + 1])) return i + 1
            if (i + 2 < t.size && t[i] == "crow" && (t[i + 1] == "nose" || t[i + 1] == "knows") && isLock(t[i + 2])) return i + 2
            if (i + 2 < t.size && isChronos(t[i] + t[i + 1], loose) && isLock(t[i + 2])) return i + 2
            if (strong(t[i])) return i
        }
        return -1
    }
}
