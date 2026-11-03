#!/usr/bin/env bash
# Keep everything of your Glass TV in one zip: the app's source from this folder, plus what the TV itself has
# (its model and Android version, the installed Glass TV app file, your settings, app wallpapers, calendar files,
# the list of apps and what was disabled, and the TV's settings).
#   TV_HOST=192.168.0.10 tools/backup-tv.sh
# Result: ~/Downloads/glass-tv-complete-backup-<date>.zip
# Note: your Google Calendar secret links live inside the app and cannot be read back; keep them somewhere safe.
set -uo pipefail
cd "$(dirname "$0")"
: "${TV_HOST:?set TV_HOST, e.g. TV_HOST=192.168.0.10 $0}"
PY=venv/bin/python
[ -x "$PY" ] || PY=venv/Scripts/python.exe
REPO="$(cd .. && pwd)"
STAMP=$(date +%Y%m%d-%H%M)
WORK="$(mktemp -d)/glass-tv-complete-backup-$STAMP"
TV="$WORK/tv"
mkdir -p "$TV/files"
tv() { "$PY" -I tv.py "$@"; }

echo "Asking the TV what it is ..."
tv --probe > "$TV/system.txt" 2>&1 || { echo "Could not reach the TV at $TV_HOST. Is it on, with network debugging allowed?" >&2; exit 1; }
tv 'getprop' > "$TV/getprop.txt" 2>&1
tv 'pm list packages -f' > "$TV/packages-all.txt" 2>&1
tv 'pm list packages -3' > "$TV/packages-user.txt" 2>&1
tv 'pm list packages -d' > "$TV/packages-disabled.txt" 2>&1
tv 'cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME' > "$TV/home-apps.txt" 2>&1
tv 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' > "$TV/home-now.txt" 2>&1
tv 'settings list system' > "$TV/settings-system.txt" 2>&1
tv 'settings list secure' > "$TV/settings-secure.txt" 2>&1
tv 'settings list global' > "$TV/settings-global.txt" 2>&1
tv 'dumpsys package com.gathin.tvhome | grep -E "versionCode|versionName|firstInstallTime|lastUpdateTime"' > "$TV/glass-tv-version.txt" 2>&1

echo "Copying the Glass TV app and its files ..."
APK=$(tv 'pm path com.gathin.tvhome' 2>/dev/null | head -n 1 | sed 's/^package://' | tr -d '\r')
[ -n "$APK" ] && "$PY" -I tv.py --pull "$APK" "$TV/glass-tv-installed.apk" >/dev/null 2>&1
DIR=/sdcard/Android/data/com.gathin.tvhome/files
tv "find $DIR -type f 2>/dev/null" | tr -d '\r' | while read -r f; do
  [ -z "$f" ] && continue
  rel="${f#$DIR/}"
  mkdir -p "$TV/files/$(dirname "$rel")"
  "$PY" -I tv.py --pull "$f" "$TV/files/$rel" >/dev/null 2>&1
done

echo "Adding the source ..."
mkdir -p "$WORK/source"
( cd "$REPO" && zip -qr "$WORK/source/glass-tv-source.zip" . -x "tools/venv/*" "*/build/*" "*/.gradle/*" "backup/*" ".git/*" "*.DS_Store" )
cp "$REPO"/launcher/theme-config.json "$WORK/source/" 2>/dev/null || true
cat > "$WORK/README.txt" <<TXT
Glass TV complete backup, $STAMP
tv/system.txt            the TV: model, Android version
tv/glass-tv-installed.apk  the Glass TV app exactly as installed
tv/files/                  your settings (config.json), app wallpapers, calendar files
tv/packages-*.txt          apps installed and disabled
tv/settings-*.txt          the TV's own settings
source/glass-tv-source.zip everything needed to build it again (launcher/, tools/, themes/, docs/)
To restore: unzip source, run launcher/build-and-install.sh, then copy tv/files back with tools/theme.sh or adb push.
TXT
OUT="$HOME/Downloads/glass-tv-complete-backup-$STAMP.zip"
( cd "$(dirname "$WORK")" && zip -qr "$OUT" "$(basename "$WORK")" )
echo "Done: $OUT"
