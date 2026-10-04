package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import kr.lunaslight.mod.util.LunaVersion;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.List;

/**
 * 49-7차: 페더식 메인(타이틀) 화면. 바닐라 TitleScreen이 뜨면 LunaCompat.maybeSwapTitleScreen이
 * 이 화면으로 교체. 배경은 바닐라 파노라마(Screen.renderPanoramaBackground, 1.20.5+)를
 * 리플렉션으로 호출하고, 없으면 어두운 그라데이션 폴백. 목업(title.png) 그대로 이식.
 *
 * 버튼: 싱글플레이어 / 멀티플레이어 / Luna 설정(초록 강조) / 마인크래프트 설정 / 게임 종료.
 * 싱글·멀티는 (Screen) 생성자 리플렉션(1.20~1.21.11 동일 시그니처 - 매핑으로 확인),
 * 실패하면 바닐라 타이틀로 폴백해 그쪽 버튼을 쓰게 함.
 *
 * 49-25차: 버튼을 작게(150×24) 가운데로 모으고 굵은 글씨(LunaDraw.textBold - Pretendard ExtraBold), 그라데이션
 * 유리 버튼(LunaDraw.roundRectBordered). 왼쪽 위 = [내 얼굴 + 닉네임] 프로필 서랍, 오른쪽 위 = [소셜] 친구 서랍
 * (둘 다 LunaSocialScreen, 옆에서 미끄러져 나오는 패널).
 */
public class LunaTitleScreen extends LunaScreenBase {
	// 49-38차(사용자: "루나 클라이언트 메인 화면처럼 - 싱글/멀티 버튼 길이·생김새 벤치마킹"): 150×24 아이콘 버튼 →
	// 210×23 가로로 긴 반투명 버튼, 글자만 가운데(아이콘 없음). 배경은 사용자 로비 스크린샷을 흐리게 렌더한
	// 정지 이미지(textures/gui/title_bg.png, 1600×900)를 화면에 꽉 차게(cover) 깔고 살짝 어둡게.
	// 49-76차(6-15, 사용자: "메인화면 크기 조절 설정은 다 필요 없음 삭제"): 49-69차의 크기 단계와
	// 49-75차의 여백·좌우 바꾸기를 전부 뺐다. 메인 화면은 다시 **고정 배치**다.
	// btnW()/chipH()/chipX() 같은 접근자는 남겨 뒀다 - 호출 자리가 수십 군데라 상수를 돌려주게 두는 편이 안전하다.
	private static final int BTN_W_BASE = 210;
	private static final int BTN_H_BASE = 23;
	private static final int BTN_GAP = 6;
	private static final int BTN_R = 3;

	private static final int BG_DIM = 0x47070A0C;      // 배경 위 어둡게(가독성) - 이미지 자체에 비네팅·감광이 들어 있어 옅게

	// 49-10차: 모노크롬 팔레트(그린 폐기 - 찐검정/어두운 회색/화이트). HTML 목업 실측값 그대로.
	// 49-40차: 반투명 버튼 바탕은 테마(런처 블랙 & 화이트/아쿠아…)를 따라가는 LunaDraw.BTN_BG를 쓴다 - 상수 아님
	private static final int BTN_BORDER = 0x1AFFFFFF;
	private static final int BTN_BORDER_HOVER = 0x4DFFFFFF;
	private static final int ICON = 0xFF878D95;
	private static final int TXT = 0xFFD9DDE2;
	private static final int TXT_HOVER = 0xFFFFFFFF;
	// 49-40차: 주 버튼 바탕/테두리(PRI_*)는 테마 색에서 계산(drawButton) - 고정 연두 제거
	private static final int PRI_FG = 0xFFF2F4F6;
	private static final int WORD_SUB = 0xFF8E959D;      // 워드마크 "CLIENT"
	private static final int DGR = 0xFFCF7B74;
	private static final int DGR_HOVER = 0xFFFF8B82;
	private static final int DGR_BG_HOVER = 0xE82A1413;
	private static final int DGR_BORDER_HOVER = 0x59D9655B;

