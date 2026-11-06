#!/usr/bin/env python3
"""Emülatör mikrofonuna 16 kHz mono PCM16 ses enjekte eder (gRPC injectAudio).

Kullanım:
  python3 tools/emulator/voice_mic.py /tmp/va_voice_turn.pcm [--rate 16000]

Gereksinim: `pip3 install grpcio grpcio-tools`. Proto, Android SDK içindeki
emulator_controller.proto'dan otomatik derlenir.
"""
import argparse
import glob
import os
import re
import subprocess
import sys
import tempfile
import time

SDK = os.environ.get('ANDROID_HOME') or os.path.expanduser(
    '~/Library/Android/sdk')


def prepare_stubs():
    out = os.path.join(tempfile.gettempdir(), 'emulator-grpc-stubs')
    pb2 = os.path.join(out, 'emulator_controller_pb2.py')
    if os.path.isfile(pb2):
        return out
    os.makedirs(out, exist_ok=True)
    proto = os.path.join(SDK, 'emulator', 'lib', 'emulator_controller.proto')
    if not os.path.isfile(proto):
        raise SystemExit('emulator_controller.proto bulunamadı: ' + proto)
    import grpc_tools
    include = os.path.join(os.path.dirname(grpc_tools.__file__), '_proto')
    subprocess.check_call([
        sys.executable, '-m', 'grpc_tools.protoc',
        '-I', os.path.dirname(proto), '-I', include,
        '--python_out=' + out, '--grpc_python_out=' + out, proto,
    ])
    return out


def endpoint():
    candidates = []
    candidates += glob.glob(os.path.expanduser(
        '~/Library/Caches/TemporaryItems/avd/running/pid_*.ini'))
    candidates += glob.glob(os.path.expanduser(
        '~/.android/avd/running/pid_*.ini'))
    candidates += glob.glob('/tmp/android-*/avd/running/pid_*.ini')
    for path in sorted(candidates, reverse=True):
        text = open(path).read()
        port = re.search(r'grpc\.port=(\d+)', text)
        token = re.search(r'grpc\.token=(.+)', text)
        if port and token:
            return int(port.group(1)), token.group(1).strip()
    raise SystemExit('çalışan emülatörün grpc.port/token bilgisi bulunamadı')


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('pcm', nargs='?')
    parser.add_argument('--enable-only', action='store_true',
                        help='ses enjekte etme; yalnız host mikrofonunu aç')
    parser.add_argument('--rate', type=int, default=16000)
    parser.add_argument('--chunk-ms', type=int, default=20)
    parser.add_argument('--real', action='store_true',
                        help='eski uyumluluk; host mikrofonu artik varsayilan acik')
    parser.add_argument('--no-host-mic', action='store_true',
                        help='host mikrofonunu kapat, yalniz enjeksiyon yap')
    args = parser.parse_args()

    sys.path.insert(0, prepare_stubs())
    import grpc
    import emulator_controller_pb2 as pb
    import emulator_controller_pb2_grpc as rpc

    port, token = endpoint()
    stub = rpc.EmulatorControllerStub(
        grpc.insecure_channel('127.0.0.1:%d' % port))
    meta = [('authorization', 'Bearer ' + token)]
    # Host mikrofonu varsayilan açık: aksi halde tarayıcı/AudioRecord
    # kayıtları sessiz kalır. Enjeksiyon realAudioEnabled=True ile de çalışır.
    real = not args.no_host_mic
    stub.setMicrophoneState(pb.MicrophoneState(realAudioEnabled=real),
                            metadata=meta, timeout=5)
    if args.enable_only or not args.pcm:
        print('host mikrofonu açıldı (realAudioEnabled=True)')
        return
    data = open(args.pcm, 'rb').read()
    chunk = max(2, int(args.rate * args.chunk_ms / 1000) * 2)
    fmt = pb.AudioFormat(
        samplingRate=args.rate,
        channels=pb.AudioFormat.Mono,
        format=pb.AudioFormat.AUD_FMT_S16,
        mode=pb.AudioFormat.MODE_REAL_TIME,
    )

    def packets():
        for offset in range(0, len(data) - chunk + 1, chunk):
            yield pb.AudioPacket(
                format=fmt,
                timestamp=int(time.time() * 1_000_000),
                audio=data[offset:offset + chunk],
            )
            time.sleep(args.chunk_ms / 1000.0)

    print('mikrofona enjekte ediliyor: %s (%.1f sn)' %
          (args.pcm, len(data) / (args.rate * 2.0)), flush=True)
    stub.injectAudio(packets(), metadata=meta, timeout=120)
    print('enjeksiyon tamam', flush=True)


if __name__ == '__main__':
    main()
