#!/bin/bash
# Dört kamerayı ayrı gerçek kliplerle besler (ön/arka/sol/sağ).
# Gereksinim: ~2 GB boş /data; 4 x 10 s x 1280x800 YUYV = 4 x 512 MB.
# Kullanım: ANDROID_SERIAL=emulator-5554 ./camera-emu/install_four_cameras.sh
set -euo pipefail
DIR=$(cd "$(dirname "$0")" && pwd)
SERIAL="${ANDROID_SERIAL:-emulator-5554}"
ADB=(adb -s "$SERIAL")
CLIPS="$DIR/clips"
TMP="${TMPDIR:-/tmp}/habcam-four"
mkdir -p "$TMP"

gen() { # src out extra_filter
    ffmpeg -y -v error -ss 0 -t 10 -i "$1" \
        -vf "${3}scale=1280:800:force_original_aspect_ratio=increase,crop=1280:800,fps=25" \
        -pix_fmt yuyv422 -f rawvideo "$2"
}

# Eşleme (shim inputId): 8=sol, 5=ön, 9=arka, 4=sağ
gen "$CLIPS/side-mirror.mp4"    "$TMP/cam0.yuyv" ""        # sol ayna
# Ön kamera: otopark POV + üst ortada A18
ffmpeg -y -v error -ss 0 -t 10 -i "$CLIPS/parking-garage.mp4" -loop 1 -i "$CLIPS/a18-overlay.png" \
    -filter_complex "[0:v]scale=1280:800:force_original_aspect_ratio=increase,crop=1280:800,fps=25[bg];[bg][1:v]overlay=0:0,format=yuv422p[out]" \
    -map "[out]" -t 10 -pix_fmt yuyv422 -f rawvideo "$TMP/cam1.yuyv"
gen "$CLIPS/rear-pov.mp4"       "$TMP/cam2.yuyv" ""        # arka kamera
gen "$CLIPS/side-mirror.mp4"    "$TMP/cam3.yuyv" "hflip,"  # sağ ayna (aynalı)

"${ADB[@]}" root >/dev/null 2>&1 || true
"${ADB[@]}" shell mkdir -p /data/local/tmp/habcam
for i in 0 1 2 3; do
    "${ADB[@]}" push "$TMP/cam$i.yuyv" "/data/local/tmp/habcam/cam$i.yuyv" >/dev/null
done
"${ADB[@]}" shell 'rm -f /data/local/tmp/habcam/frame.yuyv; chcon u:object_r:system_file:s0 /data/local/tmp/habcam/cam*.yuyv'
"${ADB[@]}" shell 'am force-stop com.eyups.roaddeck; am start -n com.eyups.roaddeck/com.mapcontrol.ui.activity.MainActivity' >/dev/null 2>&1 || true
echo "dört kamera kuruldu (sol/ön/arka/sağ)"
