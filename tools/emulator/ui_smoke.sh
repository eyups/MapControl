#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
PKG="com.eyups.roaddeck"
ACT="$PKG/com.mapcontrol.ui.activity.MainActivity"
OUT="$ROOT/tools/emulator/out/ui-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

adb -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb -s "$SERIAL" logcat -c
adb -s "$SERIAL" shell am start -W -n "$ACT" >/dev/null
sleep 2

tap_text() {
  local label="$1"
  adb -s "$SERIAL" shell uiautomator dump /sdcard/window.xml >/dev/null 2>&1 || return 1
  adb -s "$SERIAL" pull /sdcard/window.xml "$OUT/window.xml" >/dev/null 2>&1 || return 1
  local xy
  xy="$(python3 - "$OUT/window.xml" "$label" <<'PY'
import re, sys, xml.etree.ElementTree as ET
path, label = sys.argv[1], sys.argv[2]
try:
    root = ET.parse(path).getroot()
except Exception:
    sys.exit(1)
for node in root.iter('node'):
    if node.get('text') == label:
        b = node.get('bounds', '')
        m = re.match(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', b)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2)
            sys.exit(0)
sys.exit(1)
PY
)" || { echo "[WARN] bulunamadı: $label"; return 1; }
  adb -s "$SERIAL" shell input tap $xy
  sleep 1
  echo "[OK] $label @ $xy"
}

for label in "Araç" "Sürücü" "Güvenlik" "Medya" "Bağlantı" "Ayarlar" "Ana Sayfa"; do
  tap_text "$label" || true
  adb -s "$SERIAL" exec-out screencap -p > "$OUT/${label//[^[:alnum:]]/_}.png" || true
done

adb -s "$SERIAL" logcat -d -v threadtime > "$OUT/logcat.txt"
if grep -qE 'FATAL EXCEPTION|ANR in com\.eyups\.roaddeck' "$OUT/logcat.txt"; then
  echo "[FAIL] UI smoke crash/ANR. $OUT/logcat.txt" >&2
  exit 1
fi
echo "[OK] UI smoke tamam: $OUT"
