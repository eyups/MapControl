#!/usr/bin/env bash
# Araç yakıt yüzdesini fake VDBus ReadOnly kanalına yazar.
# Kullanım: ./tools/emulator/set_fuel.sh 42 [emulator-5554]
set -euo pipefail
VALUE="${1:?kullanım: set_fuel.sh <0-100> [serial]}"
SERIAL="${2:-${ANDROID_SERIAL:-emulator-5554}}"
case "$VALUE" in
  ''|*[!0-9]*) echo "Yakıt 0..100 arasında tam sayı olmalı." >&2; exit 2 ;;
esac
if [ "$VALUE" -lt 0 ] || [ "$VALUE" -gt 100 ]; then
  echo "Yakıt 0..100 aralığında olmalı." >&2
  exit 2
fi
adb -s "$SERIAL" shell am broadcast \
  -n com.desaysv.ivi.vds.carinfo/com.eyups.fakevdbus.ControlReceiver \
  -a com.eyups.fakevdbus.CONTROL \
  --ei event 327684 --ei cmd 68 --eia values "$VALUE" >/dev/null
echo "Yakıt ayarlandı: %$VALUE ($SERIAL)"
