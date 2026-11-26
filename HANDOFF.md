# PCL handoff (for the next AI / session)

Project: Android app (Kotlin, Jetpack Compose, minSdk 26, compileSdk 34). Package `com.pcl.chronoslock`. Version 0.5.0.
User's goal: always-listening voice assistant "Chronos Lock" with combat/quantum modes, many voice commands, whisper
understanding, fuzzy wake word, session-style locking, an online chatbot ("Third Eye"), and a nicer side-panel UI.

## v1.0.0 (release candidate)
- engage/disengage wording -> activate/deactivate everywhere (voice still accepts both). Always-on default OFF.
- Mic is suspended during rewarded ads (ChronosEngine.suspendMic). Home card pushes the music-safe mic install.
- Wake: lone "chronos", more sound-alikes, "<crowns/chronic> lock" pairs; EXTRA_BIASING_STRINGS (API 33+); Commands.correct()
  repairs near-miss words as a fallback. Ad errors show no codes. Developer options hidden (tap About title 7x).
- Privacy policy is shown in-app (PrivacyDialog); repo link removed from About. Premium polish: gradients, glow, crossfades.

## v0.6.0 (launch build)
- AdMob: Ads.kt (UMP consent, banner on Guide/Style/About only, rewarded ad -> Pass = 24h Power Pass: >8 widgets, >6 steps/program, no banner).
  App ID in manifest; live units hardcoded in AdConfig; About > DEVELOPER OPTIONS toggles TEST/LIVE ads (pref ads_test, default live).
- Donations/About: Screens6.kt (Buy Me a Coffee, SOL + ETH addresses, GitHub PCLStudios, @pclglobal, privacy link). PRIVACY.md in repo root.
- Material You default; unnamed MODE 1 / MODE 2 tiles with a pencil; lock widget; default TTS voice en-us-x-iob-network (once);
  Credits.kt = base64 developer signature (About: tap version 7 times). Emoji glyphs replaced (◈ ◆ ◔).

## v0.5.0 changes (written, NOT yet compiled: CI is the compiler)
- MUSIC FIX: VoskMic.kt = offline Vosk recogniser fed by AudioRecord (no audio focus => music never pauses). Model (~40 MB) is
  downloaded in-app (Voice > MUSIC-SAFE MIC). Used in listen() when installed, voskOn, not whisper, not chatbot mode; falls back to
  SpeechRecognizer on failure. Deps: vosk-android 0.3.47 + jna 5.13.0@aar (jna excluded from vosk's pom), abiFilters arm64/armeabi-v7a.
  If CI complains about duplicate JNA classes or the vosk artifact, adjust build.gradle.kts first.
- RENAMES: Names.kt (Compose state, prefs nameCombat/nameQuantum/nameEye; default chatbot name "Chatbot", "third eye" always an alias).
  Commands use Names.combat()/quantum()/eye()/…Off(); replies via Names.combatReply(); persona via ThirdEyeAI.buildPersona(name, custom)
  (custom persona "" = automatic). Help text goes through Names.rewrite().
- PROGRAMS: Flows.kt rewritten: 16 step types, per-step IF (cond/condArg) and chance, templates, move up/down, per-slot ON/OFF,
  slots now combat/quantum/lock/eye/whisper x on/off (watchers in ChronosEngine.init). {time}{date}{battery} placeholders.
- THEME: Theme.kt = Pal (mode dark/amoled/light, accent, font, glass, Material You via dynamic*ColorScheme, animations) + C getters.
  Helpers: Modifier.pressable / appear / card, AppBackground. STYLE screen (Screens4.kt) also holds name editing + RenameDialog.
- HOME WIDGETS: Widgets.kt (HomeLayout, 20 widgets, EDIT mode: wiggle, remove, move, add tray, rename modes).
- GUIDE screen (Screens5.kt): beginner Third Eye/API guide, error table, music, programs, share. IntroDialog first launch.
- QoL: settings backup/restore via clipboard (API key excluded), share button, voice commands for themes/guide/programs,
  AnimatedContent screen transitions, theme-aware status bar.
- devtests/ stubs are out of date (Step has new fields, Names added); not wired into CI.

## v0.4.0 changes
- ALWAYS ON switch (small round button by the big lock button, Home). ON = old behaviour (mic always listens for the wake word).
  OFF = mic closed in standby; pressing the big button engages the session and opens the mic; disengage/timeout closes it again.
  Gate lives at the top of ChronosEngine.listen(). Pref "alwaysOn" (default true).
- Big button text no longer overflows the circle; flashlight tile removed from Home (the "flashlight on/off" voice commands remain).
- KEEP MUSIC PLAYING (Voice screen, pref keepMusic): Android pauses other players while SpeechRecognizer listens (it takes audio focus).
  listen() notes whether music was active and ~900 ms later sends MEDIA_PLAY if it stopped. Best effort; cannot be fully fixed from an app.
- PROGRAMS screen (Screens3.kt, Flows.kt): Combat and Quantum each have ON and OFF step lists. Step types: open app, play song
  (MEDIA_PLAY_FROM_SEARCH, optional target app, several lines = random pick), open link (optional app), media key, volume, say, flashlight.
  Each step has a wait (seconds). Stored as JSON in prefs "flow_<slot>". Triggered by ChronosEngine.setCombat/setQuantum
  (buttons, voice, red alert, stand down, allOff all go through them).
- Background launches: System > ALLOW OVER OTHER APPS (SYSTEM_ALERT_WINDOW). go() allows launches if PCL is visible OR overlay permission granted.
  Android 15 narrowed that exemption, so it may still be blocked on newer phones.
- Verified on JVM: Flows.kt + Commands.kt + Util.kt + Wake.kt compile against stubs; 57/57 routing. Engine/Compose NOT compiled locally.

## v0.3.1 changes (bug-fix release)
Reported by user: Third Eye said it "can't reach my network core" + assorted minor bugs.
- ThirdEye.kt rewritten: ANY failure used to be reported as a network error (incl. JSON parse errors). Now every failure has a
  kind + real technical detail (DNS, timeout, SSL, HTTP code + provider error text, unreadable reply). The detail is shown in the
  chat bubble (⚠ line) and in System > TEST (inline result, no speech). One automatic retry on dropped connections.
  Also: URL cleaning (adds https://, strips pasted /chat/completions), User-Agent/Accept headers, 60 s read timeout,
  tolerant reply parsing (null/array content), offline pre-check in ChronosEngine.isOnline().
- Manifest: usesCleartextTraffic=true (plain http:// Ollama/LAN presets were blocked by Android => looked like a network error);
  REQUEST_IGNORE_BATTERY_OPTIMIZATIONS (System button now opens the one-tap dialog; without it Doze cuts network/mic with screen off).
- Engine: she no longer hears her own voice (350 ms restart delay + echo filter); stop()/start() reset stale flags; start() is
  idempotent (activity re-sent it and restarted the mic); finishSpeaking guarded against double calls (hush); one A.I. question at a time.
- Commands: free-text commands (note/say/ask/spell/remind/search) now beat keyword commands ("take a note about the timer");
  "scan/status/report/quote/coin/skip" need whole words (fewer false triggers); Third Eye close phrases only when the utterance is short;
  "third i" normalisation fixed; app-launching commands now say so honestly when PCL is not on screen (ChronosEngine.appVisible).
- Locale.US used for timer/pitch formatting.
- Re-verified on JVM: Commands 57/57 routing (incl. new cases), ThirdEye.kt type-checks. Engine/Compose still NOT compiled locally.

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
