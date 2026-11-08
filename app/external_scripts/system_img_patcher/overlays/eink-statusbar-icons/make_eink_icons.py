"""Copies the SettingsLib signal and Wi-Fi drawables into this overlay without translucent parts, which e-ink
dithers into grey speckles. An empty signal bar becomes a solid dot as wide as the bar, so the count still shows;
a thin outline was tried and comes out uneven, because the 17 dp icon is a fractional number of pixels wide on
the A9. Other translucent parts (Wi-Fi arcs, call quality) are dropped.

Usage: python3 make_eink_icons.py <lineage tree>/frameworks/base/packages/SettingsLib/res/drawable
"""
import re
import sys
from pathlib import Path

PATTERNS = ("ic_mobile_*.xml", "ic_wifi_*.xml")
SIGNAL_BARS = re.compile(r"ic_mobile_\d_\d_bar(_error)?\.xml")
TRANSLUCENT_PATH = re.compile(r'<path\b(?:(?!/>).)*?android:fillAlpha="[\d.]+"(?:(?!/>).)*?/>', re.S)
POINT = re.compile(r"(-?\d*\.?\d+),(-?\d*\.?\d+)")


def dot(path):
    data = re.search(r'android:pathData="([^"]+)"', path).group(1)
    xs, ys = zip(*((float(x), float(y)) for x, y in POINT.findall(data)))
    left, right, bottom = min(xs), max(xs), max(ys)
    radius = (right - left) / 2
    centre = bottom - radius
    circle = (f"M{left:g},{centre:g}a{radius:g},{radius:g} 0 1,0 {2 * radius:g},0"
              f"a{radius:g},{radius:g} 0 1,0 {-2 * radius:g},0Z")
    without_alpha = re.sub(r'\s*android:fillAlpha="[\d.]+"', "", path)
    return without_alpha.replace(data, circle)


def converted(name, text):
    if SIGNAL_BARS.fullmatch(name):
        return TRANSLUCENT_PATH.sub(lambda m: dot(m.group(0)), text)
    return TRANSLUCENT_PATH.sub("", text)


def main():
    source = Path(sys.argv[1])
    target = Path(__file__).parent / "res" / "drawable"
    target.mkdir(parents=True, exist_ok=True)
    for pattern in PATTERNS:
        for path in sorted(source.glob(pattern)):
            text = path.read_text()
            if "fillAlpha" in text:
                (target / path.name).write_text(converted(path.name, text))
                print(path.name)


if __name__ == "__main__":
    main()
