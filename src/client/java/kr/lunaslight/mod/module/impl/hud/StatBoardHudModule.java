package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.gui.LunaIcons;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import kr.lunaslight.mod.util.EntityPos;
import kr.lunaslight.mod.util.WindowAccess;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

/**
 * 49-323차(사용자 사진 - 보유머니/빚 큰 칸 둘, 아래 작은 칸 셋인 상태판 + "이런 느낌의 HUD, FPS랑 CPS 이런 것들 내가 원하는 거 5가지 띄울 수
 * 있는 기능 따로 하나", "인게임 UI가 있는 사람만 가능하고 인게임 UI를 따라"): <b>상태판</b>.
 *
 * <p>위에 큰 칸 둘(아이콘 동그라미 + 작은 이름 + 큰 값), 아래 작은 알약 칸 셋(아이콘 + 이름 + 값). 칸마다 무엇을 띄울지 고른다(없음이면 그 칸을 뺀다).
 * 색은 지금 쓰는 인게임 UI(크림, 미드나잇, 네온 사이버)의 판/카드/테두리/글자/포인트 색을 그대로 쓴다(LunaTheme). 인게임 UI를 안 쓰면(기본)
 * 아무것도 안 그린다 - 미리보기와 HUD 편집기에서만 "인게임 UI가 있어야 보입니다"를 띄운다.
 *
 * <p>CPS는 누적 클릭 수의 차이로 센다(CPS HUD가 비우는 계수기를 같이 쓰면 서로 뺏는다).
 */
public class StatBoardHudModule extends Module {

	public enum Stat {
		NONE("없음", "", ""),
		FPS("FPS", "", ""),
		CPS("CPS", "", ""),
		PING("핑", "", "ms"),
		COORDS("좌표", "", ""),
		DIRECTION("방향", "", ""),
		BIOME("바이옴", "", ""),
		CLOCK("시각", "", ""),
		GAME_TIME("게임 시간", "", ""),
		MEMORY("메모리", "", "%"),
		SPEED("속도", "", "m/s"),
		PLAYTIME("플레이타임", "", ""),
		LEVEL("레벨", "", "");

		final String label;
		final String icon;
		final String unit;

