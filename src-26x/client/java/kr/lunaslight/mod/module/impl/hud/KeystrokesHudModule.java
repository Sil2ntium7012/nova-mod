package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.ColorSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.DeltaTracker;

/**
 * 49-150차: 모양 [기본](사진 같은 어두운 반투명 둥근 키 + 옅은 테두리) / [3D 키캡](옆면 두께가 있는 진짜 키캡).
 * 누를 때 거의 즉시 내려앉고(기본은 1px 줄어듦, 3D는 윗면이 두께만큼 내려감) 눌림 색으로 바뀌며 바깥으로 번쩍임,
 * 뗄 때는 톡 튀어 오른다. 스페이스바는 기본으로 켬.
 *
 * 49-6차 재작성 (요청 반영):
 *  - 기본은 WASD만 표시. 스페이스바 줄/마우스 줄은 추가 설정으로 켬(각각 분리 토글).
 *  - 스페이스바는 실제 키보드처럼 '가로로 긴 바' + 가운데 작대기(─)로 표시.
 *  - 키캡은 1px 모서리 컷으로 살짝 둥근 느낌(gui 패키지 의존 없이 fill만 사용).
 */
public class KeystrokesHudModule extends Module {

	private static final int BOX = 18;
	private static final int GAP = 2;
	/** 49-199차: 키마다 눌릴 때 밀리는 방향(W, A, S, D, 스페이스, 좌클, 우클). */
	private static final int[] DIR_X = {0, -1, 0, 1, 0, 0, 0};
	private static final int[] DIR_Y = {-1, 0, 1, 0, 1, 1, 1};

	/** 49-150차(사용자: "사진 느낌처럼 키스트로크 꾸미고 누를 때 뗄 때 애니메이션 + 진짜 누르는 느낌, 기본/3D 키캡"). */
	public enum Style {
		MODERN("기본"),
		KEYCAP("3D 키캡");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final PositionSetting position;
	private final kr.lunaslight.mod.module.setting.EnumSetting<Style> style;
	private final ColorSetting pressedColor;
	private final ColorSetting idleColor;
	private final BooleanSetting showSpace;
	private final BooleanSetting showMouse;
	private final BooleanSetting rainbow;
	private final kr.lunaslight.mod.module.setting.IntSetting rainbowSpeed;
	private final kr.lunaslight.mod.module.setting.IntSetting rainbowSpread;

	/** 키마다 눌림 정도(0 = 뗌, 1 = 끝까지 눌림). 누를 땐 빠르게, 뗄 땐 조금 천천히 튀어 오른다. */
	private final float[] press = new float[7];
	/** 누른 순간의 번쩍임(1 → 0). */
	private final float[] flash = new float[7];
	private final boolean[] wasDown = new boolean[7];
	private long lastNanos;
	private float dt;

	public KeystrokesHudModule() {
		super("keystrokes_hud", "키스트로크", ModuleCategory.HUD, "WASD | 스페이스 | 마우스 입력");
		// 49-76차(6-1): 핫바 오른쪽 끝에서 34px 떨어진 곳, 화면 바닥에서 3px(HudPosition이 화면 안으로 잘라 넣는다).
		position = register(new PositionSetting("position", "위치", "표시 위치입니다.",
			HudPosition.of(HudPosition.Anchor.BOTTOM_CENTER, 153, 3)));
		style = register(new kr.lunaslight.mod.module.setting.EnumSetting<>("style", "모양",
			"기본(둥근 반투명 키) 또는 진짜 키보드 같은 3D 키캡입니다.", Style.MODERN, Style.class));
		pressedColor = register(new ColorSetting("pressed_color", "눌림 색", "누른 키의 색입니다.", LunaTheme.ACCENT));
		idleColor = register(new ColorSetting("idle_color", "기본 색", "누르지 않은 키의 배경색입니다.", 0xA0101318));
		showSpace = register(new BooleanSetting("show_space", "스페이스바", "스페이스바 줄을 추가합니다.", true));
		showMouse = register(new BooleanSetting("show_mouse", "마우스", "좌/우클릭 줄을 추가합니다.", true));
		rainbow = register(new BooleanSetting("rainbow", "무지개",
			"키 테두리와 눌린 키에 무지개가 흐릅니다.", false));
		rainbowSpeed = register(new kr.lunaslight.mod.module.setting.IntSetting("rainbow_speed", "무지개 속도",
			"무지개가 흐르는 속도입니다.", 30, 5, 100, 5));
		rainbowSpread = register(new kr.lunaslight.mod.module.setting.IntSetting("rainbow_spread", "무지개 번짐",
			"클수록 색이 촘촘하게 바뀝니다.", 25, 0, 120, 5));
	}

