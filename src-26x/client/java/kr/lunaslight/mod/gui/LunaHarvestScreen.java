package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.ModuleManager;
import kr.lunaslight.mod.module.impl.misc.HarvestTrackerModule;
import kr.lunaslight.mod.util.HarvestLog;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-48차: 작물 계산기 창 - 탭 셋.
 *  · <b>기록</b>: 지난 판들(시각·번 돈·캔 시간·개수). 줄 오른쪽 ×로 하나씩, 위 [전체 삭제]로 통째로 지운다.
 *  · <b>총합</b>: 아이템별 누적 개수와 시간. 시간은 그 판에서 그 아이템이 차지한 비율만큼 나눠 더한다
 *    (여러 종류를 같이 캐는 게 보통이라 "이 아이템만 몇 분"은 원래 정확히 나눌 수 없다 - 근사).
 *  · <b>가격표</b>: 아이템별 개당 가격. 숫자를 클릭해 고친다. 49-140차(사용자: "카테고리 삭제/추가랑 손에 든 아이템 없애, 가격표만"):
 *    표 추가/삭제와 [손에 든 것 추가]를 없앴다 - 추가는 설정의 [가격표 등록](아이템 목록에서 고르기)에서만.
 */
public class LunaHarvestScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 440, 306);

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

	private static final int PANEL_W = 420;
	private static final int PANEL_H = 290;
	private static final int ROW_H = 22;
	private static final int PAD = 12;
	private static final int HEAD_H = 44;
	/** 49-68차(4-13): 목록 위에 고정으로 붙는 합계 줄의 높이(스크롤과 무관하게 늘 보인다). */
	private static final int SUM_H = 26;

	private static final String[] TABS = {"기록", "총합", "가격표"};

	private final Screen parent;
	private int px, py;
	private int tab;
	private double scroll;
	/** 가격을 고치는 중인 아이템 id(없으면 null). */
	private String editing;
	private String editBuffer = "";

	public LunaHarvestScreen(Screen parent) {
		super(LunaCompat.textLiteral("작물 계산기"));
		this.parent = parent;
		LunaDraw.resetAnim("harvest");
	}

	/** 49-210차: 기능 설정의 [가격 등록]/[수확 기록] 버튼이 그 탭으로 바로 연다(0 기록, 1 총합, 2 가격표). */
	public LunaHarvestScreen(Screen parent, int tab) {
		this(parent);
		this.tab = Math.max(0, Math.min(TABS.length - 1, tab));
	}

	private HarvestTrackerModule module() {
		var opt = ModuleManager.get().find("harvest_tracker");
		return opt.isPresent() && opt.get() instanceof HarvestTrackerModule m ? m : null;
	}

	private void lunaInit0() {
		px = (width - PANEL_W) / 2;
		py = Math.max(8, (height - PANEL_H) / 2);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		commitEdit();
		HarvestLog.save();
		LunaCompat.setScreen(parent);
	}

	// ==================== 가격표 ====================

	// 49-76차(6-12-3): 가격표가 여러 벌(작물 / 광물 / …). 여기서 "prices()"는 **지금 고른 표**다.
	private Map<String, Double> prices() {
		HarvestTrackerModule m = module();
		return m == null ? new LinkedHashMap<>() : m.pricesNow();
	}

	private void savePrices(Map<String, Double> table) {
		HarvestTrackerModule m = module();
		if (m != null) {
			java.util.LinkedHashMap<String, Map<String, Double>> all = m.priceTables();
			all.put(m.activeTable(), table);
			m.savePriceTables(all);
		}
	}

	/** 표 이름 칩 줄의 높이(가격표 탭 맨 위). */
	private static final int CHIPS_H = 22;
	/** 새 표 이름을 치는 중이면 true. */
	private boolean namingTable;
	private String nameBuffer = "";

	private void renderTableChips(GuiGraphicsExtractor ctx, int lx, int ly, int lw, int mouseX, int mouseY) {
		HarvestTrackerModule m = module();
		if (m == null) {
			return;
		}
		String active = m.activeTable();
		int x = lx;
		for (String name : m.priceTables().keySet()) {
			int w = LunaDraw.width(font, name) + 16;
			boolean on = name.equals(active);
			boolean hov = LunaDraw.in(mouseX, mouseY, x, ly, w, 16);
			LunaDraw.card3d(ctx, x, ly, w, 16, hov ? 1f : 0f, on);   // 49-227차
			LunaDraw.text(ctx, font, name, x + 8, LunaDraw.textY(ly, 16), on ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
			x += w + 4;
		}
	}

	/** 칩 줄 클릭. 처리했으면 true. */
	private boolean clickTableChips(double mouseX, double mouseY, int lx, int ly, int lw) {
		HarvestTrackerModule m = module();
		if (m == null || !LunaDraw.in(mouseX, mouseY, lx, ly, lw, 16)) {
			return false;
		}
		commitEdit();
		int x = lx;
		java.util.LinkedHashMap<String, Map<String, Double>> all = m.priceTables();
		for (String name : all.keySet()) {
			int w = LunaDraw.width(font, name) + 16;
			if (LunaDraw.in(mouseX, mouseY, x, ly, w, 16)) {
				m.setActiveTable(name);
				namingTable = false;
				return true;
			}
			x += w + 4;
		}
		namingTable = false;
		return true;
	}

	private void commitTableName() {
		HarvestTrackerModule m = module();
		String name = HarvestLog.cleanTableName(nameBuffer);
		nameBuffer = "";
		namingTable = false;
		if (m == null || name.isEmpty()) {
			return;
		}
		java.util.LinkedHashMap<String, Map<String, Double>> all = m.priceTables();
		all.putIfAbsent(name, new LinkedHashMap<>());
		m.savePriceTables(all);
		m.setActiveTable(name);
	}

	private void commitEdit() {
		if (editing == null) {
			return;
		}
		Map<String, Double> table = prices();
		try {
			table.put(editing, editBuffer.isBlank() ? 0 : Double.parseDouble(editBuffer));
		} catch (NumberFormatException ignored) {
			// 숫자가 아니면 고치기 전 값을 그대로 둔다
		}
		savePrices(table);
		editing = null;
		editBuffer = "";
	}

	private String nameOf(String id) {
		Item item = LunaCompat.itemById(id);
		if (item == null) {
			return id.startsWith("minecraft:") ? id.substring(10) : id;
		}
		try {
			return new ItemStack(item).getHoverName().getString();
		} catch (Throwable t) {
			return id;
		}
	}

	// ==================== 그리기 ====================

	private void lunaRender0(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, 0xA6000000);
		float open = LunaDraw.animFrom("harvest", 0f, 1f, 18f);
		LunaDraw.setAlpha(open);

		LunaDraw.panel3d(ctx, px, py, PANEL_W, PANEL_H, 8);   // 49-227차: 사진 시안 판

		boolean backHover = LunaDraw.in(mouseX, mouseY, px + 10, py + 12, 20, 20);
		LunaDraw.card3d(ctx, px + 10, py + 12, 20, 20, backHover ? 1f : 0f, false);   // 49-227차
		LunaIcons.draw(ctx, font, LunaIcons.BACK, px + 14, LunaDraw.iconY(py + 12, 20), LunaDraw.TEXT);
		LunaDraw.text(ctx, font, "작물 계산기", px + 38, py + 18, LunaDraw.TEXT);

		// 탭
		int tx = px + PANEL_W - PAD;
		for (int i = TABS.length - 1; i >= 0; i--) {
			int tw = LunaDraw.width(font, TABS[i]) + 16;
			tx -= tw;
			boolean on = tab == i;
			boolean hov = LunaDraw.in(mouseX, mouseY, tx, py + 14, tw, 16);
			LunaDraw.card3d(ctx, tx, py + 14, tw, 16, hov ? 1f : 0f, on);   // 49-227차
			LunaDraw.text(ctx, font, TABS[i], tx + 8, LunaDraw.textY(py + 14, 16),
				on ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);
			tx -= 4;
		}

		int lx = px + PAD;
		int ly = py + HEAD_H;
		int lw = PANEL_W - PAD * 2;
		int lh = PANEL_H - HEAD_H - PAD;
		ctx.fill(lx, ly - 6, lx + lw, ly - 5, 0x1AFFFFFF);

		lunaV.scissor(ctx, lx, ly, lx + lw, ly + lh);
		switch (tab) {
			case 0 -> renderHistory(ctx, lx, ly, lw, lh, mouseX, mouseY);
			case 1 -> renderTotals(ctx, lx, ly, lw, lh);
			default -> renderPrices(ctx, lx, ly, lw, lh, mouseX, mouseY);
		}
		ctx.disableScissor();
	}

	/**
	 * 49-68차(4-13): 전체 합계 - {총 시간(ms), 총 금액, 총 개수, 판 수}.
	 *
	 * <p><b>왜 합계를 앞으로 꺼냈나</b>: 작물 계산기를 켜는 이유는 "이거 해서 얼마 버나"를 알기
	 * 위해서인데, 지금까지 화면은 한 판씩만 보여 줬다. 판이 열 개 넘어가면 사람이 암산으로 더해야 했다.
	 */
	private double[] grandTotals() {
		double ms = 0;
		double money = 0;
		double count = 0;
		List<HarvestLog.Session> list = HarvestLog.history();
		for (HarvestLog.Session s : list) {
			ms += s.activeMs;
			money += s.earned;
			count += s.totalCount();
		}
		return new double[]{ms, money, count, list.size()};
	}

	/** 시간당 수익. 잰 시간이 1분도 안 되면 -1(그 숫자는 아무 뜻이 없다 - 5초 캐고 "시간당 720만원"). */
	private static double perHour(double money, double ms) {
		return ms < 60_000 ? -1 : money / (ms / 3_600_000.0);
	}

	/** 목록 위 고정 합계 줄. 왼쪽부터 무엇을·얼마나·얼마에. */
	private void drawSummary(GuiGraphicsExtractor ctx, int lx, int ly, int lw, String left, double money, double ms) {
		LunaDraw.roundRectBordered(ctx, lx, ly, lw, SUM_H - 6, 5, LunaDraw.CARD,
			LunaDraw.withAlpha(LunaDraw.ACCENT, 0x55));
		int ty = LunaDraw.textY(ly, SUM_H - 6);
		LunaDraw.text(ctx, font, left, lx + 8, ty, LunaDraw.TEXT_SUB);
		double hourly = perHour(money, ms);
		String right = HarvestLog.money(money) + "원";
		String hourlyText = hourly < 0 ? "§8시간당 —" : "§7시간당 §f" + HarvestLog.money(hourly) + "원";
		int rw = LunaDraw.width(font, right);
		LunaDraw.text(ctx, font, right, lx + lw - 8 - rw, ty, LunaDraw.ACCENT);
		int hw = LunaDraw.width(font, hourlyText);
		LunaDraw.text(ctx, font, hourlyText, lx + lw - 16 - rw - hw, ty, LunaDraw.TEXT_SUB);
	}

	private void renderHistory(GuiGraphicsExtractor ctx, int lx, int ly, int lw, int lh, int mouseX, int mouseY) {
		List<HarvestLog.Session> list = HarvestLog.history();
		if (list.isEmpty()) {
			LunaDraw.textCentered(ctx, font, "아직 기록이 없습니다 - 캐기 시작하면 쌓입니다",
				lx + lw / 2, ly + lh / 2 - 4, LunaDraw.TEXT_DIM);
			return;
		}
		double[] g = grandTotals();
		drawSummary(ctx, lx, ly, lw,
			(int) g[3] + "판  §8|  §7" + HarvestLog.duration((long) g[0]) + "  §8|  §7" + (long) g[2] + "개",
			g[1], g[0]);
		SimpleDateFormat fmt = new SimpleDateFormat("M/d HH:mm");
		int y = ly + SUM_H - (int) scroll;
		ly += SUM_H;
		lh -= SUM_H;
		for (int i = 0; i < list.size(); i++) {
			HarvestLog.Session s = list.get(i);
			if (y > ly + lh) {
				break;
			}
			if (y + ROW_H >= ly) {
				boolean hov = LunaDraw.in(mouseX, mouseY, lx, y, lw, ROW_H - 2);
				LunaDraw.card3d(ctx, lx, y, lw, ROW_H - 2, hov ? 1f : 0f, false);   // 49-227차
				LunaDraw.text(ctx, font, fmt.format(new Date(s.startedAt)),
					lx + 8, LunaDraw.textY(y, ROW_H - 2), LunaDraw.TEXT_DIM);
				String money = HarvestLog.money(s.earned) + "원";
				LunaDraw.text(ctx, font, money, lx + 74, LunaDraw.textY(y, ROW_H - 2), LunaDraw.ACCENT);
				// 49-170차: 캔 거(블록 수) / 얻은 거(아이템 수)를 따로
				String info = HarvestLog.duration(s.activeMs) + "  §8|  §7채굴 " + s.harvests + "  §8|  §7획득 " + s.totalCount()
					+ "  §8|  §7" + s.items.size() + "종";
				LunaDraw.text(ctx, font, info, lx + 168, LunaDraw.textY(y, ROW_H - 2), LunaDraw.TEXT_SUB);
				boolean delHover = LunaDraw.in(mouseX, mouseY, lx + lw - 22, y + 3, 14, 14);
				LunaDraw.text(ctx, font, "×", lx + lw - 18, LunaDraw.textY(y, ROW_H - 2),
					delHover ? 0xFFEF4444 : LunaDraw.TEXT_DIM);
			}
			y += ROW_H;
		}
	}

	/**
	 * 49-68차(4-13): 아이템별 총합에 <b>돈 칸</b>을 붙였다. 개수와 시간만 있으면 "무엇을 캘지"를
	 * 못 고른다 - 많이 나오는 것과 <b>값이 되는 것</b>은 보통 다르다. 가격을 하나라도 정해 두면
	 * 정렬 기준도 개수 대신 <b>돈</b>으로 바뀐다(그게 이 화면을 보는 이유니까).
	 */
	private void renderTotals(GuiGraphicsExtractor ctx, int lx, int ly, int lw, int lh) {
		Map<String, int[]> totals = HarvestLog.totals();
		if (totals.isEmpty()) {
			LunaDraw.textCentered(ctx, font, "아직 기록이 없습니다", lx + lw / 2, ly + lh / 2 - 4, LunaDraw.TEXT_DIM);
			return;
		}
		Map<String, Double> prices = prices();
		boolean hasPrice = !prices.isEmpty();
		List<Map.Entry<String, int[]>> rows = new ArrayList<>(totals.entrySet());
		if (hasPrice) {
			rows.sort((a, b) -> Double.compare(value(prices, b), value(prices, a)));
		} else {
			rows.sort((a, b) -> Integer.compare(b.getValue()[0], a.getValue()[0]));
		}
		double[] g = grandTotals();
		drawSummary(ctx, lx, ly, lw, totals.size() + "종  §8|  §7" + (long) g[2] + "개  §8|  §7"
			+ HarvestLog.duration((long) g[0]), g[1], g[0]);
		int y = ly + SUM_H - (int) scroll;
		ly += SUM_H;
		lh -= SUM_H;
		for (Map.Entry<String, int[]> e : rows) {
			if (y > ly + lh) {
				break;
			}
			if (y + ROW_H >= ly) {
				LunaDraw.roundRectBordered(ctx, lx, y, lw, ROW_H - 2, 4, LunaDraw.CARD, LunaDraw.CARD_BORDER);
				int ty = LunaDraw.textY(y, ROW_H - 2);
				LunaDraw.text(ctx, font,
					LunaDraw.ellipsize(font, nameOf(e.getKey()), lw - 230), lx + 8, ty, LunaDraw.TEXT);
				LunaDraw.text(ctx, font, e.getValue()[0] + "개", lx + lw - 215, ty, LunaDraw.TEXT);
				LunaDraw.text(ctx, font, HarvestLog.duration(e.getValue()[1] * 1000L),
					lx + lw - 155, ty, LunaDraw.TEXT_SUB);
				double money = value(prices, e);
				// 가격을 안 정한 아이템은 0원이 아니라 "—"다. 0으로 적으면 "값어치 없음"으로 읽힌다.
				String cash = prices.containsKey(e.getKey()) ? HarvestLog.money(money) + "원" : "§8—";
				int cw = LunaDraw.width(font, cash);
				LunaDraw.text(ctx, font, cash, lx + lw - 8 - cw, ty, LunaDraw.ACCENT);
			}
			y += ROW_H;
		}
	}

	private static double value(Map<String, Double> prices, Map.Entry<String, int[]> row) {
		Double unit = prices.get(row.getKey());
		return unit == null ? 0 : unit * row.getValue()[0];
	}

	private void renderPrices(GuiGraphicsExtractor ctx, int lx, int ly, int lw, int lh, int mouseX, int mouseY) {
		// 49-76차(6-12-3): 맨 위에 표 이름 칩(작물 / 광물 / + 새 표)
		renderTableChips(ctx, lx, ly, lw, mouseX, mouseY);
		ly += CHIPS_H;
		lh -= CHIPS_H;
		Map<String, Double> table = prices();
		int y = ly + 2 - (int) scroll;
		if (table.isEmpty()) {
			LunaDraw.text(ctx, font, "설정의 [가격표 등록]에서 추가하세요", lx + 8, y + 6, LunaDraw.TEXT_DIM);
			return;
		}
		for (Map.Entry<String, Double> e : table.entrySet()) {
			if (y > ly + lh) {
				break;
			}
			if (y + ROW_H >= ly) {
				boolean hov = LunaDraw.in(mouseX, mouseY, lx, y, lw, ROW_H - 2);
				LunaDraw.card3d(ctx, lx, y, lw, ROW_H - 2, hov ? 1f : 0f, false);   // 49-227차
				LunaDraw.text(ctx, font, nameOf(e.getKey()), lx + 8, LunaDraw.textY(y, ROW_H - 2), LunaDraw.TEXT);
				boolean edit = e.getKey().equals(editing);
				// 49-68차(4-13): "개당"을 적어 둔다 - 이 숫자가 한 개 값인지 총액인지가 화면에 없었다
				String value = (edit ? editBuffer : HarvestLog.trim(e.getValue())) + "원";
				String unit = "개당";
				int uw = LunaDraw.width(font, unit);
				int vw = LunaDraw.width(font, value) + 16;
				int vx = lx + lw - 30 - vw;
				LunaDraw.roundRectBordered(ctx, vx, y + 3, vw, ROW_H - 8, 3,
					edit ? LunaDraw.TRACK : 0x00000000, edit ? LunaDraw.ACCENT : LunaDraw.CARD_BORDER);
				LunaDraw.text(ctx, font, unit, vx - uw - 5, LunaDraw.textY(y, ROW_H - 2), LunaDraw.TEXT_DIM);
				LunaDraw.text(ctx, font, value, vx + 8, LunaDraw.textY(y, ROW_H - 2), LunaDraw.ACCENT);
				boolean delHover = LunaDraw.in(mouseX, mouseY, lx + lw - 22, y + 3, 14, 14);
				LunaDraw.text(ctx, font, "×", lx + lw - 18, LunaDraw.textY(y, ROW_H - 2),
					delHover ? 0xFFEF4444 : LunaDraw.TEXT_DIM);
			}
			y += ROW_H;
		}
	}

	private String heldId() {
		if (minecraft == null || minecraft.player == null) {
			return null;
		}
		ItemStack stack = minecraft.player.getMainHandItem();
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Identifier id = LunaCompat.getItemId(stack.getItem());
		return id == null ? null : id.toString();
	}

	// ==================== 입력 ====================

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		if (LunaDraw.in(mouseX, mouseY, px + 10, py + 12, 20, 20) || !LunaDraw.in(mouseX, mouseY, px, py, PANEL_W, PANEL_H)) {
			onClose();
			return true;
		}
		// 탭
		int tx = px + PANEL_W - PAD;
		for (int i = TABS.length - 1; i >= 0; i--) {
			int tw = LunaDraw.width(font, TABS[i]) + 16;
			tx -= tw;
			if (LunaDraw.in(mouseX, mouseY, tx, py + 14, tw, 16)) {
				commitEdit();
				tab = i;
				scroll = 0;
				return true;
			}
			tx -= 4;
		}
		int lx = px + PAD;
		int ly = py + HEAD_H;
		int lw = PANEL_W - PAD * 2;

		if (tab == 0) {
			List<HarvestLog.Session> list = HarvestLog.history();
			// 49-68차(4-13): 목록이 합계 줄만큼 아래에서 시작한다
			int index = (int) ((mouseY - (ly + SUM_H) + scroll) / ROW_H);
			if (index >= 0 && index < list.size() && mouseX >= lx + lw - 24) {
				HarvestLog.remove(index);
			}
			return true;
		}
		if (tab == 2) {
			if (clickTableChips(mouseX, mouseY, lx, ly, lw)) {
				return true;
			}
			ly += CHIPS_H;
			Map<String, Double> table = prices();
			List<String> ids = new ArrayList<>(table.keySet());
			int index = (int) ((mouseY - (ly + 2) + scroll) / ROW_H);
			if (index >= 0 && index < ids.size()) {
				String id = ids.get(index);
				if (mouseX >= lx + lw - 24) {
					commitEdit();
					table.remove(id);
					savePrices(table);
				} else {
					commitEdit();
					editing = id;
					editBuffer = "";
				}
			}
			return true;
		}
		return true;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double verticalAmount) {
		scroll = Math.max(0, scroll - verticalAmount * 16);
		return true;
	}


	/** 49-221차: 이 칸에 초점이 있는 동안 한글 IME 유지(LunaScreenBase.lunaSyncIme). */
	@Override
	protected boolean lunaWantsText() {
		return namingTable;
	}
	private boolean lunaCharTyped0(char chr) {
		if (namingTable) {
			if (chr >= ' ' && nameBuffer.length() < 12) {
				nameBuffer += chr;
			}
			return true;
		}
		if (editing == null) {
			return false;
		}
		if ((chr >= '0' && chr <= '9') || (chr == '.' && !editBuffer.contains("."))) {
			if (editBuffer.length() < 9) {
				editBuffer += chr;
			}
		}
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (namingTable) {
			if (keyCode == InputConstants.KEY_BACKSPACE) {
				if (!nameBuffer.isEmpty()) {
					nameBuffer = nameBuffer.substring(0, nameBuffer.length() - 1);
				}
			} else if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
				commitTableName();
			} else if (keyCode == InputConstants.KEY_ESCAPE) {
				namingTable = false;
				nameBuffer = "";
			}
			return true;
		}
		if (editing != null) {
			if (keyCode == InputConstants.KEY_BACKSPACE) {
				if (!editBuffer.isEmpty()) {
					editBuffer = editBuffer.substring(0, editBuffer.length() - 1);
				}
				return true;
			}
			if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
				commitEdit();
				return true;
			}
			if (keyCode == InputConstants.KEY_ESCAPE) {
				editing = null;
				editBuffer = "";
				return true;
			}
			return true;
		}
		if (keyCode == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		return false;
	}
}
