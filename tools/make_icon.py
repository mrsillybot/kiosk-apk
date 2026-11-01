"""Draws app/res/mipmap-xxxhdpi/ic_launcher.png (a small fridge). Standard library only: python3 tools/make_icon.py"""
import struct, zlib

N = 192

def rr(x, y, x0, y0, x1, y1, r):
    """Signed distance to a rounded rectangle (negative inside)."""
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    hx, hy = (x1 - x0) / 2 - r, (y1 - y0) / 2 - r
    dx, dy = abs(x - cx) - hx, abs(y - cy) - hy
    return (max(dx, 0) ** 2 + max(dy, 0) ** 2) ** 0.5 + min(max(dx, dy), 0) - r

def over(dst, src, a):
    return tuple(d * (1 - a) + s * a for d, s in zip(dst, src))

shapes = [  # (rect, radius, colour)
    ((0, 0, N, N), 44, (31, 78, 121)),
    ((52, 24, 140, 168), 14, (245, 247, 250)),
    ((52, 82, 140, 86), 0, (31, 78, 121)),
    ((118, 40, 126, 68), 4, (31, 78, 121)),
    ((118, 98, 126, 138), 4, (31, 78, 121)),
]
raw = b""
for y in range(N):
    raw += b"\x00"
    for x in range(N):
        px, a_total = (0.0, 0.0, 0.0), 0.0
        for (x0, y0, x1, y1), r, col in shapes:
            d = rr(x + 0.5, y + 0.5, x0, y0, x1, y1, r)
            a = max(0.0, min(1.0, 0.5 - d))
            if a > 0:
                px = over(px, col, a) if a_total > 0 else col
                a_total = max(a_total, a) if len(shapes) else a
        raw += bytes([int(c) for c in px]) + bytes([int(255 * a_total)])

def chunk(t, d):
    c = struct.pack(">I", len(d)) + t + d
    return c + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)

png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", N, N, 8, 6, 0, 0, 0)) \
    + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
open("app/res/mipmap-xxxhdpi/ic_launcher.png", "wb").write(png)
