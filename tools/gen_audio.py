"""Original synthesised music + SFX for ETERNELLE. Usage: python3 gen_audio.py OUTDIR"""
import sys, os, subprocess, numpy as np
from scipy.signal import butter, lfilter
SR = 44100
out = sys.argv[1]; os.makedirs(out, exist_ok=True)
rng = np.random.default_rng(4122026)

def note(n):  # midi -> Hz
    return 440.0 * 2 ** ((n - 69) / 12)

def env(n, a=0.005, d=1.2):
    t = np.arange(n) / SR
    e = np.minimum(t / a, 1.0) * np.exp(-t / d)
    return e

def musicbox(f, dur, d=0.9, amp=0.3):
    n = int(dur * SR); t = np.arange(n) / SR
    w = (np.sin(2*np.pi*f*t) + 0.35*np.sin(2*np.pi*2*f*t)*np.exp(-t/0.3)
         + 0.12*np.sin(2*np.pi*5.04*f*t)*np.exp(-t/0.12))
    return amp * w * env(n, 0.003, d)

def bell(f, dur, amp=0.25):
    n = int(dur * SR); t = np.arange(n) / SR
    parts = [(1, 1), (2.76, .5), (5.4, .25), (8.93, .12)]
    w = sum(a*np.sin(2*np.pi*f*r*t)*np.exp(-t*(1.2+r*0.6)) for r, a in parts)
    return amp * w * np.minimum(t/0.002, 1)

def pad(freqs, dur, amp=0.08):
    n = int(dur * SR); t = np.arange(n) / SR
    w = np.zeros(n)
    for f in freqs:
        for det in (-0.6, 0.6):
            w += np.sin(2*np.pi*(f+det)*t + rng.uniform(0, 6.28))
    w *= amp / len(freqs)
    fade = np.minimum(np.minimum(t/0.8, 1), np.minimum((dur-t)/0.8, 1))
    return w * fade

def lowpass(x, fc):
    b, a = butter(2, fc/(SR/2)); return lfilter(b, a, x)

def mix_into(buf, x, start):
    s = int(start*SR); e = min(len(buf), s+len(x)); buf[s:e] += x[:e-s]

def reverb(x, wet=0.25):
    y = x.copy()
    for dl, g in ((0.031, .5), (0.047, .42), (0.071, .33), (0.113, .25), (0.167, .18), (0.239, .12)):
        d = int(dl*SR); y[d:] += g * wet * 2 * x[:-d]
    return y

def layer(a, b, off):
    x = np.zeros(max(len(a), int(off*SR)+len(b))); x[:len(a)] += a; mix_into(x, b, off); return x