	private static final int KIND_NORMAL = 0;
	private static final int KIND_PRIMARY = 1;
	private static final int KIND_DANGER = 2;

	/** 원래 바닐라 타이틀 - 화면 열기 리플렉션 실패 시 폴백. */
	private final Screen vanillaTitle;

	private record Entry(String icon, String label, int kind, Runnable action) {
	}

	private final List<Entry> entries = new ArrayList<>();

	public LunaTitleScreen(Screen vanillaTitle) {
		super(kr.lunaslight.mod.util.LunaCompat.textLiteral("Nova Client"));
		this.vanillaTitle = vanillaTitle;
		LunaDraw.resetAnim("title");
	}

	@Override
	protected void init() {
		entries.clear();
		entries.add(new Entry(LunaIcons.USER, "싱글플레이어", KIND_NORMAL,
			() -> openVanilla("net.minecraft.client.gui.screens.worldselection.SelectWorldScreen")));
		entries.add(new Entry(LunaIcons.USERS, "멀티플레이어", KIND_NORMAL,
			() -> openVanilla("net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen")));
		// 49-32차(사용자 요청): Luna 설정·마인크래프트 설정은 오른쪽 위 아이콘으로, 게임 종료는
		// 왼쪽 아래 아이콘으로 옮겨서 가운데는 실제로 "들어가는" 버튼 두 개만 남긴다.
	}

	private void openVanilla(String className) {
		if (!LunaCompat.openVanillaScreenByName(className, this)) {
			fallbackToVanillaTitle();
		}
	}

	private void openVanillaOptions() {
		try {
			kr.lunaslight.mod.util.LunaCompat.openVanillaOptions(this); // 49-45차: 1.15.2는 클래스 위치가 달라 이름으로 찾음
		} catch (Throwable t) {
			LunaCompat.warnOnce("title:options", t);
			fallbackToVanillaTitle();
		}
	}

