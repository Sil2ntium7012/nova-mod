package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * 49차~: ESC 일시정지 메뉴를 페더 클라이언트처럼 교체. 바닐라 GameMenuScreen이 열리는 순간
 * LunaCompat.maybeSwapPauseMenu(틱 훅)가 이 화면으로 바꿔치기함.
 *
 * 49-11차: 패널 박스 제거(어두워진 게임 화면 위 플로팅 버튼) + 하단 문구 제거 + 세로 오버플로 수정.
 * 49-14차: 사용자 피드백 - 부가 기능(HUD 편집기/키 설정/진단)은 **작은 아이콘 버튼 한 줄**로 내리고
 *   큰 버튼은 자주 쓰는 것만. 모서리 반경도 10 → 6("끝만 조금 둥글게, 너무 깎이면 안 됨").
 * 49-39차: 큰 버튼을 타이틀의 루나식 버튼(210×23 반투명, 글자만 가운데)과 완전히 같게.
 * "게임 나가기"는 LunaCompat.quitToTitle로 바닐라와 동일하게 처리(레벨 해제→저장→타이틀).
 */
public class LunaPauseScreen extends LunaScreenBase {
	// 버튼 치수 - 타이틀 화면과 동일(일관된 룩). 49-39차(사용자: "UI도 메인 화면 느낌 쪽으로 조금만"): 타이틀의
	// 루나식 버튼(210×23, 반경 3, 반투명, 글자만 가운데)을 그대로 - 팔레트도 타이틀 값과 같게.
	private static final int BTN_W = 210;
	private static final int BTN_H = 23;
	private static final int BTN_GAP = 6;
	private static final int BTN_R = 3;
	private static final int LOGO = 24;
	private static final int BRAND_GAP = 20;
	private static final int SEP_GAP = 11;    // 나가기 앞 구분선 여백

	// 우상단 빠른 버튼(통계/랜 서버/프로필) - 49-17차
	// 49-20차: "통계나 이런 기타 버튼들이 너무 작아" - 22 → 30(아이콘도 큰 글리프로).
	// 49-79차(사용자: "아이콘이 왼쪽으로 밀려있고 너무 커"): 버튼 30→26, 아이콘은 큰 것(22) 대신 중간(16).
	// 타이틀 화면 위쪽 아이콘 버튼과 같은 규격이 됐다.
	private static final int TOP_BTN = 26;
	private static final int TOP_GAP = 7;
	private static final int TOP_MARGIN = 12;

	// 49-21차: 부가 기능 아이콘 줄(HUD 편집기/키/진단)은 우상단 빠른 버튼 줄에 합침(사용자: "오른쪽 위에 같이 정렬")
	private static final int WORDMARK_H = 20;

	// 팔레트 - 카본 블랙 주류 + 화이트 깔짝 + 연두 낌새(49-12차). 49-39차: 타이틀(LunaTitleScreen)과 같은 값.
	// 49-40차: 반투명 버튼 바탕은 테마(런처 블랙 & 화이트/아쿠아…)를 따라가는 LunaDraw.BTN_BG를 쓴다 - 상수 아님
	private static final int BTN_BORDER = 0x1AFFFFFF;
	private static final int BTN_BORDER_HOVER = 0x4DFFFFFF;
	private static final int ICON = 0xFF878D95;
	private static final int TXT = 0xFFD9DDE2;
	private static final int TXT_HOVER = 0xFFFFFFFF;
	// 49-40차: 주 버튼 바탕/테두리(PRI_*)는 테마 색에서 계산(drawButton) - 고정 연두 제거
	private static final int PRI_TXT = 0xFFF2F4F6;
	private static final int WORD_SUB = 0xFF8E959D;
	private static final int DGR = 0xFFCF7B74;
	private static final int DGR_HOVER = 0xFFFF8B82;
	private static final int DGR_BG_HOVER = 0xE82A1413;
	private static final int DGR_BORDER_HOVER = 0x59D9655B;

	private static final int KIND_NORMAL = 0;
	private static final int KIND_PRIMARY = 1;
	private static final int KIND_DANGER = 2;

	/** 원래 바닐라 일시정지 화면 - 나가기 리플렉션이 실패했을 때의 안전 폴백. */
	private final Screen vanillaMenu;

	private record Entry(String icon, String label, int kind, Runnable action) {
	}

	/** 아이콘만 있는 작은 버튼(부가 기능) - 이름은 마우스를 올렸을 때 아래에 표시. */
	private record IconEntry(String icon, String label, Runnable action) {
	}

	private final List<Entry> entries = new ArrayList<>();
	private final List<IconEntry> topEntries = new ArrayList<>();

