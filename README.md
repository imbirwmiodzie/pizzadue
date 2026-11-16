# VibePizzaiolo

A voice‑driven pizza timer for Android. Four tabs:

- **Timer**: preheat the oven (30 min), then bake (1:30). Say **“vai!”** / **“waj!”** to start, **“pizza out”** / **“wyjmij”** to stop.
- **Dough**: dough balls (±1) and ball weight (±10 g) → grams of flour, water, salt and yeast. Hold − / + to step fast.
- **Pizzaioli**: swipe, search and comment other people's pizzas, post yours, and ask a pizzaiolo to eat together (see below).
- **Settings**: screen always on at full brightness (default on), hydration, salt and yeast (% of flour), times, ticks,
  loudness (night mode, beep volume 5–100% default 40%, vibration off by default, flashlight), voice clips, languages.

## Install

Every push to the default branch publishes the newest APK as the **latest** pre‑release:
open the repository's **Releases** page on your phone, download `vibepizzaiolo.apk` and open it.
Android asks once to allow installing apps from your browser.

Each workflow run also keeps the APKs as a downloadable artifact (Actions tab → run → Artifacts).
Tag a commit `v1.0.0` to publish a numbered release.

## Share your pizza

Camera icon on the Timer (and a big **Share this pizza** button right after a bake) → take a photo or pick one from
the gallery → choose a design (**Stamp**, **Bake sheet**, **Ticket**) and a format (**Post 4:5** 1080×1350,
**Story 9:16** 1080×1920, **Square** 1080×1080) → **Instagram**, **Facebook**, **WhatsApp**, **More…** (Android's share
menu) or **Save to gallery** (Pictures/VibePizzaiolo).

The card carries the real bake: pizza number, bake time, hydration, salt, yeast, preheat, dough balls, and the
VibePizzaiolo logo. A caption with the download link and #VibePizzaiolo is copied when you share (Instagram and
Facebook do not accept text from other apps, so paste it); WhatsApp and most other apps get it attached.

## Pizza reel and you as the pizzaiolo

**Video reel**: the camera icon → *Video reel*. An animation shows how to film (from above, once around low, the rim
with a finger press), then the phone's camera records up to 20 s. The app edits it by itself into a vertical
1080×1920 reel: from above → two quick cuts around → the rim with its real crunch → the **QUALITÀ SUPERIORE** seal
falling onto the pizza with a thud → the ending with the cartoon pizzaiolo waving and *“La pizza è pronta!”*.
Made on the phone with Media3 Transformer; shared like the cards (Instagram, Facebook, WhatsApp, more, save).

**You as the pizzaiolo**: Settings → *You, the pizzaiolo* → Selfie. The app finds your face, draws it as a cartoon
(flat colours and ink outlines), shows it as your icon on the Timer and puts it on the pizzaiolo at the end of every
reel. Everything stays on the phone.

## Pizzaioli: the community

The **Pizzaioli** tab is a gallery of other people's pizzas that you judge by swiping.

- **Account**: create one with a name, email and password, then answer four questions for your **style card**:
  how spicy, red or white, which tradition (Napoletana, Romana, teglia, pinsa, New York, Detroit, Chicago…) and how hot you bake.
- **Swipe**: right is *Buona!*, left is *Passo*. Tap a card for the details.
- **Gallery**: every pizza, searchable by type (Margherita, Diavola…) and filterable by style, sauce, spice and oven temperature.
- **Post**: the round camera button, or *Post it to the pizzaioli* in the share menu. The app proposes the type from the photo
  and you confirm it; the rest is prefilled from your style card and your last bake.
- **A pizza**: likes, comments, and **Mangiamo insieme?**, which asks its pizzaiolo to eat together. Once they accept, a chat opens under *Tavola*.

**Without a server** the tab still works: the account, your pizzas and your swipes stay on the phone, the other pizzaioli
are drawn samples (marked *sample*), and the type is guessed from the colours in the photo. With a server
(see [server/README.md](server/README.md)) the same screens show real people, and the server can name the pizza with a vision model.
Set its address under *Me → Server address*, or for every build with the repository variable `VIBEPIZZAIOLO_COMMUNITY_URL`.
If the server does not answer when the tab opens, the app falls back to the phone and offers to try again.

## How it sounds

A long beep marks every start. Bake (counted from “vai!”): 1 short tick at :15, 2 at :30, 3 at :45, one long tick on every full minute.
“Gira, gira!” at half time. At the end “La pizza è pronta!” repeats every 10 s until you say “pizza out” / “wyjmij”.

Preheat: silent except 1 tick at ⅓, 2 ticks at ⅔, 3 ticks at the end, then “Forno a legna caldo” every 30 s
until you say “vai!” or stop it. The timer then switches to Bake.

Loudness: by default everything plays as an alarm sound, so it is heard even when the phone is on silent and always
comes from the phone's speaker; **Play as alarm** off switches to media sound (media volume, Bluetooth/headphones). **Beep volume**
(Settings, 5–100%, default 40%) sets the level while a timer runs and your own alarm volume comes back afterwards;
below the alarm's lowest step the app lowers the sound itself, so 5% is really quiet, and the loudness boost only
works above 80%. **Night mode** caps it at 15% and turns vibration on. The phone's volume buttons also change it
while the app is open, and vibration and a flashlight blink per tick can be turned on.

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
./gradlew -p server test      # the community server
```

## Release signing (optional)

Without a key, builds are signed with the dev key committed in `app/signing/vibepizzaiolo-dev.keystore`, so every build installs
over the previous one. Anyone with access to this repository can sign with that key; switching to a private key later
needs one uninstall. To sign properly, add these repository secrets:
`VIBEPIZZAIOLO_KEYSTORE_BASE64` (the .jks file, base64), `VIBEPIZZAIOLO_KEYSTORE_PASSWORD`, `VIBEPIZZAIOLO_KEY_ALIAS`, `VIBEPIZZAIOLO_KEY_PASSWORD`.
