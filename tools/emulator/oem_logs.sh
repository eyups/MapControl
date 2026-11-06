#!/usr/bin/env bash
# OEM iFlytek asistan loglarini logcat'ten izler.
# Kullanim:
#   sh tools/emulator/oem_logs.sh wake          # canli: uyandirma/MVW
#   sh tools/emulator/oem_logs.sh sr            # canli: SR/NLU
#   sh tools/emulator/oem_logs.sh tts           # canli: TTS
#   sh tools/emulator/oem_logs.sh cmd           # canli: komut koprusu
#   sh tools/emulator/oem_logs.sh err           # canli: crash/ANR
#   sh tools/emulator/oem_logs.sh all
#   sh tools/emulator/oem_logs.sh wake -n 200   # son 200 satir, cikar
#   sh tools/emulator/oem_logs.sh sr -c         # once temizle
set -uo pipefail
SERIAL="${SERIAL:-emulator-5554}"
ADB="${ADB_BIN:-adb}"
MODE="${1:-all}"; shift || true
SNAP=""; CLEAR=0; PID=""; FILE=""
while [ $# -gt 0 ]; do
  case "$1" in
    -n|--tail) SNAP="$2"; shift 2 ;;
    -c|--clear) CLEAR=1; shift ;;
    -p|--pid) PID="$(adb -s "$SERIAL" shell pidof com.iflytek.cutefly.speechclient.hmi | tr -d '\r')"; shift ;;
    -f|--file) FILE="$2"; shift 2 ;;
    *) shift ;;
  esac
done
case "$MODE" in
  wake) RE='MulMvwManager|EMvwSession|issmvw|MVW_RLT|MVW_MSG|40003|ParseIvw|Merhaba Chery|wake up result|Start ISS_MVW' ;;
  sr)   RE='SrSolution|isssr|ResultsAnalyser|SemanticUtils|BusinessProcessor|InteractManager|Recognition|SpeechRecordingState|normal_text|nluResult' ;;
  tts)  RE='TtsManager|TtsService|isstts|TtsPlayer|AudioWrite|startspeak|setTTSRole|role: [0-9]+' ;;
  cmd)  RE='PlatformMessageAgent|OemCmd|ControlReceiver|CarControlOut|directToApp|LauncherManager|fakevdbus|handMessage' ;;
  err)  RE='AndroidRuntime|FATAL|ANR|Exception|NoSuchMethod|NoClassDef' ;;
  all)  RE='.' ;;
  *) echo "mod: wake|sr|tts|cmd|err|all"; exit 2 ;;
esac
[ "$CLEAR" = 1 ] && adb -s "$SERIAL" logcat -c
if [ -n "$SNAP" ]; then
  CMD=(adb -s "$SERIAL" logcat -d -v time -t "$SNAP")
else
  CMD=(adb -s "$SERIAL" logcat -v time)
fi
if [ -n "$PID" ]; then
  # --pid ile yalniz OEM sureci; grep yine uygulanir
  if [ -n "$FILE" ]; then "${CMD[@]}" --pid="$PID" | grep -E "$RE" | tee "$FILE"; else "${CMD[@]}" --pid="$PID" | grep -E "$RE"; fi
else
  if [ -n "$FILE" ]; then "${CMD[@]}" | grep -E "$RE" | tee "$FILE"; else "${CMD[@]}" | grep -E "$RE"; fi
fi
