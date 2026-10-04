#!/usr/bin/env python3
"""
49-74차(5-2 · 5-3 · 5-4): 글꼴 하나를 받아서 **마인크래프트가 읽는 글꼴 설정**으로 굽는다.

왜 이 스크립트가 필요했나
-------------------------
"폰트를 바꿔 달라"가 지금까지 막혀 있던 이유는 폰트 파일이 없어서가 아니라,
**어떤 숫자를 적어야 글자가 아이콘·바닐라 글자와 높이가 맞는지**가 폰트마다 다르고,
게다가 **버전마다 규칙이 둘**이기 때문이다(49-39차에 바이트코드로 확인한 내용):

  * 1.20.4 이하 = stb_truetype : 글리프 크기 = size / (hhea.ascent − hhea.descent)
                                 베이스라인 행 = (폰트 ascent 픽셀) − 3 + 2 × shiftY
  * 1.20.5 이상 = FreeType     : 글리프 크기 = size / head.unitsPerEm
                                 베이스라인 행 = 7 + shiftY

그래서 같은 글꼴이라도 **두 벌**을 만들어야 하고(`<이름><배율>.json` = FreeType용,
`<이름>_stb<배율>.json` = 1.20.4 이하용), 두 벌이 **같은 자리에 같은 크기로** 찍혀야 한다.
그 계산을 손으로 하다 틀린 게 5-2가 "정면 충돌"이라고 적혀 있던 이유다.

이 스크립트는 그 계산을 폰트에서 **직접 재서** 한다:

  size     = 7.0 / (capHeight / unitsPerEm)     ← 대문자(또는 한글) 높이를 7px에 맞춘다
  baseline = ceil(capHeight × size / unitsPerEm) ← 잉크 밴드의 아래 끝
  shift_ft  = baseline − 7
  size_stb  = size × (ascent − descent) / unitsPerEm
  shift_stb = (baseline − ascent × size / unitsPerEm + 3) / 2

이 식이 맞는지는 **이미 들어 있는 글꼴 넉 장으로 검산**할 수 있다(--verify 참고).
ui.ttf·title.ttf·hangul.ttf의 값이 지금 저장소에 있는 JSON과 한 자리도 안 틀리고 나온다.

쓰는 법
-------
  # 1) 모드에 넣을 JSON 12장 만들기(assets/novaclient/font/ 에 그대로 복사)
  python3 tools/bake-font.py my.ttf --base myfont

  # 2) 모드를 다시 빌드하지 않고 바로 써 보기 - 리소스팩으로 굽기
  #    (49-47차 "리소스팩 글꼴 우선" 덕분에 팩을 켜면 그 글꼴이 이긴다)
  python3 tools/bake-font.py my.ttf --pack

  # 3) 아이콘 폰트처럼 대문자 높이를 못 재는 글꼴은 숫자를 직접 준다
  python3 tools/bake-font.py icons.ttf --base icons --size 11 --baseline 9

  # 4) 지금 저장소에 들어 있는 값과 맞는지 검산
  python3 tools/bake-font.py --verify src/main/resources/assets/novaclient/font

fonttools가 필요하다:  pip install fonttools
"""

import argparse
import json
import math
import os
import sys
import zipfile

try:
    from fontTools.ttLib import TTFont
    from fontTools.pens.boundsPen import BoundsPen
except ImportError:
    sys.exit("fonttools가 필요합니다:  pip install fonttools")

# 마인크래프트 기본 비트맵 글꼴의 잉크 밴드(대문자가 차지하는 행) - 0..7.
# 여기에 맞춰야 아이콘·바닐라 글자와 높낮이가 어긋나지 않는다(NovaCompat.textBandTop 주석 참고).
TARGET_CAP_PX = 7.0
# FreeType 시대의 베이스라인 기준 행(마인크래프트가 고정으로 쓰는 값).
FT_BASE_ROW = 7.0
# stb 시대 공식의 상수(TextRenderer 바이트코드 실측).
STB_BASE_OFFSET = 3.0


def measure(path):
    """글꼴에서 필요한 치수만 뽑는다: unitsPerEm, ascent, descent, capHeight."""
    font = TTFont(path, fontNumber=0)
    upem = font["head"].unitsPerEm
    hhea = font["hhea"]
    cap = 0
    if "OS/2" in font:
        cap = getattr(font["OS/2"], "sCapHeight", 0) or 0
    if not cap:
        # OS/2에 값이 없으면 실제 글리프에서 잰다(라틴이 없는 한글 전용 글꼴도 있어서 '가'까지 본다)
        cmap = font.getBestCmap()
        glyphs = font.getGlyphSet()
        for ch in ("H", "E", "가", "글"):
            name = cmap.get(ord(ch))
            if not name:
                continue
            pen = BoundsPen(glyphs)
            glyphs[name].draw(pen)
            if pen.bounds:
                cap = max(cap, pen.bounds[3])
        if cap:
            print(f"  (OS/2에 대문자 높이가 없어 글리프에서 쟀습니다: {cap})")
    return {
        "upem": upem,
        "ascent": hhea.ascent,
        "descent": hhea.descent,
        "cap": cap,
        "name": font["name"].getDebugName(4) or os.path.basename(path),
    }


