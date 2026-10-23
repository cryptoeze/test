#!/usr/bin/env python3
"""Generates every TikTokpk brand asset from one set of vector geometry.

Outputs:
  branding/tiktokpk/                     master SVGs + HD PNGs (logo, icon, Play Store graphics)
  app/src/tiktokpk/res/drawable*/        Android vector drawables (launcher, splash, in-app mark)
  app/src/tiktokpk/res/mipmap-*/         legacy PNG launcher icons (Android 7.x)

Run:  pip install fonttools cairosvg && python3 tools/branding/tiktokpk.py
"""
import math
import os

import cairosvg

from generate import wordmark, wordmark_width, f

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "branding", "tiktokpk")
RES = os.path.join(ROOT, "app", "src", "tiktokpk", "res")

CYAN, BLUE = "#5FE6FF", "#1F5BFF"
TRI_LIGHT, TRI_DARK = "#8FF0FF", "#2F7BFF"
INK = "#0B1220"
WHITE = "#FFFFFF"
ACCENT = "#2F8BFF"

# ---- Mark geometry (1024 grid) ------------------------------------------------
R, RING_W, STEM_W = 300, 54, 64


def _pt(r, deg):
    return 512 + r * math.cos(math.radians(deg)), 512 + r * math.sin(math.radians(deg))


def ring_d():
    x1, y1 = _pt(R, -50)
    x2, y2 = _pt(R, -80)
    return f"M{f(x1)},{f(y1)}A{R},{R} 0 1 1 {f(x2)},{f(y2)}"


T_BAR = "M368,352L572,352"
T_STEM = "M470,282L470,610C470,690 540,716 612,684"
PLAY = "M578,440L722,522Q742,534 722,546L578,628Q558,640 558,617L558,451Q558,428 578,440Z"
TILE = "M300,140H724A160,160 0 0 1 884,300V724A160,160 0 0 1 724,884H300A160,160 0 0 1 140,724V300A160,160 0 0 1 300,140Z"

# Gradient spans in grid coordinates.
G_MAIN = (220, 200, 820, 840)
G_PLAY = (558, 428, 742, 640)


def svg_mark(transform="", tile=False):
    defs = (f'<defs><linearGradient id="g" gradientUnits="userSpaceOnUse" x1="{G_MAIN[0]}" y1="{G_MAIN[1]}" '
            f'x2="{G_MAIN[2]}" y2="{G_MAIN[3]}"><stop offset="0" stop-color="{CYAN}"/><stop offset="1" stop-color="{BLUE}"/>'
            f'</linearGradient><linearGradient id="t" gradientUnits="userSpaceOnUse" x1="{G_PLAY[0]}" y1="{G_PLAY[1]}" '
            f'x2="{G_PLAY[2]}" y2="{G_PLAY[3]}"><stop offset="0" stop-color="{TRI_LIGHT}"/><stop offset="1" stop-color="{TRI_DARK}"/>'
            f'</linearGradient></defs>')
    t = f'<path fill="{INK}" d="{TILE}"/>' if tile else ""
    stroke = 'fill="none" stroke="url(#g)" stroke-linecap="round" stroke-linejoin="round"'
    return (defs + f'<g transform="{transform}">{t}'
            f'<path d="{ring_d()}" {stroke} stroke-width="{RING_W}"/>'
            f'<path d="{PLAY}" fill="url(#t)"/>'
            f'<path d="{T_BAR} {T_STEM}" {stroke} stroke-width="{STEM_W}"/></g>')


def svg(w, h, body, bg=None):
    rect = f'<rect width="{w}" height="{h}" fill="{bg}"/>' if bg else ""
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {w} {h}" width="{w}" height="{h}">{rect}{body}</svg>'


def text_paths(text, x, baseline, cap, track, colors):
    paths, _ = wordmark(text, x, baseline, cap, track)
    return "".join(f'<path fill="{colors(i)}" d="{d}"/>' for i, d in enumerate(paths))


def logo_body(text_color, w, h):
    """Stacked logo: mark, TIKTOKPK, tagline (like the reference)."""
    mark = 600
    s = mark / 660.0
    body = svg_mark(transform=f"translate({f(w / 2)},{f(40 + mark / 2)}) scale({f(s)}) translate(-512,-512)")
    cap, track = 118.0, 12.0
    tw = wordmark_width("TIKTOKPK", cap, track)
    base = 40 + mark + 70 + cap
    body += text_paths("TIKTOKPK", (w - tw) / 2, base, cap, track, lambda i: text_color if i < 6 else ACCENT)
    tcap, ttrack = 34.0, 9.0
    tag = "EARNING VIDEO PLATFORM"
    tw2 = wordmark_width(tag, tcap, ttrack)
    body += text_paths(tag, (w - tw2) / 2, base + 50 + tcap, tcap, ttrack, lambda i: text_color)
    return body


def horizontal_body(text_color, w, h):
    mark = 330
    cap, track = 110.0, 10.0
    tw = wordmark_width("TIKTOKPK", cap, track)
    gap = 50
    x0 = (w - (mark + gap + tw)) / 2
    s = mark / 660.0
    body = svg_mark(transform=f"translate({f(x0 + mark / 2)},{f(h / 2)}) scale({f(s)}) translate(-512,-512)")
    body += text_paths("TIKTOKPK", x0 + mark + gap, h / 2 + cap / 2, cap, track,
                       lambda i: text_color if i < 6 else ACCENT)
    return body


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as fh:
        fh.write(content)


