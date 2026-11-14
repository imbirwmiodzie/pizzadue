#!/usr/bin/env bash
# Converts the recordings in voice/ into the OGG clips the app plays.
# A file is picked by its name: *pronta* -> end of bake, *forno* -> end of preheat, *gira* -> turn reminder.
# Any format ffmpeg reads works (mp3, wav, m4a, ogg...). Silence at both ends is trimmed and loudness evened out.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=app/src/main/res/raw
mkdir -p "$OUT"
convert() { # $1 = name pattern, $2 = output resource name
  local src
  src=$(find voice -maxdepth 1 -type f -iname "*$1*" ! -iname "*.md" | sort | tail -n 1)
  if [[ -z "$src" ]]; then echo "no voice file for '$1', keeping existing $2"; return; fi
  echo "voice: $src -> $OUT/$2.ogg"
  ffmpeg -v error -y -i "$src" \
    -af "silenceremove=start_periods=1:start_threshold=-45dB,areverse,silenceremove=start_periods=1:start_threshold=-45dB,areverse,loudnorm=I=-12:TP=-1:LRA=11,aresample=44100" \
    -ac 1 -c:a libvorbis -q:a 5 "$OUT/$2.ogg"
}
convert pronta voice_pronta
convert forno voice_forno
convert gira voice_gira
