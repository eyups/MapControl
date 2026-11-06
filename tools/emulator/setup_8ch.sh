#!/usr/bin/env bash
# 8ch test kaynagini OEM uygulamasinin files dizinine koyar.
#   sh tools/emulator/setup_8ch.sh <mono16k.pcm> [layout]
# APK: car_info/iflytek_emulator_20261101/iflytek_oem_8ch.apk
set -euo pipefail
SERIAL="${SERIAL:-emulator-5554}"
ADB="${ADB_BIN:-adb}"
PKG=com.iflytek.cutefly.speechclient.hmi
SRC="${1:?mono 16k pcm gerekli}"
LAYOUT="${2:-mic0_ref1}"
DIR="$(cd "$(dirname "$0")/../.." && pwd)"

python3 "$DIR/tools/emulator/emu8ch.py" "$SRC" /tmp/emu8ch.pcm --layout "$LAYOUT"
"$ADB" -s "$SERIAL" root >/dev/null 2>&1 || true
"$ADB" -s "$SERIAL" push /tmp/emu8ch.pcm /data/local/tmp/emu8ch.pcm >/dev/null
UIDGID=$("$ADB" -s "$SERIAL" shell stat -c '%u:%g' "/data/user/0/$PKG" | tr -d '\r')
"$ADB" -s "$SERIAL" shell "cp /data/local/tmp/emu8ch.pcm /data/user/0/$PKG/files/emu8ch.pcm && chown $UIDGID /data/user/0/$PKG/files/emu8ch.pcm && chmod 660 /data/user/0/$PKG/files/emu8ch.pcm"
echo "[OK] 8ch kaynak: /data/user/0/$PKG/files/emu8ch.pcm ($LAYOUT)"
echo "[i] recordType=4 olmali; uygulamayi yeniden baslat."
