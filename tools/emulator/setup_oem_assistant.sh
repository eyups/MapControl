#!/usr/bin/env bash
# OEM iFlytek "Merhaba Chery" asistani icin emulator kurulumu.
# Onkosul: AVD MapControl_Tiggo8_156 acik, root adb, resvr (2.2 GB) kurulu.
#   sh tools/emulator/setup_full_env.sh --serial emulator-5554   # ilk kurulum
#   sh tools/emulator/setup_oem_assistant.sh                     # bu betik
set -euo pipefail
SERIAL="${SERIAL:-emulator-5554}"
ADB="${ADB_BIN:-adb}"
PKG=com.iflytek.cutefly.speechclient.hmi
DIR="$(cd "$(dirname "$0")/../.." && pwd)"
APK="$DIR/car_info/iflytek_emulator_20261101/iflytek_oem_assistant.apk"
BRIDGE="$DIR/oem-assistant-bridge/build/outputs/apk/debug/oem-assistant-bridge-debug.apk"
RES="/data/user/0/$PKG/files/vr/iflytek/resvr"

adb_() { "$ADB" -s "$SERIAL" "$@"; }
sh_() { adb_ shell "$@"; }

adb_ root >/dev/null 2>&1 || true

echo "[1/6] APK'lar"
adb_ install -r -d "$APK" | tail -1
if [ -f "$BRIDGE" ]; then adb_ install -r "$BRIDGE" | tail -1; else
  echo "[!] Kopru APK yok: $BRIDGE (gradlew :oem-assistant-bridge:assembleDebug)"
fi

echo "[2/6] Izınler"
sh_ appops set "$PKG" SYSTEM_ALERT_WINDOW allow || true
sh_ pm grant "$PKG" android.permission.RECORD_AUDIO 2>/dev/null || true

echo "[3/6] Dil (tr)"
sh_ am force-stop "$PKG" || true
APPUID=$(sh_ stat -c '%u:%g' "/data/user/0/$PKG" | tr -d '\r')
sh_ "mkdir -p /data/user/0/$PKG/shared_prefs && cat > /data/user/0/$PKG/shared_prefs/language.xml <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <string name=\"lan\">tr</string>
</map>
XML
chown $APPUID /data/user/0/$PKG/shared_prefs/language.xml; chmod 660 /data/user/0/$PKG/shared_prefs/language.xml
cat > /data/user/0/$PKG/shared_prefs/com.iflytek.autofly.FLYSETTING.xml <<XML
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
    <int name=\"com.iflytek.autofly.IFLY_TTS_SPEAKER\" value=\"69640\" />
    <string name=\"com.iflytek.autofly.IFLY_LANGUAGE\">tr</string>
</map>
XML
chown $APPUID /data/user/0/$PKG/shared_prefs/com.iflytek.autofly.FLYSETTING.xml; chmod 660 /data/user/0/$PKG/shared_prefs/com.iflytek.autofly.FLYSETTING.xml
settings put global com.iflytek.autofly.TTS_ROLE 69640"
sh_ setprop persist.sys.locale tr-TR || true

echo "[4/6] MVW kaynak dizinleri"
sh_ "P=$RES
# T19C ve MVWRes yoksa T28'den turet
for d in MVWRes MVWRes-DeSayT19C; do
  [ -e \$P/\$d ] || cp -a \$P/MVWRes-DeSayT28 \$P/\$d
  printf '1031_1902_cst2382' > \$P/\$d/res_version
  mkdir -p \$P/Active/\$d
  [ -e \$P/Active/\$d/0 ] || cp -a \$P/\$d/0 \$P/Active/\$d/0
  printf '1031_1902_cst2382' > \$P/Active/\$d/res_version
  chown -R $APPUID \$P/\$d \$P/Active/\$d
  restorecon -R \$P/\$d \$P/Active/\$d >/dev/null 2>&1 || true
done
# recordType=4 (8x kayit yolu), tek giris
sed -i 's/\"recordType\": \"[0-9]*\"/\"recordType\": \"4\"/' \$P/business.ini || true
for f in \$P/MVWRes/0/ResTurkish/issmvw.cfg \$P/MVWRes-DeSayT19C/0/ResTurkish/issmvw.cfg; do
  [ -f \$f ] && sed -i 's/\"MulIvwInputNum\": [0-9]*/\"MulIvwInputNum\": 1/' \$f || true
done
# NLU dil kimligi (uyariyi kaldirir; calismasa da NLU tr uretiyor)
for f in \$P/SRRes/ResTurkish/isssr.cfg \$P/Active/SRRes/ResTurkish/isssr.cfg; do
  [ -f \$f ] && sed -i 's/\"wpostproc_param_languagetype\": \"[0-9]*\"/\"wpostproc_param_languagetype\": \"25\"/; s/\"wesr_param_language_type\": \"[0-9]*\"/\"wesr_param_language_type\": \"25\"/' \$f || true
done"

echo "[5/6] Baslat"
sh_ am start -n "$PKG/.CommonActivity" >/dev/null 2>&1 || true
for i in $(seq 1 30); do
  if adb_ logcat -d -t 200 2>/dev/null | grep -q "main mvw create ret: 0"; then
    echo "[OK] MVW hazir: Merhaba Chery"; break
  fi
  sleep 1
done

echo "[6/6] Kullanim"
echo "  sh tools/emulator/oem_assistant.sh wake"
echo "  sh tools/emulator/oem_assistant.sh say \"cam tavanı aç\""
echo "  sh tools/emulator/oem_assistant.sh voice 6"
