package kr.lunaslight.mod.module.impl.inventory;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.util.ContainerIndex;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaProjection;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 49-562차(사용자: 상자 사진 + "상자에 가져다 대면 정보창마냥 상자 조금 옆쪽에 상자 내부 정보가 조금 보이고, 상태창처럼 보이는 느낌.
 * 그 네모에 선이 직선과 대각선으로만 연결돼 있는 소드아트온라인 느낌으로"): <b>상자 정보창</b>.
 *
 * <p>조준선이 상자, 덫 상자, 통, 셜커 상자에 닿으면 상자 모서리에서 선이 대각선으로 뻗고 꺾여 가로로 이어진 끝에 흰 정보창이 펼쳐진다.
 * 선은 가로, 세로, 45도 대각선으로만 꺾는다. 창에는 상자 이름, 몇 분 전 기록인지, 많이 든 순서대로 아이템 격자가 나온다.
 *
 * <p>내용물은 서버가 클라에 안 보내 주므로 <b>한 번 열어 본 상자</b>만 안다(util.ContainerIndex, 아이템 찾기와 같은 기록).
 * 이 기능이 켜져 있으면 상자를 열 때 기록한다(ItemFinderModule.recordWanted).
 *
 * <p>열기: 선이 먼저 그어지고(0~0.4) 창이 가운데에서 위아래로 펼쳐진다(0.35~1). 닫기는 거꾸로 빠르게.
 */
public class ChestPeekModule extends Module {

	private final BooleanSetting showUnknown = register(new BooleanSetting(
			"show_unknown", "안 열어 본 상자", "한 번도 열어 보지 않은 상자에도 창을 띄웁니다.", true));

	private static final int PAD = 5;
	private static final int CELL = 18;
	private static final int HEADER = 14;
	private static final int COLS = 6;
	private static final int ROWS = 3;

	// SAO 느낌: 흰 반투명 창, 회색 글자, 주황 포인트
	private static final int C_BODY = 0xF0F2F2F2;
	private static final int C_HEAD = 0xFFFFFFFF;
	private static final int C_EDGE = 0xFFB4B4B4;
	private static final int C_SEP = 0xFFD2D2D2;
	private static final int C_TITLE = 0xFF3A3A3A;
	private static final int C_SUB = 0xFF8A8A8A;
	private static final int C_ACCENT = 0xFFF2A534;
	private static final int C_CELL = 0x40000000;
	private static final int C_LINE = 0xFFFFFFFF;
	private static final int C_LINE_UNDER = 0x66000000;

	/** 이번 틱에 조준한 보관함(없으면 null). */
	private volatile BlockPos target;
	/** 지금 그리고 있는 보관함(닫히는 동안에도 남아 있다). */
	private BlockPos shown;
	private float t;
	private long lastNanos;
	private int side = 1;   // 1 = 오른쪽, -1 = 왼쪽

	private ContainerIndex.Entry cacheEntry;
	private long cacheSeen;
	private Contents cache;

	private static final class Contents {
		final List<ItemStack> stacks = new ArrayList<>();
		final List<Integer> counts = new ArrayList<>();
		int more;
		String title = "상자";
		long seen;
		boolean known;
	}

	public ChestPeekModule() {
		super("chest_peek", "상자 정보창", ModuleCategory.INVENTORY, "상자를 보면 옆에 안에 든 것을 보여 줘요");
		newFeature("49-562");
	}

	@Override
	protected void onDisable() {
		target = null;
		shown = null;
		t = 0f;
	}

	@Override
	public void onTick() {
		BlockPos p = null;
		try {
			if (client.level != null && client.player != null && LunaCompat.screenOf(client) == null) {
				BlockPos b = LunaCompat.targetedBlock(client);
				if (b != null && isStorage(b)) {
					p = b;
				}
			}
		} catch (Throwable ignored) {
		}
		target = p;
	}

	private boolean isStorage(BlockPos pos) {
		String st = String.valueOf(client.level.getBlockState(pos));
		if (st.contains("ender_chest")) {
			return false;
		}
		return st.contains("chest") || st.contains("barrel") || st.contains("shulker_box");
	}

	private static boolean isChest(String st) {
		return st.contains("chest");
	}

	@Override
	public boolean hasPreview() {
		return true;
	}

	@Override
	public boolean hasNewFeaturePreview() {
		return true;
	}

	@Override
	public void drawNewFeaturePreview(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		int cx = x + 40, cy = y + h / 2 + 8;
		drawFakeChest(ctx, cx - 14, cy - 12, 28, 24);
		drawScene(ctx, cx + 12, cy - 10, 1, sample(), 1f, x + 2, y + 2, x + w - 2, y + h - 2, false);
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tickCounter) {
		if (isPreview()) {
			int cx = previewCenterX() - 54, cy = previewCenterY() + 10;
			drawFakeChest(ctx, cx - 14, cy - 12, 28, 24);
			drawScene(ctx, cx + 12, cy - 10, 1, sample(), 1f, -100000, -100000, 100000, 100000, false);
			return;
		}
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0f : Math.min(0.05f, (now - lastNanos) / 1.0e9f);
		lastNanos = now;
		BlockPos tg = LunaCompat.screenOf(client) == null ? target : null;
		if (tg != null) {
			if (!tg.equals(shown)) {
				shown = tg;
				t = 0f;
				side = 0;   // 이번에 정한다
			}
			t = Math.min(1f, t + dt / 0.32f);
		} else {
			t = Math.max(0f, t - dt / 0.16f);
			if (t <= 0f) {
				shown = null;
			}
		}
		if (shown == null || client.level == null) {
			return;
		}
		Contents c = contentsAt(shown);
		if (c == null || (!c.known && !showUnknown.get())) {
			return;
		}
		LunaProjection proj = LunaProjection.capture(client);
		if (proj == null) {
			return;
		}
		int sw = client.getWindow().getGuiScaledWidth();
		int sh = client.getWindow().getGuiScaledHeight();
		// 상자 겉면(큰 상자면 두 칸)
		String st = String.valueOf(client.level.getBlockState(shown));
		double x0 = shown.getX(), z0 = shown.getZ(), x1 = x0 + 1, z1 = z0 + 1, y0 = shown.getY(), y1 = y0 + 1;
		if (isChest(st)) {
			x0 += 0.0625;
			z0 += 0.0625;
			x1 -= 0.0625;
			z1 -= 0.0625;
			y1 = y0 + 0.875;
			BlockPos o = ContainerIndex.otherHalf(client, shown);
			if (o != null) {
				x0 = Math.min(x0, o.getX() + 0.0625);
				z0 = Math.min(z0, o.getZ() + 0.0625);
				x1 = Math.max(x1, o.getX() + 0.9375);
				z1 = Math.max(z1, o.getZ() + 0.9375);
			}
		}
		double[] out = new double[3];
		double minX = Double.MAX_VALUE, maxX = -Double.MAX_VALUE;
		double[][] top = new double[4][];
		double[][] tc = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
		for (int i = 0; i < 4; i++) {
			if (!proj.project(tc[i][0], y1, tc[i][1], out)) {
				return;
			}
			top[i] = new double[]{out[0], out[1]};
			minX = Math.min(minX, out[0]);
			maxX = Math.max(maxX, out[0]);
		}
		if (side == 0) {
			side = (minX + maxX) / 2 > sw * 0.62 ? -1 : 1;
		}
		// 정보창 쪽 윗면 모서리에서 선이 나간다
		double[] a = top[0];
		for (double[] p : top) {
			if (side > 0 ? p[0] > a[0] : p[0] < a[0]) {
				a = p;
			}
		}
		float ease = t;
		drawScene(ctx, a[0], a[1], side, c, ease, 4, 4, sw - 4, sh - 4, true);
	}

	/**
	 * 선 + 정보창. (ax, ay) = 상자 모서리, s = 1 오른쪽/-1 왼쪽, k = 펼침 정도(0~1).
	 * 창은 (bx0,by0)-(bx1,by1) 안에 들어오게 밀고, 선은 가로/세로/45도로만 다시 잇는다.
	 */
	private void drawScene(GuiGraphicsExtractor ctx, double ax, double ay, int s, Contents c, float k,
			int bx0, int by0, int bx1, int by1, boolean avoidCrosshair) {
		if (k <= 0f) {
			return;
		}
		var font = client.font;
		// ---- 창 크기 ----
		int n = c.stacks.size() + (c.more > 0 ? 1 : 0);
		String ago = c.known ? agoText(c.seen) : "";
		String title = c.title == null || c.title.isEmpty() ? "상자" : c.title;
		int titleW = LunaDraw.width(font, title);
		int agoW = ago.isEmpty() ? 0 : LunaDraw.width(font, ago);
		int cols = Math.max(1, Math.min(COLS, n));
		int rows = Math.min(ROWS, (n + COLS - 1) / COLS);
		String msg = !c.known ? "아직 안 열어 본 상자예요" : (n == 0 ? "비어 있어요" : null);
		int bodyW = msg != null ? LunaDraw.width(font, msg) : cols * CELL;
		int pw = Math.max(Math.max(bodyW + PAD * 2, titleW + agoW + PAD * 2 + 12), 76);
		int bodyH = msg != null ? 14 : rows * CELL + 2;
		int ph = HEADER + 1 + 4 + bodyH + 3;

		// ---- 자리: 모서리에서 대각선 14, 가로 10 ----
		double ex = ax + s * 24, ey = ay - 14;
		int px = (int) Math.round(s > 0 ? ex : ex - pw);
		int py = (int) Math.round(ey - HEADER / 2.0);
		if (avoidCrosshair) {
			int mid = (bx0 + bx1) / 2;
			if (s > 0) {
				px = Math.max(px, mid + 12);
			} else {
				px = Math.min(px, mid - 12 - pw);
			}
		}
		px = Math.max(bx0, Math.min(px, bx1 - pw));
		py = Math.max(by0, Math.min(py, by1 - ph));
		ex = s > 0 ? px : px + pw;
		ey = py + HEADER / 2.0;
		ax = Math.max(bx0, Math.min(ax, bx1));
		ay = Math.max(by0, Math.min(ay, by1));

		// ---- 선(가로/세로/45도만) ----
		double dx = ex - ax, dy = ey - ay;
		double mx, my;
		if (Math.abs(dx) >= Math.abs(dy)) {
			mx = ax + Math.signum(dx) * Math.abs(dy);
			my = ey;
		} else {
			mx = ax;
			my = ay + Math.signum(dy) * (Math.abs(dy) - Math.abs(dx));
		}
		double l1 = Math.hypot(mx - ax, my - ay), l2 = Math.hypot(ex - mx, ey - my);
		float lineK = clamp(k / 0.4f);
		float panelK = clamp((k - 0.35f) / 0.65f);
		double len = (l1 + l2) * easeOut(lineK);
		int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
		float la = Math.min(1f, k * 3f);
		segment(ctx, ax, ay, mx, my, Math.min(len, l1), l1, la, sw, sh);
		if (len > l1) {
			segment(ctx, mx, my, ex, ey, len - l1, l2, la, sw, sh);
		}
		// 상자 쪽 고리
		LunaDraw.roundRect(ctx, (int) Math.round(ax) - 3, (int) Math.round(ay) - 3, 7, 7, 3, alpha(0x70000000, la));
		LunaDraw.roundRect(ctx, (int) Math.round(ax) - 2, (int) Math.round(ay) - 2, 5, 5, 2, alpha(C_LINE, la));
		if (panelK <= 0f) {
			return;
		}

		// ---- 창: 가운데에서 위아래로 펼친다 ----
		float pk = easeOut(panelK);
		int cyMid = py + ph / 2;
		int half = Math.max(1, Math.round((ph / 2f + 2) * pk));
		ctx.enableScissor(px - 2, cyMid - half, px + pw + 2, cyMid + half + 1);
		try {
			float pa = Math.min(1f, panelK * 1.6f);
			LunaDraw.roundRect(ctx, px - 1, py - 1, pw + 2, ph + 2, 4, alpha(0x38000000, pa));
			LunaDraw.roundRect(ctx, px, py, pw, ph, 3, alpha(C_BODY, pa));
			LunaDraw.roundRectTop(ctx, px, py, pw, HEADER, 3, alpha(C_HEAD, pa));
			ctx.fill(px + 2, py + HEADER, px + pw - 2, py + HEADER + 1, alpha(C_SEP, pa));
			LunaDraw.roundRectOutline(ctx, px, py, pw, ph, 3, alpha(C_EDGE, pa));
			// 창 쪽 연결점
			ctx.fill(s > 0 ? px - 1 : px + pw - 1, (int) Math.round(ey) - 2, s > 0 ? px + 2 : px + pw + 1, (int) Math.round(ey) + 2, alpha(C_ACCENT, pa));
			// 제목줄
			ctx.fill(px + PAD, py + 4, px + PAD + 2, py + HEADER - 3, alpha(C_ACCENT, pa));
			int ty = LunaDraw.textY(py, HEADER);
			LunaDraw.text(ctx, font, title, px + PAD + 5, ty, alpha(C_TITLE, pa));
			if (!ago.isEmpty()) {
				LunaDraw.text(ctx, font, ago, px + pw - PAD - agoW, ty, alpha(C_SUB, pa));
			}
			int gy = py + HEADER + 1 + 4;
			int gx = px + (pw - (msg != null ? bodyW : cols * CELL)) / 2;
			if (msg != null) {
				LunaDraw.text(ctx, font, msg, gx, LunaDraw.textY(gy, 12), alpha(C_SUB, pa));
			} else {
				int max = COLS * ROWS;
				boolean overflow = n > max || c.more > 0;
				int count = overflow ? Math.min(max - 1, c.stacks.size()) : Math.min(max, c.stacks.size());
				for (int i = 0; i < count; i++) {
					int cx = gx + (i % COLS) * CELL + 1, cy = gy + (i / COLS) * CELL + 1;
					ctx.fill(cx - 1, cy - 1, cx + 17, cy + 17, alpha(C_CELL, pa));
					if (pa > 0.6f) {
						ctx.item(c.stacks.get(i), cx, cy);
						drawCount(ctx, c.counts.get(i), cx, cy);
					}
				}
				if (overflow) {
					int rest = c.stacks.size() - count + c.more;
					int cx = gx + (count % COLS) * CELL + 1, cy = gy + (count / COLS) * CELL + 1;
					ctx.fill(cx - 1, cy - 1, cx + 17, cy + 17, alpha(C_CELL, pa));
					String tx = "+" + rest;
					LunaDraw.text(ctx, font, tx, cx + 8 - LunaDraw.width(font, tx) / 2, cy + 4, alpha(C_TITLE, pa));
				}
			}
		} finally {
			ctx.disableScissor();
		}
	}

	/** 선 한 토막(전체 길이 full 중 앞 len만). 밑에 어두운 줄을 깔아 밝은 바닥에서도 보이게. */
	private static void segment(GuiGraphicsExtractor ctx, double x0, double y0, double x1, double y1, double len, double full,
			float a, int sw, int sh) {
		if (len <= 0.01 || full <= 0.01) {
			return;
		}
		double f = Math.min(1.0, len / full);
		double xe = x0 + (x1 - x0) * f, ye = y0 + (y1 - y0) * f;
		LunaProjection.lineAA(ctx, x0, y0, xe, ye, 2.6f, alpha(C_LINE_UNDER, a), sw, sh);
		LunaProjection.lineAA(ctx, x0, y0, xe, ye, 1.1f, alpha(C_LINE, a), sw, sh);
	}

	/** 미리보기용 그림 상자. */
	private static void drawFakeChest(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		ctx.fill(x, y, x + w, y + h, 0xFF2B2118);
		ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF9A6A35);
		ctx.fill(x + 1, y + h * 2 / 5, x + w - 1, y + h * 2 / 5 + 2, 0xFF2B2118);
		ctx.fill(x + w / 2 - 2, y + h * 2 / 5 - 2, x + w / 2 + 2, y + h * 2 / 5 + 4, 0xFFBFBFBF);
	}

	private Contents sample() {
		Contents c = new Contents();
		c.known = true;
		c.seen = System.currentTimeMillis();
		c.title = "상자";
		Object[][] items = {{Items.WHEAT, 192}, {Items.CARROT, 64}, {Items.POTATO, 37}, {Items.OAK_LOG, 20}};
		for (Object[] it : items) {
			c.stacks.add(new ItemStack((net.minecraft.world.item.Item) it[0]));
			c.counts.add((Integer) it[1]);
		}
		return c;
	}

	/** 이 자리 기록(많이 든 순서, 겉모습(모델)까지). 기록이 없으면 known=false. */
	private Contents contentsAt(BlockPos pos) {
		ContainerIndex.Entry e = null;
		try {
			e = ContainerIndex.get(client, pos);
		} catch (Throwable ignored) {
		}
		if (e != null && e == cacheEntry && e.seen == cacheSeen && cache != null) {
			return cache;
		}
		Contents c = new Contents();
		if (e == null) {
			try {
				c.title = client.level.getBlockState(pos).getBlock().getName().getString();
			} catch (Throwable ignored) {
			}
			cacheEntry = null;
			cache = null;
			return c;
		}
		c.known = true;
		c.seen = e.seen;
		c.title = e.title;
		Map<String, Integer> src = e.looks.isEmpty() ? e.items : e.looks;
		List<Map.Entry<String, Integer>> list = new ArrayList<>(src.entrySet());
		list.sort((p, q) -> Integer.compare(q.getValue(), p.getValue()));
		for (Map.Entry<String, Integer> it : list) {
			if (c.stacks.size() >= COLS * ROWS) {
				c.more++;
				continue;
			}
			ItemStack st = ContainerIndex.stackOfLook(it.getKey(), 1);
			if (st == null || st.isEmpty()) {
				continue;
			}
			c.stacks.add(st);
			c.counts.add(it.getValue());
		}
		cacheEntry = e;
		cacheSeen = e.seen;
		cache = c;
		return c;
	}

	private void drawCount(GuiGraphicsExtractor ctx, int count, int cx, int cy) {
		if (count <= 1) {
			return;
		}
		String tx = count >= 1000 ? (count >= 10000 ? (count / 1000) + "k" : String.format(java.util.Locale.ROOT, "%.1fk", count / 1000f)) : String.valueOf(count);
		LunaCompat.guiPush(ctx);
		LunaCompat.guiTranslateZ(ctx, 250f);
		LunaCompat.drawHudText(ctx, client.font, tx, cx + 17 - LunaCompat.getTextWidth(client.font, tx), cy + 9, 0xFFFFFFFF);
		LunaCompat.guiPop(ctx);
	}

	private static String agoText(long seen) {
		long s = Math.max(0L, (System.currentTimeMillis() - seen) / 1000L);
		if (s < 60) {
			return "방금";
		}
		if (s < 3600) {
			return (s / 60) + "분 전";
		}
		if (s < 86400) {
			return (s / 3600) + "시간 전";
		}
		return (s / 86400) + "일 전";
	}

	private static int alpha(int color, float a) {
		int al = Math.round(((color >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, a)));
		return (al << 24) | (color & 0x00FFFFFF);
	}

	private static float clamp(float v) {
		return v < 0f ? 0f : (v > 1f ? 1f : v);
	}

	private static float easeOut(float v) {
		float u = 1f - v;
		return 1f - u * u * u;
	}
}
