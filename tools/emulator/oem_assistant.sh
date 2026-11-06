#!/usr/bin/env bash
# OEM iFlytek "Merhaba Chery" asistani - emulator kontrol scripti.
# Kullanim:
#   sh tools/emulator/oem_assistant.sh start
#   sh tools/emulator/oem_assistant.sh wake
#   sh tools/emulator/oem_assistant.sh say "cam tavanı aç"
#   sh tools/emulator/oem_assistant.sh voice 5
set -uo pipefail
SERIAL="${SERIAL:-emulator-5554}"
ADB="${ADB_BIN:-adb}"
PKG=com.iflytek.cutefly.speechclient.hmi
SVC=$PKG/com.iflytek.auto.speechclient.sdk.SpeechClientService
BRIDGE=com.eyups.oemcmd
MODEL="${MODEL_DIR:-/tmp/vosk-model-small-tr}"

adb_() { "$ADB" -s "$SERIAL" "$@"; }
shell() { adb_ shell "$@"; }

ensure_model() {
  if [ -d "$MODEL" ]; then return 0; fi
  echo "[i] Vosk TR modeli cihazdan aliniyor..."
  mkdir -p "$MODEL"
  adb_ exec-out run-as com.eyups.roaddeck tar -c -C files/assistant/models vosk-model-small-tr 2>/dev/null \
    | tar -x -C "$(dirname "$MODEL")" 2>/dev/null
  [ -d "$MODEL" ] || echo "[!] Model alinamadi; voice modu calismaz."
}

cmd_start() {
  shell am force-stop "$PKG" >/dev/null 2>&1
  sleep 1
  shell am start -n "$PKG/.CommonActivity" >/dev/null 2>&1
  echo "[i] OEM asistan baslatildi; MVW init bekleniyor..."
  for i in $(seq 1 30); do
    if adb_ logcat -d -t 200 | grep -q "main mvw create ret: 0"; then
      echo "[OK] MVW hazir (Merhaba Chery)."; return 0
    fi
    sleep 1
  done
  echo "[!] MVW init logu gorulmedi."; return 1
}

cmd_wake() {
  shell "am startservice -n $SVC --es startvr true --es position all" >/dev/null 2>&1
  echo "[OK] startvr gonderildi (OEM VR UI + SR)."
}

# NLU'yu testSr ile calistirir, logdan semantic'i alir ve kopruye yollar.
cmd_say() {
  local text="$1"
  shell logcat -c
  shell "am startservice -n $SVC --es testSr true --es text '$text' --es type 1" >/dev/null 2>&1
  sleep 3
  local out
  out=$(adb_ logcat -d | grep -m1 "nluResult:" || true)
  if [ -z "$out" ]; then echo "[!] NLU sonucu yok."; return 1; fi
  local json
  json=$(printf '%s' "$out" | python3 "$(dirname "$0")/oem_nlu_parse.py" 2>/dev/null)
  if [ -z "$json" ]; then echo "[!] Semantic ayristirilamadi."; return 1; fi
  echo "[i] semantic: $json"
  # JSON'u dosyaya yazip broadcast extrasina koy (tirnak sorunlarini onler).
  printf '%s' "$json" > /tmp/oem_cmd.json
  adb_ push /tmp/oem_cmd.json /data/local/tmp/oem_cmd.json >/dev/null
  local val
  val=$(adb_ shell "cat /data/local/tmp/oem_cmd.json")
  shell "am broadcast -a com.iflytek.autofly.handMessage -f 32 -p $BRIDGE --es value '$val'" >/dev/null 2>&1
  sleep 1
  adb_ logcat -d | grep -E "OemCmd" | tail -5
}

