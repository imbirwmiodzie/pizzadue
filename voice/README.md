# Voice recordings

The app plays these at the end of a timer. Replace a file to change the voice: the build
trims silence, evens out the loudness and converts it to OGG (`scripts/convert_voice.sh`).

A file is matched by a word in its name, so keep that word in the file name:

| Word in the file name | Plays |
|---|---|
| `pronta` | End of bake: "La pizza è pronta!", repeats every 10 s until stopped |
| `forno`  | End of preheat: "Forno a legna caldo", repeats every 30 s until stopped |
| `gira`   | Half-way through the bake: "Gira, gira!" (turn the pizza) |

Any format ffmpeg reads works: mp3, wav, m4a, ogg.
