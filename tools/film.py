"""ETERNELLE — cinematic birthday film. Renders one mp4 per scene, then joins and adds the music.
Usage: python3 -I film.py OUTDIR MUSIC_DIR [scene_index_for_stills]"""
import sys, os, math, random, subprocess
import numpy as np
from PIL import Image, ImageDraw, ImageFont, ImageFilter

W, H, FPS = 1080, 1920, 30
OUT = sys.argv[1]; RAW = sys.argv[2]
os.makedirs(OUT, exist_ok=True)
GF = '/usr/share/fonts/truetype/google-fonts/'
SERIF_I = GF + 'Lora-Italic-Variable.ttf'
SANS_L = GF + 'Poppins-Light.ttf'
SANS_M = GF + 'Poppins-Medium.ttf'

PLUM = (74, 29, 85); MID = (23, 17, 42); LAV = (215, 202, 255); ROSE = (255, 194, 209)
GOLD = (255, 226, 160); GOLD2 = (240, 196, 106); PEARL = (255, 248, 245); PINK = (255, 134, 174); INK = (30, 16, 38)

def ease(x): x = min(max(x, 0.0), 1.0); return x * x * (3 - 2 * x)
def back(x):
    x = min(max(x, 0.0), 1.0); s = 1.70158; y = x - 1; return 1 + (s + 1) * y ** 3 + s * y * y
def heart_pt(th):
    x = 16 * math.sin(th) ** 3
    y = 13 * math.cos(th) - 5 * math.cos(2 * th) - 2 * math.cos(3 * th) - math.cos(4 * th)
    return x / 17, (y + 2.5) / 17

# ---------- cached assets ----------
rng = random.Random(4122026)
def make_bg():
    g = np.zeros((H + 300, W, 3), np.float32)
    ys = np.linspace(0, 1, H + 300)[:, None]
    top = np.array(MID, np.float32); m1 = np.array((42, 22, 64), np.float32); m2 = np.array(PLUM, np.float32); bot = np.array((110, 63, 126), np.float32)
    col = np.where(ys < 0.45, top + (m1 - top) * (ys / 0.45), np.where(ys < 0.8, m1 + (m2 - m1) * ((ys - 0.45) / 0.35), m2 + (bot - m2) * ((ys - 0.8) / 0.2)))
    g[:] = col[:, None, :] if col.ndim == 2 else col
    img = Image.fromarray(np.clip(g, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    for _ in range(700):
        x, y = rng.uniform(0, W), rng.uniform(0, H + 300); r = rng.choice([0.6, 0.8, 1.0, 1.3, 1.7])
        a = rng.randint(70, 200)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255) if rng.random() > 0.15 else GOLD)
    return img
BG = make_bg()

def vignette():
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt(((xx - W / 2) / (W * 0.75)) ** 2 + ((yy - H / 2) / (H * 0.62)) ** 2)
    a = np.clip((d - 0.55) * 1.4, 0, 0.75)
    v = np.zeros((H, W, 4), np.uint8); v[..., 3] = (a * 255).astype(np.uint8)
    return Image.fromarray(v, 'RGBA')
VIG = vignette()

_glow = {}
def glow_sprite(r, color):
    key = (r, color)
    if key not in _glow:
        s = r * 2 + 1
        yy, xx = np.mgrid[0:s, 0:s]
        d = np.sqrt((xx - r) ** 2 + (yy - r) ** 2) / r
        a = np.clip(1 - d, 0, 1) ** 1.5
        core = np.clip(1 - d * 3.2, 0, 1)
        arr = np.zeros((s, s, 4), np.float32)
        c = np.array(color, np.float32)
        arr[..., :3] = c * (1 - core[..., None]) + 255 * core[..., None]
        arr[..., 3] = np.clip(a + core, 0, 1) * 255
        _glow[key] = Image.fromarray(arr.astype(np.uint8), 'RGBA')
    return _glow[key]

def fade_sprite(sp, a):
    if a >= 0.999: return sp
    out = sp.copy(); al = out.getchannel('A').point(lambda v: int(v * max(a, 0)))
    out.putalpha(al); return out

def glow(img, x, y, r, color, a=1.0):
    r = max(2, int(r))
    if a <= 0.01: return
    sp = fade_sprite(glow_sprite(r, color), a)
    img.alpha_composite(sp, (int(x - r), int(y - r))) if 0 <= x - r and x + r < W and 0 <= y - r and y + r < H else img.paste(sp, (int(x - r), int(y - r)), sp)

