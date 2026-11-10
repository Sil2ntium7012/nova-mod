package kr.lunaslight.mod.module.impl.misc;

import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.module.ModuleCategory;
import kr.lunaslight.mod.module.setting.BooleanSetting;
import kr.lunaslight.mod.module.setting.HudPosition;
import kr.lunaslight.mod.module.setting.IntSetting;
import kr.lunaslight.mod.module.setting.PositionSetting;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.NeogulDungeonHud;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-613차(사용자: "광물창고 광물 미리보기 띄우는 기능 만들어 줘"): <b>광물 창고 미리보기</b> - 광물 창고(/광물창고)에 든 광물을 아이콘 격자로 화면에.
 * 개수는 광물 획득({@link NeogulMineModule})이 기억한 것(창고를 열면 맞춰지고, 캘 때마다 늘어남, 꺼낸 것도 반영). 꽉 찬 광물은 개수를 빨갛게.
 */
public class NeogulMinePeekModule extends Module {

	private final PositionSetting position = register(new PositionSetting(
			"position", "위치", "표시 위치입니다.", HudPosition.of(HudPosition.Anchor.TOP_LEFT, 5, 64)));
	private final BooleanSetting onlyMine = register(new BooleanSetting(
			"only_mine", "광산에서만", "광산에 있을 때만 보여 줍니다.", true));
	/** 49-623차(사용자: "광물 창고 좀 더 예쁘게나 배경 없이 리스트 형식으로 안 되나?"): 목록(기본) / 격자. */
	public enum Style {
		LIST("목록"), GRID("격자");

		private final String label;

