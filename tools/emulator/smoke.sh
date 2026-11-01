#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
PKG="com.eyups.roaddeck"
ACT="$PKG/com.mapcontrol.ui.activity.MainActivity"
OUT="$ROOT/tools/emulator/out/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT"

echo "[INFO] Cihaz: $SERIAL"
adb -s "$SERIAL" wait-for-device
adb -s "$SERIAL" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1 || true
adb -s "$SERIAL" shell wm dismiss-keyguard >/dev/null 2>&1 || true
adb -s "$SERIAL" logcat -c

adb -s "$SERIAL" shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb -s "$SERIAL" shell am start -W -n "$ACT" | tee "$OUT/start.txt"
sleep 3

adb -s "$SERIAL" exec-out screencap -p > "$OUT/main.png"
adb -s "$SERIAL" logcat -d -v threadtime > "$OUT/logcat.txt"
adb -s "$SERIAL" shell dumpsys activity activities > "$OUT/activities.txt" 2>&1 || true
adb -s "$SERIAL" shell dumpsys meminfo "$PKG" > "$OUT/meminfo.txt" 2>&1 || true

if grep -qE 'FATAL EXCEPTION|ANR in com\.eyups\.roaddeck' "$OUT/logcat.txt"; then
  echo "[FAIL] Crash/ANR bulundu. Log: $OUT/logcat.txt" >&2
  grep -nE 'FATAL EXCEPTION|ANR in com\.eyups\.roaddeck' "$OUT/logcat.txt" | tail -20 >&2 || true
  exit 1
fi

if ! adb -s "$SERIAL" shell dumpsys activity activities | grep -q 'mResumedActivity.*com.eyups.roaddeck'; then
  echo "[WARN] Uygulama resumed görünmüyor; activities.txt kontrol edilecek."
fi

echo "[OK] Smoke tamam. Çıktı: $OUT"