def write(name, x, stereo_pan=None, q=4):
    x = x / max(1e-6, np.max(np.abs(x))) * 0.89
    if stereo_pan is None:
        data = np.stack([x, x], 1)
    else:
        l = np.cos((stereo_pan+1)*np.pi/4); r = np.sin((stereo_pan+1)*np.pi/4)
        data = np.stack([x*l, x*r], 1)
    pcm = (data*32767).astype('<i2').tobytes()
    path = os.path.join(out, name + '.ogg')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-f', 's16le', '-ar', str(SR), '-ac', '2',
                    '-i', '-', '-c:a', 'libvorbis', '-q:a', str(q), path], input=pcm, check=True)
    print(name, round(len(x)/SR, 1), 's', os.path.getsize(path)//1024, 'KB')

# ---------- Main theme: gentle music box over a warm pad, D major, loops ----------
bpm = 76; beat = 60/bpm; bar = 3*beat  # waltz feel
prog = [(62, [50, 57, 62, 66]), (59, [47, 54, 59, 62]), (55, [43, 50, 55, 59]), (57, [45, 52, 57, 61])]
melody_bars = [
    [74, 78, 81], [78, 76, 74], [79, 78, 76], [76, 73, 69],
    [74, 78, 81], [83, 81, 78], [79, 83, 86], [85, 81, 78],
    [86, 85, 83], [81, 78, 74], [79, 78, 76], [76, 78, 81],
    [78, 81, 86], [83, 81, 78], [79, 76, 73], [74, None, None],
]
total = bar*16
theme = np.zeros(int(total*SR) + SR*3)
for i in range(16):
    root, chord = prog[i % 4]
    t0 = i*bar
    mix_into(theme, pad([note(n) for n in chord], bar+0.9), t0)
    for k, n in enumerate([chord[0], chord[2], chord[3], chord[2], chord[1], chord[2]]):
        mix_into(theme, musicbox(note(n+12), 1.2, 0.6, 0.10), t0 + k*beat/2)
    for k, m in enumerate(melody_bars[i]):
        if m: mix_into(theme, musicbox(note(m), 2.2, 1.1, 0.26), t0 + k*beat)
    if i % 2 == 1:
        mix_into(theme, bell(note(root+36), 2.0, 0.05), t0 + 2.5*beat)
theme = reverb(theme, 0.3)
# seamless loop: fold tail onto head
L = int(total*SR); tail = theme[L:]; theme = theme[:L]; theme[:len(tail)] += tail
write('music_universe', theme, q=3)

# ---------- SFX ----------
def sparkle(n_notes=7, base=84, step=0.045, amp=0.2):
    x = np.zeros(int((n_notes*step+1.2)*SR))
    scale = [0, 2, 4, 7, 9, 12, 14, 16, 19, 21, 24]
    for k in range(n_notes):
        mix_into(x, bell(note(base+scale[k % len(scale)]), 1.0, amp), k*step)
    return reverb(x, .3)

write('sfx_chime', reverb(layer(bell(note(88), 1.5, .3), 0.6*bell(note(95), 1.4, .2), .06), .3))
write('sfx_sparkle', sparkle())
# giggle: quick pitched blips with vibrato (Lumi's laugh, no human voice)
g = np.zeros(int(1.1*SR))
for k, n in enumerate([88, 91, 89, 92, 90, 94]):
    d = 0.11; nn = int(d*SR); t = np.arange(nn)/SR
    f = note(n) * (1 + 0.04*np.sin(2*np.pi*28*t)) * (1 + 0.15*t/d)
    ph = 2*np.pi*np.cumsum(f)/SR
    w = (np.sin(ph) + 0.3*np.sin(2*ph)) * np.sin(np.pi*t/d)**2
    mix_into(g, 0.3*w, k*0.13)
write('sfx_giggle', reverb(g, .2))
# whoosh: band-passed noise sweep
n = int(1.2*SR); t = np.arange(n)/SR; noise = rng.normal(0, 1, n)
wh = np.zeros(n); seg = 2048
for s in range(0, n, seg):
    fc = 300 + 3500*np.sin(np.pi*s/n)
    wh[s:s+seg] = lowpass(noise[s:s+seg+512], fc)[:len(wh[s:s+seg])]
write('sfx_whoosh', wh*np.sin(np.pi*t/1.2)**2)
# page turn: short filtered noise flick
n = int(0.45*SR); t = np.arange(n)/SR
write('sfx_page', lowpass(rng.normal(0, 1, n), 5000)*np.exp(-t/0.09)*np.minimum(t/0.01, 1))
# blow: soft breath noise
n = int(1.0*SR); t = np.arange(n)/SR
write('sfx_blow', lowpass(rng.normal(0, 1, n), 1200)*np.sin(np.pi*t)**1.5)
# pop
n = int(0.25*SR); t = np.arange(n)/SR
write('sfx_pop', np.sin(2*np.pi*(900-2400*t)*t)*np.exp(-t/0.04))
# portal: rising shimmer
x = np.zeros(int(3.2*SR))
for k in range(18):
    mix_into(x, bell(note(62 + k*2 + (k % 3)), 1.4, 0.10 + k*0.006), k*0.12)
mix_into(x, pad([note(50), note(57), note(62), note(69)], 3.0, 0.12), 0)
write('sfx_portal', reverb(x, .4))
# celebration: fanfare arpeggio + bells cascade
x = np.zeros(int(5*SR))
for k, n in enumerate([62, 66, 69, 74, 78, 81, 86]):
    mix_into(x, musicbox(note(n), 2.5, 1.2, 0.3), k*0.09)
for k in range(24):
    mix_into(x, bell(note(rng.choice([86, 88, 90, 93, 95, 98])), 1.5, 0.12), 0.7 + k*0.13)
mix_into(x, pad([note(50), note(62), note(66), note(69)], 4.5, 0.2), 0.2)
write('sfx_celebrate', reverb(x, .4))
# city bells, panned for spatial cue (Jamshedpur left, Kolkata right)
write('sfx_bell_left', reverb(layer(bell(note(74), 2.5, .3), bell(note(81), 2.2, .2), .25), .3), stereo_pan=-0.8)
write('sfx_bell_right', reverb(layer(bell(note(78), 2.5, .3), bell(note(86), 2.2, .2), .25), .3), stereo_pan=0.8)
# hug heartbeat-chime
x = np.zeros(int(2.4*SR))
for s in (0, .28, 1.2, 1.48):
    nn = int(.2*SR); t = np.arange(nn)/SR
    mix_into(x, 0.5*np.sin(2*np.pi*70*t)*np.exp(-t/0.06), s)
mix_into(x, bell(note(81), 2.0, .15), 0.1)
write('sfx_heartbeat', reverb(x, .3))

# ---------- Voice message PLACEHOLDER (replace with Ranjot's real recording) ----------
x = np.zeros(int(14*SR))
lull = [74, 78, 76, 74, 71, 74, 69, None, 74, 78, 81, 79, 78, 76, 74]
for k, m in enumerate(lull):
    if m: mix_into(x, musicbox(note(m), 2.5, 1.3, 0.25), k*0.75 + 0.5)
mix_into(x, pad([note(50), note(57), note(62)], 13.5, 0.15), 0.2)
write('veer_message', reverb(x, .35), q=3)
