#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
KM="$ROOT/tools/keymap"
adb root
sleep 1
adb wait-for-device
adb remount
adb push "$KM/Generic_tr.kcm" /system/usr/keychars/Generic.kcm
adb push "$KM/Virtual_tr.kcm" /system/usr/keychars/Virtual.kcm
adb push "$KM/qwerty2_tr.kcm" /system/usr/keychars/qwerty2.kcm
adb shell chmod 644 /system/usr/keychars/Generic.kcm /system/usr/keychars/Virtual.kcm /system/usr/keychars/qwerty2.kcm
echo "Yeniden başlatılıyor..."
adb reboot
