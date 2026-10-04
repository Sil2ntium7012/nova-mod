package kr.lunaslight.mod.util;

import kr.lunaslight.mod.gui.LunaGfx;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * 49-47차(사용자: "처음에 마크 로딩창 뜨는 거 그냥 우리 클라이언트 로딩창이랑 똑같게 바꿔줘, 배경은 내 테마 색으로"):
 * 마인크래프트 시작 로딩 화면(SplashOverlay - 모장 로고 + 빨간 막대) 위에 런처 로딩창과 같은 모양을 덮는다.
 *
 * ⚠️ <b>바닐라 render를 취소하지 않는다.</b> 그 메서드 안에서 리소스 새로고침이 끝났는지 보고 오버레이를
 * 스스로 닫기 때문에, 취소하면 로딩 화면에서 영영 안 넘어간다. 그래서 RETURN에 붙어 <b>위에 덮기만</b> 한다.
 *
 * 이 시점엔 글꼴·텍스처가 아직 로드 중일 수 있어 <b>사각형만</b>으로 그린다(글자·그림 없음):
 * 테마 배경색 + 런처 로고와 같은 3×3 점 격자(강조색) + 아래 진행 막대. 진행률은 오버레이가 들고 있는
 * ResourceReload#getProgress()를 리플렉션으로 읽고, 못 읽으면 천천히 왕복하는 막대로 대신한다.
 *
 * <h3>49-67차(5-7): 리소스팩 로딩창</h3>
 * 사용자 요청 5-7은 "리소스팩 로딩창을 클라이언트풍으로". <b>실측해 보니 같은 클래스였다</b> -
 * 마인크래프트는 시작 로딩과 리소스팩 적용에 SplashOverlay(구버전 SplashScreen) <b>한 벌</b>을 쓰고
 * {@code reloading} 깃발로만 구분한다(javap: 1.15.2~1.21.11 전부 이 필드가 있다). 그래서 49-47차에
 * 만든 이 화면이 1.17+에서는 리소스팩 로딩창도 이미 덮고 있었다. 실제로 남아 있던 것은 둘이다.
 * <ul>
 *   <li><b>1.15.2·1.16.5는 클래스 이름이 {@code SplashScreen}</b>이라 믹스인이 아예 안 붙었다 -
 *       그 두 버전에서만 모장 로딩창이 그대로 나왔다. → {@code SplashScreenLegacyMixin} 추가.</li>
 *   <li><b>리소스팩 적용은 게임 중에 일어난다.</b> 바닐라는 배경을 0.5초에 걸쳐 서서히 덮는데 우리는
 *       첫 프레임부터 불투명하게 칠해서 화면이 <b>툭</b> 하고 바뀌었다. → {@code reloading}일 때만
 *       우리도 서서히 덮는다({@link #FADE_IN_MS}).</li>
 * </ul>
 * <b>끝날 때의 사라짐은 바닐라에 맡긴다.</b> 바닐라가 언제 오버레이를 치우는지는 버전마다 다른 필드를
 * 봐야 알 수 있는데(1.15.2는 {@code applyCompleteTime}, 1.16.5부터 {@code reloadCompleteTime},
 * 게다가 실행 중엔 필드 이름이 난독화되어 이름으로 찾을 수도 없다), 어림짐작으로 먼저 걷어내면
 * <b>바닐라의 옛 배경이 잠깐 드러난다</b> - 지금보다 나쁘다. 확실하지 않은 쪽은 건드리지 않았다.
 */
public final class SplashHook {
	private SplashHook() {
	}

	/** 리소스팩 적용 때 배경을 덮는 데 쓰는 시간(ms). 바닐라 페이드인(0.5초)보다 조금 빠르게. */
	private static final int FADE_IN_MS = 350;

	private static java.lang.reflect.Field reloadField;
	private static boolean reloadFieldResolved;
	private static java.lang.reflect.Field reloadingField;
	private static boolean reloadingFieldResolved;
	private static Object fadeOwner;
	private static long fadeStart;

	public static void render(Object overlay, DrawContext ctx) {
		StallWatch.beat();   // 49-154차: 로딩창(리소스팩 적용)이 도는 동안에도 멈춤 감시 신호
		if (ctx == null) {
			return;
		}
		try {
			MinecraftClient client = MinecraftClient.getInstance();
			if (client == null || WindowAccess.of(client) == null) {
				return;
			}
			int w = WindowAccess.of(client).getScaledWidth();
			int h = WindowAccess.of(client).getScaledHeight();
			if (w <= 0 || h <= 0) {
				return;
			}
			float fade = fade(overlay);
			if (fade <= 0.01f) {
				return;                            // 아직 게임 화면이 그대로 보여야 하는 순간
			}
			// 49-124차(사용자: "로딩창 더 꾸며줘 - 촌스럽지 않고 깔끔한데 화려하게"): 사각형 fill만으로(이 시점엔
			// 폰트/텍스처가 아직 로드 중일 수 있고, 옛 버전 DrawContext shim은 fillGradient가 없을 수 있어 fill만 쓴다)
			// 은은한 배경 그라데이션 + 떠다니는 입자 + 반짝이는 진행 막대.
			int accentRgb = LunaTheme.ACCENT;
			int accent = withAlpha(accentRgb, fade);
			int white = withAlpha(0xFFFFFF, fade);
			long tms = System.currentTimeMillis();

			// 1) 배경 - 위는 테마색, 아래로 갈수록 강조색이 아주 옅게 섞인 세로 그라데이션(띠로 흉내).
			int bgTop = LunaTheme.BACKGROUND_SOLID;
			int bgBottom = mix(LunaTheme.BACKGROUND_SOLID, accentRgb, 0.13f);
			int bands = 40;
			for (int i = 0; i < bands; i++) {
				int y0 = h * i / bands;
				int y1 = h * (i + 1) / bands;
				ctx.fill(0, y0, w, y1, argb(mix(bgTop, bgBottom, i / (float) (bands - 1)), 1f, fade));
			}

			// 2) 위로 천천히 떠오르며 반짝이는 입자(은은하게).
			int particleCount = 14;
			for (int i = 0; i < particleCount; i++) {
				float sx = frac(i * 0.6180339887 + 0.13);
				double speed = 9000.0 + (i % 5) * 2600.0;
				float yy = 1f - frac(tms / speed + frac(i * 0.375));
				int ppx = Math.round(sx * w);
				int ppy = Math.round(yy * h);
				float tw = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.sin(tms / 500.0 + i * 1.7));
				int sz = (i % 4 == 0) ? 2 : 1;
				ctx.fill(ppx, ppy, ppx + sz, ppy + sz, argb(accentRgb, 0.28f * tw, fade));
			}

			// 브랜드 배치: [로고] + [N★VA CLIENT 워드마크]를 가운데. logo.png(8칸 + 가운데 별) - 타이틀과 같은 그림.
			int logoSize = Math.max(36, Math.min(56, h / 7));
			int wordH = Math.round(logoSize * 0.62f);
			int wordW = LunaGfx.wordmarkWidth(wordH);
			int brandGap = Math.round(logoSize * 0.32f);
			int groupW = logoSize + brandGap + wordW;
			int gx = (w - groupW) / 2;
			int gy = h / 2 - logoSize / 2 - Math.round(logoSize * 0.28f);

			// 49-125차(사용자: "로딩창 글 뒤에 있는 보라색 원 없애줘"): 로고 뒤 타원형 빛무리는 뺐다.

			boolean logoDrawn = LunaGfx.drawTex(ctx, LunaGfx.id("textures/gui/logo.png"),
				gx, gy, logoSize, logoSize, 0, 0, 128, 128, 128, white);
			if (logoDrawn) {
				LunaGfx.drawWordmark(ctx, gx + logoSize + brandGap, gy + (logoSize - wordH) / 2, wordH, white);
			} else {
				// 텍스처 이전(극초반·구버전): 8칸 + 가운데 별을 손으로.
				drawFallbackLogo(ctx, (w - logoSize) / 2, gy, logoSize, accent);
			}

			// 3) 진행 게이지 - 트랙 + 채움 + 앞쪽 밝은 끝 + 언더글로우 + 좌우로 스윕하는 반짝임.
			int barW = Math.min(240, Math.max(140, w / 3));
			int barX = (w - barW) / 2;
			int barH = 3;
			int barY = gy + logoSize + Math.round(logoSize * 0.5f);
			ctx.fill(barX, barY, barX + barW, barY + barH, argb(accentRgb, 0.16f, fade));

			float p = progress(overlay);
			int fillL = barX;
			int fillR;
			if (p >= 0f) {
				fillR = barX + Math.round(barW * Math.max(0f, Math.min(1f, p)));
			} else {
				// 진행률을 못 읽으면 왕복하는 짧은 막대
				int runW = barW / 4;
				float t = (tms % 1600L) / 1600f;
				float eased = (float) (0.5 - 0.5 * Math.cos(t * Math.PI * 2));
				fillL = barX + Math.round((barW - runW) * eased);
				fillR = fillL + runW;
			}
			if (fillR > fillL) {
				ctx.fill(fillL, barY, fillR, barY + barH, accent);
				// 앞쪽 밝은 끝(흰색 살짝 섞어 반짝)
				ctx.fill(Math.max(fillL, fillR - 2), barY, fillR, barY + barH, argb(mix(accentRgb, 0xFFFFFF, 0.55f), 1f, fade));
				// 언더글로우(막대 아래로 옅게 번짐)
				for (int g = 0; g < 3; g++) {
					ctx.fill(fillL, barY + barH + g, fillR, barY + barH + g + 1, argb(accentRgb, 0.12f * (1 - g / 3f), fade));
				}
				// 채움 안에서 좌우로 흐르는 하이라이트
				float sh = frac(tms / 1100.0);
				int shc = fillL + Math.round((fillR - fillL) * sh);
				int shl = Math.max(fillL, shc - 7);
				int shr = Math.min(fillR, shc + 7);
				if (shr > shl) {
					ctx.fill(shl, barY, shr, barY + barH, argb(0xFFFFFF, 0.22f, fade));
				}
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("splash", t);
		}
	}

	/**
	 * 1.15.2 전용 입구. 그 버전의 {@code SplashScreen#render}에는 <b>매트릭스 인자가 아예 없다</b>
	 * (render(int,int,float)). 1.15.2용 DrawContext shim(compat/legacy-era0)은 매트릭스를 보관만 하고
	 * 그리기에 쓰지 않으므로(그 시대 {@code DrawableHelper.fill}이 매트릭스를 안 받는다) 빈 것으로
	 * 감싸 넘긴다. 다른 버전에서는 이 생성자가 없어 조용히 아무 일도 하지 않는다.
	 */
	public static void renderNoMatrices(Object overlay) {
		try {
			DrawContext ctx = DrawContext.class
				.getConstructor(net.minecraft.client.util.math.MatrixStack.class)
				.newInstance((Object) null);
			render(overlay, ctx);
		} catch (Throwable ignored) {
			// 이 버전엔 shim 생성자가 없다 = 이 경로로 올 일도 없는 버전
		}
	}

	/**
	 * 49-84차: logo.png를 못 그리는 순간(로딩 극초반·아주 옛 버전)의 폴백 로고. 실제 logo.png와 같은 그림 -
	 * 3×3에서 가운데를 뺀 8칸 + 가운데에 별(마름모). 예전엔 오른쪽 위를 비우고 거기에 십자를 찍어서
	 * "+ 모양"으로 보였는데(사용자 지적), 그건 로고와 다른 모양이었다. box = 로고 전체가 들어갈 정사각.
	 */
	private static void drawFallbackLogo(DrawContext ctx, int x, int y, int box, int accent) {
		int cell = Math.round(box * 0.27f);
		int gap = Math.round((box - cell * 3) / 2f);
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 3; col++) {
				if (row == 1 && col == 1) {
					continue;                       // 가운데는 별 자리
				}
				int bx = x + col * (cell + gap);
				int by = y + row * (cell + gap);
				ctx.fill(bx, by, bx + cell, by + cell, accent);
			}
		}
		// 가운데 별(4각) - 마름모 두 개를 겹쳐 오목한 별 느낌. fill이 가로줄만 되므로 한 줄씩 그린다.
		int cx = x + box / 2;
		int cy = y + box / 2;
		int r = Math.round(box * 0.20f);
		for (int dy = -r; dy <= r; dy++) {
			int half = (r - Math.abs(dy)) * (r - Math.abs(dy)) / Math.max(1, r);   // 오목한 모서리
			if (half <= 0) {
				continue;
			}
			ctx.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, accent);
		}
	}

	private static int withAlpha(int rgb, float a) {
		int alpha = Math.max(0, Math.min(255, Math.round(255 * a)));
		return (rgb & 0x00FFFFFF) | (alpha << 24);
	}

	/** rgb1 → rgb2를 t(0~1)만큼 섞은 rgb(알파 무시). */
	private static int mix(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		int r = Math.round(ar + (br - ar) * t);
		int g = Math.round(ag + (bg - ag) * t);
		int bl = Math.round(ab + (bb - ab) * t);
		return (r << 16) | (g << 8) | bl;
	}

	/** rgb + 알파(0~1) + 로딩 페이드를 곱해 ARGB로. */
	private static int argb(int rgb, float alpha, float fade) {
		int a = Math.max(0, Math.min(255, Math.round(255 * alpha * fade)));
		return (rgb & 0x00FFFFFF) | (a << 24);
	}

	/** 소수부(0~1) - 반복 애니메이션용. */
	private static float frac(double x) {
		return (float) (x - Math.floor(x));
	}

	/**
	 * 지금 이 오버레이를 얼마나 덮을지(0~1).
	 *
	 * <p>시작 로딩(=게임 화면 자체가 아직 없다)은 처음부터 1 - 여기서 페이드를 넣으면 검은 화면이
	 * 잠깐 보일 뿐이다. <b>리소스팩 적용</b>은 게임 화면 위에서 일어나므로 {@link #FADE_IN_MS} 동안
	 * 서서히 덮는다. 시간은 오버레이 인스턴스별로 잰다(팩을 연달아 바꾸면 매번 새로 덮인다).
	 */
	private static float fade(Object overlay) {
		if (!isReloading(overlay)) {
			fadeOwner = null;
			return 1f;
		}
		long now = System.nanoTime() / 1_000_000L;
		if (fadeOwner != overlay) {
			fadeOwner = overlay;
			fadeStart = now;
		}
		return Math.min(1f, (now - fadeStart) / (float) FADE_IN_MS);
	}

	/**
	 * 지금 뜬 로딩창이 <b>리소스팩 적용</b>인지(true) <b>게임 시작</b>인지(false).
	 *
	 * <p>이름으로 찾지 않는다 - 실행 중에는 필드 이름이 난독화되어 있다. 대신 이 클래스에 <b>인스턴스
	 * boolean 필드가 {@code reloading} 하나뿐</b>이라는 실측(1.15.2·1.16.5·1.20.4·1.21.11 javap)에
	 * 기대어 타입으로 찾는다. 혹시 둘 이상이면 판단을 포기하고 false(=예전 그대로 불투명)로 둔다.
	 */
	private static boolean isReloading(Object overlay) {
		if (overlay == null) {
			return false;
		}
		try {
			if (!reloadingFieldResolved) {
				reloadingFieldResolved = true;
				java.lang.reflect.Field found = null;
				for (java.lang.reflect.Field f : overlay.getClass().getDeclaredFields()) {
					if (f.getType() != boolean.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
						continue;
					}
					if (found != null) {
						found = null;             // 후보가 둘 - 어느 쪽인지 알 수 없으니 안 쓴다
						break;
					}
					f.setAccessible(true);
					found = f;
				}
				reloadingField = found;
			}
			return reloadingField != null && reloadingField.getBoolean(overlay);
		} catch (Throwable ignored) {
			return false;
		}
	}

	/** 오버레이가 들고 있는 ResourceReload의 진행률(0~1). 못 읽으면 -1. */
	private static float progress(Object overlay) {
		if (overlay == null) {
			return -1f;
		}
		try {
			if (!reloadFieldResolved) {
				reloadFieldResolved = true;
				for (java.lang.reflect.Field f : overlay.getClass().getDeclaredFields()) {
					if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
						continue;
					}
					f.setAccessible(true);
					Object v = f.get(overlay);
					if (v != null && LunaCompat.callNoArg(v, "getProgress") instanceof Float) {
						reloadField = f;
						break;
					}
				}
			}
			if (reloadField != null) {
				Object v = reloadField.get(overlay);
				Object p = v == null ? null : LunaCompat.callNoArg(v, "getProgress");
				if (p instanceof Float f) {
					return f;
				}
			}
		} catch (Throwable ignored) {
		}
		return -1f;
	}
}
