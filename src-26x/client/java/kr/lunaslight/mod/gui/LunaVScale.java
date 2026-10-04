package kr.lunaslight.mod.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

/**
 * 49-247차(사용자: "GUI와 상관없이 그 UI 크기, 기능 화면 말고 키 지정 같은 데도 적용, 마크 창이 작아지면 GUI도 조절"):
 * 설정 화면(LunaClientScreen, 49-232차)에만 있던 "GUI 배율과 무관한 크기"를 다른 Nova 화면도 쓰게 뺀 것.
 *
 * <p>화면 픽셀 2칸 = 1단위(GUI 2 기준)인 가상 좌표계로 그리고, 마우스 좌표도 같은 배율로 바꿔 넘긴다. 창이 작아서
 * 화면이 원하는 크기(minW x minH 단위)가 안 들어가면 1단위 = 2픽셀보다 작게(최소 1픽셀) 줄인다 - 마크 창을 줄이면 화면도 같이 작아진다.
 * 행렬(배율)을 못 쓰는 버전은 예전 그대로(GUI 배율 따라감).
 *
 * <p>쓰는 법: 화면이 이 객체를 하나 들고, 그리기(begin/end), 입력(enter/k/exit), 배치(init 때 enter/exit)를 감싼다.
 * 가위(enableScissor)는 {@link #scissor}로 - 1.21.3 이하는 가위가 행렬을 안 따라서 실제 좌표로 바꿔 준다.
 */
public final class LunaVScale {
	private final Screen screen;
	private final int minW, minH;
	private boolean on, scisPose;
	private float k = 1f, px = 2f;
	private int realW, realH;
	private int initW = -1, initH = -1;
	/** 마지막 그리기에서 행렬을 쓸 수 있었는지(배치 때는 그리기 도구가 없어서 이 값을 본다). */
	private static boolean xformOk;

	public LunaVScale(Screen screen, int minW, int minH) {
		this.screen = screen;
		this.minW = Math.max(100, minW);
		this.minH = Math.max(80, minH);
	}

	/** 1단위가 화면 몇 픽셀인지(2 = GUI 2와 같음). */
	public float px() {
		return px;
	}

	private float calcK() {
		if (!xformOk) {
			return 1f;
		}
		double g;
		try {
			g = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale();
		} catch (Throwable t) {
			return 1f;
		}
		if (!(g > 0.0)) {
			return 1f;
		}
		double p = Math.max(1.0, Math.min(2.0, Math.min(realW * g / minW, realH * g / minH)));
		px = (float) p;
		float kk = (float) (g / p);
		return Math.abs(kk - 1f) < 0.01f ? 1f : kk;
	}

	/** 가상 크기로 들어간다(이미 들어가 있거나 바꿀 게 없으면 false). screen.width/height가 가상 크기로 바뀐다. */
	public boolean enter() {
		if (on) {
			return false;
		}
		realW = screen.width;
		realH = screen.height;
		k = calcK();
		if (k == 1f) {
			return false;
		}
		screen.width = Math.round(realW * k);
		screen.height = Math.round(realH * k);
		on = true;
		return true;
	}

	public void exit(boolean entered) {
		if (entered) {
			on = false;
			screen.width = realW;
			screen.height = realH;
		}
	}

	/** 입력 좌표에 곱할 값(들어갔으면 k, 아니면 1). */
	public double k(boolean entered) {
		return entered ? k : 1.0;
	}

	/** 그리기 시작: 가상 크기로 들어가 행렬을 줄인다. end와 짝. */
	public boolean begin(GuiGraphicsExtractor ctx) {
		xformOk = kr.lunaslight.mod.util.LunaCompat.guiTransformSupported(ctx);
		scisPose = kr.lunaslight.mod.util.LunaCompat.guiScissorFollowsPose(ctx);
		boolean e = enter();
		if (e) {
			kr.lunaslight.mod.util.LunaCompat.guiPush(ctx);
			kr.lunaslight.mod.util.LunaCompat.guiScale(ctx, 1f / k, 1f / k);
			LunaGfx.roundPx = px;
		}
		return e;
	}

	public void end(GuiGraphicsExtractor ctx, boolean entered) {
		if (entered) {
			LunaGfx.roundPx = 0f;
			kr.lunaslight.mod.util.LunaCompat.guiPop(ctx);
		}
		exit(entered);
	}

	/** 그리기 중 마우스 좌표(실제 → 가상). */
	public int mouse(int real) {
		return on ? (int) Math.floor((real + 0.5) * k) : real;
	}

	/** 배치(init)가 지금 크기로 되어 있는지 - 아니면 다시 배치해야 한다. */
	public boolean needsInit() {
		return screen.width != initW || screen.height != initH;
	}

	public void markInit() {
		initW = screen.width;
		initH = screen.height;
	}

	/** 가위 자르기 - 이 버전 가위가 행렬을 안 따르면 가상 좌표를 실제 좌표로 바꿔 준다. */
	public void scissor(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2) {
		if (on && !scisPose) {
			ctx.enableScissor((int) Math.floor(x1 / k), (int) Math.floor(y1 / k), (int) Math.ceil(x2 / k), (int) Math.ceil(y2 / k));
		} else {
			ctx.enableScissor(x1, y1, x2, y2);
		}
	}
}
