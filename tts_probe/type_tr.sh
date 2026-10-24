#!/usr/bin/env bash
set -euo pipefail
if [ $# -lt 1 ]; then
  echo "Kullanım: $0 'Türkçe metin' [--restore]"
  exit 1
fi
TEXT="$1"
if ! adb shell pm list packages | grep -q '^package:com.android.adbkeyboard$'; then
  echo "ADBKeyboard kurulu değil."
  echo "curl -L -o /tmp/ADBKeyboard.apk https://raw.githubusercontent.com/senzhk/ADBKeyBoard/master/ADBKeyboard.apk"
  echo "adb install -r /tmp/ADBKeyboard.apk"
  exit 1
fi
adb shell ime enable com.android.adbkeyboard/.AdbIME >/dev/null
adb shell ime set com.android.adbkeyboard/.AdbIME >/dev/null
B64=$(printf '%s' "$TEXT" | base64 | tr -d '\n')
adb shell "am broadcast -a ADB_INPUT_B64 --es msg '$B64'" >/dev/null
echo "Gönderildi: $TEXT"
if [ "${2:-}" = "--restore" ]; then
  sleep 0.5
  adb shell ime set com.android.inputmethod.latin/.LatinIME >/dev/null
fi
