package com.pcl.chronoslock

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One voice command. The Help tab is generated from this list, so it is always accurate. */
class Cmd(val phrase: String, val help: String, val match: (String) -> Boolean, val run: (Context) -> String)

private fun String.has(vararg w: String) = w.any { contains(it) }
private fun String.off() = has("disengage", "deactivate", "stand down", "turn off", "shut down")

private fun torch(ctx: Context, on: Boolean): Boolean = try {
    val cm = ctx.getSystemService(CameraManager::class.java)
    val id = cm.cameraIdList.firstOrNull {
        cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    }
    if (id == null) false else { cm.setTorchMode(id, on); true }
} catch (e: Exception) { false }

private fun volume(ctx: Context, up: Boolean): String {
    val am = ctx.getSystemService(AudioManager::class.java)
    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)
    return if (up) "Volume increased" else "Volume decreased"
}

object Commands {
    private fun on(b: Boolean) = if (b) "engaged" else "offline"

    /** Order matters: specific commands first, general ones last. */
    val all: List<Cmd> = listOf(
        Cmd("disengage combat mode", "Turns combat mode off.",
            { it.has("combat") && it.off() }) { ChronosEngine.combat.value = false; "Combat mode has been disengaged" },
        Cmd("disengage quantum focus", "Turns quantum focus off.",
            { it.has("quantum", "focus") && it.off() }) { ChronosEngine.quantum.value = false; "Quantum focus has been disengaged" },
        Cmd("stand down", "Turns off combat mode and quantum focus together.",
            { it.has("stand down") }) { ChronosEngine.combat.value = false; ChronosEngine.quantum.value = false; "All modes have been disengaged" },
        Cmd("combat mode", "Engages combat mode.",
            { it.has("combat") }) { ChronosEngine.combat.value = true; "Combat mode has been engaged" },
        Cmd("quantum focus", "Approves and activates quantum focus.",
            { it.has("quantum", "focus") }) { ChronosEngine.quantum.value = true; "Quantum focus has been approved" },
        Cmd("flashlight off", "Turns the phone flashlight off.",
            { it.has("flashlight", "flash light", "torch") && it.off() }) { if (torch(it, false)) "Flashlight off" else "Flashlight unavailable" },
        Cmd("flashlight on", "Turns the phone flashlight on.",
            { it.has("flashlight", "flash light", "torch") }) { if (torch(it, true)) "Flashlight on" else "Flashlight unavailable" },
        Cmd("disengage", "Disengages Chronos Lock (same as the big button).",
            { it.has("disengage", "deactivate") }) { ChronosEngine.locked.value = false; "Chronos Lock Disengaged" },
        Cmd("engage", "Engages Chronos Lock (same as the big button).",
            { it.has("engage", "activate") }) { ChronosEngine.locked.value = true; "Chronos Lock Engaged" },
        Cmd("what time is it", "Tells you the current time.",
            { it.has("time") }) { "The time is " + SimpleDateFormat("h:mm a", Locale.US).format(Date()) },
        Cmd("what is the date", "Tells you today's date.",
            { it.has("date", "what day", "today") }) { "Today is " + SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date()) },
        Cmd("battery status", "Reads your battery level and charging state.",
            { it.has("battery", "power level") }) {
            val bm = it.getSystemService(BatteryManager::class.java)
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            "Battery is at $pct percent" + if (bm.isCharging) " and charging" else ""
        },
        Cmd("status report", "Reads out the state of every system.",
            { it.has("status", "report") }) {
            "Chronos Lock is ${on(ChronosEngine.locked.value)}. Combat mode is ${on(ChronosEngine.combat.value)}. " +
                "Quantum focus is ${on(ChronosEngine.quantum.value)}."
        },
        Cmd("volume up", "Raises the media volume one step.",
            { it.has("volume") && it.has("up", "increase", "raise") || it.has("louder") }) { volume(it, true) },
        Cmd("volume down", "Lowers the media volume one step.",
            { it.has("volume") && it.has("down", "decrease", "lower") || it.has("quieter") }) { volume(it, false) },
        Cmd("help", "Opens this command list.",
            { it.has("help", "commands", "what can you do") }) { ChronosEngine.tab.value = 1; "Here are my commands" },
        Cmd("open settings", "Opens the voice settings tab.",
            { it.has("settings", "voice menu") }) { ChronosEngine.tab.value = 2; "Opening voice settings" },
        Cmd("go home", "Returns to the main screen.",
            { it.has("go home", "home screen", "main menu") }) { ChronosEngine.tab.value = 0; "Returning home" },
        Cmd("who are you", "She introduces herself.",
            { it.has("who are you", "your name") }) { "I am Chronos Lock, your personal A.I. assistant." },
    )
}