	// ==================== 색 ====================

	private int rainbowAt(int px, int py) {
		double seconds = (System.currentTimeMillis() % 600_000L) / 1000.0;
		float cyclesPerSecond = rainbowSpeed.get() / 180f;
		float wavelength = 40f + (120 - rainbowSpread.get()) * 1.2f;
		float hue = (float) (seconds * cyclesPerSecond) - (px + py * 0.35f) / wavelength;
		hue -= (float) Math.floor(hue);
		return java.awt.Color.HSBtoRGB(hue, 0.75f, 1.0f) & 0x00FFFFFF;
	}

	private static int argb(int a, int rgb) {
		return ((Math.max(0, Math.min(255, a)) & 0xFF) << 24) | (rgb & 0x00FFFFFF);
	}

	/** a → b 로 t만큼(알파 포함). */
	private static int lerp(int a, int b, float t) {
		t = Math.max(0f, Math.min(1f, t));
		int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
		int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
		return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
			| (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
	}

	/** 밝게(+) / 어둡게(-). 알파 유지. */
	private static int shade(int c, float k) {
		int target = k >= 0 ? 0xFFFFFF : 0x000000;
		return lerp(c, (c & 0xFF000000) | target, Math.abs(k));
	}

	private int accentAt(int x, int y) {
		return rainbow.get() ? argb(0xFF, rainbowAt(x, y)) : (pressedColor.getArgb() | 0xFF000000);
	}

	// ==================== 모양 그리기(둥근 사각형: 모서리 2단 계단) ====================

	/** 채운 둥근 사각형(반지름 약 2px). */
	private static void round(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
		if (w <= 4 || h <= 4) {
			ctx.fill(x, y, x + w, y + h, color);
			return;
		}
		ctx.fill(x + 2, y, x + w - 2, y + 1, color);
		ctx.fill(x + 1, y + 1, x + w - 1, y + 2, color);
		ctx.fill(x, y + 2, x + w, y + h - 2, color);
		ctx.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, color);
		ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
	}

	/** 둥근 사각형 1px 테두리(안은 비움 - 반투명 채움과 겹쳐 색이 뜨지 않게). */
	private static void ring(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
		ctx.fill(x + 2, y, x + w - 2, y + 1, color);
		ctx.fill(x + 2, y + h - 1, x + w - 2, y + h, color);
		ctx.fill(x, y + 2, x + 1, y + h - 2, color);
		ctx.fill(x + w - 1, y + 2, x + w, y + h - 2, color);
		ctx.fill(x + 1, y + 1, x + 2, y + 2, color);
		ctx.fill(x + w - 2, y + 1, x + w - 1, y + 2, color);
		ctx.fill(x + 1, y + h - 2, x + 2, y + h - 1, color);
		ctx.fill(x + w - 2, y + h - 2, x + w - 1, y + h - 1, color);
	}

	/** 테두리 안쪽 채움(ring과 안 겹치게 1px 안쪽). */
	private static void inner(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int color) {
		ctx.fill(x + 2, y + 1, x + w - 2, y + 2, color);
		ctx.fill(x + 1, y + 2, x + w - 1, y + h - 2, color);
		ctx.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, color);
	}

	// ==================== 애니메이션 ====================

	private float anim(int i, boolean down) {
		if (down && !wasDown[i]) {
			flash[i] = 1f;   // 누른 순간 번쩍
		}
		wasDown[i] = down;
		float target = down ? 1f : 0f;
		float rate = down ? 38f : 14f;   // 누르기는 거의 즉시, 떼기는 톡 튀어 오르게
		press[i] += (target - press[i]) * Math.min(1f, dt * rate);
		if (Math.abs(target - press[i]) < 0.01f) {
			press[i] = target;
		}
		flash[i] = Math.max(0f, flash[i] - dt * 5f);
		return press[i];
	}

	// ==================== 키 ====================

	/** label == null 이면 스페이스바(가운데 작대기). */
	private void drawKey(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean down, int i) {
		float t = anim(i, down);
		if (style.get() == Style.KEYCAP) {
			drawKeycap(ctx, x, y, w, h, label, t, i);
		} else {
			drawModern(ctx, x, y, w, h, label, t, i);
		}
	}

