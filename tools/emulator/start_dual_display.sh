#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
AVD="${1:-MapControl_Tiggo8_156}"
DISPLAY_INDEX="${2:-1}"
WIDTH="${3:-1280}"
HEIGHT="${4:-720}"
DPI="${5:-240}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
EMULATOR="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"

if "$ADB" devices | awk '$2 == "device" && $1 ~ /^emulator-/ {found=1} END {exit found ? 0 : 1}'; then
  echo "[FAIL] Önce çalışan emülatörü kapat: adb -s emulator-5554 emu kill" >&2
  exit 1
fi

echo "[INFO] $AVD başlatılıyor; ek display index=$DISPLAY_INDEX (guest genelde display 1 olur) ${WIDTH}x${HEIGHT}@${DPI}dpi"
nohup "$EMULATOR" -avd "$AVD" \
  -no-boot-anim -no-snapshot -gpu swiftshader_indirect \
  -multidisplay "$DISPLAY_INDEX,$WIDTH,$HEIGHT,$DPI,0" \
  > "$ROOT/tools/emulator/out/emulator-dual.log" 2>&1 &
echo $! > "$ROOT/tools/emulator/out/emulator-dual.pid"

"$ADB" wait-for-device
for _ in $(seq 1 120); do
  [[ "$("$ADB" -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && break
  sleep 1
done

# -multidisplay bazen boot sırasında adb offline olduğu için START broadcast'ini
# kaçırır; boot sonrası tekrar tetikle.
"$ADB" -s emulator-5554 shell am broadcast \
  -a com.android.emulator.multidisplay.START \
  -n com.android.emulator.multidisplay/.MultiDisplayServiceReceiver --user 0 >/dev/null 2>&1 || true
sleep 1

echo "[INFO] Display durumu:"
"$ADB" -s emulator-5554 shell dumpsys display | grep -E '^  Display [0-9]+:|DisplayDeviceInfo' | head -12
echo "[OK] İkincil display hazır (guest display ID genelde 1). Uygulama:"
echo "  ./tools/emulator/launch_on_display.sh 1 com.eyups.roaddeck/com.eyups.roaddeck.ui.activity.MainActivity"
