#!/usr/bin/env python3
"""Vosk komut/uyandirma RTF benchmark'i (host).

Kullanim:
  python3 tools/emulator/bench_vosk.py --model /tmp/vosk-model-small-tr \
      --audio /tmp/vcmd2 --grammar-file /tmp/grammar.json

Her .pcm dosyasi 16 kHz mono PCM16 olmalidir. Cikti: sure, CPU, RTF, xRT.
"""
import argparse
import glob
import json
import os
import time


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--audio", required=True, help="PCM dosyalari dizini")
    ap.add_argument("--grammar-file", help="JSON dizi: komut ifadeleri")
    ap.add_argument("--free", action="store_true", help="Gramersiz de calistir")
    args = ap.parse_args()

    from vosk import Model, KaldiRecognizer, SetLogLevel
    SetLogLevel(-1)
    model = Model(args.model)
    grammar = None
    if args.grammar_file:
        with open(args.grammar_file, encoding="utf-8") as f:
            grammar = json.load(f)
        grammar.append("[unk]")
        grammar = json.dumps(grammar, ensure_ascii=False)

    files = sorted(glob.glob(os.path.join(args.audio, "*.pcm")))
    if not files:
        raise SystemExit("PCM bulunamadi: " + args.audio)

    modes = [("grammar", grammar)]
    if args.free or grammar is None:
        modes.append(("free", None))

    for name, g in modes:
        total_audio = total_cpu = 0.0
        print("== " + name)
        for path in files:
            data = open(path, "rb").read()
            audio = len(data) / 32000.0
            rec = KaldiRecognizer(model, 16000, g) if g else KaldiRecognizer(model, 16000)
            t0 = time.perf_counter()
            rec.AcceptWaveform(data)
            result = json.loads(rec.FinalResult())
            cpu = time.perf_counter() - t0
            total_audio += audio
            total_cpu += cpu
            print("%-32s %6.2fs audio %6.3fs cpu  %s"
                  % (os.path.basename(path), audio, cpu, result.get("text", "")))
        print("TOTAL audio=%.2fs cpu=%.3fs RTF=%.4f xRT=%.1f"
              % (total_audio, total_cpu, total_cpu / total_audio,
                 total_audio / total_cpu))


if __name__ == "__main__":
    main()