	private void fallbackToVanillaTitle() {
		if (vanillaTitle != null && this.minecraft != null) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(vanillaTitle);
		}
	}

	/**
	 * 버튼 블록 위 y. 49-11차: 예전 height*0.40 방식은 전체화면 자동 GUI 배율 4(논리 높이 270px)에서
	 * 종료 버튼과 하단 문구가 화면 아래로 잘렸음 → 브랜드+버튼 전체를 세로 중앙에 놓되 어떤
	 * 배율에서도 화면 안에 들어오게 클램프.
	 */
	private int menuTop() {
		int menuH = entries.size() * btnH() + Math.max(0, entries.size() - 1) * BTN_GAP;  // 49-32차: 버튼 2개
		int logoBlock = 34 + 22;                    // 로고 + 브랜드 간격
		int ideal = (this.height - (logoBlock + menuH)) / 2 - 4 + logoBlock;
		int max = this.height - menuH - 18;         // 하단 버전 문구(13px) 여백 확보
		return Math.max(Math.min(ideal, max), Math.min(8 + logoBlock, max));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();

		// 배경: 49-38차 흐린 로비 이미지(cover) → 실패 시 바닐라 파노라마(1.20.5+) → 그라데이션
		if (!renderBackgroundImage(ctx)) {
			boolean pano = LunaCompat.renderPanorama(this, ctx, delta);
			if (!pano) {
				ctx.fillGradient(0, 0, width, height, 0xFF10141B, 0xFF0A0D12);
			} else {
				ctx.fill(0, 0, width, height, 0x66070A0C); // 파노라마 위 어둡게(가독성)
			}
		}

		float open = LunaDraw.animFrom("title", 0f, 1f, 14f);
		LunaDraw.setAlpha(open);

		int cx = width / 2;

		// 브랜드(로고 + 워드마크, 큰 사이즈)
		int logoSize = 34;
		int wordH = 26;
		int wordW = LunaGfx.wordmarkWidth(wordH);
		int groupW = logoSize + 11 + wordW;
		int lx = cx - groupW / 2;
		int ly = menuTop() - logoSize - 22 + Math.round((1f - open) * 8f);
		boolean logoDrawn = LunaGfx.drawTex(ctx, LunaGfx.id("textures/gui/logo.png"),
			lx, ly, logoSize, logoSize, 0, 0, 128, 128, 128, LunaDraw.applyAlpha(0xFFFFFFFF));
		if (!logoDrawn) {
			LunaIcons.drawLarge(ctx, font, LunaIcons.LOGO, lx + 8, LunaDraw.iconLgY(ly, logoSize), LunaDraw.ACCENT);
		}
		// 49-21차: 워드마크 이미지(N★VA CLIENT) - 텍스처 실패 시 글자 폴백
		if (LunaGfx.drawWordmark(ctx, lx + logoSize + 11, ly + (logoSize - wordH) / 2, wordH,
				LunaDraw.applyAlpha(0xFFFFFFFF)) < 0) {
			int wy = LunaDraw.textY(ly, logoSize);
			LunaDraw.text(ctx, font, "NOVA", lx + logoSize + 11, wy, 0xFFFFFFFF);
			LunaDraw.text(ctx, font, "CLIENT",
				lx + logoSize + 11 + LunaDraw.width(font, "NOVA "), wy, WORD_SUB);
		}

		// 버튼
		int y = menuTop() + Math.round((1f - open) * 8f);
		int bx = cx - btnW() / 2;
		for (Entry e : entries) {
			if (e.kind() == KIND_DANGER) {
				int sy = y + 4;
				ctx.fill(bx + 2, sy, bx + btnW() - 2, sy + 1, LunaDraw.applyAlpha(0x1AFFFFFF));
				y += 11;
			}
			boolean hovered = LunaDraw.in(mouseX, mouseY, bx, y, btnW(), btnH());
			float hov = LunaDraw.anim("tb:" + e.label(), hovered ? 1f : 0f, 18f);
			drawButton(ctx, e, bx, y, hov);
			y += btnH() + BTN_GAP;
		}

		// 49-25차: 왼쪽 위 [내 얼굴 · 닉네임] 프로필 서랍, 오른쪽 위 [소셜] 친구 서랍
		renderProfileChip(ctx, mouseX, mouseY);
		renderSocialChip(ctx, mouseX, mouseY);
		renderTopIcons(ctx, mouseX, mouseY);

		// 49-32차: 왼쪽 아래는 [게임 종료] 아이콘.
		// 49-64차(5-15 "가운데 아래쪽에 클라이언트 버전 표시"): 오른쪽 아래에 있던 줄을 가운데로 옮겼다.
		// 그 줄은 "Luna · 1.21.11"이라 이름은 Luna인데 값은 마인크래프트 버전이라 읽는 사람을 헷갈리게 했다
		// - 이제 둘을 나눠서 클라이언트 버전을 앞에 둔다(묻는 건 대개 이쪽이다).
		LunaDraw.setAlpha(1f);
		renderQuitButton(ctx, mouseX, mouseY);
		String ver = "Nova Client " + LunaVersion.client() + "   |   Minecraft " + LunaVersion.current();
		LunaDraw.textCentered(ctx, font, ver, cx, height - 13, 0x80FFFFFF);
	}

	/**
	 * 49-38차: 배경 이미지를 화면 비율에 맞춰 "cover". 49-39차: 그리기는 LunaGfx.drawLobbyBackground로 옮겨
	 * 설정·키 지정 등 타이틀에서 여는 화면들도 같은 배경을 쓴다. 텍스처를 못 그리면 false → 파노라마/그라데이션 폴백.
	 */
	private boolean renderBackgroundImage(GuiGraphicsExtractor ctx) {
		// 49-41차(사용자: "아주 조금만 일렁이게 - 움직이는 것처럼"): 아주 느린 사인 곡선으로 살짝 확대(3%±1.2%)하고
		// 잘려 나가는 여분 안에서 좌우·상하로 천천히 흐른다(한 바퀴 30~50초). 배경 자체는 그대로 한 장.
		double t = System.nanoTime() / 1_000_000_000.0;
		float zoom = 1.03f + 0.012f * (float) Math.sin(t * 0.17);
		float panX = 0.5f + 0.38f * (float) Math.sin(t * 0.13);
		float panY = 0.5f + 0.30f * (float) Math.sin(t * 0.19 + 1.7);
		if (!LunaGfx.drawLobbyBackground(ctx, width, height, zoom, panX, panY)) {
			return false;
		}
		ctx.fill(0, 0, width, height, BG_DIM);
		return true;
	}

	private static int chipX() {
		return 8;   // 49-32차: 소셜·프로필 칩을 키움
	}

	private static int chipY() {
		return 8;
	}

	private static final int TOP_GAP = 6;

	private static int btnW() {
		return BTN_W_BASE;
	}

	private static int btnH() {
		return BTN_H_BASE;
	}

	/** 칩 높이(왼쪽 위 프로필 · 오른쪽 위 소셜). */
	private static int chipH() {
		return 26;
	}

	/** 오른쪽 위 아이콘 버튼(Luna 설정 · 마인크래프트 설정) 크기. */
	private static int topIcon() {
		return 26;
	}

	/** 왼쪽 아래 종료 버튼. */
	private static int quitSize() {
		return 26;
	}

	/** 프로필 칩 안의 얼굴 크기. */
	private static int face() {
		return 14;
	}

	private String profileName() {
		return LunaCompat.sessionName(minecraft);
	}

	private int profileChipWidth() {
		return 5 + face() + 7 + LunaDraw.width(font, LunaDraw.ellipsize(font, profileName(), 90)) + 10;
	}

	private String socialLabel() {
		return "소셜";
	}

	private int socialChipWidth() {
		return 12 + 12 + 7 + LunaDraw.width(font, socialLabel()) + 12;
	}

	/** 프로필 칩의 x. 뒤집으면 오른쪽 끝으로 간다(49-74차, 5-1). */
	private int profileChipX() {
		return chipX();
	}

	private int socialChipX() {
		return width - chipX() - socialChipWidth();
	}

	/** 소셜 칩 옆에 아이콘 두 개가 놓이는 쪽의 시작 x(뒤집으면 칩 오른쪽). */
	private int iconSideX(boolean outer) {
		return outer ? socialChipX() - TOP_GAP - topIcon() * 2 - TOP_GAP : socialChipX() - TOP_GAP - topIcon();
	}

	/** 왼쪽 위: 스킨 얼굴 + 닉네임 → 프로필 서랍(계정 전환). */
	private void renderProfileChip(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		int w = profileChipWidth();
		int cx0 = profileChipX();
		boolean hovered = LunaDraw.in(mouseX, mouseY, cx0, chipY(), w, chipH());
		float hov = LunaDraw.anim("tb:profile", hovered ? 1f : 0f, 18f);
		LunaDraw.button3d(ctx, cx0, chipY(), w, chipH(), 4, LunaDraw.B_NEUTRAL, hov);   // 49-227차: 입체 버튼
		int fx = cx0 + 5;
		int fy = chipY() + (chipH() - face()) / 2 - 1;   // 49-41차: "머리 위치가 약간 낮아" → 1px 위로
		Identifier skin = LunaCompat.playerSkinTexture(minecraft);
		if (!LunaGfx.drawPlayerFace(ctx, skin, fx, fy, face(), LunaDraw.applyAlpha(0xFFFFFFFF))) {
			LunaIcons.draw(ctx, font, LunaIcons.USER, fx + 2, LunaDraw.iconY(chipY(), chipH()), LunaDraw.ACCENT);
		}
		int ty = LunaDraw.textY(chipY(), chipH());
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, profileName(), 90), fx + face() + 7, ty,
			LunaDraw.buttonText(LunaDraw.B_NEUTRAL, hov));
	}

	/**
	 * 49-130차(사용자: "소셜에 따로 확인할 게 없는데 초록 동그라미가 떠 있어"): 예전엔 런처 계정에 로그인만 돼
	 * 있어도 초록 점을 찍어 "새 소식"처럼 보였다. 이제 받은 친구 요청이 있을 때만 점을 찍는다(30초마다 확인).
	 */
	private static volatile int pendingRequests;
	private static long pendingCheckedAt;

	private static void refreshPendingRequests() {
		long now = System.currentTimeMillis();
		if (now - pendingCheckedAt < 30_000L || !kr.lunaslight.mod.util.LunaSocial.signedIn()) {
			return;
		}
		pendingCheckedAt = now;
		try {
			kr.lunaslight.mod.util.LunaSocial.fetchRequests().thenAccept(list -> pendingRequests = list == null ? 0 : list.size());
		} catch (Throwable ignored) {
		}
	}

	/** 오른쪽 위: [소셜] → 친구 서랍. 받은 친구 요청이 있으면 초록 점. */
	private void renderSocialChip(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		int w = socialChipWidth();
		int x = socialChipX();
		boolean hovered = LunaDraw.in(mouseX, mouseY, x, chipY(), w, chipH());
		float hov = LunaDraw.anim("tb:social", hovered ? 1f : 0f, 18f);
		LunaDraw.button3d(ctx, x, chipY(), w, chipH(), 4, LunaDraw.B_NEUTRAL, hov);   // 49-227차: 입체 버튼
		int ty = LunaDraw.textY(chipY(), chipH());
		LunaIcons.draw(ctx, font, LunaIcons.USERS, x + 12, LunaDraw.iconBesideText(ty),
			LunaDraw.lerpColor(LunaDraw.ACCENT, 0xFFF2F4F6, hov * 0.3f));
		LunaDraw.text(ctx, font, socialLabel(), x + 12 + 12 + 7, ty, LunaDraw.buttonText(LunaDraw.B_NEUTRAL, hov));
		refreshPendingRequests();
		if (kr.lunaslight.mod.util.LunaSocial.signedIn() && pendingRequests > 0) {
			LunaDraw.circle(ctx, x + w - 4, chipY() - 1, 5, 0xFF5AD86E);
		}
	}

	/** 오른쪽 위: 소셜 칩 왼쪽에 [Luna 설정] [마인크래프트 설정] 아이콘 버튼. */
	private int lunaSettingsX() {
		return iconSideX(true);
	}

	private int mcSettingsX() {
		return iconSideX(false);
	}

	private void renderTopIcons(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		drawIconButton(ctx, lunaSettingsX(), chipY(), LunaIcons.SLIDERS, "Nova 설정",
			mouseX, mouseY, "tb:luna", true);
		drawIconButton(ctx, mcSettingsX(), chipY(), LunaIcons.SETTINGS, "마인크래프트 설정",
			mouseX, mouseY, "tb:mc", false);
	}

	/** 종료 버튼의 x. 프로필 칩과 같은 쪽 아래 구석에 둔다. */
	private int quitX() {
		return chipX();
	}

	private void renderQuitButton(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		drawIconButton(ctx, quitX(), height - chipY() - quitSize(), LunaIcons.POWER, "게임 종료",
			mouseX, mouseY, "tb:quit", false);
	}

	/** 정사각형 아이콘 버튼 하나(마우스를 올리면 이름이 옆에 뜬다). */
	private void drawIconButton(GuiGraphicsExtractor ctx, int x, int y, String icon, String tip,
			int mouseX, int mouseY, String animKey, boolean accent) {
		int size = topIcon();
		boolean hovered = LunaDraw.in(mouseX, mouseY, x, y, size, size);
		float hov = LunaDraw.anim(animKey, hovered ? 1f : 0f, 18f);
		LunaDraw.button3d(ctx, x, y, size, size, 4, LunaDraw.B_NEUTRAL, hov);   // 49-227차: 입체 버튼
		int col = accent ? LunaDraw.ACCENT : LunaDraw.buttonText(LunaDraw.B_NEUTRAL, hov);
		// 49-79차: 상자 한가운데에 중간(16px) 아이콘. 예전엔 폭을 상수(SIZE/SIZE_LG)로 어림해 x를 잡았는데,
		// 실제 글리프 폭은 폰트가 정하므로 실측 폭으로 가운데를 잡는 drawInBox로 통일했다.
		LunaIcons.drawInBox(ctx, font, icon, x, y, size, col);
		if (hov > 0.5f) {
			// 49-256차: 테마 색 이름표(LunaDraw.tipBox)
			int tw = LunaDraw.tipWidth(font, tip);
			boolean below = y < height / 2;
			int ty = below ? y + size + 5 : y - 18;
			int tx = Math.max(4, Math.min(width - tw - 4, x + size / 2 - tw / 2));
			LunaDraw.tipBox(ctx, font, tip, tx, ty);
		}
	}

	private void drawButton(GuiGraphicsExtractor ctx, Entry e, int bx, int y, float hov) {
		// 49-227차(사진 시안): 입체 버튼 - 주 버튼은 테마색으로 채우고, 나가기(위험)는 회색 몸통 + 빨간 글자
		int kind = e.kind() == KIND_PRIMARY ? LunaDraw.B_PRIMARY : LunaDraw.B_NEUTRAL;
		int fg = e.kind() == KIND_DANGER ? LunaDraw.lerpColor(DGR, DGR_HOVER, hov) : LunaDraw.buttonText(kind, hov);
		LunaDraw.button3d(ctx, bx, y, btnW(), btnH(), BTN_R, kind, hov);
		// 49-78차(사용자: "막대기 너무 많다"): 왼쪽 강조 바 삭제(주 버튼 왼쪽 연두 바).
		// 49-38차: 루나식 - 아이콘 없이 글자만 정중앙
		int ty = LunaDraw.textY(y, btnH());
		int lw = LunaDraw.widthBold(font, e.label());
		LunaDraw.textBold(ctx, font, e.label(), bx + (btnW() - lw) / 2, ty, fg);
	}

	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		if (LunaDraw.in(mouseX, mouseY, profileChipX(), chipY(), profileChipWidth(), chipH())) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaSocialScreen(this, LunaSocialScreen.MODE_PROFILE));
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, socialChipX(), chipY(), socialChipWidth(), chipH())) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaSocialScreen(this, LunaSocialScreen.MODE_FRIENDS));
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, lunaSettingsX(), chipY(), topIcon(), topIcon())) {
			kr.lunaslight.mod.util.LunaCompat.setScreen(new LunaClientScreen(this));
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, mcSettingsX(), chipY(), topIcon(), topIcon())) {
			openVanillaOptions();
			return true;
		}
		if (LunaDraw.in(mouseX, mouseY, quitX(), height - chipY() - quitSize(), quitSize(), quitSize())) {
			this.minecraft.stop();
			return true;
		}
		int bx = width / 2 - btnW() / 2;
		int y = menuTop();
		for (Entry e : entries) {
			if (e.kind() == KIND_DANGER) {
				y += 11;
			}
			if (LunaDraw.in(mouseX, mouseY, bx, y, btnW(), btnH())) {
				e.action().run();
				return true;
			}
			y += btnH() + BTN_GAP;
		}
		return true;
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		return false; // 타이틀에선 ESC로 닫지 않음
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void onClose() {
		// 타이틀 화면은 닫아도 갈 곳이 없음 - 무시
	}
}
