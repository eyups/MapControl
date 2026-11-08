#!/usr/bin/env bash
# RoadDeck TTS flavor seçimi:
#   tools/emulator/install_tts_flavor.sh 30
#   tools/emulator/install_tts_flavor.sh 40
#
# 30 -> libissxtts30.so + /vr/iflytek/res/xTTSRes
# 40 -> libissxtts40.so + /vr/iflytek/resvr/xTTS40Res
#
# Emülatörde /vr yoksa DirectIflytekTts şu yollara düşer:
#   30: /data/local/tmp/tts30/xTTSRes (+ ../Active/TTSRes/res_version=1001 1062)
#   40: /data/local/tmp/tts40/xTTS40Res (+ ../Active/TTSRes/res_version=1031_1902_cst238)
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "kullanım: $0 30|40 [--no-build] [--serial emulator-5554]"
  exit 2
fi
case "$1" in
  30) FLAVOR=tts30; TASK=Tts30 ;;
  40) FLAVOR=tts40; TASK=Tts40 ;;
  *) echo "flavor 30 veya 40 olmalı"; exit 2 ;;
esac
shift
BUILD=1
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
while [ $# -gt 0 ]; do
  case "$1" in
    --no-build) BUILD=0; shift ;;
    --serial) SERIAL="$2"; shift 2 ;;
    *) echo "bilinmeyen argüman: $1"; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ADB=(adb -s "$SERIAL")
APK="$ROOT/app/build/outputs/apk/$FLAVOR/debug/app-$FLAVOR-debug.apk"

"$ROOT/tools/emulator/prepare_tts_flavors.sh" >/dev/null
if [ "$BUILD" = 1 ]; then
  (cd "$ROOT" && ./gradlew ":app:assemble${TASK}Debug" --console=plain >/dev/null)
fi
[ -f "$APK" ] || { echo "APK yok: $APK"; exit 1; }

log=$(mktemp)
if "${ADB[@]}" install -r "$APK" >"$log" 2>&1 && grep -q "Success" "$log"; then
  echo "kuruldu: $FLAVOR ($APK)"
else
  if grep -qE "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match" "$log"; then
    echo "imza çakışması -> com.eyups.roaddeck kaldırılıp yeniden kuruluyor"
    "${ADB[@]}" uninstall com.eyups.roaddeck >/dev/null 2>&1 || true
    "${ADB[@]}" install "$APK" >/dev/null
    echo "kuruldu: $FLAVOR"
  else
    cat "$log"; exit 1
  fi
fi

echo "test: adb -s $SERIAL shell am broadcast -n com.eyups.roaddeck/.tts.TtsTestReceiver -a com.eyups.roaddeck.TEST_TTS --es text Merhaba"