_txt = {}
def text_sprite(s, font, size, color, spacing=0):
    key = (s, font, size, color, spacing)
    if key not in _txt:
        f = ImageFont.truetype(font, size)
        lines = s.split('\n')
        boxes = [f.getbbox(l) for l in lines]
        lh = int(size * 1.35)
        w = max(b[2] - b[0] for b in boxes) + 40 + spacing * max(len(l) for l in lines)
        h = lh * len(lines) + 30
        im = Image.new('RGBA', (w, h), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
        for i, l in enumerate(lines):
            if spacing:
                tw = sum(f.getlength(ch) + spacing for ch in l) - spacing
                x = (w - tw) / 2
                for ch in l:
                    d.text((x, 10 + i * lh), ch, font=f, fill=color); x += f.getlength(ch) + spacing
            else:
                tw = f.getlength(l); d.text(((w - tw) / 2, 10 + i * lh), l, font=f, fill=color)
        # soft glow behind text
        sh = im.filter(ImageFilter.GaussianBlur(10)); out = Image.new('RGBA', im.size, (0, 0, 0, 0))
        out.alpha_composite(fade_sprite(sh, 0.55)); out.alpha_composite(im)
        _txt[key] = out
    return _txt[key]

def text(img, s, y, size, color=PEARL, font=SERIF_I, a=1.0, spacing=0, dy=0, x=None):
    if a <= 0.01: return
    sp = fade_sprite(text_sprite(s, font, size, color, spacing), a)
    img.paste(sp, (int((W if x is None else 2 * x) / 2 - sp.width / 2), int(y - sp.height / 2 + dy)), sp)

def heart_poly(cx, cy, s, n=80):
    return [(cx + heart_pt(i * 2 * math.pi / n)[0] * s, cy - heart_pt(i * 2 * math.pi / n)[1] * s) for i in range(n)]

def base(gt):
    off = int(150 + 120 * math.sin(gt * 0.05))
    img = BG.crop((0, off, W, off + H)).convert('RGBA')
    tw = random.Random(7)
    for k in range(34):
        x = tw.uniform(30, W - 30); y = tw.uniform(30, H - 30); ph = tw.uniform(0, 6)
        a = 0.25 + 0.75 * abs(math.sin(gt * 1.2 + ph))
        glow(img, x, y, 9, GOLD if k % 4 == 0 else (255, 255, 255), a * 0.8)
    return img

def drift_particles(img, gt, n=26, seed=3, a=1.0):
    r = random.Random(seed)
    for k in range(n):
        x0 = r.uniform(0, W); sp = r.uniform(18, 45); ph = r.uniform(0, 100)
        y = H - ((gt * sp + ph * 37) % (H + 200)) + 100
        x = x0 + 30 * math.sin(gt * 0.6 + ph)
        glow(img, x, y, r.choice([7, 9, 12]), GOLD if k % 3 else ROSE, a * 0.85)

# ---------- characters & props ----------
_lumi_body = None
def lumi_body(R):
    global _lumi_body
    if _lumi_body is None or _lumi_body.width != 2 * R + 1:
        s = 2 * R + 1; yy, xx = np.mgrid[0:s, 0:s]
        d = np.sqrt((xx - R) ** 2 + (yy - R) ** 2) / R
        hl = np.sqrt((xx - R * 0.7) ** 2 + (yy - R * 0.65) ** 2) / (R * 1.4)
        c0 = np.array((255, 255, 255), np.float32); c1 = np.array((226, 210, 238), np.float32)
        t = np.clip(hl, 0, 1)[..., None]
        arr = np.zeros((s, s, 4), np.float32); arr[..., :3] = c0 * (1 - t) + c1 * t
        arr[..., 3] = np.clip((1 - d) * R * 0.8, 0, 1) * 255
        _lumi_body = Image.fromarray(arr.astype(np.uint8), 'RGBA')
    return _lumi_body

def draw_lumi(img, cx, cy, R, t, happy=False, scale=1.0):
    if scale <= 0.02: return
    R = int(R * scale)
    glow(img, cx, cy, int(R * 2.3), GOLD, 0.45)
    # wings
    flap = math.sin(t * (18 if happy else 9)) * 14
    for side in (-1, 1):
        wl = Image.new('RGBA', (int(R * 2.6), int(R * 2.6)), (0, 0, 0, 0)); d = ImageDraw.Draw(wl)
        c = wl.width // 2
        d.ellipse([c, c - R * 1.0, c + R * 1.2, c + R * 0.0], fill=LAV + (200,))
        d.ellipse([c, c - R * 0.05, c + R * 0.8, c + R * 0.6], fill=ROSE + (170,))
        glow_w = wl.filter(ImageFilter.GaussianBlur(3))
        if side < 0: glow_w = glow_w.transpose(Image.FLIP_LEFT_RIGHT)
        glow_w = glow_w.rotate(-side * (18 + flap), center=(c, c), resample=Image.BICUBIC)
        img.alpha_composite(glow_w, (int(cx + side * R * 0.45 - c), int(cy - R * 0.1 - c)))
    body = lumi_body(R)
    img.alpha_composite(body, (int(cx - R), int(cy - R)))
    d = ImageDraw.Draw(img)
    # antenna + star bead
    d.line([cx, cy - R, cx + R * 0.12, cy - R * 1.45], fill=GOLD2, width=max(2, R // 18))
    glow(img, cx + R * 0.12, cy - R * 1.5, R * (0.28 + 0.06 * math.sin(t * 4)), GOLD, 1)
    blink = (t % 3.7) < 0.13
    for side in (-1, 1):
        ex, ey = cx + side * R * 0.34, cy - R * 0.08
        if happy or blink:
            d.arc([ex - R * 0.17, ey - R * 0.12, ex + R * 0.17, ey + R * 0.2], 200, 340, fill=INK, width=max(3, R // 14))
        else:
            d.ellipse([ex - R * 0.17, ey - R * 0.17, ex + R * 0.17, ey + R * 0.17], fill=INK)
            d.ellipse([ex + R * 0.0, ey - R * 0.13, ex + R * 0.12, ey - R * 0.01], fill=(255, 255, 255))
        d.ellipse([cx + side * R * 0.58 - R * 0.13, cy + R * 0.2 - R * 0.09, cx + side * R * 0.58 + R * 0.13, cy + R * 0.2 + R * 0.09], fill=(255, 170, 195))
    d.arc([cx - R * 0.17, cy + R * 0.0, cx + R * 0.17, cy + R * 0.28], 20, 160, fill=INK, width=max(3, R // 16))
    hs = R * 0.2 * (1 + 0.12 * abs(math.sin(t * 3.5)))
    d.polygon(heart_poly(cx, cy + R * 0.64, hs), fill=PINK)
    for k in range(6):
        a = k * math.pi / 3 + t * 1.3
        glow(img, cx + math.cos(a) * R * 1.7, cy + math.sin(a) * R * 0.5, max(4, R // 12), GOLD, 0.9)

def draw_flower(img, x, y, s, a, col):
    if s <= 0.02: return
    d = ImageDraw.Draw(img)
    for k in range(5):
        ang = k * 2 * math.pi / 5 + a
        px, py = x + math.cos(ang) * 22 * s, y + math.sin(ang) * 22 * s
        d.ellipse([px - 18 * s, py - 18 * s, px + 18 * s, py + 18 * s], fill=col)
    glow(img, x, y, int(18 * s) + 2, GOLD, 1)

def draw_butterfly(img, x, y, s, t, col):
    flap = abs(math.sin(t * 14)) * 0.75 + 0.25
    d = ImageDraw.Draw(img)
    for side in (-1, 1):
        w = 26 * s * flap
        d.ellipse([x + (0 if side > 0 else -w), y - 24 * s, x + (w if side > 0 else 0), y + 2 * s], fill=col)
        d.ellipse([x + (0 if side > 0 else -w * 0.7), y - 2 * s, x + (w * 0.7 if side > 0 else 0), y + 16 * s], fill=col)
    d.ellipse([x - 3 * s, y - 16 * s, x + 3 * s, y + 12 * s], fill=INK)

def fade_edges(img, t, T, inn=0.6, out=0.6):
    a = min(1, t / inn if inn else 1, (T - t) / out if out else 1)
    if a < 1:
        black = Image.new('RGBA', (W, H), (8, 4, 14, int((1 - max(a, 0)) * 255)))
        img.alpha_composite(black)

# ---------- scenes ----------
def s_title(t, gt):
    img = base(gt); drift_particles(img, gt, 30, 1)
    text(img, 'A ONE-OF-ONE BIRTHDAY UNIVERSE', 640, 30, GOLD, SANS_L, ease((t - 0.6) / 1.2), spacing=5)
    text(img, 'ETERNELLE', 800, 92, PEARL, SANS_L, ease((t - 1.2) / 1.5), spacing=22, dy=(1 - ease((t - 1.2) / 1.5)) * 30)
    text(img, 'for Shiba Sis', 960, 96, ROSE, SERIF_I, ease((t - 2.4) / 1.5), dy=(1 - ease((t - 2.4) / 1.5)) * 30)
    text(img, '4 December 2026', 1100, 34, LAV, SANS_L, ease((t - 3.4) / 1.2), spacing=4)
    return img

def s_portal(t, gt):
    img = base(gt); cx, cy = W / 2, 1000
    d = ImageDraw.Draw(img)
    pop = back(t / 0.8); rise = ease((t - 1.0) / 1.2)
    sy = 1450 - 450 * rise
    star_s = pop * (1 - ease((t - 2.4) / 0.6))
    if star_s > 0.02:
        glow(img, cx, sy, int(70 * star_s), GOLD, 1)
        pts = []
        for k in range(10):
            r = (55 if k % 2 == 0 else 22) * star_s; ang = -math.pi / 2 + k * math.pi / 5 + t * 1.5
            pts.append((cx + math.cos(ang) * r, sy + math.sin(ang) * r))
        d.polygon(pts, fill=GOLD2)
    op = back((t - 2.2) / 1.2)
    if op > 0.02:
        R = 300 * op
        glow(img, cx, cy, int(R * 1.15), LAV, 0.35)
        for w, col in ((14, GOLD2), (4, GOLD)):
            d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=col, width=w)
        d.ellipse([cx - R * 1.09, cy - R * 1.09, cx + R * 1.09, cy + R * 1.09], outline=LAV, width=3)
        for k in range(12):
            a = k * math.pi / 6 + t * 0.2
            glow(img, cx + math.cos(a) * R, cy + math.sin(a) * R, 16, PINK if k % 2 else GOLD, 1)
        for k in range(40):
            a = k * 0.9 + t * (0.9 + (k % 5) * 0.15)
            rr = (40 + (k * 37 % 100) / 100 * 240) * op
            glow(img, cx + math.cos(a) * rr, cy + math.sin(a) * rr, 8, GOLD if k % 2 else PEARL, 0.9)
    for i in range(8):
        ang = math.pi * (0.15 + i * 0.1)
        fx = cx + math.cos(ang) * 380 * (1 if i % 2 else -1) * (0.6 + 0.08 * i)
        fy = 1430 + (i % 3) * 40 + math.sin(gt + i) * 8
        draw_flower(img, fx, fy, back((t - 3.2 - i * 0.12) / 0.8) * 1.3, gt * 0.5 + i, ROSE if i % 3 else LAV)
    for i in range(3):
        a = gt * 0.7 + i * 2.1
        draw_butterfly(img, cx + math.cos(a) * 420, 760 + i * 120 + math.sin(gt * 1.5 + i) * 40, 1.6 * ease((t - 3.4) / 0.8), gt + i, (ROSE + (210,)) if i % 2 else (LAV + (210,)))
    text(img, 'A tiny golden star…', 300, 58, PEARL, SERIF_I, ease((t - 0.5) / 1) * (1 - ease((t - 2.6) / 0.6)))
    text(img, '…becomes a doorway to a universe', 300, 56, PEARL, SERIF_I, ease((t - 3.2) / 1))
    return img

def s_lumi(t, gt):
    img = base(gt); cx, cy = W / 2, 1000
    d = ImageDraw.Draw(img)
    R = 300
    for w, col in ((14, GOLD2), (4, GOLD)):
        d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=col, width=w)
    glow(img, cx, cy, 340, LAV, 0.3)
    em = back(t / 1.4)
    ly = cy + 40 * math.sin(gt * 2.2) - 60 * ease(t / 1.5)
    happy = 2.5 < t < 4.2
    draw_lumi(img, cx, ly, 190, gt, happy=happy, scale=em)
    # speech bubble
    a = ease((t - 1.6) / 0.6)
    if a > 0.01:
        sp = text_sprite('Finally! You\'re here!', SERIF_I, 64, MID)
        bw, bh = sp.width + 60, sp.height + 40
        bub = Image.new('RGBA', (bw, bh + 30), (0, 0, 0, 0)); bd = ImageDraw.Draw(bub)
        bd.rounded_rectangle([0, 0, bw - 1, bh], 40, fill=PEARL + (240,))
        bd.polygon([(bw / 2 - 25, bh - 2), (bw / 2 + 25, bh - 2), (bw / 2, bh + 28)], fill=PEARL + (240,))
        bub.alpha_composite(sp, (30, 20))
        bub = fade_sprite(bub, a)
        img.alpha_composite(bub, (int(cx - bw / 2), int(430 - (1 - a) * 30)))
    text(img, 'Meet Lumi', 1420, 72, ROSE, SERIF_I, ease((t - 3.0) / 1))
    text(img, 'your tiny celestial friend', 1510, 40, LAV, SANS_L, ease((t - 3.6) / 1), spacing=2)
    return img

def island(img, x, y, kind, s, gt):
    if s <= 0.02: return
    d = ImageDraw.Draw(img)
    w = 300 * s
    d.polygon([(x - w / 2, y), (x + w / 2, y), (x, y + 200 * s)], fill=(44, 18, 54))
    d.ellipse([x - w / 2, y - 26 * s, x + w / 2, y + 26 * s], fill=(246, 227, 196))
    glow(img, x, y + 200 * s, int(14 * s), GOLD, 1)
    if kind == 'J':
        for cxo in (-90, -55):
            d.rectangle([x + cxo * s - 9 * s, y - 170 * s, x + cxo * s + 9 * s, y], fill=(156, 149, 184))
            for k in range(3):
                c = ((gt * 0.35 + k / 3) % 1)
                glow(img, x + cxo * s + c * 30 * s, y - 180 * s - c * 90 * s, int((14 + 18 * c) * s), (233, 222, 255), (1 - c) * 0.7)
        d.polygon([(x + 20 * s, y), (x + 110 * s, y), (x + 65 * s, y - 95 * s)], fill=(199, 180, 255))
        for tx in (-10, 30, 120):
            d.polygon([(x + tx * s - 14 * s, y), (x + tx * s + 14 * s, y), (x + tx * s, y - 50 * s)], fill=(139, 211, 174))
    else:
        d.rectangle([x - 90 * s, y - 52 * s, x + 90 * s, y - 44 * s], fill=(156, 149, 184))
        for bx in (-75, 75):
            d.rectangle([x + bx * s - 9 * s, y - 130 * s, x + bx * s + 9 * s, y], fill=(156, 149, 184))
        d.rectangle([x - 80 * s, y - 132 * s, x + 80 * s, y - 124 * s], fill=(156, 149, 184))
        for k in range(9):
            bx = -70 + k * 17.5
            by = -128 + 60 * (1 - (bx / 75) ** 2)
            glow(img, x + bx * s, y + by * s, int(6 * s), GOLD, 1)
        tx = x + math.sin(gt * 0.8) * 70 * s
        d.rectangle([tx - 14 * s, y - 64 * s, tx + 14 * s, y - 52 * s], fill=(255, 210, 63))
        d.ellipse([x + 20 * s, y - 40 * s, x + 90 * s, y + 30 * s], fill=(255, 247, 243))
    label = 'Jamshedpur' if kind == 'J' else 'Kolkata'
    text(img, label, y - 230 * s, 44, GOLD, SERIF_I, min(1, s), x=x)

def s_cities(t, gt):
    img = base(gt)
    jx, kx, iy = 250, 830, 1300
    island(img, jx, iy + 12 * math.sin(gt * 1.1), 'J', back(t / 1.0), gt)
    island(img, kx, iy + 12 * math.sin(gt * 1.1 + 2), 'K', back((t - 0.3) / 1.0), gt)
    n = 34; m = ease((t - 3.4) / 1.6)
    hx0, hy0, hs = W / 2, 720, 210
    for i in range(n):
        u = i / (n - 1)
        ap = back((t - 1.2 - i * 0.05) / 0.4)
        if ap <= 0.02: continue
        bx = (1 - u) ** 2 * jx + 2 * (1 - u) * u * (W / 2) + u * u * kx
        by = (1 - u) ** 2 * (iy - 60) + 2 * (1 - u) * u * 500 + u * u * (iy - 60)
        px, py = heart_pt(i * 2 * math.pi / n)
        x = bx + (hx0 + px * hs - bx) * m; y = by + (hy0 - py * hs - by) * m
        tw = 1 + (0.35 * math.sin(gt * 5 + i) if m > 0.99 else 0)
        glow(img, x, y, int((15 if i % 3 == 0 else 11) * ap * tw), GOLD, 1)
    if m > 0.95:
        hp = ease((t - 4.8) / 0.8)
        hl = Image.new('RGBA', (W, H), (0, 0, 0, 0)); ImageDraw.Draw(hl).polygon(heart_poly(hx0, hy0 + 10, 95 * hp), fill=PINK + (int(170 * hp),))
        img.alpha_composite(hl.filter(ImageFilter.GaussianBlur(4)))
    text(img, 'Two cities. One home.', 330, 62, PEARL, SERIF_I, ease((t - 0.6) / 1) * (1 - ease((t - 4.4) / 0.5)))
    text(img, 'Distance can change the address.', 1620, 50, PEARL, SERIF_I, ease((t - 5.0) / 0.8))
    text(img, 'Never the bond.', 1700, 64, ROSE, SERIF_I, ease((t - 5.6) / 0.8))
    return img

def s_cake(t, gt):
    img = base(gt); d = ImageDraw.Draw(img)
    cx, by = W / 2, 1380 + 10 * math.sin(gt)
    out_t = 3.2
    d.ellipse([cx - 330, by - 30, cx + 330, by + 40], fill=(246, 227, 196))
    d.rounded_rectangle([cx - 260, by - 230, cx + 260, by], 30, fill=(255, 159, 189))
    d.ellipse([cx - 260, by - 260, cx + 260, by - 200], fill=(255, 240, 222))
    for k in range(11):
        x = cx - 230 + k * 46
        d.ellipse([x - 16, by - 232, x + 16, by - 180 - (k % 2) * 18], fill=(255, 240, 222))
    d.rounded_rectangle([cx - 180, by - 410, cx + 180, by - 230], 26, fill=(199, 180, 255))
    d.ellipse([cx - 180, by - 435, cx + 180, by - 385], fill=(255, 240, 222))
    for k in range(5):
        a = k * 2 * math.pi / 5 + 0.4
        d.polygon(heart_poly(cx + math.cos(a) * 110, by - 412 + math.sin(a) * 14, 20), fill=PINK)
    d.rounded_rectangle([cx - 14, by - 560, cx + 14, by - 410], 8, fill=PEARL)
    for k in range(3): d.rectangle([cx - 14, by - 540 + k * 45, cx + 14, by - 530 + k * 45], fill=GOLD2)
    if t < out_t:
        f = 1 + 0.12 * math.sin(gt * 23) + 0.08 * math.sin(gt * 37)
        glow(img, cx, by - 600, int(110 * f), (255, 161, 66), 0.7)
        d.ellipse([cx - 22, by - 650 * 1 - 20 * (f - 1) * 3, cx + 22, by - 568], fill=(255, 161, 66))
        d.ellipse([cx - 10, by - 620, cx + 10, by - 578], fill=(255, 243, 184))
        text(img, 'Make a wish…', 380, 70, PEARL, SERIF_I, ease((t - 0.5) / 0.8) * (1 - ease((t - 2.6) / 0.5)))
    else:
        tt = t - out_t
        for k in range(5):
            c = min(1, max(0, (tt - k * 0.2) / 2))
            if 0 < c < 1: glow(img, cx + math.sin(c * 6 + k) * 30, by - 590 - c * 260, int(20 + 30 * c), (233, 222, 255), (1 - c) * 0.6)
        r = random.Random(99)
        for k in range(70):
            ang = r.uniform(0, 2 * math.pi); sp = r.uniform(300, 900); up = r.uniform(400, 1100)
            x = cx + math.cos(ang) * sp * tt * 0.9; y = by - 600 - up * tt + 500 * tt * tt
            a = max(0, 1 - tt / 3)
            glow(img, x, y, r.choice([8, 11, 15]), r.choice([GOLD, PINK, LAV, PEARL]), a)
        hp = ease((tt - 0.6) / 1.0)
        for i in range(30):
            px, py = heart_pt(i * 2 * math.pi / 30)
            glow(img, cx + px * 230 * hp, 560 - py * 230 * hp, int(14 * hp * (1 + 0.3 * math.sin(gt * 6 + i))), GOLD if i % 2 else PINK, hp)
        text(img, 'Happy Birthday,', 1640, 64, PEARL, SERIF_I, ease((tt - 0.3) / 0.8))
        text(img, 'Shiba Sis', 1745, 104, ROSE, SERIF_I, ease((tt - 0.7) / 0.8))
    return img

def s_hug(t, gt):
    img = base(gt); cx, cy = W / 2, 880 + 14 * math.sin(gt * 1.1)
    glow(img, cx, cy, 420, PINK, 0.35)
    beat = (t % 1.2); s = 1 + 0.06 * (1 if beat < 0.12 else 0)
    hl = Image.new('RGBA', (W, H), (0, 0, 0, 0)); hd = ImageDraw.Draw(hl)
    hd.polygon(heart_poly(cx, cy, 300 * s), fill=(255, 143, 180, 150))
    hd.polygon(heart_poly(cx - 30, cy - 40, 200 * s), fill=(255, 200, 220, 70))
    hd.polygon(heart_poly(cx, cy, 120 * s * (1 + 0.15 * max(0, math.sin(t * 5.2)))), fill=PINK + (255,))
    img.alpha_composite(hl.filter(ImageFilter.GaussianBlur(2)))
    n = 90
    for i in range(n):
        f = i / (n - 1)
        ap = back((t - 0.4 - i * 0.035) / 0.3)
        if ap <= 0.02: continue
        a = i * 0.42 + gt * 0.6
        r = 70 + 300 * math.sqrt(math.sin(f * math.pi))
        x = cx + math.cos(a) * r; y = cy + 330 - f * 640 + math.sin(a) * r * 0.22
        front = math.sin(a) > 0
        glow(img, x, y, int((13 if i % 6 == 0 else 9) * ap), GOLD, 1 if front else 0.55)
    text(img, 'Your veer', 1500, 76, PEARL, SERIF_I, ease((t - 3.6) / 0.9))
    text(img, 'is always with you.', 1600, 76, ROSE, SERIF_I, ease((t - 4.1) / 0.9))
    text(img, 'A LITTLE UNIVERSE. A LIFETIME OF LOVE.', 1780, 28, GOLD, SANS_L, ease((t - 5.0) / 0.9), spacing=4)
    return img

SCENES = [(s_title, 5.5), (s_portal, 6.5), (s_lumi, 5.5), (s_cities, 7.5), (s_cake, 7.0), (s_hug, 7.5)]

def render_scene(idx):
    fn, T = SCENES[idx]
    gt0 = sum(d for _, d in SCENES[:idx])
    tmp = os.path.join(OUT, f'scene{idx}.tmp.mp4'); final = os.path.join(OUT, f'scene{idx}.mp4')
    p = subprocess.Popen(['ffmpeg', '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24', '-s', f'{W}x{H}',
                          '-r', str(FPS), '-i', '-', '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20',
                          '-pix_fmt', 'yuv420p', '-r', str(FPS), tmp], stdin=subprocess.PIPE)
    nf = int(round(T * FPS))
    for f in range(nf):
        t = f / FPS
        img = fn(t, gt0 + t)
        img.alpha_composite(VIG)
        fade_edges(img, t, T, 0.6 if idx else 1.0, 0.6)
        p.stdin.write(img.convert('RGB').tobytes())
    p.stdin.close()
    if p.wait() == 0: os.replace(tmp, final)
    print('scene', idx, 'done', flush=True)

def still(idx, t):
    fn, T = SCENES[idx]; gt0 = sum(d for _, d in SCENES[:idx])
    img = fn(t, gt0 + t); img.alpha_composite(VIG)
    img.convert('RGB').save(os.path.join(OUT, f'still_{idx}_{t}.jpg'), quality=88)

if __name__ == '__main__':
    if len(sys.argv) > 3 and sys.argv[3] == 'stills':
        for i, t in [(0, 4.5), (1, 5.5), (2, 4.0), (3, 6.8), (4, 2.0), (4, 5.5), (5, 6.5)]: still(i, t)
        sys.exit()
    if len(sys.argv) > 3:
        render_scene(int(sys.argv[3])); sys.exit()
    for i in range(len(SCENES)): render_scene(i)