		Style(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	private final kr.lunaslight.mod.module.setting.EnumSetting<Style> style = register(new kr.lunaslight.mod.module.setting.EnumSetting<>(
			"style", "모양", "목록은 아이콘, 이름, 개수를 한 줄씩, 격자는 아이콘 칸으로 보여 줍니다.", Style.LIST, Style.class));
	private final BooleanSetting background = register(new BooleanSetting(
			"background", "배경", "목록 뒤에 어두운 판을 깝니다. 끄면 글자만 떠 있습니다.", false));
	private final IntSetting columns = register(new IntSetting(
			"columns", "한 줄 칸 수", "한 줄에 놓을 광물 수입니다.", 5, 3, 9, 1));

	private static final int CELL = 18;

	public NeogulMinePeekModule() {
		super("neogul_mine_peek", "광물 창고 미리보기", ModuleCategory.SERVER, "광물 창고에 든 광물과 개수를 화면에 (광물 획득이 기억한 개수)");
		serverGroup("너굴마을");
		newFeature("49-613");
	}

	/** 49-614차(사용자: "차면 찰수록 초록에서 빨강으로 그라데이션"): 한도 대비 초록 → 노랑 → 빨강. 한도를 모르면 흰색. */
	private static int fillColor(int n, int max) {
		if (max <= 0) {
			return 0xFFFFFFFF;
		}
		float t = Math.max(0f, Math.min(1f, n / (float) max));
		int g0 = 0x7EE08A, y0 = 0xF5D76E, r0 = 0xFF5C5C;
		int a, b;
		float k;
		if (t < 0.5f) {
			a = g0;
			b = y0;
			k = t / 0.5f;
		} else {
			a = y0;
			b = r0;
			k = (t - 0.5f) / 0.5f;
		}
		int r = Math.round(((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * k);
		int gg = Math.round(((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * k);
		int bb = Math.round((a & 255) + ((b & 255) - (a & 255)) * k);
		return 0xFF000000 | (r << 16) | (gg << 8) | bb;
	}

	private static String compact(int n) {
		if (n < 1000) {
			return Integer.toString(n);
		}
		if (n < 10_000) {
			String s = String.format("%.1f", n / 1000f);
			return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + "k";
		}
		if (n < 1_000_000) {
			return (n / 1000) + "k";
		}
		return String.format("%.1fm", n / 1_000_000f);
	}

	private static ItemStack ng(String key, net.minecraft.world.item.Item fb) {
		try {
			ItemStack st = NeogulData.icon(NeogulData.get(key), 1);
			if (st != null && !st.isEmpty()) {
				return st;
			}
		} catch (Throwable ignored) {
		}
		return new ItemStack(fb);
	}

	private static List<Object[]> sample() {
		List<Object[]> l = new ArrayList<>();
		l.add(new Object[]{new ItemStack(Items.STONE), "돌", 1452, 200000, new long[]{3, System.currentTimeMillis()}});
		l.add(new Object[]{new ItemStack(Items.COAL), "석탄", 2209, 20000});
		l.add(new Object[]{new ItemStack(Items.IRON_INGOT), "철 주괴", 7071, 12000});
		l.add(new Object[]{new ItemStack(Items.COPPER_INGOT), "구리 주괴", 4300, 4752, new long[]{28, System.currentTimeMillis()}});
		l.add(new Object[]{new ItemStack(Items.GOLD_INGOT), "금 주괴", 1463, 5000});
		l.add(new Object[]{new ItemStack(Items.LAPIS_LAZULI), "청금석", 5197, 80000});
		l.add(new Object[]{new ItemStack(Items.DIAMOND), "다이아몬드", 899, 2000});
		l.add(new Object[]{ng("cook_coal", Items.CHARCOAL), "요리용 석탄", 293, -1});
		l.add(new Object[]{ng("ruby", Items.REDSTONE), "루비 수정", 14, -1});
		return l;
	}

	@Override
	public void onHudRender(GuiGraphicsExtractor ctx, DeltaTracker tick) {
		List<Object[]> items;
		if (isPreview()) {
			items = sample();
		} else {
			if (client.player == null || !NeogulData.onNeogul(client)) {
				return;
			}
			if (onlyMine.get() && !"mine".equals(NeogulDungeonHud.location)) {
				return;
			}
			items = NeogulMineModule.storage();
			if (items == null) {
				// 49-615차(사용자: "광물 창고 미리보기도 광창 한 번 열어 보라 해야 해"): 아직 창고를 안 열어 개수를 모름 - 판 자리에 안내
				drawHint(ctx);
				return;
			}
			if (items.isEmpty()) {
				return;
			}
		}
		int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
		if (style.get() == Style.LIST) {
			int[] wh = listSize(items);
			int lx = position.get().resolveX(sw, wh[0]);
			int ly = position.get().resolveY(sh, wh[1]);
			drawList(ctx, lx, ly, wh[0], wh[1], items, background.get(), hudOnRight(lx, wh[0]));
			return;
		}
		int cols = Math.max(1, Math.min(columns.get(), items.size()));
		int rows = (items.size() + cols - 1) / cols;
		int w = cols * CELL + 8, h = 15 + rows * CELL + 4;
		int x = position.get().resolveX(sw, w);
		int y = position.get().resolveY(sh, h);
		draw(ctx, x, y, w, h, cols, items);
	}

	@Override
	public void onTick() {
		NeogulMineModule.readForPeek();
	}

	private static final int ROW = 12, TITLE = 12;

	/** 이 줄의 최근 "+n"(미리보기 예시는 5번째 칸). */
	private static long[] gainOf(Object[] it) {
		return it.length > 4 && it[4] instanceof long[] ga ? ga : NeogulMineModule.recentGain((String) it[1]);
	}

	private static String shortName(String n) {
		return n.replaceAll("\\[[^\\]]*\\]", "").trim();
	}

	/** 목록 크기 {폭, 높이}: 아이콘 12 + 이름(+n) + 간격 + 개수. */
	private int[] listSize(List<Object[]> items) {
		int nameW = LunaDraw.width(client.font, "광물 창고"), countW = 0;
		for (Object[] it : items) {
			int w = LunaDraw.width(client.font, shortName((String) it[1]));
			long[] g = gainOf(it);
			if (g != null) {
				w += 3 + LunaDraw.width(client.font, "+" + g[0]);
			}
			nameW = Math.max(nameW, w);
			countW = Math.max(countW, LunaDraw.width(client.font, String.format("%,d", (Integer) it[2])));
		}
		return new int[]{14 + nameW + 10 + countW, TITLE + items.size() * ROW};
	}

	/**
	 * 49-623차: 목록 모양 - 배경 없이(설정으로 판 깔기) 줄마다 [아이콘 0.75배] 이름 (+n 초록) ...... 개수(찰수록 초록 → 빨강).
	 * 오른쪽에 두면 거울처럼(개수가 왼쪽, 아이콘이 오른쪽 끝).
	 */
	private void drawList(GuiGraphicsExtractor ctx, int x, int y, int w, int h, List<Object[]> items, boolean bg, boolean right) {
		if (bg) {
			drawHudPanel(ctx, x, y, w, h);
		}
		String title = "광물 창고";
		int tw = LunaDraw.width(client.font, title);
		LunaCompat.drawHudText(ctx, client.font, title, right ? x + w - tw : x, y + 1, 0xFFFFD36A);
		long now = System.currentTimeMillis();
		for (int i = 0; i < items.size(); i++) {
			Object[] it = items.get(i);
			int ry = y + TITLE + i * ROW;
			int ix = right ? x + w - 12 : x;
			LunaCompat.guiPush(ctx);
			try {
				LunaCompat.guiTranslate(ctx, ix, ry);
				LunaCompat.guiScale(ctx, 0.75f, 0.75f);
				ctx.item((ItemStack) it[0], 0, 0);
			} finally {
				LunaCompat.guiPop(ctx);
			}
			String name = shortName((String) it[1]);
			int nw = LunaDraw.width(client.font, name);
			int ty = ry + 2;
			int nx = right ? x + w - 14 - nw : x + 14;
			LunaCompat.drawHudText(ctx, client.font, name, nx, ty, 0xFFF2F4F7);
			long[] g = gainOf(it);
			if (g != null) {
				long age = now - g[1];
				float a = it.length > 4 ? 1f : age < 1700 ? 1f : Math.max(0f, (2500 - age) / 800f);
				String plus = "+" + g[0];
				int pw = LunaDraw.width(client.font, plus);
				float base = LunaDraw.alpha();
				LunaDraw.setAlpha(base * a);
				try {
					LunaCompat.drawHudText(ctx, client.font, plus, right ? nx - 3 - pw : nx + nw + 3, ty, 0xFF7EE08A);
				} finally {
					LunaDraw.setAlpha(base);
				}
			}
			int n = (Integer) it[2], max = (Integer) it[3];
			String c = String.format("%,d", n);
			int cw = LunaDraw.width(client.font, c);
			LunaCompat.drawHudText(ctx, client.font, c, right ? x : x + w - cw, ty, fillColor(n, max));
		}
	}

	private void drawHint(GuiGraphicsExtractor ctx) {
		int sw = client.getWindow().getGuiScaledWidth(), sh = client.getWindow().getGuiScaledHeight();
		String l1 = "광물 창고";
		String l2 = "/광물창고 를 한 번 열어 주세요";
		int w = Math.max(LunaDraw.width(client.font, l1), LunaDraw.width(client.font, l2)) + 10, h = 26;
		int x = position.get().resolveX(sw, w);
		int y = position.get().resolveY(sh, h);
		drawHudPanel(ctx, x, y, w, h);
		LunaCompat.drawHudText(ctx, client.font, l1, x + 5, y + 4, 0xFFFFD36A);
		LunaCompat.drawHudText(ctx, client.font, l2, x + 5, y + 15, 0xFFE6E8EC);
	}

	private void draw(GuiGraphicsExtractor ctx, int x, int y, int w, int h, int cols, List<Object[]> items) {
		drawHudPanel(ctx, x, y, w, h);
		LunaCompat.drawHudText(ctx, client.font, "광물 창고", x + 5, y + 4, 0xFFFFD36A);
		for (int i = 0; i < items.size(); i++) {
			Object[] it = items.get(i);
			int cx = x + 4 + (i % cols) * CELL, cy = y + 15 + (i / cols) * CELL;
			LunaDraw.roundRect(ctx, cx, cy, CELL - 1, CELL - 1, 2, 0x22FFFFFF);
			ctx.item((ItemStack) it[0], cx, cy);
			int n = (Integer) it[2], max = (Integer) it[3];
			String c = compact(n);
			LunaCompat.guiPush(ctx);
			try {
				LunaCompat.guiTranslate(ctx, cx + CELL - 1, cy + CELL - 6);
				LunaCompat.guiScale(ctx, 0.75f, 0.75f);
				LunaCompat.drawHudText(ctx, client.font, c, -LunaDraw.width(client.font, c), 0, fillColor(n, max));
			} finally {
				LunaCompat.guiPop(ctx);
			}
			// 49-614차(사용자: "광창에 추가될 때마다 광물 옆에 +n"): 칸 안 오른쪽 위에 초록 +n, 0.8초 떠 있다가 위로 올라가며 사라짐
			long[] g = it.length > 4 && it[4] instanceof long[] ga ? ga : NeogulMineModule.recentGain((String) it[1]);
			if (g != null) {
				long age = System.currentTimeMillis() - g[1];
				float a = age < 1700 ? 1f : Math.max(0f, (2500 - age) / 800f);
				if (it.length > 4) {
					a = 1f;
				}
				int lift = age < 1700 || it.length > 4 ? 0 : Math.round((age - 1700) / 160f);
				String plus = "+" + g[0];
				float base = LunaDraw.alpha();
				LunaDraw.setAlpha(base * a);
				LunaCompat.guiPush(ctx);
				try {
					LunaCompat.guiTranslate(ctx, cx + CELL - 1, cy + 1 - lift);   // 칸 안 오른쪽 위(옆 칸 숫자를 안 가리게)
					LunaCompat.guiScale(ctx, 0.75f, 0.75f);
					int pw = LunaDraw.width(client.font, plus);
					LunaDraw.roundRect(ctx, -pw - 2, -1, pw + 3, 10, 2, 0xC0102414);
					LunaCompat.drawHudText(ctx, client.font, plus, -pw, 0, 0xFF7EE08A);
				} finally {
					LunaCompat.guiPop(ctx);
					LunaDraw.setAlpha(base);
				}
			}
		}
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
	public String newFeatureScene() {
		return "stone";
	}

	@Override
	public void drawNewFeaturePreview(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		List<Object[]> items = sample();
		if (style.get() == Style.LIST) {
			List<Object[]> few = items.subList(0, Math.min(6, items.size()));
			int[] wh = listSize(few);
			drawList(ctx, x + 8, y + 6, wh[0], wh[1], few, false, false);
			return;
		}
		int cols = 5;
		int rows = (items.size() + cols - 1) / cols;
		int pw = cols * CELL + 8, ph = 15 + rows * CELL + 4;
		draw(ctx, x + 6, y + 6, pw, ph, cols, items);
	}
}
