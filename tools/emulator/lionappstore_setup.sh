#!/usr/bin/env bash
# LionAppStore (com.liontech.appstore) emülatör kurulumu.
#
# APK: car_apks/com.liontech.appstore/LionAppStore-emulator-signed.apk
#   - AOSP platform testkey ile imzalı (emülatör test-keys olduğu için
#     android.uid.system shared user'ına katılabiliyor)
#   - com.chery.platform + VDBus + android.car sınıfları classes4-7 olarak eklendi
#   - GlobalUtil.getPkiState() -> 1 yaması (PKI beklemesini atlar)
#
# Kullanım:
#   tools/emulator/lionappstore_setup.sh                 # kur + başlat
#   tools/emulator/lionappstore_setup.sh --mock          # mock backend + proxy
#   tools/emulator/lionappstore_setup.sh --stop          # proxy'yi durdur, proxy ayarını temizle
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
MOCK=0
STOP=0
while [ $# -gt 0 ]; do
  case "$1" in
    --mock) MOCK=1; shift ;;
    --stop) STOP=1; shift ;;
    --serial) SERIAL="$2"; shift 2 ;;
    *) echo "bilinmeyen argüman: $1"; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ADB=(adb -s "$SERIAL")
PKG=com.liontech.appstore
APK="$ROOT/car_apks/com.liontech.appstore/LionAppStore-emulator-signed.apk"
PIDFILE="${TMPDIR:-/tmp}/mapcontrol-appstore-proxy.pid"

if [ "$STOP" = 1 ]; then
  if [ -f "$PIDFILE" ]; then
    kill "$(cat "$PIDFILE")" 2>/dev/null || true
    rm -f "$PIDFILE"
  fi
  "${ADB[@]}" shell settings put global http_proxy :0 >/dev/null 2>&1 || true
  echo "proxy durduruldu, http_proxy temizlendi"
  exit 0
fi

if [ ! -f "$APK" ]; then
  echo "APK yok: $APK"; exit 1
fi
if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
  echo "emülatör bağlı değil: $SERIAL"; exit 1
fi

install_apk() {
  local log
  log=$(mktemp)
  if "${ADB[@]}" install -r --no-incremental "$APK" >"$log" 2>&1 && grep -q "Success" "$log"; then
    echo "kuruldu: $PKG"
    return 0
  fi
  if grep -qE "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match|INSTALL_FAILED_SHARED_USER_INCOMPATIBLE" "$log"; then
    echo "imza/sharedUser çakışması -> $PKG kaldırılıp yeniden kuruluyor"
    "${ADB[@]}" uninstall "$PKG" >/dev/null 2>&1 || true
    "${ADB[@]}" install --no-incremental "$APK" >/dev/null
    echo "kuruldu: $PKG"
    return 0
  fi
  cat "$log"; return 1
}

install_apk
"${ADB[@]}" shell am force-stop "$PKG"

if [ "$MOCK" = 1 ]; then
  "$ROOT/tools/emulator/appstore_proxy_certs.sh" --serial "$SERIAL"
  if [ -f "$PIDFILE" ] && kill -0 "$(cat "$PIDFILE")" 2>/dev/null; then
    echo "proxy zaten çalışıyor (pid $(cat "$PIDFILE"))"
  else
    nohup python3 "$ROOT/tools/emulator/appstore_mock_proxy.py" --port 8443 \
      >"${TMPDIR:-/tmp}/mapcontrol-appstore-proxy.log" 2>&1 &
    echo $! > "$PIDFILE"
    echo "mock proxy başladı (pid $(cat "$PIDFILE")), log: ${TMPDIR:-/tmp}/mapcontrol-appstore-proxy.log"
  fi
  "${ADB[@]}" shell settings put global http_proxy 10.0.2.2:8443
  echo "http_proxy=10.0.2.2:8443"
else
  echo "not: gerçek backend PKI istemci sertifikası istiyor; içerik için --mock kullan."
fi

"${ADB[@]}" shell am start -W -n "$PKG/.launcher.SplashActivity" >/dev/null
echo "LionAppStore başlatıldı ($SERIAL)"
