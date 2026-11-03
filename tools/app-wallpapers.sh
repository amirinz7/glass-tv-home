#!/usr/bin/env bash
# Push pictures that show behind the home screen while an app tile is focused.
# Name each picture after the app: netflix.jpg, viu.jpg, youtube.jpg ... (the name must be part of the package name).
# Usage: TV_HOST=192.168.0.10 tools/app-wallpapers.sh ~/Downloads/app-wallpapers
set -euo pipefail
SRC="${1:?usage: app-wallpapers.sh <folder with netflix.jpg, viu.jpg ...>}"
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST, e.g. TV_HOST=192.168.0.10 $0 folder}"
PY=venv/bin/python
DIR=/sdcard/Android/data/com.gathin.tvhome/files/app-wallpapers
"$PY" -I tv.py "mkdir -p $DIR" >/dev/null
for f in "$SRC"/*.jpg "$SRC"/*.jpeg "$SRC"/*.png; do
  [ -f "$f" ] || continue
  "$PY" -I b64push.py "$f" "$DIR/$(basename "$f")" | tail -n 1
done
echo "Done. Focus an app tile on the TV."
