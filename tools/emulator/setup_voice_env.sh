#!/usr/bin/env bash
# Sesli asistan emülatör ortamını tek komutla kurar/onarır.
#
# Kurulanlar:
#   1) fake-vdbus (carinfo, carlan, carstate, setting, sms) — kalıcı seed
#   2) emulator-control / VDBus Lab (sürüklenebilir "VDBus" overlay'i)
#   3) iFlytek TTS (hazır patched APK + 2.2 GB resvr kaynakları)
#   4) RoadDeck debug APK + Vosk TR uyandırma modeli
#   5) Asistan prefs (etkin, "dinle beni", overlay kapalı) + araç seed'i
#
# Kullanım:
#   sh tools/emulator/setup_voice_env.sh [--serial emulator-5554]
#        [--skip-app] [--skip-model] [--skip-iflytek] [--no-start]
#
# Uyandırma sesi enjeksiyonu (emülatör mikrofonu):
#   python3 tools/emulator/voice_mic.py /tmp/va_voice_turn.pcm
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
ADB_BIN="${ADB:-$(command -v adb)}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
APP_PKG="com.eyups.roaddeck"
IFLY_PKG="com.iflytek.cutefly.speechclient.hmi"
IFLY_DIR="$ROOT/car_info/iflytek_emulator_20261101"
IFLY_APK="$IFLY_DIR/iflytek_audio_fixed.apk"
RESVR="$IFLY_DIR/vr/iflytek/resvr"
VOSK_URL="https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip"
VOSK_SHA="8c8d07cec1ce31add14967c16891c84152cdf76d391e33c08278119b9ea96e5"
VOSK_DIR="/tmp/vosk-model-small-tr-0.3"

SKIP_APP=0
SKIP_MODEL=0
SKIP_IFLYTEK=0
NO_START=0
while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --skip-app) SKIP_APP=1; shift ;;
    --skip-model) SKIP_MODEL=1; shift ;;
    --skip-iflytek) SKIP_IFLYTEK=1; shift ;;
    --no-start) NO_START=1; shift ;;
    -h|--help) sed -n '2,30p' "$0"; exit 0 ;;
    *) echo "Bilinmeyen argüman: $1" >&2; exit 2 ;;
  esac
done

adb() { "$ADB_BIN" -s "$SERIAL" "$@"; }

echo "== cihaz =="
adb get-state >/dev/null
adb root >/dev/null 2>&1 || true
sleep 1

echo "== fake-vdbus (5 paket) =="
"$ROOT/gradlew" -p "$ROOT" \
  :fake-vdbus:assembleCarinfoDebug :fake-vdbus:assembleCarlanDebug \
  :fake-vdbus:assembleCarstateDebug :fake-vdbus:assembleSettingDebug \
  :fake-vdbus:assembleSmsDebug --console=plain >/dev/null
for flavor in carinfo carlan carstate setting sms; do
  adb install -r "$ROOT/fake-vdbus/build/outputs/apk/$flavor/debug/fake-vdbus-$flavor-debug.apk" >/dev/null
  echo "  kuruldu: com.desaysv.ivi.vds.$flavor"
done

echo "== VDBus Lab overlay =="
ANDROID_SERIAL="$SERIAL" sh "$ROOT/tools/emulator/install_control.sh" >/dev/null
adb shell appops set com.eyups.emulatorcontrol SYSTEM_ALERT_WINDOW allow

if [ "$SKIP_IFLYTEK" = "0" ]; then
  echo "== iFlytek TTS (emülatör portu) =="
  if [ ! -f "$IFLY_APK" ]; then
    echo "  HATA: $IFLY_APK yok" >&2
    exit 1
  fi
  adb install -r -g "$IFLY_APK" >/dev/null
  APPUID="$(adb shell "stat -c %u /data/user/0/$IFLY_PKG" | tr -d '\r')"
  adb shell "mkdir -p /data/user/0/$IFLY_PKG/files/vr/iflytek"
  HAVE="$(adb shell "du -sm /data/user/0/$IFLY_PKG/files/vr/iflytek/resvr 2>/dev/null | cut -f1" | tr -d '\r')"
  if [ -z "$HAVE" ] || [ "$HAVE" -lt 1000 ]; then
    echo "  resvr kopyalanıyor (2.2 GB, ~15 sn)…"
    adb push "$RESVR" "/data/user/0/$IFLY_PKG/files/vr/iflytek/" >/dev/null
    adb shell "chown -R $APPUID:$APPUID /data/user/0/$IFLY_PKG/files/vr; restorecon -R /data/user/0/$IFLY_PKG/files/vr"
  else
    echo "  resvr zaten kurulu (${HAVE} MB)"
  fi
  adb shell appops set "$IFLY_PKG" RECORD_AUDIO allow || true
  adb shell pm grant "$IFLY_PKG" android.permission.RECORD_AUDIO 2>/dev/null || true
fi

if [ "$SKIP_APP" = "0" ]; then
  echo "== RoadDeck debug =="
  "$ROOT/gradlew" -p "$ROOT" :app:assembleDebug --console=plain >/dev/null
  adb install -r "$ROOT/app/build/outputs/apk/debug/app-debug.apk" >/dev/null
