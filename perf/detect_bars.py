"""Detect HWUI GPU-profiling bars (visual_bars) in a screenshot.
Heuristic: bars live in the bottom ~30% of the window; they contain saturated red/blue/orange
segments and a green 16ms guide line spanning most of the width.
"""
import sys
from PIL import Image

path = sys.argv[1]
im = Image.open(path).convert("RGB")
w, h = im.size
px = im.load()
y0 = int(h * 0.70)
band = h - y0
red = blue = green_row_best = 0
for y in range(y0, h, 2):
    g_count = 0
    for x in range(0, w, 4):
        r, g, b = px[x, y]
        if r > 180 and g < 90 and b < 90:
            red += 1
        if b > 180 and r < 110 and g < 170:
            blue += 1
        if g > 150 and r < 120 and b < 120:
            g_count += 1
    green_row_best = max(green_row_best, g_count)
samples = ((h - y0) // 2) * ((w + 3) // 4)
cols = (w + 3) // 4
print(f"size={w}x{h} band_rows={(h-y0)//2}")
print(f"red_frac={red/samples:.4f} blue_frac={blue/samples:.4f} best_green_row_frac={green_row_best/cols:.3f}")
verdict = (red / samples > 0.02 and blue / samples > 0.01) or green_row_best / cols > 0.55
print("BARS_PRESENT" if verdict else "NO_BARS")
