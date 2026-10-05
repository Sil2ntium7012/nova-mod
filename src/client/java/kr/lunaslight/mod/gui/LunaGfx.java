package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.LunaClientMod;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Method;
import java.util.function.Function;

/**
 * 48차(2026-09-01): "마인크래프트 느낌"을 벗기 위한 그래픽 호환층.
 *
 *  1. 폰트 - 픽셀 폰트 대신 모드에 동봉한 TTF(Pretendard 서브셋, assets/lunaslight/font/ui.json)를
 *     설정 UI 텍스트에 입힘. Style#withFont가 1.21.9+에서 Identifier → StyleSpriteSource.Font로
 *     바뀌어서 리플렉션으로 둘 다 지원.
 *  2. 안티앨리어싱 도형 - 1px 계단 대신 부드러운 원 텍스처(textures/gui/round.png, blur=true)를
 *     DrawContext#drawTexture로 그림. drawTexture 시그니처가 세 시대(1.20.x Identifier 우선 +
 *     RenderSystem.setShaderColor / 1.21.2~5 Function<Identifier,RenderLayer> / 1.21.6+
 *     RenderPipeline)로 갈려서 파라미터 타입을 보고 골라 호출. 전부 실패하면 false를 돌려주고
 *     LunaDraw가 예전 fill 방식으로 폴백 - 어느 버전에서도 UI가 사라지진 않음.
 */
public final class LunaGfx {
	private LunaGfx() {
	}

	private static final String NS = "lunaslight";
	private static Identifier roundTexture;
	private static boolean textureBroken;

	// drawTexture 해석 결과 캐시
	private static Method drawTextureMethod;
	private static int drawTextureMode = -1; // 0=1.20.x(Identifier 우선), 1=Function, 2=RenderPipeline, -2=없음
	private static Object pipelineOrFunction;
	private static Method setShaderColor;
	// 49-36차: 1.20.x는 fill이 GUI RenderLayer로 그리고 끝나며 블렌딩을 꺼 버려서(RenderPhase.Transparency
	// endDrawing → disableBlend), 바로 뒤에 즉시 그리는 drawTexture가 알파를 무시하고 불투명하게 찍혔다
	// (연한 흰 테두리(0x16FFFFFF)의 둥근 모서리만 새하얀 괄호처럼 보이던 원인). 그리기 전에 블렌딩을 켠다.
	private static Method enableBlend, defaultBlendFunc;

	// ---------------------------------------------------------------- 식별자

	public static Identifier id(String path) {
		return LunaCompat.identifier(NS, path);
	}

	/**
	 * 49-21차: 브랜드 워드마크 이미지(N★VA CLIENT - Pretendard Black/SemiBold + 별, wordmark.png의
	 * 0,0~492×112 영역). 높이 h로 그리고 폭을 돌려줌. 텍스처를 못 그리면 -1(호출부가 글자로 폴백).
	 */
	public static int drawWordmark(DrawContext ctx, int x, int y, int h, int argb) {
		int w = Math.round(h * 492f / 112f);
		return drawTex(ctx, id("textures/gui/wordmark.png"), x, y, w, h, 0, 0, 492, 112, 512, argb) ? w : -1;
	}

	public static int wordmarkWidth(int h) {
		return Math.round(h * 492f / 112f);
	}

	/** 49-21차: 바닐라(minecraft 네임스페이스) 텍스처 식별자. */
	public static Identifier mcId(String path) {
		return LunaCompat.identifier("minecraft", path);
	}

	// ---------------------------------------------------------------- 로비 배경(49-38차 타이틀 → 49-39차 공용)

	private static final int LOBBY_W = 1600, LOBBY_H = 900;
	private static boolean lobbyBroken;

	/**
	 * 흐린 로비 이미지(textures/gui/title_bg.png)를 화면에 꽉 차게(cover - 짧은 쪽을 채우고 긴 쪽은 가운데 기준으로
	 * 잘라냄) 깐다. 텍스처를 못 그리면 false(호출부가 파노라마/그라데이션 폴백). 위에 어둡게 덮는 건 호출부 몫.
	 */
	public static boolean drawLobbyBackground(DrawContext ctx, int width, int height) {
		return drawLobbyBackground(ctx, width, height, 1f, 0.5f, 0.5f);
	}

