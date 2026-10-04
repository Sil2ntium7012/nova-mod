package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaStats;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 49-27차: Luna 통계 화면(사용자: "전체 / 서버(맵) 이런 식으로 나뉘는 것").
 *
 * 왼쪽 = 범위 목록(전체 + 서버·맵), 오른쪽 = 그 범위의 기록. 위 탭으로 요약 · 아이템 · 처치 · 죽음 · 이동을 고른다.
 * 아래 줄에 [이미지로 저장] [파일로 저장] [불러오기]. 불러오기는 같은 마인크래프트 계정(uuid)에서만 된다.
 */
public class LunaStatsScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 600, 340);

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
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

	private static final int MAX_W = 640;
	private static final int MAX_H = 380;
	private static final int MARGIN = 14;
	private static final int SIDEBAR_W = 150;
	private static final int TAB_H = 30;
	private static final int FOOT_H = 26;
	private static final int ROW_H = 18;

	// 49-142차(사용자가 고른 새 장르: 전투, 채굴/농사/낚시, 플레이 습관, 채팅/소셜/서버): 처치·죽음은 [전투]로,
	// 상호작용은 [생활]로 합치고 [습관] [소셜]을 새로. 각 탭 위에 대시보드 숫자 상자.
	private static final String[] TABS = {"요약", "전투", "생활", "습관", "소셜", "아이템", "이동", "기간"};
	private static final int TAB_SUMMARY = 0, TAB_COMBAT = 1, TAB_LIFE = 2, TAB_HABIT = 3, TAB_SOCIAL = 4,
			TAB_ITEMS = 5, TAB_TRAVEL = 6, TAB_PERIOD = 7;

	private final Screen parent;
	private int px, py, pw, ph;
	private int tab;
	private int scopeIndex;                 // 0 = 전체, 1.. = 서버 목록
	private float scroll, scrollTarget;
	private int maxScroll;
	private List<LunaStats.Scope> worlds = new ArrayList<>();
	private String notice;
	private long noticeUntil;
	private String itemFilter = "";
	/**
	 * 49-73차: 몇 <b>프레임 뒤에</b> 화면을 담을지. 예전엔 boolean이라 "그린 그 프레임에" 담았는데,
	 * <b>그러면 포스터가 안 찍힌다</b>.
	 *
	 * <p>1.21.8+는 GUI 그리기가 <b>프레임 끝에 한꺼번에</b> GPU로 넘어간다(DrawContext에 {@code draw()}가
	 * 아예 없어졌다 - javap 실측). 그리는 도중에 화면을 읽으라고 시키면 <b>아직 안 올라간 이번 프레임</b>
	 * 대신 <b>직전 프레임</b>이 찍힌다. 즉 포스터를 그린 첫 프레임에 담으면 포스터가 없는 그림이 나온다.
	 *
	 * <p>그래서 포스터를 <b>몇 프레임 띄워 둔 뒤</b> 담는다. 사람 눈에는 한순간이고, 어느 버전에서도
	 * 이미 다 올라간 화면을 담게 된다.
	 */
	private int captureIn;
	/** 49-67차(5-13): 이 프레임만 "전체 한 장" 포스터로 그린다(그 프레임을 그대로 이미지로 저장). */
	private boolean poster;
	/** 포스터에서 목록을 자를 개수(0이면 전부). 통계가 수백 줄이면 한 장에 안 들어간다. */
	private int posterLimit;
	/**
	 * 49-67차(5-11 "정렬"): 아이템 탭 정렬 기준. 0 = 합계(기본), 1~5 = 열 순서(사용·버림·제작·캔·획득).
	 * 머리글을 누르면 바뀐다.
	 */
	private int itemSort;
	/** 49-172차(사용자: "합계순 한 번 더 누르면 내림/오름차순"): false면 오름차순. 열을 바꾸면 내림차순으로 돌아온다. */
	private boolean itemSortDesc = true;
	/** 이번 프레임에 그린 아이템 머리글의 자리(클릭 판정을 그리기와 같은 값으로). */
	private int itemHeadY = Integer.MIN_VALUE;
	private int itemHeadX;
	private int[] itemHeadCols = new int[0];
	/** 49-53차(5-12): 불러오기 = 파일 고르기 + 확인. null이면 창이 닫힌 상태. */
	private List<Path> importList;
	private Path importPending;
	private String importPendingInfo;
	/** 49-28차: 표가 나타날 때 값이 차오르는 애니메이션(탭·범위를 바꿀 때마다 다시 시작). */
	private long bodyStartNanos = System.nanoTime();
	private int rowIndex;
	private final java.util.Set<String> collapsedYears = new java.util.HashSet<>();

	public LunaStatsScreen(Screen parent) {
		super(kr.lunaslight.mod.util.LunaCompat.textLiteral("통계"));
		this.parent = parent;
		LunaDraw.resetAnim("stats");
	}

	private void lunaInit0() {
		worlds = LunaStats.worlds();
	}

	private LunaStats.Scope scope() {
		if (scopeIndex <= 0 || scopeIndex > worlds.size()) {
			return LunaStats.global();
		}
		return worlds.get(scopeIndex - 1);
	}

	/** 탭·범위가 바뀌면 애니메이션을 다시 시작. */
	private void restartAnim() {
		bodyStartNanos = System.nanoTime();
		rowIndex = 0;
	}

	/** 0~1. 줄 순서(index)마다 조금씩 늦게 시작해 위에서부터 차례로 차오른다. */
	private float progress(int index) {
		float elapsed = (System.nanoTime() - bodyStartNanos) / 1_000_000_000f;
		float t = (elapsed - index * 0.035f) / 0.42f;
		if (t <= 0) {
			return 0f;
		}
		if (t >= 1) {
			return 1f;
		}
		float inv = 1f - t;
		return 1f - inv * inv * inv; // ease-out cubic
	}

	/** 다음 줄의 진행도(그리는 순서대로 자동 증가). */
	private float nextProgress() {
		if (poster) {
			return 1f;   // 포스터는 한 프레임만 그리고 바로 찍는다 - 차오르는 중간 값이 찍히면 안 된다
		}
		return progress(rowIndex++);
	}

	private static String num(long v) {
		return String.format(java.util.Locale.ROOT, "%,d", v);
	}

	/** 진행도만큼 차오른 숫자. */
	private static String animNum(long v, float p) {
		return num(Math.round(v * (double) p));
	}

	private void notice(String s) {
		notice = s;
		noticeUntil = System.currentTimeMillis() + 4000;
	}

	// ==================== 렌더 ====================

	private void lunaRender0(DrawContext ctx, int mouseX, int mouseY, float delta) {
		// 49-142차: 설정 화면과 같은 다크/라이트(포스터 이미지는 늘 어두운 판)
		if (!poster) {
			LunaClientScreen.applyScreenTheme();
		}
		try {
			renderStats(ctx, mouseX, mouseY, delta);
		} finally {
			LunaClientScreen.restoreScreenTheme();
		}
	}

	private void renderStats(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		// 49-192차: 원그래프가 마우스를 올린 조각을 알 수 있게(포스터는 마우스 없음)
		hoverMX = poster ? -99999 : mouseX;
		hoverMY = poster ? -99999 : mouseY;
		scroll += (scrollTarget - scroll) * Math.min(1f, LunaDraw.dt() * 18f);
		if (Math.abs(scrollTarget - scroll) < 0.3f) {
			scroll = scrollTarget;
		}
		if (poster) {
			renderPoster(ctx);
			LunaDraw.setAlpha(1f);
			if (captureIn > 0 && --captureIn == 0) {
				captureOnTick = true;   // 49-172차: 그리는 도중이 아니라 다음 틱(이 프레임이 다 올라간 뒤)에 찍는다
			}
			return;
		}
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.OVERLAY);
		float open = LunaDraw.animFrom("stats", 0f, 1f, 16f);
		LunaDraw.setAlpha(open);
		// 49-143차(사용자: "통계는 말한 대로 멋있게 안 나왔고"): 창 모양을 버리고 설정 화면과 같은 전체 화면 대시보드로.
		// 왼쪽 = 로고 + 범위(전체 / 서버 / 맵) + 저장 버튼, 위 = 큰 제목 + 탭, 본문 = 숫자 상자 + 그래프 카드.
		layout();
		ctx.fill(0, 0, width, height, LunaDraw.applyAlpha((LunaClientScreen.themeBg() & 0x00FFFFFF)
			| (LunaClientScreen.themeLight() ? 0xF5000000 : 0xEE000000)));
		renderRail(ctx, mouseX, mouseY);
		renderTop(ctx, mouseX, mouseY);
		renderBody(ctx, mouseX, mouseY);

		if (notice != null && System.currentTimeMillis() < noticeUntil) {
			int w = LunaDraw.width(textRenderer, notice) + 16;
			int ny = height - 30;
			LunaDraw.card3d(ctx, cx0 + (cw0 - w) / 2, ny, w, 16, 0f, true);   // 49-227차
			LunaDraw.textCentered(ctx, textRenderer, notice, cx0 + cw0 / 2, LunaDraw.textY(ny, 16), LunaDraw.ACCENT);
		}
		if (importList != null) {
			renderImport(ctx, mouseX, mouseY);
		}
		LunaDraw.setAlpha(1f);

		if (captureIn > 0 && --captureIn == 0) {
			captureOnTick = true;
		}
	}

	/**
	 * 49-172차(사용자: "통계 저장하면 마크 화면이 찍히고 통계는 안 보임"): 1.21.6+는 화면(GUI)을 프레임 맨 끝에 따로
	 * 그리므로, 화면을 그리는 도중에 프레임버퍼를 읽으면 GUI가 아직 없다. 틱은 프레임 그리기 전에 돌고 그때
	 * 프레임버퍼에는 직전 프레임(GUI 포함)이 그대로 있으므로 여기서 찍는다.
	 */
	private boolean captureOnTick;

	@Override
	public void tick() {
		super.tick();
		if (captureOnTick) {
			captureOnTick = false;
			saveImage();
		}
		tickShot();
	}

	// ==================== 49-230차: 저장이 끝날 때까지 지켜본다 ====================
	// 사용자: "통계 캡처가 안 돼(높은 버전, 1.21.11 이하도)". 화면 읽기(takeScreenshot)는 버전마다 비동기/동기가 갈리고
	// 그림이 안 오면 아무 말도 없었다. 이제 ① 화면 읽기로 받아 직접 쓰고, 2.5초 안에 파일이 안 생기면 ② 바닐라 스크린샷 저장에
	// 이름을 정해 맡긴 뒤 그 파일을 통계 폴더로 옮긴다. 포스터는 저장이 끝날 때까지 그대로 둔다(②도 같은 그림을 찍게).
	private Path shotFile;            // 지금 만드는 그림 파일(null이면 없음)
	private volatile boolean shotDone;
	private long shotStartedAt;
	private long fallbackAt;          // ② 시작 시각(0이면 아직)

	private void tickShot() {
		if (shotFile == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (shotDone) {
			finishShot();
			return;
		}
		if (fallbackAt == 0) {
			if (now - shotStartedAt > 2500) {
				fallbackAt = now;
				if (!LunaCompat.saveScreenshotNamed(client, shotFile.getFileName().toString())) {
					announce("§c이미지를 저장하지 못했습니다");
					finishShot();
				}
			}
			return;
		}
		Path vanilla = LunaCompat.gameDirPath(client).resolve("screenshots").resolve(shotFile.getFileName().toString());
		try {
			if (java.nio.file.Files.isRegularFile(vanilla) && java.nio.file.Files.size(vanilla) > 0
					&& now - java.nio.file.Files.getLastModifiedTime(vanilla).toMillis() > 300) {
				java.nio.file.Files.move(vanilla, shotFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
				announce("이미지로 저장했습니다 | " + shortPath(shotFile) + "  §7([폴더 열기]로 확인)");
				finishShot();
				return;
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:image:move", t);
		}
		if (now - fallbackAt > 6000) {
			announce("§c이미지를 저장하지 못했습니다");
			finishShot();
		}
	}

	private void finishShot() {
		shotFile = null;
		shotDone = false;
		fallbackAt = 0;
		if (poster) {
			poster = false;
			posterLimit = 0;
		}
	}

	/**
	 * 49-67차(5-13 "전체 통계를 한 장에, 왼쪽 위에 내 정보"): 한 프레임만 그리는 <b>포스터</b>.
	 *
	 * <p>탭을 하나씩 찍어 모으는 대신, 화면 전체를 두 칸으로 나눠 <b>요약 · 아이템 · 처치 · 상호작용</b>을
	 * 한 번에 올린다. 왼쪽 위에는 누구의 기록인지(닉네임 · 범위 · 뽑은 시각). 목록은 {@link #posterLimit}
	 * 개씩만 - 아이템이 300종이면 한 장에 들어갈 수가 없고, 어차피 상위 몇 개가 알고 싶은 전부다.
	 *
	 * <p>탭 전환도 스크롤도 없는 한 장이라 <b>애니메이션을 꺼야 한다</b>(차오르는 중간 숫자가 찍히면
	 * 틀린 값이 박힌 그림이 된다) - {@link #nextProgress()}가 포스터일 때 1을 돌려주는 이유다.
	 */
	private void renderPoster(DrawContext ctx) {
		ctx.fill(0, 0, width, height, 0xFF0B0D10);
		LunaStats.Scope s = scope();
		rowIndex = 0;

		// ---- 왼쪽 위: 내 정보 ----
		LunaIcons.draw(ctx, textRenderer, LunaIcons.CHART, 14, LunaDraw.iconY(12, 20), LunaDraw.ACCENT);
		String who = client.player != null ? client.player.getName().getString() : "Nova";
		LunaDraw.textBold(ctx, textRenderer, who + " 의 통계", 34, LunaDraw.textY(12, 20), LunaDraw.TEXT);
		String sub = s.displayLabel() + "  |  " + date(System.currentTimeMillis());
		LunaDraw.text(ctx, textRenderer, sub, 34, 26, LunaDraw.TEXT_DIM);
		// 오른쪽 위에 클라이언트 표식(어디서 뽑은 그림인지)
		String brand = "Nova Client " + kr.lunaslight.mod.util.LunaVersion.mod();
		LunaDraw.text(ctx, textRenderer, brand, width - 12 - LunaDraw.width(textRenderer, brand), 14,
			LunaDraw.TEXT_DIM);
		ctx.fill(14, 40, width - 14, 41, 0x22FFFFFF);

		// ---- 두 칸 ----
		int top = 50;
		int gap = 16;
		int colW = (width - 28 - gap) / 2;
		int lx = 14;
		int rx = 14 + colW + gap;
		int ly = top;
		int ry = top;
		ly += renderSummary(ctx, s, lx, ly, colW) + 6;
		if (!s.killTypes.isEmpty()) {
			renderCounts(ctx, s.killTypes, lx, ly, colW, "", true);
		}
		ry += renderItems(ctx, s, rx, ry, colW, -1, -1) + 6;
		renderInteractions(ctx, s, rx, ry, colW);
	}

	// ==================== 49-143차: 전체 화면 배치 ====================

	private static final int TOP_Y = 16;
	private static final int TITLE_H = 24;
	private static final int TABS_Y = TOP_Y + 32;
	private static final int SCOPE_H = 18;
	// 49-172차(사용자: "이미지 저장을 전체 요약 한 장 / 이 화면만으로 나누기")
	private static final String[] LINKS = {"전체 요약 저장", "이 화면 저장", "파일로 저장", "불러오기", "폴더 열기"};
	private int railX, railW, dividerX, cx0, cw0, rightEdge, contentTop;

	private void layout() {
		railX = Math.max(12, Math.round(width * 0.053f));
		railW = Math.max(96, Math.min(150, Math.round(width * 0.22f)));
		dividerX = railX + railW;
		cx0 = dividerX + 24;
		rightEdge = width - railX - 10;
		cw0 = rightEdge - cx0;
		contentTop = TABS_Y + 24;
		px = 0;
		py = 0;
		pw = width;
		ph = height;
	}

	// ---- 갈무리 글자(설정 화면과 같은 글꼴)

	private Text gObj(String s) {
		// 49-155차(사용자: "기능에 폰트 기본으로 다 되돌려"): 갈무리 고정을 풀고 글꼴 설정(기본 = 마크 글꼴)을 따른다.
		return LunaGfx.text(s);
	}

	private int gWidth(String s) {
		return LunaCompat.textWidth(textRenderer, gObj(s));
	}

	private void gText(DrawContext ctx, String s, int x, int boxY, int boxH, int color) {
		ctx.drawText(textRenderer, gObj(s), x, boxY + Math.round(boxH / 2f - LunaCompat.textVisualCenter()), LunaDraw.applyAlpha(color), false);
	}

	private String gFit(String s, int maxW) {
		if (s == null || gWidth(s) <= maxW) {
			return s == null ? "" : s;
		}
		String t = s;
		while (t.length() > 1 && gWidth(t + "…") > maxW) {
			t = t.substring(0, t.length() - 1);
		}
		return t + "…";
	}

	/** 갈무리 2배(큰 제목·큰 숫자). 2D 변환이 안 되는 옛 버전은 1배. */
	private void gBig(DrawContext ctx, String s, int x, int boxY, int boxH, int color) {
		if (!LunaCompat.guiTransformSupported(ctx)) {
			gText(ctx, s, x, boxY, boxH, color);
			return;
		}
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, x, boxY + Math.round(boxH / 2f - LunaCompat.textVisualCenter() * 2f));
			LunaCompat.guiScale(ctx, 2f, 2f);
			ctx.drawText(textRenderer, gObj(s), 0, 0, LunaDraw.applyAlpha(color), false);
		} finally {
			LunaCompat.guiPop(ctx);
		}
	}

	private int gBigWidth(String s, DrawContext ctx) {
		return gWidth(s) * (LunaCompat.guiTransformSupported(ctx) ? 2 : 1);
	}

	// ---- 왼쪽: 로고 + 범위 + 저장 링크

	private int scopeTop() {
		return TOP_Y + 40;
	}

	private int linksTop() {
		return height - 24 - LINKS.length * 14 - 10;
	}

	private void renderRail(DrawContext ctx, int mouseX, int mouseY) {
		int logo = 20;
		int wordH = Math.max(8, Math.min(14, Math.round((railW - 10 - logo - 6) * 112f / 492f)));
		int ly = TOP_Y + (TITLE_H - logo) / 2;
		if (!LunaGfx.drawTex(ctx, LunaGfx.id("textures/gui/logo.png"), railX, ly, logo, logo, 0, 0, 128, 128, 128,
				LunaDraw.applyAlpha(0xFFFFFFFF))) {
			LunaIcons.drawInBox(ctx, textRenderer, LunaIcons.LOGO, railX, ly, logo, LunaClientScreen.themeAccent());
		}
		if (LunaGfx.drawWordmark(ctx, railX + logo + 6, TOP_Y + (TITLE_H - wordH) / 2, wordH,
				LunaDraw.applyAlpha(LunaClientScreen.themeText())) < 0) {
			gText(ctx, "NOVA", railX + logo + 6, TOP_Y, TITLE_H, LunaClientScreen.themeText());
		}
		int y = scopeTop();
		int bottom = linksTop() - 8;
		for (int i = 0; i <= worlds.size(); i++) {
			if (y + SCOPE_H > bottom) {
				break;
			}
			LunaStats.Scope sc = i == 0 ? LunaStats.global() : worlds.get(i - 1);
			boolean sel = scopeIndex == i || (i == 0 && (scopeIndex <= 0 || scopeIndex > worlds.size()));
			boolean hov = LunaDraw.in(mouseX, mouseY, railX - 4, y, railW, SCOPE_H);
			String hours = LunaStats.formatHours(sc.playMs);
			int hw = LunaDraw.width(textRenderer, hours);
			int lx = railX + 8;
			int right = dividerX - 12;
			gText(ctx, gFit(i == 0 ? "전체" : sc.displayLabel(), right - hw - 6 - lx), lx, y, SCOPE_H,
				sc.missing ? LunaClientScreen.themeDim() : sel || hov ? LunaClientScreen.themeText() : LunaClientScreen.themeSub());
			LunaDraw.text(ctx, textRenderer, hours, right - hw, LunaDraw.textY(y, SCOPE_H),
				sel ? LunaClientScreen.themeAccent() : LunaClientScreen.themeDim());
			if (sel) {
				int dy = y + SCOPE_H / 2 - 1;
				ctx.fill(railX + 2, dy, railX + 5, dy + 3, LunaDraw.applyAlpha(LunaClientScreen.themeAccent()));
			}
			y += SCOPE_H;
			if (i == 0) {
				ctx.fill(railX + 8, y + 3, dividerX - 12, y + 4, LunaDraw.applyAlpha(LunaClientScreen.themeLine()));
				y += 8;
			}
		}
		// 저장 링크 + 닫기
		int ly2 = linksTop();
		ctx.fill(railX + 8, ly2 - 6, dividerX - 12, ly2 - 5, LunaDraw.applyAlpha(LunaClientScreen.themeLine()));
		for (String l : LINKS) {
			boolean hov = LunaDraw.in(mouseX, mouseY, railX + 6, ly2, railW - 16, 14);
			LunaDraw.text(ctx, textRenderer, l, railX + 8, LunaDraw.textY(ly2, 14),
				hov ? LunaClientScreen.themeAccent() : LunaClientScreen.themeSub());
			ly2 += 14;
		}
		int ey = height - 24;
		boolean eh = LunaDraw.in(mouseX, mouseY, railX + 6, ey - 2, gWidth("ESC 닫기") + 4, 13);
		gText(ctx, "ESC 닫기", railX + 8, ey - 2, 12, eh ? LunaClientScreen.themeText() : LunaClientScreen.themeDim());
		ctx.fill(dividerX, TOP_Y, dividerX + 1, height - 16, LunaDraw.applyAlpha(LunaClientScreen.themeLine()));
	}

	// ---- 위: 큰 제목 + 탭 + 닫기

	private int tabX(int i) {
		int x = cx0;
		for (int k = 0; k < i; k++) {
			x += gWidth(TABS[k]) + 14;
		}
		return x;
	}

	private void renderTop(DrawContext ctx, int mouseX, int mouseY) {
		LunaStats.Scope s = scope();
		String title = gFit(scopeIndex <= 0 ? "전체 통계" : s.displayLabel(), cw0 / 2 - 20);
		gBig(ctx, title, cx0, TOP_Y, TITLE_H, LunaClientScreen.themeText());
		int sx = cx0 + gBigWidth(title, ctx) + 10;
		String sub = LunaStats.formatHours(s.playMs) + " 플레이  |  " + num(s.sessions) + "번 접속";
		if (sx + gWidth(sub) < rightEdge - 24) {
			gText(ctx, sub, sx, TOP_Y + 12, 11, LunaClientScreen.themeSub());
		}
		boolean ch = LunaDraw.in(mouseX, mouseY, rightEdge - 16, TOP_Y + 4, 16, 16);
		LunaIcons.drawInBox(ctx, textRenderer, LunaIcons.CLOSE, rightEdge - 16, TOP_Y + 4, 16,
			ch ? LunaClientScreen.themeAccent() : LunaClientScreen.themeSub());
		// 탭(글자 + 고른 탭 밑 테마색 2px)
		ctx.fill(cx0, TABS_Y + 16, rightEdge, TABS_Y + 17, LunaDraw.applyAlpha(LunaClientScreen.themeLine()));
		for (int i = 0; i < TABS.length; i++) {
			int tx = tabX(i);
			int tw = gWidth(TABS[i]);
			boolean sel = tab == i;
			boolean hov = LunaDraw.in(mouseX, mouseY, tx - 4, TABS_Y - 2, tw + 8, 18);
			gText(ctx, TABS[i], tx, TABS_Y, 14, sel || hov ? LunaClientScreen.themeText() : LunaClientScreen.themeSub());
			if (sel) {
				ctx.fill(tx, TABS_Y + 15, tx + tw, TABS_Y + 17, LunaDraw.applyAlpha(LunaClientScreen.themeAccent()));
			}
		}
	}

	private int listTop() {
		return contentTop;
	}

	private int listBottom() {
		return height - 12;
	}

	private void renderBody(DrawContext ctx, int mouseX, int mouseY) {
		int x = cx0;
		int y = contentTop;
		int w = cw0;
		int h = listBottom() - y;
		LunaStats.Scope s = scope();
		lunaV.scissor(ctx, x - 2, y, x + w + 2, y + h);
		try {
			int cy = y + 2 - (int) scroll;
			int total;
			rowIndex = 0;
			switch (tab) {
				case TAB_COMBAT -> total = dCombat(ctx, s, x, cy, w);
				case TAB_LIFE -> total = dLife(ctx, s, x, cy, w);
				case TAB_HABIT -> total = dHabit(ctx, s, x, cy, w);
				case TAB_SOCIAL -> total = dSocial(ctx, s, x, cy, w);
				case TAB_ITEMS -> total = renderItems(ctx, s, x, cy, w, mouseX, mouseY);
				case TAB_TRAVEL -> total = dTravel(ctx, s, x, cy, w);
				case TAB_PERIOD -> total = dPeriod(ctx, s, x, cy, w, mouseX, mouseY);
				default -> total = dSummary(ctx, s, x, cy, w);
			}
			maxScroll = Math.max(0, total - (h - 8));
			scrollTarget = Math.max(0, Math.min(scrollTarget, maxScroll));
		} finally {
			ctx.disableScissor();
		}
		if (maxScroll > 0) {
			int thumbH = Math.max(20, h * h / (h + maxScroll));
			int thumbY = y + (int) ((h - thumbH) * (scroll / maxScroll));
			LunaDraw.roundRect(ctx, x + w + 5, thumbY, 2, thumbH, 1, ink(0x40));
		}
	}

	/** 한 줄짜리 항목(이름 왼쪽, 값 오른쪽, 사이에 가는 선). 나타날 때 살짝 밀려 들어온다. */
	private void statRow(DrawContext ctx, int x, int y, int w, String label, String value, boolean accent) {
		float p = nextProgress();
		if (p <= 0.01f) {
			return;
		}
		float base = LunaDraw.alpha();
		LunaDraw.setAlpha(base * p);
		x += Math.round((1f - p) * 5f);
		LunaDraw.text(ctx, textRenderer, label, x, y, LunaDraw.TEXT_SUB);
		int vw = LunaDraw.width(textRenderer, value);
		LunaDraw.text(ctx, textRenderer, value, x + w - vw, y, accent ? LunaDraw.ACCENT : LunaDraw.TEXT);
		LunaDraw.linkLine(ctx, x + LunaDraw.width(textRenderer, label) + 6, x + w - vw - 6, y + 4);
		LunaDraw.setAlpha(base);
	}

	/** 숫자가 0에서 차오르는 줄. */
	private void statNumRow(DrawContext ctx, int x, int y, int w, String label, long value, String suffix, boolean accent) {
		float p = progress(rowIndex);
		statRow(ctx, x, y, w, label, animNum(value, p) + suffix, accent);
	}

	/**
	 * 49-32차: "숫자 × [마크 하트]" 줄. 하트 반칸 = 1이라 2로 나눈 값이 하트 개수.
	 * 하트 그림을 못 그리면 글자로 대체.
	 */
	private void statHeartRow(DrawContext ctx, int x, int y, int w, String label, double damage) {
		float p = nextProgress();
		if (p <= 0.01f) {
			return;
		}
		float base = LunaDraw.alpha();
		LunaDraw.setAlpha(base * p);
		int sx = x + Math.round((1f - p) * 5f);
		LunaDraw.text(ctx, textRenderer, label, sx, y, LunaDraw.TEXT_SUB);

		long hearts = Math.round(damage / 2);
		String value = animNum(hearts, p) + " ×";
		int heart = 9;
		int vw = LunaDraw.width(textRenderer, value) + 3 + heart;
		int vx = sx + w - vw;
		LunaDraw.text(ctx, textRenderer, value, vx, y, LunaDraw.TEXT);
		int hx = vx + vw - heart;
		int hy = y - 1;   // 글자 줄(9px)과 눈높이 맞추기
		if (!LunaGfx.drawHeart(ctx, hx, hy, heart, LunaDraw.withAlpha(0xFFFFFFFF, Math.round(255 * LunaDraw.alpha())))) {
			LunaDraw.text(ctx, textRenderer, "하트", vx, y, LunaDraw.TEXT);
		}
		LunaDraw.linkLine(ctx, sx + LunaDraw.width(textRenderer, label) + 6, vx - 6, y + 4);
		LunaDraw.setAlpha(base);
	}

	private void sectionTitle(DrawContext ctx, int x, int y, int w, String title) {
		LunaDraw.sectionTitle(ctx, textRenderer, x, y, w, title);
	}

	private int renderSummary(DrawContext ctx, LunaStats.Scope s, int x, int y, int w) {
		int y0 = y;
		sectionTitle(ctx, x, y, w, s.displayLabel());
		y += 16;
		statRow(ctx, x, y, w, "플레이타임", LunaStats.formatHours(s.playMs), true);
		y += 13;
		statRow(ctx, x, y, w, "자리 비움(AFK)", LunaStats.formatHours(s.afkMs), false);
		y += 13;
		statNumRow(ctx, x, y, w, "접속 횟수", s.sessions, "번", false);
		y += 13;
		if (s.firstSeen > 0) {
			statRow(ctx, x, y, w, "처음 플레이", date(s.firstSeen), false);
			y += 13;
			statRow(ctx, x, y, w, "마지막 플레이", date(s.lastSeen), false);
			y += 13;
		}
		// 49-28차: 이번 달 요약 한 줄
		LunaStats.Period month = s.months.get(LunaStats.monthKey(System.currentTimeMillis()));
		if (month != null && month.playMs > 0) {
			statRow(ctx, x, y, w, "이번 달", LunaStats.formatHours(month.playMs)
				+ " | 처치 " + num(month.kills) + " | 죽음 " + num(month.deaths), false);
			y += 13;
		}
		y += 6;
		sectionTitle(ctx, x, y, w, "전투");
		y += 16;
		statNumRow(ctx, x, y, w, "죽은 횟수", s.deaths, "번", false);
		y += 13;
		statNumRow(ctx, x, y, w, "처치", s.kills, "마리", false);
		y += 13;
		statHeartRow(ctx, x, y, w, "잃은 체력", s.damageTaken);
		y += 19;
		// 49-67차(5-11 "반복행동 분리" · "횟수와 거리를 떨어뜨리기"): 예전엔 [행동] 한 칸에
		// 거리·점프·웅크림·거래·상자·캔 것·제작이 전부 섞여 있었다. 단위가 다른 값(미터 vs 번)이
		// 한 줄씩 번갈아 나와서 읽기 어려웠다 - 성격대로 세 칸으로 나눈다.
		sectionTitle(ctx, x, y, w, "이동");
		y += 16;
		statRow(ctx, x, y, w, "이동 거리", LunaStats.formatDistance(s.totalDistance()), false);
		y += 13;
		y += 6;
		sectionTitle(ctx, x, y, w, "만들기 | 캐기");
		y += 16;
		long mined = 0;
		long crafted = 0;
		long picked = 0;
		for (LunaStats.ItemStat it : s.items.values()) {
			mined += it.mined;
			crafted += it.crafted;
			picked += it.picked;
		}
		statNumRow(ctx, x, y, w, "캔 블록", mined, "개", false);
		y += 13;
		statNumRow(ctx, x, y, w, "제작한 아이템", crafted, "개", false);
		y += 13;
		// 49-67차(5-11 "획득한 횟수 추가")
		statNumRow(ctx, x, y, w, "획득한 아이템", picked, "개", false);
		y += 13;
		statNumRow(ctx, x, y, w, "주민과 거래", s.trades, "번", false);
		y += 13;
		y += 6;
		sectionTitle(ctx, x, y, w, "반복 행동");
		y += 16;
		statNumRow(ctx, x, y, w, "점프", s.jumps, "번", false);
		y += 13;
		statRow(ctx, x, y, w, "웅크린 시간", LunaStats.formatDuration(s.sneakMs), false);
		y += 13;
		statNumRow(ctx, x, y, w, "상자 연 횟수", s.chestsOpened, "번", false);
		y += 13;
		long interactions = 0;
		for (long v : liveInteractions(s).values()) {
			interactions += v;
		}
		if (interactions > 0) {
			statNumRow(ctx, x, y, w, "상호작용", interactions, "번", false);
			y += 13;
		}
		return y - y0 + 10;
	}

	// ==================== 49-143차: 대시보드(카드 + 큰 숫자 + 그래프) ====================

	/** 반투명 덧칠(다크 = 흰색, 라이트 = 남색). 포스터는 늘 어두운 판이라 흰색. */
	private int ink(int alpha) {
		return poster ? ((alpha & 0xFF) << 24) | 0xFFFFFF : LunaClientScreen.ink(alpha);
	}

	private static final int GAP = 8;
	private static final int CARD_HEAD = 24;

	// ==================== 49-192차: 원그래프(도넛) ====================
	// 사용자: "원그래프도 좀 이용해줘 멋있게". 조각 사이는 가는 틈, 가장자리는 부드럽게(실제 픽셀 단위로 그림),
	// 처음 열 때 12시 방향부터 시계 방향으로 차오르고, 마우스를 올린 조각은 밝게 + 가운데에 비율과 이름.

	private int hoverMX = -99999, hoverMY = -99999;

	/** 조각 색(첫 조각은 테마색, 마지막 "기타"는 회색). */
	private static final int[] PIE_COLORS = {
		0, 0xFF5B9CFF, 0xFFFFB547, 0xFFFF6B8B, 0xFFA78BFA, 0xFF2DD4BF, 0xFFFF8A4C, 0xFF9AA3AF,
	};

	private int pieColor(int i, int n, boolean hasOther) {
		if (hasOther && i == n - 1) {
			return 0xFF7C8591;
		}
		return i == 0 ? (LunaClientScreen.themeAccent() | 0xFF000000) : PIE_COLORS[Math.min(i, PIE_COLORS.length - 1)];
	}

	/** 그린 도넛을 다시 계산하지 않게: 같은 모양이면 줄(run) 목록을 그대로 쓴다. */
	private final Map<String, int[]> pieRuns = new java.util.HashMap<>();

	/**
	 * 도넛 하나를 (cx, cy) 가운데, 바깥 반지름 r1, 안쪽 r0(둘 다 GUI 단위)로 그린다. 반환 = 마우스가 올라간 조각(-1 = 없음).
	 * 실제 화면 픽셀 단위로 계산해 가장자리를 부드럽게 칠한다(GUI 배율이 3이면 한 칸이 3픽셀이라 계단이 크게 보임).
	 */
	private int drawDonut(DrawContext ctx, int cx, int cy, int r1, int r0, long[] vals, int[] colors, float progress) {
		long total = 0;
		for (long v : vals) {
			total += Math.max(0, v);
		}
		if (total <= 0) {
			LunaDraw.circle(ctx, cx - r1, cy - r1, r1 * 2, ink(0x10));
			LunaDraw.circle(ctx, cx - r0, cy - r0, r0 * 2, LunaClientScreen.themeCard());
			return -1;
		}
		int n = vals.length;
		double[] edge = new double[n + 1];
		double acc = 0;
		for (int i = 0; i < n; i++) {
			edge[i] = acc / total;
			acc += Math.max(0, vals[i]);
		}
		edge[n] = 1.0;
		// 마우스가 올라간 조각(GUI 좌표)
		int hover = -1;
		double hx = hoverMX - cx, hy = hoverMY - cy;
		double hd = Math.sqrt(hx * hx + hy * hy);
		if (hd >= r0 && hd <= r1 + 2 && progress >= 0.999f) {
			double a = Math.atan2(hx, -hy);
			double f = (a < 0 ? a + Math.PI * 2 : a) / (Math.PI * 2);
			for (int i = 0; i < n; i++) {
				if (f >= edge[i] && f < edge[i + 1]) {
					hover = i;
					break;
				}
			}
		}
		int gs = Math.max(1, LunaCompat.currentGuiScale());
		boolean phys = LunaCompat.guiTransformSupported(ctx);
		int k = phys ? gs : 1;
		int R = r1 * k, Rin = r0 * k, grow = hover >= 0 ? 2 * k : 0;
		float pq = Math.round(progress * 40f) / 40f;
		StringBuilder key = new StringBuilder().append(R).append('/').append(Rin).append('/').append(pq).append('/').append(hover);
		for (int i = 0; i < n; i++) {
			key.append(',').append(vals[i]).append(':').append(colors[i]);
		}
		int[] runs = pieRuns.get(key.toString());
		if (runs == null) {
			runs = buildDonutRuns(R, Rin, grow, edge, colors, pq, hover);
			if (pieRuns.size() > 64) {
				pieRuns.clear();
			}
			pieRuns.put(key.toString(), runs);
		}
		if (phys) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, cx, cy);
			LunaCompat.guiScale(ctx, 1f / gs, 1f / gs);
		}
		try {
			int ox = phys ? 0 : cx, oy = phys ? 0 : cy;
			for (int i = 0; i + 3 < runs.length; i += 4) {
				ctx.fill(ox + runs[i], oy + runs[i + 2], ox + runs[i + 1], oy + runs[i + 2] + 1, LunaDraw.applyAlpha(runs[i + 3]));
			}
		} finally {
			if (phys) {
				LunaCompat.guiPop(ctx);
			}
		}
		return hover;
	}

	/** 도넛을 가로줄 조각(x0, x1, y, 색)으로 - 같은 색이 이어지면 한 번에 칠한다. */
	private static int[] buildDonutRuns(int R, int Rin, int grow, double[] edge, int[] colors, float progress, int hover) {
		int n = colors.length;
		int Rmax = R + grow;
		java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
		double gapPx = Math.max(0.8, R / 60.0);   // 조각 사이 틈(픽셀)
		for (int py = -Rmax; py < Rmax; py++) {
			int runX = 0, runC = 0;
			boolean open = false;
			for (int px = -Rmax; px <= Rmax; px++) {
				double x = px + 0.5, y = py + 0.5;
				double d = Math.sqrt(x * x + y * y);
				int color = 0;
				if (d >= Rin - 1 && d <= Rmax + 1) {
					double a = Math.atan2(x, -y);
					double f = (a < 0 ? a + Math.PI * 2 : a) / (Math.PI * 2);
					if (f <= progress) {
						int seg = n - 1;
						for (int i = 0; i < n; i++) {
							if (f < edge[i + 1]) {
								seg = i;
								break;
							}
						}
						double outer = seg == hover ? R + grow : R;
						double cov = Math.max(0, Math.min(1, outer + 0.5 - d)) * Math.max(0, Math.min(1, d - (Rin - 0.5)));
						// 조각 경계에서 틈(0 조각이 아니면)
						double da = Math.min(f - edge[seg], edge[seg + 1] - f) * Math.PI * 2 * d;
						if (edge[seg + 1] - edge[seg] < 0.9999 && da < gapPx) {
							cov *= Math.max(0, Math.min(1, da - gapPx + 1));
						}
						// 차오르는 끝도 부드럽게
						double tip = (progress - f) * Math.PI * 2 * d;
						if (progress < 0.9999 && tip < 1) {
							cov *= Math.max(0, tip);
						}
						if (cov > 0.02) {
							int base = colors[seg];
							float dim = hover >= 0 && seg != hover ? 0.45f : 1f;
							int al = (int) Math.round(((base >>> 24) & 0xFF) * cov * dim);
							// 알파를 16단계로 묶어 줄 수를 줄인다(눈으로는 구분 안 됨)
							al = Math.min(255, (al + 8) / 16 * 16);
							color = al <= 0 ? 0 : (al << 24) | (base & 0xFFFFFF);
						}
					}
				}
				if (open && color != runC) {
					out.add(runX); out.add(px); out.add(py); out.add(runC);
					open = false;
				}
				if (!open && color != 0) {
					runX = px;
					runC = color;
					open = true;
				}
			}
			if (open) {
				out.add(runX); out.add(Rmax + 1); out.add(py); out.add(runC);
			}
		}
		int[] arr = new int[out.size()];
		for (int i = 0; i < arr.length; i++) {
			arr[i] = out.get(i);
		}
		return arr;
	}

	/**
	 * 원그래프 카드: 왼쪽 도넛(가운데 합계 또는 올린 조각의 비율), 오른쪽 범례(색 점, 이름, 값, 비율).
	 * 8개가 넘으면 7개 + 기타. 반환 = 카드 높이.
	 */
	private int donutCard(DrawContext ctx, int x, int y, int w, String title, List<String> labels, List<Long> values,
			java.util.function.LongFunction<String> fmt, String centerLabel, String empty) {
		List<String> ls = new ArrayList<>(labels);
		List<Long> vs = new ArrayList<>(values);
		boolean other = false;
		if (ls.size() > 8) {
			long rest = 0;
			for (int i = 7; i < vs.size(); i++) {
				rest += vs.get(i);
			}
			ls = new ArrayList<>(ls.subList(0, 7));
			vs = new ArrayList<>(vs.subList(0, 7));
			ls.add("기타");
			vs.add(rest);
			other = true;
		}
		int n = ls.size();
		int r1 = Math.max(26, Math.min(44, (w - 20) / 5));
		int r0 = Math.round(r1 * 0.62f);
		int legendH = Math.max(1, n) * BAR_ROW;
		int bodyH = Math.max(r1 * 2 + 12, legendH + 8);
		int h = CARD_HEAD + bodyH;
		card(ctx, x, y, w, h, title);
		if (n == 0) {
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, empty, w - 20), x + 10,
				LunaDraw.textY(y + CARD_HEAD, BAR_ROW), LunaClientScreen.themeDim());
			return h;
		}
		long[] v = new long[n];
		int[] col = new int[n];
		long total = 0;
		for (int i = 0; i < n; i++) {
			v[i] = vs.get(i);
			col[i] = pieColor(i, n, other);
			total += Math.max(0, v[i]);
		}
		float p = nextProgress();
		int cx = x + 12 + r1, cy = y + CARD_HEAD + bodyH / 2 - 2;
		int hover = drawDonut(ctx, cx, cy, r1, r0, v, col, p);
		// 가운데 글자
		String top, bottom;
		if (hover >= 0) {
			top = Math.round(v[hover] * 100.0 / Math.max(1, total)) + "%";
			bottom = ls.get(hover);
		} else {
			top = fmt.apply(total);
			bottom = centerLabel;
		}
		int inner = r0 * 2 - 6;
		String tf = gFit(top, inner);
		gText(ctx, tf, cx - gWidth(tf) / 2, cy - 11, 14, hover >= 0 ? (col[hover] | 0xFF000000) : LunaClientScreen.themeText());
		String bf = LunaDraw.ellipsize(textRenderer, bottom, inner);
		LunaDraw.text(ctx, textRenderer, bf, cx - LunaDraw.width(textRenderer, bf) / 2, cy + 4, LunaClientScreen.themeDim());
		// 범례
		int lx = cx + r1 + 14;
		int lw = x + w - 10 - lx;
		int ly = y + CARD_HEAD + Math.max(0, (bodyH - legendH) / 2) - 2;
		for (int i = 0; i < n; i++) {
			int ry = ly + i * BAR_ROW;
			boolean hl = hover == i || (hover < 0 && LunaDraw.in(hoverMX, hoverMY, lx, ry, lw, BAR_ROW));
			if (hl) {
				LunaDraw.roundRect(ctx, lx - 4, ry, lw + 6, BAR_ROW, 3, ink(0x10));
			}
			LunaDraw.circle(ctx, lx, ry + BAR_ROW / 2 - 3, 6, col[i]);
			String pct = Math.round(v[i] * 100.0 / Math.max(1, total)) + "%";
			String val = fmt.apply(v[i]);
			int pw = LunaDraw.width(textRenderer, "100%");
			int vw = LunaDraw.width(textRenderer, val);
			LunaDraw.text(ctx, textRenderer, pct, x + w - 10 - LunaDraw.width(textRenderer, pct), LunaDraw.textY(ry, BAR_ROW),
				hl ? LunaClientScreen.themeText() : LunaClientScreen.themeSub());
			int valX = x + w - 10 - pw - 8 - vw;
			boolean showVal = valX > lx + 50;
			if (showVal) {
				LunaDraw.text(ctx, textRenderer, val, valX, LunaDraw.textY(ry, BAR_ROW), LunaClientScreen.themeDim());
			}
			int nameW = (showVal ? valX - 6 : x + w - 10 - pw - 6) - (lx + 10);
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, ls.get(i), Math.max(10, nameW)), lx + 10,
				LunaDraw.textY(ry, BAR_ROW), LunaClientScreen.themeText());
		}
		return h;
	}
	private static final int BAR_ROW = 14;

	/** 카드 바탕(설정 화면 상자와 같은 평평한 카드 + 1px 테두리) + 왼쪽 위 제목. */
	private void card(DrawContext ctx, int x, int y, int w, int h, String title) {
		// 49-227차(사진 시안): 아래 두께가 있는 카드
		LunaDraw.card3d(ctx, x, y, w, h, 4, LunaClientScreen.themeCard(), LunaClientScreen.themeLine());
		if (title != null) {
			gText(ctx, title, x + 10, y + 5, 14, LunaClientScreen.themeSub());
		}
	}

	/** 숫자 상자 격자(이름 작게, 값은 갈무리 2배, 첫 칸은 테마색). 반환 = 쓴 높이. */
	private int kpis(DrawContext ctx, int x, int y, int w, String[][] items) {
		return kpis(ctx, x, y, w, items, true);
	}

	/** 49-196차: accentFirst = 첫 칸 값을 테마색으로(요약은 위 큰 판이 테마색이라 끈다). */
	private int kpis(DrawContext ctx, int x, int y, int w, String[][] items, boolean accentFirst) {
		int cols = Math.max(2, Math.min(4, (w + GAP) / (90 + GAP)));
		int tw = (w - GAP * (cols - 1)) / cols;
		int th = 46;
		int rows = (items.length + cols - 1) / cols;
		for (int i = 0; i < items.length; i++) {
			int tx = x + (i % cols) * (tw + GAP);
			int ty = y + (i / cols) * (th + GAP);
			float p = nextProgress();
			if (p <= 0.01f) {
				continue;
			}
			float base = LunaDraw.alpha();
			LunaDraw.setAlpha(base * p);
			card(ctx, tx, ty, tw, th, null);
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, items[i][0], tw - 16), tx + 10, ty + 8,
				LunaClientScreen.themeSub());
			String v = items[i][1];
			int color = i == 0 && accentFirst ? LunaClientScreen.themeAccent() : LunaClientScreen.themeText();
			if (gBigWidth(v, ctx) <= tw - 20) {
				gBig(ctx, v, tx + 10, ty + 20, 22, color);
			} else {
				gText(ctx, gFit(v, tw - 20), tx + 10, ty + 22, 16, color);
			}
			LunaDraw.setAlpha(base);
		}
		return rows * th + (rows - 1) * GAP;
	}

	/** 막대 카드: 이름 | 막대 | 값. 반환 = 카드 높이. */
	private int barsCard(DrawContext ctx, int x, int y, int w, String title, List<String> labels, List<Long> values,
			List<String> shown, String empty) {
		int n = Math.max(1, labels.size());
		int h = CARD_HEAD + n * BAR_ROW + 8;
		card(ctx, x, y, w, h, title);
		int ry = y + CARD_HEAD;
		if (labels.isEmpty()) {
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, empty, w - 20), x + 10, LunaDraw.textY(ry, BAR_ROW),
				LunaClientScreen.themeDim());
			return h;
		}
		long max = 1;
		for (long v : values) {
			max = Math.max(max, v);
		}
		int valW = 0;
		for (String sh : shown) {
			valW = Math.max(valW, LunaDraw.width(textRenderer, sh));
		}
		int labelW = Math.max(40, Math.min((w - 20) * 2 / 5, w - 20 - valW - 50));
		int bx = x + 10 + labelW + 6;
		int bw = Math.max(10, x + w - 10 - valW - 8 - bx);
		for (int i = 0; i < labels.size(); i++) {
			float p = nextProgress();
			float base = LunaDraw.alpha();
			LunaDraw.setAlpha(base * Math.max(0.001f, p));
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, labels.get(i), labelW), x + 10,
				LunaDraw.textY(ry, BAR_ROW), LunaClientScreen.themeText());
			LunaDraw.roundRect(ctx, bx, ry + 5, bw, 4, 2, ink(0x14));
			int full = (int) Math.max(2, bw * values.get(i) / (double) max);
			LunaDraw.roundRect(ctx, bx, ry + 5, Math.max(2, Math.round(full * p)), 4, 2,
				i == 0 ? LunaClientScreen.themeAccent() : LunaDraw.withAlpha(LunaClientScreen.themeAccent(), 0x99));
			String v = shown.get(i);
			LunaDraw.text(ctx, textRenderer, v, x + w - 10 - LunaDraw.width(textRenderer, v), LunaDraw.textY(ry, BAR_ROW),
				LunaClientScreen.themeSub());
			LunaDraw.setAlpha(base);
			ry += BAR_ROW;
		}
		return h;
	}

	/** 세로 막대 그래프 카드(달별·날별). labels는 막대 아래 글자(null이면 안 씀). */
	private int columnsCard(DrawContext ctx, int x, int y, int w, int chartH, String title, long[] vals, String[] labels,
			String peakText) {
		int h = CARD_HEAD + chartH + 16;
		card(ctx, x, y, w, h, title);
		if (peakText != null) {
			LunaDraw.text(ctx, textRenderer, peakText, x + w - 10 - LunaDraw.width(textRenderer, peakText), y + 9,
				LunaClientScreen.themeDim());
		}
		int n = vals.length;
		int gx = x + 10;
		int gw = w - 20;
		int gap = n > 20 ? 1 : 3;
		int bw = Math.max(2, (gw - gap * (n - 1)) / n);
		long max = 1;
		for (long v : vals) {
			max = Math.max(max, v);
		}
		int base = y + CARD_HEAD + chartH;
		float p = nextProgress();
		for (int i = 0; i < n; i++) {
			int bx = gx + i * (bw + gap);
			ctx.fill(bx, base - chartH, bx + bw, base, LunaDraw.applyAlpha(ink(0x0C)));
			int bh = vals[i] <= 0 ? 0 : Math.max(2, Math.round(chartH * (float) (vals[i] / (double) max) * p));
			if (bh > 0) {
				ctx.fill(bx, base - bh, bx + bw, base, LunaDraw.applyAlpha(i == n - 1
					? LunaClientScreen.themeAccent() : LunaDraw.withAlpha(LunaClientScreen.themeAccent(), 0xA6)));
			}
			if (labels != null && labels[i] != null) {
				String l = labels[i];
				int lw = LunaDraw.width(textRenderer, l);
				LunaDraw.text(ctx, textRenderer, l, bx + (bw - lw) / 2, base + 4, LunaClientScreen.themeDim());
			}
		}
		return h;
	}

	/** 두 카드를 나란히(좁으면 위아래). 반환 = 쓴 높이. */
	private interface CardFn {
		int draw(int x, int y, int w);
	}

	private int pair(int x, int y, int w, CardFn left, CardFn right) {
		if (w >= 380) {
			int cw = (w - GAP) / 2;
			int a = left.draw(x, y, cw);
			int b = right.draw(x + cw + GAP, y, w - cw - GAP);
			return Math.max(a, b);
		}
		int a = left.draw(x, y, w);
		int b = right.draw(x, y + a + GAP, w);
		return a + GAP + b;
	}

	/** 키-값 카드(두 열). */
	private int infoCard(DrawContext ctx, int x, int y, int w, String title, String[][] rows) {
		int cols = w >= 380 ? 2 : 1;
		int per = (rows.length + cols - 1) / cols;
		int h = CARD_HEAD + per * BAR_ROW + 8;
		card(ctx, x, y, w, h, title);
		int colW = (w - 20 - (cols - 1) * 16) / cols;
		for (int i = 0; i < rows.length; i++) {
			int c = i / per;
			int r = i % per;
			int rx = x + 10 + c * (colW + 16);
			int ry = y + CARD_HEAD + r * BAR_ROW;
			LunaDraw.text(ctx, textRenderer, rows[i][0], rx, LunaDraw.textY(ry, BAR_ROW), LunaClientScreen.themeSub());
			String v = rows[i][1];
			LunaDraw.text(ctx, textRenderer, v, rx + colW - LunaDraw.width(textRenderer, v), LunaDraw.textY(ry, BAR_ROW),
				LunaClientScreen.themeText());
		}
		return h;
	}

	// ---- 자료 뽑기

	private record Top(List<String> labels, List<Long> values, List<String> shown) {
	}

	private static Top topOf(Map<String, Long> map, int limit, java.util.function.Function<String, String> name,
			java.util.function.LongFunction<String> fmt) {
		List<Map.Entry<String, Long>> list = new ArrayList<>(map.entrySet());
		list.sort(Map.Entry.<String, Long>comparingByValue().reversed());
		Top t = new Top(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		for (int i = 0; i < Math.min(limit, list.size()); i++) {
			Map.Entry<String, Long> e = list.get(i);
			if (e.getValue() <= 0) {
				continue;
			}
			t.labels().add(name.apply(e.getKey()));
			t.values().add(e.getValue());
			t.shown().add(fmt.apply(e.getValue()));
		}
		return t;
	}

	private static String n0(long v) {
		return num(v);
	}

	private long[] lastMonths(LunaStats.Scope s, int n, String[] labelsOut) {
		long[] v = new long[n];
		java.time.YearMonth ym = java.time.YearMonth.now();
		for (int i = 0; i < n; i++) {
			java.time.YearMonth m = ym.minusMonths(n - 1 - i);
			String key = m.toString();
			LunaStats.Period p = s.months.get(key);
			v[i] = p == null ? 0 : p.playMs;
			labelsOut[i] = m.getMonthValue() + "월";
		}
		return v;
	}

	private static long minedTotal(LunaStats.Scope s) {
		long t = 0;
		for (LunaStats.ItemStat it : s.items.values()) {
			t += it.mined;
		}
		return t;
	}

	// ---- 탭들

	/**
	 * 49-196차(사용자: "통계 한번에 정리 좀 더 깔끔하게"): [요약]을 다시 짰다. 예전엔 숫자 상자 8개 + 달별 + 순위 둘 + 기록 8줄이
	 * 한꺼번에 쏟아져서 무엇이 중요한지 안 보였고, 전투(죽음 원인, 잃은 체력)나 소셜(채팅) 탭과 겹치는 숫자도 섞여 있었다.
	 * <ol>
	 *   <li>맨 위 큰 판 하나: 플레이타임(크게) + 언제부터, 접속 횟수, 연속 접속 한 줄 + 오른쪽에 최근 30일 막대.</li>
	 *   <li>숫자 상자는 대표 넷만(처치, 죽음, 이동 거리, 캔 블록) 한 줄.</li>
	 *   <li>달별 플레이, 많이 캔 블록과 잡은 생명체(5개씩), 기록(6줄) - 나머지 자세한 건 각 탭에.</li>
	 * </ol>
	 */
	private int dSummary(DrawContext ctx, LunaStats.Scope s, int x, int y, int w) {
		int y0 = y;
		y += summaryHero(ctx, s, x, y, w) + GAP;
		y += kpis(ctx, x, y, w, new String[][]{
			{"처치", num(s.kills)},
			{"죽음", num(s.deaths)},
			{"이동 거리", LunaStats.formatDistance(s.totalDistance())},
			{"캔 블록", num(minedTotal(s))}}, false) + GAP;
		String[] ml = new String[12];
		long[] mv = lastMonths(s, 12, ml);
		y += columnsCard(ctx, x, y, w, 60, "달별 플레이", mv, ml, "이번 달 " + LunaStats.formatHours(mv[11])) + GAP;
		Map<String, Long> minedMap = new java.util.LinkedHashMap<>();
		for (Map.Entry<String, LunaStats.ItemStat> e : s.items.entrySet()) {
			if (e.getValue().mined > 0) {
				minedMap.put(itemLabel(e.getKey(), e.getValue()), e.getValue().mined);
			}
		}
		Top mined = topOf(minedMap, 5, k -> k, LunaStatsScreen::n0);
		Top kills = topOf(s.killTypes, 5, LunaStatsScreen::shortId, LunaStatsScreen::n0);
		y += pair(x, y, w,
			(cx, cy, cw) -> barsCard(ctx, cx, cy, cw, "많이 캔 블록", mined.labels(), mined.values(), mined.shown(), "아직 캔 블록이 없습니다"),
			(cx, cy, cw) -> barsCard(ctx, cx, cy, cw, "잡은 생명체", kills.labels(), kills.values(), kills.shown(), "아직 잡은 생명체가 없습니다"))
			+ GAP;
		y += infoCard(ctx, x, y, w, "기록", new String[][]{
			{"마지막 플레이", date(s.lastSeen)},
			{"자리 비움", LunaStats.formatHours(s.afkMs)},
			{"점프", num(s.jumps) + "번"},
			{"상자 연 횟수", num(s.chestsOpened) + "번"},
			{"주민과 거래", num(s.trades) + "번"},
			{"보낸 채팅", num(s.chatSent) + "번"}});
		return y - y0 + 10;
	}

	/** 49-196차: [요약] 맨 위 큰 판 - 왼쪽 플레이타임(크게) + 한 줄 요약, 오른쪽 최근 30일 막대. 반환 = 높이. */
	private int summaryHero(DrawContext ctx, LunaStats.Scope s, int x, int y, int w) {
		int h = 78;
		float p = nextProgress();
		if (p <= 0.01f) {
			return h;
		}
		float base = LunaDraw.alpha();
		LunaDraw.setAlpha(base * p);
		try {
			card(ctx, x, y, w, h, null);
			// 오른쪽 최근 30일(넓을 때만)
			boolean chart = w >= 360;
			int chartW = chart ? Math.min(240, w * 42 / 100) : 0;
			int leftW = w - 24 - (chart ? chartW + 16 : 0);
			LunaDraw.text(ctx, textRenderer, "플레이타임", x + 12, y + 10, LunaClientScreen.themeSub());
			String play = LunaStats.formatHours(s.playMs);
			if (gBigWidth(play, ctx) <= leftW) {
				gBig(ctx, play, x + 12, y + 24, 24, LunaClientScreen.themeAccent());
			} else {
				gText(ctx, gFit(play, leftW), x + 12, y + 26, 16, LunaClientScreen.themeAccent());
			}
			String since = s.firstSeen > 0 ? date(s.firstSeen) + "부터" : "기록 시작 전";
			String line = since + "  |  접속 " + num(s.sessions) + "번  |  연속 " + streak(s.days) + "일";
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, line, leftW), x + 12, y + h - 18,
				LunaClientScreen.themeDim());
			if (chart) {
				int gx = x + w - 12 - chartW;
				// 왼쪽과 나누는 세로 선
				ctx.fill(gx - 8, y + 12, gx - 7, y + h - 12, LunaDraw.applyAlpha(LunaClientScreen.themeLine()));
				java.time.LocalDate today = java.time.LocalDate.now();
				long[] dv = new long[30];
				long max = 1;
				for (int i = 0; i < 30; i++) {
					dv[i] = s.days.getOrDefault(today.minusDays(29 - i).toString(), 0L);
					max = Math.max(max, dv[i]);
				}
				LunaDraw.text(ctx, textRenderer, "최근 30일", gx, y + 10, LunaClientScreen.themeSub());
				String todayText = "오늘 " + LunaStats.formatDuration(dv[29]);
				LunaDraw.text(ctx, textRenderer, todayText, gx + chartW - LunaDraw.width(textRenderer, todayText), y + 10,
					LunaClientScreen.themeDim());
				int top = y + 26;
				int bottom = y + h - 12;
				int ch = bottom - top;
				int gap = 1;
				int bw = Math.max(1, (chartW - gap * 29) / 30);
				int used = bw * 30 + gap * 29;
				int bx0 = gx + chartW - used;
				for (int i = 0; i < 30; i++) {
					int bx = bx0 + i * (bw + gap);
					ctx.fill(bx, top, bx + bw, bottom, LunaDraw.applyAlpha(ink(0x0C)));
					int bh = dv[i] <= 0 ? 0 : Math.max(2, Math.round(ch * (float) (dv[i] / (double) max) * p));
					if (bh > 0) {
						ctx.fill(bx, bottom - bh, bx + bw, bottom, LunaDraw.applyAlpha(i == 29
							? LunaClientScreen.themeAccent() : LunaDraw.withAlpha(LunaClientScreen.themeAccent(), 0x8C)));
					}
				}
			}
		} finally {
			LunaDraw.setAlpha(base);
		}
		return h;
	}

	private int dCombat(DrawContext ctx, LunaStats.Scope s, int x, int y, int w) {
		int y0 = y;
		Map<String, Long> live = liveInteractions(s);
		long dealt = live.getOrDefault("준 피해", 0L);
		long blocked = live.getOrDefault("방패로 막은 피해", 0L);
		String kd = String.format(java.util.Locale.ROOT, "%.2f", s.kills / (double) Math.max(1, s.deaths));
		y += kpis(ctx, x, y, w, new String[][]{
			{"처치", num(s.kills)},
			{"죽음", num(s.deaths)},
			{"K/D", kd},
			{"공격", num(s.hits) + "번"},
			{"PvP 처치", num(s.playerKills)},
			{"PvP 죽음", num(s.playerDeaths)},
			{"가장 오래 산 시간", LunaStats.formatDuration(s.bestLifeMs)},
			{"이번 목숨", LunaStats.formatDuration(s.lifeMs)}}) + GAP;
		Top kills = topOf(s.killTypes, 8, LunaStatsScreen::shortId, LunaStatsScreen::n0);
		Top deaths = topOf(s.deathCauses, 40, k -> k, LunaStatsScreen::n0);
		y += pair(x, y, w,
			(cx, cy, cw) -> barsCard(ctx, cx, cy, cw, "잡은 생명체", kills.labels(), kills.values(), kills.shown(), "아직 잡은 생명체가 없습니다"),
			(cx, cy, cw) -> donutCard(ctx, cx, cy, cw, "죽은 원인", deaths.labels(), deaths.values(), v -> num(v) + "번", "죽음",
				"아직 죽은 적이 없습니다"))
			+ GAP;
		y += infoCard(ctx, x, y, w, "체력", new String[][]{
			{"잃은 체력", num(Math.round(s.damageTaken / 2)) + " 하트"},
			{"준 피해", num(Math.round(dealt / 20.0)) + " 하트"},
			{"방패로 막은 피해", num(Math.round(blocked / 20.0)) + " 하트"},
			{"처치당 공격", s.kills > 0 ? String.format(java.util.Locale.ROOT, "%.1f번", s.hits / (double) s.kills) : "-"}});
		return y - y0 + 10;
	}

	private int dLife(DrawContext ctx, LunaStats.Scope s, int x, int y, int w) {
		int y0 = y;
		Map<String, Long> live = liveInteractions(s);
		Map<String, Long> ores = new java.util.LinkedHashMap<>();
		Map<String, Long> crops = new java.util.LinkedHashMap<>();
		long oreSum = 0;
		long cropSum = 0;
		long crafted = 0;
		for (Map.Entry<String, LunaStats.ItemStat> e : s.items.entrySet()) {
			LunaStats.ItemStat it = e.getValue();
			crafted += it.crafted;
			String id = it.base != null ? it.base : e.getKey();
			if (it.mined > 0 && isOre(id)) {
				ores.merge(itemLabel(e.getKey(), it), it.mined, Long::sum);
				oreSum += it.mined;
			} else if (it.mined > 0 && CROPS.contains(pathOf(id))) {
				crops.merge(itemLabel(e.getKey(), it), it.mined, Long::sum);
				cropSum += it.mined;
			}
		}
		y += kpis(ctx, x, y, w, new String[][]{
			{"캔 블록", num(minedTotal(s))},
			{"캔 광물", num(oreSum)},
			{"수확한 작물", num(cropSum)},
			{"낚은 물고기", num(live.getOrDefault("낚은 물고기", 0L))},
			{"동물 번식", num(live.getOrDefault("동물 번식", 0L))},
			{"제작", num(crafted)},
			{"주민과 거래", num(s.trades)},
			{"상자 연 횟수", num(s.chestsOpened)}}) + GAP;
		Top o = topOf(ores, 8, k -> k, LunaStatsScreen::n0);
		Top c = topOf(crops, 8, k -> k, LunaStatsScreen::n0);
		y += pair(x, y, w,
			(cx, cy, cw) -> barsCard(ctx, cx, cy, cw, "광물", o.labels(), o.values(), o.shown(), "아직 캔 광물이 없습니다"),
			(cx, cy, cw) -> barsCard(ctx, cx, cy, cw, "작물", c.labels(), c.values(), c.shown(), "아직 수확한 작물이 없습니다"))
			+ GAP;
		Map<String, Long> inter = new java.util.LinkedHashMap<>();
		for (Map.Entry<String, Long> e : live.entrySet()) {
			if (!LunaStats.isDamageInteraction(e.getKey())) {
				inter.put(e.getKey(), e.getValue());
			}
		}
		Top it = topOf(inter, 12, k -> k, LunaStatsScreen::n0);
		y += barsCard(ctx, x, y, w, "상호작용", it.labels(), it.values(), it.shown(),
			"마인크래프트 기본 통계에서 읽어 옵니다 (월드에 들어가면 채워집니다)");
		return y - y0 + 10;
	}

	private int dHabit(DrawContext ctx, LunaStats.Scope s, int x, int y, int w) {
		int y0 = y;
		long[] dayTotal = new long[7];
		long[] hourTotal = new long[24];
		long hmMax = 1;
		for (int i = 0; i < 168; i++) {
			dayTotal[i / 24] += s.hourMs[i];
			hourTotal[i % 24] += s.hourMs[i];
			hmMax = Math.max(hmMax, s.hourMs[i]);
		}
		int bestDay = 0;
		int bestHour = 0;
		for (int i = 1; i < 7; i++) {
			if (dayTotal[i] > dayTotal[bestDay]) {
				bestDay = i;
			}
		}
		for (int i = 1; i < 24; i++) {
			if (hourTotal[i] > hourTotal[bestHour]) {
				bestHour = i;
			}
		}
		boolean any = hmMax > 1;
		long avg = s.sessions > 0 ? s.playMs / s.sessions : 0;
		int afkPct = s.playMs > 0 ? (int) Math.round(s.afkMs * 100.0 / s.playMs) : 0;
		y += kpis(ctx, x, y, w, new String[][]{
			{"가장 긴 접속", LunaStats.formatDuration(s.longestSessionMs)},
			{"평균 접속", LunaStats.formatDuration(avg)},
			{"연속 접속", streak(s.days) + "일"},
			{"최고 연속", bestStreak(s.days) + "일"},
			{"많이 한 요일", any ? WEEK[bestDay] + "요일" : "-"},
			{"많이 한 시간", any ? bestHour + "시" : "-"},
			{"자리 비움", afkPct + "%"},
			{"플레이타임", LunaStats.formatHours(s.playMs)}}) + GAP;
		// 요일 × 시간 칸
		int labelW = 16;
		int cell = Math.max(5, Math.min(16, (w - 20 - labelW) / 24));
		int hmH = CARD_HEAD + 12 + 7 * cell + 10;
		card(ctx, x, y, w, hmH, "요일 × 시간");
		if (!any) {
			LunaDraw.text(ctx, textRenderer, "이번 업데이트부터 모읍니다 - 조금 놀고 오면 채워집니다", x + 10,
				y + CARD_HEAD + 4, LunaClientScreen.themeDim());
		} else {
			int gx = x + 10 + labelW;
			for (int hr = 0; hr < 24; hr += 3) {
				LunaDraw.text(ctx, textRenderer, String.valueOf(hr), gx + hr * cell, y + CARD_HEAD, LunaClientScreen.themeDim());
			}
			int top = y + CARD_HEAD + 12;
			float p = nextProgress();
			for (int d = 0; d < 7; d++) {
				LunaDraw.text(ctx, textRenderer, WEEK[d], x + 10, top + d * cell + Math.max(0, (cell - 8) / 2), LunaClientScreen.themeSub());
				for (int hr = 0; hr < 24; hr++) {
					long v = s.hourMs[d * 24 + hr];
					float k = v <= 0 ? 0f : (0.2f + 0.8f * (float) (v / (double) hmMax)) * p;
					int col = LunaDraw.lerpColor(ink(0x10), LunaClientScreen.themeAccent() | 0xFF000000, k);
					int cx = gx + hr * cell;
					int cy = top + d * cell;
					ctx.fill(cx, cy, cx + cell - 1, cy + cell - 1, LunaDraw.applyAlpha(col));
				}
			}
		}
		y += hmH + GAP;
		// 최근 14일
		java.time.LocalDate today = java.time.LocalDate.now();
		long[] dv = new long[14];
		String[] dl = new String[14];
		for (int i = 0; i < 14; i++) {
			java.time.LocalDate d = today.minusDays(13 - i);
			dv[i] = s.days.getOrDefault(d.toString(), 0L);
			dl[i] = i % 2 == 1 || i == 13 ? String.valueOf(d.getDayOfMonth()) : null;
		}
		y += columnsCard(ctx, x, y, w, 60, "최근 14일", dv, dl, "오늘 " + LunaStats.formatDuration(dv[13]));
		return y - y0 + 10;
	}

	private int dSocial(DrawContext ctx, LunaStats.Scope s, int x, int y, int w) {
		int y0 = y;
		y += kpis(ctx, x, y, w, new String[][]{
			{"보낸 채팅", num(s.chatSent)},
			{"받은 채팅", num(s.chatReceived)},
			{"내 이름 불림", num(s.mentions)},
			{"함께한 사람", num(s.peers.size()) + "명"},
			{"들어간 서버/맵", num(worlds.size()) + "곳"},
			{"접속", num(s.sessions) + "번"},
			{"채팅 비율", s.chatReceived > 0 ? Math.round(s.chatSent * 100.0 / Math.max(1, s.chatReceived)) + "%" : "-"},
			{"한 시간당 채팅", s.playMs > 3_600_000L ? String.valueOf(Math.round(s.chatSent / (s.playMs / 3_600_000.0))) : "-"}}) + GAP;
		Map<String, Long> servers = new java.util.LinkedHashMap<>();
		for (LunaStats.Scope w2 : worlds) {
			servers.merge(w2.displayLabel(), w2.playMs, Long::sum);
		}
		Top sv = topOf(servers, 40, k -> k, LunaStats::formatHours);
		Top pe = topOf(s.peers, 10, k -> k, LunaStats::formatDuration);
		y += pair(x, y, w,
			(cx, cy, cw) -> donutCard(ctx, cx, cy, cw, "서버/맵별 시간", sv.labels(), sv.values(), LunaStats::formatHours, "전체",
				"아직 기록이 없습니다"),
			(cx, cy, cw) -> barsCard(ctx, cx, cy, cw, "같이 오래 논 사람", pe.labels(), pe.values(), pe.shown(),
				"이번 업데이트부터 모읍니다"));
		return y - y0 + 10;
	}

	private int dTravel(DrawContext ctx, LunaStats.Scope s, int x, int y, int w) {
		int y0 = y;
		double total = s.totalDistance();
		long travel = 0;
		for (long v : s.travelMs.values()) {
			travel += v;
		}
		y += kpis(ctx, x, y, w, new String[][]{
			{"전체 거리", LunaStats.formatDistance(total)},
			{"걸은 거리", LunaStats.formatDistance(s.distance.getOrDefault("walk", 0d))},
			{"달린 거리", LunaStats.formatDistance(s.distance.getOrDefault("sprint", 0d))},
			{"이동한 시간", LunaStats.formatHours(travel)}}) + GAP;
		Map<String, Long> dist = new java.util.LinkedHashMap<>();
		Map<String, Long> time = new java.util.LinkedHashMap<>();
		for (String mode : LunaStats.MODES) {
			double d = s.distance.getOrDefault(mode, 0d);
			if (d > 0) {
				dist.put(LunaStats.modeName(mode), Math.round(d));
			}
			long ms = s.travelMs.getOrDefault(mode, 0L);
			if (ms > 0) {
				time.put(LunaStats.modeName(mode), ms);
			}
		}
		Top dt = topOf(dist, 8, k -> k, v -> LunaStats.formatDistance(v));
		Top tt = topOf(time, 8, k -> k, LunaStats::formatDuration);
		y += pair(x, y, w,
			(cx, cy, cw) -> donutCard(ctx, cx, cy, cw, "방법별 거리", dt.labels(), dt.values(), v -> LunaStats.formatDistance(v), "전체",
				"아직 기록이 없습니다"),
			(cx, cy, cw) -> barsCard(ctx, cx, cy, cw, "방법별 시간", tt.labels(), tt.values(), tt.shown(), "아직 기록이 없습니다"));
		return y - y0 + 10;
	}

	/**
	 * 49-192차(사용자: "통계에 년도/월별 플탐 이런 것도 따로, 원그래프도 멋있게"): 기간 탭 = 연도 고르기(칩) →
	 * 그 해 숫자 상자 → 1~12월 막대 → [달별 비율 | 연도별 비율] 원그래프 → [시간대 | 요일] 원그래프 → 예전 연도/달 목록.
	 */
	private int dPeriod(DrawContext ctx, LunaStats.Scope s, int x, int y, int w, int mouseX, int mouseY) {
		int y0 = y;
		// 연도 목록(기록 있는 해, 최근 먼저)
		java.util.TreeMap<String, LunaStats.Period> years = new java.util.TreeMap<>(Comparator.reverseOrder());
		for (Map.Entry<String, LunaStats.Period> e : s.months.entrySet()) {
			years.computeIfAbsent(LunaStats.yearOf(e.getKey()), k -> new LunaStats.Period()).add(e.getValue());
		}
		String thisYear = String.valueOf(java.time.Year.now().getValue());
		if (years.isEmpty()) {
			years.put(thisYear, new LunaStats.Period());
		}
		if (periodYear == null || !years.containsKey(periodYear)) {
			periodYear = years.firstKey();
		}
		// 연도 칩
		yearChips.clear();
		yearChipKeys.clear();
		int chx = x;
		for (String yr : years.keySet()) {
			String label = yr + "년";
			int cw = gWidth(label) + 18;
			boolean sel = yr.equals(periodYear);
			boolean hov = LunaDraw.in(hoverMX, hoverMY, chx, y, cw, 20);
			LunaDraw.card3d(ctx, chx, y, cw, 20, hov ? 1f : 0f, sel);   // 49-227차
			gText(ctx, label, chx + 9, y + 3, 14, sel ? LunaClientScreen.themeText() : LunaClientScreen.themeSub());
			yearChips.add(new int[]{chx, y, cw, 20});
			yearChipKeys.add(yr);
			chx += cw + 6;
		}
		y += 20 + GAP;

		LunaStats.Period yp = years.get(periodYear);
		long[] mv = new long[12];
		String[] ml = new String[12];
		int bestMonth = -1;
		int monthsPlayed = 0;
		for (int m = 1; m <= 12; m++) {
			LunaStats.Period p = s.months.get(periodYear + "-" + (m < 10 ? "0" : "") + m);
			mv[m - 1] = p == null ? 0 : p.playMs;
			ml[m - 1] = m + "월";
			if (mv[m - 1] > 0) {
				monthsPlayed++;
				if (bestMonth < 0 || mv[m - 1] > mv[bestMonth]) {
					bestMonth = m - 1;
				}
			}
		}
		int daysPlayed = 0;
		long dayMax = 0;
		for (Map.Entry<String, Long> e : s.days.entrySet()) {
			if (e.getKey().startsWith(periodYear + "-") && e.getValue() >= 60_000L) {
				daysPlayed++;
				dayMax = Math.max(dayMax, e.getValue());
			}
		}
		y += kpis(ctx, x, y, w, new String[][]{
			{periodYear + "년 플레이", LunaStats.formatHours(yp.playMs)},
			{"가장 많이 한 달", bestMonth < 0 ? "-" : (bestMonth + 1) + "월"},
			{"한 달 평균", monthsPlayed > 0 ? LunaStats.formatHours(yp.playMs / monthsPlayed) : "-"},
			{"플레이한 날", daysPlayed + "일"},
			{"하루 평균", daysPlayed > 0 ? LunaStats.formatDuration(yp.playMs / daysPlayed) : "-"},
			{"가장 오래 한 날", dayMax > 0 ? LunaStats.formatDuration(dayMax) : "-"},
			{"처치", num(yp.kills)},
			{"죽음", num(yp.deaths)}}) + GAP;
		y += columnsCard(ctx, x, y, w, 80, periodYear + "년 달별 플레이", mv, ml,
			bestMonth < 0 ? null : "최고 " + (bestMonth + 1) + "월 " + LunaStats.formatHours(mv[bestMonth])) + GAP;

		// 원그래프: 그 해 달별 비율 | 연도별 비율
		List<String> mLabels = new ArrayList<>();
		List<Long> mVals = new ArrayList<>();
		Integer[] order = new Integer[12];
		for (int i = 0; i < 12; i++) {
			order[i] = i;
		}
		java.util.Arrays.sort(order, (a, b) -> Long.compare(mv[b], mv[a]));
		for (int i : order) {
			if (mv[i] > 0) {
				mLabels.add((i + 1) + "월");
				mVals.add(mv[i]);
			}
		}
		List<String> yLabels = new ArrayList<>();
		List<Long> yVals = new ArrayList<>();
		for (Map.Entry<String, LunaStats.Period> e : years.entrySet()) {
			if (e.getValue().playMs > 0) {
				yLabels.add(e.getKey() + "년");
				yVals.add(e.getValue().playMs);
			}
		}
		final String yr = periodYear;
		y += pair(x, y, w,
			(cx, cy, cw) -> donutCard(ctx, cx, cy, cw, yr + "년 달별 비율", mLabels, mVals, LunaStats::formatHours, yr + "년",
				"이 해에는 아직 기록이 없습니다"),
			(cx, cy, cw) -> donutCard(ctx, cx, cy, cw, "연도별 플레이", yLabels, yVals, LunaStats::formatHours, "전체",
				"아직 기록이 없습니다")) + GAP;

		// 원그래프: 시간대 | 요일(전체 기간 - 요일×시간 기록은 해를 나누지 않는다)
		long[] slot = new long[5];
		long[] week = new long[7];
		for (int i = 0; i < 168; i++) {
			int hr = i % 24;
			slot[hr < 6 ? 0 : hr < 12 ? 1 : hr < 18 ? 2 : hr < 22 ? 3 : 4] += s.hourMs[i];
			week[i / 24] += s.hourMs[i];
		}
		String[] slotNames = {"새벽 0~6시", "아침 6~12시", "오후 12~18시", "저녁 18~22시", "밤 22~24시"};
		List<String> sl = new ArrayList<>();
		List<Long> sv = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			if (slot[i] > 0) {
				sl.add(slotNames[i]);
				sv.add(slot[i]);
			}
		}
		List<String> wl = new ArrayList<>();
		List<Long> wv = new ArrayList<>();
		for (int i = 0; i < 7; i++) {
			if (week[i] > 0) {
				wl.add(WEEK[i] + "요일");
				wv.add(week[i]);
			}
		}
		y += pair(x, y, w,
			(cx, cy, cw) -> donutCard(ctx, cx, cy, cw, "시간대", sl, sv, LunaStats::formatHours, "전체", "아직 기록이 없습니다"),
			(cx, cy, cw) -> donutCard(ctx, cx, cy, cw, "요일", wl, wv, LunaStats::formatHours, "전체", "아직 기록이 없습니다"))
			+ GAP + 4;
		y += renderPeriods(ctx, s, x + 4, y, w - 8, mouseX, mouseY);
		return y - y0;
	}

	/** 49-192차: 기간 탭에서 고른 연도(null = 기록 있는 가장 최근 해)와 칩 자리. */
	private String periodYear;
	private final List<int[]> yearChips = new ArrayList<>();
	private final List<String> yearChipKeys = new ArrayList<>();

	private static boolean played(Map<String, Long> days, java.time.LocalDate d) {
		return days.getOrDefault(d.toString(), 0L) >= 60_000L;
	}

	/** 오늘(오늘 아직 안 했으면 어제)부터 거꾸로 이어진 접속일. */
	private static int streak(Map<String, Long> days) {
		java.time.LocalDate d = java.time.LocalDate.now();
		if (!played(days, d)) {
			d = d.minusDays(1);
		}
		int n = 0;
		while (played(days, d) && n < 3650) {
			n++;
			d = d.minusDays(1);
		}
		return n;
	}

	private static int bestStreak(Map<String, Long> days) {
		List<java.time.LocalDate> list = new ArrayList<>();
		for (Map.Entry<String, Long> e : days.entrySet()) {
			if (e.getValue() >= 60_000L) {
				try {
					list.add(java.time.LocalDate.parse(e.getKey()));
				} catch (Throwable ignored) {
				}
			}
		}
		list.sort(Comparator.naturalOrder());
		int best = 0;
		int run = 0;
		java.time.LocalDate prev = null;
		for (java.time.LocalDate d : list) {
			run = prev != null && prev.plusDays(1).equals(d) ? run + 1 : 1;
			best = Math.max(best, run);
			prev = d;
		}
		return best;
	}

	private static final String[] WEEK = {"월", "화", "수", "목", "금", "토", "일"};
	private static final java.util.Set<String> CROPS = new java.util.HashSet<>(java.util.Arrays.asList(
		"wheat", "carrots", "potatoes", "beetroots", "melon", "pumpkin", "nether_wart", "cocoa", "sugar_cane",
		"sweet_berry_bush", "torchflower_crop", "pitcher_crop", "bamboo", "cactus"));

	private static String pathOf(String id) {
		int i = id == null ? -1 : id.indexOf(':');
		return id == null ? "" : i >= 0 ? id.substring(i + 1) : id;
	}

	private static boolean isOre(String id) {
		String p = pathOf(id);
		return p.endsWith("_ore") || p.equals("ancient_debris");
	}

	private static String itemLabel(String key, LunaStats.ItemStat it) {
		return it.name != null && !it.name.isEmpty() ? it.name : pathOf(key).replace('_', ' ');
	}

	private String date(long ms) {
		if (ms <= 0) {
			return "-";
		}
		return DateTimeFormatter.ofPattern("yyyy.MM.dd").format(
			java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()));
	}

	private int renderItems(DrawContext ctx, LunaStats.Scope s, int x, int y, int w, int mouseX, int mouseY) {
		int y0 = y;
		sectionTitle(ctx, x, y, w, "아이템 " + s.items.size() + "종");
		y += 16;
		// 머리글 - 49-67차(5-11): [획득] 열을 더하고, 머리글을 누르면 그 열로 정렬한다.
		// (누를 수 있다는 걸 알리려고 지금 정렬 중인 열에 ▾를 붙이고 강조색으로 그린다)
		int[] cols = {w - 235, w - 190, w - 145, w - 100, w - 55};
		itemHeadY = y;
		itemHeadX = x;
		itemHeadCols = cols;
		String arrow = itemSortDesc ? "▾" : "▴";
		LunaDraw.text(ctx, textRenderer, itemSort == 0 ? "이름 §7(합계순 " + arrow + ")" : "이름", x, y,
			itemSort == 0 ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);
		String[] heads = {"사용", "버림", "제작", "캔", "획득"};
		for (int i = 0; i < heads.length; i++) {
			boolean on = itemSort == i + 1;
			LunaDraw.text(ctx, textRenderer, on ? heads[i] + arrow : heads[i], x + cols[i], y,
				on ? LunaDraw.ACCENT : LunaDraw.TEXT_DIM);
		}
		y += 12;
		List<Map.Entry<String, LunaStats.ItemStat>> list = new ArrayList<>(s.items.entrySet());
		Comparator<Map.Entry<String, LunaStats.ItemStat>> cmp = Comparator.comparingLong(
			(Map.Entry<String, LunaStats.ItemStat> e) -> sortValue(e.getValue()));
		list.sort(itemSortDesc ? cmp.reversed() : cmp);
		if (posterLimit > 0 && list.size() > posterLimit) {
			list = list.subList(0, posterLimit);
		}
		if (list.isEmpty()) {
			LunaDraw.text(ctx, textRenderer, "아직 기록이 없습니다", x, y, LunaDraw.TEXT_DIM);
			return y - y0 + 14;
		}
		for (Map.Entry<String, LunaStats.ItemStat> e : list) {
			LunaStats.ItemStat it = e.getValue();
			String name = it.name != null ? it.name : e.getKey();
			boolean variant = e.getKey().contains("#");
			float p = nextProgress();
			if (p <= 0.01f) {
				y += 12;
				continue;
			}
			float base = LunaDraw.alpha();
			LunaDraw.setAlpha(base * p);
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, name, cols[0] - 8), x, y,
				variant ? LunaDraw.ACCENT : LunaDraw.TEXT_SUB);
			long[] vals = {it.used, it.dropped, it.crafted, it.mined, it.picked};
			for (int i = 0; i < vals.length; i++) {
				String v = vals[i] == 0 ? "-" : animNum(vals[i], p);
				LunaDraw.text(ctx, textRenderer, v, x + cols[i], y, vals[i] == 0 ? LunaDraw.TEXT_DIM : LunaDraw.TEXT);
			}
			LunaDraw.setAlpha(base);
			y += 12;
		}
		return y - y0 + 10;
	}

	/** 49-67차(5-11): 지금 고른 기준으로 정렬할 때 쓸 값. 0이면 합계(예전 기본값). */
	private long sortValue(LunaStats.ItemStat it) {
		return switch (itemSort) {
			case 1 -> it.used;
			case 2 -> it.dropped;
			case 3 -> it.crafted;
			case 4 -> it.mined;
			case 5 -> it.picked;
			default -> it.total();
		};
	}

	/**
	 * 아이템 탭 머리글 클릭 → 그 열로 정렬. 같은 열을 다시 누르면 합계순으로 돌아간다
	 * (오름차순을 따로 두지 않은 이유: 통계에서 "가장 적게 쓴 것"을 찾는 일은 거의 없다).
	 */
	private boolean handleItemHeaderClick(double mouseX, double mouseY) {
		if (tab != TAB_ITEMS || itemHeadY == Integer.MIN_VALUE || itemHeadCols.length == 0) {
			return false;
		}
		if (mouseY < itemHeadY - 2 || mouseY > itemHeadY + 11) {
			return false;
		}
		for (int i = itemHeadCols.length - 1; i >= 0; i--) {
			if (mouseX >= itemHeadX + itemHeadCols[i] - 4) {
				int want = i + 1;
				// 49-172차: 같은 열을 다시 누르면 내림/오름차순 전환, 다른 열이면 그 열 내림차순
				if (itemSort == want) {
					itemSortDesc = !itemSortDesc;
				} else {
					itemSort = want;
					itemSortDesc = true;
				}
				restartAnim();
				return true;
			}
		}
		if (mouseX >= itemHeadX) {
			if (itemSort == 0) {
				itemSortDesc = !itemSortDesc;
			} else {
				itemSort = 0;
				itemSortDesc = true;
			}
			restartAnim();
			return true;
		}
		return false;
	}

	private int renderCounts(DrawContext ctx, Map<String, Long> map, int x, int y, int w, String empty, boolean entity) {
		int y0 = y;
		sectionTitle(ctx, x, y, w, entity ? "잡은 생명체" : "죽은 원인");
		y += 16;
		if (map.isEmpty()) {
			LunaDraw.text(ctx, textRenderer, empty, x, y, LunaDraw.TEXT_DIM);
			return y - y0 + 14;
		}
		List<Map.Entry<String, Long>> list = new ArrayList<>(map.entrySet());
		list.sort(Map.Entry.<String, Long>comparingByValue().reversed());
		if (posterLimit > 0 && list.size() > posterLimit) {
			list = list.subList(0, posterLimit);
		}
		long max = list.get(0).getValue();
		for (Map.Entry<String, Long> e : list) {
			String label = entity ? shortId(e.getKey()) : e.getKey();
			y = barRow(ctx, x, y, w, label, e.getValue(), max, "");
		}
		return y - y0 + 10;
	}

	/** 이름 · 자라나는 막대 · 숫자 한 줄. */
	private int barRow(DrawContext ctx, int x, int y, int w, String label, long value, long max, String suffix) {
		float p = nextProgress();
		if (p <= 0.01f) {
			return y + 13;
		}
		float base = LunaDraw.alpha();
		LunaDraw.setAlpha(base * p);
		LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, label, w - 130), x, y, LunaDraw.TEXT_SUB);
		int barW = 70;
		int bx = x + w - barW - 44;
		int full = (int) Math.max(2, barW * value / (double) Math.max(1, max));
		LunaDraw.roundRect(ctx, bx, y + 2, barW, 5, 2, ink(0x1A));
		LunaDraw.roundRect(ctx, bx, y + 2, Math.max(1, Math.round(full * p)), 5, 2, LunaDraw.withAlpha(LunaDraw.ACCENT, 0xB4));
		String v = animNum(value, p) + suffix;
		LunaDraw.text(ctx, textRenderer, v, x + w - LunaDraw.width(textRenderer, v), y, LunaDraw.TEXT);
		LunaDraw.setAlpha(base);
		return y + 13;
	}

	/** 상호작용 탭 - 바닐라가 세어 둔 상자 열기 · 제작대 · 낚시 · 번식 같은 것들. */
	/**
	 * 49-67차(5-13 "상호작용 안 불러와짐"): <b>원인은 우리가 '늘어난 만큼만' 더하고 있었다는 것.</b>
	 *
	 * <p>상호작용(상자 열기·제작대·낚시·번식…)은 <b>우리가 세는 값이 아니라 마인크래프트가 이미 세어 둔
	 * 값</b>이다. 그런데 동기화가 "이전에 본 값보다 늘어난 만큼"만 더하는 방식이라, 모드를 깔기 전에
	 * 쌓인 숫자는 <b>영영 안 들어왔다</b>(처음 본 순간은 기준만 잡고 넘어가므로). 그래서 상자를 천 번
	 * 열어 둔 사람에게도 0으로 보였다.
	 *
	 * <p>고침: 이 탭은 <b>지금 이 순간의 마인크래프트 통계를 그대로 읽어 보여 준다</b>. 누적 계산을
	 * 거치지 않으니 언제 열어도 맞고, 두 번 더해질 일도 없다. 월드 밖이거나 못 읽으면 그때만
	 * 저장해 둔 값으로 돌아간다.
	 */
	private Map<String, Long> liveInteractions(LunaStats.Scope s) {
		Map<String, Long> live = LunaStats.readVanillaInteractions(client);
		return live != null && !live.isEmpty() ? live : s.interactions;
	}

	private int renderInteractions(DrawContext ctx, LunaStats.Scope s, int x, int y, int w) {
		int y0 = y;
		Map<String, Long> source = liveInteractions(s);
		sectionTitle(ctx, x, y, w, "상호작용 " + source.size() + "종");
		y += 16;
		if (source.isEmpty()) {
			LunaDraw.text(ctx, textRenderer, "아직 기록이 없습니다", x, y, LunaDraw.TEXT_DIM);
			y += 12;
			LunaDraw.text(ctx, textRenderer, "마인크래프트 기본 통계에서 읽어 옵니다 (월드에 들어가면 채워집니다)", x, y, LunaDraw.TEXT_DIM);
			return y - y0 + 14;
		}
		List<Map.Entry<String, Long>> list = new ArrayList<>(source.entrySet());
		list.sort(Map.Entry.<String, Long>comparingByValue().reversed());
		if (posterLimit > 0 && list.size() > posterLimit) {
			list = list.subList(0, posterLimit);
		}
		long max = list.get(0).getValue();
		for (Map.Entry<String, Long> e : list) {
			boolean damage = LunaStats.isDamageInteraction(e.getKey());
			long shown = damage ? Math.round(e.getValue() / 10.0) : e.getValue();
			long shownMax = damage ? Math.max(1, Math.round(max / 10.0)) : max;
			y = barRow(ctx, x, y, w, e.getKey(), shown, damage ? shownMax : max, "");
		}
		return y - y0 + 10;
	}

	/** 기간 탭 - 연도별로 접히는 달 목록(플레이 시간 막대 + 그 달의 굵직한 숫자). */
	private int renderPeriods(DrawContext ctx, LunaStats.Scope s, int x, int y, int w, int mouseX, int mouseY) {
		int y0 = y;
		sectionTitle(ctx, x, y, w, "연도 | 달");
		y += 16;
		if (s.months.isEmpty()) {
			LunaDraw.text(ctx, textRenderer, "아직 기록이 없습니다", x, y, LunaDraw.TEXT_DIM);
			return y - y0 + 14;
		}
		// 연도별로 묶기
		Map<String, List<String>> byYear = new java.util.TreeMap<>(Comparator.reverseOrder());
		for (String key : s.months.keySet()) {
			byYear.computeIfAbsent(LunaStats.yearOf(key), k -> new ArrayList<>()).add(key);
		}
		long maxMonth = 1;
		for (LunaStats.Period p : s.months.values()) {
			maxMonth = Math.max(maxMonth, p.playMs);
		}
		periodRows.clear();
		yearKeys.clear();
		for (Map.Entry<String, List<String>> ye : byYear.entrySet()) {
			String year = ye.getKey();
			List<String> months = ye.getValue();
			months.sort(Comparator.reverseOrder());
			LunaStats.Period sum = new LunaStats.Period();
			for (String m : months) {
				sum.add(s.months.get(m));
			}
			boolean collapsed = collapsedYears.contains(year);
			float p = nextProgress();
			float base = LunaDraw.alpha();
			LunaDraw.setAlpha(base * p);
			// 연도 줄
			LunaDraw.roundRect(ctx, x - 2, y - 2, w + 4, 15, 4, ink(0x0F));
			LunaDraw.chevron(ctx, x + 2, y + 3, !collapsed, LunaDraw.TEXT_SUB);
			LunaDraw.text(ctx, textRenderer, year + "년", x + 12, LunaDraw.textY(y - 2, 15), LunaDraw.TEXT);
			String right = LunaStats.formatHours(sum.playMs) + " | 처치 " + num(sum.kills) + " | 죽음 " + num(sum.deaths);
			LunaDraw.text(ctx, textRenderer, right, x + w - LunaDraw.width(textRenderer, right), LunaDraw.textY(y - 2, 15), LunaDraw.ACCENT);
			LunaDraw.setAlpha(base);
			periodRows.add(new int[]{y - 2, 15});
			yearKeys.add(year);
			y += 17;
			if (collapsed) {
				continue;
			}
			for (String m : months) {
				LunaStats.Period mp = s.months.get(m);
				String label = "  " + Integer.parseInt(m.substring(5)) + "월";
				float mpp = nextProgress();
				float b2 = LunaDraw.alpha();
				LunaDraw.setAlpha(b2 * mpp);
				LunaDraw.text(ctx, textRenderer, label, x, y, LunaDraw.TEXT_SUB);
				int barW = 60;
				int bx = x + 40;
				int full = (int) Math.max(2, barW * mp.playMs / (double) maxMonth);
				LunaDraw.roundRect(ctx, bx, y + 2, barW, 5, 2, ink(0x14));
				LunaDraw.roundRect(ctx, bx, y + 2, Math.max(1, Math.round(full * mpp)), 5, 2,
					LunaDraw.withAlpha(LunaDraw.ACCENT, 0x9E));
				String time = LunaStats.formatHours(mp.playMs);
				LunaDraw.text(ctx, textRenderer, time, bx + barW + 8, y, LunaDraw.TEXT);
				String detail = "죽음 " + num(mp.deaths) + " | 처치 " + num(mp.kills) + " | 캠 " + num(mp.mined)
					+ " | " + LunaStats.formatDistance(mp.distance);
				LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, detail, w - (bx + barW + 8 - x) - 44),
					x + w - Math.min(LunaDraw.width(textRenderer, detail), w - (bx + barW + 8 - x) - 44), y, LunaDraw.TEXT_DIM);
				LunaDraw.setAlpha(b2);
				y += 13;
			}
			y += 4;
		}
		return y - y0 + 10;
	}

	/** 기간 탭에서 클릭 판정을 위한 연도 줄 위치(그릴 때 채움): {y, 높이} + 연도 키. */
	private final List<int[]> periodRows = new ArrayList<>();
	private final List<String> yearKeys = new ArrayList<>();

	/**
	 * 49-32차(사용자: "잡은 생명체 이런 게 영어로 뜨는데 한글로 해줘"):
	 * 엔티티 id(minecraft:zombie)를 게임 언어 이름(좀비)으로. 번역이 없으면 예전처럼 다듬은 영어.
	 */
	private static String shortId(String id) {
		int i = id.indexOf(':');
		String namespace = i >= 0 ? id.substring(0, i) : "minecraft";
		String path = i >= 0 ? id.substring(i + 1) : id;
		String key = "entity." + namespace + "." + path;
		String translated = LunaCompat.translate(key);
		if (translated != null && !translated.isEmpty() && !translated.equals(key)) {
			return translated;
		}
		return path.replace('_', ' ');
	}

	private int renderTravel(DrawContext ctx, LunaStats.Scope s, int x, int y, int w) {
		int y0 = y;
		sectionTitle(ctx, x, y, w, "이동");
		y += 16;
		double total = s.totalDistance();
		statRow(ctx, x, y, w, "전체", LunaStats.formatDistance(total), true);
		y += 15;
		for (String mode : LunaStats.MODES) {
			double d = s.distance.getOrDefault(mode, 0d);
			long ms = s.travelMs.getOrDefault(mode, 0L);
			if (d <= 0 && ms <= 0) {
				continue;
			}
			statRow(ctx, x, y, w, LunaStats.modeName(mode),
				LunaStats.formatDistance(d) + " | " + LunaStats.formatDuration(ms), false);
			y += 13;
		}
		return y - y0 + 10;
	}

	// ==================== 아래 줄(저장/불러오기) ====================

	private record FootButton(String label, int x, int w) {
	}

	private List<FootButton> footButtons() {
		List<FootButton> out = new ArrayList<>();
		String[] labels = {"이미지로 저장", "파일로 저장", "불러오기", "폴더 열기"};
		int x = px + pw - 10;
		for (int i = labels.length - 1; i >= 0; i--) {
			int w = LunaDraw.width(textRenderer, labels[i]) + 16;
			x -= w;
			out.add(0, new FootButton(labels[i], x, w));
			x -= 5;
		}
		return out;
	}

	private void renderFooter(DrawContext ctx, int mouseX, int mouseY) {
		int y = py + ph - FOOT_H;
		LunaDraw.fadeLine(ctx, px + 1, y, pw - 2, LunaDraw.ACCENT);
		int by = y + (FOOT_H - 16) / 2;
		LunaDraw.text(ctx, textRenderer, "기록은 내 컴퓨터에만 저장됩니다", px + 12, LunaDraw.textY(y, FOOT_H), LunaDraw.TEXT_DIM);
		for (FootButton b : footButtons()) {
			boolean hov = LunaDraw.in(mouseX, mouseY, b.x(), by, b.w(), 16);
			LunaDraw.button3d(ctx, textRenderer, b.x(), by, b.w(), 16, b.label(), LunaDraw.B_NEUTRAL, hov ? 1f : 0f);   // 49-227차
		}
	}

	// ==================== 저장 ====================

	private Path exportDir() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("stats-export");   // 49-212차: 폴더 이름에 루나/노바 없음(런처가 옛 폴더를 옮김)
	}

	/**
	 * 49-67차(5-13 "이미지 저장 위치"): 알림에 <b>게임 폴더 기준 짧은 경로</b>만 보여 준다.
	 * 예전엔 절대 경로를 통째로 찍어서 알림 상자를 넘치고 정작 "어디에 생겼는지"는 안 읽혔다.
	 * 정확히 어디인지 알고 싶으면 아래 <b>[폴더 열기]</b>가 그 폴더를 바로 연다.
	 */
	private String shortPath(Path file) {
		try {
			Path gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
			return gameDir.relativize(file).toString();
		} catch (Throwable ignored) {
			return file.getFileName().toString();
		}
	}

	private String stamp() {
		return DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss").format(LocalDateTime.now());
	}

	/**
	 * 지금 화면을 그대로 이미지로.
	 *
	 * <h3>49-73차 — 여기가 계속 안 되던 진짜 이유</h3>
	 * 49-32차부터 "통계 이미지 저장이 안 된다"가 반복됐는데, 그때 붙인 폴백(바닐라 스크린샷 저장을
	 * 빌려 쓰기)도 최신 버전에서는 듣지 않았다. 원인을 이번에 <b>javap로 끝까지</b> 봤다.
	 *
	 * <table>
	 *   <tr><th></th><th>~1.20.6</th><th>1.21.5+</th></tr>
	 *   <tr><td>{@code takeScreenshot}</td><td>NativeImage를 <b>돌려줌</b></td>
	 *       <td><b>Consumer를 나중에 부름</b>(비동기)</td></tr>
	 *   <tr><td>{@code saveScreenshot}</td><td>(File, String, Framebuffer, Consumer)</td>
	 *       <td>1.21.8+는 <b>(File, String, Framebuffer, int, Consumer)</b></td></tr>
	 * </table>
	 *
	 * 그래서 새 버전에서는 ① 픽셀을 긁는 쪽이 <b>언제나 null</b>이고 ② 폴백은 <b>이름을 우리가 정하지
	 * 못하는 3인자 판</b>에 걸려, 파일은 바닐라 이름으로 screenshots에 생기는데 우리는 luna-stats로
	 * 옮기려 해서 <b>옮기기가 조용히 실패</b>했다. 화면에는 "저장했습니다"가 뜨는데 <b>그 자리에 파일이
	 * 없었던 것</b>이다.
	 *
	 * <p>이제 {@link LunaCompat#captureFramebufferAsync}로 <b>받는 쪽을 콜백으로</b> 맞춘다 -
	 * 옛 판은 그 자리에서, 새 판은 읽기가 끝난 뒤 그림이 온다. 그림이 <b>실제로 손에 들어온 다음에만</b>
	 * "저장했습니다"라고 말한다.
	 */
	private void saveImage() {
		try {
			Path dir = exportDir();
			java.nio.file.Files.createDirectories(dir);
			Path file = dir.resolve("stats_" + stamp() + ".png");
			shotFile = file;
			shotDone = false;
			fallbackAt = 0;
			shotStartedAt = System.currentTimeMillis();
			boolean started = LunaCompat.captureFramebufferAsync(client, image -> {
				// 이 콜백은 렌더 스레드에서 온다. PNG 굽기는 수십 ms지만 사용자가 한 번 누른 일이라
				// 여기서 끝내는 쪽이 낫다 - 다른 스레드로 넘기면 채팅·알림을 건드릴 때가 위험하다.
				try {
					LunaCompat.writeImage(image, file.toFile());
					// 49-230차: 파일이 실제로 생겼을 때만 "저장했습니다"(아니면 tickShot이 바닐라 저장으로 다시 시도)
					if (java.nio.file.Files.isRegularFile(file) && java.nio.file.Files.size(file) > 0 && file.equals(shotFile)) {
						announce("이미지로 저장했습니다 | " + shortPath(file) + "  §7([폴더 열기]로 확인)");
						shotDone = true;
					}
				} catch (Throwable ex) {
					LunaCompat.warnOnce("stats:image:write", ex);   // 49-230차: 실패하면 tickShot이 2.5초 뒤 바닐라 저장으로
				} finally {
					try {
						image.close();
					} catch (Throwable ignored) {
					}
				}
			});
			if (!started) {
				shotStartedAt = 0;   // 49-230차: 화면 읽기가 아예 안 되면 곧바로 바닐라 저장으로(다음 틱)
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:image", t);
			announce("§c이미지를 저장하지 못했습니다 (" + t.getClass().getSimpleName() + ")");
		}
	}

	private void saveFile() {
		try {
			Path file = exportDir().resolve("stats_" + stamp() + ".json");
			LunaStats.exportTo(client, file);
			announce("파일로 저장했습니다 | " + shortPath(file) + "  §7([폴더 열기]로 확인)");
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:export", t);
			notice("저장하지 못했습니다");
		}
	}

	/**
	 * 49-53차(5-12): 예전에는 폴더에서 <b>가장 최근 파일을 알아서 골라 곧바로 합쳤다</b>. 그래서
	 * 누를 때마다 기록이 불어났고 어느 파일이 들어가는지도 알 수 없었다. 이제 <b>파일을 고르고</b>,
	 * 무엇이 어떻게 되는지 확인한 뒤에야 덮어쓴다.
	 */
	private void importFile() {
		try {
			Path dir = exportDir();
			List<Path> found = new ArrayList<>();
			if (java.nio.file.Files.isDirectory(dir)) {
				try (java.util.stream.Stream<Path> files = java.nio.file.Files.list(dir)) {
					for (Path p : files.toList()) {
						if (p.getFileName().toString().endsWith(".json")) {
							found.add(p);
						}
					}
				}
			}
			if (found.isEmpty()) {
				notice("stats-export 폴더에 백업 파일이 없습니다");
				return;
			}
			found.sort(Comparator.comparingLong(p -> -p.toFile().lastModified()));   // 최근 것부터
			importList = found;
			importPending = null;
			importPendingInfo = null;
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:import", t);
			notice("불러오지 못했습니다");
		}
	}

	private void closeImport() {
		importList = null;
		importPending = null;
		importPendingInfo = null;
	}

	/** 확인까지 끝난 뒤 실제 덮어쓰기. */
	private void doReplace() {
		Path file = importPending;
		closeImport();
		if (file == null) {
			return;
		}
		announce(LunaStats.replaceFrom(client, file));
		worlds = LunaStats.worlds();
		scopeIndex = 0;
		restartAnim();
	}

	// ==================== 불러오기 창 ====================

	private static final int IMP_ROW_H = 20;

	private int impW() {
		return Math.min(pw - 40, 430);
	}

	private int impRows() {
		return importList == null ? 0 : Math.min(importList.size(), 7);
	}

	private int impH() {
		return 30 + impRows() * IMP_ROW_H + (importPending == null ? 34 : 50);
	}

	private int impX() {
		return px + (pw - impW()) / 2;
	}

	private int impY() {
		return py + (ph - impH()) / 2;
	}

	private void renderImport(DrawContext ctx, int mouseX, int mouseY) {
		int w = impW();
		int h = impH();
		int x = impX();
		int y = impY();
		ctx.fill(px, py, px + pw, py + ph, 0xB4000000);
		LunaDraw.panel3d(ctx, x, y, w, h, 6);   // 49-227차
		LunaDraw.textBold(ctx, textRenderer, "불러올 파일 고르기", x + 12, LunaDraw.textY(y, 28), LunaDraw.TEXT);

		int ry = y + 28;
		for (int i = 0; i < impRows(); i++) {
			Path p = importList.get(i);
			boolean sel = p.equals(importPending);
			boolean hov = LunaDraw.in(mouseX, mouseY, x + 8, ry, w - 16, IMP_ROW_H);
			if (sel || hov) {
				LunaDraw.roundRect(ctx, x + 8, ry, w - 16, IMP_ROW_H, 4,
					sel ? LunaDraw.withAlpha(LunaDraw.ACCENT, 0x2E) : 0x14FFFFFF);
			}
			String name = p.getFileName().toString();
			String info = LunaStats.previewOf(p);
			int infoW = info == null ? 0 : LunaDraw.width(textRenderer, info);
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, name, w - 32 - infoW),
				x + 14, LunaDraw.textY(ry, IMP_ROW_H), sel ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
			if (info != null) {
				LunaDraw.text(ctx, textRenderer, info, x + w - 14 - infoW,
					LunaDraw.textY(ry, IMP_ROW_H), LunaDraw.TEXT_DIM);
			}
			ry += IMP_ROW_H;
		}

		int by = y + h - 24;
		if (importPending == null) {
			LunaDraw.text(ctx, textRenderer, "고른 파일로 전체 기록을 덮어씁니다", x + 12,
				LunaDraw.textY(by, 16), LunaDraw.TEXT_DIM);
			drawImpButton(ctx, x + w - 12 - 54, by, 54, "닫기", mouseX, mouseY, false);
		} else {
			String head = importPendingInfo == null ? "지금 기록을 전부 지우고 이 파일로 바꿉니다"
					: "지금 기록을 전부 지우고 이 파일(" + importPendingInfo + ")로 바꿉니다";
			LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, head, w - 24), x + 12,
				LunaDraw.textY(by - 16, 16), 0xFFF0A63C);
			LunaDraw.text(ctx, textRenderer, "지금 기록은 덮어쓰기 전에 자동으로 백업됩니다", x + 12,
				LunaDraw.textY(by, 16), LunaDraw.TEXT_DIM);
			drawImpButton(ctx, x + w - 12 - 54, by, 54, "취소", mouseX, mouseY, false);
			drawImpButton(ctx, x + w - 12 - 54 - 6 - 64, by, 64, "덮어쓰기", mouseX, mouseY, true);
		}
	}

	private void drawImpButton(DrawContext ctx, int x, int y, int w, String label,
			int mouseX, int mouseY, boolean danger) {
		boolean hov = LunaDraw.in(mouseX, mouseY, x, y, w, 16);
		// 49-227차: 공용 입체 버튼(지우기 같은 위험한 건 빨강)
		LunaDraw.button3d(ctx, textRenderer, x, y, w, 16, label, danger ? LunaDraw.B_DANGER : LunaDraw.B_NEUTRAL, hov ? 1f : 0f);
	}

	/** 불러오기 창이 떠 있을 때의 클릭. 창이 먹었으면 true. */
	private boolean importClicked(double mouseX, double mouseY) {
		int w = impW();
		int h = impH();
		int x = impX();
		int y = impY();
		int ry = y + 28;
		for (int i = 0; i < impRows(); i++) {
			if (LunaDraw.in(mouseX, mouseY, x + 8, ry, w - 16, IMP_ROW_H)) {
				Path p = importList.get(i);
				if (!LunaStats.isMine(client, p)) {
					notice("다른 계정의 백업입니다");
					return true;
				}
				importPending = p;
				importPendingInfo = LunaStats.previewOf(p);
				return true;
			}
			ry += IMP_ROW_H;
		}
		int by = y + h - 24;
		if (LunaDraw.in(mouseX, mouseY, x + w - 12 - 54, by, 54, 16)) {   // 닫기 / 취소
			if (importPending != null) {
				importPending = null;      // 고른 것만 무르고 목록은 유지
			} else {
				closeImport();
			}
			return true;
		}
		if (importPending != null && LunaDraw.in(mouseX, mouseY, x + w - 12 - 54 - 6 - 64, by, 64, 16)) {
			doReplace();
			return true;
		}
		// 창 밖을 누르면 닫기
		if (!LunaDraw.in(mouseX, mouseY, x, y, w, h)) {
			closeImport();
		}
		return true;
	}

	private void openFolder() {
		try {
			Path dir = exportDir();
			java.nio.file.Files.createDirectories(dir);
			net.minecraft.util.Util.getOperatingSystem().open(dir.toUri());
		} catch (Throwable t) {
			LunaCompat.warnOnce("stats:folder", t);
			notice("폴더를 열지 못했습니다");
		}
	}

	// ==================== 입력 ====================

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		if (importList != null) {
			return importClicked(mouseX, mouseY);
		}
		layout();
		// 닫기
		if (LunaDraw.in(mouseX, mouseY, rightEdge - 18, TOP_Y + 2, 20, 20)) {
			close();
			return true;
		}
		// 탭
		for (int i = 0; i < TABS.length; i++) {
			int tx = tabX(i);
			if (LunaDraw.in(mouseX, mouseY, tx - 4, TABS_Y - 2, gWidth(TABS[i]) + 8, 18)) {
				tab = i;
				scroll = 0;
				scrollTarget = 0;
				restartAnim();
				return true;
			}
		}
		if (mouseX < dividerX) {
			// 범위 목록(그리기와 같은 순서·간격)
			int y = scopeTop();
			int bottom = linksTop() - 8;
			for (int i = 0; i <= worlds.size(); i++) {
				if (y + SCOPE_H > bottom) {
					break;
				}
				if (LunaDraw.in(mouseX, mouseY, railX - 4, y, railW, SCOPE_H)) {
					scopeIndex = i;
					scroll = 0;
					scrollTarget = 0;
					restartAnim();
					return true;
				}
				y += SCOPE_H + (i == 0 ? 8 : 0);
			}
			// 저장 링크
			int ly = linksTop();
			for (String l : LINKS) {
				if (LunaDraw.in(mouseX, mouseY, railX + 6, ly, railW - 16, 14)) {
					switch (l) {
						case "전체 요약 저장" -> {
							// 49-67차(5-13): 화면을 "포스터"로 바꿔 그린 뒤 몇 프레임 뒤에 찍는다(captureIn 주석 참고).
							poster = true;
							posterLimit = 12;
							captureIn = 3;
						}
						case "이 화면 저장" -> {
							// 49-172차: 보고 있는 화면 그대로(탭·범위·스크롤 그대로)
							poster = false;
							captureIn = 3;
						}
						case "파일로 저장" -> saveFile();
						case "불러오기" -> importFile();
						default -> openFolder();
					}
					return true;
				}
				ly += 14;
			}
			int ey = height - 24;
			if (LunaDraw.in(mouseX, mouseY, railX + 6, ey - 2, gWidth("ESC 닫기") + 4, 13)) {
				close();
			}
			return true;
		}
		// 기간 탭: 연도 줄을 누르면 접기/펴기
		if (tab == TAB_PERIOD) {
			for (int i = 0; i < yearChips.size() && i < yearChipKeys.size(); i++) {
				int[] r = yearChips.get(i);
				if (LunaDraw.in(mouseX, mouseY, r[0], r[1], r[2], r[3])) {
					periodYear = yearChipKeys.get(i);
					restartAnim();
					return true;
				}
			}
			for (int i = 0; i < periodRows.size() && i < yearKeys.size(); i++) {
				int[] r = periodRows.get(i);
				if (LunaDraw.in(mouseX, mouseY, cx0, r[0], cw0, r[1])) {
					String year = yearKeys.get(i);
					if (!collapsedYears.remove(year)) {
						collapsedYears.add(year);
					}
					restartAnim();
					return true;
				}
			}
		}
		// 49-67차(5-11): 아이템 탭 머리글 정렬
		if (handleItemHeaderClick(mouseX, mouseY)) {
			return true;
		}
		// 아래 버튼(49-143차: 왼쪽 저장 링크로 옮김 - 이 자리는 더 이상 그리지 않는다)
		int by = Integer.MIN_VALUE / 2;
		for (FootButton b : footButtons()) {
			if (LunaDraw.in(mouseX, mouseY, b.x(), by, b.w(), 16)) {
				switch (b.label()) {
					case "이미지로 저장" -> {
						// 49-67차(5-13 "전체 통계를 한 장에"): 보고 있던 탭만 찍지 않고,
						// 화면을 "포스터"로 바꿔 그린 뒤 그 화면을 찍는다.
						// 49-73차: 그린 **그 프레임**이 아니라 몇 프레임 뒤에 찍는다(captureIn 주석 참고).
						poster = true;
						posterLimit = 12;
						captureIn = 3;
					}
					case "파일로 저장" -> saveFile();
					case "불러오기" -> importFile();
					default -> openFolder();
				}
				return true;
			}
		}
		return true;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double verticalAmount) {
		scrollTarget = Math.max(0, Math.min(maxScroll, scrollTarget - (float) verticalAmount * 24f));
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (importList != null) {
				closeImport();
				return true;
			}
			close();
			return true;
		}
		return false;
	}

	@Override
	public void close() {
		LunaStats.saveNow();
		kr.lunaslight.mod.util.LunaCompat.setScreen(parent);
	}

	/**
	 * 49-32차(사용자: "저장되면 됐다고 메시지 좀 띄워줘"): 화면 안내와 채팅 양쪽에 남긴다.
	 * 화면을 닫은 뒤에도 채팅에 기록이 남아 저장 여부를 확인할 수 있다.
	 */
	private void announce(String message) {
		notice(message);
		try {
			LunaCompat.printLocalMessage(client, "§a[Nova] §f" + message);
		} catch (Throwable ignored) {
		}
	}
}
