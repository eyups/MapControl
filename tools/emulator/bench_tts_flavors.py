#!/usr/bin/env python3
"""tts30 / tts40 / ttsService gecikme benchmark'ı.

Ön koşul:
  tools/emulator/prepare_tts_flavors.sh
  ./gradlew :app:assembleTts30Debug :app:assembleTts40Debug :app:assembleTtsServiceDebug
  Emülatörde tts30/tts40 kaynakları:
    /data/local/tmp/tts30/xTTSRes + /data/local/tmp/tts30/Active/TTSRes/res_version (1001 1062)
    /data/local/tmp/tts40/xTTS40Res + /data/local/tmp/tts40/Active/TTSRes/res_version (1031_1902_cst238)

Ölçümler (TtsBench logları):
  direct: request -> init, start, first_audio, last_audio, done
  service: request -> service_connected, session, start, play_begin, play_completed
"""
import argparse
import json
import re
import shlex
import statistics
import subprocess
import time
from pathlib import Path

SERIAL = "emulator-5554"
ROOT = Path(__file__).resolve().parents[2]
PKG = "com.eyups.roaddeck"
RECEIVER = f"{PKG}/.tts.TtsTestReceiver"
APKS = {
    "tts30": ROOT / "app/build/outputs/apk/tts30/debug/app-tts30-debug.apk",
    "tts40": ROOT / "app/build/outputs/apk/tts40/debug/app-tts40-debug.apk",
    "ttsService": ROOT / "app/build/outputs/apk/ttsService/debug/app-ttsService-debug.apk",
}
TEXTS = [
    ("short", "Merhaba"),
    ("long", "Merhaba bugun hava cok guzel ve yollar acik"),
]
LINE = re.compile(
    r"flavor=(\S+) phase=(\w+) ms=(\d+)(?: ok=(\w+))?(?: bytes=(\d+))?(?: ret=(-?\d+))?"
)


def adb(*args, timeout=30):
    return subprocess.run(
        ["adb", "-s", SERIAL, *args], capture_output=True, text=True, timeout=timeout
    )


def install(flavor):
    apk = APKS[flavor]
    if not apk.exists():
        raise SystemExit(f"APK yok: {apk}")
    r = adb("install", "-r", "-g", str(apk), timeout=180)
    if "Success" not in r.stdout:
        raise SystemExit(f"kurulum başarısız {flavor}: {r.stdout}\n{r.stderr}")


def broadcast(text):
    cmd = (
        f"am broadcast -n {RECEIVER} -a {PKG}.TEST_TTS "
        f"--es text {shlex.quote(text)}"
    )
    adb("shell", cmd)


def read_log():
    return adb("logcat", "-d", "-v", "brief", timeout=20).stdout


def collect(flavor, text, timeout=10):
    adb("logcat", "-c")
    broadcast(text)
    deadline = time.time() + timeout
    seen = {}
    while time.time() < deadline:
        time.sleep(0.25)
        for line in read_log().splitlines():
            if "TtsBench" not in line:
                continue
            m = LINE.search(line)
            if not m:
                continue
            f, phase, ms, ok, byt, ret = m.groups()
            if f != flavor:
                continue
            seen[phase] = {
                "ms": int(ms),
                "ok": ok,
                "bytes": int(byt) if byt else None,
                "ret": ret,
            }
        if flavor == "ttsService":
            # İlk-ses gecikmesi için play_begin yeterli; servis emülatörde
            # play_completed sonrası çökebiliyor.
            if "play_begin" in seen or "play_completed" in seen:
                break
        elif "done" in seen:
            break
    return seen


def main():
    global SERIAL
    ap = argparse.ArgumentParser()
    ap.add_argument("--serial", default=SERIAL)
    ap.add_argument("--iterations", type=int, default=3)
    ap.add_argument("--timeout", type=float, default=10)
    ap.add_argument("--only", choices=["tts30", "tts40", "ttsService"])
    ap.add_argument("--json", default="/tmp/tts_bench_results.json")
    args = ap.parse_args()
    SERIAL = args.serial

    flavors = [args.only] if args.only else list(APKS)
    results = {}
    for flavor in flavors:
        install(flavor)
        adb("shell", "am", "force-stop", PKG)
        if flavor == "ttsService":
            adb("shell", "am", "force-stop", "com.iflytek.cutefly.speechclient.hmi")
        time.sleep(1)
        results[flavor] = {}
        for label, text in TEXTS:
            rows = []
            for i in range(args.iterations):
                rows.append(collect(flavor, text, args.timeout))
                time.sleep(1.0)
            results[flavor][label] = rows
            print(flavor, label, json.dumps(rows), flush=True)

    Path(args.json).write_text(json.dumps(results, indent=2))
    print("\n=== SUMMARY (warm median; cold first) ===")
    keys = [
        "init", "service_connected", "session", "start",
        "first_audio", "play_begin", "last_audio", "done", "play_completed",
    ]
    for flavor, by_text in results.items():
        for label, rows in by_text.items():
            parts = []
            for key in keys:
                vals = [r[key]["ms"] for r in rows if key in r]
                if not vals:
                    continue
                warm = vals[1:] if len(vals) > 1 else vals
                parts.append(f"{key}={int(statistics.median(warm))}ms(cold {vals[0]})")
            print(flavor, label, " ".join(parts))
    print("json:", args.json)


if __name__ == "__main__":
    main()
