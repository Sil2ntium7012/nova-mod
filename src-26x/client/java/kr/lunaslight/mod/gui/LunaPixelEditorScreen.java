package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.PixelArt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * 49-170차 → 49-172차 → 49-174차(사용자: "투명도 게이지로 0~100, 색도 더 세부적으로, 무지개 블록이랑 빛나는 블록,
 * 항상 HUD는 중앙에, 널널하게"): HUD 배경 픽셀 편집기.
 *
 * <p>위: 제목. 가운데: 캔버스 = <b>그 기능의 HUD 상자 실제 크기</b>(1칸 = 1px)를 화면 한가운데에 확대(휠 배율, 가운데
 * 버튼 끌기로 이동). 기능이 지금 설정대로 그리는 모습(글자·아이콘)이 캔버스 위에 그대로 겹쳐 보이고, 그 아래 배경만
 * 칠한다. 아래: 넓은 도구 막대 - 도구 / 효과(없음·무지개·빛남·반짝임) / 색(채도·밝기 네모 + 색상 막대 + 색코드 +
 * 자주 쓰는 색) / 투명도 게이지(0~100) / 실제 크기 미리보기 / 버튼.
 *
 * <p>동작 원리: {@link Module#pixelEditing}에 이 기능을 걸고 기능의 미리보기를 확대(cell배) 변환 안에서 그리게 한다.
 * 기능이 배경 상자를 그리려는 순간({@code Module.hudBox}) 그 자리를 적어 두고 편집 중인 그림을 대신 그린다.
 */
public class LunaPixelEditorScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 480, 320);

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		try {
			lunaRender0(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
		} finally {
			lunaV.end(ctx, e);
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

	@Override
	protected boolean lunaCharTyped(char chr) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaCharTyped0(chr);
		} finally {
			lunaV.exit(e);
		}
	}

	private static final int[] PALETTE = {
			0xFFFFFF, 0xD5D9DE, 0x8E959D, 0x3A4048, 0x14181C, 0x000000,
			0xE05A5A, 0xF08A3C, 0xF2D34B, 0x7CC96B, 0x46B8A0, 0x4E8DE0,
			0x8B6CE0, 0xE070B8, 0x8A5A3C, 0xF6C6A2, 0xA9D973, 0x9AD8F0};
	private static final String[] TOOLS = {"브러시", "지우개", "채우기", "스포이드", "선택"};
	private static final int TOOL_SELECT = 4;
	private static final int TOOL_BW = 44;   // 49-497차: 도구 5개
	private static final int ROW_H = 98;    // 도구 막대 한 줄 높이(칸 제목 + 내용)
	private static final int ROW_GAP = 6;
	private static final int SQ = 60;       // 채도·밝기 네모 한 변
	private static final int BAR_W = 10;    // 색상 막대 폭

	private final Screen parent;
	private final Module module;
	private PixelArt art;
	private final PixelArt existing;
	private final ArrayDeque<PixelArt> undo = new ArrayDeque<>();
	// 49-497차(사용자: "되돌리기랑 앞으로 가기, 드래그 모드 넣어서 그린 거 드래그로 지정해서 없애거나 옮길 수 있게, 드래그에 복사도 넣어서
	// 완전 다른 HUD에 넣을 수 있게"): 앞으로 가기 + 선택 도구. 선택한 칸을 끌면 떠서(floating) 움직이고, 다른 데를 누르거나 도구를
	// 바꾸면 내려앉는다(투명한 칸은 밑그림을 안 지움). 복사한 그림은 편집기 밖(static)에 있어 다른 기능의 편집기에서도 붙여 넣는다.
	private final ArrayDeque<PixelArt> redo = new ArrayDeque<>();
	private static PixelArt clipboard;
	private int selX, selY, selW, selH;      // 그림 좌표, selW 0 = 선택 없음
	private PixelArt floating;
	private boolean selecting, moving, selDragged;
	private int selAx, selAy, moveSx, moveSy, moveOx, moveOy;
	private int lastMouseX = -1, lastMouseY = -1;

	private int tool;
	private int effect;                      // PixelArt.FX_*
	private float palH, palS = 0.5f, palV = 0.9f;
	private int alphaPct = 100;
	private String hexBuf = "";
	private boolean hexFocus;
	private boolean painting;
	private int dragChannel;                 // 0 없음, 10 네모, 11 색상 막대, 12 투명도
	private int lastPx = -1, lastPy = -1;
	private String tip;

	// 배치
	private int cell = 6;
	private int zoom;
	private int panX, panY;
	private boolean panning;
	private int boxW, boxH;
	private int canvasX, canvasY;
	private int left, top, contentW, contentH;
	private int areaX, areaY, areaW, areaH;
	private int barX, barY, barW, barH;
	// 도구 막대 안 자리(그리기와 클릭이 같은 값을 쓰게 한 번 계산). Y는 각 칸 제목 줄.
	private int secToolsX, secColorX, secPalX, secAlphaX, secPrevX, secBtnX;
	private int secToolsY, secColorY, secPalY, secAlphaY, secPrevY, secBtnY;
	// 49-174차: GUI 배율이 커서 화면이 좁으면(1080p 자동 배율 4 = 480×270) 편집기만 한 단계 작게 그린다.
	// uiS = 편집기 배율 / 게임 GUI 배율, vw·vh = 그 배율에서의 화면 크기. 마우스 좌표도 같은 비율로 나눈다.
	private float uiS = 1f;
	// 49-176차(사용자: "노래 제목같은 그런 글을 못그리는 거지 배경은 가능해야 함"): 글자와 아이콘은 기능이 매번 그 위에
	// 새로 그리는 것이라 그림에 들어가지 않는다 - 배경은 어디든 칠할 수 있다. [내용 가림]을 켜면 글자와 아이콘을 가려
	// 배경 그림만 보인다(글자 밑을 칠할 때 보기 편하게).
	private boolean hideContent;
	private int vw, vh;
	private boolean unsupported;
	private int framesWithoutBox;

	public LunaPixelEditorScreen(Screen parent, Module module) {
		super(LunaCompat.textLiteral("배경 그리기"));
		this.parent = parent;
		this.module = module;
		this.existing = module.hudPixelArt();
		setRgb(LunaDraw.ACCENT & 0x00FFFFFF);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ==================== 색 ====================

	private int rgb() {
		return PixelArt.hsv(palH, palS, palV) & 0x00FFFFFF;
	}

	private void setRgb(int rgb) {
		float[] hsv = rgbToHsv(rgb);
		palH = hsv[0];
		palS = hsv[1];
		palV = hsv[2];
	}

	private int currentColor() {
		int a = Math.round(alphaPct * 2.55f);
		return (Math.max(0, Math.min(255, a)) << 24) | rgb();
	}

	private static float[] rgbToHsv(int argb) {
		float r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
		float d = max - min;
		float h = 0;
		if (d > 0) {
			if (max == r) {
				h = (g - b) / d;
			} else if (max == g) {
				h = (b - r) / d + 2f;
			} else {
				h = (r - g) / d + 4f;
			}
			h /= 6f;
			if (h < 0) {
				h += 1f;
			}
		}
		return new float[]{h, max == 0 ? 0 : d / max, max};
	}

	// ==================== 배치 ====================

	/** 편집기 배율: 게임 GUI 배율보다 크게 하지 않고, 편집기가 900×500(안 되면 620×340) 이상 넓게 보이는 가장 큰 정수 배율. */
	private float computeUiScale() {
		int g = Math.max(1, LunaCompat.currentGuiScale());
		if (g <= 1) {
			return 1f;
		}
		int ww = width * g, wh = height * g;
		for (int e = g; e >= 2; e--) {
			if (ww / e >= 900 && wh / e >= 500) {
				return e / (float) g;
			}
		}
		for (int e = g; e >= 1; e--) {
			if (ww / e >= 620 && wh / e >= 340) {
				return e / (float) g;
			}
		}
		return 1f / g;
	}

	/** 배율을 넣고 그릴 때도 맞는 가위 자르기(1.21.6+는 행렬을 따르고, 그 전은 화면 좌표라 직접 곱한다). */
	private void scissor(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2) {
		if (uiS != 1f && !LunaCompat.guiScissorFollowsPose(ctx)) {
			lunaV.scissor(ctx, (int) Math.floor(x1 * uiS), (int) Math.floor(y1 * uiS), (int) Math.ceil(x2 * uiS), (int) Math.ceil(y2 * uiS));
		} else {
			lunaV.scissor(ctx, x1, y1, x2, y2);
		}
	}

	private void layout() {
		if (vw <= 0 || vh <= 0) {
			vw = width;
			vh = height;
		}
		contentW = vw - 24;
		contentH = vh - 24;
		left = 12;
		top = 12;
		barW = contentW;
		barX = left;
		// 도구 막대 칸: [도구/효과] [색] [자주 쓰는 색] [투명도] … [실제 크기] [버튼]. 폭이 모자라면 다음 줄로 넘긴다.
		int[] w = {240, 170, 100, 136};
		int[] sx = new int[4], sr = new int[4];
		int lim = barX + barW - 12;
		int x = barX + 12, row = 0;
		for (int i = 0; i < w.length; i++) {
			if (x + w[i] > lim && x > barX + 12) {
				row++;
				x = barX + 12;
			}
			sx[i] = x;
			sr[i] = row;
			x += w[i] + 14;
		}
		if (x + 150 > lim) {
			row++;
			x = barX + 12;
		}
		int rows = row + 1;
		barH = 20 + rows * ROW_H + (rows - 1) * ROW_GAP;
		barY = top + contentH - barH;
		secToolsX = sx[0];
		secColorX = sx[1];
		secPalX = sx[2];
		secAlphaX = sx[3];
		secBtnX = lim - 150;
		secPrevX = x;
		int by0 = barY + 10;
		secToolsY = by0 + sr[0] * (ROW_H + ROW_GAP);
		secColorY = by0 + sr[1] * (ROW_H + ROW_GAP);
		secPalY = by0 + sr[2] * (ROW_H + ROW_GAP);
		secAlphaY = by0 + sr[3] * (ROW_H + ROW_GAP);
		secBtnY = by0 + row * (ROW_H + ROW_GAP);
		secPrevY = secBtnY;
		areaX = left;
		layoutFileBar();
		areaY = top + 24 + fileBarH + 8;
		areaW = contentW;
		areaH = barY - 10 - areaY;
		if (boxW > 0 && boxH > 0) {
			int pd = pad();
			int aw = boxW + 2 * pd, ah = boxH + 2 * pd;
			int fit = Math.max(2, Math.min(12, Math.min(areaW / aw, areaH / ah)));
			cell = zoom > 0 ? zoom : Math.max(4, fit);
			int cw = aw * cell, chh = ah * cell;
			int maxPanX = Math.max(0, (cw - areaW) / 2 + 8), maxPanY = Math.max(0, (chh - areaH) / 2 + 8);
			panX = Math.max(-maxPanX, Math.min(maxPanX, panX));
			panY = Math.max(-maxPanY, Math.min(maxPanY, panY));
			// canvasX/Y = HUD 상자 왼쪽 위(여백 안쪽). 그림 전체는 artX()/artY()부터.
			canvasX = areaX + (areaW - cw) / 2 - panX + pd * cell;
			canvasY = areaY + (areaH - chh) / 2 - panY + pd * cell;
		}
	}

	private int pad() {
		return art == null ? 0 : art.pad;
	}

	private int artX() {
		return canvasX - pad() * cell;
	}

	private int artY() {
		return canvasY - pad() * cell;
	}

	private void ensureArt() {
		if (boxW <= 0 || boxH <= 0) {
			return;
		}
		if (art != null && art.w == boxW + 2 * art.pad && art.h == boxH + 2 * art.pad) {
			return;
		}
		if (art != null) {
			settle();
			art = art.resizedBox(boxW, boxH);
		} else if (existing != null) {
			art = existing.resizedBox(boxW, boxH);
		} else {
			art = PixelArt.roundedBox(boxW, boxH, module.hudBgColorForEditor(), 4);
		}
	}

	// ==================== 그리기 ====================

	private void lunaRender0(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaClientScreen.applyScreenTheme();
		boolean scaled = false;
		try {
			LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.OVERLAY);
			ctx.fill(0, 0, width, height, LunaDraw.applyAlpha((LunaClientScreen.themeBg() & 0x00FFFFFF) | 0xEE000000));
			float s = computeUiScale();
			if (s < 0.999f && LunaCompat.guiTransformSupported(ctx)) {
				uiS = s;
			} else {
				uiS = 1f;
			}
			vw = Math.round(width / uiS);
			vh = Math.round(height / uiS);
			if (uiS != 1f) {
				LunaCompat.guiPush(ctx);
				LunaCompat.guiScale(ctx, uiS, uiS);
				scaled = true;
			}
			draw(ctx, (int) Math.floor(mouseX / uiS), (int) Math.floor(mouseY / uiS), delta);
			if (scaled) {
				LunaCompat.guiPop(ctx);
				scaled = false;
			}
			// 설명 말풍선은 배율 밖(원래 크기)에서 실제 마우스 자리에
			if (tip != null) {
				ctx.setTooltipForNextFrame(font, LunaCompat.textLiteral(tip), mouseX, mouseY);
			}
		} finally {
			if (scaled) {
				LunaCompat.guiPop(ctx);
			}
			LunaClientScreen.restoreScreenTheme();
			Module.pixelEditing = null;
			Module.pixelEditingArt = null;
		}
	}

	private void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		layout();
		tip = null;
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		int dim = LunaClientScreen.themeDim();
		int acc = LunaClientScreen.themeAccent();

		LunaDraw.text(ctx, font, "배경 그리기", left + 4, LunaDraw.textY(top, 16), txt);
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, module.getDisplayName(), 220),
			left + 4 + LunaDraw.width(font, "배경 그리기") + 10, LunaDraw.textY(top, 16), sub);
		boolean ch = LunaDraw.in(mouseX, mouseY, left + contentW - 18, top, 16, 16);
		LunaIcons.drawInBox(ctx, font, LunaIcons.CLOSE, left + contentW - 18, top, 16, ch ? acc : sub);
		// 내용 가림 켜고 끄기
		int lbw = lockBtnW(), lbx = lockBtnX();
		boolean lh = LunaDraw.in(mouseX, mouseY, lbx, top, lbw, 16);
		LunaDraw.roundRect(ctx, lbx, top, lbw, 16, 4, LunaClientScreen.ink(lh ? 0x26 : 0x14));
		LunaDraw.roundRectOutline(ctx, lbx + 5, top + 4, 8, 8, 2, hideContent ? acc : LunaClientScreen.ink(0x60));
		if (hideContent) {
			LunaDraw.roundRect(ctx, lbx + 7, top + 6, 4, 4, 1, acc);
		}
		LunaDraw.text(ctx, font, "내용 가림", lbx + 17, LunaDraw.textY(top, 16), hideContent ? txt : sub);
		if (lh) {
			tip = "HUD 글자와 아이콘을 가리고 배경 그림만 표시 (글자는 그림에 들어가지 않음)";
		}
		// 49-497차: 되돌리기 / 앞으로 + 바깥 여백
		int ux = undoBtnX();
		miniBtn(ctx, ux, top, 56, "되돌리기", !undo.isEmpty() || floating != null, mouseX, mouseY);
		miniBtn(ctx, ux + 60, top, 56, "앞으로", !redo.isEmpty(), mouseX, mouseY);
		if (LunaDraw.in(mouseX, mouseY, ux, top, 56, 16)) {
			tip = "되돌리기 (Ctrl+Z)";
		} else if (LunaDraw.in(mouseX, mouseY, ux + 60, top, 56, 16)) {
			tip = "앞으로 가기 (Ctrl+Y)";
		}
		int px0 = padCtrlX();
		miniBtn(ctx, px0, top, 16, "-", pad() > 0, mouseX, mouseY);
		LunaDraw.textCentered(ctx, font, "여백 " + pad() + "px", px0 + 16 + padLabelW() / 2, LunaDraw.textY(top, 16), pad() > 0 ? txt : sub);
		miniBtn(ctx, px0 + 16 + padLabelW(), top, 16, "+", pad() < PixelArt.MAX_PAD, mouseX, mouseY);
		if (LunaDraw.in(mouseX, mouseY, px0, top, padCtrlW(), 16)) {
			tip = "HUD 상자 바깥으로 더 크게 그리기 (옆면, 그림자, 장식). 누를 때마다 2px, Shift는 8px";
		}

		drawFileBar(ctx, mouseX, mouseY, txt, sub, dim, acc);
		drawCanvas(ctx, mouseX, mouseY, dim);
		drawBar(ctx, mouseX, mouseY, txt, sub, dim, acc);
		if (toast != null && System.currentTimeMillis() - toastAt < 2500) {
			int tw = LunaDraw.width(font, toast) + 16;
			LunaDraw.card3d(ctx, (vw - tw) / 2, vh - 34, tw, 18, 4, LunaClientScreen.themeCard(), LunaClientScreen.themeLine());
			LunaDraw.textCentered(ctx, font, toast, vw / 2, LunaDraw.textY(vh - 34, 18), txt);
		}
		if (confirm.isOpen()) {
			confirm.render(ctx, font, vw, vh, mouseX, mouseY);
		}
	}

	// ==================== 49-492차: 슬롯 3칸 + 파일 내보내기/가져오기 ====================
	// 사용자: "그림 그리기는 슬롯 형태로 3개까지, 덮어쓰시겠습니까? 요런 것도 다, 직접 그린 배경 전체랑 개별 모두 파일로 뽑아서 보내고 그 파일 받아서 적용".
	// 머리 아래 한 줄: [슬롯 1 (작은 그림) 불러오기 저장] ×3 … [내보내기] [가져오기] [전체 내보내기] [전체 가져오기].
	// 폭이 모자라면 파일 버튼은 다음 줄로. 덮어쓰기, 버리기는 확인 창(LunaConfirm)으로 묻는다.

	private final LunaConfirm confirm = new LunaConfirm();
	private String toast;
	private long toastAt;
	private int fileBarH = 20;
	private int slotX0, slotY0, fileX0, fileY0;
	private static final int SLOT_W = 128;
	private static final int THUMB_W = 34;
	private static final String[] FILE_BTNS = {"내보내기", "가져오기", "전체 내보내기", "전체 가져오기"};

	private void toast(String s) {
		if (s != null) {
			toast = s;
			toastAt = System.currentTimeMillis();
		}
	}

	private int fileBtnW(int i) {
		return LunaDraw.width(font, FILE_BTNS[i]) + 14;
	}

	private void layoutFileBar() {
		int slotsW = 3 * SLOT_W + 2 * 6;
		int filesW = 0;
		for (int i = 0; i < FILE_BTNS.length; i++) {
			filesW += fileBtnW(i) + (i > 0 ? 4 : 0);
		}
		slotX0 = left + 4;
		slotY0 = top + 24;
		if (slotsW + 16 + filesW <= contentW - 8) {
			fileBarH = 20;
			fileX0 = left + contentW - 4 - filesW;
			fileY0 = slotY0;
		} else {
			fileBarH = 44;
			fileX0 = left + 4;
			fileY0 = slotY0 + 24;
		}
	}

	private void drawFileBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int txt, int sub, int dim, int acc) {
		for (int i = 0; i < 3; i++) {
			int x = slotX0 + i * (SLOT_W + 6);
			int y = slotY0;
			LunaDraw.roundRect(ctx, x, y, SLOT_W, 20, 5, LunaClientScreen.ink(0x10));
			String raw = module.hudSlotRaw(i);
			PixelArt pa = raw == null || raw.isEmpty() ? null : PixelArt.decode(raw);
			LunaDraw.text(ctx, font, String.valueOf(i + 1), x + 5, LunaDraw.textY(y, 20), pa == null ? dim : txt);
			int tx = x + 13;
			LunaDraw.roundRect(ctx, tx, y + 3, THUMB_W, 14, 2, LunaClientScreen.ink(0x18));
			if (pa != null) {
				try {
					pa.draw(ctx, tx + 1, y + 4, THUMB_W - 2, 12, 1f);
				} catch (Throwable ignored) {
				}
			} else {
				LunaDraw.textCentered(ctx, font, "빔", tx + THUMB_W / 2, LunaDraw.textY(y + 3, 14), dim);
			}
			int bx = tx + THUMB_W + 4;
			int lw = (SLOT_W - (bx - x) - 6) / 2;
			miniBtn(ctx, bx, y + 2, lw, "불러오기", pa != null, mouseX, mouseY);
			miniBtn(ctx, bx + lw + 2, y + 2, lw, "저장", art != null, mouseX, mouseY);
		}
		int x = fileX0;
		for (int i = 0; i < FILE_BTNS.length; i++) {
			int w = fileBtnW(i);
			miniBtn(ctx, x, fileY0 + 2, w, FILE_BTNS[i], true, mouseX, mouseY);
			if (LunaDraw.in(mouseX, mouseY, x, fileY0 + 2, w, 16)) {
				tip = switch (i) {
					case 0 -> "지금 그린 그림을 파일로 저장";
					case 1 -> "파일의 그림을 지금 캔버스로 불러오기";
					case 2 -> "모든 기능의 배경 그림과 슬롯을 파일 하나로 저장";
					default -> "파일의 모든 기능 배경 그림과 슬롯을 적용";
				};
			}
			x += w + 4;
		}
	}

	private void miniBtn(GuiGraphicsExtractor ctx, int x, int y, int w, String label, boolean on, int mouseX, int mouseY) {
		boolean hov = on && LunaDraw.in(mouseX, mouseY, x, y, w, 16);
		LunaDraw.roundRect(ctx, x, y, w, 16, 4, on ? LunaClientScreen.ink(hov ? 0x30 : 0x18) : LunaClientScreen.ink(0x08));
		LunaDraw.textCentered(ctx, font, LunaDraw.ellipsize(font, label, w - 4), x + w / 2, LunaDraw.textY(y, 16),
			on ? (hov ? LunaClientScreen.themeText() : LunaClientScreen.themeSub()) : LunaClientScreen.themeDim());
	}

	/** 파일 줄 클릭. 처리했으면 true. */
	private boolean clickFileBar(double mx, double my) {
		for (int i = 0; i < 3; i++) {
			int slot = i;
			int x = slotX0 + i * (SLOT_W + 6);
			int tx = x + 13;
			int bx = tx + THUMB_W + 4;
			int lw = (SLOT_W - (bx - x) - 6) / 2;
			String raw = module.hudSlotRaw(i);
			boolean filled = raw != null && !raw.isEmpty();
			if (LunaDraw.in(mx, my, bx, slotY0 + 2, lw, 16) && filled) {
				Runnable load = () -> {
					PixelArt pa = PixelArt.decode(module.hudSlotRaw(slot));
					if (pa != null) {
						settle();
						pushUndo();
						art = boxW > 0 && boxH > 0 ? pa.resizedBox(boxW, boxH) : pa;
						toast("슬롯 " + (slot + 1) + " 그림을 불러왔어요 (저장을 눌러야 적용)");
					}
				};
				if (art != null && !art.isEmpty()) {
					confirm.showInfo(LunaIcons.INFO, "슬롯 " + (slot + 1) + " 불러오기", "지금 그리던 그림을 슬롯 그림으로 바꿀까요?",
						"Ctrl+Z로 되돌릴 수 있어요", "불러오기", "취소", load);
				} else {
					load.run();
				}
				return true;
			}
			if (LunaDraw.in(mx, my, bx + lw + 2, slotY0 + 2, lw, 16) && art != null) {
				settle();
				Runnable save = () -> {
					module.setHudSlotRaw(slot, art.isEmpty() ? "" : art.encode());
					kr.lunaslight.mod.config.LunaClientConfig.save();
					toast("슬롯 " + (slot + 1) + "에 저장했어요");
				};
				if (filled) {
					confirm.show(LunaIcons.INFO, "슬롯 " + (slot + 1) + " 덮어쓰기", "이미 그림이 있어요. 덮어쓰시겠습니까?", "덮어쓰기", save);
				} else {
					save.run();
				}
				return true;
			}
		}
		int x = fileX0;
		for (int i = 0; i < FILE_BTNS.length; i++) {
			int w = fileBtnW(i);
			if (LunaDraw.in(mx, my, x, fileY0 + 2, w, 16)) {
				fileAction(i);
				return true;
			}
			x += w + 4;
		}
		return false;
	}

	private void fileAction(int i) {
		settle();
		switch (i) {
			case 0 -> {
				if (art == null || art.isEmpty()) {
					toast("내보낼 그림이 없어요");
					return;
				}
				toast("저장할 곳을 고르는 창을 여는 중…");
				kr.lunaslight.mod.util.BackgroundFiles.exportFile(kr.lunaslight.mod.util.BackgroundFiles.single(art.encode()),
					"nova-bg-" + module.getId(), this::toast);
			}
			case 1 -> {
				toast("가져올 파일을 고르는 창을 여는 중…");
				kr.lunaslight.mod.util.BackgroundFiles.importFile((o, msg) -> {
					if (o == null) {
						toast(msg);
						return;
					}
					String one = kr.lunaslight.mod.util.BackgroundFiles.pickOne(o, module.getId());
					PixelArt pa = one == null || one.isEmpty() ? null : PixelArt.decode(one);
					if (pa == null) {
						toast("파일에 쓸 수 있는 그림이 없어요");
						return;
					}
					Runnable apply = () -> {
						settle();
						pushUndo();
						art = boxW > 0 && boxH > 0 ? pa.resizedBox(boxW, boxH) : pa;
						toast("가져왔어요 (저장을 눌러야 적용)");
					};
					if (art != null && !art.isEmpty()) {
						confirm.showInfo(LunaIcons.INFO, "그림 가져오기", "지금 그리던 그림을 가져온 그림으로 바꿀까요?",
							"Ctrl+Z로 되돌릴 수 있어요", "가져오기", "취소", apply);
					} else {
						apply.run();
					}
				});
			}
			case 2 -> {
				com.google.gson.JsonObject all = kr.lunaslight.mod.util.BackgroundFiles.all();
				int n = kr.lunaslight.mod.util.BackgroundFiles.moduleCount(all);
				if (n == 0) {
					toast("직접 그린 배경이 아직 없어요");
					return;
				}
				toast("저장할 곳을 고르는 창을 여는 중…");
				kr.lunaslight.mod.util.BackgroundFiles.exportFile(all, "nova-bg-all", this::toast);
			}
			default -> {
				toast("가져올 파일을 고르는 창을 여는 중…");
				kr.lunaslight.mod.util.BackgroundFiles.importFile((o, msg) -> {
					if (o == null) {
						toast(msg);
						return;
					}
					int n = kr.lunaslight.mod.util.BackgroundFiles.moduleCount(o);
					if (n == 0) {
						toast("전체 배경 파일이 아니에요 (하나짜리는 [가져오기]로)");
						return;
					}
					confirm.show(LunaIcons.INFO, "전체 가져오기", n + "개 기능의 배경 그림과 슬롯을 덮어쓰시겠습니까?", "덮어쓰기", () -> {
						int done = kr.lunaslight.mod.util.BackgroundFiles.applyAll(o);
						String mine = module.hudPixelsRaw();
						PixelArt pa = mine == null || mine.isEmpty() ? null : PixelArt.decode(mine);
						if (pa != null) {
							art = boxW > 0 && boxH > 0 ? pa.resizedBox(boxW, boxH) : pa;
						}
						toast(done + "개 기능에 적용했어요");
					});
				});
			}
		}
	}

	/** 화면에 보일 그림: 떠 있는 선택 그림이 있으면 얹은 사본. */
	private PixelArt shown() {
		if (floating == null || art == null) {
			return art;
		}
		PixelArt c = art.copy();
		c.stamp(floating, selX, selY);
		return c;
	}

	private void checker(GuiGraphicsExtractor ctx, int x0, int y0, int cw, int chh) {
		int sq = cell * 2;
		for (int y = 0; y * sq < chh; y++) {
			for (int x = 0; x * sq < cw; x++) {
				int c = ((x + y) & 1) == 0 ? 0xFF2A2E34 : 0xFF23272C;
				ctx.fill(x0 + x * sq, y0 + y * sq, Math.min(x0 + cw, x0 + x * sq + sq), Math.min(y0 + chh, y0 + y * sq + sq), c);
			}
		}
	}

	private void drawCanvas(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int dim) {
		boolean known = boxW > 0 && boxH > 0 && art != null;
		PixelArt view = shown();
		scissor(ctx, areaX, areaY, areaX + areaW, areaY + areaH);
		if (known) {
			checker(ctx, artX(), artY(), art.w * cell, art.h * cell);
		}
		ctx.disableScissor();
		int bx = Module.pixelEditBox[0], by = Module.pixelEditBox[1];
		int originX = known ? canvasX - bx * cell : areaX;
		int originY = known ? canvasY - by * cell : areaY;
		int pw = boxW > 0 ? boxW + 24 : Math.max(60, areaW / cell);
		int ph = boxH > 0 ? boxH + 24 : Math.max(30, areaH / cell);
		Module.pixelEditing = module;
		Module.pixelEditingArt = view;
		Module.pixelEditBoxSeen = false;
		boolean xform = LunaCompat.guiTransformSupported(ctx);
		if (xform) {
			scissor(ctx, areaX, areaY, areaX + areaW, areaY + areaH);
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, originX, originY);
			LunaCompat.guiScale(ctx, cell, cell);
		}
		try {
			module.renderPreview(ctx, 0, 0, pw, ph);
		} catch (Throwable t) {
			LunaCompat.warnOnce("pixelEditor:" + module.getId(), t);
		} finally {
			if (xform) {
				LunaCompat.guiPop(ctx);
				ctx.disableScissor();
			}
			Module.pixelEditing = null;
			Module.pixelEditingArt = null;
		}
		if (Module.pixelEditBoxSeen) {
			int nw = Module.pixelEditBox[2], nh = Module.pixelEditBox[3];
			if (nw > 0 && nh > 0 && (nw != boxW || nh != boxH)) {
				boxW = nw;
				boxH = nh;
				layout();
				ensureArt();
			}
			framesWithoutBox = 0;
		} else if (!known && ++framesWithoutBox > 30) {
			unsupported = true;
		}
		if (!xform) {
			unsupported = true;
		}
		if (unsupported) {
			LunaDraw.textCentered(ctx, font, "이 기능은 배경 상자를 직접 그려서 픽셀 편집을 지원하지 않습니다", areaX + areaW / 2, areaY + areaH / 2 - 4, dim);
			return;
		}
		if (!known) {
			LunaDraw.textCentered(ctx, font, "HUD 크기를 재는 중...", areaX + areaW / 2, areaY + areaH / 2 - 4, dim);
			return;
		}
		int ax = artX(), ay = artY();
		int aw = art.w, ah = art.h;
		int cw = aw * cell, chh = ah * cell;
		scissor(ctx, areaX, areaY, areaX + areaW, areaY + areaH);
		if (hideContent) {
			// 글자와 아이콘 위를 바둑판 + 그림으로 다시 덮는다(그림 밖은 원래 그대로)
			checker(ctx, ax, ay, cw, chh);
			view.drawScaled(ctx, ax, ay, cell);
		}
		if (cell >= 4) {
			int grid = cell >= 6 ? 0x22FFFFFF : 0x12FFFFFF;
			for (int x = 0; x <= aw; x++) {
				ctx.fill(ax + x * cell, ay, ax + x * cell + 1, ay + chh, grid);
			}
			for (int y = 0; y <= ah; y++) {
				ctx.fill(ax, ay + y * cell, ax + cw, ay + y * cell + 1, grid);
			}
		}
		LunaDraw.roundRectOutline(ctx, ax - 1, ay - 1, cw + 2, chh + 2, 0, 0x60FFFFFF);
		if (art.pad > 0) {
			// HUD 상자 자리(여백 안쪽)
			LunaDraw.roundRectOutline(ctx, canvasX - 1, canvasY - 1, boxW * cell + 2, boxH * cell + 2, 0, 0xB0FFFFFF);
		}
		int in = art.inset();
		int gl = LunaDraw.withAlpha(LunaClientScreen.themeAccent(), 0x70);
		ctx.fill(ax + in * cell, ay, ax + in * cell + 1, ay + chh, gl);
		ctx.fill(ax + (aw - in) * cell, ay, ax + (aw - in) * cell + 1, ay + chh, gl);
		ctx.fill(ax, ay + in * cell, ax + cw, ay + in * cell + 1, gl);
		ctx.fill(ax, ay + (ah - in) * cell, ax + cw, ay + (ah - in) * cell + 1, gl);
		int[] p = pixelAt(mouseX, mouseY);
		if (p != null) {
			LunaDraw.roundRectOutline(ctx, ax + p[0] * cell, ay + p[1] * cell, cell + 1, cell + 1, 0, 0xC0FFFFFF);
		}
		if (selW > 0) {
			drawSelection(ctx, ax + selX * cell, ay + selY * cell, selW * cell, selH * cell);
		}
		ctx.disableScissor();
		String info = boxW + "×" + boxH + "px" + (art.pad > 0 ? " + 여백 " + art.pad + "px" : "") + "  |  배율 " + cell + "배(휠)  |  가운데 버튼 끌기: 이동";
		if (tool == TOOL_SELECT) {
			info += "  |  끌어서 선택, 선택 안을 끌면 옮김  |  Ctrl+C 복사, Ctrl+X 잘라내기, Ctrl+V 붙여넣기, Delete 지우기";
		} else {
			info += "  |  모서리 " + in + "px는 그대로, 글이 길어지면 가운데만 늘어남";
		}
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, info, areaW - 8), areaX + 4, areaY + areaH - 12, dim);
	}

	/** 선택 칸: 검정/흰 번갈아 움직이는 점선. */
	private void drawSelection(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		int phase = (int) ((System.currentTimeMillis() / 120) % 8);
		for (int i = 0; i < w; i++) {
			int c = ((i + phase) / 4) % 2 == 0 ? 0xFFFFFFFF : 0xFF000000;
			ctx.fill(x + i, y, x + i + 1, y + 1, c);
			ctx.fill(x + i, y + h - 1, x + i + 1, y + h, c);
		}
		for (int i = 0; i < h; i++) {
			int c = ((i + phase) / 4) % 2 == 0 ? 0xFFFFFFFF : 0xFF000000;
			ctx.fill(x, y + i, x + 1, y + i + 1, c);
			ctx.fill(x + w - 1, y + i, x + w, y + i + 1, c);
		}
		if (floating != null) {
			ctx.fill(x, y, x + w, y + h, LunaDraw.withAlpha(LunaClientScreen.themeAccent(), 0x22));
		}
	}

	/** 마우스 아래 그림 칸(여백 포함 그림 좌표). 그림 밖이면 null. */
	private int[] pixelAt(double mx, double my) {
		if (art == null || mx < artX() || my < artY() || mx >= artX() + art.w * cell || my >= artY() + art.h * cell
				|| !LunaDraw.in(mx, my, areaX, areaY, areaW, areaH)) {
			return null;
		}
		return new int[]{(int) ((mx - artX()) / cell), (int) ((my - artY()) / cell)};
	}

	/** 그림 밖이어도 그림 좌표로(옮기기용). */
	private int[] rawPixel(double mx, double my) {
		return new int[]{(int) Math.floor((mx - artX()) / cell), (int) Math.floor((my - artY()) / cell)};
	}

	private int undoBtnX() {
		return left + 4 + LunaDraw.width(font, "배경 그리기") + 10 + Math.min(220, LunaDraw.width(font, module.getDisplayName())) + 14;
	}

	private int padLabelW() {
		return LunaDraw.width(font, "여백 40px") + 10;
	}

	private int padCtrlW() {
		return 32 + padLabelW();
	}

	private int padCtrlX() {
		return lockBtnX() - 10 - padCtrlW();
	}

	private int lockBtnW() {
		return LunaDraw.width(font, "내용 가림") + 23;
	}

	private int lockBtnX() {
		return left + contentW - 18 - 8 - lockBtnW();
	}

	// ==================== 도구 막대 ====================

	private void segButton(GuiGraphicsExtractor ctx, int x, int y, int w, int h, String label, boolean sel, int mouseX, int mouseY, int txt, int acc) {
		boolean hov = LunaDraw.in(mouseX, mouseY, x, y, w, h);
		// 49-227차: 공용 입체 버튼(고른 것 = 테마색 채움)
		int kind = sel ? LunaDraw.B_PRIMARY : LunaDraw.B_NEUTRAL;
		LunaDraw.button3d(ctx, font, x, y, w, h, label, kind, hov ? 1f : 0f);
	}

	private void drawBar(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int txt, int sub, int dim, int acc) {
		LunaDraw.roundRect(ctx, barX, barY, barW, barH, 8, LunaClientScreen.ink(0x0C));
		int y0 = secToolsY;
		// --- 도구 / 효과 ---
		int x = secToolsX;
		LunaDraw.text(ctx, font, "도구", x, y0, sub);
		int bw = 52;
		for (int i = 0; i < TOOLS.length; i++) {
			segButton(ctx, x + i * (TOOL_BW + 4), y0 + 12, TOOL_BW, 18, TOOLS[i], tool == i, mouseX, mouseY, txt, acc);
			if (i == TOOL_SELECT && LunaDraw.in(mouseX, mouseY, x + i * (TOOL_BW + 4), y0 + 12, TOOL_BW, 18)) {
				tip = "끌어서 고른 칸을 옮기거나 지우거나 복사 (다른 기능의 배경에 붙여 넣기도 됨)";
			}
		}
		LunaDraw.text(ctx, font, "효과", x, y0 + 40, sub);
		for (int i = 0; i < PixelArt.FX_LABELS.length; i++) {
			int bx = x + i * (bw + 4), by = y0 + 52;
			boolean sel = effect == i;
			if (i == PixelArt.FX_RAINBOW && !sel) {
				// 무지개 버튼은 스스로 무지개
				long now = System.currentTimeMillis();
				for (int k = 0; k < bw; k++) {
					ctx.fill(bx + k, by, bx + k + 1, by + 18, LunaDraw.withAlpha(PixelArt.effectColor(0xFFFFFFFF, PixelArt.FX_RAINBOW, k, 0, now), 0x60));
				}
				LunaDraw.textCentered(ctx, font, PixelArt.FX_LABELS[i], bx + bw / 2, LunaDraw.textY(by, 18), txt);
			} else if (i == PixelArt.FX_GLOW && !sel) {
				LunaDraw.roundRect(ctx, bx, by, bw, 18, 4, PixelArt.effectColor(LunaDraw.withAlpha(acc, 0x60), PixelArt.FX_GLOW, 0, 0, System.currentTimeMillis()));
				LunaDraw.textCentered(ctx, font, PixelArt.FX_LABELS[i], bx + bw / 2, LunaDraw.textY(by, 18), txt);
			} else {
				segButton(ctx, bx, by, bw, 18, PixelArt.FX_LABELS[i], sel, mouseX, mouseY, txt, acc);
			}
			if (LunaDraw.in(mouseX, mouseY, bx, by, bw, 18)) {
				tip = switch (i) {
					case PixelArt.FX_RAINBOW -> "키스트로크처럼 색이 흐르는 무지개(색 설정과 무관, 투명도만)";
					case PixelArt.FX_GLOW -> "마크 인챈트처럼 그 색 위로 밝은 띠가 지나감";
					case PixelArt.FX_TWINKLE -> "픽셀마다 다른 박자로 반짝임";
					default -> "평범한 색";
				};
			}
		}
		// 49-497차: 선택 칸 버튼
		boolean hasSel = selW > 0;
		for (int i = 0; i < SEL_BTNS.length; i++) {
			boolean on = i == 2 ? clipboard != null : hasSel;
			miniBtn(ctx, x + i * (SEL_BW + 4), y0 + 76, SEL_BW, SEL_BTNS[i], on, mouseX, mouseY);
		}
		// --- 색: 채도·밝기 네모 + 색상 막대 + 색코드 ---
		x = secColorX;
		y0 = secColorY;
		LunaDraw.text(ctx, font, "색", x, y0, sub);
		int sqX = x, sqY = y0 + 12;
		for (int i = 0; i < SQ; i++) {
			float s = i / (float) (SQ - 1);
			int topC = PixelArt.hsv(palH, s, 1f);
			ctx.fillGradient(sqX + i, sqY, sqX + i + 1, sqY + SQ, LunaDraw.applyAlpha(topC), LunaDraw.applyAlpha(0xFF000000));
		}
		LunaDraw.roundRectOutline(ctx, sqX - 1, sqY - 1, SQ + 2, SQ + 2, 0, dragChannel == 10 ? acc : LunaClientScreen.ink(0x40));
		int mx = sqX + Math.round(palS * (SQ - 1)), my = sqY + Math.round((1 - palV) * (SQ - 1));
		LunaDraw.roundRectOutline(ctx, mx - 2, my - 2, 5, 5, 0, 0xFF000000);
		LunaDraw.roundRectOutline(ctx, mx - 1, my - 1, 3, 3, 0, 0xFFFFFFFF);
		int hueX = sqX + SQ + 6;
		for (int k = 0; k < 6; k++) {
			int ya = sqY + SQ * k / 6, yb = sqY + SQ * (k + 1) / 6;
			ctx.fillGradient(hueX, ya, hueX + BAR_W, yb, LunaDraw.applyAlpha(PixelArt.hsv(k / 6f, 1, 1)), LunaDraw.applyAlpha(PixelArt.hsv((k + 1) / 6f, 1, 1)));
		}
		LunaDraw.roundRectOutline(ctx, hueX - 1, sqY - 1, BAR_W + 2, SQ + 2, 0, dragChannel == 11 ? acc : LunaClientScreen.ink(0x40));
		int hy = sqY + Math.round(palH * (SQ - 1));
		ctx.fill(hueX - 2, hy - 1, hueX + BAR_W + 2, hy + 2, 0xFF000000);
		ctx.fill(hueX - 1, hy, hueX + BAR_W + 1, hy + 1, 0xFFFFFFFF);
		// 색코드 + 지금 색
		int fx = hueX + BAR_W + 10, fw = 64;
		LunaDraw.roundRect(ctx, fx, sqY, 22, 22, 4, currentColor() | (effect == PixelArt.FX_RAINBOW ? 0 : 0));
		if (effect != PixelArt.FX_NONE) {
			long now = System.currentTimeMillis();
			for (int k = 0; k < 22; k++) {
				ctx.fill(fx + k, sqY + 16, fx + k + 1, sqY + 22, PixelArt.effectColor(currentColor() | 0xFF000000, effect, k, 0, now));
			}
		}
		LunaDraw.roundRectOutline(ctx, fx, sqY, 22, 22, 4, LunaClientScreen.ink(0x40));
		LunaDraw.text(ctx, font, "#", fx, sqY + 30, sub);
		LunaDraw.roundRectBorderedFlat(ctx, fx + 10, sqY + 26, fw, 18, 4, LunaClientScreen.ink(0x12), hexFocus ? LunaDraw.withAlpha(acc, 0x8C) : LunaClientScreen.ink(0x26));
		String shown = hexFocus ? hexBuf : String.format(Locale.ROOT, "%06X", rgb());
		LunaDraw.text(ctx, font, shown, fx + 15, LunaDraw.textY(sqY + 26, 18), txt);
		if (hexFocus && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = fx + 15 + LunaDraw.width(font, shown) + 1;
			ctx.fill(cx, sqY + 30, cx + 1, sqY + 40, acc);
		}
		// --- 자주 쓰는 색 ---
		x = secPalX;
		y0 = secPalY;
		LunaDraw.text(ctx, font, "자주 쓰는 색", x, y0, sub);
		int sw = 14, gap = 3, cols = 6;
		for (int i = 0; i < PALETTE.length; i++) {
			int bx = x + (i % cols) * (sw + gap), by = y0 + 12 + (i / cols) * (sw + gap);
			LunaDraw.roundRect(ctx, bx, by, sw, sw, 3, 0xFF000000 | PALETTE[i]);
			if (PALETTE[i] == rgb()) {
				LunaDraw.roundRectOutline(ctx, bx - 1, by - 1, sw + 2, sw + 2, 4, 0xFFFFFFFF);
			}
		}
		// --- 투명도 게이지 0~100 ---
		x = secAlphaX;
		y0 = secAlphaY;
		LunaDraw.text(ctx, font, "투명도  §f" + alphaPct + "%", x, y0, sub);
		int tx = x, ty = y0 + 16, tw = 130, th = 12;
		// 바둑판 위에 투명 → 불투명
		for (int k = 0; k < tw; k += 4) {
			int c = ((k / 4) & 1) == 0 ? 0xFF3A3E44 : 0xFF2A2E34;
			ctx.fill(tx + k, ty, Math.min(tx + tw, tx + k + 4), ty + th, c);
			c = ((k / 4) & 1) == 0 ? 0xFF2A2E34 : 0xFF3A3E44;
		}
		for (int k = 0; k < tw; k++) {
			int a = Math.round(255f * k / (tw - 1));
			ctx.fill(tx + k, ty, tx + k + 1, ty + th, (a << 24) | rgb());
		}
		LunaDraw.roundRectOutline(ctx, tx - 1, ty - 1, tw + 2, th + 2, 0, dragChannel == 12 ? acc : LunaClientScreen.ink(0x40));
		int kx = tx + Math.round(alphaPct / 100f * (tw - 1));
		ctx.fill(kx - 2, ty - 3, kx + 3, ty + th + 3, 0xFF000000);
		ctx.fill(kx - 1, ty - 2, kx + 2, ty + th + 2, 0xFFFFFFFF);
		LunaDraw.text(ctx, font, "0", tx, ty + th + 4, dim);
		LunaDraw.text(ctx, font, "100", tx + tw - LunaDraw.width(font, "100"), ty + th + 4, dim);
		// --- 실제 크기 ---
		x = secPrevX;
		y0 = secPrevY;
		int prevW = secBtnX - 14 - x;
		if (prevW >= 60) {
			LunaDraw.text(ctx, font, "실제 크기", x, y0, sub);
			int ph = ROW_H - 14;
			LunaDraw.roundRect(ctx, x, y0 + 12, prevW, ph, 4, LunaClientScreen.ink(0x0C));
			if (art != null) {
				int pd = art.pad;
				int dw = Math.max(1, Math.min(prevW - 8 - 2 * pd, boxW)), dh = Math.max(1, Math.min(ph - 8 - 2 * pd, boxH));
				scissor(ctx, x + 4, y0 + 16, x + prevW - 4, y0 + 12 + ph - 4);
				shown().drawBox(ctx, x + (prevW - dw) / 2, y0 + 12 + (ph - dh) / 2, dw, dh, 1f);
				ctx.disableScissor();
			}
		}
		// --- 버튼 ---
		x = secBtnX;
		y0 = secBtnY;
		int b2 = (150 - 6) / 2;
		drawButton(ctx, x, y0 + 12, b2, "저장", true, mouseX, mouseY);
		drawButton(ctx, x + b2 + 6, y0 + 12, b2, "취소", false, mouseX, mouseY);
		drawButton(ctx, x, y0 + 38, b2, "기본 상자", false, mouseX, mouseY);
		drawButton(ctx, x + b2 + 6, y0 + 38, b2, "비우기", false, mouseX, mouseY);
		LunaDraw.text(ctx, font, "우클릭 지우기 | Ctrl+Z, Ctrl+Y", x, y0 + 66, dim);
	}

	private static final String[] SEL_BTNS = {"복사", "잘라내기", "붙여넣기", "지우기"};
	private static final int SEL_BW = 56;

	private void drawButton(GuiGraphicsExtractor ctx, int x, int y, int w, String label, boolean primary, int mouseX, int mouseY) {
		boolean hov = LunaDraw.in(mouseX, mouseY, x, y, w, 20);
		int acc = LunaClientScreen.themeAccent();
		// 49-227차: 공용 입체 버튼
		LunaDraw.button3d(ctx, font, x, y, w, 20, label, primary ? LunaDraw.B_PRIMARY : LunaDraw.B_NEUTRAL, hov ? 1f : 0f);
	}

	// ==================== 입력 ====================

	private void pushUndo() {
		if (art == null) {
			return;
		}
		undo.push(art.copy());
		while (undo.size() > 60) {
			undo.removeLast();
		}
		redo.clear();
	}

	private void doUndo() {
		commitFloating();   // 띄운 순간 되돌릴 자리를 이미 적어 뒀다 - 내려놓은 모습은 [앞으로]로 다시 갈 수 있게
		clearSel();
		if (!undo.isEmpty() && art != null) {
			redo.push(art.copy());
			art = undo.pop();
			toast("되돌렸어요");
		}
	}

	private void doRedo() {
		settle();
		if (!redo.isEmpty() && art != null) {
			undo.push(art.copy());
			art = redo.pop();
			toast("앞으로 갔어요");
		}
	}

	// ---- 49-497차: 선택 ----

	private void clearSel() {
		selW = selH = 0;
		selecting = moving = false;
	}

	/** 떠 있는 그림을 내려놓는다. */
	private void commitFloating() {
		if (floating != null && art != null) {
			art.stamp(floating, selX, selY);
		}
		floating = null;
	}

	/** 내려놓고 선택도 없앤다(저장, 불러오기, 도구 바꾸기 전에). */
	private void settle() {
		commitFloating();
		clearSel();
	}

	private void lift() {
		if (floating != null || art == null || selW <= 0) {
			return;
		}
		pushUndo();
		floating = art.region(selX, selY, selW, selH);
		art.fillRect(selX, selY, selW, selH, 0, 0);
	}

	private void copySel() {
		if (selW <= 0 || art == null) {
			return;
		}
		clipboard = floating != null ? floating.copy() : art.region(selX, selY, selW, selH);
		clipboard.pad = 0;
		toast("복사했어요 (다른 기능의 배경 그리기에서도 붙여 넣을 수 있어요)");
	}

	private void deleteSel() {
		if (art == null || selW <= 0) {
			return;
		}
		if (floating != null) {
			floating = null;
		} else {
			pushUndo();
			art.fillRect(selX, selY, selW, selH, 0, 0);
		}
		clearSel();
	}

	private void pasteClip() {
		if (art == null) {
			return;
		}
		if (clipboard == null) {
			toast("복사한 그림이 없어요");
			return;
		}
		commitFloating();
		pushUndo();
		floating = clipboard.copy();
		floating.pad = 0;
		int[] p = pixelAt(lastMouseX, lastMouseY);
		int x = p != null ? p[0] : art.pad;
		int y = p != null ? p[1] : art.pad;
		selX = Math.max(-floating.w + 1, Math.min(art.w - 1, x));
		selY = Math.max(-floating.h + 1, Math.min(art.h - 1, y));
		selW = floating.w;
		selH = floating.h;
		setTool(TOOL_SELECT);
		toast("붙여 넣었어요 - 끌어서 옮기고, 다른 데를 누르면 내려놔요");
	}

	private void setTool(int t) {
		if (t != TOOL_SELECT && tool == TOOL_SELECT) {
			settle();
		}
		tool = t;
	}

	private void changePad(int delta) {
		if (art == null) {
			return;
		}
		int np = Math.max(0, Math.min(PixelArt.MAX_PAD, art.pad + delta));
		if (np == art.pad) {
			return;
		}
		settle();
		pushUndo();
		art = art.withPad(np);
		layout();
	}

	private void paint(int px, int py, boolean erase) {
		if (art == null || (px == lastPx && py == lastPy)) {
			return;
		}
		lastPx = px;
		lastPy = py;
		int t = erase ? 1 : tool;
		switch (t) {
			case 0 -> art.set(px, py, currentColor(), effect);
			case 1 -> art.set(px, py, 0, 0);
			case 2 -> art.flood(px, py, currentColor(), effect);
			case 3 -> {
				int c = art.get(px, py);
				if ((c >>> 24) != 0) {
					setRgb(c & 0x00FFFFFF);
					alphaPct = Math.round(((c >>> 24) & 0xFF) / 2.55f);
					effect = art.effect(px, py);
					tool = 0;
				}
			}
			default -> {
			}
		}
	}

	/** 색 조작부 드래그(네모/색상 막대/투명도). 처리했으면 true. */
	private boolean colorDrag(double mouseX, double mouseY) {
		int sqX = secColorX, sqY = secColorY + 12;
		int hueX = sqX + SQ + 6;
		if (dragChannel == 10) {
			palS = (float) Math.max(0, Math.min(1, (mouseX - sqX) / (SQ - 1)));
			palV = 1f - (float) Math.max(0, Math.min(1, (mouseY - sqY) / (SQ - 1)));
			return true;
		}
		if (dragChannel == 11) {
			palH = (float) Math.max(0, Math.min(1, (mouseY - sqY) / (SQ - 1)));
			return true;
		}
		if (dragChannel == 12) {
			int tx = secAlphaX, tw = 130;
			alphaPct = (int) Math.round(Math.max(0, Math.min(100, (mouseX - tx) / (tw - 1) * 100)));
			return true;
		}
		return false;
	}

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		mouseX /= uiS;
		mouseY /= uiS;
		if (confirm.isOpen()) {
			return confirm.mouseClicked(mouseX, mouseY, button);
		}
		layout();
		hexFocus = false;
		dragChannel = 0;
		if (button == 0 && clickFileBar(mouseX, mouseY)) {
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, left + contentW - 18, top, 16, 16)) {
			onClose();
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, lockBtnX(), top, lockBtnW(), 16)) {
			hideContent = !hideContent;
			return true;
		}
		// 49-497차: 되돌리기 / 앞으로 / 여백
		int ux = undoBtnX();
		if (LunaDraw.in(mouseX, mouseY, ux, top, 56, 16)) {
			doUndo();
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, ux + 60, top, 56, 16)) {
			doRedo();
			return true;
		}
		int px0 = padCtrlX();
		int step = LunaCompat.isKeyPressed(net.minecraft.client.Minecraft.getInstance(), InputConstants.KEY_LSHIFT)
			|| LunaCompat.isKeyPressed(net.minecraft.client.Minecraft.getInstance(), InputConstants.KEY_RSHIFT) ? 8 : 2;
		if (LunaDraw.in(mouseX, mouseY, px0, top, 16, 16)) {
			changePad(-step);
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, px0 + 16 + padLabelW(), top, 16, 16)) {
			changePad(step);
			return true;
		}
		if (button == 2 && LunaDraw.in(mouseX, mouseY, areaX, areaY, areaW, areaH)) {
			panning = true;
			return true;
		}
		int[] p = pixelAt(mouseX, mouseY);
		// 49-497차: 선택 도구 - 선택 안을 누르면 띄워서 옮기기, 밖을 누르면 내려놓고 새로 고르기
		if (tool == TOOL_SELECT && button == 0 && art != null && LunaDraw.in(mouseX, mouseY, areaX, areaY, areaW, areaH)) {
			int[] r = rawPixel(mouseX, mouseY);
			if (selW > 0 && r[0] >= selX && r[1] >= selY && r[0] < selX + selW && r[1] < selY + selH) {
				lift();
				moving = true;
				moveSx = r[0];
				moveSy = r[1];
				moveOx = selX;
				moveOy = selY;
				return true;
			}
			commitFloating();
			clearSel();
			if (p != null) {
				selecting = true;
				selDragged = false;
				selAx = p[0];
				selAy = p[1];
				selX = p[0];
				selY = p[1];
				selW = 1;
				selH = 1;
			}
			return true;
		}
		if (p != null && (button == 0 || button == 1)) {
			pushUndo();
			painting = true;
			lastPx = lastPy = -1;
			paint(p[0], p[1], button == 1);
			return true;
		}
		int y0 = secToolsY;
		// 도구 / 효과
		int x = secToolsX, bw = 52;
		for (int i = 0; i < TOOLS.length; i++) {
			if (LunaDraw.in(mouseX, mouseY, x + i * (TOOL_BW + 4), y0 + 12, TOOL_BW, 18)) {
				setTool(i);
				return true;
			}
		}
		for (int i = 0; i < SEL_BTNS.length; i++) {
			if (LunaDraw.in(mouseX, mouseY, x + i * (SEL_BW + 4), y0 + 76, SEL_BW, 16)) {
				switch (i) {
					case 0 -> copySel();
					case 1 -> {
						copySel();
						deleteSel();
					}
					case 2 -> pasteClip();
					default -> deleteSel();
				}
				return true;
			}
		}
		for (int i = 0; i < PixelArt.FX_LABELS.length; i++) {
			if (LunaDraw.in(mouseX, mouseY, x + i * (bw + 4), y0 + 52, bw, 18)) {
				effect = i;
				if (tool == 1) {
					setTool(0);
				}
				return true;
			}
		}
		// 색
		int sqX = secColorX, sqY = secColorY + 12, hueX = sqX + SQ + 6;
		if (LunaDraw.in(mouseX, mouseY, sqX - 1, sqY - 1, SQ + 2, SQ + 2)) {
			dragChannel = 10;
			colorDrag(mouseX, mouseY);
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, hueX - 2, sqY - 1, BAR_W + 4, SQ + 2)) {
			dragChannel = 11;
			colorDrag(mouseX, mouseY);
			return true;
		}
		int fx = hueX + BAR_W + 10;
		if (LunaDraw.in(mouseX, mouseY, fx + 10, sqY + 26, 64, 18)) {
			hexFocus = true;
			hexBuf = "";
			return true;
		}
		// 자주 쓰는 색
		x = secPalX;
		y0 = secPalY;
		int sw = 14, gap = 3, cols = 6;
		for (int i = 0; i < PALETTE.length; i++) {
			int bx = x + (i % cols) * (sw + gap), by = y0 + 12 + (i / cols) * (sw + gap);
			if (LunaDraw.in(mouseX, mouseY, bx, by, sw, sw)) {
				setRgb(PALETTE[i]);
				if (tool == 1) {
					setTool(0);
				}
				return true;
			}
		}
		// 투명도
		if (LunaDraw.in(mouseX, mouseY, secAlphaX - 3, secAlphaY + 12, 136, 22)) {
			dragChannel = 12;
			colorDrag(mouseX, mouseY);
			return true;
		}
		// 버튼
		x = secBtnX;
		y0 = secBtnY;
		int b2 = (150 - 6) / 2;
		if (LunaDraw.in(mouseX, mouseY, x, y0 + 12, b2, 20)) {
			settle();
			if (art != null) {
				module.setHudPixelArt(art.isEmpty() ? null : art);
				kr.lunaslight.mod.config.LunaClientConfig.save();
			}
			onClose();
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, x + b2 + 6, y0 + 12, b2, 20)) {
			onClose();
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, x, y0 + 38, b2, 20) && art != null) {
			settle();
			pushUndo();
			art = PixelArt.roundedBox(boxW, boxH, module.hudBgColorForEditor(), 4).withPad(art.pad);
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, x + b2 + 6, y0 + 38, b2, 20) && art != null) {
			settle();
			pushUndo();
			art = new PixelArt(boxW, boxH).withPad(art.pad);
			return true;
		}
		return true;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double amount) {
		mouseX /= uiS;
		mouseY /= uiS;
		if (!LunaDraw.in(mouseX, mouseY, areaX, areaY, areaW, areaH) || boxW <= 0) {
			return false;
		}
		int old = cell;
		int next = Math.max(2, Math.min(14, cell + (amount > 0 ? 1 : -1)));
		if (next == old) {
			return true;
		}
		double fx = (mouseX - canvasX) / old, fy = (mouseY - canvasY) / old;
		zoom = next;
		int ccx = areaX + areaW / 2, ccy = areaY + areaH / 2;
		panX = (int) Math.round(fx * next - (mouseX - ccx) - boxW * next / 2.0);
		panY = (int) Math.round(fy * next - (mouseY - ccy) - boxH * next / 2.0);
		layout();
		return true;
	}

	private boolean lunaMouseDragged0(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		mouseX /= uiS;
		mouseY /= uiS;
		deltaX /= uiS;
		deltaY /= uiS;
		if (dragChannel != 0) {
			return colorDrag(mouseX, mouseY);
		}
		if (panning) {
			panX -= (int) Math.round(deltaX);
			panY -= (int) Math.round(deltaY);
			return true;
		}
		if (moving && art != null) {
			int[] r = rawPixel(mouseX, mouseY);
			selX = Math.max(-selW + 1, Math.min(art.w - 1, moveOx + r[0] - moveSx));
			selY = Math.max(-selH + 1, Math.min(art.h - 1, moveOy + r[1] - moveSy));
			return true;
		}
		if (selecting && art != null) {
			int[] r = rawPixel(mouseX, mouseY);
			int rx = Math.max(0, Math.min(art.w - 1, r[0])), ry = Math.max(0, Math.min(art.h - 1, r[1]));
			if (rx != selAx || ry != selAy) {
				selDragged = true;
			}
			selX = Math.min(selAx, rx);
			selY = Math.min(selAy, ry);
			selW = Math.abs(rx - selAx) + 1;
			selH = Math.abs(ry - selAy) + 1;
			return true;
		}
		if (!painting) {
			return false;
		}
		int[] p = pixelAt(mouseX, mouseY);
		if (p != null && tool != 2 && tool != 3) {
			paint(p[0], p[1], button == 1);
		}
		return true;
	}

	private boolean lunaMouseReleased0(double mouseX, double mouseY, int button) {
		if (selecting && !selDragged) {
			clearSel();   // 그냥 누르기만 했다 = 선택 풀기
		}
		selecting = false;
		moving = false;
		painting = false;
		panning = false;
		dragChannel = 0;
		lastPx = lastPy = -1;
		return false;
	}

	private boolean lunaCharTyped0(char chr) {
		if (!hexFocus) {
			return false;
		}
		if (hexBuf.length() < 6 && Character.digit(chr, 16) >= 0) {
			hexBuf += Character.toUpperCase(chr);
			if (hexBuf.length() == 6) {
				setRgb(Integer.parseInt(hexBuf, 16));
				hexFocus = false;
			}
		}
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (confirm.isOpen()) {
			return confirm.keyPressed(keyCode);
		}
		if (hexFocus) {
			if (keyCode == InputConstants.KEY_BACKSPACE && !hexBuf.isEmpty()) {
				hexBuf = hexBuf.substring(0, hexBuf.length() - 1);
			} else if (keyCode == InputConstants.KEY_ESCAPE || keyCode == InputConstants.KEY_RETURN) {
				hexFocus = false;
			}
			return true;
		}
		boolean ctrl = (modifiers & InputConstants.MOD_CONTROL) != 0;
		boolean shift = (modifiers & InputConstants.MOD_SHIFT) != 0;
		if (ctrl && keyCode == InputConstants.KEY_Z) {
			if (shift) {
				doRedo();
			} else {
				doUndo();
			}
			return true;
		}
		if (ctrl && keyCode == InputConstants.KEY_Y) {
			doRedo();
			return true;
		}
		if (ctrl && keyCode == InputConstants.KEY_C) {
			copySel();
			return true;
		}
		if (ctrl && keyCode == InputConstants.KEY_X) {
			copySel();
			deleteSel();
			return true;
		}
		if (ctrl && keyCode == InputConstants.KEY_V) {
			pasteClip();
			return true;
		}
		if (ctrl && keyCode == InputConstants.KEY_A && art != null) {
			settle();
			setTool(TOOL_SELECT);
			selX = 0;
			selY = 0;
			selW = art.w;
			selH = art.h;
			return true;
		}
		if ((keyCode == InputConstants.KEY_DELETE || keyCode == InputConstants.KEY_BACKSPACE) && selW > 0) {
			deleteSel();
			return true;
		}
		if (keyCode == InputConstants.KEY_RETURN && selW > 0) {
			settle();
			return true;
		}
		if (keyCode == InputConstants.KEY_ESCAPE) {
			if (selW > 0) {
				settle();   // 선택부터 내려놓는다
				return true;
			}
			onClose();
			return true;
		}
		if (keyCode >= InputConstants.KEY_1 && keyCode <= InputConstants.KEY_5) {
			setTool(keyCode - InputConstants.KEY_1);
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		Module.pixelEditing = null;
		Module.pixelEditingArt = null;
		LunaCompat.setScreen(parent);
	}
}
