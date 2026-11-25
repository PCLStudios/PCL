package com.pcl.chronoslock
import kotlinx.coroutines.flow.MutableStateFlow
enum class Screen { HOME, EYE, FLOWS, NOTES, HELP, VOICE, SYSTEM }
object ChronosEngine {
    val log = ArrayList<String>()
    @Volatile var appVisible = true
    val locked = MutableStateFlow(false); val combat = MutableStateFlow(false); val quantum = MutableStateFlow(false)
    val whisper = MutableStateFlow(false); val thirdEye = MutableStateFlow(false); val torch = MutableStateFlow(false)
    val lastReply = MutableStateFlow(""); val tab = MutableStateFlow(Screen.HOME); val notes = MutableStateFlow<List<String>>(emptyList()); val rate = MutableStateFlow(1f)
    fun setWhisper(b: Boolean) { whisper.value = b }; fun setCombat(b: Boolean) { combat.value = b }; fun setQuantum(b: Boolean) { quantum.value = b }; fun announce(t: String) {}; fun setRate(f: Float) {}; fun setPitch(f: Float) {}; fun nextVoice() = "voice"; fun setTimer(ms: Long, l: String) { log.add("timer $ms '$l'") }
    fun cancelTimer() {}; fun timerLeftMs() = 0L; fun hush() {}; fun openEye() = "eye"; fun closeEye() = "closed"; fun ask(q: String) { log.add("ask '$q'") }
    fun addNote(s: String) { log.add("note '$s'") }; fun clearNotes() {}; fun allOff() {}
}