fi

if [ "$SKIP_MODEL" = "0" ]; then
  HAVE_MODEL="$(adb shell "run-as $APP_PKG ls files/assistant/models/vosk-model-small-tr/final.mdl 2>/dev/null" | tr -d '\r')"
  if [ -z "$HAVE_MODEL" ]; then
    echo "== Vosk TR modeli =="
    if [ ! -f "$VOSK_DIR/final.mdl" ]; then
      if [ ! -f /tmp/vosk-tr.zip ]; then
        curl -fL --max-time 600 -o /tmp/vosk-tr.zip "$VOSK_URL"
      fi
      SUM="$(shasum -a 256 /tmp/vosk-tr.zip | awk '{print $1}')"
      [ "$SUM" = "$VOSK_SHA" ] || { echo "  HATA: model sha256 uyuşmuyor" >&2; exit 1; }
      rm -rf "$VOSK_DIR"
      unzip -q /tmp/vosk-tr.zip -d /tmp
    fi
    adb shell "run-as $APP_PKG mkdir -p files/assistant/models/vosk-model-small-tr"
    ( cd "$VOSK_DIR" && tar cf - . ) | \
      adb shell "run-as $APP_PKG tar -C files/assistant/models/vosk-model-small-tr -xf -"
    echo "  model kuruldu (56 MB)"
  else
    echo "== Vosk modeli zaten kurulu =="
  fi
fi

echo "== prefs =="
adb shell am force-stop "$APP_PKG"
adb exec-out "run-as $APP_PKG cat shared_prefs/RoadDeckPrefs.xml" > /tmp/mcprefs.xml 2>/dev/null || true
python3 - "$ROOT" <<'PY'
import re, sys
path='/tmp/mcprefs.xml'
try:
    s=open(path).read()
except Exception:
    s="<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n</map>\n"
def put(kind, name, value):
    global s
    if kind == 'string':
        pat = r'<string name="%s"[^>]*>.*?</string>|<string name="%s"[^>]*/>' % (
            re.escape(name), re.escape(name))
        line = '    <string name="%s">%s</string>' % (name, value)
    else:
        pat = r'<%s name="%s"[^>]*/>' % (kind, re.escape(name))
        line = '    <%s name="%s" value="%s" />' % (kind, name, value)
    if re.search(pat, s, re.S):
        s = re.sub(pat, line.strip(), s, flags=re.S)
    else:
        s = s.replace('</map>', line + '\n</map>')
put('boolean','assistantEnabled','true')
put('boolean','disclaimerAccepted','true')
put('boolean','onboardingCompleted','true')
put('string','assistantWakeWord','dinle beni')
put('string','assistantWakeSound','none')
open(path,'w').write(s)
print('  prefs hazır')
PY
adb push /tmp/mcprefs.xml /data/local/tmp/mcprefs.xml >/dev/null
adb shell "run-as $APP_PKG cp /data/local/tmp/mcprefs.xml shared_prefs/RoadDeckPrefs.xml"

echo "== host mikrofonu =="
python3 "$ROOT/tools/emulator/voice_mic.py" --enable-only 2>/dev/null || \
  echo "  UYARI: gRPC mikrofon açılamadı (pip3 install grpcio grpcio-tools)"

echo "== araç seed (power ON, P, motor çalışıyor) =="
seed() {
  adb shell "am broadcast -a com.eyups.fakevdbus.CONTROL -p com.desaysv.ivi.vds.carinfo \
    --ei event $1 --ei cmd $2 --eia values $3" >/dev/null 2>&1
}
seed 327684 14 2   # güç modu ON
seed 327684 26 1   # vites P
seed 327684 74 1   # motor
seed 327684 119 0  # gösterge hızı
seed 327684 0 0    # gerçek hız
seed 327681 6 1    # cam tavan kapalı
seed 327681 10 1   # güneşlik kapalı
seed 327690 19 145 # dış sıcaklık 17.5 °C
seed 327690 18 1   # klima çalışıyor
seed 327684 68 54  # yakıt %54

if [ "$NO_START" = "0" ]; then
  adb shell am start -n "$APP_PKG/com.eyups.roaddeck.ui.activity.MainActivity" >/dev/null 2>&1 || true
fi

cat <<EOF

Kurulum tamam.

- VDBus Lab: uygulama çekmecesinde "VDBus Lab"; overlay'de sürüklenebilir
  "VDBus" pill'i görünür. Power ON/STBY/OFF ve event/cmd/values buradan.
- Uyandırma modeli: files/assistant/models/vosk-model-small-tr
- Asistan: güç ON'dan 20 sn sonra dinlemeye başlar ("dinle beni").
- Emülatör mikrofonuna ses verme:
    python3 tools/emulator/voice_mic.py /tmp/va_voice_turn.pcm
  (realAudioEnabled=True olmalı; script bunu kendisi ayarlar.)
- iFlytek TTS: ilk konuşmada uygulama servisi startService+bind eder.
  Kaynaklar: /data/user/0/$IFLY_PKG/files/vr/iflytek/resvr
EOF