cmd_voice() {
  local secs="${1:-6}"
  ensure_model
  [ -d "$MODEL" ] || return 1
  echo "[i] Emulator mikrofonundan ${secs} sn kayit..."
  python3 "$(dirname "$0")/voice_mic.py" --enable-only >/dev/null 2>&1 || true
  adb_ shell am force-stop "$PKG" >/dev/null 2>&1
  sleep 1
  adb_ shell "tinycap /data/local/tmp/oem_wake.wav -D 0 -d 0 -c 2 -r 48000 -b 16 -T $secs" >/dev/null 2>&1
  adb_ pull /data/local/tmp/oem_wake.wav /tmp/oem_wake_raw.wav >/dev/null 2>&1
  ffmpeg -loglevel error -i /tmp/oem_wake_raw.wav -ar 16000 -ac 1 /tmp/oem_wake.wav -y
  local text
  text=$(python3 - "$MODEL" <<'PY'
import sys, json, wave
from vosk import Model, KaldiRecognizer, SetLogLevel
SetLogLevel(-1)
w = wave.open('/tmp/oem_wake.wav', 'rb')
pcm = w.readframes(w.getnframes())
m = Model(sys.argv[1])
g = '["merhaba cinliler", "merhaba chery", "[unk]"]'
r = KaldiRecognizer(m, 16000, g)
r.AcceptWaveform(pcm)
print(json.loads(r.FinalResult()).get('text', ''))
PY
)
  echo "[i] duyulan: $text"
  case "$text" in
    *"merhaba cinliler"*|*"merhaba chery"*|*"merhaba cheri"*)
      cmd_start >/dev/null 2>&1
      cmd_wake ;;
    *) echo "[!] Uyandirma kelimesi yok (Merhaba Chery)."; return 1 ;;
  esac
  echo "[i] Komut icin 5 sn kayit..."
  sleep 1
  adb_ shell "tinycap /data/local/tmp/oem_cmd.wav -D 0 -d 0 -c 2 -r 48000 -b 16 -T 5" >/dev/null 2>&1
  adb_ pull /data/local/tmp/oem_cmd.wav /tmp/oem_cmd_raw.wav >/dev/null 2>&1
  ffmpeg -loglevel error -i /tmp/oem_cmd_raw.wav -ar 16000 -ac 1 /tmp/oem_cmd.wav -y
  local cmd
  cmd=$(python3 - "$MODEL" <<'PY'
import sys, json, wave
from vosk import Model, KaldiRecognizer, SetLogLevel
SetLogLevel(-1)
w = wave.open('/tmp/oem_cmd.wav', 'rb')
pcm = w.readframes(w.getnframes())
m = Model(sys.argv[1])
g = '["cam tavanı aç", "cam tavanı kapat", "güneşliği aç", "güneşliği kapat", "klima aç", "klima kapat", "müzik aç", "müzik kapat", "sesi aç", "sesi kapat", "[unk]"]'
r = KaldiRecognizer(m, 16000, g)
r.AcceptWaveform(pcm)
print(json.loads(r.FinalResult()).get('text', ''))
PY
)
  echo "[i] komut: $cmd"
  [ -n "$cmd" ] && cmd_say "$cmd"
}

cmd_inject() {
  local wake="${1:-/tmp/mvw_audio/wake_rep.pcm}"
  local cmd="${2:-/tmp/mvw_audio/cmd16.pcm}"
  python3 "$(dirname "$0")/voice_mic.py" --enable-only >/dev/null 2>&1 || true
  python3 - "$(dirname "$0")" <<'PY'
import sys, glob, os, re
sys.path.insert(0, '/tmp/emulator-grpc-stubs')
try:
    import grpc, emulator_controller_pb2 as pb, emulator_controller_pb2_grpc as rpc
except Exception:
    sys.exit(0)
port = token = None
for p in glob.glob(os.path.expanduser('~/Library/Caches/TemporaryItems/avd/running/pid_*.ini')):
    t = open(p).read()
    m = re.search(r'grpc\.port=(\d+)', t); n = re.search(r'grpc\.token=(.+)', t)
    if m and n:
        port = int(m.group(1)); token = n.group(1).strip()
if port:
    stub = rpc.EmulatorControllerStub(grpc.insecure_channel('127.0.0.1:%d' % port))
    stub.setMicrophoneState(pb.MicrophoneState(realAudioEnabled=False),
                            metadata=[('authorization', 'Bearer ' + token)], timeout=5)
PY
  echo "[i] enjeksiyon: $wake"
  python3 "$(dirname "$0")/voice_mic.py" "$wake" >/dev/null 2>&1
  sleep 1
  echo "[i] enjeksiyon: $cmd"
  python3 "$(dirname "$0")/voice_mic.py" "$cmd" >/dev/null 2>&1
}

case "${1:-}" in
  start) cmd_start ;;
  wake) cmd_wake ;;
  say) shift; cmd_say "${1:-}" ;;
  voice) shift; cmd_voice "${1:-5}" ;;
  inject) shift; cmd_inject "${1:-/tmp/mvw_audio/wake_rep.pcm}" "${2:-/tmp/mvw_audio/cmd16.pcm}" ;;
  *) echo "kullanim: $0 {start|wake|say \"metin\"|voice [sn]}"; exit 2 ;;
esac