		Stat(String label, String icon, String unit) {
			this.label = label;
			this.icon = icon;
			this.unit = unit;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum Corner {
		TOP_LEFT("왼쪽 위"), TOP_RIGHT("오른쪽 위");

		final String label;

		Corner(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final EnumSetting<Stat>[] slots;
	private final EnumSetting<Corner> corner;

	// ---- 값 ----
	private final java.util.ArrayDeque<Long> leftTimes = new java.util.ArrayDeque<>();
	private final java.util.ArrayDeque<Long> rightTimes = new java.util.ArrayDeque<>();
	private long lastLeft = -1;
	private long lastRight = -1;
	private double lastX, lastZ;
	private boolean haveLast;
	private double speed;
	private int fallbackFps;
	private int frames;
	private long windowStart;
	private static final long START = System.currentTimeMillis();

	@SuppressWarnings("unchecked")
	public StatBoardHudModule() {
		super("stat_board", "상태판", ModuleCategory.HUD, "원하는 정보 5가지를 카드로 (인게임 UI 전용)");
		// 49-337차(사용자: "상태판에 인게임 UI ~~ 이거 치워 주고"): 안내 줄 삭제
		slots = new EnumSetting[5];
		String[] names = {"큰 칸 1", "큰 칸 2", "작은 칸 1", "작은 칸 2", "작은 칸 3"};
		Stat[] defs = {Stat.FPS, Stat.CPS, Stat.PING, Stat.DIRECTION, Stat.PLAYTIME};
		for (int i = 0; i < 5; i++) {
			slots[i] = register(new EnumSetting<>("slot" + (i + 1), names[i], "이 칸에 띄울 정보입니다. 없음이면 칸을 뺍니다.", defs[i], Stat.class));
		}
		// 49-337차(사용자: "켜면 무조건 왼쪽 위 고정, 위치는 오른쪽 위랑 왼쪽 위만, 오른쪽이면 모양 반대"): 자유 위치 대신 둘 중 하나
		corner = register(new EnumSetting<>("corner", "자리", "왼쪽 위 또는 오른쪽 위에 붙습니다. 오른쪽이면 모양이 거울처럼 뒤집힙니다.", Corner.TOP_LEFT, Corner.class));
		settingsPage(kr.lunaslight.mod.module.SettingsPage.INGAME_UI);   // 49-328차: [일반] 아래 [인게임 UI] 페이지
	}

	/** 인게임 UI를 쓰는 중인가(가졌고, 착용했고, [코스메틱]에서 끄지 않음). */
	private static boolean unlocked() {
		return LunaTheme.skin() != LunaTheme.Skin.DEFAULT;
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	// ==================== 값 모으기 ====================

	@Override
	public void onTick() {
		long now = System.currentTimeMillis();
		LunaCompat.ensureMouseClickCounter();
		long l = LunaCompat.totalLeftClicks();
		long r = LunaCompat.totalRightClicks();
		if (lastLeft >= 0) {
			for (long i = lastLeft; i < l; i++) {
				leftTimes.addLast(now);
			}
			for (long i = lastRight; i < r; i++) {
				rightTimes.addLast(now);
			}
		}
		lastLeft = l;
		lastRight = r;
		while (!leftTimes.isEmpty() && now - leftTimes.peekFirst() > 1000) {
			leftTimes.removeFirst();
		}
		while (!rightTimes.isEmpty() && now - rightTimes.peekFirst() > 1000) {
			rightTimes.removeFirst();
		}
		if (client != null && client.player != null) {
			double x = EntityPos.x(client.player), z = EntityPos.z(client.player);
			if (haveLast) {
				double d = Math.hypot(x - lastX, z - lastZ) * 20.0;   // 틱당 → 초당
				speed = speed * 0.7 + d * 0.3;
			}
			lastX = x;
			lastZ = z;
			haveLast = true;
		} else {
			haveLast = false;
			speed = 0;
		}
	}

	private int fps() {
		try {
			java.lang.reflect.Method m = LunaCompat.getMethodCompat(net.minecraft.client.MinecraftClient.class, "getCurrentFps");
			return (int) m.invoke(client);
		} catch (Throwable t) {
			long now = System.nanoTime();
			if (windowStart == 0) {
				windowStart = now;
			}
			frames++;
			if (now - windowStart >= 1_000_000_000L) {
				fallbackFps = (int) (frames * 1_000_000_000L / (now - windowStart));
				frames = 0;
				windowStart = now;
			}
			return fallbackFps;
		}
	}

	/** 그 칸의 값(단위는 따로). 모르면 "--". */
	private String value(Stat s, boolean sample) {
		if (sample) {
			return switch (s) {
				case FPS -> "144";
				case CPS -> "8 / 3";
				case PING -> "42";
				case COORDS -> "128, 64, -32";
				case DIRECTION -> "북동";
				case BIOME -> "plains";
				case CLOCK -> "21:30";
				case GAME_TIME -> "06:00";
				case MEMORY -> "41";
				case SPEED -> "4.3";
				case PLAYTIME -> "1:24:05";
				case LEVEL -> "30";
				default -> "";
			};
		}
		try {
			switch (s) {
				case FPS:
					return String.valueOf(fps());
				case CPS:
					return leftTimes.size() + " / " + rightTimes.size();
				case PING: {
					if (client.getNetworkHandler() == null || client.player == null) {
						return "--";
					}
					net.minecraft.client.network.PlayerListEntry e = client.getNetworkHandler().getPlayerListEntry(client.player.getUuid());
					return e == null ? "--" : String.valueOf(e.getLatency());
				}
				case COORDS: {
					net.minecraft.util.math.BlockPos p = client.player.getBlockPos();
					return p.getX() + ", " + p.getY() + ", " + p.getZ();
				}
				case DIRECTION: {
					float yaw = LunaCompat.getYaw(client.player);
					String[] d = {"남", "남서", "서", "북서", "북", "북동", "동", "남동"};
					return d[(int) Math.floorMod(Math.round(((yaw % 360f) + 360f) % 360f / 45f), 8)];
				}
				case BIOME: {
					String b = LunaCompat.getBiomeName(client.world, client.player.getBlockPos());
					return b == null ? "--" : b;
				}
				case CLOCK:
					return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
				case GAME_TIME: {
					Object t = null;
					for (String n : new String[]{"getOverworldClockTime", "getDayTime", "getTimeOfDay"}) {
						t = LunaCompat.callNoArg(client.world, n);
						if (t instanceof Long) {
							break;
						}
					}
					if (!(t instanceof Long)) {
						return "--";
					}
					long day = Math.floorMod((Long) t, 24000L);
					int h = (int) ((day / 1000 + 6) % 24);
					int m = (int) (day % 1000 * 60 / 1000);
					return String.format(java.util.Locale.ROOT, "%02d:%02d", h, m);
				}
				case MEMORY: {
					Runtime rt = Runtime.getRuntime();
					long used = rt.totalMemory() - rt.freeMemory();
					return String.valueOf(Math.round(used * 100.0 / rt.maxMemory()));
				}
				case SPEED:
					return String.format(java.util.Locale.ROOT, "%.1f", speed);
				case PLAYTIME: {
					long sec = (System.currentTimeMillis() - START) / 1000;
					try {
						sec = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
					} catch (Throwable ignored) {
					}
					long h = sec / 3600, m = sec / 60 % 60, ss = sec % 60;
					return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, ss)
							: String.format(java.util.Locale.ROOT, "%d:%02d", m, ss);
				}
				case LEVEL:
					return String.valueOf(client.player.experienceLevel);
				default:
					return "";
			}
		} catch (Throwable t) {
			return "--";
		}
	}

	// ==================== 그리기 ====================
	// 49-336차(사용자: "제대로 오랫동안 해도 되니까 제대로, 인게임에서 똑같거나 완전 비슷한 수준까지"): 사진을 픽셀 단위로 재서 그대로.
	// 사진(1px = 1칸)에서 잰 것:
	//   - 위 띠 25칸, 아래 띠 19칸, 사이 틈 없음. 둘 다 같은 폭. 왼쪽은 화면 끝에 붙어 각지고 오른쪽 끝은 반원(반지름 = 높이/2).
	//   - 테두리 없음. 띠마다 위에 1칸 밝은 줄(속보다 밝게), 아래에 1칸 어두운 줄만(살짝 입체).
	//   - 큰 칸: 큰 아이콘(왼쪽 5칸, 세로 가운데) + 위 아주 작은 이름(0.6배, y+3) + 굵은 값(1배, y+12) + 작은 단위(0.6배).
	//   - 작은 칸: 작은 아이콘 + 이름(0.8배) + 굵은 값(0.8배) + 단위(0.6배), 세로 가운데 한 줄.
	//   - 칸 사이는 세로 점선(위아래 5칸 띄움). 칸은 띠 폭을 정확히 n등분.
	// 색은 인게임 UI 팔레트: 위 띠 = 카드에 포인트 색 30%(크림이면 복숭아), 아래 띠 = 카드에 초록 16%(연두). 아이콘/글자는 기능 아이콘, 본문색.

	// 49-342차(사용자: "위아래 말고 같은 라인에, 크기 조금만 줄여"): 큰 칸도 이름과 값을 한 줄에. 띠 25/19 → 21/16.
	private static final int TOP_H = 21;
	private static final int BOT_H = 16;
	// 49-342차(사용자 사진, GUI 2: "글씨가 아예 안 보이는데 글 크기 조절 좀"): 0.6/0.8배로 줄인 글자가 GUI 2에서 흐리고 작아 안 읽혔다.
	// 전부 1배(픽셀 그대로, 선명)로. 이름/단위는 흐린 색, 값은 굵게 해서 구분한다.
	private static final float LABEL_K = 1f;
	private static final float SMALL_K = 1f;

	public void onHudRender(DrawContext ctx, RenderTickCounter tick) {
		boolean sample = isPreview() || client.player == null;
		TextRenderer font = client.textRenderer;
		int sw = WindowAccess.of(client).getScaledWidth();
		int sh = WindowAccess.of(client).getScaledHeight();
		if (!unlocked()) {
			if (isPreview()) {
				String msg = "인게임 UI가 있어야 보입니다";
				int w = LunaDraw.width(font, msg) + 12;
				int x = corner.get() == Corner.TOP_RIGHT ? sw - w : 0;
				int y = 0;
				LunaDraw.roundRect(ctx, x, y, w, 14, 4, 0xC0101215);
				LunaDraw.text(ctx, font, msg, x + 6, y + 3, 0xFFB0B6BE);
			}
			return;
		}
		java.util.List<Stat> bigs = new java.util.ArrayList<>();
		java.util.List<Stat> small = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) {
			Stat st = slots[i].get();
			if (st != Stat.NONE) {
				(i < 2 ? bigs : small).add(st);
			}
		}
		if (bigs.isEmpty() && small.isEmpty()) {
			return;
		}
		String[] bigV = new String[bigs.size()];
		String[] smallV = new String[small.size()];
		int topNeed = 0, botNeed = 0;
		for (int i = 0; i < bigs.size(); i++) {
			bigV[i] = value(bigs.get(i), sample);
			topNeed = Math.max(topNeed, bigNeed(font, bigs.get(i), bigV[i]));
		}
		for (int i = 0; i < small.size(); i++) {
			smallV[i] = value(small.get(i), sample);
			botNeed = Math.max(botNeed, smallNeed(font, small.get(i), smallV[i]));
		}
		// 칸을 n등분하므로 가장 넓은 칸 × n. 오른쪽 반원 안에는 글자를 안 놓으므로 그만큼 더.
		int w = Math.max(bigs.isEmpty() ? 0 : topNeed * bigs.size() + TOP_H / 2, small.isEmpty() ? 0 : botNeed * small.size() + BOT_H / 2);
		int h = (bigs.isEmpty() ? 0 : TOP_H) + (small.isEmpty() ? 0 : BOT_H);
		// 49-337차: 왼쪽 위 또는 오른쪽 위에 딱 붙는다(오른쪽이면 거울 모양)
		boolean mirror = corner.get() == Corner.TOP_RIGHT;
		// 49-357차(사용자: "왼쪽 위 HUD 묶음을 화면의 1/4 길이 정도로 줄이고 크기 자체를 거기에 맞춰"): 폭이 화면 폭의 1/4이 되게
		// 묶음 전체(띠 높이, 아이콘, 글자)를 같은 배율로 키우거나 줄인다. 붙은 모서리를 기준으로 늘린다. 배율을 못 쓰는 버전은 그대로.
		float k = Math.max(0.5f, Math.min(4f, (sw / 4f) / Math.max(1, w)));
		boolean scaled = Math.abs(k - 1f) > 0.01f && LunaCompat.guiTransformSupported(ctx);
		if (scaled) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, mirror ? sw - w * k : 0f, 0f);
			LunaCompat.guiScale(ctx, k, k);
		}
		try {
			drawBoard(ctx, font, scaled ? 0 : (mirror ? sw - w : 0), w, mirror, bigs, small, bigV, smallV);
		} finally {
			if (scaled) {
				LunaCompat.guiPop(ctx);
			}
		}
	}

