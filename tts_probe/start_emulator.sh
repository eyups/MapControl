#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
AVD="${1:-MapControl_Tiggo8_156}"
EMULATOR="${ANDROID_HOME:-$HOME/Library/Android/sdk}/emulator/emulator"
# -writable-system: /system Türkçe kcm yaması için.
# -charmap: host Unicode -> doğru Android keycode eşlemesi için.
# -no-audio KULLANMA; ses çıkışı kapalı kalır.
exec "$EMULATOR" -avd "$AVD" \
  -no-boot-anim -no-snapshot -gpu swiftshader_indirect \
  -writable-system \
  -charmap "$ROOT/tools/keymap/host_charmap_tr.txt"
