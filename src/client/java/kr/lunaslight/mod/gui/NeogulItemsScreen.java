package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 49-133차(사용자: "너굴 아이템 - R 누르면 너굴마을의 모든 아이템/도구의 출처와 경로를 확인, UI로 글 없이 아이템으로 깔끔하게,
 * JEI 느낌으로 하고 마크 UI를 그대로 쓸 수 있도록 - 우리가 쓰는 UI 노노"): <b>너굴 아이템</b> 창.
 *
 * <p>바닐라 GUI 판과 슬롯만 쓴다(루나 둥근 카드 없음). 글자는 마우스를 올렸을 때 뜨는 바닐라 툴팁뿐이다.
 * <ul>
 *   <li>오른쪽 판: 위에 분류 탭(아이콘), 가운데 아이템 격자, 아래 검색 칸. 좌클릭 = 얻는 법, 우클릭 = 쓰임새.</li>
 *   <li>왼쪽 판: 고른 아이템 한 칸 + [얻는 법 | 쓰임새] 두 칸, 그 아래 줄마다
 *       <b>[어디서] [재료…] → [결과]</b>. 재료나 결과를 누르면 그 아이템으로 넘어가고, 백스페이스로 되돌아온다.</li>
 * </ul>
 */
public class NeogulItemsScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 480, 320);

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

	private static final int SLOT = 18;
	private static final int B = 7;
	private static final int H = 170;
	private static final int DETAIL_W = 176;
	private static final int LIST_COLS = 8;
	private static final int BAR = 6;
	private static final int LIST_W = B * 2 + LIST_COLS * SLOT + BAR;
	private static final int GAP = 4;
	private static final int ROW_H = 22;
	private static final int TAB = 20;

	private final int toggleKey;

	private int x0, py, lx;
	private String cat = "";          // "" = 전체
	private String query = "";
	private boolean queryFocused;
	private int listScroll;           // 줄 단위
	private int rowScroll;            // 줄 단위
	private String selected;
	private boolean usesMode;
	private final List<Object[]> history = new ArrayList<>();   // {key, usesMode}

	/** 이번 프레임에 마우스를 받는 칸들 - 툴팁과 클릭이 같이 쓴다. */
	private record Hot(int x, int y, int w, int h, String tip, String key, int action) {
	}

	private static final int ACT_NONE = 0, ACT_ITEM = 1, ACT_MODE_FROM = 2, ACT_MODE_USES = 3, ACT_CAT = 4, ACT_SEARCH = 5;
	private final List<Hot> hots = new ArrayList<>();

	private List<NeogulData.Entry> filtered = new ArrayList<>();
	private String filteredKey = null;

	public NeogulItemsScreen(int toggleKey) {
		super(LunaCompat.textLiteral("너굴 아이템"));
		this.toggleKey = toggleKey;
	}

	@Override
	public boolean shouldPause() {
		return false;
	}

	private void lunaInit0() {
		int total = DETAIL_W + GAP + LIST_W;
		x0 = Math.max(2, (width - total) / 2);
		lx = x0 + DETAIL_W + GAP;
		py = Math.max(TAB, (height - H) / 2 + TAB / 2);
		if (selected == null) {
			List<NeogulData.Entry> all = NeogulData.items();
			if (!all.isEmpty()) {
				selected = all.get(0).key();
			}
		}
	}

	// ==================== 목록 ====================

	private List<NeogulData.Entry> filtered() {
		String k = cat + "\u0000" + query;
		if (k.equals(filteredKey)) {
			return filtered;
		}
		filteredKey = k;
		String q = NeogulData.normalize(query).toLowerCase(Locale.ROOT);
		List<NeogulData.Entry> out = new ArrayList<>();
		for (NeogulData.Entry e : NeogulData.items()) {
			if (!cat.isEmpty() && !cat.equals(e.cat())) {
				continue;
			}
			if (!q.isEmpty() && !NeogulData.normalize(e.name()).toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			out.add(e);
		}
		filtered = out;
		listScroll = 0;
		return out;
	}

	private int listRows() {
		return (H - B * 2 - 12 - 3) / SLOT;
	}

	// ==================== 그리기 ====================

	private void lunaRender0(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, 0xA6000000);
		hots.clear();
		drawTabs(ctx, false);
		LunaDraw.mcPanel(ctx, x0, py, DETAIL_W, H);
		LunaDraw.mcPanel(ctx, lx, py, LIST_W, H);
		drawTabs(ctx, true);
		drawList(ctx, mouseX, mouseY);
		drawDetail(ctx, mouseX, mouseY);

		Hot h = hotAt(mouseX, mouseY);
		if (h != null && h.tip() != null && !h.tip().isEmpty()) {
			ctx.drawTooltip(textRenderer, LunaCompat.textLiteral(h.tip()), mouseX, mouseY);
		}
	}

	/** 분류 탭: 고르지 않은 탭은 판 뒤에(selectedPass=false), 고른 탭은 판 위에 붙여 그린다(바닐라 크리에이티브 탭처럼). */
	private void drawTabs(DrawContext ctx, boolean selectedPass) {
		List<NeogulData.Category> cats = NeogulData.categories();
		for (int i = 0; i <= cats.size(); i++) {
			String id = i == 0 ? "" : cats.get(i - 1).id();
			boolean sel = id.equals(cat);
			if (sel != selectedPass) {
				continue;
			}
			int tx = lx + i * TAB;
			if (tx + TAB > lx + LIST_W) {
				break;
			}
			ItemStack icon = i == 0 ? NeogulData.vanilla(List.of("minecraft:chest")) : NeogulData.icon(cats.get(i - 1));
			if (sel) {
				LunaDraw.mcPanel(ctx, tx, py - TAB + 1, TAB, TAB + 3);
				ctx.fill(tx + 3, py - 1, tx + TAB - 3, py + 3, LunaDraw.applyAlpha(LunaDraw.MC_PANEL));
				ctx.drawItem(icon, tx + 2, py - TAB + 4);
			} else {
				LunaDraw.mcPanel(ctx, tx, py - TAB + 3, TAB, TAB, 0xFFA8A8A8);
				ctx.drawItem(icon, tx + 2, py - TAB + 5);
			}
			hots.add(new Hot(tx, py - TAB + 1, TAB, TAB - 1, i == 0 ? "전체" : cats.get(i - 1).name(), id, ACT_CAT));
		}
	}

	private void slot(DrawContext ctx, int x, int y, ItemStack stack, boolean count) {
		LunaDraw.mcSlot(ctx, x, y);
		if (stack == null || stack.isEmpty()) {
			return;
		}
		try {
			ctx.drawItem(stack, x + 1, y + 1);
			if (count) {
				LunaCompat.drawItemOverlay(ctx, textRenderer, stack, x + 1, y + 1);
			}
		} catch (Throwable ignored) {
		}
	}

	private static void frame(DrawContext ctx, int x, int y, int w, int h, int c) {
		ctx.fill(x, y, x + w, y + 1, c);
		ctx.fill(x, y + h - 1, x + w, y + h, c);
		ctx.fill(x, y, x + 1, y + h, c);
		ctx.fill(x + w - 1, y, x + w, y + h, c);
	}

	private void hoverFill(DrawContext ctx, int mx, int my, int x, int y) {
		if (LunaDraw.in(mx, my, x + 1, y + 1, 16, 16)) {
			ctx.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
		}
	}

	private void drawList(DrawContext ctx, int mx, int my) {
		List<NeogulData.Entry> list = filtered();
		int rows = listRows();
		int totalRows = (list.size() + LIST_COLS - 1) / LIST_COLS;
		int maxScroll = Math.max(0, totalRows - rows);
		listScroll = Math.max(0, Math.min(maxScroll, listScroll));
		int gx = lx + B;
		int gy = py + B;
		for (int r = 0; r < rows; r++) {
			for (int c = 0; c < LIST_COLS; c++) {
				int i = (listScroll + r) * LIST_COLS + c;
				int sx = gx + c * SLOT;
				int sy = gy + r * SLOT;
				NeogulData.Entry e = i < list.size() ? list.get(i) : null;
				slot(ctx, sx, sy, e == null ? null : NeogulData.icon(e, 1), false);
				if (e == null) {
					continue;
				}
				if (e.key().equals(selected)) {
					frame(ctx, sx, sy, SLOT, SLOT, 0xFFFFFFFF);
				}
				hoverFill(ctx, mx, my, sx, sy);
				hots.add(new Hot(sx + 1, sy + 1, 16, 16, e.name(), e.key(), ACT_ITEM));
			}
		}
		// 스크롤 막대(바닐라 크리에이티브 창 느낌)
		int bx = lx + LIST_W - B - BAR + 2;
		int bh = rows * SLOT;
		ctx.fill(bx, gy, bx + BAR - 2, gy + bh, 0xFF8B8B8B);
		if (maxScroll > 0) {
			int th = Math.max(10, bh * rows / Math.max(rows, totalRows));
			int ty = gy + (bh - th) * listScroll / maxScroll;
			ctx.fill(bx, ty, bx + BAR - 2, ty + th, 0xFFE0E0E0);
			ctx.fill(bx + 1, ty + th - 1, bx + BAR - 2, ty + th, 0xFF6F6F6F);
		}
		// 검색 칸(바닐라 입력 칸)
		int sx = lx + B;
		int sy = py + H - B - 12;
		int sw = LIST_COLS * SLOT + BAR;
		ctx.fill(sx, sy, sx + sw, sy + 12, queryFocused ? 0xFFFFFFFF : 0xFFA0A0A0);
		ctx.fill(sx + 1, sy + 1, sx + sw - 1, sy + 11, 0xFF000000);
		String shown = query;
		while (!shown.isEmpty() && LunaDraw.width(textRenderer, shown) > sw - 8) {
			shown = shown.substring(1);
		}
		ctx.drawText(textRenderer, LunaCompat.textLiteral(shown), sx + 3, sy + 2, 0xFFE0E0E0, true);
		if (queryFocused && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = sx + 3 + LunaDraw.width(textRenderer, shown);
			ctx.fill(cx, sy + 2, cx + 1, sy + 10, 0xFFD0D0D0);
		}
		hots.add(new Hot(sx, sy, sw, 12, null, null, ACT_SEARCH));
	}

	private void drawArrow(DrawContext ctx, int x, int y) {
		int c = 0xFF8B8B8B;
		ctx.fill(x, y + 6, x + 15, y + 9, c);
		for (int i = 0; i < 7; i++) {
			ctx.fill(x + 15 + i, y + i, x + 16 + i, y + 15 - i, c);
		}
	}

	private void drawDetail(DrawContext ctx, int mx, int my) {
		int x = x0 + B;
		int y = py + B;
		NeogulData.Entry sel = NeogulData.get(selected);
		slot(ctx, x, y, sel == null ? null : NeogulData.icon(sel, 1), false);
		if (sel != null) {
			hots.add(new Hot(x + 1, y + 1, 16, 16, sel.name(), null, ACT_NONE));
		}
		// [얻는 법 | 쓰임새]
		int ux = x0 + DETAIL_W - B - SLOT;
		int fx = ux - SLOT - 2;
		slot(ctx, fx, y, NeogulData.vanilla(List.of("minecraft:crafting_table")), false);
		slot(ctx, ux, y, NeogulData.vanilla(List.of("minecraft:hopper")), false);
		frame(ctx, usesMode ? ux : fx, y, SLOT, SLOT, 0xFFFFFFFF);
		hoverFill(ctx, mx, my, fx, y);
		hoverFill(ctx, mx, my, ux, y);
		hots.add(new Hot(fx + 1, y + 1, 16, 16, "얻는 법", null, ACT_MODE_FROM));
		hots.add(new Hot(ux + 1, y + 1, 16, 16, "쓰임새", null, ACT_MODE_USES));

		int lineY = y + SLOT + 3;
		ctx.fill(x, lineY, x0 + DETAIL_W - B, lineY + 1, 0xFF8B8B8B);
		ctx.fill(x, lineY + 1, x0 + DETAIL_W - B, lineY + 2, 0xFFFFFFFF);

		int top = lineY + 5;
		int bottom = py + H - B;
		int visible = Math.max(1, (bottom - top) / ROW_H);
		List<Object[]> rows = rows(sel);   // {stationId, List<Ing> or null, outKey, outCount}
		rowScroll = Math.max(0, Math.min(Math.max(0, rows.size() - visible), rowScroll));
		int outX = x0 + DETAIL_W - B - SLOT;
		int arrowX = outX - 4 - 22;
		if (rows.isEmpty()) {
			slot(ctx, x, top, NeogulData.vanilla(List.of("minecraft:barrier")), false);
			hots.add(new Hot(x + 1, top + 1, 16, 16, usesMode ? "쓰이는 곳 없음" : "아직 모름", null, ACT_NONE));
			return;
		}
		for (int i = 0; i < visible && rowScroll + i < rows.size(); i++) {
			Object[] r = rows.get(rowScroll + i);
			int ry = top + i * ROW_H;
			NeogulData.Station st = NeogulData.station((String) r[0]);
			slot(ctx, x, ry, NeogulData.icon(st), false);
			if (st != null) {
				hots.add(new Hot(x + 1, ry + 1, 16, 16, join(st.name(), st.note(), (String) r[4]), null, ACT_NONE));
			}
			@SuppressWarnings("unchecked")
			List<NeogulData.Ing> ins = (List<NeogulData.Ing>) r[1];
			if (ins != null) {
				int ix = x + SLOT + 4;
				for (NeogulData.Ing in : ins) {
					if (ix + SLOT > arrowX - 2) {
						break;
					}
					NeogulData.Entry ie = NeogulData.get(in.key());
					slot(ctx, ix, ry, ie == null ? null : NeogulData.icon(ie, in.count()), true);
					if (in.key().equals(selected)) {
						frame(ctx, ix, ry, SLOT, SLOT, 0xFFFFFFFF);
					}
					hoverFill(ctx, mx, my, ix, ry);
					hots.add(new Hot(ix + 1, ry + 1, 16, 16,
						(ie == null ? in.key() : ie.name()) + (in.count() > 1 ? " " + in.count() + "개" : ""), in.key(), ACT_ITEM));
					ix += SLOT;
				}
			}
			drawArrow(ctx, arrowX, ry + 1);
			if (ins != null && r[4] != null && !((String) r[4]).isEmpty()) {
				hots.add(new Hot(arrowX, ry + 1, 22, 15, (String) r[4], null, ACT_NONE));
			}
			String outKey = (String) r[2];
			int outCount = (Integer) r[3];
			NeogulData.Entry oe = NeogulData.get(outKey);
			slot(ctx, outX, ry, oe == null ? null : NeogulData.icon(oe, outCount), true);
			hoverFill(ctx, mx, my, outX, ry);
			hots.add(new Hot(outX + 1, ry + 1, 16, 16,
				(oe == null ? outKey : oe.name()) + (outCount > 1 ? " " + outCount + "개" : ""), outKey, ACT_ITEM));
		}
		// 줄이 더 있으면 오른쪽 끝에 작은 막대
		if (rows.size() > visible) {
			int bh = visible * ROW_H - 4;
			int th = Math.max(8, bh * visible / rows.size());
			int ty = top + (bh - th) * rowScroll / Math.max(1, rows.size() - visible);
			int bx = x0 + DETAIL_W - 5;
			ctx.fill(bx, top, bx + 2, top + bh, 0xFF8B8B8B);
			ctx.fill(bx, ty, bx + 2, ty + th, 0xFFFFFFFF);
		}
	}

	/** 툴팁 한 줄: 빈 칸은 건너뛰고 " | "로 잇는다. */
	private static String join(String... parts) {
		StringBuilder sb = new StringBuilder();
		for (String p : parts) {
			if (p == null || p.isEmpty()) {
				continue;
			}
			if (sb.length() > 0) {
				sb.append(" | ");
			}
			sb.append(p);
		}
		return sb.toString();
	}

	/** 지금 모드의 줄들. 얻는 법 = 기본 출처 + 만드는 법, 쓰임새 = 재료로 쓰는 제작법. */
	private List<Object[]> rows(NeogulData.Entry sel) {
		List<Object[]> out = new ArrayList<>();
		if (sel == null) {
			return out;
		}
		if (!usesMode) {
			for (NeogulData.Source from : sel.from()) {
				out.add(new Object[]{from.at(), null, sel.key(), 1, from.note()});
			}
			for (NeogulData.Recipe r : NeogulData.recipesFor(sel.key())) {
				out.add(new Object[]{r.at(), r.in(), r.out(), r.count(), r.note()});
			}
		} else {
			for (NeogulData.Recipe r : NeogulData.usesOf(sel.key())) {
				out.add(new Object[]{r.at(), r.in(), r.out(), r.count(), r.note()});
			}
		}
		return out;
	}

	private Hot hotAt(double mx, double my) {
		for (int i = hots.size() - 1; i >= 0; i--) {
			Hot h = hots.get(i);
			if (LunaDraw.in(mx, my, h.x(), h.y(), h.w(), h.h())) {
				return h;
			}
		}
		return null;
	}

	// ==================== 입력 ====================

	private void open(String key, boolean uses) {
		if (key == null || NeogulData.get(key) == null) {
			return;
		}
		if (key.equals(selected) && uses == usesMode) {
			return;
		}
		if (selected != null) {
			history.add(new Object[]{selected, usesMode});
			if (history.size() > 64) {
				history.remove(0);
			}
		}
		selected = key;
		usesMode = uses;
		rowScroll = 0;
	}

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		Hot h = hotAt(mouseX, mouseY);
		queryFocused = h != null && h.action() == ACT_SEARCH;
		if (h == null) {
			return false;
		}
		switch (h.action()) {
			case ACT_ITEM -> open(h.key(), button == 1);
			case ACT_MODE_FROM -> {
				usesMode = false;
				rowScroll = 0;
			}
			case ACT_MODE_USES -> {
				usesMode = true;
				rowScroll = 0;
			}
			case ACT_CAT -> {
				cat = h.key() == null ? "" : h.key();
				listScroll = 0;
			}
			case ACT_SEARCH -> {
				if (button == 1) {
					query = "";
				}
			}
			default -> {
				return true;
			}
		}
		LunaCompat.playUiSound(client, "UI_BUTTON_CLICK", 1.0f);
		return true;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double verticalAmount) {
		int step = verticalAmount > 0 ? -1 : (verticalAmount < 0 ? 1 : 0);
		if (mouseX >= lx) {
			listScroll = Math.max(0, listScroll + step);
		} else {
			rowScroll = Math.max(0, rowScroll + step);
		}
		return true;
	}

	private boolean lunaCharTyped0(char chr) {
		if (!queryFocused || chr < ' ') {
			return false;
		}
		query += chr;
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			if (queryFocused) {
				queryFocused = false;
				return true;
			}
			close();
			return true;
		}
		if (!queryFocused && keyCode == toggleKey && toggleKey > 0) {
			close();
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
			if (queryFocused) {
				if (!query.isEmpty()) {
					query = query.substring(0, query.length() - 1);
				}
			} else if (!history.isEmpty()) {
				Object[] prev = history.remove(history.size() - 1);
				selected = (String) prev[0];
				usesMode = (Boolean) prev[1];
				rowScroll = 0;
			}
			return true;
		}
		if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
			queryFocused = false;
			List<NeogulData.Entry> l = filtered();
			if (!l.isEmpty()) {
				open(l.get(0).key(), false);
			}
			return true;
		}
		return queryFocused;
	}
}