	private void drawBoard(DrawContext ctx, TextRenderer font, int x, int w, boolean mirror, java.util.List<Stat> bigs, java.util.List<Stat> small,
			String[] bigV, String[] smallV) {
		int y = 0;
		boolean flushLeft = !mirror, flushRight = mirror;

		// 49-337차(사용자: "테마색 말고 인게임 UI 색을 따라가게 - 크림이면 크림색이 나와야지"): 포인트 색(크림은 초록)을 섞지 않고
		// 스킨 팔레트의 판/카드 색 그대로. 위 띠 = 판 색을 테두리 색 쪽으로 조금 진하게, 아래 띠 = 카드 색. 아이콘/글자는 본문색.
		boolean light = LunaTheme.light();
		int panel = 0xFF000000 | (LunaTheme.PANEL & 0x00FFFFFF);
		int card = 0xFF000000 | (LunaTheme.CARD & 0x00FFFFFF);
		int line = 0xFF000000 | (LunaTheme.CARD_BORDER & 0x00FFFFFF);
		int topFill = LunaDraw.lerpColor(panel, line, light ? 0.35f : 0.25f);
		int botFill = card;
		int accent = 0xFF000000 | (LunaTheme.TEXT_SUB & 0x00FFFFFF);
		int divider = 0x55000000 | (LunaTheme.TEXT & 0x00FFFFFF);

		int cy = y;
		if (!bigs.isEmpty()) {
			bar(ctx, x, cy, w, TOP_H, topFill, light, mirror, flushLeft || flushRight);
			int cx = mirror ? x + TOP_H / 2 : x;
			int cellW = (w - TOP_H / 2) / bigs.size();
			for (int i = 0; i < bigs.size(); i++) {
				if (i > 0) {
					dots(ctx, cx, cy + 5, TOP_H - 10, divider);
				}
				drawBig(ctx, font, cx, cy, bigs.get(i), bigV[i], accent);
				cx += cellW;
			}
			cy += TOP_H;
		}
		if (!small.isEmpty()) {
			bar(ctx, x, cy, w, BOT_H, botFill, light, mirror, flushLeft || flushRight);
			int cx = mirror ? x + BOT_H / 2 : x;
			int cellW = (w - BOT_H / 2) / small.size();
			for (int i = 0; i < small.size(); i++) {
				if (i > 0) {
					dots(ctx, cx, cy + 4, BOT_H - 8, divider);
				}
				drawSmall(ctx, font, cx, cy, small.get(i), smallV[i], accent);
				cx += cellW;
			}
		}
	}

