package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.impl.misc.VideoPipModule;
import kr.lunaslight.mod.util.LunaPip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;
import java.util.Locale;

/**
 * 49-179차(사용자: "영상 플랫폼이 여러 개 띄울 수도 있으니까 켜 놓은 리스트 중에 골라서 그걸 트는 느낌으로"):
 * [보고 있는 영상]의 창 고르기. 런처가 2초마다 알려 주는 창 목록(영상 사이트 창 먼저)을 줄로 보여 주고, 누르면
 * 그 창을 튼다. 맨 위 [자동]은 영상 사이트 창 중 하나를 알아서 고른다. 목록은 열려 있는 동안 계속 새로 고쳐진다.
 *
 * <p>49-189차(사용자: "화면 초점도 이상한 곳에 있어"): 아래 [영역 지정]을 누르면 고른 창 전체가 크게 보이고, 그 위에서
 * 끌어서 영상이 나오는 자리를 직접 정한다(모양은 비율 설정대로 16:9 또는 9:16으로 고정). [자동 영역]은 다시 움직이는 곳을
 * 찾는 방식으로 돌린다. 정한 자리는 창 전체를 0~1로 본 값이라 창 크기가 바뀌어도 같은 곳을 가리킨다.
 */
public class LunaVideoPickScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 400, 316);

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		try {
			if (lunaV.needsInit()) {
				lunaInit0();
				lunaV.markInit();
			}
			lunaRender0(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
		} finally {
			lunaV.end(ctx, e);
		}
	}

	@Override
	protected void init() {
		boolean e = lunaV.enter();
		try {
			lunaInit0();
			lunaV.markInit();
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseClicked0(mouseX * k, mouseY * k, button);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseReleased(double mouseX, double mouseY, int button) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseReleased0(mouseX * k, mouseY * k, button);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseDragged0(mouseX * k, mouseY * k, button, deltaX * k, deltaY * k);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseScrolled0(mouseX * k, mouseY * k, verticalAmount);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaKeyPressed0(keyCode, scanCode, modifiers);
		} finally {
			lunaV.exit(e);
		}
	}

	private static final int PANEL_W = 380;
	private static final int PANEL_H = 300;
	private static final int ROW_H = 24;
	private static final int PAD = 12;
	private static final int HEAD_H = 40;
	private static final int FOOT_H = 34;
	private static final int BTN_H = 20;

	private final Screen parent;
	private final VideoPipModule module;
	private int px, py;
	private double scroll;

	// 영역 지정 모드
	private boolean areaMode;
	private int apx, apy, apw, aph;          // 패널
	private int ix, iy, iw, ih;              // 그림이 그려진 자리(화면 좌표)
	private float selX, selY, selW, selH;    // 고른 자리(0~1), selW <= 0이면 없음
	private boolean dragging;
	private double dragX0, dragY0;

	public LunaVideoPickScreen(Screen parent, VideoPipModule module) {
		super(kr.lunaslight.mod.util.LunaCompat.textLiteral("영상 고르기"));
		this.parent = parent;
		this.module = module;
		LunaDraw.resetAnim("vpick");
	}

	private void lunaInit0() {
		px = (width - PANEL_W) / 2;
		py = Math.max(8, (height - PANEL_H) / 2);
		apw = Math.min(560, width - 24);
		aph = Math.min(380, height - 24);
		apx = (width - apw) / 2;
		apy = (height - aph) / 2;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		module.setAreaPicking(false);
		kr.lunaslight.mod.util.LunaCompat.setScreen(parent);
	}

	private List<String> rows() {
		return LunaPip.windows;
	}

	private int listTop() {
		return py + HEAD_H;
	}

	private int listH() {
		return PANEL_H - HEAD_H - FOOT_H - 4;
	}

	// ---- 아래 버튼 자리 ----
	private int footY() {
		return py + PANEL_H - FOOT_H + (FOOT_H - BTN_H) / 2;
	}

	private int areaBtnX() {
		return px + PAD;
	}

	private int autoBtnX() {
		return px + PAD + 96;
	}

	private int areaFootY() {
		return apy + aph - FOOT_H + (FOOT_H - BTN_H) / 2;
	}

	private int saveBtnX() {
		return apx + apw - PAD - 70;
	}

	private int cancelBtnX() {
		return apx + apw - PAD - 70 - 8 - 70;
	}

	private void lunaRender0(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaPip.poll();
		module.pumpFrames();
		LunaDraw.beginFrame();
		float open = LunaDraw.animFrom("vpick", 0f, 1f, 16f);
		LunaDraw.setAlpha(open);
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.applyAlpha(LunaDraw.OVERLAY));
		if (areaMode) {
			renderArea(ctx, mouseX, mouseY);
			return;
		}
		LunaDraw.panel3d(ctx, px, py, PANEL_W, PANEL_H, 10);   // 49-227차: 사진 시안 판

		LunaDraw.text(ctx, font, "영상 고르기", px + PAD, LunaDraw.textY(py + 10, 22), LunaDraw.TEXT);
		String sub = LunaPip.launcherPresent ? rows().size() + "개" : "런처로 실행해야 목록이 나옵니다";
		LunaDraw.text(ctx, font, sub, px + PAD + LunaDraw.width(font, "영상 고르기") + 8,
			LunaDraw.textY(py + 10, 22), LunaDraw.TEXT_DIM);
		int closeX = px + PANEL_W - PAD - 22;
		boolean closeHover = LunaDraw.in(mouseX, mouseY, closeX, py + 10, 22, 22);
		LunaDraw.button3d(ctx, closeX, py + 10, 22, 22, 5, LunaDraw.B_NEUTRAL, closeHover ? 1f : 0f);   // 49-227차
		LunaIcons.draw(ctx, font, LunaIcons.CLOSE, closeX + 6, LunaDraw.iconY(py + 10, 22),
			closeHover ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
		LunaDraw.fadeLine(ctx, px + PAD, py + HEAD_H - 4, PANEL_W - PAD * 2, LunaDraw.ACCENT);

		List<String> list = rows();
		String chosen = module.chosenWindow();
		int total = list.size() + 1;   // 맨 위 [자동]
		int lt = listTop(), lh = listH();
		int maxScroll = Math.max(0, total * ROW_H - lh);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		lunaV.scissor(ctx, px + 1, lt, px + PANEL_W - 1, lt + lh);
		for (int i = 0; i < total; i++) {
			int ry = lt + i * ROW_H - (int) scroll;
			if (ry + ROW_H < lt || ry > lt + lh) {
				continue;
			}
			String name = i == 0 ? "자동 (영상 사이트 창 중에서)" : list.get(i - 1);
			boolean sel = i == 0 ? chosen.isEmpty() : name.equals(chosen);
			boolean hov = LunaDraw.in(mouseX, mouseY, px + PAD, ry, PANEL_W - PAD * 2, ROW_H - 2);
			LunaDraw.card3d(ctx, px + PAD, ry, PANEL_W - PAD * 2, ROW_H - 4, hov ? 1f : 0f, sel);   // 49-227차
			if (sel) {
				LunaDraw.roundRect(ctx, px + PAD + 4, ry + 6, 3, ROW_H - 14, 1, LunaDraw.ACCENT);
			}
			String shown = LunaDraw.ellipsize(font, name, PANEL_W - PAD * 2 - 24);
			LunaDraw.text(ctx, font, shown, px + PAD + 14, LunaDraw.textY(ry, ROW_H - 2), sel ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
		}
		if (list.isEmpty()) {
			LunaDraw.textCentered(ctx, font, LunaPip.launcherPresent
				? "영상 창이 없습니다 - 브라우저에서 영상을 열어 두세요" : "Nova Client 런처로 게임을 켜면 창 목록이 나옵니다",
				px + PANEL_W / 2, lt + ROW_H + 16, LunaDraw.TEXT_DIM);
		}
		ctx.disableScissor();

		// 아래: [영역 지정] [자동 영역] + 지금 방식
		int fy = footY();
		// 49-222차(사용자: "영역 지정 클릭이 안 돼"): 예전엔 영상이 지금 찍히고 있을 때만 눌렸다. 게임을 다시 켜면 탭 제목(창 이름)이
		// 바뀌어 고른 창을 못 찾고 멈춰 있는 경우가 많아 버튼이 계속 회색이었다. 런처만 있으면 누를 수 있고, 꺼져 있으면 켜서 연다.
		boolean canArea = LunaPip.launcherPresent;
		boolean h1 = canArea && LunaDraw.in(mouseX, mouseY, areaBtnX(), fy, 88, BTN_H);
		LunaDraw.pillButton(ctx, font, areaBtnX(), fy, 88, BTN_H, "영역 지정", h1, canArea);
		boolean hasArea = !module.area().isEmpty();
		boolean h2 = hasArea && LunaDraw.in(mouseX, mouseY, autoBtnX(), fy, 88, BTN_H);
		LunaDraw.pillButton(ctx, font, autoBtnX(), fy, 88, BTN_H, "자동 영역", h2, false);
		String mode = !canArea ? "런처로 실행해야 영역을 정할 수 있습니다" : hasArea ? "직접 정한 자리" : "움직이는 곳 자동";
		LunaDraw.text(ctx, font, mode, autoBtnX() + 96, LunaDraw.textY(fy, BTN_H), LunaDraw.TEXT_DIM);
	}

	// ==================== 영역 지정 ====================

	private void openArea() {
		areaMode = true;
		dragging = false;
		module.setAreaPicking(true);
		float[] a = parseArea(module.area());
		if (a != null) {
			selX = a[0];
			selY = a[1];
			selW = a[2];
			selH = a[3];
		} else {
			selW = selH = 0;
		}
	}

	private void closeArea() {
		areaMode = false;
		dragging = false;
		module.setAreaPicking(false);
	}

	private static float[] parseArea(String s) {
		if (s == null || s.isEmpty()) {
			return null;
		}
		try {
			String[] p = s.split(",");
			if (p.length != 4) {
				return null;
			}
			float[] v = new float[4];
			for (int i = 0; i < 4; i++) {
				v[i] = Float.parseFloat(p[i].trim());
			}
			return v[2] > 0.01f && v[3] > 0.01f ? v : null;
		} catch (Throwable t) {
			return null;
		}
	}

	private void renderArea(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		LunaDraw.panel3d(ctx, apx, apy, apw, aph, 10);   // 49-227차: 사진 시안 판
		LunaDraw.text(ctx, font, "영역 지정", apx + PAD, LunaDraw.textY(apy + 10, 22), LunaDraw.TEXT);
		LunaDraw.text(ctx, font, "영상이 나오는 곳을 끌어서 고르세요", apx + PAD + LunaDraw.width(font, "영역 지정") + 8,
			LunaDraw.textY(apy + 10, 22), LunaDraw.TEXT_DIM);
		LunaDraw.fadeLine(ctx, apx + PAD, apy + HEAD_H - 4, apw - PAD * 2, LunaDraw.ACCENT);

		// 그림 자리: 가운데에 비율 그대로 맞춰 넣는다
		int bx = apx + PAD, by = apy + HEAD_H, bw = apw - PAD * 2, bh = aph - HEAD_H - FOOT_H - 4;
		Identifier tex = module.frameTexture();
		int tw = module.frameWidth(), th = module.frameHeight();
		ctx.fill(bx, by, bx + bw, by + bh, 0xFF0B0C0E);
		if (tex == null || tw <= 0 || th <= 0) {
			iw = ih = 0;
			// 49-222차: 런처가 창을 못 찾았으면 그 이유를 보여 준다(무한 "받아 오는 중" 대신)
			String err = LunaPip.error;
			String wait = !LunaPip.running && err != null && !err.isEmpty() ? err + " - 목록에서 창을 다시 고르세요" : "창을 받아 오는 중…";
			LunaDraw.textCentered(ctx, font, LunaDraw.ellipsize(font, wait, bw - 16), bx + bw / 2, by + bh / 2 - 4, LunaDraw.TEXT_DIM);
		} else {
			float k = Math.min(bw / (float) tw, bh / (float) th);
			iw = Math.max(1, Math.round(tw * k));
			ih = Math.max(1, Math.round(th * k));
			ix = bx + (bw - iw) / 2;
			iy = by + (bh - ih) / 2;
			LunaGfx.drawImageRegion(ctx, tex, ix, iy, iw, ih, 0, 0, tw, th, tw, th, 0xFFFFFFFF);
			if (selW > 0 && selH > 0) {
				int sx = ix + Math.round(selX * iw), sy = iy + Math.round(selY * ih);
				int sw = Math.round(selW * iw), sh = Math.round(selH * ih);
				// 바깥은 어둡게
				int dim = 0x99000000;
				ctx.fill(ix, iy, ix + iw, sy, dim);
				ctx.fill(ix, sy + sh, ix + iw, iy + ih, dim);
				ctx.fill(ix, sy, sx, sy + sh, dim);
				ctx.fill(sx + sw, sy, ix + iw, sy + sh, dim);
				LunaDraw.roundRectOutline(ctx, sx - 1, sy - 1, sw + 2, sh + 2, 0, LunaDraw.ACCENT);
			}
		}

		int fy = areaFootY();
		boolean hc = LunaDraw.in(mouseX, mouseY, cancelBtnX(), fy, 70, BTN_H);
		LunaDraw.pillButton(ctx, font, cancelBtnX(), fy, 70, BTN_H, "취소", hc, false);
		boolean canSave = selW > 0 && selH > 0;
		boolean hs = canSave && LunaDraw.in(mouseX, mouseY, saveBtnX(), fy, 70, BTN_H);
		LunaDraw.pillButton(ctx, font, saveBtnX(), fy, 70, BTN_H, "저장", hs, canSave);
		String shape = module.shortsRatio() ? "쇼츠 모양(9:16)으로 잡힙니다" : "일반 영상 모양(16:9)으로 잡힙니다";
		LunaDraw.text(ctx, font, shape, apx + PAD, LunaDraw.textY(fy, BTN_H), LunaDraw.TEXT_DIM);
	}

	/** 끈 두 점으로 비율이 고정된 사각형(그림 안쪽으로 잘라 넣음). */
	private void updateSelection(double mx, double my) {
		if (iw <= 0 || ih <= 0) {
			return;
		}
		int tw = Math.max(1, module.frameWidth()), th = Math.max(1, module.frameHeight());
		// 원본 픽셀 기준 비율 → 화면에 그려진 그림(같은 배율)에서도 같은 비율
		float aspect = module.shortsRatio() ? 9f / 16f : 16f / 9f;
		double x0 = Math.max(ix, Math.min(ix + iw, dragX0)), y0 = Math.max(iy, Math.min(iy + ih, dragY0));
		double x1 = Math.max(ix, Math.min(ix + iw, mx)), y1 = Math.max(iy, Math.min(iy + ih, my));
		double w = Math.abs(x1 - x0), h = Math.abs(y1 - y0);
		if (w < 2 && h < 2) {
			return;
		}
		if (w / Math.max(1e-6, h) > aspect) {
			w = h * aspect;
		} else {
			h = w / aspect;
		}
		double sx = x1 >= x0 ? x0 : x0 - w;
		double sy = y1 >= y0 ? y0 : y0 - h;
		// 그림 밖으로 나가면 비율을 지킨 채 줄인다
		double maxW = x1 >= x0 ? ix + iw - x0 : x0 - ix;
		double maxH = y1 >= y0 ? iy + ih - y0 : y0 - iy;
		double f = Math.min(1.0, Math.min(maxW / Math.max(1e-6, w), maxH / Math.max(1e-6, h)));
		w *= f;
		h *= f;
		sx = x1 >= x0 ? x0 : x0 - w;
		sy = y1 >= y0 ? y0 : y0 - h;
		selX = (float) ((sx - ix) / iw);
		selY = (float) ((sy - iy) / ih);
		selW = (float) (w / iw);
		selH = (float) (h / ih);
	}

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		if (areaMode) {
			int fy = areaFootY();
			if (LunaDraw.in(mouseX, mouseY, cancelBtnX(), fy, 70, BTN_H)) {
				closeArea();
				return true;
			}
			if (selW > 0 && selH > 0 && LunaDraw.in(mouseX, mouseY, saveBtnX(), fy, 70, BTN_H)) {
				module.setArea(String.format(Locale.ROOT, "%.4f,%.4f,%.4f,%.4f", selX, selY, selW, selH));
				closeArea();
				return true;
			}
			if (iw > 0 && LunaDraw.in(mouseX, mouseY, ix, iy, iw, ih)) {
				dragging = true;
				dragX0 = mouseX;
				dragY0 = mouseY;
			}
			return true;
		}
		int closeX = px + PANEL_W - PAD - 22;
		if (LunaDraw.in(mouseX, mouseY, closeX, py + 10, 22, 22) || !LunaDraw.in(mouseX, mouseY, px, py, PANEL_W, PANEL_H)) {
			onClose();
			return true;
		}
		int fy = footY();
		if (LunaDraw.in(mouseX, mouseY, areaBtnX(), fy, 88, BTN_H)) {
			if (LunaPip.launcherPresent) {
				if (!module.isEnabled()) {
					module.setEnabled(true);
					kr.lunaslight.mod.config.LunaClientConfig.save();
				}
				openArea();
			}
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, autoBtnX(), fy, 88, BTN_H)) {
			module.setArea("");
			return true;
		}
		int lt = listTop(), lh = listH();
		if (!LunaDraw.in(mouseX, mouseY, px + PAD, lt, PANEL_W - PAD * 2, lh)) {
			return true;
		}
		int i = (int) ((mouseY - lt + scroll) / ROW_H);
		List<String> list = rows();
		if (i == 0) {
			module.chooseWindow("");
		} else if (i - 1 < list.size()) {
			module.chooseWindow(list.get(i - 1));
		}
		return true;
	}

	private boolean lunaMouseDragged0(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (areaMode && dragging) {
			updateSelection(mouseX, mouseY);
			return true;
		}
		return false;
	}

	private boolean lunaMouseReleased0(double mouseX, double mouseY, int button) {
		if (areaMode && dragging) {
			updateSelection(mouseX, mouseY);
			dragging = false;
			return true;
		}
		return false;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double amount) {
		if (!areaMode) {
			scroll -= amount * ROW_H;
		}
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (keyCode == InputConstants.KEY_ESCAPE) {
			if (areaMode) {
				closeArea();
			} else {
				onClose();
			}
			return true;
		}
		return false;
	}
}
