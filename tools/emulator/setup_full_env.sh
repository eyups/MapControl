#!/usr/bin/env bash
# Emülatörü (yeniden) tam test ortamına hazırlar.
#
# Kapsam:
#   1) Fake VDBus 5 paket + VDBus Lab overlay
#   2) iFlytek TTS APK + resvr (2.2 GB) + izinler
#   3) RoadDeck debug APK + Vosk TR uyandırma modeli + asistan prefs
#   4) Host mikrofonu + araç seed (power ON, P, motor)
#   5) HAB kamera shim + dört kamera test klibi
#
# Kullanım:
#   sh tools/emulator/setup_full_env.sh
#   sh tools/emulator/setup_full_env.sh --serial emulator-5554
#   sh tools/emulator/setup_full_env.sh --camera single
#   sh tools/emulator/setup_full_env.sh --skip-camera
#   sh tools/emulator/setup_full_env.sh --skip-voice
#
# AVD yeniden oluşturulduysa önce 16 GB data partition ile açılmalıdır:
#   emulator -avd MapControl_Tiggo8_156 -partition-size 16384 -wipe-data ...
# Wipe sonrası RoadDeck uygulama verisi ayrıca geri yüklenmelidir.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
ADB_BIN="${ADB:-$(command -v adb)}"
CAMERA_MODE="four"
SKIP_CAMERA=0
SKIP_VOICE=0

while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --camera) CAMERA_MODE="$2"; shift 2 ;;
    --skip-camera) SKIP_CAMERA=1; shift ;;
    --skip-voice) SKIP_VOICE=1; shift ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "Bilinmeyen argüman: $1" >&2; exit 2 ;;
  esac
done

case "$CAMERA_MODE" in
  four|single) ;;
  *) echo "--camera four|single olmalı" >&2; exit 2 ;;
esac

adb() { "$ADB_BIN" -s "$SERIAL" "$@"; }

echo "== cihaz =="
adb get-state >/dev/null
MODEL="$(adb shell getprop ro.product.model | tr -d '\r')"
case "$MODEL" in
  *sdk*|*Emulator*|*"Android SDK"*) ;;
  *) echo "[FAIL] $SERIAL emülatör gibi görünmüyor (model=$MODEL)" >&2; exit 1 ;;
esac
adb root >/dev/null 2>&1 || true
sleep 1

echo "== data partition =="
DATA_KB="$(adb shell df -k /data 2>/dev/null | awk 'NR==2 {print $2}' | tr -d '\r')"
DATA_GB=$(( ${DATA_KB:-0} / 1024 / 1024 ))
echo "  /data: ${DATA_GB} GB"
if [ "$DATA_GB" -lt 14 ]; then
  cat >&2 <<'EOF'
[FAIL] /data 16 GB değil. DVR 2 GB rezerv + 10 dk headroom için 16 GB AVD gerekli.
       Veri kaybını önlemek için önce tam AVD ve RoadDeck verisi yedeklenmeli.
       Yeni/wipe edilmiş AVD:
         emulator -avd MapControl_Tiggo8_156 -partition-size 16384 -wipe-data ...
EOF
  exit 1
fi

if [ "$SKIP_VOICE" = "0" ]; then
  echo "== ses/VDBus/asistan =="
  ANDROID_SERIAL="$SERIAL" ADB="$ADB_BIN" \
    sh "$ROOT/tools/emulator/setup_voice_env.sh" --serial "$SERIAL" --no-start
fi

if [ "$SKIP_CAMERA" = "0" ]; then
  echo "== kamera shim =="
  ANDROID_SERIAL="$SERIAL" ADB="$ADB_BIN" sh "$ROOT/camera-emu/install.sh"
  if [ "$CAMERA_MODE" = "four" ]; then
    echo "== dört kamera test klibi =="
    ANDROID_SERIAL="$SERIAL" ADB="$ADB_BIN" sh "$ROOT/camera-emu/install_four_cameras.sh"
  fi
fi

echo "== uygulamayı başlat =="
adb shell am force-stop com.eyups.roaddeck >/dev/null 2>&1 || true
adb shell am start -n com.eyups.roaddeck/com.mapcontrol.ui.activity.MainActivity >/dev/null

echo "== doğrulama =="
for pkg in \
  com.desaysv.ivi.vds.carinfo \
  com.desaysv.ivi.vds.carlan \
  com.desaysv.ivi.vds.carstate \
  com.desaysv.ivi.vds.setting \
  com.desaysv.ivi.vds.sms \
  com.eyups.emulatorcontrol \
  com.iflytek.cutefly.speechclient.hmi \
  com.eyups.roaddeck; do
  if adb shell pm list packages | tr -d '\r' | grep -qx "package:$pkg"; then
    echo "  [OK] $pkg"
  else
    echo "  [FAIL] $pkg" >&2
    exit 1
  fi
done

if [ "$SKIP_CAMERA" = "0" ]; then
  WRAP="$(adb shell getprop wrap.com.eyups.roaddeck | tr -d '\r')"
  [ "$WRAP" = "/data/local/tmp/habwrap.sh" ] || {
    echo "  [FAIL] camera wrap=$WRAP" >&2; exit 1;
  }
  adb shell 'ls /data/local/tmp/habcam/cam0.yuyv >/dev/null 2>&1' || {
    echo "  [FAIL] /data/local/tmp/habcam/cam0.yuyv yok" >&2; exit 1;
  }
  echo "  [OK] camera shim + $CAMERA_MODE kamera"
fi

if [ "$SKIP_VOICE" = "0" ]; then
  HAVE_RESVR="$(adb shell "du -sm /data/user/0/com.iflytek.cutefly.speechclient.hmi/files/vr/iflytek/resvr 2>/dev/null | cut -f1" | tr -d '\r')"
  [ "${HAVE_RESVR:-0}" -ge 1000 ] || {
    echo "  [FAIL] iFlytek resvr eksik (${HAVE_RESVR:-0} MB)" >&2; exit 1;
  }
  echo "  [OK] iFlytek TTS resvr (${HAVE_RESVR} MB)"
  adb shell "run-as com.eyups.roaddeck ls files/assistant/models/vosk-model-small-tr/final.mdl" >/dev/null || {
    echo "  [FAIL] Vosk modeli yok" >&2; exit 1;
  }
  echo "  [OK] Vosk TR modeli"
fi

adb shell 'df -h /data'
echo "[OK] emülatör tam ortamı hazır"
