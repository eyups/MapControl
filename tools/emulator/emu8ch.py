#!/usr/bin/env python3
"""16 kHz mono PCM16 -> 8 kanal PCM16 (emulator 8ch test kaynagi).

Kullanim:
  python3 tools/emulator/emu8ch.py in.pcm out8.pcm --layout mic0_ref1
Layoutlar:
  mic0_ref1   : ch0=mic, ch1..7=0
  mic_all     : tum kanallar mic
  2mic2ref    : ch0/1=mic, ch2/3=mic, ch4..7=0
  4mic4ref    : ch0..7=mic
"""
import argparse, array

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('src'); ap.add_argument('dst')
    ap.add_argument('--layout', default='mic0_ref1',
                    choices=['mic0_ref1', 'mic_all', '2mic2ref', '4mic4ref'])
    a = ap.parse_args()
    d = array.array('h', open(a.src, 'rb').read())
    out = array.array('h')
    for s in d:
        if a.layout == 'mic_all':
            out.extend([s] * 8)
        elif a.layout == '2mic2ref':
            out.extend([s, s, s, s, 0, 0, 0, 0])
        elif a.layout == '4mic4ref':
            out.extend([s] * 8)
        else:
            out.extend([s, 0, 0, 0, 0, 0, 0, 0])
    open(a.dst, 'wb').write(out.tobytes())
    print('%s -> %s, %d frame, layout=%s' % (a.src, a.dst, len(d), a.layout))

if __name__ == '__main__':
    main()
