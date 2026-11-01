#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"

# Güvenlik: bu APK'lar OEM paket adlarını taklit eder; sadece emülatörde kurulmalı.
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
if [[ "$MODEL" != *"sdk"* && "$MODEL" != *"Emulator"* && "$MODEL" != *"Android SDK"* ]]; then
  echo "[FAIL] $SERIAL emülatör gibi görünmüyor (model=$MODEL). Fake VDBus kurulmadı." >&2
  exit 1
fi

JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
"$ROOT/gradlew" -p "$ROOT" :fake-vdbus:assembleDebug --console=plain

for flavor in carinfo sms carlan; do
  apk="$ROOT/fake-vdbus/build/outputs/apk/$flavor/debug/fake-vdbus-$flavor-debug.apk"
  echo "[INFO] $flavor -> $apk"
  adb -s "$SERIAL" install -r -g "$apk"
done

for pkg in com.desaysv.ivi.vds.carinfo com.desaysv.ivi.vds.sms com.desaysv.ivi.vds.carlan; do
  adb -s "$SERIAL" shell pm grant "$pkg" android.permission.POST_NOTIFICATIONS >/dev/null 2>&1 || true
done

echo "[OK] Fake VDBus paketleri kuruldu."
