package kr.lunaslight.mod.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 49-170차(사용자: "모든 기능들 직접 수정해서 꾸밀 수 있게 - 기능들 배경을 직접 픽셀로 그릴 수 있게"):
 * HUD 배경으로 쓰는 픽셀 그림. 설정 문자열 한 줄("w,h,AARRGGBB×w·h[,효과 한 자리×w·h]")로 저장하고, 상자 크기에 맞춰
 * <b>9분할</b>로 그린다 - 네 모서리(inset×inset)는 그대로, 변과 가운데는 늘리지 않고 <b>반복</b>(픽셀 그림은
 * 늘리면 뭉개진다). 매 프레임 픽셀마다 fill하면 무거우므로 (상자 크기별) 색이 같은 가로/세로 구간을 합친
 * 사각형 목록을 한 번 만들어 캐시한다.
 *
 * <p>49-174차(사용자: "무지개 블록이랑 빛나는 블록도"): 픽셀마다 <b>효과</b> 한 자리 - 0 없음, 1 무지개(키스트로크처럼
 * 시간과 자리에 따라 색이 흐름), 2 빛남(마크 인챈트처럼 그 색 위로 밝은 띠가 비스듬히 지나감), 3 반짝임(픽셀마다
 * 다른 박자로 밝아졌다 어두워짐). 효과 픽셀은 매 프레임 색이 달라 캐시 사각형에 넣지 않고 따로 찍는다.
 */
public final class PixelArt {

	public static final int FX_NONE = 0;
	public static final int FX_RAINBOW = 1;
	public static final int FX_GLOW = 2;
	public static final int FX_TWINKLE = 3;
	public static final String[] FX_LABELS = {"없음", "무지개", "빛남", "반짝임"};

	public final int w;
	public final int h;
	public final int[] px;    // ARGB, 0 = 투명
	public final byte[] fx;   // 효과(FX_*)

	public PixelArt(int w, int h) {
		this.w = w;
		this.h = h;
		this.px = new int[w * h];
		this.fx = new byte[w * h];
	}

	public PixelArt copy() {
		PixelArt a = new PixelArt(w, h);
		System.arraycopy(px, 0, a.px, 0, px.length);
		System.arraycopy(fx, 0, a.fx, 0, fx.length);
		return a;
	}

	public int get(int x, int y) {
		return x < 0 || y < 0 || x >= w || y >= h ? 0 : px[y * w + x];
	}

	public int effect(int x, int y) {
		return x < 0 || y < 0 || x >= w || y >= h ? 0 : fx[y * w + x];
	}

	public void set(int x, int y, int argb) {
		set(x, y, argb, FX_NONE);
	}

	public void set(int x, int y, int argb, int effect) {
		if (x >= 0 && y >= 0 && x < w && y < h) {
			px[y * w + x] = argb;
			fx[y * w + x] = (byte) (argb == 0 ? 0 : Math.max(0, Math.min(3, effect)));
			cacheKey = -1;
		}
	}

	public boolean isEmpty() {
		for (int v : px) {
			if ((v >>> 24) != 0) {
				return false;
			}
		}
		return true;
	}

	/** 모서리 크기(9분할). 짧은 변의 1/3(2~12). 그림이 HUD 상자와 같은 크기면 1:1로 그려지고, 상자가 커질 때만 가운데가 반복된다. */
	public int inset() {
		return Math.max(2, Math.min(12, Math.min(w, h) / 3));
	}

	/** 49-172차: 다른 크기로 옮긴 복사본(겹치는 부분은 그대로, 나머지는 9분할 규칙으로 채움). */
	public PixelArt resized(int nw, int nh) {
		PixelArt a = new PixelArt(nw, nh);
		if (w == nw && h == nh) {
			System.arraycopy(px, 0, a.px, 0, px.length);
			System.arraycopy(fx, 0, a.fx, 0, fx.length);
			return a;
		}
		cacheW = nw;
		cacheH = nh;
		for (int y = 0; y < nh; y++) {
			int sy = srcCoord(1, y, h);
			for (int x = 0; x < nw; x++) {
				int si = sy * w + srcCoord(0, x, w);
				a.px[y * nw + x] = px[si];
				a.fx[y * nw + x] = fx[si];
			}
		}
		cacheKey = -1;
		return a;
	}

