# PCL — Project Chronos Lock (v0.3.1)

Voice-operated personal A.I. for Android. Side-panel UI, 80+ voice commands, whisper mode, online chatbot ("Third Eye").

## Build the APK
1. Android Studio (Koala or newer): File > Open > this PCL folder, let Gradle sync.
2. Build > Build Bundle(s)/APK(s) > Build APK(s)  ->  app/build/outputs/apk/debug/app-debug.apk
3. Install, allow Microphone + Notifications.
4. (Recommended) System tab > BATTERY OPTIMISATION: exclude PCL, so listening survives screen-off.

## How it works
- Say **"Chronos Lock"** once. The big button turns green (ENGAGED). Sound-alikes work ("kronos luck", "cronus clock"...).
- While engaged you can say commands directly: "combat mode", "set a timer for five minutes", "what time is it".
- Before that, every command is ignored (voice AND the Combat/Quantum buttons).
- "Chronos Lock, combat mode" in one breath also works.
- "disengage" / "good night" / the button / the session timeout (System tab) ends the session.
- **Whisper mode** ("whisper mode"): online recognition, longer listening window, looser wake matching, quiet replies.
- **Third Eye** ("open third eye"): every sentence goes to an online chatbot that knows it is Third Eye. No history is kept.
  "close third eye" / "goodbye" to leave. One-off: "ask what is gravity".

## Third Eye needs an API key
Pollinations (default) now requires a free key: https://enter.pollinations.ai . Paste it in System > Third Eye.
Presets for Groq, OpenRouter, Gemini and any custom OpenAI-compatible server (e.g. Ollama) are in the same screen.

## Tweaking
Commands: Commands.kt (the Commands screen is generated from that list). Wake matching: Wake.kt.
Voice defaults: Voice screen. A.I. persona: System screen.
