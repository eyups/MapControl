#!/bin/bash
# Installs the camera emulator into a running emulator (userdebug, root adb).
# The app (com.eyups.roaddeck) is NOT modified.
set -e
PKG=com.eyups.roaddeck
ADB=${ADB:-adb}
[ -n "$ANDROID_SERIAL" ] && ADB="$ADB -s $ANDROID_SERIAL"
DIR=$(cd "$(dirname "$0")" && pwd)
if [ ! -f "$DIR/out/libhabshim.so" ]; then
    "$DIR/build.sh"
fi
$ADB root >/dev/null 2>&1 || true
sleep 1
$ADB push "$DIR/out/libhabshim.so" /data/local/tmp/libhabshim.so >/dev/null
if [ -f "$DIR/out/frame.yuyv" ]; then
    $ADB shell mkdir -p /data/local/tmp/habcam
    $ADB push "$DIR/out/frame.yuyv" /data/local/tmp/habcam/frame.yuyv >/dev/null || true
fi
$ADB shell "cat > /data/local/tmp/habwrap.sh <<'EOS'
#!/system/bin/sh
export LD_PRELOAD=/data/local/tmp/libhabshim.so
exec \"\$@\"
EOS
chmod 755 /data/local/tmp/habwrap.sh /data/local/tmp/libhabshim.so
chcon u:object_r:system_file:s0 /data/local/tmp/habwrap.sh /data/local/tmp/libhabshim.so 2>/dev/null || true
[ -f /data/local/tmp/habcam/frame.yuyv ] && chcon u:object_r:system_file:s0 /data/local/tmp/habcam/frame.yuyv 2>/dev/null || true
[ -e /dev/hab ] || touch /dev/hab
setprop wrap.$PKG /data/local/tmp/habwrap.sh"
$ADB shell "am force-stop $PKG; sleep 1; am start -n $PKG/com.eyups.roaddeck.ui.activity.MainActivity" >/dev/null 2>&1 || true
echo "installed; app restarted with shim"