	/**
	 * 띠 하나: 끝 반원(pill - roundRect는 둥글기를 4로 자른다) + 벽 쪽은 각지게(벽에 안 붙었으면 살짝 둥글게 3) + 위 밝은 줄 + 아래 어두운 줄.
	 * mirror면 반원이 왼쪽. 줄은 반원 안까지 들어가지 않는다.
	 */
	private static void bar(DrawContext ctx, int x, int y, int w, int h, int fill, boolean light, boolean mirror, boolean flush) {
		int r = h / 2;
		LunaDraw.pill(ctx, x, y, w, h, fill);
		int fw = Math.min(w / 2, h);
		if (flush) {
			// 벽 쪽을 각지게: 그 반쪽을 네모로 덮는다(벽 밖까지 4칸 더 - 화면 끝에 모서리가 안 보이게)
			if (mirror) {
				ctx.fill(x + w - fw, y, x + w + 4, y + h, LunaDraw.applyAlpha(fill));
			} else {
				ctx.fill(x - 4, y, x + fw, y + h, LunaDraw.applyAlpha(fill));
			}
		} else {
			LunaDraw.roundRect(ctx, mirror ? x + w - fw : x, y, fw, h, 3, fill);
		}
		int hi = LunaDraw.lerpColor(fill, 0xFFFFFFFF, light ? 0.45f : 0.18f);
		int lo = LunaDraw.lerpColor(fill, 0xFF000000, light ? 0.18f : 0.35f);
		int lx0 = mirror ? x + r : x + (flush ? -4 : 3);
		int lx1 = mirror ? x + w + (flush ? 4 : -3) : x + w - r;
		ctx.fill(lx0, y, lx1, y + 1, LunaDraw.applyAlpha(hi));
		ctx.fill(lx0, y + h - 1, lx1, y + h, LunaDraw.applyAlpha(lo));
	}