	public LunaPauseScreen(Screen vanillaMenu) {
		super(kr.lunaslight.mod.util.LunaCompat.textLiteral("게임 메뉴"));
		this.vanillaMenu = vanillaMenu;
		LunaDraw.resetAnim("pause");
	}

	@Override
	protected void init() {
		entries.clear();
		entries.add(new Entry(LunaIcons.RIGHT, "게임으로 돌아가기", KIND_NORMAL, () -> kr.lunaslight.mod.util.LunaCompat.setScreen(null)));
		// 49-41차(사용자: "루나 설정만 색 다른 거 없애 줘"): 다른 버튼과 같은 모양
		entries.add(new Entry(LunaIcons.SLIDERS, "Nova 설정", KIND_NORMAL,
			() -> kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaClientScreen(this))));
		entries.add(new Entry(LunaIcons.SETTINGS, "마인크래프트 설정", KIND_NORMAL, this::openVanillaOptions));

		// 49-17차: 우상단 빠른 버튼 - 49-21차부터 부가 기능(HUD 편집기/키/진단)도 여기에 한 줄로.
		topEntries.clear();
		topEntries.add(new IconEntry(LunaIcons.MOVE, "HUD 편집기",
			() -> kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaHudEditorScreen(this, (Module) null))));
		topEntries.add(new IconEntry(LunaIcons.KEYBOARD, "키 지정",
			() -> kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaKeybindsScreen(this))));
		topEntries.add(new IconEntry(LunaIcons.ACTIVITY, "성능 진단",
			() -> kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaDiagnosticsScreen(this))));
		// 49-27차: 마인크래프트 기본 통계 대신 Luna 통계(전체 · 서버별 · 아이템별 · 백업)
		topEntries.add(new IconEntry(LunaIcons.CHART, "통계", () -> kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaStatsScreen(this))));
		// 49-67차(4-20): 찍은 스크린샷을 게임 안에서 바로 넘겨 본다(알트탭 없이)
		topEntries.add(new IconEntry(LunaIcons.IMAGE, "스크린샷",
			() -> kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaScreenshotScreen(this))));
		if (LunaCompat.isSinglePlayer(this.minecraft)) {
			topEntries.add(new IconEntry(LunaIcons.WIFI, "랜 서버 열기", () -> LunaCompat.openVanillaScreenByName(
				"net.minecraft.client.gui.screens.ShareToLanScreen", this)));
		}
		// 49-41차(사용자): 프로필 자리에 소셜, 프로필은 메인 화면처럼 왼쪽 위 얼굴 칩으로
		topEntries.add(new IconEntry(LunaIcons.USERS, "소셜",
			() -> kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaSocialScreen(this, LunaSocialScreen.MODE_FRIENDS))));
	}

	private int topRowX(int index) {
		int n = Math.max(1, topEntries.size());
		int rowW = n * TOP_BTN + (n - 1) * TOP_GAP;
		return width - TOP_MARGIN - rowW + index * (TOP_BTN + TOP_GAP);
	}

	private void openVanillaOptions() {
		try {
			kr.lunaslight.mod.util.LunaCompat.openVanillaOptions(this); // 49-45차: 1.15.2는 클래스 위치가 달라 이름으로 찾음
		} catch (Throwable t) {
			LunaCompat.warnOnce("pause:options", t);
			if (vanillaMenu != null) {
				kr.lunaslight.mod.util.LunaCompat.setScreen(vanillaMenu);
			}
		}
	}

	/**
	 * 월드/서버에서 나가기 - 바닐라 나가기 버튼과 완전히 동일한 시퀀스(LunaCompat.quitToTitle).
	 * 예전엔 레벨 연결 해제를 건너뛰어 "세계 저장 중" 정지(Watchdog 크래시)가 났음.
	 */
	private void quit() {
		try {
			LunaCompat.quitToTitle(this.minecraft);
		} catch (Throwable t) {
			LunaCompat.warnOnce("pause:quit", t);
			if (vanillaMenu != null && this.minecraft != null) {
				kr.lunaslight.mod.util.LunaCompat.setScreen(vanillaMenu);
			}
		}
	}

	// ---- 레이아웃(렌더/클릭이 같은 계산을 쓰도록 한 곳에 모음) ----

	/** 큰 버튼 3개 + 구분선 + 나가기 버튼의 총 높이(상수). */
	private int menuHeight() {
		return 3 * (BTN_H + BTN_GAP) + SEP_GAP + BTN_H;
	}

	/** 콘텐츠(브랜드+메뉴) 위쪽 y - 세로 중앙, 어떤 GUI 배율에서도 화면 안에 들어오게 클램프. */
	private int contentTop() {
		int blockH = LOGO + BRAND_GAP + menuHeight();
		int ideal = (this.height - blockH) / 2 - 2;
		int max = this.height - blockH - 6;
		return Math.max(4, Math.min(ideal, max));
	}

	private int buttonsTop() {
		return contentTop() + LOGO + BRAND_GAP;
	}

	private int quitY() {
		return buttonsTop() + 3 * (BTN_H + BTN_GAP) + SEP_GAP;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		float open = LunaDraw.animFrom("pause", 0f, 1f, 16f);
		LunaDraw.setAlpha(open);

		// 배경 패널 없이, 게임 화면만 어둡게(바닐라처럼 - 버튼이 바로 떠 있음)
		if (!kr.lunaslight.mod.util.LunaCompat.renderScreenBackground(this, ctx, mouseX, mouseY, delta)) {
			ctx.fill(0, 0, width, height, LunaDraw.applyAlpha(0x8C000000));
		}

		int cx = width / 2;
		int rise = Math.round((1f - open) * 8f);

		// 브랜드: 로고 + 워드마크 이미지(49-21차: "로고는 폰트를 안 따라가도 돼, 멋져야지" - N★VA CLIENT 이미지)
		int by = contentTop() + rise;
		int wordW = LunaGfx.wordmarkWidth(WORDMARK_H);
		int groupW = LOGO + 9 + wordW;
		int lx = cx - groupW / 2;
		boolean logoDrawn = LunaGfx.drawTex(ctx, LunaGfx.id("textures/gui/logo.png"),
			lx, by, LOGO, LOGO, 0, 0, 128, 128, 128, LunaDraw.applyAlpha(0xFFFFFFFF));
		if (!logoDrawn) {
			LunaIcons.drawLarge(ctx, font, LunaIcons.LOGO, lx + 3, LunaDraw.iconLgY(by, LOGO), LunaDraw.ACCENT);
		}
		if (LunaGfx.drawWordmark(ctx, lx + LOGO + 9, by + (LOGO - WORDMARK_H) / 2, WORDMARK_H,
				LunaDraw.applyAlpha(0xFFFFFFFF)) < 0) {
			int wy = LunaDraw.textY(by, LOGO);
			LunaDraw.text(ctx, font, "NOVA", lx + LOGO + 9, wy, 0xFFFFFFFF);
			LunaDraw.text(ctx, font, "CLIENT",
				lx + LOGO + 9 + LunaDraw.width(font, "NOVA "), wy, WORD_SUB);
		}

		// 큰 버튼 3개
		int bx = cx - BTN_W / 2;
		int y = buttonsTop() + rise;
		for (Entry e : entries) {
			boolean hovered = LunaDraw.in(mouseX, mouseY, bx, y, BTN_W, BTN_H);
			float hov = LunaDraw.anim("pb:" + e.label(), hovered ? 1f : 0f, 18f);
			drawButton(ctx, e, bx, y, hov);
			y += BTN_H + BTN_GAP;
		}

		// 49-41차: 왼쪽 위 [내 얼굴 · 닉네임] 프로필 칩(타이틀과 동일) → 프로필 서랍
		renderProfileChip(ctx, mouseX, mouseY);

		// 우상단 빠른 버튼(통계/랜 서버/소셜)
		String topHover = null;
		for (int i = 0; i < topEntries.size(); i++) {
			IconEntry te = topEntries.get(i);
			int tx = topRowX(i);
			int ty2 = TOP_MARGIN;
			boolean hovered = LunaDraw.in(mouseX, mouseY, tx, ty2, TOP_BTN, TOP_BTN);
			float hov = LunaDraw.anim("pt:" + te.label(), hovered ? 1f : 0f, 18f);
			LunaDraw.button3d(ctx, tx, ty2, TOP_BTN, TOP_BTN, 4, LunaDraw.B_NEUTRAL, hov);   // 49-227차: 입체 버튼
			LunaIcons.drawInBox(ctx, font, te.icon(), tx, ty2, TOP_BTN,
				LunaDraw.lerpColor(ICON, LunaDraw.ACCENT, hov));
			if (hovered) {
				topHover = te.label();
			}
		}
		if (topHover != null) {
			// 49-124차(사용자: "오른쪽 위 기능들 이름이 너무 연해서 안보임"): 어두운 칩 위에 밝은 글씨로.
			// 49-256차: 테마 색 이름표(LunaDraw.tipBox) - 크림 테마에서 어두운 칩 + 어두운 글자로 안 보였다
			int lw = LunaDraw.tipWidth(font, topHover);
			LunaDraw.tipBox(ctx, font, topHover, width - TOP_MARGIN - lw + 4, TOP_MARGIN + TOP_BTN + 3);
		}

		// 구분선 + 나가기
		int qy = quitY() + rise;
		int sy = qy - SEP_GAP + 5;
		ctx.fill(bx + 2, sy, bx + BTN_W - 2, sy + 1, LunaDraw.applyAlpha(0x1AFFFFFF));
		Entry quitEntry = new Entry(LunaIcons.POWER, "게임 나가기", KIND_DANGER, this::quit);
		boolean qHover = LunaDraw.in(mouseX, mouseY, bx, qy, BTN_W, BTN_H);
		drawButton(ctx, quitEntry, bx, qy, LunaDraw.anim("pb:quit", qHover ? 1f : 0f, 18f));

		LunaDraw.setAlpha(1f);
	}

	private void drawButton(GuiGraphicsExtractor ctx, Entry e, int bx, int y, float hov) {
		// 49-227차(사진 시안): 입체 버튼 - 주 버튼은 테마색으로 채우고, 나가기(위험)는 회색 몸통 + 빨간 글자
		int kind = e.kind() == KIND_PRIMARY ? LunaDraw.B_PRIMARY : LunaDraw.B_NEUTRAL;
		int fg = e.kind() == KIND_DANGER ? LunaDraw.lerpColor(DGR, DGR_HOVER, hov) : LunaDraw.buttonText(kind, hov);
		LunaDraw.button3d(ctx, bx, y, BTN_W, BTN_H, BTN_R, kind, hov);
		// 49-78차(사용자: "막대기 너무 많다"): 왼쪽 강조 바 삭제(주 버튼 왼쪽 연두 바).
		// 49-39차: 타이틀과 동일하게 아이콘 없이 굵은 글씨만 정중앙(루나식)
		int ty = LunaDraw.textY(y, BTN_H);
		int lw = LunaDraw.widthBold(font, e.label());
		LunaDraw.textBold(ctx, font, e.label(), bx + (BTN_W - lw) / 2, ty, fg);
	}

	// ---- 49-41차: 프로필 칩(타이틀 화면과 같은 치수) ----
	private static final int CHIP_Y = 8, CHIP_H = 26, CHIP_X = 8, FACE = 14;

	private String profileName() {
		return LunaCompat.sessionName(minecraft);
	}

	private int profileChipWidth() {
		return 5 + FACE + 7 + LunaDraw.width(font, LunaDraw.ellipsize(font, profileName(), 90)) + 10;
	}

	private void renderProfileChip(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		int w = profileChipWidth();
		boolean hovered = LunaDraw.in(mouseX, mouseY, CHIP_X, CHIP_Y, w, CHIP_H);
		float hov = LunaDraw.anim("pb:profile", hovered ? 1f : 0f, 18f);
		LunaDraw.button3d(ctx, CHIP_X, CHIP_Y, w, CHIP_H, 4, LunaDraw.B_NEUTRAL, hov);   // 49-227차: 입체 버튼
		int fx = CHIP_X + 5;
		int fy = CHIP_Y + (CHIP_H - FACE) / 2 - 1;   // 49-41차: "머리 위치가 약간 낮아" → 1px 위로
		net.minecraft.resources.Identifier skin = LunaCompat.playerSkinTexture(minecraft);
		if (!LunaGfx.drawPlayerFace(ctx, skin, fx, fy, FACE, LunaDraw.applyAlpha(0xFFFFFFFF))) {
			LunaIcons.draw(ctx, font, LunaIcons.USER, fx + 2, LunaDraw.iconY(CHIP_Y, CHIP_H), LunaDraw.ACCENT);
		}
		int ty = LunaDraw.textY(CHIP_Y, CHIP_H);
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, profileName(), 90), fx + FACE + 7, ty,
			LunaDraw.buttonText(LunaDraw.B_NEUTRAL, hov));
	}

	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		if (LunaDraw.in(mouseX, mouseY, CHIP_X, CHIP_Y, profileChipWidth(), CHIP_H)) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaSocialScreen(this, LunaSocialScreen.MODE_PROFILE));
			return true;
		}
		for (int i = 0; i < topEntries.size(); i++) {
			if (LunaDraw.in(mouseX, mouseY, topRowX(i), TOP_MARGIN, TOP_BTN, TOP_BTN)) {
				topEntries.get(i).action().run();
				return true;
			}
		}
		int bx = width / 2 - BTN_W / 2;
		int y = buttonsTop();
		for (Entry e : entries) {
			if (LunaDraw.in(mouseX, mouseY, bx, y, BTN_W, BTN_H)) {
				e.action().run();
				return true;
			}
			y += BTN_H + BTN_GAP;
		}
		if (LunaDraw.in(mouseX, mouseY, bx, quitY(), BTN_W, BTN_H)) {
			quit();
			return true;
		}
		return true; // 일시정지 화면에선 다른 클릭 흡수
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == InputConstants.KEY_ESCAPE) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(null);
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		if (this.minecraft != null) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(null);
		}
	}
}
