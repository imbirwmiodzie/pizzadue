# PizzaDue

A voice‑driven pizza timer for Android. Three tabs:

- **Timer**: preheat the oven (30 min), then bake (1:30). Say **“vai!”** / **“waj!”** to start, **“pizza out”** / **“wyjmij”** to stop.
- **Dough**: dough balls (±1) and ball weight (±10 g) → grams of flour, water, salt and yeast. Hold − / + to step fast.
- **Settings**: screen always on at full brightness (default on), hydration, salt and yeast (% of flour), times, ticks,
  loudness (max volume, vibration off by default, flashlight), voice clips, languages.

## Install

Every push to the default branch publishes the newest APK as the **latest** pre‑release:
open the repository's **Releases** page on your phone, download `pizzadue.apk` and open it.
Android asks once to allow installing apps from your browser.

Each workflow run also keeps the APKs as a downloadable artifact (Actions tab → run → Artifacts).
Tag a commit `v1.0.0` to publish a numbered release.

## How it sounds

A long beep marks every start. Bake (counted from “vai!”): 1 short tick at :15, 2 at :30, 3 at :45, one long tick on every full minute.
“Gira, gira!” at half time. At the end “La pizza è pronta!” repeats every 10 s until you say “pizza out” / “wyjmij”.

Preheat: silent except 1 tick at ⅓, 2 ticks at ⅔, 3 ticks at the end, then “Forno a legna caldo” every 30 s
until you say “vai!” or stop it. The timer then switches to Bake.

Loudness: everything plays on the alarm stream with Android's loudness enhancer, alarm volume goes to maximum
while a timer runs (and back afterwards), and vibration and a flashlight blink per tick can be turned on.

## Voice commands

Voice only starts and stops the timer. Offline (Vosk), English and Polish listening at the same time,
only while the app is open. There are no spoken replies: a long beep confirms the start.

| Action | English | Polski |
|---|---|---|
| Start the timer (preheat or bake, whichever is selected) | vai!, waj!*, pizza in, start | start |
| Stop the timer or the end call | pizza out, stop | wyjmij, stop, koniec |

\* “vai” and “waj” are not words in either speech model. The English model hears them as “vie”, which sounds the same,
so the English list needs to be on for “vai!” to work.

## Build locally

```bash
scripts/fetch_models.sh      # speech models into app/src/main/assets (not in git, ~160 MB)
scripts/convert_voice.sh     # voice/ recordings -> app/src/main/res/raw (needs ffmpeg)
./gradlew testDebugUnitTest assembleDebug
```

## Release signing (optional)

Without a key, builds are signed with the dev key committed in `app/signing/dev.keystore`, so every build installs
over the previous one. Anyone with access to this repository can sign with that key; switching to a private key later
needs one uninstall. To sign properly, add these repository secrets:
`PIZZADUE_KEYSTORE_BASE64` (the .jks file, base64), `PIZZADUE_KEYSTORE_PASSWORD`, `PIZZADUE_KEY_ALIAS`, `PIZZADUE_KEY_PASSWORD`.