	/** 세로 점선(한 칸 걸러 1칸). */
	private static void dots(DrawContext ctx, int x, int y, int h, int color) {
		int c = LunaDraw.applyAlpha(color);
		for (int i = 0; i < h; i += 2) {
			ctx.fill(x, y + i, x + 1, y + i + 1, c);
		}
	}

	private static int bigIconW(TextRenderer font, Stat s) {
		return Math.max(8, LunaIcons.width(font, s.icon, true));
	}

	private static int smallIconW(TextRenderer font, Stat s) {
		return Math.max(6, LunaIcons.width(font, s.icon, false));
	}

	private static int bigNeed(TextRenderer font, Stat s, String v) {
		int lw = Math.round(LunaDraw.width(font, s.label) * LABEL_K);
		int vw = LunaDraw.width(font, "§l" + v);
		int uw = s.unit.isEmpty() ? 0 : Math.round(LunaDraw.width(font, s.unit) * LABEL_K) + 2;
		return 5 + bigIconW(font, s) + 4 + lw + 4 + vw + uw + 10;
	}

	private static int smallNeed(TextRenderer font, Stat s, String v) {
		int lw = Math.round(LunaDraw.width(font, s.label) * SMALL_K);
		int vw = Math.round(LunaDraw.width(font, "§l" + v) * SMALL_K);
		int uw = s.unit.isEmpty() ? 0 : Math.round(LunaDraw.width(font, s.unit) * LABEL_K) + 1;
		return 5 + smallIconW(font, s) + 3 + lw + 3 + vw + uw + 9;
	}

