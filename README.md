# PCL — Project Chronos Lock (v0.1)

## Build the APK
1. Install Android Studio (Koala or newer). File > Open > select this PCL folder. Let Gradle sync.
2. Build > Build Bundle(s)/APK(s) > Build APK(s)
   Output: app/build/outputs/apk/debug/app-debug.apk
3. Install on your phone, allow Microphone + Notifications.

## Voice commands
- "Chronos Lock"                -> "Listening", then say a command
- "Chronos Lock combat mode"    -> "Combat mode has been engaged"
- "Chronos Lock quantum focus"  -> "Quantum focus has been approved"
- "Chronos Lock engage/disengage" and the big button -> "Chronos Lock Engaged / Disengaged"

## Tweaking the voice
ChronosEngine.kt top: VOICE_PITCH, VOICE_RATE, FEMALE_HINTS.
Install "Speech Services by Google" + English (US) voices in phone TTS settings.
