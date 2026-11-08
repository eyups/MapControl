#!/usr/bin/env bash
# LionSpotify (com.liontech.spotify) + taban Spotify (com.spotify.music 5.2.0.0)
# emülatör kurulumu. Gerekli yamalı/imzalı APK'lar car_apks/com.liontech.spotify altında:
#   LionSpotifyBase-emulator-signed.apk      : allowlist'e MapControl imzası eklendi (QR login için)
#   LionSpotify-emulator-overlay-signed.apk  : FloatViewNormal pencere tipi 2001 -> 2038
#
# Yapılanlar:
#  1) İki APK'yı kurar (imza çakışırsa önce kaldırır)
#  2) Aktivasyon kapısını açar: persist.sys.chery.sim.active=1 + sp_source/IS_SOURCE_ACTIVE=true
#  3) Floating mini-player için SYSTEM_ALERT_WINDOW appop'unu verir
#  4) SplashActivity'yi başlatır (giriş yoksa taban Spotify'ın QR ekranı açılır)
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
ADB=(adb -s "$SERIAL")
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="$ROOT/car_apks/com.liontech.spotify"
BASE_PKG=com.spotify.music
LION_PKG=com.liontech.spotify

if ! "${ADB[@]}" shell id 2>/dev/null | grep -q "uid=0"; then
  echo "adb root gerekli (prefs yazımı için)"; "${ADB[@]}" root; sleep 2
fi

install_apk() {
  local apk="$1" pkg="$2" log
  log=$(mktemp)
  if "${ADB[@]}" install -r -g "$apk" >"$log" 2>&1 && grep -q "Success" "$log"; then
    echo "kuruldu: $pkg"
    return 0
  fi
  if grep -qE "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match|INSTALL_FAILED_VERSION_DOWNGRADE" "$log"; then
    echo "imza/sürüm çakışması -> $pkg kaldırılıp yeniden kuruluyor"
    "${ADB[@]}" shell pm uninstall "$pkg" >/dev/null || true
    "${ADB[@]}" install -r -g "$apk" >/dev/null
    echo "kuruldu: $pkg"
    return 0
  fi
  cat "$log"; return 1
}

install_apk "$DIR/LionSpotifyBase-emulator-signed.apk" "$BASE_PKG"
install_apk "$DIR/LionSpotify-emulator-overlay-signed.apk" "$LION_PKG"

# Aktivasyon: SimStatusUtils persist.sys.chery.sim.active == "1" okur,
# SpUtil ise sp_source/IS_SOURCE_ACTIVE anahtarını arar.
"${ADB[@]}" shell setprop persist.sys.chery.sim.active 1
"${ADB[@]}" shell am force-stop "$LION_PKG"

APPUID=$("${ADB[@]}" shell stat -c %u "/data/data/$LION_PKG" | tr -d '\r')
APPGID=$("${ADB[@]}" shell stat -c %g "/data/data/$LION_PKG" | tr -d '\r')
tmp=$(mktemp)
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <boolean name="IS_SOURCE_ACTIVE" value="true" />\n    <boolean name="IS_SIM_ACTIVE" value="true" />\n</map>\n' > "$tmp"
"${ADB[@]}" push "$tmp" /data/local/tmp/sp_source.xml >/dev/null
# Taze kurulumda uygulama henüz hiç açılmadığı için veri dizini olmayabilir.
"${ADB[@]}" shell "mkdir -p /data/data/$LION_PKG/shared_prefs \
  && chown $APPUID:$APPGID /data/data/$LION_PKG /data/data/$LION_PKG/shared_prefs \
  && chmod 700 /data/data/$LION_PKG \
  && chmod 771 /data/data/$LION_PKG/shared_prefs \
  && cp /data/local/tmp/sp_source.xml /data/data/$LION_PKG/shared_prefs/sp_source.xml \
  && chown $APPUID:$APPGID /data/data/$LION_PKG/shared_prefs/sp_source.xml \
  && chmod 660 /data/data/$LION_PKG/shared_prefs/sp_source.xml \
  && restorecon /data/data/$LION_PKG/shared_prefs/sp_source.xml"

# FloatViewNormal (mini player) 2038 tipiyle ekleniyor; appop olmadan yine BadTokenException alınır.
"${ADB[@]}" shell appops set "$LION_PKG" SYSTEM_ALERT_WINDOW allow

"${ADB[@]}" shell am start -W -n "$LION_PKG/.ui.page.SplashActivity" >/dev/null
echo "LionSpotify başlatıldı. Giriş yoksa taban Spotify QR ekranı açılır;"
echo "QR'ı telefon Spotify uygulamasıyla tara. Giriş sonrası ana sayfa açılır."
