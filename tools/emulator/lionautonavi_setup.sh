#!/usr/bin/env bash
# LionAutoNavi (com.liontech.autonavi) emülatör kurulumu.
#
# APK: car_apks/com.liontech.autonavi/LionAutoNavi-emulator-signed.apk
#   - AOSP platform testkey ile imzalı (android.uid.system shared user'ı için)
#   - com.chery.platform + VDBus + android.car sınıfları classes4-7 olarak eklendi
#   - Ham APK: car_info/rootdump/.../product/app/LionAutoNavi/LionAutoNavi.apk
#
# Kullanım:
#   tools/emulator/lionautonavi_setup.sh [--serial emulator-5554] [--dev]
#
# Akış:
#   1) Splash → gizlilik sözleşmesi: kutuyu işaretle + "Devam et"
#   2) InitializeSuccessActivity: "Next"
#   3) Aktivasyon/SIM kapısı emülatörde geçilmezse:
#      --dev ile NavigationActivity doğrudan açılır
#      (IS_FROM_DEVELOPER=true); alternatif: "Initialization completed"
#      alanına 5 kez dokun (ClickProxy.exam).
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
DEV=0
while [ $# -gt 0 ]; do
  case "$1" in
    --serial) SERIAL="$2"; shift 2 ;;
    --dev) DEV=1; shift ;;
    *) echo "bilinmeyen argüman: $1"; exit 2 ;;
  esac
done

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ADB=(adb -s "$SERIAL")
PKG=com.liontech.autonavi
ACT="$PKG/.map.ui.page.splash.SplashActivity"
APK="$ROOT/car_apks/com.liontech.autonavi/LionAutoNavi-emulator-signed.apk"

if [ ! -f "$APK" ]; then
  echo "APK yok: $APK"; exit 1
fi
if ! "${ADB[@]}" get-state >/dev/null 2>&1; then
  echo "emülatör bağlı değil: $SERIAL"; exit 1
fi

log=$(mktemp)
if "${ADB[@]}" install -r --no-incremental "$APK" >"$log" 2>&1 && grep -q "Success" "$log"; then
  echo "kuruldu: $PKG"
else
  if grep -qE "INSTALL_FAILED_UPDATE_INCOMPATIBLE|signatures do not match|INSTALL_FAILED_SHARED_USER_INCOMPATIBLE" "$log"; then
    echo "imza/sharedUser çakışması -> $PKG kaldırılıp yeniden kuruluyor"
    "${ADB[@]}" uninstall "$PKG" >/dev/null 2>&1 || true
    "${ADB[@]}" install --no-incremental "$APK" >/dev/null
    echo "kuruldu: $PKG"
  else
    cat "$log"; exit 1
  fi
fi

"${ADB[@]}" shell am force-stop "$PKG"
if [ "$DEV" = 1 ]; then
  "${ADB[@]}" shell am start -W -n "$PKG/.map.ui.page.navi.NavigationActivity" --ez IS_FROM_DEVELOPER true >/dev/null
  echo "LionAutoNavi NavigationActivity (developer modu) başlatıldı."
else
  "${ADB[@]}" shell am start -W -n "$ACT" >/dev/null
  echo "LionAutoNavi başlatıldı."
  echo "Gizlilik ekranında kutuyu işaretleyip 'Devam et' → InitializeSuccessActivity'de 'Next'."
  echo "Aktivasyon takılırsa: 'Initialization completed' alanına 5 kez dokun veya --dev kullan."
fi