	/**
	 * 기본: 사진처럼 어두운 반투명 둥근 키 + 옅은 밝은 테두리. 누르면 1px 안쪽으로 줄어들며(눌리는 느낌) 채움이 눌림 색으로,
	 * 누른 순간 바깥으로 번쩍임이 퍼졌다 사라진다.
	 */
	private void drawModern(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, float t, int i) {
		int acc = accentAt(x + w / 2, y);
		// 번쩍임(바깥 1px 링, 누른 순간 → 사라짐)
		if (flash[i] > 0.02f) {
			ring(ctx, x - 1, y - 1, w + 2, h + 2, argb(Math.round(0x90 * flash[i]), acc));
		}
		int s = Math.round(t);   // 눌리면 1px 줄어든다
		// 49-199차(사용자: "누를 때 키에 따라 움직임도 변하게"): 줄어든 윗면이 그 키의 방향으로 1px 밀린다
		// (W 위, A 왼쪽, S 아래, D 오른쪽, 스페이스와 클릭은 아래). 떼면 가운데로 돌아온다.
		int kx = x + s + DIR_X[i] * s, ky = y + s + DIR_Y[i] * s, kw = w - 2 * s, kh = h - 2 * s;
		int idle = idleColor.getArgb();
		int fill = lerp(idle, argb(0xD8, acc), t);
		inner(ctx, kx, ky, kw, kh, fill);
		int edgeIdle = rainbow.get() ? argb(0xB0, rainbowAt(x + w / 2, y)) : 0x70C7CFDA;
		ring(ctx, kx, ky, kw, kh, lerp(edgeIdle, argb(0xFF, shade(acc | 0xFF000000, 0.35f)), t));
		// 윗면 하이라이트(안 눌렸을 때만 살짝)
		if (t < 0.99f) {
			ctx.fill(kx + 2, ky + 1, kx + kw - 2, ky + 2, argb(Math.round(0x22 * (1f - t)), 0xFFFFFF));
		}
		drawLabel(ctx, kx, ky, kw, kh, label, lerp(0xFFFFFFFF, textOn(acc), t));
	}

	/**
	 * 3D 키캡: 아래 옆면(두께 3px, 어두운 색) 위에 윗면이 떠 있다. 누르면 윗면이 옆면 두께만큼 내려앉아 옆면이 사라지고,
	 * 윗면 가운데 오목한 면(접시)과 위 하이라이트로 진짜 키캡처럼 보인다.
	 */
	private void drawKeycap(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, float t, int i) {
		int depth = 3;
		int acc = accentAt(x + w / 2, y);
		int baseRgb = (idleColor.getArgb() & 0x00FFFFFF);
		// 너무 어두운 기본 색이면 키캡 회색으로
		int face = argb(0xF0, luminance(baseRgb) < 0.08f ? 0x2B3038 : baseRgb);
		face = lerp(face, argb(0xF0, acc), t * 0.85f);
		int side = shade(face, -0.45f);
		int faceH = h - depth;
		int drop = Math.round(depth * t);
		// 49-199차: 3D 키캡은 내려앉는 게 눌림이라, 옆 방향 키(A, D)만 1px 옆으로 기운다
		x += DIR_X[i] * Math.round(t);
		// 옆면(두께)
		round(ctx, x, y + depth, w, faceH, side);
		// 윗면
		int fy = y + drop;
		round(ctx, x, fy, w, faceH, face);
		// 윗면 테두리(아주 어둡게) + 위 하이라이트
		ring(ctx, x, fy, w, faceH, 0x90000000);
		ctx.fill(x + 2, fy + 1, x + w - 2, fy + 2, argb(Math.round(0x40 * (1f - t * 0.6f)), 0xFFFFFF));
		// 가운데 접시(살짝 어두운 안쪽 면)
		if (w > 8 && faceH > 8) {
			inner(ctx, x + 2, fy + 2, w - 4, faceH - 4, shade(face, -0.08f));
		}
		if (flash[i] > 0.02f) {
			ring(ctx, x - 1, y + drop - 1, w + 2, faceH + 2, argb(Math.round(0x80 * flash[i]), acc));
		}
		drawLabel(ctx, x, fy, w, faceH, label, lerp(0xFFE9ECEF, textOn(acc), t));
	}

	private static float luminance(int rgb) {
		return (0.2126f * ((rgb >> 16) & 0xFF) + 0.7152f * ((rgb >> 8) & 0xFF) + 0.0722f * (rgb & 0xFF)) / 255f;
	}

