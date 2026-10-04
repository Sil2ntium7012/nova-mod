package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.util.WindowAccess;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.gui.LunaGfx;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.FloatSetting;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * 49-77차: <b>마우스 잔상</b> - 커서가 보이는 화면(ESC 메뉴·인벤토리·설정…)에서 마우스 커서 뒤에
 * 꼬리를 남긴다. 게임 화면(월드)에서는 커서가 없으니 아무것도 안 그린다.
 *
 * <p>49-48차의 "마우스 움직임"은 시야각 변화를 HUD 상자 안에 궤적으로 그리는 것이었는데, 사용자가 원한 건
 * 그게 아니라 <b>"ESC 같은 걸 눌렀을 때 마우스 보이는 화면에서 커서에 잔상 뜨는 것"</b>이었다(49-77차).
 * 상자·위치·민감도는 전부 없앴다 - 잔상은 커서를 따라가지 자리가 따로 없다. 남은 설정은 색·길이·두께.
 *
 * <p>어떻게: 화면이 그려진 직후({@link LunaCompat#registerScreenAfterRender}) 프레임마다 커서 위치를 시각과
 * 함께 쌓고, "길이"(밀리초)보다 오래된 점은 버린다. 머리(커서 쪽)는 굵고 진하게, 꼬리는 가늘고 옅게
 * AA 선으로 잇는다. 커서가 멈추면 점이 한자리에 쌓이고 오래된 점부터 사라져 꼬리가 저절로 걷힌다.
 * 커서 좌표는 창 픽셀({@code Mouse#getX})을 GUI 배율로 나눠 소수점까지 쓴다 - 정수 mouseX로는 느린
 * 움직임에서 계단이 생긴다.
 *
 * <p>모듈 id는 예전 것({@code mousestrokes_hud})을 그대로 둬 저장된 켬/끔이 이어진다.
 */
public class MousestrokesHudModule extends Module {

	private static final int MAX_POINTS = 256;

	private final ColorSetting lineColor = register(new ColorSetting(
			"line_color", "색", "잔상의 색입니다.", 0xFFA9D973));
	private final IntSetting length = register(new IntSetting(
			"length", "길이", "잔상이 남아 있는 시간입니다.", 220, 80, 600, 20).unit("ms"));
	private final FloatSetting thickness = register(new FloatSetting(
			"thickness", "두께", "커서 쪽 두께입니다(꼬리로 갈수록 가늘어집니다).", 2.5f, 1.0f, 5.0f, 0.25f).style());

	// 최근 커서 위치(원형 버퍼). 0번 자리가 아니라 head가 가장 최근.
	private final double[] xs = new double[MAX_POINTS];
	private final double[] ys = new double[MAX_POINTS];
	private final long[] ts = new long[MAX_POINTS];
	private int head = -1;
	private int count;
	private Object lastScreen;

	// 49-103차: 부드럽게 + 표본점마다 점(구슬) 생기는 것 제거용 스무딩 버퍼(핑퐁, 매 프레임 재할당 X = GC 없음).
	// Chaikin(모서리 깎기)을 두 번 돌리면 점 개수가 최대 4배로 늘어(2n→2·2n) 균일해지므로 넉넉히 잡는다.
	private static final int SMAX = MAX_POINTS * 4 + 8;
	private final double[] sxA = new double[SMAX], syA = new double[SMAX];
	private final float[] stA = new float[SMAX];
	private final double[] sxB = new double[SMAX], syB = new double[SMAX];
	private final float[] stB = new float[SMAX];

	public MousestrokesHudModule() {
		super("mousestrokes_hud", "마우스 잔상", ModuleCategory.HUD, "커서가 보이는 화면에서 마우스 뒤에 남는 꼬리");
		LunaCompat.registerScreenAfterRender(this::onScreenFrame);
	}

	@Override
	protected void onEnable() {
		count = 0;
		head = -1;
	}

	/** 화면이 한 프레임 그려진 뒤. 커서 위치를 쌓고 꼬리를 그린다. */
	private void onScreenFrame(Object screen, DrawContext ctx, int mouseX, int mouseY) {
		if (!isEnabled() || client == null || WindowAccess.of(client) == null) {
			return;
		}
		if (screen != lastScreen) {
			// 다른 화면으로 바뀌면 이전 화면의 꼬리를 끌고 오지 않는다
			lastScreen = screen;
			count = 0;
			head = -1;
		}
		double mx = mouseX;
		double my = mouseY;
		try {
			double sf = WindowAccess.of(client).getScaleFactor();
			drawPx = (float) Math.max(1.0, sf);
			if (sf > 0) {
				mx = client.mouse.getX() / sf;
				my = client.mouse.getY() / sf;
			}
		} catch (Throwable ignored) {
		}
		long now = System.nanoTime();
		push(mx, my, now);
		draw(ctx, now, WindowAccess.of(client).getScaledWidth(), WindowAccess.of(client).getScaledHeight());
	}

	private void push(double x, double y, long now) {
		head = (head + 1) % MAX_POINTS;
		xs[head] = x;
		ys[head] = y;
		ts[head] = now;
		count = Math.min(count + 1, MAX_POINTS);
	}

	private void draw(DrawContext ctx, long now, int sw, int sh) {
		long life = length.get() * 1_000_000L;
		int col = lineColor.getArgb();
		int baseA = (col >>> 24) & 0xFF;
		int rgb = col & 0x00FFFFFF;
		float headW = thickness.get();

		// 살아있는 점을 오래된→최근 순으로 A 버퍼에 모은다. t: 1=방금, 0=사라지기 직전.
		int n = 0;
		for (int i = count - 1; i >= 0 && n < MAX_POINTS; i--) {
			int idx = ((head - i) % MAX_POINTS + MAX_POINTS) % MAX_POINTS;
			long age = now - ts[idx];
			if (age >= life) {
				continue;   // 너무 오래된 점(가장 이른 것들)은 건너뜀
			}
			sxA[n] = xs[idx];
			syA[n] = ys[idx];
			stA[n] = 1f - (float) age / life;
			n++;
		}
		if (n < 2) {
			return;
		}
		// Chaikin 모서리 깎기 2회: 원·긴 획을 부드럽게 하고, 표본 사이 점 밀도를 균일하게 만든다. 핑퐁(A→B→A).
		n = chaikin(sxA, syA, stA, n, sxB, syB, stB);
		n = chaikin(sxB, syB, stB, n, sxA, syA, stA);
		stampPath(ctx, n, baseA, rgb, headW);
	}

	/**
	 * 49-170차(사용자: "잔상 중간에 동그라미가 막 끊기면서 보이는데"): 예전엔 점 사이를 선분(회전 사각형)으로 이어
	 * 그렸는데, 빠르게 원을 그리면 선분끼리 꺾이는 자리마다 바깥쪽에 쐐기 틈이 생기고 반투명 선분이 겹치는 안쪽은
	 * 진해져 "끊긴 원"처럼 보였다. 이제는 경로를 따라 <b>같은 간격으로 부드러운 원을 찍는다</b> - 어떤 각도로
	 * 꺾여도 틈이 없고, 겹침은 어디서나 같은 비율이라 얼룩이 없다. 겹침(지름/간격)만큼 원 하나의 알파를 낮춰
	 * 전체 진하기는 설정 색의 알파와 같게 맞춘다.
	 */
	private void stampPath(DrawContext ctx, int n, int baseA, int rgb, float headW) {
		// 49-232차(사용자: "마우스 잔상이 너무 네모로 보이고 끊겨"): 예전엔 GUI 단위로 찍어서 꼬리(지름 2 이하)가 GUI 픽셀
		// 네모(GUI 3이면 화면 3x3칸)였고, 점 개수 한도(900)에 걸리면 오래된 쪽부터 찍다 보니 커서 쪽이 잘려 끊겨 보였다.
		// 이제 행렬을 1/배율로 줄여 <b>화면 픽셀 단위</b>로 찍고(꼬리도 1~2px 점, 원 텍스처도 실제 크기에 맞춤),
		// 커서 쪽(최근)부터 찍어 한도에 걸려도 꼬리 끝만 짧아진다.
		boolean sub = LunaCompat.guiTransformSupported(ctx);
		float s = sub ? Math.max(1f, drawPx) : 1f;
		boolean scaled = s > 1.001f;
		float prevPx = LunaGfx.roundPx;
		if (scaled) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiScale(ctx, 1f / s, 1f / s);
			LunaGfx.roundPx = 1f;
		}
		try {
			double carry = 0;
			int stamps = 0;
			for (int i = n - 1; i >= 1 && stamps < MAX_STAMPS; i--) {
				double x0 = sxA[i] * s, y0 = syA[i] * s, x1 = sxA[i - 1] * s, y1 = syA[i - 1] * s;
				float t0 = stA[i], t1 = stA[i - 1];
				double segLen = Math.hypot(x1 - x0, y1 - y0);
				if (segLen < 1e-6) {
					continue;
				}
				double pos = carry;
				while (pos <= segLen && stamps < MAX_STAMPS) {
					double f = pos / segLen;
					float t = t0 + (t1 - t0) * (float) f;
					float d = Math.max(1f, headW * s * t);
					double step = Math.max(0.6, d * 0.3);
					double k = Math.max(1.0, d / step);
					double want = baseA / 255.0 * t * t;
					int a = (int) Math.round((1.0 - Math.pow(1.0 - want, 1.0 / k)) * 255.0);
					if (a >= 2) {
						stamp(ctx, x0 + (x1 - x0) * f, y0 + (y1 - y0) * f, d, rgb | (a << 24), sub);
						stamps++;
					}
					pos += step;
				}
				carry = pos - segLen;
			}
		} finally {
			if (scaled) {
				LunaGfx.roundPx = prevPx;
				LunaCompat.guiPop(ctx);
			}
		}
	}

	private static final int MAX_STAMPS = 4000;

	/** 지금 1단위 = 화면 몇 픽셀(onScreenFrame/미리보기가 정한다). */
	private float drawPx = 1f;

	/** 지름 d의 원 하나를 (x, y) 가운데에. 소수 자리는 guiTranslate로 옮겨 찍어 흔들림이 없다(안 되면 정수 자리). */
	private static void stamp(DrawContext ctx, double x, double y, float d, int color, boolean sub) {
		int di = Math.max(1, Math.round(d));
		double left = x - di / 2.0, top = y - di / 2.0;
		int ix = (int) Math.floor(left), iy = (int) Math.floor(top);
		if (di <= 2) {
			ctx.fill(ix, iy, ix + di, iy + di, color);
			return;
		}
		if (sub) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, (float) (left - ix), (float) (top - iy));
			LunaDraw.circle(ctx, ix, iy, di, color);
			LunaCompat.guiPop(ctx);
		} else {
			LunaDraw.circle(ctx, ix, iy, di, color);
		}
	}

	/** Chaikin 모서리 깎기 한 번(양 끝점은 유지). 출력 점 개수 = 2n-2를 반환. t(진하기)도 같은 비율로 섞는다. */
	private static int chaikin(double[] ix, double[] iy, float[] it, int n,
			double[] ox, double[] oy, float[] ot) {
		if (n < 3) {
			for (int i = 0; i < n; i++) {
				ox[i] = ix[i];
				oy[i] = iy[i];
				ot[i] = it[i];
			}
			return n;
		}
		int m = 0;
		ox[m] = ix[0];
		oy[m] = iy[0];
		ot[m] = it[0];
		m++;
		int limit = ox.length - 2;
		for (int i = 0; i < n - 1 && m < limit; i++) {
			double x0 = ix[i], y0 = iy[i], x1 = ix[i + 1], y1 = iy[i + 1];
			float t0 = it[i], t1 = it[i + 1];
			ox[m] = x0 * 0.75 + x1 * 0.25;
			oy[m] = y0 * 0.75 + y1 * 0.25;
			ot[m] = t0 * 0.75f + t1 * 0.25f;
			m++;
			ox[m] = x0 * 0.25 + x1 * 0.75;
			oy[m] = y0 * 0.25 + y1 * 0.75;
			ot[m] = t0 * 0.25f + t1 * 0.75f;
			m++;
		}
		ox[m] = ix[n - 1];
		oy[m] = iy[n - 1];
		ot[m] = it[n - 1];
		m++;
		return m;
	}

	/** 설정 화면 미리보기: 고정된 S자 꼬리 하나. */
	@Override
	public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
		if (!isPreview()) {
			return;
		}
		int cx = previewCenterX();
		int cy = previewCenterY();
		int col = lineColor.getArgb();
		int baseA = (col >>> 24) & 0xFF;
		int rgb = col & 0x00FFFFFF;
		float headW = thickness.get();
		// 49-88차(8-12): 길이 설정에 따라 꼬리가 길고 짧게(기본 길이에서 24마디)
		int n = Math.max(6, Math.min(48, Math.round(24f * length.get() / (float) length.getDefaultValue())));
		for (int i = 0; i <= n; i++) {
			double s = i / (double) n;
			sxA[i] = cx + 22 - s * 46;
			syA[i] = cy - 2 + Math.sin(s * Math.PI * 1.5) * 9;
			stA[i] = 1f - (float) s;
		}
		drawPx = LunaGfx.roundPx > 0f ? LunaGfx.roundPx : 2f;   // 설정 화면(가상 GUI 2) 기준
		stampPath(context, n + 1, baseA, rgb, headW);
	}

	@Override
	public boolean hasPreview() {
		return true;
	}
}
