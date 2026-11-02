#!/bin/bash
# Produces a raw YUYV loop (1280x800 @25fps) the shim feeds to all 4 cameras.
# Usage: ./make_video.sh [source-video] [seconds] [outfile]
set -e
SRC=${1:-}
SECS=${2:-3}
OUT=${3:-$(cd "$(dirname "$0")" && pwd)/out/frame.yuyv}
mkdir -p "$(dirname "$OUT")"
if [ -z "$SRC" ]; then
    ffmpeg -y -f lavfi -i "testsrc2=size=1280x800:rate=25" -t "$SECS" \
        -pix_fmt yuyv422 -f rawvideo "$OUT"
else
    ffmpeg -y -i "$SRC" -t "$SECS" -vf "scale=1280:800" \
        -pix_fmt yuyv422 -f rawvideo "$OUT"
fi
ls -lh "$OUT"
