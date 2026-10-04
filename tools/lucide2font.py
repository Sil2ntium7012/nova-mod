#!/usr/bin/env python3
"""Lucide SVG(획·둥근 끝) → icons.ttf 글리프(채운 윤곽).

49-86차(8-3·8-5·8-9·8-10): 손으로 그린 글리프 대신 실제 Lucide 픽토그램을 쓴다.
49-92차(사용자: "안티앨리어싱도 안 되고 삐죽삐죽 튀어나오고 너무 두껍다"): 획을 **캡슐 여러 개를 겹쳐** 흉내 내던 것을
버리고 shapely로 **진짜 스트로크 윤곽**(둥근 끝·둥근 이음, 합집합, 구멍 포함)을 만든다. 겹친 윤곽이 하나도 없어
래스터라이저가 겹침 경계에서 울퉁불퉁해지지 않고, 곡선은 32분할이라 매끈하다. 획 굵기는 2 → **1.6**(24 격자 기준).

쓰는 법
  python3 tools/lucide2font.py build <icons.ttf> <iconmap.json> [lucide-icons-dir]
    iconmap.json: {"names": {"lucide-name": codepoint, ...}}  (tools/iconmap.json)
  python3 tools/lucide2font.py sheet <icons.ttf> <out.png>     # 확인용 컨택트시트

Lucide SVG는 npm `lucide-static` 패키지의 icons/ 폴더(기본 /tmp/lucide/package/icons). fonttools · shapely · svgpathtools 필요.
"""
import json
import math
import re
import sys
import xml.etree.ElementTree as ET

from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont
from shapely.geometry import LineString, Point, Polygon
from shapely.ops import unary_union
from svgpathtools import Line, parse_path

GRID = 24.0
UNIT = 1000.0 / GRID
STROKE = 1.6            # Lucide 기본은 2 - 화면 24px에서 1.6px이 정갈하다(사용자: "너무 두껍다")
RES = 8                 # 원 하나 = 32점


def P(x, y):
    """SVG 좌표(왼쪽 위 원점, y 아래로) → 폰트 좌표."""
    return (x * UNIT, (GRID - y) * UNIT)


def flatten_subpaths(d, samples=16):
    """SVG path d → [(points, closed)] (SVG 좌표)."""
    path = parse_path(d)
    subs = []
    cur = []
    start = None
    closed = False
    prev_end = None

    def pt(c):
        return (c.real, c.imag)

    for seg in path:
        s = pt(seg.start)
        if prev_end is None or abs(seg.start - prev_end) > 1e-4:
            if cur:
                subs.append((cur, closed))
            cur = [s]
            start = seg.start
            closed = False
        if isinstance(seg, Line):
            cur.append(pt(seg.end))
        else:
            for k in range(1, samples + 1):
                cur.append(pt(seg.point(k / samples)))
        prev_end = seg.end
        if abs(seg.end - start) < 1e-3 and len(cur) > 2:
            closed = True
    if cur:
        subs.append((cur, closed))
    out = []
    for pts, cl in subs:
        dd = [pts[0]]
        for p in pts[1:]:
            if abs(p[0] - dd[-1][0]) > 1e-4 or abs(p[1] - dd[-1][1]) > 1e-4:
                dd.append(p)
        out.append((dd, cl))
    return out


def stroke(pts, closed):
    """점열 → 굵기 STROKE의 스트로크 영역(둥근 끝·둥근 이음)."""
    if len(pts) == 1:
        return Point(pts[0]).buffer(STROKE / 2, resolution=RES)
    if closed and len(pts) >= 3:
        ring = list(pts) + [pts[0]]
        return LineString(ring).buffer(STROKE / 2, resolution=RES, join_style=1)
    return LineString(pts).buffer(STROKE / 2, resolution=RES, cap_style=1, join_style=1)


def svg_to_shape(svgpath):
    """SVG → 채워진 영역(shapely, SVG 좌표)."""
    root = ET.parse(svgpath).getroot()
    parts = []

    def tag(e):
        return e.tag.split('}')[-1]

    for e in root.iter():
        t = tag(e)
        if t == 'path':
            for pts, cl in flatten_subpaths(e.attrib['d']):
                parts.append(stroke(pts, cl))
        elif t == 'line':
            parts.append(stroke([(float(e.attrib['x1']), float(e.attrib['y1'])),
                                 (float(e.attrib['x2']), float(e.attrib['y2']))], False))
        elif t in ('polyline', 'polygon'):
            nums = [float(v) for v in re.findall(r'-?[\d.]+', e.attrib.get('points', ''))]
            parts.append(stroke(list(zip(nums[0::2], nums[1::2])), t == 'polygon'))
        elif t == 'circle':
            cx, cy, r = float(e.attrib['cx']), float(e.attrib['cy']), float(e.attrib['r'])
            parts.append(Point(cx, cy).buffer(r, resolution=RES).exterior.buffer(STROKE / 2, resolution=RES))
        elif t == 'ellipse':
            cx, cy = float(e.attrib['cx']), float(e.attrib['cy'])
            rx, ry = float(e.attrib['rx']), float(e.attrib['ry'])
            ring = [(cx + rx * math.cos(a), cy + ry * math.sin(a)) for a in
                    [2 * math.pi * k / 48 for k in range(48)]]
            parts.append(stroke(ring, True))
        elif t == 'rect':
            x, y = float(e.attrib['x']), float(e.attrib['y'])
            w, h = float(e.attrib['width']), float(e.attrib['height'])
            rr = float(e.attrib.get('rx', 0) or 0)
            if rr > 0:
                box = Polygon([(x + rr, y + rr), (x + w - rr, y + rr), (x + w - rr, y + h - rr), (x + rr, y + h - rr)])
                outline = box.buffer(rr, resolution=RES, join_style=1).exterior
            else:
                outline = Polygon([(x, y), (x + w, y), (x + w, y + h), (x, y + h)]).exterior
            parts.append(outline.buffer(STROKE / 2, resolution=RES, join_style=1))
    return unary_union(parts)