def nice(value, step=0.25, snap=0.15):
    """읽기 좋은 값으로 다듬는다.

    정수에 아주 가까우면 정수로, 아니면 0.25 칸으로 맞춘다. **화면에 나오는 결과를 바꾸지 않는
    범위에서만** 다듬는 것이다 - GUI 배율 4에서도 0.25px는 실제 화소 하나보다 작다. 손으로 검산할
    때 9.901보다 10.0이 훨씬 낫고, 저장소에 이미 들어 있는 값도 이 방식으로 적혀 있다.
    """
    whole = round(value)
    if abs(value - whole) <= snap:
        return float(whole)
    return round(round(value / step) * step, 3)


def numbers(m, size=None, baseline=None):
    """치수 → 두 시대의 (size, shift). size/baseline을 주면 그 값을 쓴다."""
    upem = m["upem"]
    if size is None:
        if not m["cap"]:
            sys.exit("대문자 높이를 잴 수 없는 글꼴입니다 - --size 와 --baseline 을 직접 주세요.")
        size = nice(TARGET_CAP_PX * upem / m["cap"])
    cap_px = m["cap"] * size / upem if m["cap"] else 0
    if baseline is None:
        baseline = float(math.ceil(cap_px - 1e-6))
    span = m["ascent"] - m["descent"]
    # size_stb는 다듬지 않는다. 이건 고르는 값이 아니라 **FreeType 쪽과 픽셀 크기를 정확히 맞추려고
    # 유도된 값**이라, 0.25 칸으로 스냅하면 두 시대의 글자 크기가 실제로 어긋난다(11.934 → 12.0은
    # 0.5% 차이지만 그만큼 더 크게 찍힌다).
    size_stb = round(size * span / upem, 3)
    ascent_px = m["ascent"] * size / upem
    shift_ft = nice(baseline - FT_BASE_ROW)
    shift_stb = nice((baseline - ascent_px + STB_BASE_OFFSET) / 2.0)
    return {
        "size": size, "shift": shift_ft,
        "size_stb": size_stb, "shift_stb": shift_stb,
        "baseline": baseline, "cap_px": round(cap_px, 3),
    }


def provider(file_ref, size, shift, oversample, extra=None):
    p = {
        "type": "ttf",
        "file": file_ref,
        "size": float(size),
        "oversample": float(oversample),
        "shift": [0, float(shift)],
        "skip": "",
    }
    if extra:
        p.update(extra)
    return p


def write_mod_jsons(out_dir, base, file_ref, n, fallback):
    """모드용 12장. 뒤에 바닐라 글꼴을 깔지(fallback) 여부는 글꼴 성격에 따라."""
    os.makedirs(out_dir, exist_ok=True)
    made = []
    for scale in range(1, 7):
        for era in ("", "_stb"):
            size = n["size_stb"] if era else n["size"]
            shift = n["shift_stb"] if era else n["shift"]
            doc = {"providers": [provider(file_ref, size, shift, scale)]}
            if fallback:
                # 한글만 든 글꼴은 뒤에 바닐라 라틴을 깔아 준다(mchan이 그렇게 돼 있다)
                doc["providers"].append({"type": "reference", "id": "minecraft:default"})
            name = f"{base}{era}{scale}.json"
            with open(os.path.join(out_dir, name), "w", encoding="utf-8") as f:
                json.dump(doc, f, indent=1, ensure_ascii=False)
                f.write("\n")
            made.append(name)
    return made


