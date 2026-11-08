#!/usr/bin/env bash
# Emülatörde kurulu Spotify'ı açar.
#
# Kurulu APK (com.spotify.music 5.6.0.17133) Android Automotive (AAOS) build'i:
# manifestte <uses-feature android.hardware.type.automotive> var, MAIN/LAUNCHER
# aktivitesi yok. Bu yüzden Launcher3'te simge çıkmaz; uygulama yalnızca
# bileşen adı verilerek başlatılabilir (player UI'ı car launcher'ın
# MediaBrowserService istemcisi çizer, uygulamada ayrı bir player activity yok).
#
# Kullanım: tools/emulator/spotify.sh [login|pin|qr|password|settings|stop|status]
set -euo pipefail

SERIAL="${ANDROID_SERIAL:-emulator-5554}"
PKG="com.spotify.music"
ADB=(adb -s "$SERIAL")
BASE="$PKG/com.spotify.automotive"

case "${1:-login}" in
  login|password)
    # Giriş ekranı: QR + "PIN kodu ile oturum aç" + "Parola ile oturum aç"
    COMP="$BASE.login.view.PasswordLoginActivity" ;;
  pin)
    COMP="$BASE.loginv2.view.PinLoginActivityV2" ;;
  qr)
    COMP="$BASE.loginv2.view.QrCodeLoginActivityV2" ;;
  settings)
    COMP="$BASE.settingspage.SettingsPageActivity" ;;
  stop)
    "${ADB[@]}" shell am force-stop "$PKG"
    echo "durduruldu: $PKG"
    exit 0 ;;
  status)
    "${ADB[@]}" shell dumpsys activity activities \
      | grep -E "mResumedActivity.*$PKG" || echo "spotify ön planda değil"
    exit 0 ;;
  *)
    echo "kullanım: $0 [login|pin|qr|password|settings|stop|status]" >&2
    exit 1 ;;
esac

# Ana ekran simgesi olmadığı için doğrudan bileşen başlatıyoruz. Shell kullanıcısı
# userdebug imajlarda START_ANY_ACTIVITY yetkisine sahip; export edilmemiş
# aktiviteler bu yüzden açılabiliyor.
"${ADB[@]}" shell am start -W -n "$COMP"
