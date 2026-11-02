#!/usr/bin/env bash
# Emülatörde sinyal simülasyonu: left | right | hazard | off
set -euo pipefail
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
MODE="${1:-}"
PKG=com.desaysv.ivi.vds.carinfo
case "$MODE" in
  left)   L=1 R=0 H=0;;
  right)  L=0 R=1 H=0;;
  hazard) L=1 R=1 H=1;;
  off)    L=0 R=0 H=0;;
  *) echo "kullanım: $0 left|right|hazard|off" >&2; exit 2;;
esac
send() { adb -s "$SERIAL" shell am broadcast -n "$PKG/com.eyups.fakevdbus.ControlReceiver" \
          -a com.eyups.fakevdbus.CONTROL --ei event 327684 --ei cmd "$1" --eia values "$2" >/dev/null; }
send 35 "$L"   # ID_DIRECTION_IND_LEFT
send 33 "$L"   # ID_LH_TURN_LIGHT_STS
send 34 "$R"   # ID_DIRECTION_IND_RIGHT
send 32 "$R"   # ID_RH_TURN_LIGHT_STS
send 79 "$H"   # ID_HAZARD_LIGHT
echo "[OK] sinyal: $MODE"
