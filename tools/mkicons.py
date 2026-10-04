#!/usr/bin/env python3
"""
49-76차(6-18): 아이콘 글리프를 **직접 그려서** icons.ttf에 넣는다.

49-53차에 24개를 넣을 때 쓴 스크립트는 대화 기록에만 남아 있었다(저장소에 없었다). 이번에 저장소에
둔다 - 다음에 아이콘 하나 바꿀 때 또 처음부터 만들지 않게.

그리는 법
--------
Lucide와 같은 규칙: **24×24 격자, 획 굵기 2, 둥근 끝**. 글리프는 획을 채운 윤곽(outline)이다.
여기서는 도형을 셋으로만 적는다:

  line(x1, y1, x2, y2)      두 점을 잇는 획(양 끝 둥글게)
  ring(cx, cy, r)           반지름 r인 원의 테두리(굵기 2)
  dot(cx, cy, r)            채운 원

폴리라인은 line 여러 개 + 꺾이는 자리에 dot(반지름 1)을 놓으면 Lucide의 둥근 이음과 같아진다.

⚠️ 채움은 non-zero다. 겹치는 윤곽은 **같은 방향**이면 합집합이 되고(그래서 획끼리 겹쳐도 된다),
ring은 바깥 원과 안쪽 원의 방향을 반대로 둬서 구멍을 낸다. "큰 원 − 작은 원"을 두 윤곽으로 두는데
작은 원이 큰 원 밖으로 나가면 나간 부분이 칠해진다(49-53차에 초승달이 그렇게 깨졌다) - ring은
동심원이라 안전하다.

쓰는 법
------
  python3 tools/mkicons.py src/main/resources/assets/novaclient/font/icons.ttf

이미 있는 코드 포인트는 덮어쓴다. fonttools가 필요하다.
"""

import math
import sys

from fontTools.ttLib import TTFont
from fontTools.pens.ttGlyphPen import TTGlyphPen

GRID = 24
UNIT = 1000.0 / GRID       # 격자 한 칸 = 41.67 유닛(upem 1000, ascent 1000, descent 0)
STROKE = 2.0               # 획 굵기(격자 단위)


def P(x, y):
    """격자 좌표(왼쪽 위 원점, y 아래로) → 폰트 좌표."""
    return (x * UNIT, (GRID - y) * UNIT)


def circle_pts(cx, cy, r, n=24, reverse=False):
    pts = []
    for i in range(n):
        a = 2 * math.pi * i / n
        pts.append(P(cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts[::-1] if reverse else pts


def capsule_pts(x1, y1, x2, y2, w=STROKE, n=8):
    """두 점을 잇는 굵기 w의 획 - 양 끝을 반원으로."""
    dx, dy = x2 - x1, y2 - y1
    L = math.hypot(dx, dy)
    if L < 1e-6:
        return circle_pts(x1, y1, w / 2)
    ux, uy = dx / L, dy / L
    nx, ny = -uy, ux
    h = w / 2
    pts = []
    # B 쪽 반원
    for i in range(n + 1):
        a = -math.pi / 2 + math.pi * i / n
        pts.append(P(x2 + (ux * math.cos(a) + nx * math.sin(a)) * h,
                     y2 + (uy * math.cos(a) + ny * math.sin(a)) * h))
    # A 쪽 반원
    for i in range(n + 1):
        a = math.pi / 2 + math.pi * i / n
        pts.append(P(x1 + (ux * math.cos(a) + nx * math.sin(a)) * h,
                     y1 + (uy * math.cos(a) + ny * math.sin(a)) * h))
    return pts


def orient(pts):
    """폰트 좌표에서 시계 방향(TrueType 바깥 윤곽 규칙)으로 맞춘다."""
    area = 0.0
    for i in range(len(pts)):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % len(pts)]
        area += x1 * y2 - x2 * y1
    return pts if area < 0 else pts[::-1]


class Icon:
    def __init__(self):
        self.contours = []

    def line(self, x1, y1, x2, y2):
        self.contours.append(orient(capsule_pts(x1, y1, x2, y2)))
        return self

    def poly(self, *pts):
        """꺾이는 폴리라인 - 마디마다 둥근 이음."""
        for i in range(len(pts) - 1):
            self.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1])
        for x, y in pts[1:-1]:
            self.dot(x, y, STROKE / 2)
        return self

    def dot(self, cx, cy, r):
        self.contours.append(orient(circle_pts(cx, cy, r)))
        return self

    def ring(self, cx, cy, r):
        outer = orient(circle_pts(cx, cy, r + STROKE / 2))
        inner = orient(circle_pts(cx, cy, r - STROKE / 2))[::-1]   # 반대 방향 = 구멍
        self.contours.append(outer)
        self.contours.append(inner)
        return self

    def rrect(self, x, y, w, h, r):
        """둥근 사각형 테두리 - 변 넷 + 모서리 호."""
        self.line(x + r, y, x + w - r, y)
        self.line(x + r, y + h, x + w - r, y + h)
        self.line(x, y + r, x, y + h - r)
        self.line(x + w, y + r, x + w, y + h - r)
        for cx, cy, a0 in ((x + r, y + r, math.pi), (x + w - r, y + r, 1.5 * math.pi),
                           (x + w - r, y + h - r, 0), (x + r, y + h - r, 0.5 * math.pi)):
            prev = None
            for i in range(7):
                a = a0 + (math.pi / 2) * i / 6
                px, py = cx + r * math.cos(a), cy + r * math.sin(a)
                if prev:
                    self.line(prev[0], prev[1], px, py)
                prev = (px, py)
        return self


