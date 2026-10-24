#!/usr/bin/env bash
set -euo pipefail

# MapControl'den bağımsız TTS probe için Sherpa-ONNX JNI + Türkçe Piper modelleri.
# Kullanım: ./fetch_deps.sh

ROOT="$(cd "$(dirname "$0")" && pwd)"
DEPS="$ROOT/.deps"
SHERPA_VERSION="1.10.30"
SHERPA_URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/v${SHERPA_VERSION}/sherpa-onnx-v${SHERPA_VERSION}-android.tar.bz2"
TTS_KT_URL="https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v${SHERPA_VERSION}/android/SherpaOnnxTts/app/src/main/java/com/k2fsa/sherpa/onnx/Tts.kt"

mkdir -p "$DEPS" "$ROOT/app/src/main/jniLibs" "$ROOT/app/src/main/assets"
mkdir -p "$ROOT/app/src/main/java/com/k2fsa/sherpa/onnx"

if [ ! -f "$DEPS/sherpa-android.tar.bz2" ]; then
  echo "Sherpa-ONNX v${SHERPA_VERSION} indiriliyor..."
  curl -L --fail -o "$DEPS/sherpa-android.tar.bz2" "$SHERPA_URL"
fi

if [ ! -f "$ROOT/app/src/main/jniLibs/arm64-v8a/libsherpa-onnx-jni.so" ]; then
  rm -rf "$DEPS/sherpa-android"
  mkdir -p "$DEPS/sherpa-android"
  tar xjf "$DEPS/sherpa-android.tar.bz2" -C "$DEPS/sherpa-android"
  cp -R "$DEPS/sherpa-android/jniLibs/arm64-v8a" "$ROOT/app/src/main/jniLibs/"
  cp -R "$DEPS/sherpa-android/jniLibs/x86_64" "$ROOT/app/src/main/jniLibs/"
fi

# dfki: tam paket (espeak-ng-data dahil, tek kopya kaynak).
# Diğerleri: sadece ONNX + tokens; espeak verisi dfki'den paylaşılır.
for v in dfki fahrettin fettah; do
  DIR="vits-piper-tr_TR-${v}-medium-int8"
  ONNX="tr_TR-${v}-medium.onnx"
  ARC="$DEPS/${DIR}.tar.bz2"
  URL="https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/${DIR}.tar.bz2"

  if [ ! -f "$ARC" ]; then
    echo "${DIR} indiriliyor..."
    curl -L --fail -o "$ARC" "$URL"
  fi

  if [ ! -f "$ROOT/app/src/main/assets/${DIR}/${ONNX}" ]; then
    if [ "$v" = "dfki" ]; then
      rm -rf "$ROOT/app/src/main/assets/${DIR}"
      tar xjf "$ARC" -C "$ROOT/app/src/main/assets"
    else
      rm -rf "$DEPS/${DIR}" "$ROOT/app/src/main/assets/${DIR}"
      mkdir -p "$DEPS/${DIR}" "$ROOT/app/src/main/assets/${DIR}"
      tar xjf "$ARC" -C "$DEPS/${DIR}"
      cp "$DEPS/${DIR}/${DIR}/${ONNX}" "$ROOT/app/src/main/assets/${DIR}/"
      cp "$DEPS/${DIR}/${DIR}/tokens.txt" "$ROOT/app/src/main/assets/${DIR}/"
      [ -f "$DEPS/${DIR}/${DIR}/MODEL_CARD" ] && cp "$DEPS/${DIR}/${DIR}/MODEL_CARD" "$ROOT/app/src/main/assets/${DIR}/" || true
    fi
  fi
done

if [ ! -f "$ROOT/app/src/main/java/com/k2fsa/sherpa/onnx/Tts.kt" ]; then
  curl -L --fail -o "$ROOT/app/src/main/java/com/k2fsa/sherpa/onnx/Tts.kt" "$TTS_KT_URL"
fi

echo "Hazır. Şimdi:"
echo "  JAVA_HOME=\"/Applications/Android Studio.app/Contents/jbr/Contents/Home\" \\"
echo "  \"$HOME/.gradle/wrapper/dists/gradle-8.10.2-bin\"/*/gradle-8.10.2/bin/gradle -p \"$ROOT\" :app:assembleDebug"
