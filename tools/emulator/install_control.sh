#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
JAVA_HOME="${JAVA_HOME:-/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
"$ROOT/gradlew" -p "$ROOT" :emulator-control:assembleDebug --console=plain
adb -s "$SERIAL" install -r -g "$ROOT/emulator-control/build/outputs/apk/debug/emulator-control-debug.apk"
adb -s "$SERIAL" shell appops set com.eyups.emulatorcontrol SYSTEM_ALERT_WINDOW allow
adb -s "$SERIAL" shell am start -W -n com.eyups.emulatorcontrol/.MainActivity
