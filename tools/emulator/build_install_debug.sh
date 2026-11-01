#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
PKG="com.eyups.roaddeck"

# EMULATOR GUARD: debug test APK'ları araca kurulmamalı.
MODEL="$(adb -s "$SERIAL" shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
if [[ "$MODEL" != *"sdk"* && "$MODEL" != *"Emulator"* && "$MODEL" != *"Android SDK"* ]]; then
  echo "[FAIL] $SERIAL emülatör değil (model=$MODEL)." >&2
  exit 1
fi

if [[ "${1:-}" != "--no-build" ]]; then
  "$ROOT/gradlew" -p "$ROOT" :app:assembleDebug
fi

if [[ ! -f "$APK" ]]; then
  echo "[FAIL] APK yok: $APK" >&2
  exit 1
fi

echo "[INFO] Kuruluyor: $APK -> $SERIAL"
if ! adb -s "$SERIAL" install -r -g "$APK"; then
  echo "[WARN] İmza uyuşmadı; emülatörde eski paket kaldırılıp yeniden kurulacak."
  adb -s "$SERIAL" uninstall "$PKG" >/dev/null 2>&1 || true
  adb -s "$SERIAL" install -r -g "$APK"
fi

# Runtime izinleri mümkün olduğunca ver; protected olanlar zaten verilemez.
for perm in \
  android.permission.CAMERA \
  android.permission.RECORD_AUDIO \
  android.permission.ACCESS_FINE_LOCATION \
  android.permission.ACCESS_BACKGROUND_LOCATION \
  android.permission.POST_NOTIFICATIONS \
  android.permission.BLUETOOTH_CONNECT \
  android.permission.BLUETOOTH_SCAN \
  android.permission.MANAGE_EXTERNAL_STORAGE \
  android.permission.SYSTEM_ALERT_WINDOW \
  android.permission.PACKAGE_USAGE_STATS; do
  adb -s "$SERIAL" shell pm grant "$PKG" "$perm" >/dev/null 2>&1 || true
done

adb -s "$SERIAL" shell appops set "$PKG" MANAGE_EXTERNAL_STORAGE allow >/dev/null 2>&1 || true
adb -s "$SERIAL" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow >/dev/null 2>&1 || true
adb -s "$SERIAL" shell appops set "$PKG" PACKAGE_USAGE_STATS allow >/dev/null 2>&1 || true

echo "[OK] Kuruldu. Paket/aktivite:"
adb -s "$SERIAL" shell dumpsys package "$PKG" | grep -E 'versionName|versionCode' || true
