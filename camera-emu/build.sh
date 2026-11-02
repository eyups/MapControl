#!/bin/bash
# Builds the HAB camera emulator shim (emulator-only, LD_PRELOAD; app is not modified).
set -e
NDK=${ANDROID_NDK_HOME:-$HOME/Library/Android/sdk/ndk/25.1.8937393}
TOOL=$NDK/toolchains/llvm/prebuilt/darwin-x86_64/bin
SYS=$NDK/toolchains/llvm/prebuilt/darwin-x86_64/sysroot/usr/lib/aarch64-linux-android
OUT=$(cd "$(dirname "$0")" && pwd)/out
mkdir -p "$OUT"
$TOOL/clang --target=aarch64-linux-android29 -shared -fPIC -O2 -std=c++17 \
    "$(dirname "$0")/hab_shim.cpp" -o "$OUT/libhabshim.so" \
    -llog -landroid "$SYS/libc++_static.a" "$SYS/libc++abi.a" \
    -Wl,--version-script="$(dirname "$0")/shim.map"
echo "built: $OUT/libhabshim.so"
