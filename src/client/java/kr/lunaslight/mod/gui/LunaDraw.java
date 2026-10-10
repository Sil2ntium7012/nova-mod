package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.HashMap;
import java.util.Map;

/**
 * 47~48차: 설정 화면/HUD 편집기 공용 그리기 도우미 - 페더 클라이언트 느낌의 "둥근 모서리 +
 * 반투명 검정 + 부드러운 폰트 + 애니메이션".
 *
 * 48차부터 둥근 모서리는 안티앨리어싱 원 텍스처(LunaGfx.drawRound)로 그리고, 텍스트는 동봉 TTF
 * 폰트(LunaGfx.text)로 그림. 텍스처/폰트 해석이 실패한 버전에서는 자동으로 47차의 fill 방식으로 폴백.
 */
public final class LunaDraw {
	private LunaDraw() {
	}

	// ---- 팔레트 ----
	// 49-19차: "분위기를 더 어둡게" - 패널/카드/트랙을 한 단계씩 더 내리고 뒷배경 막도 진하게.
	// 밝기 차이는 유지해서 층(패널 < 카드 < 호버)이 여전히 구분되게 함.
	public static final int OVERLAY = 0x8C000000;          // 뒤 게임 화면이 비치는 어두운 막
	// 49-40차: 배경(패널/카드/글자)도 런처 "테마"(블랙 & 화이트·아쿠아·스카이…)를 따라가므로 상수가 아니라
	// 갈아끼울 수 있는 값 - LunaTheme.refresh()가 전부 갱신한다. 호출부는 그대로 LunaDraw.PANEL 등을 쓰면 된다.
	// (static final로 두면 다른 클래스의 상수 식에 값이 박혀 버려서 안 바뀐다 - 반드시 non-final)
	public static int PANEL = LunaTheme.PANEL;            // 메인 패널(거의 검정)
	public static int PANEL_BORDER = LunaTheme.PANEL_BORDER_LIVE;
	public static int SIDEBAR = LunaTheme.SIDEBAR;
	public static int CARD = LunaTheme.CARD;
	public static int CARD_HOVER = LunaTheme.CARD_HOVER;
	public static int CARD_BORDER = LunaTheme.CARD_BORDER;
	// 49-29차: 테마 색(클라이언트에서 장착한 색)을 따라가므로 상수가 아니라 갈아끼울 수 있는 값.
	// LunaTheme.refresh()가 두 값을 갱신한다 - 호출부는 그대로 LunaDraw.ACCENT를 쓰면 된다.
	public static int ACCENT = LunaTheme.ACCENT;
	public static int ACCENT_SOFT = LunaTheme.ACCENT_SOFT; // 선택된 카테고리 배경 등(아주 은은하게)
	public static final int ACCENT_DIM = 0xFF6A7076;
	public static int TRACK = LunaTheme.TRACK;
	public static final int KNOB = 0xFFF4F4F8;
	public static int TEXT = LunaTheme.TEXT;
	public static int TEXT_SUB = LunaTheme.TEXT_SUB;
	public static int TEXT_DIM = LunaTheme.TEXT_DIM;
	/** 타이틀·일시정지의 반투명 버튼(루나식) - 패널색에 알파만 다르게, 테마를 따라감. */
	public static int BTN_BG = LunaTheme.BTN_BG;
	public static int BTN_BG_HOVER = LunaTheme.BTN_BG_HOVER;

	/** 화면 전체 페이드 등에 쓰는 전역 알파 배수(0~1). 모든 헬퍼가 색에 곱함. */
	private static float alphaMul = 1f;

	public static void setAlpha(float a) {
		alphaMul = Math.max(0f, Math.min(1f, a));
	}

	public static float alpha() {
		return alphaMul;
	}

	// 49-22차: "UI 둥근 정도 심함 - 확실히 넣거나 빼기" → 모든 둥근 모서리를 최대 4px로 통일(알약 포함).
	// 카드/버튼/패널/모달이 제각각 5~10px이던 것을 한 규격으로 - 둥글되 과하지 않게.
	public static final int MAX_RADIUS = 4;

	public static int radius(int requested) {
		if (requested <= 0) {
			return 0;
		}
		if (softNow()) {
			return Math.min(requested + SOFT_EXTRA, SOFT_MAX_RADIUS);
		}
		return Math.min(requested, MAX_RADIUS);
	}

	// 49-161차(사용자: "UI좀 조금 더 다듬어줄 수 없어? 너무 네모가 보여 클라이언트처럼 부드럽게", 고른 것: 설정 화면 전체 +
	// 켜기/끄기 스위치, 둥근 정도 중간 6px): 루나 설정 화면들(일시정지, 타이틀 빼고)을 그리는 동안에만 모든 모서리를
	// 2px 더 둥글게(최대 6). 테두리 상자(바깥 4 / 안 3)는 6 / 5가 되어 두께가 그대로 맞는다.
	// HUD 상자와 그 미리보기, 편집기 샘플은 예전 모양 그대로 - Module이 그 동안 beginHud()/endHud()로 막는다
	// (설정 화면 뒤에 그려지는 게임 HUD도 마찬가지).
	public static final int SOFT_EXTRA = 2;
	public static final int SOFT_MAX_RADIUS = 6;
	private static int hudDepth;
	private static Class<?> softClass;
	private static boolean softClassResult;

	public static void beginHud() {
		hudDepth++;
	}

	public static void endHud() {
		if (hudDepth > 0) {
			hudDepth--;
		}
	}

	private static boolean softNow() {
		if (hudDepth > 0) {
			return false;
		}
		Object screen;
		try {
			screen = net.minecraft.client.MinecraftClient.getInstance().currentScreen;
		} catch (Throwable t) {
			return false;
		}
		if (screen == null) {
			return false;
		}
		Class<?> c = screen.getClass();
		if (c != softClass) {
			String n = c.getName();
			softClassResult = n.startsWith("kr.lunaslight.mod.gui.")
					&& !n.endsWith(".LunaPauseScreen") && !n.endsWith(".LunaTitleScreen");
			softClass = c;
		}
		return softClassResult;
	}

	/**
	 * 49-161차: 알약(양 끝이 반원). 모서리 제한(MAX/SOFT) 없이 짧은 변의 절반을 반지름으로 쓴다 - 켜기/끄기 스위치 트랙.
	 */
	public static void pill(DrawContext ctx, int x, int y, int w, int h, int color) {
		LunaGfx.layerBreak(ctx);   // 49-292차
		if (w <= 0 || h <= 0) {
			return;
		}
		color = applyAlpha(color);
		int r = Math.min(w, h) / 2;
		if (r > 0 && LunaGfx.drawRound(ctx, x, y, r, r, 0, 0, 64, 64, color)
			&& LunaGfx.drawRound(ctx, x + w - r, y, r, r, 64, 0, 64, 64, color)
			&& LunaGfx.drawRound(ctx, x, y + h - r, r, r, 0, 64, 64, 64, color)
			&& LunaGfx.drawRound(ctx, x + w - r, y + h - r, r, r, 64, 64, 64, 64, color)) {
			ctx.fill(x + r, y, x + w - r, y + h, color);
			ctx.fill(x, y + r, x + r, y + h - r, color);
			ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
			return;
		}
		ctx.fill(x, y + r, x + w, y + h - r, color);
		for (int dy = 0; dy < r; dy++) {
			double cy = r - dy - 0.5;
			int inset = r - (int) Math.floor(Math.sqrt(r * (double) r - cy * cy));
			ctx.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
			ctx.fill(x + inset, y + h - dy - 1, x + w - inset, y + h - dy, color);
		}
	}

	public static int applyAlpha(int argb) {
		if (alphaMul >= 0.999f) {
			return argb;
		}
		int a = Math.round(((argb >>> 24) & 0xFF) * alphaMul);
		return (argb & 0x00FFFFFF) | (a << 24);
	}

	// =====================================================================
	// 도형
	// =====================================================================

	/** 둥근 사각형(채움). radius가 0이면 일반 사각형. */
	public static void roundRect(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
		LunaGfx.layerBreak(ctx);   // 49-292차
		if (w <= 0 || h <= 0) {
			return;
		}
		color = applyAlpha(color);
		int r = Math.min(radius(radius), Math.min(w, h) / 2);
		if (r <= 0) {
			ctx.fill(x, y, x + w, y + h, color);
			return;
		}
		// 부드러운 텍스처 모서리(4개) + 몸통 fill
		if (LunaGfx.drawRound(ctx, x, y, r, r, 0, 0, 64, 64, color)
			&& LunaGfx.drawRound(ctx, x + w - r, y, r, r, 64, 0, 64, 64, color)
			&& LunaGfx.drawRound(ctx, x, y + h - r, r, r, 0, 64, 64, 64, color)
			&& LunaGfx.drawRound(ctx, x + w - r, y + h - r, r, r, 64, 64, 64, 64, color)) {
			ctx.fill(x + r, y, x + w - r, y + h, color);
			ctx.fill(x, y + r, x + r, y + h - r, color);
			ctx.fill(x + w - r, y + r, x + w, y + h - r, color);
			return;
		}
		// 폴백: 원의 방정식으로 줄마다 채움
		ctx.fill(x, y + r, x + w, y + h - r, color);
		for (int dy = 0; dy < r; dy++) {
			double cy = r - dy - 0.5;
			int inset = r - (int) Math.floor(Math.sqrt(r * (double) r - cy * cy));
			ctx.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
			ctx.fill(x + inset, y + h - dy - 1, x + w - inset, y + h - dy, color);
		}
	}

