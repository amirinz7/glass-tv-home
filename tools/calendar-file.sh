#!/usr/bin/env bash
# Put a calendar file (.ics, e.g. exported from Google Calendar) on the TV. It is a snapshot: it does not
# update by itself. For a live calendar use tools/calendar-link.sh with the secret iCal address.
#   TV_HOST=192.168.0.10 tools/calendar-file.sh ~/Downloads/my-calendar.ics
set -euo pipefail
SRC="${1:?usage: calendar-file.sh <file.ics>}"
case "$SRC" in /*) ;; *) SRC="$PWD/$SRC" ;; esac
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST, e.g. TV_HOST=192.168.0.10 $0 file.ics}"
[ -f "$SRC" ] || { echo "no such file: $SRC" >&2; exit 1; }
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
DIR=/sdcard/Android/data/com.gathin.tvhome/files
"$PY" -I tv.py "mkdir -p $DIR" >/dev/null
NAME=$(basename "$SRC" | tr -c 'A-Za-z0-9._-\n' '_')
"$PY" -I b64push.py "$SRC" "$DIR/$NAME" | tail -n 1
"$PY" -I tv.py "am broadcast -a com.gathin.tvhome.SET_CALENDAR -p com.gathin.tvhome --es url 'file://$DIR/$NAME' --ez on true" | tail -n 2
echo "Done. Open the Calendar on the TV in a few seconds."
