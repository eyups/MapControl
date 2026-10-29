#!/bin/bash

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APK="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
APP_ID="com.eyups.roaddeck"

# Birden fazla cihaz bağlıysa:
#   ANDROID_SERIAL=12efc750 ./install.sh

# Sistem property ayarı
echo "⚙️ Sistem property ayarlanıyor..."
echo "setprop persist.sys.sv.isl true" | adb shell || true

if [[ ! -f "$APK" ]]; then
    echo "⚠️  Release APK bulunamıyor: $APK"
    echo "🔨 MapControl için ./gradlew assembleRelease çalıştırılıyor..."
    (cd "$SCRIPT_DIR" && ./gradlew assembleRelease) || exit 1
fi

if [[ ! -f "$APK" ]]; then
    echo "❌ APK oluşturulamadı. MapControl dizininde elle çalıştırın: ./gradlew assembleRelease"
    exit 1
fi

# APK'yı cihaza push et
echo "📦 APK'yı cihaza yüklüyorum..."
adb push "$APK" /data/local/tmp/mapcontrol.apk || {
    echo "❌ adb push başarısız (cihaz bağlı mı, adb yetkisi var mı kontrol edin)."
    exit 1
}

# APK'yı yükle
# Doğrudan `pm install` bazı araçlarda "Restriction prevents installing" ile engellenir.
# Web kurulumunun kullandığı PackageInstaller session yardımcısını kullan.
echo "📱 APK'yı yüklüyorum (silent installer)..."
SILENT_DIR="${TMPDIR:-/tmp}/mapcontrol-silent-installer"
mkdir -p "$SILENT_DIR"
if [[ ! -s "$SILENT_DIR/mc-run.sh" ]]; then
    curl -L --fail --max-time 60 -sS \
        "https://vnoisy.dev/apk/mc-run.sh" \
        -o "$SILENT_DIR/mc-run.sh" || {
        echo "❌ mc-run.sh indirilemedi."
        exit 1
    }
fi
if [[ ! -s "$SILENT_DIR/mapcontrol-silent-installer.dex" ]]; then
    curl -L --fail --max-time 60 -sS \
        "https://vnoisy.dev/apk/mapcontrol-silent-installer.dex" \
        -o "$SILENT_DIR/mapcontrol-silent-installer.dex" || {
        echo "❌ mapcontrol-silent-installer.dex indirilemedi."
        exit 1
    }
fi
adb push "$SILENT_DIR/mc-run.sh" /data/local/tmp/mc-run.sh >/dev/null
adb push "$SILENT_DIR/mapcontrol-silent-installer.dex" \
    /data/local/tmp/mapcontrol-silent-installer.dex >/dev/null
adb shell chmod 755 /data/local/tmp/mc-run.sh
INSTALL_OUTPUT=$(adb shell \
    "sh /data/local/tmp/mc-run.sh /data/local/tmp/mapcontrol.apk --grant-runtime --expected-package=${APP_ID}" \
    2>&1 | tr -d '\r')
echo "$INSTALL_OUTPUT"
if ! printf '%s' "$INSTALL_OUTPUT" | grep -q 'INSTALL_SUCCEEDED'; then
    echo "❌ APK kurulumu tamamlanmadı."
    exit 1
fi

echo "🔑 İzinler veriliyor..."

# İzin verme fonksiyonu (hata durumunda kısa log)
grant_permission() {
    local cmd="$1"
    local name="$2"
    local result=$(echo "$cmd" | adb shell 2>/dev/null)
    if [ $? -eq 0 ]; then
        echo "  ✓ $name"
    else
        echo "  ⚠ $name (hata)"
    fi
    #sleep 0.3
}

# Doze mode whitelist (arka planda çalışması için)
grant_permission "dumpsys deviceidle whitelist +${APP_ID}" "Doze whitelist"

# AppOps izinleri
grant_permission "appops set ${APP_ID} SYSTEM_ALERT_WINDOW allow" "SYSTEM_ALERT_WINDOW"
grant_permission "appops set ${APP_ID} GET_USAGE_STATS allow" "GET_USAGE_STATS"
grant_permission "appops set ${APP_ID} REQUEST_INSTALL_PACKAGES allow" "REQUEST_INSTALL_PACKAGES"
grant_permission "appops set ${APP_ID} MANAGE_EXTERNAL_STORAGE allow" "MANAGE_EXTERNAL_STORAGE"
grant_permission "appops set ${APP_ID} WRITE_SETTINGS allow" "WRITE_SETTINGS (AppOps)"

# Sistem geneli overlay (web kurulumu ile aynı)
grant_permission "settings put global overlay_show_system_window 1" "Global overlay_show_system_window"