	/**
	 * 49-195차: 위쪽 두 모서리만 둥근 사각형(아래는 직각). 제목 줄처럼 아래 본문과 이어 붙이는 띠에 쓴다 - 예전엔 스코어보드
	 * 제목 띠를 둥근 사각형으로 r만큼 더 길게 그린 뒤 아래를 투명(0)으로 "지우려" 해서 지워지지 않고 첫 줄까지 덮었다.
	 */
	public static void roundRectTop(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
		LunaGfx.layerBreak(ctx);   // 49-292차
		if (w <= 0 || h <= 0) {
			return;
		}
		color = applyAlpha(color);
		int r = Math.min(radius(radius), Math.min(w / 2, h));
		if (r <= 0) {
			ctx.fill(x, y, x + w, y + h, color);
			return;
		}
		if (LunaGfx.drawRound(ctx, x, y, r, r, 0, 0, 64, 64, color)
			&& LunaGfx.drawRound(ctx, x + w - r, y, r, r, 64, 0, 64, 64, color)) {
			ctx.fill(x + r, y, x + w - r, y + r, color);
			ctx.fill(x, y + r, x + w, y + h, color);
			return;
		}
		ctx.fill(x, y + r, x + w, y + h, color);
		for (int dy = 0; dy < r; dy++) {
			double cy = r - dy - 0.5;
			int inset = r - (int) Math.floor(Math.sqrt(r * (double) r - cy * cy));
			ctx.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
		}
	}

	/**
	 * 49-180차: 윤곽선({@link #roundRectOutline})과 <b>같은 계단 모서리</b>로 채운 둥근 사각형. 기본 roundRect는 부드러운
	 * 텍스처 모서리라 계단 윤곽선과 모서리 모양이 달라, 둘을 겹치면 배경이 윤곽선 밖으로 삐져나와 보였다
	 * (사용자: "테두리가 검정색이 튀어나와 있음 - 색 테두리랑 검정색이랑 모양이 다름"). 윤곽선을 같이 그릴 때 이걸 쓴다.
	 */
	public static void roundRectPixel(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
		LunaGfx.layerBreak(ctx);   // 49-292차
		if (w <= 0 || h <= 0) {
			return;
		}
		color = applyAlpha(color);
		int r = Math.min(radius(radius), Math.min(w, h) / 2);
		if (r <= 0) {
			ctx.fill(x, y, x + w, y + h, color);
			return;
		}
		ctx.fill(x, y + r, x + w, y + h - r, color);
		for (int dy = 0; dy < r; dy++) {
			double cy = r - dy - 0.5;
			int inset = r - (int) Math.floor(Math.sqrt(r * (double) r - cy * cy));
			ctx.fill(x + inset, y + dy, x + w - inset, y + dy + 1, color);
			ctx.fill(x + inset, y + h - dy - 1, x + w - inset, y + h - dy, color);
		}
	}

	/** 패널 뒤 부드러운 그림자(아래로 살짝 번짐) - 패널을 그리기 직전에 호출. */
	public static void shadow(DrawContext ctx, int x, int y, int w, int h, int radius) {
		roundRect(ctx, x - 3, y - 1, w + 6, h + 7, radius + 2, 0x1F000000);
		roundRect(ctx, x - 1, y + 1, w + 2, h + 4, radius + 1, 0x2E000000);
	}

	/**
	 * 49-25차: 세로 그라데이션 둥근 사각형(위 top → 아래 bottom). 모서리는 텍스처 원(위쪽은 top색, 아래쪽은
	 * bottom색), 몸통과 좌우 띠는 fillGradient. 텍스처를 못 쓰는 버전은 줄마다 색을 섞어 채움.
	 */
	public static void roundRectGradient(DrawContext ctx, int x, int y, int w, int h, int radius, int top, int bottom) {
		LunaGfx.layerBreak(ctx);   // 49-292차
		if (w <= 0 || h <= 0) {
			return;
		}
		top = applyAlpha(top);
		bottom = applyAlpha(bottom);
		if (top == bottom) {
			roundRect(ctx, x, y, w, h, radius, top);
			return;
		}
		int r = Math.min(radius(radius), Math.min(w, h) / 2);
		if (r <= 0) {
			ctx.fillGradient(x, y, x + w, y + h, top, bottom);
			return;
		}
		int midTop = lerpColor(top, bottom, r / (float) h);
		int midBottom = lerpColor(top, bottom, (h - r) / (float) h);
		if (LunaGfx.drawRound(ctx, x, y, r, r, 0, 0, 64, 64, top)
			&& LunaGfx.drawRound(ctx, x + w - r, y, r, r, 64, 0, 64, 64, top)
			&& LunaGfx.drawRound(ctx, x, y + h - r, r, r, 0, 64, 64, 64, bottom)
			&& LunaGfx.drawRound(ctx, x + w - r, y + h - r, r, r, 64, 64, 64, 64, bottom)) {
			ctx.fillGradient(x + r, y, x + w - r, y + h, top, bottom);
			ctx.fillGradient(x, y + r, x + r, y + h - r, midTop, midBottom);
			ctx.fillGradient(x + w - r, y + r, x + w, y + h - r, midTop, midBottom);
			return;
		}
		ctx.fillGradient(x, y + r, x + w, y + h - r, midTop, midBottom);
		for (int dy = 0; dy < r; dy++) {
			double cy = r - dy - 0.5;
			int inset = r - (int) Math.floor(Math.sqrt(r * (double) r - cy * cy));
			ctx.fill(x + inset, y + dy, x + w - inset, y + dy + 1, lerpColor(top, bottom, dy / (float) h));
			ctx.fill(x + inset, y + h - dy - 1, x + w - inset, y + h - dy, lerpColor(top, bottom, (h - dy - 1) / (float) h));
		}
	}

	/** 색을 흰색 쪽으로 k(0~1)만큼(알파 유지). */
	public static int lighten(int argb, float k) {
		int a = (argb >>> 24) & 0xFF;
		return (lerpColor(argb | 0xFF000000, 0xFFFFFFFF, k) & 0x00FFFFFF) | (a << 24);
	}

	/** RGB에 k를 곱함(알파 유지). */
	public static int darken(int argb, float k) {
		int a = (argb >>> 24) & 0xFF;
		int r = Math.round(((argb >> 16) & 0xFF) * k);
		int g = Math.round(((argb >> 8) & 0xFF) * k);
		int b = Math.round((argb & 0xFF) * k);
		return (a << 24) | (r << 16) | (g << 8) | b;
	}

