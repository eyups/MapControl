#!/usr/bin/env bash
# tts30 / tts40 Gradle flavor'ları için native .so setlerini hazırlar.
#
# Kaynaklar (araç root dump'ı):
#   tts30: LionAutoNavi.apk      -> issxtts30 + issauth + xtts + websockets + zmq
#   tts40: IflytekSpeechClient.apk -> issxtts40 + issauth + xtts + websockets + zmq
#                                   + idas + opencc + gnustl_shared
#
# Çıktı:
#   app/src/tts30/jniLibs/arm64-v8a/
#   app/src/tts40/jniLibs/arm64-v8a/
#
# Kullanım:
#   tools/emulator/prepare_tts_flavors.sh
#   ./gradlew :app:assembleTts30Debug
#   ./gradlew :app:assembleTts40Debug
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DUMP="$ROOT/car_info/rootdump/full_20261101/root/product/app"
APK30="$DUMP/LionAutoNavi/LionAutoNavi.apk"
APK40="$DUMP/IflytekSpeechClient/IflytekSpeechClient.apk"
OUT30="$ROOT/app/src/tts30/jniLibs/arm64-v8a"
OUT40="$ROOT/app/src/tts40/jniLibs/arm64-v8a"

[ -f "$APK30" ] || { echo "APK yok: $APK30"; exit 1; }
[ -f "$APK40" ] || { echo "APK yok: $APK40"; exit 1; }

extract() {
  local apk="$1" out="$2" lib="$3"
  unzip -j -o "$apk" "lib/arm64-v8a/$lib" -d "$out" >/dev/null
  [ -s "$out/$lib" ] || { echo "çıkarılamadı: $lib ($apk)"; exit 1; }
}

rm -rf "$OUT30" "$OUT40"
mkdir -p "$OUT30" "$OUT40"

for lib in libissxtts30.so libissauth.so libxtts.so libwebsockets.so libzmq.so; do
  extract "$APK30" "$OUT30" "$lib"
done
for lib in libissxtts40.so libissauth.so libxtts.so libwebsockets.so libzmq.so \
           libidas.so libopencc.so libgnustl_shared.so; do
  extract "$APK40" "$OUT40" "$lib"
done

echo "tts30:"
ls -lh "$OUT30" | tail -n +2
echo "tts40:"
ls -lh "$OUT40" | tail -n +2
echo "hazır. Build: ./gradlew :app:assembleTts30Debug :app:assembleTts40Debug"
