package com.pcl.chronoslock

import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

// Pure helpers (no Android dependencies) so they can be unit-tested on a plain JVM.

private fun String.has(vararg w: String) = w.any { contains(it) }

internal val NUM = mapOf("zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
    "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14,
    "fifteen" to 15, "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19, "twenty" to 20,
    "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90)

/** Spoken numbers -> digits ("twenty five" -> "25"). */
internal fun digitize(s: String): String {
    val t = s.split(" ").filter { it.isNotEmpty() }
    val out = ArrayList<String>()
    var i = 0
    while (i < t.size) {
        val a = NUM[t[i]]
        val b = if (i + 1 < t.size) NUM[t[i + 1]] else null
        if (a != null && a >= 20 && b != null && b in 1..9) { out.add((a + b).toString()); i += 2 }
        else { out.add(a?.toString() ?: t[i]); i++ }
    }
    return out.joinToString(" ")
}

internal fun timerMs(s: String): Long? {
    val d = digitize(s)
    val unit = when { d.has("hour") -> 3600L; d.has("minute", "min") -> 60L; d.has("second", "sec") -> 1L; else -> return null }
    var n = Regex("""\d+""").find(d)?.value?.toLong() ?: 1L
    if (n == 0L) n = 1
    if (d.has("half an hour", "half hour")) return 1800_000L
    return n * unit * 1000L
}
internal fun human(ms: Long): String {
    val s = ms / 1000
    fun p(n: Long, u: String) = "$n $u" + if (n == 1L) "" else "s"
    return when {
        s >= 3600 -> p(s / 3600, "hour") + if (s % 3600 >= 60) " " + p(s % 3600 / 60, "minute") else ""
        s >= 60 -> p(s / 60, "minute") + if (s % 60 > 0) " " + p(s % 60, "second") else ""
        else -> p(s, "second")
    }
}
internal fun fmt(d: Double) = if (d == Math.floor(d) && abs(d) < 1e12) d.toLong().toString() else "%.4f".format(Locale.US, d).trimEnd('0').trimEnd('.')

private val CALC = Regex("""(-?\d+(?:\.\d+)?)\s*(plus|minus|times|multiplied by|divided by|over|x|\+|\*|/|-)\s*(-?\d+(?:\.\d+)?)""")
internal fun calc(s: String): String? {
    val m = CALC.find(digitize(s)) ?: return null
    val a = m.groupValues[1].toDouble(); val b = m.groupValues[3].toDouble()
    val r = when (m.groupValues[2]) {
        "plus", "+" -> a + b
        "minus", "-" -> a - b
        "times", "x", "*", "multiplied by" -> a * b
        else -> if (b == 0.0) return "I cannot divide by zero" else a / b
    }
    return "${fmt(a)} ${m.groupValues[2]} ${fmt(b)} is ${fmt(r)}"
}

/** "7 30 pm" / "7:30" -> hour, minute (24h). With no am/pm the next upcoming occurrence is chosen. */
internal fun parseClock(s: String): Pair<Int, Int>? {
    val m = Regex("""(\d{1,2})(?:[: ](\d{2}))?\s*(am|pm|a m|p m|in the morning|in the afternoon|in the evening|at night)?""").find(digitize(s)) ?: return null
    val h = m.groupValues[1].toInt(); val min = m.groupValues[2].ifBlank { "0" }.toInt(); val tag = m.groupValues[3]
    if (h > 23 || min > 59) return null
    if (h > 12) return h to min
    return when {
        tag.startsWith("p") || tag.has("afternoon", "evening", "night") -> (h % 12 + 12) to min
        tag.startsWith("a") || tag.has("morning") -> (h % 12) to min
        else -> {
            val now = Calendar.getInstance(); val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
            val best = listOf(h % 12, h % 12 + 12).map { it * 60 + min }.minByOrNull { (it - nowMin + 1440) % 1440 }!!
            (best / 60) to (best % 60)
        }
    }
}
internal fun clockText(h: Int, m: Int) = "%d:%02d %s".format(Locale.US, if (h % 12 == 0) 12 else h % 12, m, if (h < 12) "AM" else "PM")