	/**
	 * 테두리 있는 둥근 사각형. 49-25차: 툴팁 상자와 같은 결 - 테두리는 아래로 갈수록 옅어지고, 속은 위가 살짝
	 * 밝은 세로 그라데이션, 안쪽 윗줄에 가는 하이라이트("다른 곳도 그라데이션으로 예쁘게, 마크 느낌 안 나게").
	 * 카드·패널·모달·버튼·칩이 전부 이 함수를 쓰므로 한 번에 바뀐다.
	 */
	public static void roundRectBordered(DrawContext ctx, int x, int y, int w, int h, int radius, int fill, int border) {
		// 49-34차(사용자: "그라데이션을 너무 대충 넣어서 하나도 안예뻐 - 다른 클라이언트 벤치마킹"):
		// 49-25차에 넣었던 "속은 위가 밝고 아래가 어둡게, 테두리는 아래로 갈수록 사라지는" 세로 그라데이션을
		// 뺐다. 거의 검정인 바탕에서는 아래쪽이 탁해지고 테두리가 끊긴 것처럼 보여서, 카드·버튼·패널이
		// 전부 마감 안 된 느낌이 났다. 루나·페더·배드라이언은 **평평한 바탕 + 균일한 1px 테두리 + 아주
		// 옅은 위쪽 하이라이트 한 줄**이 전부다 - 그대로 따른다.
		// 49-226차(사진 시안): 화면(HUD 아님)의 테두리 상자는 아래로 2px 두께(어두운 띠)를 깐다 - 판, 카드, 칩이 한 번에 입체로.
		if (hudDepth == 0 && h >= 10 && w >= 10 && ((fill >>> 24) & 0xFF) >= 0xC0) {
			roundRect(ctx, x, y + 2, w, h, radius, 0x47000000);
		}
		roundRect(ctx, x, y, w, h, radius, border);
		roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fill);
		if (h >= 12 && w >= 12) {
			int r = Math.max(1, Math.min(radius(radius), Math.min(w, h) / 2));
			ctx.fill(x + r, y + 1, x + w - r, y + 2, applyAlpha(0x0AFFFFFF));
		}
	}

	/** 49-25차: 예전 방식(평면 채움)이 필요한 곳용. */
	public static void roundRectBorderedFlat(DrawContext ctx, int x, int y, int w, int h, int radius, int fill, int border) {
		roundRect(ctx, x, y, w, h, radius, border);
		roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), fill);
	}

	/** 둥근 사각형 테두리(1px)만. 배경이 투명한 곳(HUD 편집기 박스)에 사용. */
	public static void roundRectOutline(DrawContext ctx, int x, int y, int w, int h, int radius, int color) {
		LunaGfx.layerBreak(ctx);   // 49-292차
		if (w <= 0 || h <= 0) {
			return;
		}
		color = applyAlpha(color);
		int r = Math.min(radius(radius), Math.min(w, h) / 2);
		if (r <= 0) {
			ctx.fill(x, y, x + w, y + 1, color);
			ctx.fill(x, y + h - 1, x + w, y + h, color);
			ctx.fill(x, y, x + 1, y + h, color);
			ctx.fill(x + w - 1, y, x + w, y + h, color);
			return;
		}
		ctx.fill(x + r, y, x + w - r, y + 1, color);
		ctx.fill(x + r, y + h - 1, x + w - r, y + h, color);
		ctx.fill(x, y + r, x + 1, y + h - r, color);
		ctx.fill(x + w - 1, y + r, x + w, y + h - r, color);
		int prevInset = r;
		for (int dy = 0; dy < r; dy++) {
			double cy = r - dy - 0.5;
			int inset = r - (int) Math.floor(Math.sqrt(r * (double) r - cy * cy));
			int from = inset;
			int to = Math.max(inset + 1, prevInset);
			ctx.fill(x + from, y + dy, x + to, y + dy + 1, color);
			ctx.fill(x + w - to, y + dy, x + w - from, y + dy + 1, color);
			ctx.fill(x + from, y + h - dy - 1, x + to, y + h - dy, color);
			ctx.fill(x + w - to, y + h - dy - 1, x + w - from, y + h - dy, color);
			prevInset = inset;
		}
	}

	// =====================================================================
	// 49-28차: 화면 공통 "결" - 통계 화면에서 쓰던 가는 선/섹션 제목을 다른 화면도 함께 쓴다
	// =====================================================================

	/** 왼쪽에서 오른쪽으로 사라지는 1px 선. */
	public static void fadeLine(DrawContext ctx, int x, int y, int w, int color) {
		if (w <= 0) {
			return;
		}
		ctx.fillGradient(x, y, x + w / 2, y + 1, applyAlpha(withAlpha(color, 0x30)), applyAlpha(0x10FFFFFF));
		ctx.fillGradient(x + w / 2, y, x + w, y + 1, applyAlpha(0x10FFFFFF), applyAlpha(0x03FFFFFF));
	}

	/**
	 * 49-124차(사용자: "줄이 끝까지 이어지지 말고 그라데이션으로 끝나게"): 제목 뒤에서 <b>정해진 길이 안에서</b>
	 * 완전 투명(알파 0)으로 사라지는 머리줄. 폭이 아무리 넓어도 토글까지 이어지지 않고, ease-out 곡선으로
	 * 부드럽게 흩어진다(fillGradient는 선형이라 여러 토막으로 곡선을 흉내 낸다).
	 */
	public static void headerFade(DrawContext ctx, int x, int y, int w, int color) {
		if (w <= 0) {
			return;
		}
		int len = Math.min(w, 150);
		int rgb = color & 0x00FFFFFF;
		final int a0 = 0x2E;      // 제목 바로 뒤 알파(46)
		final int steps = 16;
		for (int i = 0; i < steps; i++) {
			double s0 = i / (double) steps;
			double s1 = (i + 1) / (double) steps;
			int sx0 = x + (int) Math.round(len * s0);
			int sx1 = x + (int) Math.round(len * s1);
			if (sx1 <= sx0) {
				continue;
			}
			int aa0 = (int) Math.round(a0 * (1 - s0) * (1 - s0));   // ease-out: (1-s)^2 → 0에 수렴
			int aa1 = (int) Math.round(a0 * (1 - s1) * (1 - s1));
			ctx.fillGradient(sx0, y, sx1, y + 1,
					applyAlpha((aa0 << 24) | rgb), applyAlpha((aa1 << 24) | rgb));
		}
	}

	/** 가운데가 옅어지는 연결선(항목 이름과 값 사이). */
	public static void linkLine(DrawContext ctx, int x0, int x1, int y) {
		if (x1 <= x0) {
			return;
		}
		int mid = (x0 + x1) / 2;
		ctx.fillGradient(x0, y, mid, y + 1, applyAlpha(0x12FFFFFF), applyAlpha(0x07FFFFFF));
		ctx.fillGradient(mid, y, x1, y + 1, applyAlpha(0x07FFFFFF), applyAlpha(0x12FFFFFF));
	}

	/**
	 * 49-93차(사용자: "이름 ---- 값 막대기 없애거나 조금만 가다 사라지게, 저것 때문에 구분이 안 됨"):
	 * 이름 뒤에서 조금 나가다 사라지는 꼬리. 칸을 가로지르는 막대기가 아니라 이름에 붙은 짧은 여운.
	 *
	 * <p>49-103차(사용자: "막대기 뚝 끊지 말고 그라데이션으로 서서히 없애고"): 22px에서 선형으로 뚝 끝나던 것을
	 * 최대 42px까지 ease-out(제곱) 곡선으로 부드럽게 흩어지게. 알파가 처음엔 진하다가 뒤로 갈수록 완만하게 0으로
	 * 수렴해, 눈에 보이는 길이는 여전히 짧지만 끝이 딱 잘리지 않는다. fillGradient는 선형이라 여러 토막으로 나눠
	 * 곡선을 흉내 낸다.
	 */
	public static void linkTail(DrawContext ctx, int x0, int x1, int y) {
		if (x1 <= x0) {
			return;
		}
		int end = Math.min(x1, x0 + 42);
		int len = end - x0;
		if (len <= 0) {
			return;
		}
		final int a0 = 0x20;      // 이름 바로 뒤 알파(32)
		final int steps = 14;
		for (int i = 0; i < steps; i++) {
			double s0 = i / (double) steps;
			double s1 = (i + 1) / (double) steps;
			int sx0 = x0 + (int) Math.round(len * s0);
			int sx1 = x0 + (int) Math.round(len * s1);
			if (sx1 <= sx0) {
				continue;
			}
			int aa0 = (int) Math.round(a0 * (1 - s0) * (1 - s0));   // ease-out: (1-s)^2
			int aa1 = (int) Math.round(a0 * (1 - s1) * (1 - s1));
			ctx.fillGradient(sx0, y, sx1, y + 1,
					applyAlpha((aa0 << 24) | 0x00FFFFFF), applyAlpha((aa1 << 24) | 0x00FFFFFF));
		}
	}

	/** 섹션 제목 + 오른쪽으로 사라지는 강조선. */
	public static void sectionTitle(DrawContext ctx, TextRenderer tr, int x, int y, int w, String title) {
		text(ctx, tr, title, x, y, TEXT);
		int lx = x + width(tr, title) + 8;
		fadeLine(ctx, lx, y + 4, x + w - lx, ACCENT);
	}

	/** 이름 · 연결선 · 값 한 줄(통계/진단/설정 공용). */
	public static void infoRow(DrawContext ctx, TextRenderer tr, int x, int y, int w, String label, String value, int valueColor) {
		infoRowColored(ctx, tr, x, y, w, label, value, TEXT_SUB, valueColor);
	}

	/** 이름 색까지 정하는 판. */
	public static void infoRowColored(DrawContext ctx, TextRenderer tr, int x, int y, int w, String label, String value,
			int labelColor, int valueColor) {
		text(ctx, tr, label, x, y, labelColor);
		int vw = width(tr, value);
		text(ctx, tr, value, x + w - vw, y, valueColor);
		linkLine(ctx, x + width(tr, label) + 6, x + w - vw - 6, y + 4);
	}

	/**
	 * 작은 꺾쇠(설정 그룹 접기/펴기).	/**
	 * 작은 꺾쇠(설정 그룹 접기/펴기). 동봉 아이콘 폰트 서브셋에 chevron이 없어서 fill로 직접 그린다.
	 * down=true면 ∨(펼침), false면 ›(접힘). 5×5 안에 들어감.
	 */
	public static void chevron(DrawContext ctx, int x, int y, boolean down, int color) {
		color = applyAlpha(color);
		for (int i = 0; i < 3; i++) {
			if (down) {
				ctx.fill(x + i, y + i, x + i + 1, y + i + 2, color);
				ctx.fill(x + 4 - i, y + i, x + 5 - i, y + i + 2, color);
			} else {
				ctx.fill(x + i, y + i, x + i + 2, y + i + 1, color);
				ctx.fill(x + i, y + 4 - i, x + i + 2, y + 5 - i, color);
			}
		}
	}

	/** 원(지름 d). */
	public static void circle(DrawContext ctx, int x, int y, int d, int color) {
		LunaGfx.layerBreak(ctx);   // 49-292차
		if (!LunaGfx.drawRound(ctx, x, y, d, d, 0, 0, 128, 128, applyAlpha(color))) {
			roundRect(ctx, x, y, d, d, d / 2, color);
		}
	}

	/**
	 * 페더 스타일 토글 스위치(알약). t = 0(꺼짐)~1(켜짐) 애니메이션 진행도 - 노브가 미끄러지고
	 * 트랙 색이 섞임.
	 */
	// 49-104차(사용자: "기능 껐다켰다 하는 버튼은 활성화했을 때 테마색 말고 초록색 고정으로"):
	// 켜짐 스위치를 테마(ACCENT)와 무관하게 항상 초록으로. 값은 기본 테마(초록)일 때와 같은 톤 - 어두운 초록 트랙 + 밝은 초록 노브.
	private static final int TOGGLE_ON_TRACK = 0xFF566E42;
	private static final int TOGGLE_ON_KNOB = 0xFFB9E387;

	public static void toggle(DrawContext ctx, int x, int y, int w, int h, float t, boolean enabledLook) {
		t = Math.max(0f, Math.min(1f, t));
		int on = enabledLook ? TOGGLE_ON_TRACK : ACCENT_DIM; // 켜짐 = 고정 초록 트랙 + 밝은 초록 노브(테마색 안 따라감)
		if (enabledLook && skinSwitch(ctx, x, y, w, h, t)) {
			return;   // 49-279차
		}
		if (soft()) {
			// 크림: 시안처럼 하늘색 알약 + 흰 노브
			pill(ctx, x, y, w, h, lerpColor(0xFFE6D8BE, enabledLook ? 0xFF62ACE6 : 0xFFB9C9D6, t));
			int ks = h - 4;
			circle(ctx, Math.round(x + 2 + (w - ks - 4) * t), y + 2, ks, 0xFFFFFFFF);
			return;
		}
		int track = lerpColor(TRACK, on, t);
		// 49-128차: 알약 + 동그란 노브 → 모서리 2px 트랙 + 네모 노브(작은 동그라미가 각져 보인다고).
		// 49-161차(사용자: "너무 네모가 보여 클라이언트처럼 부드럽게", 스위치 골라 줌): 다시 알약 트랙 + 동그란 노브.
		// 이번엔 둘 다 부드러운 텍스처 원(LunaGfx.drawRound)으로 그려 계단이 안 진다.
		pill(ctx, x, y, w, h, track);
		int knobSize = h - 4;
		int knobX = Math.round(x + 2 + (w - knobSize - 4) * t);
		int knob = enabledLook ? lerpColor(0xFF80848E, TOGGLE_ON_KNOB, t) : 0xFF6A6E78;
		circle(ctx, knobX, y + 2, knobSize, knob);
	}

	/** 슬라이더: 둥근 트랙 + 채움 + 노브. ratio01은 0~1. */
	public static void slider(DrawContext ctx, int x, int y, int w, float ratio01, boolean hovered) {
		int trackH = 4;
		int ty = y + 5;
		roundRect(ctx, x, ty, w, trackH, 2, TRACK);
		// 49-234차(사용자: "게이지 끝까지 당기면 원이 밖으로 튀어나가"): 노브 가운데가 트랙 양 끝에서 반지름(5)만큼 안쪽에서만 움직인다
		float r = Math.max(0f, Math.min(1f, ratio01));
		int cx = x + 5 + Math.round((w - 10) * r);
		int fillW = Math.max(trackH, cx - x);
		roundRect(ctx, x, ty, fillW, trackH, 2, ACCENT);
		int knob = hovered ? 10 : 8;
		int kx = cx - knob / 2;
		circle(ctx, kx, ty + trackH / 2 - knob / 2, knob, KNOB);
	}

	// =====================================================================
	// 49-227차: 사진 시안 공용 부품(사용자: "다른 UI들도 전부 이런 입체감 + 예쁜 버튼으로")
	// 판 = 그림자 + 아래 두께 3px + 테두리 + 불투명 속, 카드 = 아래 두께 2px + 테두리 + 속,
	// 버튼 = 아래 두께 2px + 테두리 + 위가 밝은 그라데이션 속(+ 윗줄 하이라이트). 모든 Nova 화면이 이것만 쓴다.
	// =====================================================================

	public static final int B_NEUTRAL = 0, B_PRIMARY = 1, B_DANGER = 2, B_GOOD = 3;
	private static final int GOOD_BASE = 0xFF5C9A38;   // 켜짐 초록(테마와 무관)
	private static final int DANGER_BASE = 0xFFB9493F;

	private static boolean lightTheme() {
		try {
			return LunaTheme.light();
		} catch (Throwable t) {
			return false;
		}
	}

	/** 판 속(불투명). */
	public static int surfaceBg() {
		return lightTheme() ? 0xFFF7EEDC : LunaTheme.mix(0xFF000000 | LunaTheme.PANEL, 0xFF000000 | LunaTheme.CARD, 0.35f);
	}

	/** 카드 속(판보다 한 단계 밝게). */
	public static int surfaceCard() {
		return lightTheme() ? 0xFFFFFBF2 : LunaTheme.mix(0xFF000000 | LunaTheme.CARD, 0xFF000000 | LunaTheme.CARD_HOVER, 0.5f);
	}

	/** 카드/판 테두리. */
	public static int surfaceLine() {
		return lightTheme() ? 0xFFE6D6B8 : LunaTheme.mix(0xFF000000 | LunaTheme.TRACK, 0xFF000000 | LunaTheme.TEXT, 0.06f);
	}

	/** 입력칸 속(카드보다 깊게). */
	public static int surfaceField() {
		return lightTheme() ? 0xFFFFFFFF : LunaTheme.mix(surfaceBg(), 0xFF000000, 0.25f);
	}

	/** 아래 두께 색. */
	public static int surfaceEdge() {
		return lightTheme() ? 0xFFD6C29C : LunaTheme.mix(surfaceBg(), 0xFF000000, 0.55f);
	}

	/** 판(화면 가운데 큰 창): 바깥 그림자 + 아래 두께 3px + 테두리 + 불투명 속. */
	// ---- 49-239차(사용자: "크림은 시안 오른쪽이랑 진짜 비슷하게 부드럽고 말랑하게"): 밝은(크림) 스킨일 땐 둥글기를 키우고
	// 아래 두께(어두운 띠) 대신 갈색 기운의 옅은 그림자 두 겹으로 띄운다.
	public static boolean soft() {
		return lightTheme();
	}

	// ==================== 49-279차: 미드나잇 / 네온 사이버 화면 스킨 ====================
	// 런처 상점 인게임 UI 2종(claude/nova-mod-ui-skins-midnight-neon.md). 색은 LunaTheme 팔레트가 바꾸고, 여기서는 모양(빛 번짐,
	// 네온의 잘린 모서리, 버튼, 스위치)만 바꾼다. 크림처럼 Nova 화면 공용 부품(panel3d, card3d, button3d, toggle)과
	// LunaClientScreen의 판, 카드, 줄, 켜짐 버튼, 스위치가 이 함수들을 먼저 물어본다.

	public static boolean midnight() {
		try {
			return LunaTheme.skin() == LunaTheme.Skin.MIDNIGHT;
		} catch (Throwable t) {
			return false;
		}
	}

	public static boolean neon() {
		try {
			return LunaTheme.skin() == LunaTheme.Skin.NEON;
		} catch (Throwable t) {
			return false;
		}
	}

	public static final int MID_BORDER = 0xFF9670FF, MID_GLOW = 0x8C50FF, NEON_CYAN = 0xFF20F0FF, NEON_PINK = 0xFFFF3CC8;

	/** 상자 바깥으로 번지는 빛(1px 고리를 바깥으로 갈수록 옅게 - 속은 안 칠해서 반투명 판이 물들지 않는다). */
	public static void glow(DrawContext ctx, int x, int y, int w, int h, int r, int rgb, int a0, int spread) {
		// 49-285차(사용자: "미드나잇 바깥 빛나는 모서리에 검은 선으로 빛이 안 나오는 곳"): 1px 고리(계단 모서리)는 둥근 모서리에서
		// 고리끼리 틈이 나 검은 줄이 보였다. 바깥부터 옅은 둥근 판을 겹쳐 칠한다(안으로 갈수록 겹쳐 진해짐, 부드러운 둥근 모서리).
		int al = Math.max(1, Math.round(a0 * 2f / (spread + 1)));
		for (int i = spread; i >= 1; i--) {
			roundRect(ctx, x - i, y - i, w + 2 * i, h + 2 * i, r + i, (al << 24) | (rgb & 0xFFFFFF));
		}
	}

	/** 네온 모양(오른쪽 위, 왼쪽 아래를 cut만큼 대각선으로 잘라냄) 채우기. */
	public static void neonFill(DrawContext ctx, int x, int y, int w, int h, int cut, int color) {
		if (w <= 0 || h <= 0) {
			return;
		}
		cut = Math.max(0, Math.min(cut, Math.min(w, h) / 2));
		color = applyAlpha(color);
		for (int j = 0; j < cut; j++) {
			ctx.fill(x, y + j, x + w - (cut - j), y + j + 1, color);
		}
		if (h - 2 * cut > 0) {
			ctx.fill(x, y + cut, x + w, y + h - cut, color);
		}
		for (int j = 0; j < cut; j++) {
			ctx.fill(x + j + 1, y + h - cut + j, x + w, y + h - cut + j + 1, color);
		}
	}

	/** 네온 모양 테두리(두께 t). 속은 안 칠한다. */
	public static void neonFrame(DrawContext ctx, int x, int y, int w, int h, int cut, int t, int color) {
		if (w <= 2 * t || h <= 2 * t) {
			neonFill(ctx, x, y, w, h, cut, color);
			return;
		}
		cut = Math.max(0, Math.min(cut, Math.min(w, h) / 2));
		color = applyAlpha(color);
		int iw = w - 2 * t, ih = h - 2 * t, icut = Math.max(0, Math.min(cut, Math.min(iw, ih) / 2));
		int band = cut + t;
		for (int j = 0; j < h; j++) {
			if (j == band && h - band > band) {
				// 가운데 곧은 구간: 양옆 띠만 한 번에
				ctx.fill(x, y + band, x + t, y + h - band, color);
				ctx.fill(x + w - t, y + band, x + w, y + h - band, color);
				j = h - band - 1;
				continue;
			}
			int ox0 = x + (j >= h - cut ? j - (h - cut) + 1 : 0);
			int ox1 = x + w - (j < cut ? cut - j : 0);
			if (j < t || j >= h - t) {
				ctx.fill(ox0, y + j, ox1, y + j + 1, color);
				continue;
			}
			int jj = j - t;
			int ix0 = x + t + (jj >= ih - icut ? jj - (ih - icut) + 1 : 0);
			int ix1 = x + t + iw - (jj < icut ? icut - jj : 0);
			if (ix0 > ox0) {
				ctx.fill(ox0, y + j, Math.min(ix0, ox1), y + j + 1, color);
			}
			if (ox1 > ix1) {
				ctx.fill(Math.max(ix1, ox0), y + j, ox1, y + j + 1, color);
			}
		}
	}

	/** 큰 판(창). 미드나잇/네온이면 그리고 true. */
	public static boolean skinPanel(DrawContext ctx, int x, int y, int w, int h, int r) {
		if (midnight()) {
			r += 3;
			glow(ctx, x, y, w, h, r, MID_GLOW, 0x5A, 9);
			roundRect(ctx, x, y, w, h, r, LunaTheme.mix(0xFF16112F, 0xFFA06EFF, 0.45f));
			roundRectGradient(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), 0xF01D1740, 0xF0100C22);
			midnightSky(ctx, x, y, w, h, r);   // 49-283차: 별, 오로라, 달
			ctx.fill(x + r, y + 1, x + w - r, y + 2, applyAlpha(0x22E9DDFF));
			return true;
		}
		if (neon()) {
			int cut = Math.max(6, Math.min(14, Math.min(w, h) / 10));
			for (int i = 7; i >= 1; i--) {
				float k = 1f - (i - 0.5f) / 7f;
				neonFrame(ctx, x - i, y - i, w + 2 * i, h + 2 * i, cut + i / 2, 1, (Math.round(0x70 * k * k) << 24) | (NEON_CYAN & 0xFFFFFF));
			}
			neonFill(ctx, x + 2, y + 2, w - 4, h - 4, cut, 0xEE060609);
			neonFrame(ctx, x + 2, y + 2, w - 4, h - 4, cut, 1, 0x2A20F0FF);   // 안쪽 은은한 빛
			neonFrame(ctx, x + 3, y + 3, w - 6, h - 6, cut, 1, 0x1220F0FF);
			cyberDecor(ctx, x, y, w, h, cut);   // 49-283차: 사이버펑크(주사선, 색 번짐, 모서리 꺾쇠, 눈금)
			neonFrame(ctx, x, y, w, h, cut, 2, NEON_CYAN);
			return true;
		}
		return false;
	}

	/** 카드(기능 칸, 묶음, 작은 판). 미드나잇/네온이면 그리고 true. */
	public static boolean skinCard(DrawContext ctx, int x, int y, int w, int h, int r, int fill, int border) {
		if (midnight()) {
			r = Math.min(r + 2, Math.min(w, h) / 2);
			roundRect(ctx, x, y + 1, w, h, r, 0x30000000);
			roundRect(ctx, x, y, w, h, r, border);
			roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
			return true;
		}
		if (neon()) {
			// 49-283차: 사이버펑크 - 오른쪽 위, 왼쪽 아래를 잘라낸 카드 + 왼쪽 위 시안 꺾쇠
			int cut = Math.max(2, Math.min(6, Math.min(w, h) / 5));
			neonFill(ctx, x, y, w, h, cut, border);
			neonFill(ctx, x + 1, y + 1, w - 2, h - 2, cut, fill);
			if (w > 14 && h > 10) {
				ctx.fill(x, y, x + 6, y + 1, applyAlpha(NEON_CYAN));
				ctx.fill(x, y, x + 1, y + 5, applyAlpha(NEON_CYAN));
			}
			return true;
		}
		return false;
	}

	/**
	 * 버튼 몸통. 미드나잇: 일반 = 짙은 보라 그라데이션 + 보라 테두리, 주 = 밝은 보라 그라데이션 + 빛.
	 * 네온: 일반 = 투명 + 분홍 테두리, 주 = 옅은 시안 + 시안 테두리 + 빛. 그리면 true.
	 */
	public static boolean skinButton(DrawContext ctx, int x, int y, int w, int h, boolean primary, float hov) {
		if (midnight()) {
			int r = Math.min(5, h / 2);
			if (primary) {
				glow(ctx, x, y, w, h, r, 0x8C5AFF, Math.round(0x50 + 0x30 * hov), 4);
				roundRect(ctx, x, y, w, h, r, 0xFFB597FF);
				roundRectGradient(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1),
					lighten(0xFF8A5CFF, 0.10f * hov), lighten(0xFF6A3DF0, 0.10f * hov));
			} else {
				roundRect(ctx, x, y, w, h, r, LunaTheme.mix(0xFF1C1638, MID_BORDER, 0.35f + 0.25f * hov));
				roundRectGradient(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1),
					lighten(0xFF2A2150, 0.08f * hov), lighten(0xFF1C1638, 0.08f * hov));
			}
			if (w > 2 * r + 2) {
				ctx.fill(x + r, y + 1, x + w - r, y + 2, applyAlpha(primary ? 0x40FFFFFF : 0x14FFFFFF));
			}
			return true;
		}
		if (neon()) {
			// 49-283차: 사이버펑크 - 모서리 잘린 버튼, 주 버튼은 분홍이 1px 어긋나 번지고 왼쪽에 굵은 시안 띠
			int cut = Math.max(2, Math.min(5, h / 3));
			if (primary) {
				neonFill(ctx, x, y, w, h, cut, NEON_CYAN);
				neonFill(ctx, x + 1, y + 1, w - 2, h - 2, cut, 0xFF060609);
				neonFill(ctx, x + 1, y + 1, w - 2, h - 2, cut, Math.round(0x24 + 0x22 * hov) << 24 | 0x20F0FF);
				ctx.fill(x + 1, y + 1, x + 3, y + h - cut, applyAlpha(NEON_CYAN));
				ctx.fill(x + cut + 2, y + h, x + w - 2, y + h + 1, applyAlpha(0x80FF3CC8));   // 아래 분홍 번짐 한 줄
			} else {
				int edge = LunaTheme.mix(0xFF060609, NEON_PINK, 0.75f + 0.25f * hov);
				neonFill(ctx, x, y, w, h, cut, edge);
				neonFill(ctx, x + 1, y + 1, w - 2, h - 2, cut, 0xFF060609);
				neonFill(ctx, x + 1, y + 1, w - 2, h - 2, cut, Math.round(0x08 + 0x14 * hov) << 24 | 0xFF3CC8);
				if (hov > 0.01f) {
					ctx.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, applyAlpha((Math.round(0xC0 * hov) << 24) | 0xFF3CC8));
				}
			}
			return true;
		}
		return false;
	}

	/** 스킨 버튼 글자색(0 = 스킨 아님). */
	public static int skinButtonText(boolean primary, float hov) {
		if (midnight()) {
			return primary ? 0xFFFFFFFF : lerpColor(0xFFDDD3FF, 0xFFFFFFFF, 0.5f * hov);
		}
		if (neon()) {
			return primary ? 0xFFBFFCFF : lerpColor(0xFFFF9CE6, 0xFFFFD3F3, hov);
		}
		return 0;
	}

	/** 켜기/끄기 스위치. 미드나잇 = 보라 알약(켜면 빛) + 흰 노브, 네온 = 시안 테두리 네모 + 네모 노브. 그리면 true. */
	public static boolean skinSwitch(DrawContext ctx, int x, int y, int w, int h, float t) {
		t = Math.max(0f, Math.min(1f, t));
		if (midnight()) {
			if (t > 0.01f) {
				glow(ctx, x, y, w, h, h / 2, 0x6A3DF0, Math.round(0x60 * t), 3);
			}
			pill(ctx, x, y, w, h, lerpColor(0xFF2C2648, 0xFF6A3DF0, t));
			int ks = h - 4;
			circle(ctx, Math.round(x + 2 + (w - ks - 4) * t), y + 2, ks, lerpColor(0xFF8A82B0, 0xFFFFFFFF, t));
			return true;
		}
		if (neon()) {
			int edge = lerpColor(0xFF55556A, NEON_CYAN, t);
			roundRect(ctx, x, y, w, h, 1, edge);
			roundRect(ctx, x + 1, y + 1, w - 2, h - 2, 0, 0xFF08080D);
			int ks = h - 4;
			int kx = Math.round(x + 2 + (w - ks - 4) * t);
			if (t > 0.01f) {
				glow(ctx, kx, y + 2, ks, ks, 0, 0x20F0FF, Math.round(0x70 * t), 3);
			}
			ctx.fill(kx, y + 2, kx + ks, y + 2 + ks, applyAlpha(edge));
			return true;
		}
		return false;
	}

	/**
	 * 49-283차(사용자: "미드나잇도 뭔가 킥을"): 미드나잇 판 위에 밤하늘 - 위쪽 보라 오로라(옅은 띠), 반짝이는 별(자리는 판 크기로
	 * 고정, 밝기만 천천히 깜빡), 오른쪽 아래 초승달과 빛. 판 바탕 바로 뒤에 그려서 내용이 위에 덮인다.
	 */
	public static void midnightSky(DrawContext ctx, int x, int y, int w, int h, int r) {
		if (w < 40 || h < 40) {
			return;
		}
		long now = System.currentTimeMillis();
		// 오로라: 위에서 아래로 옅어지는 보라/분홍 띠 두 겹
		int band = Math.min(36, h / 4);
		for (int j = 0; j < band; j++) {
			float k = 1f - j / (float) band;
			int a = Math.round(0x2C * k * k);
			if (a > 0) {
				int c = j % 2 == 0 ? 0x8A5CFF : 0xB06CFF;
				int yy = 2 + j;
				double dy = r - yy - 0.5;
				int in = yy < r ? (int) Math.ceil(r - Math.sqrt(Math.max(0, r * (double) r - dy * dy))) : 1;
				ctx.fill(x + Math.max(1, in), y + yy, x + w - Math.max(1, in), y + yy + 1, applyAlpha((a << 24) | c));
			}
		}
		// 별: 판 크기로 정해지는 자리(매 프레임 같은 자리), 밝기만 깜빡
		int n = Math.max(12, Math.min(140, w * h / 1400));
		long seed = (long) w * 73856093L ^ (long) h * 19349663L;
		for (int i = 0; i < n; i++) {
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int sx = x + 4 + (int) ((seed >>> 33) % Math.max(1, w - 8));
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int sy = y + 4 + (int) ((seed >>> 33) % Math.max(1, h - 8));
			seed = seed * 6364136223846793005L + 1442695040888963407L;
			int kind = (int) ((seed >>> 40) & 0xFF);
			double ph = (kind / 255.0) * Math.PI * 2;
			double tw = 0.5 + 0.5 * Math.sin(now / (900.0 + (kind & 31) * 40.0) + ph);
			int a = (int) Math.round((kind < 40 ? 0xC0 : 0x50) * (0.35 + 0.65 * tw));
			int col = (kind & 3) == 0 ? 0xE6D8FF : 0xFFFFFF;
			ctx.fill(sx, sy, sx + 1, sy + 1, applyAlpha((a << 24) | col));
			if (kind < 40) {   // 밝은 별은 십자 빛
				int a2 = a / 3;
				ctx.fill(sx - 1, sy, sx, sy + 1, applyAlpha((a2 << 24) | col));
				ctx.fill(sx + 1, sy, sx + 2, sy + 1, applyAlpha((a2 << 24) | col));
				ctx.fill(sx, sy - 1, sx + 1, sy, applyAlpha((a2 << 24) | col));
				ctx.fill(sx, sy + 1, sx + 1, sy + 2, applyAlpha((a2 << 24) | col));
			}
		}
		// 초승달(오른쪽 아래): 빛 + 밝은 원 위에 바탕색 원을 비껴 덮는다
		if (w >= 120 && h >= 90) {
			int d = 18, mx = x + w - d - 16, my = y + h - d - 16;
			for (int g = 6; g >= 1; g--) {
				circle(ctx, mx - g, my - g, d + 2 * g, (Math.round(0x10 * (1f - g / 7f)) << 24) | 0xB597FF);
			}
			// 초승달: 밝은 원 안에서 비껴 놓은 원 밖인 부분만 줄마다 칠한다(덮어 지우지 않아 빛과 하늘이 그대로)
			double rad = d / 2.0, cx0 = mx + rad, cy0 = my + rad, cx1 = cx0 + 5, cy1 = cy0 - 3;
			for (int j = 0; j < d; j++) {
				double py = my + j + 0.5;
				double ha = rad * rad - (py - cy0) * (py - cy0);
				if (ha <= 0) {
					continue;
				}
				double sa = Math.sqrt(ha), a0 = cx0 - sa, a1 = cx0 + sa;
				double hb = rad * rad - (py - cy1) * (py - cy1);
				int col = 0x9AF0EAFF;
				if (hb <= 0) {
					ctx.fill((int) Math.round(a0), (int) py, (int) Math.round(a1), (int) py + 1, applyAlpha(col));
					continue;
				}
				double sb = Math.sqrt(hb), b0 = cx1 - sb;
				if (b0 > a0) {
					ctx.fill((int) Math.round(a0), (int) py, (int) Math.round(Math.min(b0, a1)), (int) py + 1, applyAlpha(col));
				}
			}
		}
	}

	/**
	 * 49-283차(사용자: "네온 색감은 유지하고 조금 더 사이버펑크 UI 느낌"): 네온 판 장식 - 옅은 주사선(3줄마다), 모서리 바깥 굵은 꺾쇠
	 * (왼쪽 위 시안, 오른쪽 아래 분홍), 위 테두리의 기울어진 시안 탭과 눈금, 아래쪽 분홍 사선 줄무늬.
	 */
	public static void cyberDecor(DrawContext ctx, int x, int y, int w, int h, int cut) {
		if (w < 60 || h < 40) {
			return;
		}
		// 주사선
		for (int j = y + 4; j < y + h - 4; j += 3) {
			int l = x + 3 + (j >= y + h - cut ? j - (y + h - cut) + 1 : 0);
			int rr = x + w - 3 - (j < y + cut ? (y + cut) - j : 0);
			ctx.fill(l, j, rr, j + 1, applyAlpha(0x0A20F0FF));
		}
		// 바깥 꺾쇠
		// 49-285차(사용자: "모서리에 핑크가 약간 이상해"): 꺾쇠를 2px로 가늘게, 판에 4px 붙여서, 오른쪽 아래도 시안(분홍 덩어리 없앰)
		int L = Math.min(22, Math.min(w, h) / 4), t = 2;
		ctx.fill(x - 4, y - 4, x - 4 + L, y - 4 + t, applyAlpha(NEON_CYAN));
		ctx.fill(x - 4, y - 4, x - 4 + t, y - 4 + L, applyAlpha(NEON_CYAN));
		ctx.fill(x + w + 4 - L, y + h + 4 - t, x + w + 4, y + h + 4, applyAlpha(NEON_CYAN));
		ctx.fill(x + w + 4 - t, y + h + 4 - L, x + w + 4, y + h + 4, applyAlpha(NEON_CYAN));
		// 위 테두리 탭(기울어진 시안 덩어리) + 눈금
		int tabX = x + 22, tabW = Math.min(90, w / 4);
		for (int j = 0; j < 4; j++) {
			ctx.fill(tabX + j, y - 4 + j, tabX + tabW - 4 + j, y - 3 + j, applyAlpha(NEON_CYAN));
		}
		for (int i = 0; i < 6; i++) {
			int tx = tabX + tabW + 8 + i * 5;
			ctx.fill(tx, y - 3, tx + 2, y, applyAlpha(i % 3 == 2 ? NEON_PINK : 0xAA20F0FF));
		}
		// 아래쪽 분홍 사선 줄무늬(경고 띠)
		int sx0 = x + w - cut - 70, sy0 = y + h + 3;
		if (sx0 > x + cut + 10) {
			for (int i = 0; i < 8; i++) {
				int bx = sx0 + i * 7;
				for (int j = 0; j < 3; j++) {
					ctx.fill(bx + j, sy0 + j, bx + j + 3, sy0 + j + 1, applyAlpha(0xCCFF3CC8));
				}
			}
		}
	}

	/** 설정 줄 바탕. 미드나잇 = 흰색 4%, 네온 = 분홍 6% + 왼쪽 2px 분홍 띠. 그리면 true. */
	public static boolean skinRow(DrawContext ctx, int x, int y, int w, int h) {
		if (midnight()) {
			roundRect(ctx, x, y, w, h, Math.min(5, h / 2), 0x0BFFFFFF);
			ctx.fill(x + 4, y, x + w - 4, y + 1, applyAlpha(0x1CB597FF));   // 49-283차: 윗변 보라 빛 한 줄
			return true;
		}
		if (neon()) {
			ctx.fill(x, y, x + w, y + h, applyAlpha(0x0FFF3CC8));
			ctx.fill(x, y, x + 2, y + h, applyAlpha(NEON_PINK));
			return true;
		}
		return false;
	}

	/**
	 * 크림 스킨 상자 아래.
	 * 49-326차(사용자: 게시판 시안 사진 + "크림 UI를 너무 입체감 말고 깔끔한 입체 UI로"): 번지는 갈색 그림자 두 겹 대신 시안처럼
	 * 상자 아래로 2px 내려 깐 또렷한 베이지 띠(아래 두께) 하나. 그 위에 테두리와 속을 그리면 바닥에 살짝 놓인 깔끔한 카드가 된다.
	 */
	public static final int CREAM_LIP = 0xFFE4D5B9;

	public static void softShadow(DrawContext ctx, int x, int y, int w, int h, int r) {
		roundRect(ctx, x, y + 2, w, h, r, CREAM_LIP);
	}

	public static void panel3d(DrawContext ctx, int x, int y, int w, int h, int r) {
		if (skinPanel(ctx, x, y, w, h, r)) {
			return;   // 49-279차
		}
		if (soft()) {
			// 49-326차: 큰 판도 번지는 그림자 대신 3px 아래 두께 + 1px 테두리(깔끔한 입체)
			r += 4;
			roundRect(ctx, x, y + 3, w, h, r, 0xFFDCCAA6);
			roundRect(ctx, x, y, w, h, r, 0xFFE3D0AE);
			roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), surfaceBg());
			return;
		}
		roundRect(ctx, x - 2, y, w + 4, h + 7, r + 1, 0x38000000);
		roundRect(ctx, x, y + 3, w, h, r, surfaceEdge());
		roundRect(ctx, x, y, w, h, r, lightTheme() ? 0xFFDCC8A4 : LunaTheme.mix(surfaceLine(), 0xFFFFFFFF, 0.06f));
		roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), surfaceBg());
	}

	/** 카드: 아래 두께 2px + 테두리 + 속. fill/border가 0이면 기본 카드색. */
	public static void card3d(DrawContext ctx, int x, int y, int w, int h, int r, int fill, int border) {
		if (w <= 2 || h <= 2) {
			return;
		}
		if (skinCard(ctx, x, y, w, h, r, fill == 0 ? surfaceCard() : fill, border == 0 ? surfaceLine() : border)) {
			return;   // 49-279차
		}
		if (soft()) {
			r = Math.min(r + 3, Math.min(w, h) / 2);
			softShadow(ctx, x, y, w, h, r);
			roundRect(ctx, x, y, w, h, r, border == 0 ? surfaceLine() : border);
			roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill == 0 ? surfaceCard() : fill);
			return;
		}
		roundRect(ctx, x, y + 2, w, h, r, surfaceEdge());
		roundRect(ctx, x, y, w, h, r, border == 0 ? surfaceLine() : border);
		roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill == 0 ? surfaceCard() : fill);
	}

	/** 카드(호버 0~1, 고름 = 테마색 테두리/속). */
	public static void card3d(DrawContext ctx, int x, int y, int w, int h, float hov, boolean selected) {
		int fill = lerpColor(surfaceCard(), LunaTheme.mix(surfaceCard(), 0xFF000000 | LunaTheme.TEXT, 0.05f), hov);
		int border = lerpColor(surfaceLine(), LunaTheme.mix(surfaceLine(), 0xFF000000 | LunaTheme.TEXT, 0.15f), hov);
		if (selected) {
			fill = LunaTheme.mix(fill, ACCENT | 0xFF000000, 0.08f);
			border = LunaTheme.mix(surfaceLine(), ACCENT | 0xFF000000, 0.6f);
		}
		card3d(ctx, x, y, w, h, 4, fill, border);
	}

	/** 입력칸: 깊은 속 + 테두리(초점이면 테마색) + 아래 두께 1px. */
	public static void field3d(DrawContext ctx, int x, int y, int w, int h, int r, boolean focused, boolean hovered) {
		if (w <= 2 || h <= 2) {
			return;
		}
		if (soft()) {
			r = Math.min(r + 2, Math.min(w, h) / 2);
			roundRect(ctx, x, y + 1, w, h, r, CREAM_LIP);   // 49-326차: 1px 아래 두께
		} else {
			roundRect(ctx, x, y + 1, w, h, r, surfaceEdge());
		}
		int border = focused ? LunaTheme.mix(surfaceLine(), ACCENT | 0xFF000000, 0.7f)
			: hovered ? LunaTheme.mix(surfaceLine(), 0xFF000000 | LunaTheme.TEXT, 0.15f) : surfaceLine();
		roundRect(ctx, x, y, w, h, r, border);
		roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), surfaceField());
		if (!soft()) {
			ctx.fill(x + r, y + 1, x + w - r, y + 2, applyAlpha(lightTheme() ? 0x0C000000 : 0x40000000));
		}
	}

	private static int buttonBase(int kind) {
		return switch (kind) {
			case B_PRIMARY -> ACCENT | 0xFF000000;
			case B_GOOD -> GOOD_BASE;
			case B_DANGER -> DANGER_BASE;
			default -> lightTheme() ? 0xFFFBF3E2 : LunaTheme.mix(surfaceCard(), 0xFF000000 | LunaTheme.TEXT, 0.07f);
		};
	}

	/** 입체 버튼 몸통(글자는 부르는 쪽이 buttonText 색으로). hov = 0~1. */
	public static void button3d(DrawContext ctx, int x, int y, int w, int h, int r, int kind, float hov) {
		if (w <= 2 || h <= 2) {
			return;
		}
		if ((kind == B_NEUTRAL || kind == B_PRIMARY) && skinButton(ctx, x, y, w, h, kind == B_PRIMARY, hov)) {
			return;   // 49-279차
		}
		int base = buttonBase(kind);
		if (hov > 0f) {
			base = kind == B_NEUTRAL && lightTheme() ? darken(base, 1f - 0.04f * hov) : lighten(base, 0.10f * hov);
		}
		int edge = kind == B_NEUTRAL ? surfaceEdge() : darken(base, 0.42f);
		int border = kind == B_NEUTRAL
			? lerpColor(LunaTheme.mix(surfaceLine(), 0xFF000000 | LunaTheme.TEXT, 0.10f),
				LunaTheme.mix(surfaceLine(), 0xFF000000 | LunaTheme.TEXT, 0.22f), hov)
			: darken(base, 0.72f);
		int top = kind == B_NEUTRAL ? lighten(base, lightTheme() ? 0.6f : 0.05f) : lighten(base, 0.14f);
		if (soft()) {
			// 크림: 더 둥글게. 49-326차(깔끔한 입체): 그림자, 그라데이션, 윗줄 빛 없이 단색 속 + 테두리 + 2px 아래 두께
			r = Math.min(r + 3, h / 2);
			int fillC = kind == B_NEUTRAL ? lerpColor(0xFFFFFDF7, 0xFFFBF3E2, hov) : base;
			roundRect(ctx, x, y + 2, w, h, r, kind == B_NEUTRAL ? 0xFFDDCBA8 : darken(base, 0.62f));
			roundRect(ctx, x, y, w, h, r, kind == B_NEUTRAL ? lerpColor(0xFFE6D6B8, 0xFFD6C29C, hov) : darken(base, 0.80f));
			roundRect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fillC);
			return;
		}
		roundRect(ctx, x, y + 2, w, h, r, edge);
		roundRect(ctx, x, y, w, h, r, border);
		roundRectGradient(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), top, base);
		if (w > 2 * r + 2) {
			ctx.fill(x + Math.max(2, r), y + 1, x + w - Math.max(2, r), y + 2,
				applyAlpha(kind == B_NEUTRAL ? (lightTheme() ? 0x80FFFFFF : 0x14FFFFFF) : 0x33FFFFFF));
		}
	}

	/**
	 * 49-256차: 아이콘 버튼 이름표(툴팁). 테마 카드 색 바탕 + 테두리 + 본문 글자색 - 예전엔 검은 바탕에 고정 글자색이라 밝은(크림) 테마에서
	 * 글자가 바탕과 같은 어두운 색으로 바뀌어 안 보였다(사용자: "글 색이 하나도 안보여 테마랑 맞지도 않고"). 높이 14.
	 */
	public static void tipBox(DrawContext ctx, TextRenderer tr, String s, int x, int y) {
		int w = width(tr, s) + 10;
		roundRect(ctx, x, y + 1, w, 14, 4, 0x26000000);
		roundRect(ctx, x, y, w, 14, 4, surfaceLine());
		roundRect(ctx, x + 1, y + 1, w - 2, 12, 3, surfaceCard());
		text(ctx, tr, s, x + 5, textY(y, 14), TEXT);
	}

	public static int tipWidth(TextRenderer tr, String s) {
		return width(tr, s) + 10;
	}

	/** 버튼 글자색. */
	public static int buttonText(int kind, float hov) {
		if (kind == B_NEUTRAL || kind == B_PRIMARY) {
			int sk = skinButtonText(kind == B_PRIMARY, hov);   // 49-279차
			if (sk != 0) {
				return sk;
			}
		}
		return switch (kind) {
			case B_PRIMARY -> LunaTheme.ON_ACCENT;
			case B_GOOD, B_DANGER -> 0xFFF7FAF4;
			default -> lerpColor(TEXT_SUB, TEXT, Math.max(0.4f, hov));
		};
	}

	/** 버튼 + 가운데 글자 한 번에. */
	public static void button3d(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label, int kind, float hov) {
		button3d(ctx, x, y, w, h, Math.min(4, h / 2), kind, hov);
		if (label != null && !label.isEmpty()) {
			String t = ellipsize(tr, label, w - 6);
			text(ctx, tr, t, x + (w - width(tr, t)) / 2, textY(y, h), buttonText(kind, hov));
		}
	}

	/** 네모 아이콘 버튼(뒤로, 닫기, 톱니 …). active면 테마색 테두리 + 아이콘. */
	public static void iconButton3d(DrawContext ctx, TextRenderer tr, int x, int y, int size, String glyph, float hov, boolean active) {
		card3d(ctx, x, y, size, size, hov, active);
		int c = active ? (ACCENT | 0xFF000000) : lerpColor(TEXT_SUB, TEXT, hov);
		if (size >= 18) {
			LunaIcons.drawInBox(ctx, tr, glyph, x, y, size, c);
		} else {
			LunaIcons.draw(ctx, tr, glyph, x + (size - 10) / 2, iconY(y, size), c);
		}
	}

	/** 작은 알약 버튼. */
	public static void pillButton(DrawContext ctx, TextRenderer tr, int x, int y, int w, int h, String label,
			boolean hovered, boolean accent) {
		// 49-227차: 공용 입체 버튼(button3d)으로 - 강조 = 테마색 채움, 아니면 회색 몸통
		int kind = accent ? B_PRIMARY : B_NEUTRAL;
		button3d(ctx, x, y, w, h, Math.min(4, h / 2), kind, hovered ? 1f : 0f);
		int tw = width(tr, label);
		text(ctx, tr, label, x + (w - tw) / 2, textY(y, h), buttonText(kind, hovered ? 1f : 0f));
	}

	// =====================================================================
	// 텍스트(동봉 폰트)
	// =====================================================================

	// ---- 세로 정렬(49-10차, 49-13차 폰트 모드 대응) ----
	// 49-39차(사용자: "아이콘이랑 글 높낮이도 안 맞잖아"): 예전 상수(아이콘 1.5, 큰 아이콘 −1.75, 마크 글자 4.0)는
	// 1.21(FreeType) 기준 실측이라 1.20.4 이하(stb_truetype - 세로 규칙이 다름)에선 아이콘이 1~7px 내려앉았고,
	// 마크 글자 중심도 실제(대문자 0..7 → 3.5)보다 0.5 컸다. 폰트 JSON을 두 벌로 나눠 두 시대 모두
	// 잉크 밴드를 같게 맞췄고(LunaCompat.textBandTop 주석), 그 밴드 기준으로 상수를 다시 잡았다:
	//  마크 기본/한글 픽셀 = 0..7(중심 3.5), 모던 Pretendard = 0.3..8.5(중심 4.4), 아이콘 11px = 3.5, 큰 아이콘 17px = 3.5.
	private static final float ICON_CENTER = 3.5f;

	/** 상자(boxY..boxY+boxH) 세로 정중앙에 오는 텍스트 draw y. */
	/**
	 * 49-236차(사용자: "폰트가 한 칸씩 밀렸어"): 반올림(.5 → 위로 1)이 글자와 아이콘을 상자 가운데보다 1px 아래로 보냈다.
	 * 글자 잉크가 8줄(0..7)이고 상자가 짝수 높이면 늘 .5가 나와 전부 한 칸 내려앉았다(입체 버튼은 아래 두께까지 있어 더 처져
	 * 보였다). .5는 위쪽으로 붙인다.
	 */
	private static int roundHalfDown(float v) {
		// 49-241차(사용자: "높은 버전만 밀렸던 거라 아랫버전은 또 다시 밀려"): .5를 위로 붙이는 건 26.x(src-26x)에서만.
		// 1.21.11 이하는 예전 반올림이 맞았다(1.21.11 GUI 2 실측) - 여기서는 그대로 반올림.
		return Math.round(v);
	}

	public static int textY(int boxY, int boxH) {
		return boxY + roundHalfDown(boxH / 2f - LunaCompat.textVisualCenter());
	}

	/** 상자 세로 정중앙에 오는 일반(11px) 아이콘 draw y. */
	public static int iconY(int boxY, int boxH) {
		return boxY + roundHalfDown(boxH / 2f - ICON_CENTER);
	}

	/** ty에 그린 텍스트와 시각적 중심을 맞추는(같은 줄) 아이콘 draw y. */
	public static int iconBesideText(int textDrawY) {
		return textDrawY + roundHalfDown(LunaCompat.textVisualCenter() - ICON_CENTER);
	}

	// 49-20차: 큰 아이콘(iconslg.ttf size 17) 실측 중심. 버튼을 키우면서 큰 아이콘을
	// 쓰게 돼 세로 정렬 헬퍼가 하나 더 필요해짐. 49-39차: 폰트 JSON에서 베이스라인을 12로 맞춰 작은 아이콘·글자와 같은 3.5.
	private static final float ICON_LG_CENTER = 3.5f;

	/** 49-79차: 중간(16px) 아이콘의 잉크 중심도 같은 3.5(폰트 JSON 베이스라인을 그렇게 구웠다). */
	public static int iconMdY(int boxY, int boxH) {
		return boxY + roundHalfDown(boxH / 2f - ICON_LG_CENTER);
	}

	/** 49-245차: iconMdY의 반올림 전 값(화면 픽셀 단위로 맞춰 그릴 때). */
	public static float iconMdYf(int boxY, int boxH) {
		return boxY + boxH / 2f - ICON_LG_CENTER;
	}

	/** 상자 세로 정중앙에 오는 큰(17px) 아이콘 draw y. */
	public static int iconLgY(int boxY, int boxH) {
		return boxY + roundHalfDown(boxH / 2f - ICON_LG_CENTER);
	}

	public static int width(TextRenderer tr, String s) {
		return LunaCompat.textWidth(tr, LunaGfx.text(s));
	}

	public static void text(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
		if (hudDepth > 0 && kr.lunaslight.mod.util.LunaCompat.hudCream) {
			s = kr.lunaslight.mod.util.LunaCompat.creamText(s);   // 49-257차: 크림 HUD 상자 위
			color = kr.lunaslight.mod.util.LunaCompat.creamColor(color);
		}
		ctx.drawText(tr, LunaGfx.text(s), x, y, applyAlpha(color), false);
	}

	public static void textCentered(DrawContext ctx, TextRenderer tr, String s, int cx, int y, int color) {
		text(ctx, tr, s, cx - width(tr, s) / 2, y, color);
	}

	// ---- 49-25차: 굵은 글씨(타이틀·일시정지 버튼, 화면 제목) - 모던 모드는 동봉 Pretendard ExtraBold(title.ttf) ----
	// 49-37차(사용자: "글이 2개로 겹쳐 보이잖아"): 마크/픽셀 글꼴 모드에서 1px 옆에 한 번 더 그려 굵게 흉내 내던 것을
	// 없앰. 픽셀 글꼴은 획이 1px라 GUI 배율 2 이상에서 두 획이 떨어져 "글자가 두 개로 겹친" 것처럼 보였다
	// (유니폰트·갈무리 둘 다). 픽셀 글꼴은 그냥 한 번만 그린다 - 굵기는 모던 글꼴에서만.

	public static int widthBold(TextRenderer tr, String s) {
		Object t = LunaGfx.titleText(s);
		if (t instanceof net.minecraft.text.Text txt) {
			return LunaCompat.textWidth(tr, txt);
		}
		return width(tr, s);
	}

	public static void textBold(DrawContext ctx, TextRenderer tr, String s, int x, int y, int color) {
		Object t = LunaGfx.titleText(s);
		if (t instanceof net.minecraft.text.Text txt) {
			ctx.drawText(tr, txt, x, y, applyAlpha(color), false);
			return;
		}
		text(ctx, tr, s, x, y, color);
	}

	/** 폭에 맞게 "…"로 자름. */
	public static String ellipsize(TextRenderer tr, String text, int maxWidth) {
		if (text == null) {
			return "";
		}
		if (width(tr, text) <= maxWidth) {
			return text;
		}
		String dots = "…";
		int dotsW = width(tr, dots);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			sb.append(text.charAt(i));
			if (width(tr, sb.toString()) + dotsW > maxWidth) {
				sb.setLength(Math.max(0, sb.length() - 1));
				break;
			}
		}
		return sb + dots;
	}

	/**
	 * 49-21차: 바닐라 폰트 + §서식 코드가 섞인 문자열용 ellipsize(HUD/채팅 검색). 폭 측정을
	 * LunaCompat.getTextWidth(현재 글꼴 모드)로 하고, §코드 한가운데서 잘리지 않게 한다.
	 */
	public static String ellipsizeFormatted(TextRenderer tr, String text, int maxWidth) {
		if (text == null) {
			return "";
		}
		if (LunaCompat.getTextWidth(tr, text) <= maxWidth) {
			return text;
		}
		String dots = "…";
		int dotsW = LunaCompat.getTextWidth(tr, dots);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				sb.append(c).append(text.charAt(i + 1));
				i++;
				continue;
			}
			sb.append(c);
			if (LunaCompat.getTextWidth(tr, sb.toString()) + dotsW > maxWidth) {
				sb.setLength(Math.max(0, sb.length() - 1));
				break;
			}
		}
		return sb + dots;
	}

	// =====================================================================
	// 49-24차: 마인크래프트 인벤토리 느낌(바닐라 GUI 회색 판 + 베벨 슬롯) - 셜커 격자/핫바 줄 교체 미리보기용
	// =====================================================================

	public static final int MC_PANEL = 0xFFC6C6C6;
	public static final int MC_SLOT = 0xFF8B8B8B;

	/** 바닐라 GUI 판: 회색 바탕 + 왼/위 흰 하이라이트 + 오른/아래 어두운 그림자(모서리 1px 깎음). base로 색 틴트 가능. */
	public static void mcPanel(DrawContext ctx, int x, int y, int w, int h, int base) {
		int light = lerpColor(base, 0xFFFFFFFF, 0.55f);
		int dark = lerpColor(base, 0xFF000000, 0.6f);
		ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, applyAlpha(base));
		ctx.fill(x + 1, y, x + w - 2, y + 1, applyAlpha(0xFF000000));
		ctx.fill(x + 1, y + h - 1, x + w - 2, y + h, applyAlpha(0xFF000000));
		ctx.fill(x, y + 1, x + 1, y + h - 2, applyAlpha(0xFF000000));
		ctx.fill(x + w - 1, y + 1, x + w, y + h - 2, applyAlpha(0xFF000000));
		ctx.fill(x + 1, y + 1, x + w - 2, y + 3, applyAlpha(light));
		ctx.fill(x + 1, y + 1, x + 3, y + h - 2, applyAlpha(light));
		ctx.fill(x + 2, y + h - 3, x + w - 1, y + h - 1, applyAlpha(dark));
		ctx.fill(x + w - 3, y + 2, x + w - 1, y + h - 1, applyAlpha(dark));
	}

	public static void mcPanel(DrawContext ctx, int x, int y, int w, int h) {
		mcPanel(ctx, x, y, w, h, MC_PANEL);
	}

	/** 바닐라 인벤토리 슬롯(18×18): 회색 바닥 + 왼/위 어둡고 오른/아래 밝은 1px. */
	public static void mcSlot(DrawContext ctx, int x, int y) {
		ctx.fill(x, y, x + 18, y + 18, applyAlpha(MC_SLOT));
		ctx.fill(x, y, x + 17, y + 1, applyAlpha(0xFF373737));
		ctx.fill(x, y, x + 1, y + 17, applyAlpha(0xFF373737));
		ctx.fill(x + 1, y + 17, x + 18, y + 18, applyAlpha(0xFFFFFFFF));
		ctx.fill(x + 17, y + 1, x + 18, y + 18, applyAlpha(0xFFFFFFFF));
	}

	// =====================================================================
	// 애니메이션(프레임 독립, 지수 보간)
	// =====================================================================

	private static final Map<String, float[]> ANIM = new HashMap<>();
	private static long lastFrameNanos;
	private static float frameDt;

	/** 이번 프레임의 경과 시간(초). 부드러운 스크롤 등 화면 쪽 보간에 사용. */
	public static float dt() {
		return frameDt;
	}

	/** 매 프레임 render 진입부에서 한 번 호출 - 프레임 시간 계산. */
	public static void beginFrame() {
		long now = System.nanoTime();
		// 49-25차: 한 프레임 안에서 두 번 불리면(서랍 화면이 뒤에 부모 화면을 그릴 때) 두 번째는 무시 - dt가 0이 되면 안 됨
		if (lastFrameNanos != 0 && now - lastFrameNanos < 200_000L) {
			return;
		}
		frameDt = lastFrameNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
		lastFrameNanos = now;
	}

	/**
	 * key의 현재 값을 target 쪽으로 speed(1/초 단위의 반응 속도, 10~20이 자연스러움)만큼 접근시켜
	 * 돌려줌. 처음 보는 key는 target에서 시작(첫 프레임 튐 방지).
	 */
	public static float anim(String key, float target, float speed) {
		float[] v = ANIM.get(key);
		if (v == null) {
			v = new float[]{target};
			ANIM.put(key, v);
			return target;
		}
		float k = 1f - (float) Math.exp(-speed * frameDt);
		v[0] += (target - v[0]) * k;
		if (Math.abs(target - v[0]) < 0.002f) {
			v[0] = target;
		}
		return v[0];
	}

	/** 처음 보는 key를 특정 시작값으로 두고 싶을 때(패널 열림 페이드 등). */
	public static float animFrom(String key, float start, float target, float speed) {
		if (!ANIM.containsKey(key)) {
			ANIM.put(key, new float[]{start});
		}
		return anim(key, target, speed);
	}

	public static void resetAnim(String key) {
		ANIM.remove(key);
	}

	// =====================================================================
	// 유틸
	// =====================================================================

	public static boolean in(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	/** 0xAARRGGBB에서 알파만 바꿈. */
	public static int withAlpha(int argb, int alpha) {
		return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
	}

	public static int lerpColor(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int ra = Math.round(aa + (ba - aa) * t);
		int rr = Math.round(ar + (br - ar) * t);
		int rg = Math.round(ag + (bg - ag) * t);
		int rb = Math.round(ab + (bb - ab) * t);
		return (ra << 24) | (rr << 16) | (rg << 8) | rb;
	}
}