	/** 큰 칸: 큰 아이콘(왼쪽 5, 세로 가운데) + 위 작은 이름 + 아래 굵은 값 + 작은 단위. */
	private void drawBig(DrawContext ctx, TextRenderer font, int x, int y, Stat s, String v, int accent) {
		int ix = x + 5;
		LunaIcons.drawLarge(ctx, font, s.icon, ix, LunaDraw.iconLgY(y, TOP_H), accent);
		int tx = ix + bigIconW(font, s) + 4;
		int ty = LunaDraw.textY(y, TOP_H);
		LunaDraw.text(ctx, font, s.label, tx, ty, LunaTheme.TEXT_SUB);
		String bold = "§l" + v;
		int vx = tx + LunaDraw.width(font, s.label) + 4;
		LunaDraw.text(ctx, font, bold, vx, ty, LunaTheme.TEXT);
		if (!s.unit.isEmpty()) {
			LunaDraw.text(ctx, font, s.unit, vx + LunaDraw.width(font, bold) + 2, ty, LunaTheme.TEXT_SUB);
		}
	}

	/** 작은 칸: 작은 아이콘 + 이름 + 굵은 값 + 단위, 세로 가운데 한 줄. */
	private void drawSmall(DrawContext ctx, TextRenderer font, int x, int y, Stat s, String v, int accent) {
		int ix = x + 5;
		LunaIcons.draw(ctx, font, s.icon, ix, LunaDraw.iconY(y, BOT_H), accent);
		int lx = ix + smallIconW(font, s) + 3;
		float ty = LunaDraw.textY(y, BOT_H);
		scaledText(ctx, font, s.label, lx, ty, SMALL_K, LunaTheme.TEXT_SUB);
		String bold = "§l" + v;
		int vx = lx + Math.round(LunaDraw.width(font, s.label) * SMALL_K) + 3;
		scaledText(ctx, font, bold, vx, ty, SMALL_K, LunaTheme.TEXT);
		if (!s.unit.isEmpty()) {
			scaledText(ctx, font, s.unit, vx + Math.round(LunaDraw.width(font, bold) * SMALL_K) + 1, ty, LABEL_K, LunaTheme.TEXT_SUB);
		}
	}

	private static void scaledText(DrawContext ctx, TextRenderer font, String s, float x, float y, float k, int color) {
		if (Math.abs(k - 1f) < 0.01f || !LunaCompat.guiTransformSupported(ctx)) {
			LunaDraw.text(ctx, font, s, Math.round(x), Math.round(y), color);
			return;
		}
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslate(ctx, x, y);
		LunaCompat.guiScale(ctx, k, k);
		LunaDraw.text(ctx, font, s, 0, 0, color);
		LunaCompat.guiPop(ctx);
	}
}
