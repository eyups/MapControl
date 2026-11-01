#!/usr/bin/env bash
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
PKG="${FAKE_VDBUS_PKG:-com.desaysv.ivi.vds.carinfo}"
EVENT="${1:?event id gerekli}"
CMD="${2:?cmd id gerekli}"
shift 2
VALUES="${*:-0}"

adb -s "$SERIAL" shell am broadcast \
  -n "$PKG/com.eyups.fakevdbus.ControlReceiver" \
  -a com.eyups.fakevdbus.CONTROL \
  --ei event "$EVENT" \
  --ei cmd "$CMD" \
  --eia values "$VALUES"