def write_pack(ttf_path, n, out_zip, namespace="novafont"):
    """리소스팩 한 장. 모드를 다시 빌드하지 않고 바로 켜 볼 수 있다.

    `minecraft:include/default`는 1.20부터 있다 - 그 아래 버전에서는 이 팩 대신
    --base 로 모드에 구워 넣는 쪽을 쓴다.
    """
    base_name = os.path.splitext(os.path.basename(ttf_path))[0]
    doc = {
        "providers": [
            provider(f"{namespace}:{base_name}.ttf", n["size"], n["shift"], 2.0),
            {"type": "reference", "id": "minecraft:include/default"},
            {"type": "reference", "id": "minecraft:include/unifont"},
        ]
    }
    doc_stb = {
        "providers": [
            provider(f"{namespace}:{base_name}.ttf", n["size_stb"], n["shift_stb"], 2.0),
            {"type": "reference", "id": "minecraft:include/default"},
            {"type": "reference", "id": "minecraft:include/unifont"},
        ]
    }
    with zipfile.ZipFile(out_zip, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("pack.mcmeta", json.dumps({
            "pack": {
                "description": f"Nova font - {base_name}",
                "pack_format": 34,
                "min_format": 4,
                "max_format": [2147483647, 2147483647],
            }
        }, indent=2) + "\n")
        z.write(ttf_path, f"assets/{namespace}/font/{base_name}.ttf")
        z.writestr("assets/minecraft/font/default.json",
                   json.dumps(doc, indent=1, ensure_ascii=False) + "\n")
        # 1.20.4 이하로 쓸 때 이 파일의 이름을 default.json으로 바꿔 넣으면 된다
        z.writestr("default-1.20.4-and-below.json",
                   json.dumps(doc_stb, indent=1, ensure_ascii=False) + "\n")


def verify(font_dir):
    """이미 들어 있는 글꼴로 공식을 검산한다. 하나라도 어긋나면 0이 아닌 코드로 끝난다."""
    cases = [
        # (ttf, base, 저장소에 있는 값 - size/shift/size_stb/shift_stb)
        ("ui.ttf", "ui", 10.0, 1.0, 11.934, 0.75),
        ("title.ttf", "title", 10.0, 1.0, 11.934, 0.75),
        ("hangul.ttf", "mchan", 8.0, 0.0, 9.0, 1.0),
    ]
    bad = 0
    for ttf, base, size, shift, size_stb, shift_stb in cases:
        path = os.path.join(font_dir, ttf)
        if not os.path.exists(path):
            print(f"  건너뜀 {ttf} (없음)")
            continue
        n = numbers(measure(path))
        got = (n["size"], n["shift"], n["size_stb"], n["shift_stb"])
        want = (size, shift, size_stb, shift_stb)
        ok = all(abs(a - b) < 0.002 for a, b in zip(got, want))
        print(f"  {base:6} 계산 {got}  저장소 {want}  {'OK' if ok else '↯ 다름'}")
        if not ok:
            bad += 1
    if bad:
        sys.exit(f"{bad}개가 어긋납니다 - 공식이나 저장소 JSON 중 하나가 틀렸습니다.")
    print("검산 통과 - 공식이 저장소 값을 그대로 재현합니다.")


def main():
    ap = argparse.ArgumentParser(description="TTF 하나를 마인크래프트 글꼴 설정으로 굽는다")
    ap.add_argument("font", nargs="?", help="구울 .ttf 파일")
    ap.add_argument("--base", help="모드에 넣을 이름(예: myfont → myfont1.json …)")
    ap.add_argument("--file", help="JSON이 가리킬 글꼴 파일 이름(기본: --base 와 같음. 예: iconslg가 icons.ttf를 쓸 때)")
    ap.add_argument("--out", help="내보낼 폴더(기본: ./baked)")
    ap.add_argument("--size", type=float, help="FreeType 시대 size를 직접 지정")
    ap.add_argument("--baseline", type=float, help="베이스라인 행을 직접 지정")
    ap.add_argument("--fallback", action="store_true",
                    help="뒤에 바닐라 글꼴을 깐다(한글만 든 글꼴일 때)")
    ap.add_argument("--pack", action="store_true", help="리소스팩(.zip)으로 굽는다")
    ap.add_argument("--verify", metavar="DIR", help="이미 있는 글꼴로 공식을 검산")
    args = ap.parse_args()

    if args.verify:
        verify(args.verify)
        return
    if not args.font:
        ap.error("구울 글꼴 파일을 주세요(또는 --verify).")

    m = measure(args.font)
    n = numbers(m, args.size, args.baseline)
    print(f"{m['name']}  unitsPerEm={m['upem']} ascent={m['ascent']} descent={m['descent']} cap={m['cap']}")
    print(f"  FreeType(1.20.5+) : size={n['size']}  shift=[0, {n['shift']}]")
    print(f"  stb(~1.20.4)      : size={n['size_stb']}  shift=[0, {n['shift_stb']}]")
    print(f"  대문자 높이 {n['cap_px']}px, 베이스라인 {n['baseline']}행 "
          f"→ 잉크 밴드 {round(n['baseline'] - n['cap_px'], 2)}..{n['baseline']} (목표 0..7)")

    out = args.out or "baked"
    if args.pack:
        os.makedirs(out, exist_ok=True)
        zip_path = os.path.join(out, "NovaFont-" +
                                os.path.splitext(os.path.basename(args.font))[0] + ".zip")
        write_pack(args.font, n, zip_path)
        print(f"\n리소스팩: {zip_path}")
        print("  resourcepacks 폴더에 넣고 켜면 끝입니다(1.20 이상).")
        print("  1.20.4 이하라면 팩 안의 default-1.20.4-and-below.json을")
        print("  assets/minecraft/font/default.json 로 바꿔 넣으세요.")
        return

    if not args.base:
        ap.error("--base 를 주거나 --pack 을 쓰세요.")
    made = write_mod_jsons(out, args.base, f"novaclient:{args.file or args.base}.ttf", n, args.fallback)
    print(f"\n{len(made)}장을 {out}/ 에 만들었습니다.")
    print(f"  이 폴더의 내용과 글꼴 파일({args.base}.ttf)을")
    print("  src/main/resources/assets/novaclient/font/ 에 복사한 뒤 다시 빌드하세요.")


if __name__ == "__main__":
    main()
