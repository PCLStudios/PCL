# PCL handoff (for the next AI / session)

Project: Android app (Kotlin, Jetpack Compose, minSdk 26, compileSdk 34). Package `com.pcl.chronoslock`. Version 0.3.0.
User's goal: always-listening voice assistant "Chronos Lock" with combat/quantum modes, many voice commands, whisper
understanding, fuzzy wake word, session-style locking, an online chatbot ("Third Eye"), and a nicer side-panel UI.

## State: all requested features are WRITTEN but the APK has NOT been built
No Android SDK / Gradle network in the authoring sandbox. What WAS verified on a plain JVM (see devtests/):
- Wake.kt fuzzy matching (WakeTest.kt): 14 sound-alike phrases accepted, 9 unrelated phrases rejected.
- Util.kt helpers (UtilTest.kt): maths, timer parsing, alarm time parsing, number words. All pass.
- Commands.kt compiles against stub Android classes and 53/53 sample phrases route to the right command (devtests/stubs/CmdTest.kt).
NOT verified: ChronosEngine.kt, Service, Theme/MainActivity/Screens*.kt against real Android + Compose APIs.
FIRST THING TO DO: open in Android Studio, sync, build; fix any compile errors (most likely spots: Compose API
names in Screens*.kt/Theme.kt, material3 ModalNavigationDrawer usage). Then test on device.

## Files (app/src/main/java/com/pcl/chronoslock)
- ChronosEngine.kt  state flows + speech loop + TTS + timers/notes + Third Eye. Session rules live in `decide()`.
- Wake.kt           fuzzy "Chronos Lock" matcher (Levenshtein + sound-alike lists; `loose` = whisper mode).
- Commands.kt       82 `Cmd(cat, phrase, help, match, run)` entries. ORDER MATTERS (specific before general). Help screen is generated from it.
- Util.kt           pure helpers (digitize, timerMs, calc, parseClock...).
- ThirdEye.kt       stateless OpenAI-compatible client + provider presets (Pollinations/Groq/OpenRouter/Gemini/custom).
- ChronosService.kt foreground mic service + partial wake lock (so timers/listening survive screen-off).
- Theme.kt          colours (object C), Panel/PillButton/Chip/Field/Text2 components.
- MainActivity.kt   permissions, side-panel navigation (ModalNavigationDrawer), top bar.
- Screens.kt        Home (lock button, wave bars, modes, tiles, timers), Third Eye chat, Notes.
- Screens2.kt       Commands (searchable, grouped), Voice (whisper switch, voices, sliders), System (timeout, A.I. settings, phone setup).

## Behaviour decisions
- Engage: Wake.find() on any of up to 5 recogniser alternatives. Wake alone => "Chronos Lock Engaged" (button turns green).
  Wake + command => engages and runs it. While `locked`, commands run without wake word (<= 8 words unless `Commands.openEnded`).
- Not locked => everything but the wake word is ignored. Combat/Quantum UI buttons are also gated; whisper/flashlight/third-eye tiles are not.
- Whisper: online recognition, longer silence windows, loose wake distance, 5 alternatives, TTS volume 0.35.
- Third Eye: every FINAL sentence -> ThirdEyeAI.ask (persona + that question only). Chat shown in UI only. Errors are spoken (missing/bad key, limit, network).
- "bugs out" fixes: per-session id ignores stale recogniser callbacks, watchdog restarts hung recogniser, error backoff,
  offline<->online fallback, TTS utterance ids + speak guard so a lost "done" callback can't freeze her.
- Pollinations now REQUIRES a key (verified in docs). Default endpoint https://gen.pollinations.ai/v1, model "openai". No verified keyless option exists.
- Android blocks background activity launches: app/settings/search/alarm UI commands work only while PCL is on screen. Media keys, volume, flashlight, timers, notes work with screen off.

## Ideas not done yet
- Landscape/tablet permanent drawer; Material You theming; boot-completed auto start; custom user-defined commands;
  mute the recogniser start beep; streaming AI replies; a real unit-test source set wired into Gradle.
