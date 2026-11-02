#!/usr/bin/env bash
# Emülatörün host (qemu) ve guest (Android) kaynak tüketimini gösterir.
#   ./tools/emulator/usage.sh [-s emulator-5554] [-w saniye]
set -u
SERIAL=${ANDROID_SERIAL:-emulator-5554}
WATCH=0
while [ $# -gt 0 ]; do
  case "$1" in
    -s|--serial) SERIAL="$2"; shift 2 ;;
    -w|--watch) WATCH="${2:-2}"; shift 2 ;;
    -h|--help) sed -n '2,4p' "$0"; exit 0 ;;
    *) echo "bilinmeyen argüman: $1" >&2; exit 2 ;;
  esac
done
ADB=${ADB:-adb}
command -v "$ADB" >/dev/null 2>&1 || ADB="$HOME/Library/Android/sdk/platform-tools/adb"

sample() {
  echo "=== $(date '+%H:%M:%S')  $SERIAL ==="
  local avd pid line
  avd=$("$ADB" -s "$SERIAL" emu avd name 2>/dev/null | head -1 | tr -d '\r')
  pid=""
  [ -n "$avd" ] && pid=$(pgrep -f "qemu-system.*-avd $avd" | head -1)
  if [ -n "$pid" ]; then
    echo "-- host: $avd (qemu pid $pid)"
    cpu=$(top -l 2 -s 1 -pid "$pid" -stats pid,cpu 2>/dev/null | grep -E "^ *$pid +[0-9]" | tail -1 | awk '{print $2}')
    rss=$(ps -o rss= -p "$pid" | tr -d ' ')
    thr=$(ps -M -p "$pid" 2>/dev/null | tail -n +2 | wc -l | tr -d ' ')
    awk -v c="$cpu" -v r="$rss" -v t="$thr" 'BEGIN{printf "   CPU %s%%   RSS %.0f MB   threads %s\n",c,r/1024,t}'
    ps -o etime= -p "$pid" | awk '{print "   uptime "$1}'
  else
    echo "-- host: qemu süreci bulunamadı (avd=${avd:-?})"
  fi
  echo "-- guest: load / RAM / disk"
  "$ADB" -s "$SERIAL" shell 'cat /proc/loadavg | sed "s/^/   load: /"; grep -E "MemTotal|MemFree|MemAvailable|SwapTotal|SwapFree" /proc/meminfo | sed "s/^/   /"; df -h /data 2>/dev/null | tail -1 | sed "s/^/   /"' 2>/dev/null
  echo "-- guest: en yüksek 8 süreç"
  "$ADB" -s "$SERIAL" shell 'top -b -n 1 -m 8' 2>/dev/null | sed -n '1,14p' | sed 's/^/   /'
  echo "-- app: com.eyups.roaddeck"
  "$ADB" -s "$SERIAL" shell 'dumpsys meminfo com.eyups.roaddeck 2>/dev/null | grep -E "TOTAL PSS|TOTAL RSS|Java Heap|Native Heap|Graphics"' 2>/dev/null | sed 's/^/   /'
  echo "-- app threads (cam-hub*=HAB şimi, cam-gl=GL, RenderThread=UI)"
  "$ADB" -s "$SERIAL" shell 'pid=$(pidof com.eyups.roaddeck); [ -n "$pid" ] && top -H -b -n 1 -p $pid -m 10' 2>/dev/null | sed -n '1,16p' | sed 's/^/   /'
  echo "-- cpuinfo (ilk 8 satır)"
  "$ADB" -s "$SERIAL" shell 'dumpsys cpuinfo 2>/dev/null | head -8' 2>/dev/null | sed 's/^/   /'
}

if [ "$WATCH" -gt 0 ]; then
  while true; do sample; sleep "$WATCH"; done
else
  sample
fi
