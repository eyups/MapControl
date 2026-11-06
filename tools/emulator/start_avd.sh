#!/usr/bin/env bash
set -euo pipefail

AVD="${1:-MapControl_Tiggo8_156}"
ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
EMULATOR="$ANDROID_HOME/emulator/emulator"
ADB="$ANDROID_HOME/platform-tools/adb"

if ! command -v "$EMULATOR" >/dev/null 2>&1; then
  echo "[FAIL] emulator bulunamadı: $EMULATOR" >&2
  exit 1
fi

# Zaten çalışan bir emülatör varsa ikinci instance açma.
if "$ADB" devices | awk '$2 == "device" && $1 == "emulator-5554" {found=1} END {exit found ? 0 : 1}'; then
  echo "[OK] Çalışan emülatör var: emulator-5554"
  exit 0
fi

echo "[INFO] AVD başlatılıyor: $AVD"
# -grpc-use-token: voice_mic.py host mikrofon enjeksiyonu için gerekli.
exec "$EMULATOR" -avd "$AVD" -port 5554 \
  -grpc 8558 -grpc-use-token \
  -allow-host-audio \
  -no-boot-anim \
  -no-snapshot \
  -gpu swiftshader_indirect