	/** 눌림 색 위 글자색: 밝은 색이면 검정, 어두우면 흰색. */
	private static int textOn(int acc) {
		return luminance(acc & 0x00FFFFFF) > 0.6f ? 0xFF101216 : 0xFFFFFFFF;
	}

	private void drawLabel(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, int color) {
		if (label == null) {
			int lineW = Math.min(22, w - 10);
			int lx = x + (w - lineW) / 2;
			int ly = y + h / 2;
			ctx.fill(lx, ly - 1, lx + lineW, ly, color);
			return;
		}
		// 49-172차(사용자: "wasd 글 위치가 한 칸 왼쪽으로 밀림"): 글자 폭에는 마지막 글자 뒤 1px 간격이 들어 있어 1을 뺀 잉크 폭으로
		// 맞춘다. 49-199차(사용자: "wasd가 오른쪽에 있는 거 같고 - 가운데 유지되게"): 키 폭 18은 짝수, 글자 잉크 5는 홀수라 정수
		// 자리로는 가운데가 안 나온다(49-172차 전엔 반 칸 왼쪽, 후엔 반 칸 오른쪽). 반 픽셀까지 옮겨 정확히 가운데에 둔다
		// (GUI 배율 2 이상이면 반 픽셀 = 실제 한 픽셀이라 흐려지지 않는다). 옮길 수 없는 옛 버전은 예전처럼.
		int ink = Math.max(1, LunaCompat.getTextWidth(client.font, label) - 1);
		if (LunaCompat.guiTransformSupported(ctx)) {
			float fx = x + (w - ink) / 2f;
			float fy = y + h / 2f - LunaCompat.textVisualCenter();
			LunaCompat.guiPush(ctx);
			try {
				LunaCompat.guiTranslate(ctx, fx, fy);
				LunaCompat.drawHudText(ctx, client.font, label, 0, 0, color);
			} finally {
				LunaCompat.guiPop(ctx);
			}
			return;
		}
		int tx = x + (w - ink + 1) / 2;
		int ty = y + Math.round(h / 2f - LunaCompat.textVisualCenter());
		LunaCompat.drawHudText(ctx, client.font, label, tx, ty, color);
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
		if (client.player == null && !isPreview()) {
			return; // 49-26차: 미리보기(타이틀 화면 등 월드 밖)에서는 눌리지 않은 키로 그림
		}
		long now = System.nanoTime();
		dt = lastNanos == 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
		lastNanos = now;

		boolean cap = style.get() == Style.KEYCAP;
		int box = cap ? BOX + 2 : BOX;         // 3D는 두께만큼 조금 더 높다
		int fullW = 3 * BOX + 2 * GAP;
		int spaceH = cap ? 12 : 10;
		int totalH = box + GAP + box
			+ (showSpace.get() ? GAP + spaceH : 0)
			+ (showMouse.get() ? GAP + box : 0);

		int originX = position.get().resolveX(client.getWindow().getGuiScaledWidth(), fullW);
		int y = position.get().resolveY(client.getWindow().getGuiScaledHeight(), totalH);
		boolean prev = isPreview();

		drawKey(context, originX + BOX + GAP, y, BOX, box, "W", !prev && isPressed("forwardKey", "keyForward"), 0);
		y += box + GAP;
		drawKey(context, originX, y, BOX, box, "A", !prev && isPressed("leftKey", "keyLeft"), 1);
		drawKey(context, originX + BOX + GAP, y, BOX, box, "S", !prev && isPressed("backKey", "keyBack"), 2);
		drawKey(context, originX + 2 * (BOX + GAP), y, BOX, box, "D", !prev && isPressed("rightKey", "keyRight"), 3);
		y += box;
		if (showSpace.get()) {
			y += GAP;
			drawKey(context, originX, y, fullW, spaceH, null, !prev && isPressed("jumpKey", "keyJump"), 4);
			y += spaceH;
		}
		if (showMouse.get()) {
			y += GAP;
			int half = (fullW - GAP) / 2;
			drawKey(context, originX, y, half, box, "좌클", !prev && isPressed("attackKey", "keyAttack"), 5);
			drawKey(context, originX + half + GAP, y, fullW - half - GAP, box, "우클", !prev && isPressed("useKey", "keyUse"), 6);
		}
	}

	private boolean isPressed(String newName, String oldName) {
		return LunaCompat.isKeyBindingPressed(LunaCompat.getKeyBindingField(client.options, newName, oldName));
	}
}
