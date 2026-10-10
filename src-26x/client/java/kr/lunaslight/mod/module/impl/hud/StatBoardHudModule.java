package kr.lunaslight.mod.module.impl.hud;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.gui.LunaIcons;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.EnumSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.InfoSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaTheme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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

	private final EnumSetting<Stat>[] slots;
	private final PositionSetting position;

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
		register(new InfoSetting("ui", "인게임 UI", () -> unlocked()
				? "쓰는 중: " + LunaTheme.skin() + " (이 색을 따라갑니다)"
				: "인게임 UI가 있어야 보입니다(런처 상점에서 사고 착용)"));
		slots = new EnumSetting[5];
		String[] names = {"큰 칸 1", "큰 칸 2", "작은 칸 1", "작은 칸 2", "작은 칸 3"};
		Stat[] defs = {Stat.FPS, Stat.CPS, Stat.PING, Stat.DIRECTION, Stat.PLAYTIME};
		for (int i = 0; i < 5; i++) {
			slots[i] = register(new EnumSetting<>("slot" + (i + 1), names[i], "이 칸에 띄울 정보입니다. 없음이면 칸을 뺍니다.", defs[i], Stat.class));
		}
		position = register(new PositionSetting("position", "위치", "상태판이 뜨는 자리입니다.",
				HudPosition.of(HudPosition.Anchor.TOP_LEFT, 4, 16)));
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
			double x = client.player.getX(), z = client.player.getZ();
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
			java.lang.reflect.Method m = LunaCompat.getMethodCompat(net.minecraft.client.Minecraft.class, "getCurrentFps");
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
					if (client.getConnection() == null || client.player == null) {
						return "--";
					}
					net.minecraft.client.multiplayer.PlayerInfo e = client.getConnection().getPlayerInfo(client.player.getUUID());
					return e == null ? "--" : String.valueOf(e.getLatency());
				}
				case COORDS: {
					net.minecraft.core.BlockPos p = client.player.blockPosition();
					return p.getX() + ", " + p.getY() + ", " + p.getZ();
				}
				case DIRECTION: {
					float yaw = LunaCompat.getYaw(client.player);
					String[] d = {"남", "남서", "서", "북서", "북", "북동", "동", "남동"};
					return d[(int) Math.floorMod(Math.round(((yaw % 360f) + 360f) % 360f / 45f), 8)];
				}
				case BIOME: {
					String b = LunaCompat.getBiomeName(client.level, client.player.blockPosition());
					return b == null ? "--" : b;
				}
				case CLOCK:
					return java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
				case GAME_TIME: {
					Object t = null;
					for (String n : new String[]{"getOverworldClockTime", "getDayTime", "getTimeOfDay"}) {
						t = LunaCompat.callNoArg(client.level, n);
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

	private static final int PAD = 4;
	private static final int GAP = 3;
	private static final int BIG_H = 28;
	private static final int SMALL_H = 14;
	private static final int W = 204;

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tick) {
		boolean sample = isPreview() || client.player == null;
		Font font = client.font;
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		if (!unlocked()) {
			if (isPreview()) {
				String msg = "인게임 UI가 있어야 보입니다";
				int w = LunaDraw.width(font, msg) + 12;
				int x = position.get().resolveX(sw, w);
				int y = position.get().resolveY(sh, 14);
				LunaDraw.roundRect(ctx, x, y, w, 14, 4, 0xC0101215);
				LunaDraw.text(ctx, font, msg, x + 6, y + 3, 0xFFB0B6BE);
			}
			return;
		}
		Stat[] big = {slots[0].get(), slots[1].get()};
		java.util.List<Stat> small = new java.util.ArrayList<>();
		for (int i = 2; i < 5; i++) {
			if (slots[i].get() != Stat.NONE) {
				small.add(slots[i].get());
			}
		}
		java.util.List<Stat> bigs = new java.util.ArrayList<>();
		for (Stat s : big) {
			if (s != Stat.NONE) {
				bigs.add(s);
			}
		}
		if (bigs.isEmpty() && small.isEmpty()) {
			return;
		}
		int h = PAD * 2 + (bigs.isEmpty() ? 0 : BIG_H) + (small.isEmpty() ? 0 : SMALL_H) + (!bigs.isEmpty() && !small.isEmpty() ? GAP : 0);
		int x = position.get().resolveX(sw, W);
		int y = position.get().resolveY(sh, h);

		int panel = 0xF0000000 | (LunaTheme.PANEL & 0x00FFFFFF);
		int card = 0xFF000000 | (LunaTheme.CARD & 0x00FFFFFF);
		int border = LunaTheme.PANEL_BORDER_LIVE;
		int accent = LunaTheme.ACCENT | 0xFF000000;
		// 판: 그림자 + 위가 조금 밝은 그라데이션 + 테두리
		LunaDraw.roundRect(ctx, x, y + 1, W, h, 7, 0x40000000);
		LunaDraw.roundRectGradient(ctx, x, y, W, h, 7, LunaDraw.lerpColor(panel, 0xFFFFFFFF, 0.06f), panel);
		LunaDraw.roundRectOutline(ctx, x, y, W, h, 7, border);

		int cy = y + PAD;
		if (!bigs.isEmpty()) {
			int n = bigs.size();
			int cw = (W - PAD * 2 - GAP * (n - 1)) / n;
			for (int i = 0; i < n; i++) {
				drawBig(ctx, font, x + PAD + i * (cw + GAP), cy, cw, bigs.get(i), value(bigs.get(i), sample), card, accent);
			}
			cy += BIG_H + GAP;
		}
		if (!small.isEmpty()) {
			int n = small.size();
			int cw = (W - PAD * 2 - GAP * (n - 1)) / n;
			for (int i = 0; i < n; i++) {
				drawSmall(ctx, font, x + PAD + i * (cw + GAP), cy, cw, small.get(i), value(small.get(i), sample), card, accent);
			}
		}
	}

	/** 큰 칸: 둥근 카드 + 왼쪽 아이콘 동그라미 + 위 작은 이름(포인트 색) + 아래 큰 값(1.35배) + 단위. */
	private void drawBig(GuiGraphicsExtractor ctx, Font font, int x, int y, int w, Stat s, String v, int card, int accent) {
		LunaDraw.roundRectBordered(ctx, x, y, w, BIG_H, 6, card, LunaTheme.CARD_BORDER);
		int ic = 18;
		int ix = x + 5, iy = y + (BIG_H - ic) / 2;
		LunaDraw.roundRect(ctx, ix, iy, ic, ic, ic / 2, LunaDraw.lerpColor(card, accent, 0.22f));
		LunaIcons.drawInBox(ctx, font, s.icon, ix, iy, ic, accent);
		int tx = ix + ic + 6;
		scaledText(ctx, font, s.label, tx, y + 4, 0.75f, LunaDraw.lerpColor(accent, LunaTheme.TEXT, 0.35f));
		float vs = 1.35f;
		int avail = x + w - 6 - tx;
		int unitW = s.unit.isEmpty() ? 0 : Math.round(LunaDraw.width(font, s.unit) * 0.75f) + 2;
		int vw = LunaDraw.width(font, v);
		if (vw * vs > avail - unitW) {
			vs = Math.max(0.6f, (avail - unitW) / (float) Math.max(1, vw));
		}
		scaledText(ctx, font, v, tx, y + 12, vs, LunaTheme.TEXT);
		if (!s.unit.isEmpty()) {
			scaledText(ctx, font, s.unit, tx + Math.round(vw * vs) + 2, y + 12 + Math.round(8 * vs) - 7, 0.75f, LunaTheme.TEXT_SUB);
		}
	}

	/** 작은 칸: 알약 + 작은 아이콘 + 이름(흐림) + 오른쪽 끝 값. */
	private void drawSmall(GuiGraphicsExtractor ctx, Font font, int x, int y, int w, Stat s, String v, int card, int accent) {
		LunaDraw.roundRectBordered(ctx, x, y, w, SMALL_H, SMALL_H / 2, LunaDraw.lerpColor(card, 0xFFFFFFFF, 0.04f), LunaTheme.CARD_BORDER);
		int ic = 10;
		LunaIcons.drawInBox(ctx, font, s.icon, x + 4, y + (SMALL_H - ic) / 2, ic, accent);
		int lx = x + 4 + ic + 3;
		scaledText(ctx, font, s.label, lx, y + 4, 0.7f, LunaTheme.TEXT_SUB);
		String full = s.unit.isEmpty() ? v : v + s.unit;
		int labelW = Math.round(LunaDraw.width(font, s.label) * 0.7f);
		int avail = x + w - 6 - (lx + labelW + 3);
		float vs = 0.85f;
		int vw = LunaDraw.width(font, full);
		if (vw * vs > avail) {
			vs = Math.max(0.5f, avail / (float) Math.max(1, vw));
		}
		int vx = x + w - 6 - Math.round(vw * vs);
		scaledText(ctx, font, full, vx, y + (SMALL_H - Math.round(8 * vs)) / 2 + 1, vs, LunaTheme.TEXT);
	}

	private static void scaledText(GuiGraphicsExtractor ctx, Font font, String s, float x, float y, float k, int color) {
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
