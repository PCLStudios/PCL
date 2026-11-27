<p align="center"><img src="docs/icon.png" width="120" alt="PCL icon"></p>

<h1 align="center">PCL · Project Chronos Lock</h1>
<p align="center"><b>A voice-first assistant for Android.</b> Say “Chronos Lock”, run commands and automations, keep your music playing, and talk to an A.I. chatbot you can rename.</p>

<p align="center">
  <a href="https://buymeacoffee.com/pclstudios">Buy me a coffee</a> ·
  <a href="https://x.com/pclglobal">@pclglobal</a> ·
  <a href="https://github.com/PCLStudios">PCL Studios</a>
</p>

---

## Features

- **Wake word.** Say “Chronos Lock” (or just “Chronos”). Fuzzy matching copes with mishearing; commands repair near-miss words.
- **80+ voice commands.** Time, timers, alarms, notes, calls, apps, flashlight, volume, media, Wi-Fi/Bluetooth, battery and more.
- **Music-safe mic.** An optional offline recogniser (Vosk) reads the microphone without taking audio focus, so music and videos keep playing while PCL listens.
- **Renamable modes and chatbot.** Name Combat Mode, Quantum Focus and the chatbot anything. Say the name and it happens; she answers using it.
- **Programs.** Automations that run when a mode switches on or off: open apps, play songs, set volume, speak, flash the flashlight, wait, timers, notes, switch other modes, ask the A.I., web search, and more, with IF conditions and chance.
- **Chatbot.** Any OpenAI-compatible provider (Groq, Gemini, OpenRouter, Pollinations or your own server) using your own API key.
- **Home widgets.** Every Home button is a widget: add, remove and reorder in EDIT mode.
- **Themes.** Material You, dark / AMOLED / light, accent colours, fonts, optional glass UI, tasteful animations.
- **In-app guide** with a step-by-step chatbot setup for beginners.

## Install

Download the latest signed APK from [Releases](../../releases) or from Uptodown. Updates install over the previous version because every release is signed with the same key.

Requirements: Android 8.0 (API 26) or newer, microphone permission, and Speech Services by Google for the standard recogniser.

## Build from source

Requirements: JDK 17, Gradle 8.9, Android SDK 34.

```
gradle assembleDebug
```

Release builds are produced by the **Build Release APK** GitHub Action, which signs with a private key kept in repository secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`). No keys are stored in this repository.

## Project layout

| File | What it does |
| --- | --- |
| `ChronosEngine.kt` | State, recognition loop, speech, timers, notes, chatbot |
| `Wake.kt`, `Commands.kt`, `Names.kt` | Wake word, command table, renamable names |
| `VoskMic.kt` | Music-safe offline microphone |
| `Flows.kt`, `Screens3.kt` | Programs (automation steps and conditions) |
| `Theme.kt`, `Widgets.kt`, `Screens*.kt` | Theming, Home widgets, screens |
| `Ads.kt` | AdMob banner, rewarded Power Pass, consent |

## Privacy

PCL has no accounts and no server. See the [privacy policy](https://pclstudios.github.io/PCL/) or [PRIVACY.md](PRIVACY.md).

## Support the project

- [Buy me a coffee](https://buymeacoffee.com/pclstudios)
- Solana: `5RXdE64oP5yZkzY7jaTZRasg8bfG6YV1oSiccmv4YATH`
- Ethereum: `0x20f03f587ba7ca0b198e53cb4ae3d8e7e0e272a7`

Stars and shares help too.

## License

[MIT](LICENSE) © 2026 PCL Studios