def png(svg_text, path, w, h=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    cairosvg.svg2png(bytestring=svg_text.encode(), write_to=path, output_width=w, output_height=h or w)


# ---- Android vector drawables ---------------------------------------------------
def _gradient(attr, g, c0, c1):
    return (f'\n            <aapt:attr name="{attr}">\n'
            f'                <gradient android:type="linear" android:startX="{g[0]}" android:startY="{g[1]}"'
            f' android:endX="{g[2]}" android:endY="{g[3]}">\n'
            f'                    <item android:offset="0" android:color="{c0}"/>\n'
            f'                    <item android:offset="1" android:color="{c1}"/>\n'
            f'                </gradient>\n            </aapt:attr>')


def vd(scale, viewport=108, tile=False, mono=None):
    t = viewport / 2 - 512 * scale

    def stroke_path(d, width):
        color = (f' android:strokeColor="{mono}">' if mono else ">" + _gradient("android:strokeColor", G_MAIN, "#FF5FE6FF", "#FF1F5BFF"))
        return (f'\n        <path android:pathData="{d}" android:fillColor="#00000000" android:strokeWidth="{width}"'
                f' android:strokeLineCap="round" android:strokeLineJoin="round"{color}\n        </path>')

    items = f'\n        <path android:fillColor="{INK}" android:pathData="{TILE}"/>' if tile else ""
    items += stroke_path(ring_d(), RING_W)
    if mono:
        items += f'\n        <path android:fillColor="{mono}" android:pathData="{PLAY}"/>'
    else:
        items += (f'\n        <path android:pathData="{PLAY}">'
                  + _gradient("android:fillColor", G_PLAY, "#FF8FF0FF", "#FF2F7BFF") + '\n        </path>')
    items += stroke_path(T_BAR + T_STEM, STEM_W)
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<!-- Generated by tools/branding/tiktokpk.py - do not edit by hand. -->\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    xmlns:aapt="http://schemas.android.com/aapt"\n'
            f'    android:width="{viewport}dp" android:height="{viewport}dp"\n'
            f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}">\n'
            f'    <group android:translateX="{f(t)}" android:translateY="{f(t)}"\n'
            f'        android:scaleX="{scale}" android:scaleY="{scale}">{items}\n'
            f'    </group>\n</vector>\n')


def main():
    # Masters
    icon = svg(1024, 1024, svg_mark(tile=True, transform="translate(512,512) scale(0.98) translate(-512,-512)"))
    write(f"{OUT}/tiktokpk-icon.svg", icon)
    write(f"{OUT}/tiktokpk-mark.svg", svg(1024, 1024, svg_mark()))
    png(icon, f"{OUT}/png/tiktokpk-icon-4096.png", 4096)
    for mode, color in (("light", INK), ("dark", WHITE)):
        s = svg(1200, 1000, logo_body(color, 1200, 1000))
        write(f"{OUT}/tiktokpk-logo-{mode}.svg", s)
        png(s, f"{OUT}/png/tiktokpk-logo-{mode}-4k.png", 3600, 3000)
        hz = svg(2000, 440, horizontal_body(color, 2000, 440))
        write(f"{OUT}/tiktokpk-logo-horizontal-{mode}.svg", hz)
        png(hz, f"{OUT}/png/tiktokpk-logo-horizontal-{mode}-4k.png", 4000, 880)

    # Play Store
    play = svg(1024, 1024, svg_mark(transform="translate(512,512) scale(1.25) translate(-512,-512)"), bg=INK)
    png(play, f"{OUT}/play-store/icon-512.png", 512)
    feature = svg(1024, 500,
                  '<defs><radialGradient id="bgr" cx="0.5" cy="0.45" r="0.7">'
                  f'<stop offset="0" stop-color="#16233A"/><stop offset="1" stop-color="{INK}"/></radialGradient></defs>'
                  '<rect width="1024" height="500" fill="url(#bgr)"/>'
                  '<g transform="translate(512,250) scale(0.44) translate(-1000,-220)">'
                  + horizontal_body(WHITE, 2000, 440) + '</g>')
    png(feature, f"{OUT}/play-store/feature-graphic-1024x500.png", 1024, 500)

    # Android launcher (adaptive): mark inside the 66dp safe zone
    write(f"{RES}/drawable/ic_launcher_foreground.xml", vd(0.0985))
    write(f"{RES}/drawable/ic_launcher_monochrome.xml", vd(0.0985, mono="#FFFFFFFF"))
    # Splash (288dp canvas): ring spans the same 152dp as the CryptoEze tile so the intro lines up.
    splash = vd(0.0872)
    write(f"{RES}/drawable/ic_splash.xml", splash)
    write(f"{RES}/drawable-night/ic_splash.xml", splash)
    write(f"{RES}/drawable/ic_brand_mark.xml", vd(0.1375, tile=True))
    write(f"{RES}/drawable/ic_notification.xml", vd(0.0235, viewport=24, mono="#FFFFFFFF"))

    legacy = svg(1024, 1024, svg_mark(tile=True, transform="translate(512,512) scale(1.26) translate(-512,-512)"))
    rnd = svg(1024, 1024, f'<circle cx="512" cy="512" r="500" fill="{INK}"/>'
              + svg_mark(transform="translate(512,512) scale(1.12) translate(-512,-512)"))
    for d, px in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        png(legacy, f"{RES}/mipmap-{d}/ic_launcher.png", px)
        png(rnd, f"{RES}/mipmap-{d}/ic_launcher_round.png", px)
    print("TikTokpk assets generated.")


if __name__ == "__main__":
    main()
