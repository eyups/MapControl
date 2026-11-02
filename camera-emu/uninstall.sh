#!/bin/bash
# Removes the camera emulation (app is untouched; only the LD_PRELOAD wrap is cleared).
set -e
PKG=com.eyups.roaddeck
ADB=${ADB:-adb}
[ -n "$ANDROID_SERIAL" ] && ADB="$ADB -s $ANDROID_SERIAL"
$ADB shell "setprop wrap.$PKG ''"
echo "removed. Restart app to run without the camera shim."