	/**
	 * 49-41차(사용자: "메인 화면 배경 아주 조금만 일렁이게"): zoom(1 = cover 딱 맞춤, 1.03 = 3% 확대)과
	 * 잘려 나가는 여분 안에서의 위치(panX/panY 0~1, 0.5 = 가운데)를 받아 살짝 움직이는 배경을 만든다.
	 * 텍스처 한 장을 위치만 바꿔 그리는 것이라 비용은 정지 배경과 같다.
	 */
	public static boolean drawLobbyBackground(DrawContext ctx, int width, int height, float zoom, float panX, float panY) {
		if (lobbyBroken || width <= 0 || height <= 0) {
			return false;
		}
		// 49-66차(5-8 "메인화면 배경 움직임이 뚝뚝 끊김"): 원인은 **정수 반올림**이었다.
		// 한 바퀴가 30~50초인 아주 느린 움직임이라 위치도 크기도 1px 바뀌는 데 1초쯤 걸리는데,
		// 그리는 자리·크기가 전부 정수라 그 1초 동안 멈췄다가 1px 튀기를 반복했다(= 뚝뚝).
		//
		// 고친 방법: **크기는 확대 없이 정수로 한 번만** 잡고(창 크기가 그대로면 값이 안 변한다),
		// 일렁임(확대)과 흐름(위치)은 **행렬**로 준다 - 행렬은 소수를 받으므로 계단이 안 생긴다.
		// 비용은 그대로다(텍스처 한 장을 한 번 그리는 것).
		float z = Math.max(1f, zoom);
		float base = Math.max(width / (float) LOBBY_W, height / (float) LOBBY_H);
		int dw = Math.round(LOBBY_W * base);
		int dh = Math.round(LOBBY_H * base);
		// 확대된 뒤의 크기를 기준으로 "잘려 나가는 여분" 안에서 위치를 잡는다.
		float fx = (width - dw * z) * Math.max(0f, Math.min(1f, panX));
		float fy = (height - dh * z) * Math.max(0f, Math.min(1f, panY));
		boolean smooth = LunaCompat.guiTransformSupported(ctx);
		if (smooth) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, fx, fy);
			LunaCompat.guiScale(ctx, z, z);
		}
		// 행렬을 못 쓰는 버전에서는 예전처럼 정수 자리에 그린다(느리게 끊기지만 안 깨진다).
		int dx = smooth ? 0 : Math.round(fx);
		int dy = smooth ? 0 : Math.round(fy);
		boolean ok = drawTexRect(ctx, id("textures/gui/title_bg.png"), dx, dy, dw, dh,
			0, 0, LOBBY_W, LOBBY_H, LOBBY_W, LOBBY_H, 0xFFFFFFFF);
		if (smooth) {
			LunaCompat.guiPop(ctx);
		}
		if (!ok) {
			lobbyBroken = true;
		}
		return ok;
	}

	/**
	 * 49-39차(사용자: "UI도 메인 화면 느낌 쪽으로"): Luna 화면들의 공통 배경.
	 *  · 월드 밖(타이틀에서 열었을 때): 바닐라 renderBackground는 1.20.x에서 흙 타일을 깔아 "구석기" 느낌이 났다 →
	 *    타이틀과 같은 흐린 로비 이미지 위에 dim을 덮는다(메인 화면과 이어지는 룩).
	 *  · 월드 안: 바닐라 renderBackground(1.20.5+ 블러, 그 아래는 그라데이션) → 실패 시 dim만.
	 */
	public static void drawScreenBackdrop(Object screen, DrawContext ctx, int width, int height,
			int mouseX, int mouseY, float delta, int dim) {
		boolean inWorld;
		try {
			inWorld = net.minecraft.client.MinecraftClient.getInstance().world != null;
		} catch (Throwable ignored) {
			inWorld = true;
		}
		if (!inWorld && drawLobbyBackground(ctx, width, height)) {
			ctx.fill(0, 0, width, height, dim);
			return;
		}
		if (!LunaCompat.renderScreenBackground(screen, ctx, mouseX, mouseY, delta)) {
			ctx.fill(0, 0, width, height, dim);
		}
	}

	// ---------------------------------------------------------------- 하트

	private static Identifier heartTex;
	// 0 = sprites/hud/heart/full.png(1.20.2+), 1 = icons.png(그 아래), -1 = 아직 모름, -2 = 없음
	private static int heartMode = -1;

	/**
	 * 49-32차: 바닐라 하트를 그대로 그린다(통계 화면의 "잃은 체력" 등).
	 * 1.20.2부터는 hud/heart/full.png 한 장, 그 아래 버전은 icons.png의 52,0 자리.
	 */
	public static boolean drawHeart(DrawContext ctx, int x, int y, int size, int argb) {
		if (heartMode == -1) {
			resolveHeart();
		}
		if (heartMode < 0) {
			return false;
		}
		return heartMode == 0
				? drawTex(ctx, heartTex, x, y, size, size, 0, 0, 9, 9, 9, argb)
				: drawTex(ctx, heartTex, x, y, size, size, 52, 0, 9, 9, 256, argb);
	}

	private static void resolveHeart() {
		heartMode = -2;
		try {
			Identifier sprite = mcId("textures/gui/sprites/hud/heart/full.png");
			if (hasResource(sprite)) {
				heartTex = sprite;
				heartMode = 0;
				return;
			}
			Identifier icons = mcId("textures/gui/icons.png");
			if (hasResource(icons)) {
				heartTex = icons;
				heartMode = 1;
			}
		} catch (Throwable t) {
			warnOnce("heart", t);
		}
	}

	/** 리소스가 실제로 있는지(버전마다 getResource가 Optional이거나 예외를 던져서 반사로 감싼다). */
	private static boolean hasResource(Identifier id) {
		try {
			net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
			Object manager = client == null ? null : client.getResourceManager();
			if (manager == null) {
				return false;
			}
			for (Method m : manager.getClass().getMethods()) {
				if (m.getParameterCount() != 1 || !LunaCompat.nameMatches(manager.getClass(), "getResource", m.getName())
						|| !m.getParameterTypes()[0].isInstance(id)) {
					continue;
				}
				m.setAccessible(true);
				Object r = m.invoke(manager, id);
				if (r instanceof java.util.Optional<?> opt) {
					return opt.isPresent();
				}
				return r != null;
			}
		} catch (Throwable ignored) {
			// 예외를 던지는 버전(1.16 등) = 없음
		}
		return false;
	}

	// ---------------------------------------------------------------- 폰트
	// 48-2차: 폰트 Style 해석은 LunaCompat.uiStyle()로 일원화(GUI 배율별 ui1~ui4 폰트 선택 포함 -
	// HUD 텍스트와 설정 화면이 같은 폰트/같은 선명도를 쓰게 됨).

	/**
	 * UI 폰트가 입혀진 Text. 폰트 해석 실패 시 기본 폰트 Text.
	 * 49-36차: Text.literal/MutableText 직접 사용 → LunaCompat.styledText(리플렉션) - 1.15.2~1.18.2에서도 컴파일.
	 */
	public static Text text(String s) {
		String str = s == null ? "" : s;
		// 49-156차: uiText를 거쳐 [리소스팩 글꼴 우선]일 때 기호는 바닐라 글꼴로(LunaCompat.packSplitText)
		Object styled = LunaCompat.uiText(str);
		Text t = styled instanceof Text st ? st : null;
		if (t == null) {
			t = LunaCompat.textLiteral(str);
		}
		return t;
	}

	/**
	 * 49-25차: 타이틀 화면용 굵은 글씨 Text(Pretendard ExtraBold 서브셋, font/title<배율>.json). 모던 폰트 모드일 때만
	 * 있고, 마크 폰트 모드/실패 시 null(호출부가 겹쳐 그리기로 굵게).
	 */
	public static Object titleText(String s) {
		if (LunaCompat.fontMode() != LunaCompat.FONT_MODERN) {
			return null;
		}
		return LunaCompat.styledText(s == null ? "" : s, LunaCompat.fontStyle("title"));
	}

	// ---------------------------------------------------------------- 텍스처

	private static Identifier roundTexture() {
		if (roundTexture == null) {
			roundTexture = id("textures/gui/round.png");
		}
		return roundTexture;
	}

	/**
	 * round.png의 원 일부 영역을 (x,y,w,h)에 색을 입혀 그림. u/v/regionW/regionH는 <b>예전 128x128 한 원 기준</b>
	 * (사분면 64, 온원 128)이라 부르는 쪽은 그대로다.
	 *
	 * <p>49-165차(사용자: "안티엘리어싱, 픽셀이 너무 잘보임"): 예전엔 지름 128 원 하나를 반지름 4~6(GUI 배율 3이면
	 * 화면 12~18px)으로 줄여 그렸다. 밉맵이 없는 GUI 텍스처라 64px 사분면을 12px로 줄이면 2x2 텍셀만 섞여 가장자리가
	 * 계단졌다. 이제 round.png(256x256)에 지름 4/8/12/16/24/32/48/64/128 원이 같이 있고, <b>실제 화면 픽셀 크기</b>
	 * (GUI 배율 곱)에 가장 가까운 원을 골라 1:1에 가깝게 그린다.
	 */
	public static boolean drawRound(DrawContext ctx, int x, int y, int w, int h, int u, int v, int regionW, int regionH, int argb) {
		Identifier tex = roundTexture();
		if (tex == null) {
			return false;
		}
		boolean full = regionW >= 128;
		int want = Math.max(1, Math.round(Math.max(w, h) * (roundPx > 0f ? roundPx : guiScale()) * (full ? 1f : 2f)));
		int d = 128;
		for (int cand : CIRCLE_SIZES) {
			if (cand >= want) {
				d = cand;
				break;
			}
		}
		int ox = CIRCLE_X[indexOf(d)];
		int oy = CIRCLE_Y[indexOf(d)];
		if (full) {
			return drawTex(ctx, tex, x, y, w, h, ox, oy, d, d, 256, argb);
		}
		int half = d / 2;
		int qx = u >= 64 ? 1 : 0;
		int qy = v >= 64 ? 1 : 0;
		return drawTex(ctx, tex, x, y, w, h, ox + qx * half, oy + qy * half, half, half, 256, argb);
	}

	// round.png(256x256) 안 원들의 지름과 왼쪽 위 자리(tools로 만든 배치 - 바꾸면 그림도 같이).
	// 49-256차(사용자: "GUI 바꾸니까 이런 선이 생기는데"): 예전 배치는 원들이 빈틈 없이 붙어 있어서, 설정 화면처럼 1단위가
	// 소수 픽셀이 되는 배율에서 부드러운(선형) 샘플링이 옆 원의 가장자리를 끌어와 카드 모서리에 짧은 선, 버튼 끝에 세로줄이 생겼다.
	// 원마다 둘레에 투명한 2px 이상을 두고 다시 배치했다.
	private static final int[] CIRCLE_SIZES = {4, 8, 12, 16, 24, 32, 48, 64, 128};
	private static final int[] CIRCLE_X = {248, 236, 220, 200, 172, 136, 204, 136, 2};
	private static final int[] CIRCLE_Y = {70, 70, 70, 70, 70, 70, 2, 2, 2};

	private static int indexOf(int d) {
		for (int i = 0; i < CIRCLE_SIZES.length; i++) {
			if (CIRCLE_SIZES[i] == d) {
				return i;
			}
		}
		return CIRCLE_SIZES.length - 1;
	}

	/**
	 * 49-232차: 1단위 = 화면 몇 픽셀인지 덮어쓰기(0 = GUI 배율 그대로). 행렬로 크기를 바꿔 그리는 곳(설정 화면 가상 GUI 2,
	 * 마우스 잔상의 화면 픽셀 그리기)이 잠깐 맞춰 두면 원 텍스처를 실제 픽셀 크기에 맞게 고른다.
	 */
	public static float roundPx;

	/** 49-245차: 지금 1단위가 화면 몇 픽셀인지(설정 화면 가상 GUI 2면 그 값, 아니면 GUI 배율). */
	public static float pxPerUnit() {
		return roundPx > 0f ? roundPx : guiScale();
	}

	private static float cachedGuiScale = 2f;
	private static long guiScaleAt;

	/** 지금 GUI 배율(창 설정). 프레임마다 여러 번 불려서 0.5초 캐시. */
	private static float guiScale() {
		long now = System.currentTimeMillis();
		if (now - guiScaleAt > 500L) {
			guiScaleAt = now;
			try {
				cachedGuiScale = (float) (kr.lunaslight.mod.util.WindowAccess.of(net.minecraft.client.MinecraftClient.getInstance()).getScaleFactor());
			} catch (Throwable ignored) {
			}
		}
		return cachedGuiScale;
	}

	/** 임의 텍스처(정사각 texSize)의 (u,v,regionW,regionH) 영역을 색 곱해서 그림. 로고 등에 사용. */
	public static boolean drawTex(DrawContext ctx, Identifier tex, int x, int y, int w, int h,
			int u, int v, int regionW, int regionH, int texSize, int argb) {
		return drawTexRect(ctx, tex, x, y, w, h, u, v, regionW, regionH, texSize, texSize, argb);
	}

	/**
	 * 49-67차(4-20): 이 버전에서 <b>텍스처를 그릴 수 있는지</b>. <b>사진을 보여 주는 화면은 그 사실을 먼저
	 * 물어보고</b>, 못 그리면 빈 칸을 늘어놓는 대신 목록으로 바꿔 보여 준다 - 없는 기능을 있는 척하지 않기 위해서다.
	 * 49-82차까지는 1.15.2~1.19.4가 false였다(shim DrawContext에 drawTexture가 없었다). 이제 shim에도
	 * drawTexture가 있어(compat/legacy-era0|1) 전 버전 true가 기대값이고, 실제로 그리다 터지면 그때 false가 된다.
	 */
	public static boolean texturesUsable(DrawContext ctx) {
		return ctx != null && !textureBroken && resolveDrawTexture(ctx);
	}

	/**
	 * 49-67차(4-20): <b>그때그때 만든 텍스처</b>(스크린샷 보관함의 미리보기 등)를 통째로 그림.
	 *
	 * <p>{@link #drawTexRect}와 달리 <b>실패해도 {@code textureBroken}을 켜지 않는다</b>. 그 깃발은
	 * "이 버전에서는 텍스처 그리기 자체가 안 된다"는 뜻이라, 켜지면 UI 전체가 둥근 모서리를 포기하고
	 * 각진 옛 방식으로 내려간다. 스크린샷 <b>한 장</b>이 깨진 것을 그 신호로 오해하면 안 된다.
	 */
	public static boolean drawImage(DrawContext ctx, Identifier tex, int x, int y, int w, int h,
			int texW, int texH, int argb) {
		boolean was = textureBroken;
		boolean ok = drawTexRect(ctx, tex, x, y, w, h, 0, 0, texW, texH, texW, texH, argb);
		if (!ok) {
			textureBroken = was;
		}
		return ok;
	}

	/** 49-176차: 텍스처 한 부분(아이콘 그림 등)을 그림. drawImage처럼 실패해도 textureBroken을 켜지 않는다. */
	public static boolean drawImageRegion(DrawContext ctx, Identifier tex, int x, int y, int w, int h,
			int u, int v, int regionW, int regionH, int texW, int texH, int argb) {
		boolean was = textureBroken;
		boolean ok = drawTexRect(ctx, tex, x, y, w, h, u, v, regionW, regionH, texW, texH, argb);
		if (!ok) {
			textureBroken = was;
		}
		return ok;
	}

	/** 49-38차: 정사각이 아닌 텍스처(타이틀 배경 1600×900 등)용 - texW/texH를 따로 받는 판. */
	public static boolean drawTexRect(DrawContext ctx, Identifier tex, int x, int y, int w, int h,
			int u, int v, int regionW, int regionH, int texW, int texH, int argb) {
		if (textureBroken || w <= 0 || h <= 0 || tex == null) {
			return false;
		}
		if (!resolveDrawTexture(ctx)) {
			textureBroken = true;
			return false;
		}
		try {
			switch (drawTextureMode) {
				case 2, 1 -> drawTextureMethod.invoke(ctx, pipelineOrFunction, tex, x, y, (float) u, (float) v, w, h, regionW, regionH, texW, texH, argb);
				case 0 -> {
					float a = ((argb >>> 24) & 0xFF) / 255f;
					float r = ((argb >> 16) & 0xFF) / 255f;
					float g = ((argb >> 8) & 0xFF) / 255f;
					float b = (argb & 0xFF) / 255f;
					if (enableBlend != null) {
						enableBlend.invoke(null);
						defaultBlendFunc.invoke(null);
					}
					setShaderColor.invoke(null, r, g, b, a);
					try {
						drawTextureMethod.invoke(ctx, tex, x, y, w, h, (float) u, (float) v, regionW, regionH, texW, texH);
					} finally {
						setShaderColor.invoke(null, 1f, 1f, 1f, 1f);
					}
				}
				default -> {
					return false;
				}
			}
			texSinceBreak = true;   // 49-292차
			return true;
		} catch (Throwable t) {
			warnOnce("drawTexture", t);
			textureBroken = true;
			return false;
		}
	}

	// ---------------------------------------------------------------- 49-292차: 새 GUI 렌더러 층 나누기
	// 사용자(사진): "이거 이상한 선 생기는데 막아줘" - 미드나잇 카드/버튼 안에 세로 선, 버튼 끝이 밝게 뜸.
	// 1.21.6+ / 26.x GUI 렌더러는 한 층(stratum) 안의 그림을 파이프라인별로 다시 정렬한다(색 채우기 먼저, 텍스처 나중).
	// 둥근 사각형은 모서리(텍스처) + 몸통(채우기)이라, 여러 겹을 쌓으면(빛 번짐, 테두리, 속) 앞 겹의 모서리가 뒤 겹의 몸통 위로 올라와
	// 경계에 선과 밝은 띠가 생겼다. 텍스처를 그린 뒤 다음 둥근 도형을 그리기 전에 층을 나눠 그린 순서 그대로 겹치게 한다.
	// 예전 렌더러(그 메서드가 없는 버전)에서는 아무것도 안 한다.

	/** 마지막 층 나누기 뒤로 텍스처를 그렸나. */
	public static boolean texSinceBreak;
	private static java.lang.reflect.Method stratumMethod;
	/** 0 = 아직 안 찾음, 1 = 있음, -1 = 없음(예전 렌더러). */
	private static int stratumState;

	/** 텍스처를 그린 뒤라면 새 층을 연다(그 뒤 그림이 앞 그림 위에 그대로 올라가게). */
	public static void layerBreak(DrawContext ctx) {
		if (!texSinceBreak || ctx == null || stratumState < 0) {
			return;
		}
		texSinceBreak = false;
		try {
			if (stratumState == 0) {
				java.lang.reflect.Method m = kr.lunaslight.mod.util.LunaCompat.findMethod(DrawContext.class, "createNewRootLayer");
				if (m == null) {
					m = kr.lunaslight.mod.util.LunaCompat.findMethod(DrawContext.class, "nextStratum");
				}
				stratumMethod = m;
				stratumState = m == null ? -1 : 1;
				if (m == null) {
					return;
				}
			}
			stratumMethod.invoke(ctx);
		} catch (Throwable t) {
			stratumState = -1;
			warnOnce("layerBreak", t);
		}
	}

	// ---------------------------------------------------------------- 49-42차: 블렌딩 모드 채우기(색보정용)

	/** GL 블렌딩 인자(1.15~1.21.4 RenderSystem.blendFunc(int,int)용). */
	private static final int GL_ZERO = 0, GL_ONE = 1, GL_DST_COLOR = 0x306, GL_ONE_MINUS_DST_COLOR = 0x307;

	public enum Blend {
		/** out = src × dst (어둡게·색 곱하기) */
		MULTIPLY("DST_COLOR", "ZERO", GL_DST_COLOR, GL_ZERO),
		/** out = dst + src (밝게) */
		ADD("ONE", "ONE", GL_ONE, GL_ONE),
		/** out = dst × (1 + src) (1~2배 곱하기) */
		MULTIPLY_ADD("DST_COLOR", "ONE", GL_DST_COLOR, GL_ONE),
		/** out = src × (1 − dst) (src 흰색이면 반전) */
		INVERT("ONE_MINUS_DST_COLOR", "ZERO", GL_ONE_MINUS_DST_COLOR, GL_ZERO);

		final String srcName, dstName;
		final int glSrc, glDst;

		Blend(String srcName, String dstName, int glSrc, int glDst) {
			this.srcName = srcName;
			this.dstName = dstName;
			this.glSrc = glSrc;
			this.glDst = glDst;
		}
	}

	private static final Object[] BLEND_PIPELINES = new Object[Blend.values().length];
	private static Method fillPipeline;      // 1.21.6+: DrawContext.fill(RenderPipeline, int, int, int, int, int)
	private static Method blendFuncInt;      // ≤1.21.4: RenderSystem.blendFunc(int, int)
	private static boolean blendBroken;
	private static Identifier whiteTex;

	/**
	 * 화면 사각형을 지정한 블렌딩으로 채운다(색보정 = 화면 전체에 곱하기/더하기/반전 몇 번).
	 *  · 1.21.6+: GUI_SNIPPET에 BlendFunction만 바꾼 RenderPipeline을 만들어 DrawContext.fill(pipeline, …)
	 *    (blaze3d 이름은 프로덕션에서도 난독화되지 않아 리플렉션이 안전, RenderPipelines만 야른 이름)
	 *  · 1.17~1.21.1(drawTexture 즉시 그리기 시대): RenderSystem.blendFunc(int,int) 걸고 흰 텍스처를 색 곱해 그림
	 *  · 1.21.2~1.21.5(RenderLayer 시대 - 레이어가 블렌딩을 덮어씀)·1.16 이하: 지원 안 함(false)
	 */
	public static boolean fillBlend(DrawContext ctx, int x, int y, int w, int h, int argb, Blend blend) {
		if (blendBroken || w <= 0 || h <= 0) {
			return false;
		}
		if (!resolveDrawTexture(ctx)) {
			return false;
		}
		try {
			if (drawTextureMode == 2) {
				Object pipeline = blendPipeline(ctx, blend);
				if (pipeline == null) {
					return false;
				}
				fillPipeline.invoke(ctx, pipeline, x, y, x + w, y + h, 0xFF000000 | (argb & 0x00FFFFFF));
				return true;
			}
			if (drawTextureMode != 0 || enableBlend == null) {
				return false;
			}
			if (blendFuncInt == null) {
				Class<?> rs = renderStateClass();
				blendFuncInt = LunaCompat.getMethodCompat(rs, "blendFunc", int.class, int.class);
			}
			if (whiteTex == null) {
				whiteTex = id("textures/gui/white.png");
			}
			enableBlend.invoke(null);
			blendFuncInt.invoke(null, blend.glSrc, blend.glDst);
			try {
				float r = ((argb >> 16) & 0xFF) / 255f;
				float g = ((argb >> 8) & 0xFF) / 255f;
				float b = (argb & 0xFF) / 255f;
				setShaderColor.invoke(null, r, g, b, 1f);
				drawTextureMethod.invoke(ctx, whiteTex, x, y, w, h, 0f, 0f, 8, 8, 8, 8);
			} finally {
				setShaderColor.invoke(null, 1f, 1f, 1f, 1f);
				defaultBlendFunc.invoke(null);
			}
			return true;
		} catch (Throwable t) {
			warnOnce("fillBlend", t);
			blendBroken = true;
			return false;
		}
	}

	/** 1.21.6+: 블렌딩만 다른 GUI 파이프라인(한 번 만들어 캐시). */
	private static Object blendPipeline(DrawContext ctx, Blend blend) throws Exception {
		Object cached = BLEND_PIPELINES[blend.ordinal()];
		if (cached != null) {
			return cached;
		}
		Class<?> rp = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline");
		Class<?> snippet = Class.forName("com.mojang.blaze3d.pipeline.RenderPipeline$Snippet");
		Class<?> pipelines = LunaCompat.classForName("net.minecraft.client.gl.RenderPipelines");
		Object guiSnippet = LunaCompat.getFieldCompat(pipelines, "GUI_SNIPPET").get(null);
		Object arr = java.lang.reflect.Array.newInstance(snippet, 1);
		java.lang.reflect.Array.set(arr, 0, guiSnippet);
		Object builder = rp.getMethod("builder", arr.getClass()).invoke(null, arr);
		builder.getClass().getMethod("withLocation", String.class).invoke(builder, "lunaslight:pipeline/blend_" + blend.name().toLowerCase(java.util.Locale.ROOT));
		Class<?> bf = Class.forName("com.mojang.blaze3d.pipeline.BlendFunction");
		Class<?> sf = Class.forName("com.mojang.blaze3d.platform.SourceFactor");
		Class<?> df = Class.forName("com.mojang.blaze3d.platform.DestFactor");
		@SuppressWarnings({"unchecked", "rawtypes"})
		Object fn = bf.getConstructor(sf, df).newInstance(Enum.valueOf((Class) sf, blend.srcName), Enum.valueOf((Class) df, blend.dstName));
		builder.getClass().getMethod("withBlend", bf).invoke(builder, fn);
		Object pipeline = builder.getClass().getMethod("build").invoke(builder);
		if (fillPipeline == null) {
			fillPipeline = LunaCompat.getMethodCompat(ctx.getClass(), "fill", rp, int.class, int.class, int.class, int.class, int.class);
		}
		BLEND_PIPELINES[blend.ordinal()] = pipeline;
		return pipeline;
	}

	private static Method legacyBlendSep;

	/** 1.15+ RenderSystem, 없으면(1.14.4) GlStateManager. */
	private static Class<?> renderStateClass() throws ClassNotFoundException {
		try {
			return Class.forName("com.mojang.blaze3d.systems.RenderSystem");
		} catch (ClassNotFoundException e) {
			return LunaCompat.classForName("com.mojang.blaze3d.platform.GlStateManager");
		}
	}

	/** 1.14.4용 defaultBlendFunc 대신(리플렉션 Method로 같은 자리에 꽂는다). */
	@SuppressWarnings("unused")
	private static void legacyDefaultBlendFunc() throws Exception {
		if (legacyBlendSep != null) {
			legacyBlendSep.invoke(null, 770, 771, 1, 0);
		}
	}

	private static boolean resolveDrawTexture(DrawContext ctx) {
		if (drawTextureMode != -1) {
			return drawTextureMode >= 0;
		}
		drawTextureMode = -2;
		try {
			Class<?> ctxClass = ctx.getClass();
			for (Method m : ctxClass.getMethods()) {
				if (!LunaCompat.nameMatches(ctxClass, "drawTexture", m.getName())) {
					continue;
				}
				Class<?>[] p = m.getParameterTypes();
				// 1.21.2+: (X, Identifier, int x, int y, float u, float v, int w, int h, int regionW, int regionH, int texW, int texH, int color)
				if (p.length == 13 && p[1] == Identifier.class && p[4] == float.class && p[12] == int.class) {
					if (p[0].getName().endsWith("RenderPipeline")) {
						Class<?> pipelines = LunaCompat.classForName("net.minecraft.client.gl.RenderPipelines");
						pipelineOrFunction = LunaCompat.getFieldCompat(pipelines, "GUI_TEXTURED").get(null);
						drawTextureMethod = m;
						drawTextureMode = 2;
						return true;
					}
					if (Function.class.isAssignableFrom(p[0])) {
						Class<?> renderLayer = LunaCompat.classForName("net.minecraft.client.render.RenderLayer");
						Method guiTextured = LunaCompat.getMethodCompat(renderLayer, "getGuiTextured", Identifier.class);
						pipelineOrFunction = (Function<Identifier, Object>) identifier -> {
							try {
								return guiTextured.invoke(null, identifier);
							} catch (Throwable t) {
								throw new RuntimeException(t);
							}
						};
						drawTextureMethod = m;
						drawTextureMode = 1;
						return true;
					}
				}
				// 1.20.x: (Identifier, int x, int y, int w, int h, float u, float v, int regionW, int regionH, int texW, int texH)
				// 49-82차: 1.15.2~1.19.4의 shim DrawContext(compat/legacy-era0|1)도 이제 같은 시그니처를 갖는다.
				if (p.length == 11 && p[0] == Identifier.class && p[1] == int.class && p[5] == float.class && p[6] == float.class) {
					Class<?> rs = renderStateClass();
					if (rs.getName().endsWith("GlStateManager")) {
						// 49-176차: 1.14.4엔 RenderSystem(1.15에 생김)이 없다 - GlStateManager의 같은 이름 메서드로.
						// defaultBlendFunc도 없어서 같은 값(SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ZERO)을 직접 건다.
						setShaderColor = LunaCompat.getMethodCompat(rs, "color4f", float.class, float.class, float.class, float.class);
						try {
							enableBlend = LunaCompat.getMethodCompat(rs, "enableBlend");
							legacyBlendSep = LunaCompat.getMethodCompat(rs, "blendFuncSeparate", int.class, int.class, int.class, int.class);
							defaultBlendFunc = LunaGfx.class.getDeclaredMethod("legacyDefaultBlendFunc");
							defaultBlendFunc.setAccessible(true);
						} catch (Throwable ignored) {
							enableBlend = null;
							defaultBlendFunc = null;
						}
						drawTextureMethod = m;
						drawTextureMode = 0;
						return true;
					}
					try {
						setShaderColor = rs.getMethod("setShaderColor", float.class, float.class, float.class, float.class);
					} catch (NoSuchMethodException e) {
						// 1.15.2~1.16.5: 셰이더 이전 - 고정 파이프라인 색은 color4f(같은 인자, 같은 뜻)
						setShaderColor = rs.getMethod("color4f", float.class, float.class, float.class, float.class);
					}
					try {
						enableBlend = rs.getMethod("enableBlend");
						defaultBlendFunc = rs.getMethod("defaultBlendFunc");
					} catch (Throwable ignored) {
						enableBlend = null;
						defaultBlendFunc = null;
					}
					drawTextureMethod = m;
					drawTextureMode = 0;
					return true;
				}
			}
		} catch (Throwable t) {
			warnOnce("resolveDrawTexture", t);
		}
		return false;
	}

	// ---------------------------------------------------------------- 플레이어 얼굴(49-25차)

	/** 스킨 텍스처(64×64)의 얼굴(8,8)과 모자 겹(40,8)을 size 크기로. 실패하면 false. */
	public static boolean drawPlayerFace(DrawContext ctx, Identifier skin, int x, int y, int size, int argb) {
		if (skin == null) {
			return false;
		}
		if (!drawTex(ctx, skin, x, y, size, size, 8, 8, 8, 8, 64, argb)) {
			return false;
		}
		drawTex(ctx, skin, x, y, size, size, 40, 8, 8, 8, 64, argb);
		return true;
	}

	// ---------------------------------------------------------------- 바닐라 배고픔 아이콘(49-25차: 포만감 HUD·음식 툴팁 공용)

	private static final boolean SPRITE_ERA = kr.lunaslight.mod.util.LunaVersion.isWithin("1.20.2", null);
	private static Identifier foodFull, foodHalf, foodEmpty, iconsPng, foodOutline;

	/**
	 * 바닐라 고기 아이콘(kind 2=가득, 1=반, 0=빈 테두리)을 (x,y)에 9×9로 색 곱해 그림. 1.20.2+는 스프라이트 파일,
	 * 그 전은 icons.png 아틀라스. 실패하면 false(호출부가 사각형으로 대체).
	 */
	public static boolean drawFoodIcon(DrawContext ctx, int x, int y, int kind, int argb) {
		if (SPRITE_ERA) {
			if (foodFull == null) {
				foodFull = mcId("textures/gui/sprites/hud/food_full.png");
				foodHalf = mcId("textures/gui/sprites/hud/food_half.png");
				foodEmpty = mcId("textures/gui/sprites/hud/food_empty.png");
			}
			Identifier tex = kind == 2 ? foodFull : (kind == 1 ? foodHalf : foodEmpty);
			return drawTex(ctx, tex, x, y, 9, 9, 0, 0, 9, 9, 9, argb);
		}
		if (iconsPng == null) {
			iconsPng = mcId("textures/gui/icons.png");
		}
		int u = kind == 2 ? 52 : (kind == 1 ? 61 : 16);
		return drawTex(ctx, iconsPng, x, y, 9, 9, u, 27, 9, 9, 256, argb);
	}

	/**
	 * 고기 윤곽 스프라이트(food_outline.png 9×9)의 오른쪽 cols열(1~9)을 (x,y)에 색 곱해 그림 - 포만감 테두리.
	 * 실패하면 아이콘 아래 1px 선으로 대체.
	 */
	public static void drawFoodOutline(DrawContext ctx, int x, int y, int cols, int argb) {
		cols = Math.max(1, Math.min(9, cols));
		if (foodOutline == null) {
			foodOutline = id("textures/gui/food_outline.png");
		}
		if (!drawTex(ctx, foodOutline, x + 9 - cols, y, cols, 9, 9 - cols, 0, cols, 9, 9, argb)) {
			ctx.fill(x + 9 - cols, y + 8, x + 9, y + 9, argb);
		}
	}

	// ---------------------------------------------------------------- 로그

	private static final java.util.Set<String> WARNED = new java.util.HashSet<>();

	static void warnOnce(String key, Throwable t) {
		if (WARNED.add(key)) {
			LunaClientMod.LOGGER.warn("[Nova] UI 그래픽 호환층 실패(" + key + ") - 기본 방식으로 대체", t);
		}
	}
}