def orient(pts, clockwise):
    """폰트 좌표 점열을 시계(바깥) / 반시계(구멍)로."""
    area = 0.0
    for i in range(len(pts)):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % len(pts)]
        area += x1 * y2 - x2 * y1
    is_cw = area < 0
    return pts if is_cw == clockwise else pts[::-1]


def ring_pts(coords):
    pts = [P(x, y) for x, y in coords]
    if len(pts) > 1 and abs(pts[0][0] - pts[-1][0]) < 1e-6 and abs(pts[0][1] - pts[-1][1]) < 1e-6:
        pts = pts[:-1]
    return pts


def shape_to_glyph(shape):
    pen = TTGlyphPen(None)
    polys = list(shape.geoms) if hasattr(shape, 'geoms') else [shape]
    for poly in polys:
        if poly.is_empty or not isinstance(poly, Polygon):
            continue
        rings = [(poly.exterior.coords, True)] + [(h.coords, False) for h in poly.interiors]
        for coords, outer in rings:
            pts = orient(ring_pts(coords), outer)
            if len(pts) < 3:
                continue
            pen.moveTo((round(pts[0][0]), round(pts[0][1])))
            for p in pts[1:]:
                pen.lineTo((round(p[0]), round(p[1])))
            pen.closePath()
    return pen.glyph()


def center_all(font):
    """모든 글리프 잉크를 폭 1000의 가로 가운데로(lsb = xMin) - mkicons.py 49-77차와 같은 규칙."""
    glyf = font["glyf"]
    hmtx = font["hmtx"]
    for name in font.getGlyphOrder():
        g = glyf[name]
        if g.numberOfContours <= 0:
            continue
        g.recalcBounds(glyf)
        adv, _ = hmtx[name]
        dx = int(round(adv / 2 - (g.xMin + g.xMax) / 2))
        if dx != 0:
            g.coordinates.translate((dx, 0))
            g.recalcBounds(glyf)
        hmtx[name] = (adv, g.xMin)


def build(ttf, mapfile, icons_dir):
    names = json.load(open(mapfile, encoding='utf-8'))['names']
    font = TTFont(ttf)
    glyf = font['glyf']
    hmtx = font['hmtx']
    cmaps = [t for t in font['cmap'].tables if t.isUnicode()]
    order = font.getGlyphOrder()
    n = 0
    for name, cp in names.items():
        shape = svg_to_shape('%s/%s.svg' % (icons_dir, name))
        gn = 'uni%04X' % cp
        glyf[gn] = shape_to_glyph(shape)
        hmtx[gn] = (1000, 0)
        if gn not in order:
            order.append(gn)
        for t in cmaps:
            t.cmap[cp] = gn
        n += 1
    font.setGlyphOrder(order)
    font['maxp'].numGlyphs = len(order)
    center_all(font)
    font.save(ttf)
    print('글리프', n, '→', ttf)


def sheet(ttf, out, size=24, cols=16):
    from PIL import Image, ImageDraw, ImageFont
    font = TTFont(ttf)
    cps = sorted([t for t in font['cmap'].tables if t.isUnicode()][0].cmap)
    cell = size + 16
    img = Image.new('L', (cols * cell, ((len(cps) + cols - 1) // cols) * cell), 0)
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(ttf, size)
    for i, cp in enumerate(cps):
        d.text(((i % cols) * cell + 8, (i // cols) * cell + 4), chr(cp), font=f, fill=255)
    img.save(out)
    print(out, img.size)


if __name__ == '__main__':
    if len(sys.argv) >= 4 and sys.argv[1] == 'build':
        build(sys.argv[2], sys.argv[3], sys.argv[4] if len(sys.argv) > 4 else '/tmp/lucide/package/icons')
    elif len(sys.argv) >= 4 and sys.argv[1] == 'sheet':
        sheet(sys.argv[2], sys.argv[3])
    else:
        sys.exit(__doc__)
