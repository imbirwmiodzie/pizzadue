# VibePizzaiolo

A voice‑driven pizza timer for Android. Four tabs:

- **Timer**: preheat the oven (30 min), then bake (1:30). Say **“ciao pizza start”** to start and **“ciao pizza stop”** to stop (see [Voice commands](#voice-commands)).
- **Dough**: dough balls (±1) and ball weight (±10 g) → grams of flour, water, salt and yeast. Hold − / + to step fast.
  Below it, the **fermentation simulator** (*Symulator fermentacji*, in Polish): a wizard from the schedule to the yeast (see below).
- **Pizzaioli**: swipe, search and comment other people's pizzas, post yours, and ask a pizzaiolo to eat together (see below).
- **Serata**: a pizza night with friends: they order through one link in the browser, the app counts the dough and
  keeps the baking queue (see below).
- **Pizzaioli**: the community: post, swipe and search pizzas.
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
falling onto the pizza with a thud → the ending with the pen-and-ink pizzaiolo waving and *“La pizza è pronta!”*.
Made on the phone with Media3 Transformer; shared like the cards (Instagram, Facebook, WhatsApp, more, save).

**In the browser, nothing to install**: the server (see [server/README.md](server/README.md)) also serves the reel as a
web page at `https://<server>/reel`. Tap *Open the camera*, then the red button: the page counts down and says what to
film (3 s from above, 9 s around, 5 s on the rim) while it records, keeping only the seconds that go into the reel, then
adds the seal and the ending with the pizzaiolo and the voice. It films at 1080p / 30 fps without HDR whatever the camera
app is set to, works in Chrome and Safari (MP4; other browsers save WebM), and makes the reel on the phone: nothing is
uploaded. Add `?n=12&bake=92` to the link to print the pizza number and the bake time (seconds).

**You as the pizzaiolo**: Settings → *You, the pizzaiolo* → Selfie. The app finds your eyes, draws your face in pen
and ink (ink lines, hatched shadows, skin and hair in flat washes), cuts it to the shape of your head with your hair and
puts it on the drawn pizzaiolo, under the toque: your icon on the Timer and in the community, and the chef at the end of
every reel. A plain wall behind you works best. Everything stays on the phone.

## Fermentation simulator

Dough tab → *Symulator fermentacji*. A five-step wizard, in Polish:

1. **Styl**: a preset (*Neapolitańska tego samego dnia*, *48 h w lodówce*, *Szybka 2 h*, *Nowojorska*) or your own recipe.
2. **Ciasto**: flour (g) and dough balls, flour strength W 180–380, hydration 55–80 %, salt 0–3.5 %, and optionally
   sugar, oil and vinegar (teaspoons per 500 g of flour).
3. **Czas**: any number of stages, each with its hours and temperature (e.g. 2 h at 22 °C → 24 h at 4 °C → 2 h at 22 °C),
   and the dough's temperature after mixing.
4. **Drożdże**: fresh, instant or active dry, in % of flour (fresh ≈ 3 × instant, active dry ≈ 1.25–1.5 × instant), or
   **Policz za mnie**: say in how many hours you bake and it works out the yeast (stretching the schedule's longest stage).
5. **Wynik**: one chart over the hours since mixing with the room and dough temperature, the rise (%), the pH and a
   conventional gluten strength, the ready-to-bake window in green and over-fermentation in red (tap or drag to read it),
   advice (the yeast that would make it ready, the weakest flour that holds), the grams for the batch, short notes on the
   chemistry, and **Zapisz w Dough**, which sets the Dough tab to the same balls, weight, hydration, salt and yeast.

It is an approximate, educational model, not a simulation of a real dough: yeast activity follows the cardinal temperature
model (CTMI) with −2 / 33 / 45 °C, the dough's temperature lags the room's by 1.5 h, and the rest is simple formulas tuned to
rules of thumb. The assumptions are in `app/src/main/java/com/vibepizzaiolo/dough/Fermentation.kt` and on the result screen.

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

## Serata: a pizza night with friends

The **Serata** tab organises a shared bake. It needs the server (`server/README.md`), because friends take part
through a link, without installing anything:

1. **Before**: *New pizza night* → name, when, tonight's menu, a list of things to bring, spare dough balls →
   **Share the link** (WhatsApp or anything else). Friends open it in the browser, type their name and pick their
   pizzas; they also see who brings what and can take an item. No account: the browser remembers who it is, and a
   browser that forgot taps its name. You can write in a guest without a phone and order for them.
2. **The board** shows the orders per pizza and per person and the dough for the night (ordered pizzas + spare balls,
   with flour, water, salt and yeast from your Dough settings); one tap sets the Dough tab to that number of balls.
3. **The bake**: *Close orders · start the bake*. Friends' phones now show only “The bake is on. Put your phone away.”
   Your phone shows the queue: the test pizza first, then one pizza each before anybody gets a second. The Timer tab
   says whose pizza is next, and every finished bake (“ciao pizza stop”) ticks the next one by itself; taps on the queue
   correct it.
4. **After**: *Finish the night*; the link then says grazie.

Pizzas that are photographed classics show their photo (Margherita, Marinara, Diavola, Capricciosa, Quattro formaggi,
Hawaii, Nutella, Tonno, Tonno e cipolla, Salsiccia, Salsiccia e funghi, Amatriciana, Rucola e grana): picked by the name,
or by exactly the same toppings, and never when the note adds or takes off something the photo shows. Any other pizza
is drawn from its ingredients. The pizza in the middle floats, turns slowly and catches the light. The photos are cut out
of `scripts/pizza_photos/menu-*.jpg` by `scripts/pizza_photos/cut.py`.

The guests' page speaks Polish or English, whichever the phone is set to. This tab is separate from **Pizzaioli**
(the public community with swiping); the two only share the server address.

## How it sounds

A long beep marks every start. Bake (counted from “ciao pizza start”): 1 short tick at :15, 2 at :30, 3 at :45, one long tick on every full minute.
“Gira, gira!” at half time. At the end “La pizza è pronta!” repeats every 10 s until you say “ciao pizza stop”.

Preheat: silent except 1 tick at ⅓, 2 ticks at ⅔, 3 ticks at the end, then “Forno a legna caldo” every 30 s
until you say “ciao pizza start” or stop it. The timer then switches to Bake.

Loudness: by default everything plays as an alarm sound, so it is heard even when the phone is on silent and always
comes from the phone's speaker; **Play as alarm** off switches to media sound (media volume, Bluetooth/headphones). **Beep volume**
(Settings, 5–100%, default 40%) sets the level while a timer runs and your own alarm volume comes back afterwards;
below the alarm's lowest step the app lowers the sound itself, so 5% is really quiet, and the loudness boost only
works above 80%. **Night mode** caps it at 15% and turns vibration on. The phone's volume buttons also change it
while the app is open, and vibration and a flashlight blink per tick can be turned on.

## Voice commands

Voice only starts and stops the timer. Offline (Vosk), only while the app is open. There are no spoken replies:
a long beep confirms the start.

| Action | Say |
|---|---|
| Start the timer (preheat or bake, whichever is selected) | **ciao pizza start** |
| Stop the timer or the end call | **ciao pizza stop** |

Like “OK Google”, the three words have to come in a row: a lone “start” or “stop” does nothing, so the radio can no
longer turn the timer off and on. An English and a Polish model listen at the same time; both know all three words, so
the phrases are the same in both. When the two disagree (one hears “start”, the other “stop”), the surer one wins.
Settings → Voice commands turns either off; both on hears you best.

Tested on synthetic speech (5 voices, 3 speeds) through the app's listening loop: the old commands (“vai!”, “stop”,
“start”, “wyjmij”, “koniec”…) were triggered 47 times by 2½ minutes of English and Polish radio talk, “ciao pizza …”
never. The phrase was heard 30/30 in quiet, 27/30 with an extractor fan behind and 25/30 over a radio at 10 dB.

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