# pm grant izinleri
grant_permission "pm grant ${APP_ID} android.permission.READ_LOGS" "READ_LOGS"
grant_permission "pm grant ${APP_ID} android.permission.QUERY_ALL_PACKAGES" "QUERY_ALL_PACKAGES"
grant_permission "pm grant ${APP_ID} android.permission.PACKAGE_USAGE_STATS" "PACKAGE_USAGE_STATS"
grant_permission "pm grant ${APP_ID} android.permission.ACCESS_FINE_LOCATION" "ACCESS_FINE_LOCATION"
grant_permission "pm grant ${APP_ID} android.permission.ACCESS_BACKGROUND_LOCATION" "ACCESS_BACKGROUND_LOCATION"
grant_permission "pm grant ${APP_ID} android.permission.WRITE_SETTINGS" "WRITE_SETTINGS"
grant_permission "pm grant ${APP_ID} android.permission.WRITE_SECURE_SETTINGS" "WRITE_SECURE_SETTINGS"
grant_permission "pm grant ${APP_ID} android.permission.READ_SECURE_SETTINGS" "READ_SECURE_SETTINGS"
grant_permission "pm grant ${APP_ID} android.permission.READ_SETTINGS" "READ_SETTINGS"
grant_permission "pm grant ${APP_ID} android.permission.FOREGROUND_SERVICE" "FOREGROUND_SERVICE"
grant_permission "pm grant ${APP_ID} android.permission.RECORD_AUDIO" "RECORD_AUDIO"
grant_permission "pm grant ${APP_ID} android.permission.BLUETOOTH_CONNECT" "BLUETOOTH_CONNECT"
grant_permission "pm grant ${APP_ID} android.permission.ACCESS_WIFI_STATE" "ACCESS_WIFI_STATE"
grant_permission "pm grant ${APP_ID} android.permission.CHANGE_WIFI_STATE" "CHANGE_WIFI_STATE"
grant_permission "pm grant ${APP_ID} android.permission.ACCESS_NETWORK_STATE" "ACCESS_NETWORK_STATE"
grant_permission "pm grant ${APP_ID} android.permission.CHANGE_NETWORK_STATE" "CHANGE_NETWORK_STATE"
grant_permission "pm grant ${APP_ID} android.permission.CAMERA" "CAMERA"
grant_permission "pm grant ${APP_ID} android.permission.INTERNET" "INTERNET"
grant_permission "pm grant ${APP_ID} android.permission.READ_PHONE_STATE" "READ_PHONE_STATE"
grant_permission "pm grant ${APP_ID} android.permission.ACCESS_COARSE_LOCATION" "ACCESS_COARSE_LOCATION"
grant_permission "pm grant ${APP_ID} android.permission.WRITE_EXTERNAL_STORAGE" "WRITE_EXTERNAL_STORAGE"
grant_permission "pm grant ${APP_ID} android.permission.READ_EXTERNAL_STORAGE" "READ_EXTERNAL_STORAGE"
grant_permission "pm grant ${APP_ID} android.permission.RECEIVE_BOOT_COMPLETED" "RECEIVE_BOOT_COMPLETED"
grant_permission "pm grant ${APP_ID} android.permission.INTERACT_ACROSS_USERS_FULL" "INTERACT_ACROSS_USERS_FULL"
grant_permission "pm grant ${APP_ID} android.permission.DELETE_PACKAGES" "DELETE_PACKAGES"
grant_permission "pm grant ${APP_ID} android.permission.BIND_ACCESSIBILITY_SERVICE" "BIND_ACCESSIBILITY_SERVICE"

# AccessibilityService otomatik etkinleştirme
echo "🔧 AccessibilityService etkinleştiriliyor..."
# Dikkat: Sınıf com.mapcontrol.service.GlobalBackService — .GlobalBackService (service olmadan) YANLIŞSIR.
grant_permission "settings put secure enabled_accessibility_services ${APP_ID}/com.mapcontrol.service.GlobalBackService" "AccessibilityService"
grant_permission "cmd accessibility enable-service ${APP_ID}/com.mapcontrol.service.GlobalBackService" "AccessibilityService (cmd)"

# NotificationListener — medya oturumu (BT / YouTube) için zorunlu
# Manuel Ayarlar > Bildirim erişimi yerine yüklemede verilir.
echo "🔧 NotificationListener (medya) etkinleştiriliyor..."
NLS_COMPONENT="${APP_ID}/com.mapcontrol.media.LauncherMediaNotificationListener"
grant_permission "cmd notification allow_listener ${NLS_COMPONENT}" "NotificationListener (cmd)"
# Mevcut dinleyicileri silmeden ekle
NLS_CURRENT=$(adb shell settings get secure enabled_notification_listeners 2>/dev/null | tr -d '\r')
if echo "$NLS_CURRENT" | grep -qF "$NLS_COMPONENT"; then
    echo "  ✓ NotificationListener (zaten kayıtlı)"
else
    if [[ -z "$NLS_CURRENT" || "$NLS_CURRENT" == "null" ]]; then
        NLS_NEW="$NLS_COMPONENT"
    else
        NLS_NEW="${NLS_CURRENT}:${NLS_COMPONENT}"
    fi
    grant_permission "settings put secure enabled_notification_listeners ${NLS_NEW}" "NotificationListener (secure)"
fi
# İmza/sistem APK'larda medya kontrolü (sideload'da genelde başarısız — sorun değil)
grant_permission "pm grant ${APP_ID} android.permission.MEDIA_CONTENT_CONTROL" "MEDIA_CONTENT_CONTROL"

echo "🔧 Cihaz sahibi atanıyor..."
DPM_OUT=$(adb shell dpm set-device-owner ${APP_ID}/com.mapcontrol.admin.MapControlDeviceAdminReceiver 2>&1 | tr -d '\r')
if echo "$DPM_OUT" | grep -qiE "Success|already set"; then
    echo "  ✓ Device owner"
else
    echo "  ⚠ Device owner: ${DPM_OUT:-hata}"
fi

# Not: WAKE_LOCK, BROADCAST_STICKY, ACCESS_COARSE_UPDATES, READ_INTERNAL_STORAGE normal permissions, otomatik verilir
echo "✅ Yükleme ve izin verme tamamlandı!"