def build(icon):
    pen = TTGlyphPen(None)
    for c in icon.contours:
        pen.moveTo(c[0])
        for p in c[1:]:
            pen.lineTo(p)
        pen.closePath()
    return pen.glyph()


# ---------------------------------------------------------------- 아이콘 정의

def elytra():
    """겉날개 - 몸통 하나 + 양쪽 날개. 순환 화살표(E145)는 "교체"만 말하고 날개가 없었다."""
    i = Icon()
    i.line(12, 4, 12, 19)                                    # 몸통
    i.poly((11, 6), (5, 5), (2.5, 11), (5, 18), (11, 17))     # 왼 날개(딱정벌레 껍질처럼 아래로 늘어진다)
    i.poly((13, 6), (19, 5), (21.5, 11), (19, 18), (13, 17))  # 오른 날개
    return i


def hotbar_swap():
    """핫바 교체 - 위아래 화살표 둘(⇅). 사용자: "화살표 같은 것 활용"."""
    i = Icon()
    i.line(8, 20, 8, 4)
    i.poly((4, 8), (8, 4), (12, 8))
    i.line(16, 4, 16, 20)
    i.poly((12, 16), (16, 20), (20, 16))
    return i


def mouse_tweaks():
    """마우스 트윅스 - 마우스 몸통 + 가운데 줄 + 오른쪽으로 끄는 화살표."""
    i = Icon()
    i.rrect(4, 3, 10, 18, 5)          # 몸통
    i.line(9, 3, 9, 10)               # 왼쪽/오른쪽 버튼 나누는 줄
    i.line(4, 10, 14, 10)             # 버튼과 몸통 경계
    i.line(16, 12, 22, 12)            # 끌기 화살표
    i.poly((19, 9), (22, 12), (19, 15))
    return i


def harvest():
    """작물 계산기 - 계산기. 밀 이삭(E902)은 뭔지 알아보기 어려웠다."""
    i = Icon()
    i.rrect(5, 2, 14, 20, 2)          # 몸통
    i.line(9, 6, 15, 6)               # 표시창(굵은 줄 하나)
    for row in (11, 15):
        for col in (9, 12, 15):
            i.dot(col, row, 1.1)      # 버튼
    i.line(9, 19, 15, 19)             # 아래 긴 버튼
    return i


def youtube_window():
    """49-80차(4-6) 유튜브 창 - 화면 위에 뜬 작은 창(둥근 네모) 안의 재생 삼각형."""
    i = Icon()
    i.rrect(2, 4, 20, 15, 3)          # 창
    i.poly((10, 8.5), (15.5, 11.5), (10, 14.5), (10, 8.5))   # ▶
    return i


ICONS = {
    0xE918: elytra,
    0xE919: hotbar_swap,
    0xE91A: mouse_tweaks,
    0xE91B: harvest,
    0xE91C: youtube_window,
}


def center_all(font):
    """49-77차: 모든 글리프의 잉크를 1000 폭의 가로 가운데로.

    원본 아이콘 폰트는 글리프마다 lsb=0으로 잉크가 왼쪽에 붙어 있었다(전원 0..833, 닫기 0..582 …).
    (26-12)/2 식으로 상자 가운데에 그려도 잉크가 왼쪽으로 1~3px 밀리던 이유(사용자: "아이콘들이 중앙에서
    왼쪽으로 밀려 있어"). 폭(advance)은 1000 그대로 두고 윤곽만 옮기므로 글자 폭 계산은 안 바뀐다.
    """
    glyf = font["glyf"]
    hmtx = font["hmtx"]
    moved = 0
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
        # lsb는 반드시 xMin과 같아야 한다 - 래스터라이저는 윤곽을 "왼쪽 끝 = lsb"에 놓으므로 윤곽이 가운데(42..958)에
        # 있어도 lsb=0이면 42만큼 왼쪽으로 밀려 그려진다. 원본 폰트가 전부 lsb=0이라 그게 진짜 원인이었다.
        if dx != 0 or hmtx[name][1] != g.xMin:
            hmtx[name] = (adv, g.xMin)
            moved += 1
    return moved


def main():
    if len(sys.argv) < 2:
        sys.exit("사용법: mkicons.py <icons.ttf> [--center-only]")
    path = sys.argv[1]
    font = TTFont(path)
    if "--center-only" in sys.argv:
        n = center_all(font)
        font.save(path)
        print("가운데로 옮긴 글리프:", n)
        return
    glyf = font["glyf"]
    hmtx = font["hmtx"]
    cmap_tables = [t for t in font["cmap"].tables if t.isUnicode()]
    order = font.getGlyphOrder()
    for cp, fn in ICONS.items():
        name = "uni%04X" % cp
        glyf[name] = build(fn())
        hmtx[name] = (1000, 0)
        if name not in order:
            order.append(name)
        for t in cmap_tables:
            t.cmap[cp] = name
    font.setGlyphOrder(order)
    if "maxp" in font:
        font["maxp"].numGlyphs = len(order)
    center_all(font)   # 49-77차: 새로 그린 것 포함 전부 가로 가운데로
    font.save(path)
    print("넣은 글리프:", ", ".join("U+%04X" % c for c in ICONS))


if __name__ == "__main__":
    main()
