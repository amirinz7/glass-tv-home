#!/usr/bin/env bash
# Link your Google Calendar from the computer (no phone or QR code needed).
# Get the address on a computer: calendar.google.com > Settings (gear) > your calendar > Integrate calendar >
# "Secret address in iCal format" (ends in basic.ics). Then:
#   TV_HOST=192.168.0.10 tools/calendar-link.sh
# or:  TV_HOST=192.168.0.10 tools/calendar-link.sh 'https://calendar.google.com/.../basic.ics' 'https://calendar.google.com/.../basic.ics'
# Several calendars are fine (also a file:///sdcard/Android/data/com.gathin.tvhome/files/birthdays.ics). A holiday calendar replaces the built-in Malaysia holidays.
set -euo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST, e.g. TV_HOST=192.168.0.10 $0}"
URL="$*"
if [ -z "$URL" ]; then read -r -p "Paste the secret iCal address(es), separated by spaces, and press Enter: " URL; fi
case "$URL" in https://*) ;; *) echo "That is not a web address (it must start with https://)." >&2; exit 1 ;; esac
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
"$PY" -I tv.py "am broadcast -a com.gathin.tvhome.SET_CALENDAR -p com.gathin.tvhome --es url '$URL' --ez on true" | tail -n 2
echo "Sent. Open the Calendar on the TV in a few seconds."