	/** 49-172차: 기본 그림 = 지금 배경 설정과 같은 둥근 상자(반지름 4). 이 위에 덧그리며 꾸민다. */
	public static PixelArt roundedBox(int w, int h, int argb, int radius) {
		PixelArt a = new PixelArt(w, h);
		int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				double dx = x < r ? r - 0.5 - x : x >= w - r ? x - (w - r - 0.5) : 0;
				double dy = y < r ? r - 0.5 - y : y >= h - r ? y - (h - r - 0.5) : 0;
				if (dx * dx + dy * dy <= r * r) {
					a.px[y * w + x] = argb;
				}
			}
		}
		return a;
	}

	// ==================== 저장/읽기 ====================

	public String encode() {
		StringBuilder sb = new StringBuilder(16 + px.length * 9);
		sb.append(w).append(',').append(h).append(',');
		for (int v : px) {
			sb.append(String.format(Locale.ROOT, "%08X", v));
		}
		boolean anyFx = false;
		for (byte b : fx) {
			if (b != 0) {
				anyFx = true;
				break;
			}
		}
		if (anyFx) {
			sb.append(',');
			for (byte b : fx) {
				sb.append((char) ('0' + Math.max(0, Math.min(9, b))));
			}
		}
		return sb.toString();
	}

	/** 잘못된 문자열이면 null. */
	public static PixelArt decode(String s) {
		if (s == null || s.isEmpty()) {
			return null;
		}
		try {
			int c1 = s.indexOf(',');
			int c2 = s.indexOf(',', c1 + 1);
			int w = Integer.parseInt(s.substring(0, c1));
			int h = Integer.parseInt(s.substring(c1 + 1, c2));
			if (w < 2 || h < 2 || w > 600 || h > 300) {
				return null;
			}
			int c3 = s.indexOf(',', c2 + 1);
			String data = c3 < 0 ? s.substring(c2 + 1) : s.substring(c2 + 1, c3);
			if (data.length() != w * h * 8) {
				return null;
			}
			PixelArt a = new PixelArt(w, h);
			for (int i = 0; i < w * h; i++) {
				a.px[i] = (int) Long.parseLong(data.substring(i * 8, i * 8 + 8), 16);
			}
			if (c3 >= 0) {
				String f = s.substring(c3 + 1);
				for (int i = 0; i < w * h && i < f.length(); i++) {
					a.fx[i] = (byte) Math.max(0, Math.min(3, f.charAt(i) - '0'));
				}
			}
			return a;
		} catch (Throwable t) {
			return null;
		}
	}

	// ==================== 효과 색 ====================

	/** 효과가 걸린 픽셀의 이번 프레임 색. x, y = 상자 안 자리(반복돼도 화면 자리 기준이라 무늬가 이어진다). */
	public static int effectColor(int base, int effect, int x, int y, long now) {
		int a = (base >>> 24) & 0xFF;
		switch (effect) {
			case FX_RAINBOW -> {
				double seconds = (now % 600_000L) / 1000.0;
				float hue = (float) (seconds * 0.35) - (x + y * 0.35f) / 60f;
				hue -= (float) Math.floor(hue);
				return (a << 24) | (hsv(hue, 0.75f, 1f) & 0x00FFFFFF);
			}
			case FX_GLOW -> {
				// 인챈트 반짝임: 비스듬한 밝은 띠가 왼쪽 위 → 오른쪽 아래로 지나가고, 전체가 살짝 숨 쉰다
				double band = ((x - y) * 3.0 + now / 6.0) % 90.0;
				if (band < 0) {
					band += 90.0;
				}
				float k = (float) Math.max(0.0, 1.0 - Math.abs(band - 45.0) / 14.0);
				float pulse = 0.08f + 0.08f * (float) Math.sin(now / 420.0);
				return lerp(base, (a << 24) | 0xFFFFFF, Math.min(1f, k * 0.7f + pulse));
			}
			case FX_TWINKLE -> {
				int hsh = (x * 73856093) ^ (y * 19349663);
				double phase = (hsh & 0xFFFF) / 65535.0 * Math.PI * 2;
				float k = 0.5f + 0.5f * (float) Math.sin(now / 260.0 + phase);
				int dark = lerp(base, (a << 24), 0.35f);
				int light = lerp(base, (a << 24) | 0xFFFFFF, 0.45f);
				return lerp(dark, light, k);
			}
			default -> {
				return base;
			}
		}
	}

	private static int lerp(int c1, int c2, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int a = Math.round(((c1 >>> 24) & 0xFF) + (((c2 >>> 24) & 0xFF) - ((c1 >>> 24) & 0xFF)) * t);
		int r = Math.round(((c1 >> 16) & 0xFF) + (((c2 >> 16) & 0xFF) - ((c1 >> 16) & 0xFF)) * t);
		int g = Math.round(((c1 >> 8) & 0xFF) + (((c2 >> 8) & 0xFF) - ((c1 >> 8) & 0xFF)) * t);
		int b = Math.round((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	public static int hsv(float h, float s, float v) {
		h = (h % 1f + 1f) % 1f;
		float c = v * s;
		float hp = h * 6f;
		float x = c * (1 - Math.abs(hp % 2 - 1));
		float r = 0, g = 0, b = 0;
		switch ((int) hp) {
			case 0 -> { r = c; g = x; }
			case 1 -> { r = x; g = c; }
			case 2 -> { g = c; b = x; }
			case 3 -> { g = x; b = c; }
			case 4 -> { r = x; b = c; }
			default -> { r = c; b = x; }
		}
		float m = v - c;
		return 0xFF000000 | (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
	}

	// ==================== 그리기(9분할 반복) ====================

	private int cacheKey = -1;
	private int cacheW, cacheH;
	private int[] rects;      // x, y, w, h, argb 다섯 칸씩(효과 없는 픽셀)
	private int[] fxPixels;   // dstX, dstY, srcIndex 세 칸씩(효과 픽셀)

	private int srcCoord(int d, int dst, int size) {
		int in = inset();
		if (dst < in) {
			return dst;
		}
		int total = d == 0 ? cacheW : cacheH;
		if (dst >= total - in) {
			return Math.max(0, size - (total - dst));
		}
		int mid = size - in * 2;
		return mid <= 0 ? in : in + ((dst - in) % mid);
	}

	private void build(int bw, int bh) {
		cacheW = bw;
		cacheH = bh;
		List<int[]> out = new ArrayList<>();
		List<int[]> fxs = new ArrayList<>();
		int prevFrom = -1, prevTo = -1;
		for (int y = 0; y < bh; y++) {
			int sy = srcCoord(1, y, h);
			int from = out.size();
			int runStart = 0;
			int runColor = 0;
			boolean has = false;
			for (int x = 0; x <= bw; x++) {
				int c;
				if (x < bw) {
					int si = sy * w + srcCoord(0, x, w);
					c = px[si];
					if (fx[si] != 0 && (c >>> 24) != 0) {
						fxs.add(new int[]{x, y, si});
						c = 0;   // 효과 픽셀은 정적 목록에서 뺀다
					}
				} else {
					c = 0x1;   // 끝 표시
				}
				if (!has) {
					if (x < bw && (c >>> 24) != 0) {
						has = true;
						runStart = x;
						runColor = c;
					}
					continue;
				}
				if (x == bw || c != runColor) {
					out.add(new int[]{runStart, y, x - runStart, 1, runColor});
					has = x < bw && (c >>> 24) != 0;
					runStart = x;
					runColor = c;
				}
			}
			int to = out.size();
			if (prevFrom >= 0 && to - from == prevTo - prevFrom) {
				boolean same = true;
				for (int i = 0; i < to - from; i++) {
					int[] a = out.get(prevFrom + i), b = out.get(from + i);
					if (a[0] != b[0] || a[2] != b[2] || a[4] != b[4]) {
						same = false;
						break;
					}
				}
				if (same) {
					for (int i = 0; i < to - from; i++) {
						out.get(prevFrom + i)[3]++;
					}
					while (out.size() > from) {
						out.remove(out.size() - 1);
					}
					continue;
				}
			}
			prevFrom = from;
			prevTo = to;
		}
		rects = new int[out.size() * 5];
		for (int i = 0; i < out.size(); i++) {
			System.arraycopy(out.get(i), 0, rects, i * 5, 5);
		}
		fxPixels = new int[fxs.size() * 3];
		for (int i = 0; i < fxs.size(); i++) {
			System.arraycopy(fxs.get(i), 0, fxPixels, i * 3, 3);
		}
		cacheKey = bw * 100003 + bh;
	}

	/** (x, y, bw, bh) 상자를 이 그림으로 채운다. alpha는 곱하는 값(0~1). */
	public void draw(GuiGraphicsExtractor ctx, int x, int y, int bw, int bh, float alpha) {
		if (bw <= 0 || bh <= 0) {
			return;
		}
		int key = bw * 100003 + bh;
		if (rects == null || key != cacheKey) {
			build(bw, bh);
		}
		boolean mul = alpha < 0.999f;
		for (int i = 0; i < rects.length; i += 5) {
			int c = rects[i + 4];
			if (mul) {
				int a = Math.round(((c >>> 24) & 0xFF) * Math.max(0f, alpha));
				c = (a << 24) | (c & 0x00FFFFFF);
			}
			ctx.fill(x + rects[i], y + rects[i + 1], x + rects[i] + rects[i + 2], y + rects[i + 1] + rects[i + 3], c);
		}
		if (fxPixels.length > 0) {
			long now = System.currentTimeMillis();
			for (int i = 0; i < fxPixels.length; i += 3) {
				int dx = fxPixels[i], dy = fxPixels[i + 1], si = fxPixels[i + 2];
				int c = effectColor(px[si], fx[si], dx, dy, now);
				if (mul) {
					int a = Math.round(((c >>> 24) & 0xFF) * Math.max(0f, alpha));
					c = (a << 24) | (c & 0x00FFFFFF);
				}
				ctx.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, c);
			}
		}
	}

	/** 편집기/미리보기용: 픽셀 한 칸을 cell 크기로(1:1이 아닌 확대). 효과도 살아 움직인다. */
	public void drawScaled(GuiGraphicsExtractor ctx, int x, int y, int cell) {
		long now = System.currentTimeMillis();
		for (int yy = 0; yy < h; yy++) {
			for (int xx = 0; xx < w; xx++) {
				int i = yy * w + xx;
				int c = px[i];
				if ((c >>> 24) != 0) {
					if (fx[i] != 0) {
						c = effectColor(c, fx[i], xx, yy, now);
					}
					ctx.fill(x + xx * cell, y + yy * cell, x + xx * cell + cell, y + yy * cell + cell, c);
				}
			}
		}
	}

	/** 채우기(같은 색·효과로 이어진 영역). */
	public void flood(int x, int y, int argb, int effect) {
		flood(x, y, argb, effect, 0, 0, 0, 0);
	}

	/** 49-175차: 가운데 네모 칸(lx, ly, lw, lh)은 안 넘어가는 채우기(편집기의 내용 영역 잠금용). lw/lh가 0이면 막는 칸 없음. */
	public void flood(int x, int y, int argb, int effect, int lx, int ly, int lw, int lh) {
		if (lw > 0 && lh > 0 && x >= lx && y >= ly && x < lx + lw && y < ly + lh) {
			return;
		}
		int target = get(x, y);
		int targetFx = effect(x, y);
		if (x < 0 || y < 0 || x >= w || y >= h || (target == argb && targetFx == effect)) {
			return;
		}
		java.util.ArrayDeque<int[]> q = new java.util.ArrayDeque<>();
		q.add(new int[]{x, y});
		while (!q.isEmpty()) {
			int[] p = q.poll();
			int cx = p[0], cy = p[1];
			if (cx < 0 || cy < 0 || cx >= w || cy >= h) {
				continue;
			}
			if (lw > 0 && lh > 0 && cx >= lx && cy >= ly && cx < lx + lw && cy < ly + lh) {
				continue;
			}
			int i = cy * w + cx;
			if (px[i] != target || fx[i] != targetFx) {
				continue;
			}
			px[i] = argb;
			fx[i] = (byte) (argb == 0 ? 0 : effect);
			q.add(new int[]{cx + 1, cy});
			q.add(new int[]{cx - 1, cy});
			q.add(new int[]{cx, cy + 1});
			q.add(new int[]{cx, cy - 1});
		}
		cacheKey = -1;
	}

	/** 49-175차: 네모 칸 전체를 한 색(과 효과)으로. 늘어나는 가운데를 한 색으로 칠할 때 - 한 색이라 늘어나도 안 깨진다. */
	public void fillRect(int x, int y, int rw, int rh, int argb, int effect) {
		for (int yy = Math.max(0, y); yy < Math.min(h, y + rh); yy++) {
			for (int xx = Math.max(0, x); xx < Math.min(w, x + rw); xx++) {
				int i = yy * w + xx;
				px[i] = argb;
				fx[i] = (byte) (argb == 0 ? 0 : effect);
			}
		}
		cacheKey = -1;
	}
}
