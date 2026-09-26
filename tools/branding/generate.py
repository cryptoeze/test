#!/usr/bin/env python3
"""Generates every CryptoEze brand asset from one set of vector geometry.

Outputs:
  branding/                      master SVGs + HD PNGs (logo, icon, Play Store graphics)
  app/src/main/res/drawable*/    Android vector drawables (launcher, splash, notification)
  app/src/main/res/mipmap-*/     legacy PNG launcher icons (Android 7.x)

Run:  pip install fonttools cairosvg && python3 tools/branding/generate.py
"""
import math
import os

import cairosvg
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "branding")
RES = os.path.join(ROOT, "app", "src", "main", "res")
FONT = os.path.join(os.path.dirname(__file__), "Montserrat_800ExtraBold.ttf")

# ---- Brand palette -------------------------------------------------------
TEAL = "#1FD6C4"
GOLD = "#E2C07D"
INK = "#121820"      # tile + dark text
WHITE = "#FFFFFF"

# ---- Mark geometry (1024 x 1024 design grid, centre 512,512) --------------
CX = CY = 512.0
R_OUT, R_IN, CUT = 212.0, 155.0, 91.0     # ring radii and half-height of the opening
DOT_X, DOT_R = 682.0, 43.0
TILE_X0, TILE_X1, TILE_RAD = 140.0, 884.0, 160.0


def f(v):
    return f"{v:.2f}".rstrip("0").rstrip(".")


def ring_path():
    xo = CX + math.sqrt(R_OUT ** 2 - CUT ** 2)
    xi = CX + math.sqrt(R_IN ** 2 - CUT ** 2)
    top, bot = CY - CUT, CY + CUT
    return (f"M{f(xo)},{f(top)}A{f(R_OUT)},{f(R_OUT)} 0 1 0 {f(xo)},{f(bot)}"
            f"L{f(xi)},{f(bot)}A{f(R_IN)},{f(R_IN)} 0 1 1 {f(xi)},{f(top)}Z")


def dot_path():
    r = DOT_R
    return (f"M{f(DOT_X - r)},{f(CY)}A{f(r)},{f(r)} 0 1 1 {f(DOT_X + r)},{f(CY)}"
            f"A{f(r)},{f(r)} 0 1 1 {f(DOT_X - r)},{f(CY)}Z")


def tile_path():
    a, b, r = TILE_X0, TILE_X1, TILE_RAD
    return (f"M{f(a + r)},{f(a)}H{f(b - r)}A{f(r)},{f(r)} 0 0 1 {f(b)},{f(a + r)}"
            f"V{f(b - r)}A{f(r)},{f(r)} 0 0 1 {f(b - r)},{f(b)}H{f(a + r)}"
            f"A{f(r)},{f(r)} 0 0 1 {f(a)},{f(b - r)}V{f(a + r)}A{f(r)},{f(r)} 0 0 1 {f(a + r)},{f(a)}Z")


# ---- Wordmark: text converted to outlines ---------------------------------
def wordmark(text, x, baseline, cap_height, tracking):
    """Returns [(svg path d, char)] for text laid out at the given cap height."""
    font = TTFont(FONT)
    gs = font.getGlyphSet()
    cmap = font.getBestCmap()
    cap = font["OS/2"].sCapHeight or 700
    s = cap_height / cap
    out = []
    for ch in text:
        name = cmap[ord(ch)]
        pen = SVGPathPen(gs)
        gs[name].draw(TransformPen(pen, (s, 0, 0, -s, x, baseline)))
        out.append(pen.getCommands())
        x += gs[name].width * s + tracking
    return out, x


def wordmark_width(text, cap_height, tracking):
    _, end = wordmark(text, 0, 0, cap_height, tracking)
    return end - tracking


# ---- SVG writers -----------------------------------------------------------
def svg(w, h, body, bg=None):
    rect = f'<rect width="{w}" height="{h}" fill="{bg}"/>' if bg else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">'
            f"{rect}{body}</svg>")


def mark_body(tile=True, tile_color=INK, transform=""):
    t = f'<path fill="{tile_color}" d="{tile_path()}"/>' if tile else ""
    return (f'<g transform="{transform}">{t}<path fill="{TEAL}" d="{ring_path()}"/>'
            f'<path fill="{GOLD}" d="{dot_path()}"/></g>')


def logo_body(text_color, w, h):
    """Stacked logo: tile on top, CRYPTOEZE underneath (like the original)."""
    cap, track = 116.0, 12.0
    tw = wordmark_width("CRYPTOEZE", cap, track)
    x0 = (w - tw) / 2
    icon_size = 560
    s = icon_size / 744.0
    ix = (w - icon_size) / 2
    iy = 40
    body = mark_body(transform=f"translate({f(ix - TILE_X0 * s)},{f(iy - TILE_X0 * s)}) scale({f(s)})")
    baseline = iy + icon_size + 80 + cap
    paths, _ = wordmark("CRYPTOEZE", x0, baseline, cap, track)
    for i, d in enumerate(paths):
        body += f'<path fill="{text_color if i < 6 else TEAL}" d="{d}"/>'
    return body


