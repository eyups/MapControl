#!/usr/bin/env bash
set -euo pipefail
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
DISPLAY="${1:?display id gerekli}"
shift
if [[ $# -eq 0 ]]; then
  echo "Kullanım: $0 <display> <package/activity>" >&2
  exit 1
fi
adb -s "$SERIAL" shell am start --display "$DISPLAY" -n "$1"
