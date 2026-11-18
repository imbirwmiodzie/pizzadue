#!/usr/bin/env bash
# Downloads the offline Italian speech model into the app's assets (the voice commands are Italian).
# It is not committed to git (about 90 MB unpacked); CI runs this before every build.
set -euo pipefail
cd "$(dirname "$0")/.."
CACHE=.cache/models
ASSETS=app/src/main/assets/models
mkdir -p "$CACHE" "$ASSETS"
fetch() { # $1 = model name, $2 = asset folder
  local zip="$CACHE/$1.zip"
  [[ -f "$zip" ]] || curl -fsSL --retry 4 -o "$zip" "https://alphacephei.com/vosk/models/$1.zip"
  rm -rf "$ASSETS/$2" "$CACHE/$1"
  unzip -q "$zip" -d "$CACHE"
  mv "$CACHE/$1" "$ASSETS/$2"
  # files the recogniser does not need at runtime
  rm -rf "$ASSETS/$2/README" "$ASSETS/$2/rescore" "$ASSETS/$2/rnnlm"
  echo "$1" > "$ASSETS/$2/version.txt"
  echo "model $1 -> $ASSETS/$2 ($(du -sh "$ASSETS/$2" | cut -f1))"
}
# the English and Polish models of earlier versions are no longer used
rm -rf "$ASSETS/en" "$ASSETS/pl"
fetch vosk-model-small-it-0.22 it