def horizontal_logo_body(text_color, w, h):
    cap, track = 118.0, 10.0
    tile = 300
    gap = 60
    tw = wordmark_width("CRYPTOEZE", cap, track)
    x0 = (w - (tile + gap + tw)) / 2
    s = tile / 744.0
    y0 = (h - tile) / 2
    body = mark_body(transform=f"translate({f(x0 - TILE_X0 * s)},{f(y0 - TILE_X0 * s)}) scale({f(s)})")
    paths, _ = wordmark("CRYPTOEZE", x0 + tile + gap, h / 2 + cap / 2, cap, track)
    for i, d in enumerate(paths):
        body += f'<path fill="{text_color if i < 6 else TEAL}" d="{d}"/>'
    return body


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write(content)


def png(svg_text, path, size_w, size_h=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cairosvg.svg2png(bytestring=svg_text.encode(), write_to=path,
                     output_width=size_w, output_height=size_h or size_w)


# ---- Android vector drawables ---------------------------------------------
def vd(paths, scale, viewport=108, tx=None):
    """VectorDrawable: 1024-grid paths scaled around centre into a `viewport` canvas."""
    t = viewport / 2 - 512 * scale if tx is None else tx
    items = "".join(f'\n        <path android:fillColor="{c}" android:pathData="{d}"/>' for c, d in paths)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by tools/branding/generate.py - do not edit by hand. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{viewport}dp" android:height="{viewport}dp"\n'
            f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n'
            f'    <group android:translateX="{f(t)}" android:translateY="{f(t)}"\n'
            f'        android:scaleX="{scale}" android:scaleY="{scale}">{items}\n'
            f'    </group>\n</vector>\n')


def main():
    ring, dot, tile = ring_path(), dot_path(), tile_path()

    # Master SVGs ------------------------------------------------------------
    icon_svg = svg(1024, 1024, mark_body())
    write(f"{OUT}/cryptoeze-icon.svg", icon_svg)
    write(f"{OUT}/cryptoeze-mark.svg", svg(1024, 1024, mark_body(tile=False)))
    for mode, color in (("light", INK), ("dark", WHITE)):
        s = svg(1400, 1000, logo_body(color, 1400, 1000))
        write(f"{OUT}/cryptoeze-logo-{mode}.svg", s)
        png(s, f"{OUT}/png/cryptoeze-logo-{mode}-4k.png", 3920, 2800)
        h = svg(2000, 440, horizontal_logo_body(color, 2000, 440))
        write(f"{OUT}/cryptoeze-logo-horizontal-{mode}.svg", h)
        png(h, f"{OUT}/png/cryptoeze-logo-horizontal-{mode}-4k.png", 4000, 880)
    png(icon_svg, f"{OUT}/png/cryptoeze-icon-4096.png", 4096)

    # Play Store assets ------------------------------------------------------
    play_icon = svg(1024, 1024, mark_body(tile=False, transform="translate(512,512) scale(1.28) translate(-512,-512)"), bg=INK)
    png(play_icon, f"{OUT}/play-store/icon-512.png", 512)
    feature = svg(1024, 500,
                  '<defs><radialGradient id="g" cx="0.5" cy="0.45" r="0.7">'
                  f'<stop offset="0" stop-color="#1B2530"/><stop offset="1" stop-color="{INK}"/></radialGradient></defs>'
                  '<rect width="1024" height="500" fill="url(#g)"/>'
                  '<g transform="translate(512,250) scale(0.42) translate(-1000,-220)">'
                  + horizontal_logo_body(WHITE, 2000, 440) + '</g>')
    png(feature, f"{OUT}/play-store/feature-graphic-1024x500.png", 1024, 500)

    # Android: adaptive launcher icon (108dp, glyph inside the 66dp safe zone)
    s_fg = 0.128
    write(f"{RES}/drawable/ic_launcher_foreground.xml", vd([(TEAL, ring), (GOLD, dot)], s_fg))
    write(f"{RES}/drawable/ic_launcher_monochrome.xml", vd([("#FFFFFFFF", ring), ("#FFFFFFFF", dot)], s_fg))

    # Android 12+ splash icons (288dp canvas masked to a 192dp circle)
    s_tile = 0.0765   # whole tile fits inside the circular mask
    write(f"{RES}/drawable/ic_splash.xml", vd([(INK, tile), (TEAL, ring), (GOLD, dot)], s_tile))
    write(f"{RES}/drawable-night/ic_splash.xml", vd([(INK, tile), (TEAL, ring), (GOLD, dot)], s_tile))

    # Notification icon: white silhouette on 24dp
    write(f"{RES}/drawable/ic_notification.xml", vd([("#FFFFFFFF", ring), ("#FFFFFFFF", dot)], 0.0235, viewport=24))

    # In-app mark used on offline/lock screens
    write(f"{RES}/drawable/ic_brand_mark.xml", vd([(INK, tile), (TEAL, ring), (GOLD, dot)], 0.1375))

    # Legacy PNG launcher icons (Android 7.x)
    legacy = svg(1024, 1024, mark_body(transform="translate(512,512) scale(1.26) translate(-512,-512)"))
    for d, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        png(legacy, f"{RES}/mipmap-{d}/ic_launcher.png", px)
        round_svg = svg(1024, 1024, f'<circle cx="512" cy="512" r="500" fill="{INK}"/>'
                        + mark_body(tile=False, transform="translate(512,512) scale(1.2) translate(-512,-512)"))
        png(round_svg, f"{RES}/mipmap-{d}/ic_launcher_round.png", px)
    print("Brand assets generated.")


if __name__ == "__main__":
    main()
