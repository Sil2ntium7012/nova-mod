package kr.lunaslight.mod.util;

import com.mojang.blaze3d.platform.InputConstants;
import kr.lunaslight.mod.gui.LunaDraw;
import kr.lunaslight.mod.gui.NeogulItemsScreen;
import kr.lunaslight.mod.mixin.ContainerScreenAccessorMixin;
import kr.lunaslight.mod.module.impl.misc.NeogulItemsModule;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * 49-391차 너굴 아이템 목록 → 49-394차(사용자: "카테고리가 이런 걸 말한 게 아냐, 저 화면 자체에서 나눠져 있어야 해. 위에서 고르는 게 아니라
 * 열고 닫기로 해서 서랍처럼 장비별, 작물들, 낚싯대나 도구, 광물 등 모두 제각각으로", "이 창(보기 창)에서도 옆에 JEI처럼"):
 * <b>서랍식 아이템 목록</b>. 인벤토리(아이템 창 전부)와 너굴 아이템 보기 창 오른쪽에 뜬다.
 * <ul>
 *   <li>묶음마다 서랍 머리(▶/▼ + 대표 아이템 + 이름 + 개수). 머리를 누르면 열리고 닫힌다. 처음엔 다 닫혀 있다.</li>
 *   <li>휠로 위아래. 맨 아래 검색 칸(한글 됨) - 검색 중엔 맞는 아이템이 있는 서랍만, 다 연 채로.</li>
 *   <li>아이템 좌클릭/R = 얻는 법, 우클릭/U = 쓰임새. 인벤토리 칸에 마우스를 올리고 R/U도 된다.</li>
 * </ul>
 */
public final class NeogulJei {
	private NeogulJei() {
	}

	private static final int SLOT = 18;
	private static final int TOP = 4;
	private static final int SEARCH_H = 14;

	private static String query = "";
	private static boolean focused;
	private static Screen owner;
	private static int scroll;   // 줄 단위
	private static final Set<String> OPEN = new HashSet<>();

	/** 서랍 하나. */
	private record Group(String id, String name, List<NeogulData.Entry> items) {
	}

	/** 화면 한 줄(18px): 서랍 머리 또는 아이템 줄. */
	private record Line(Group g, int row) {
		boolean header() {
			return row < 0;
		}
	}

	/** 이번 프레임의 자리. */
	private static int gx, cols, sy, sw, bottom, visLines;
	private static boolean shown;
	private static List<Line> lines = new ArrayList<>();
	private static List<Group> groups = new ArrayList<>();
	private static String groupsFor;

	public static void init() {
		try {
			ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
				if (!(screen instanceof AbstractContainerScreen<?>)) {
					return;
				}
				ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> {
					if (drawnEarly) {
						drawnEarly = false;   // 49-399차: 툴팁 전에 이미 그렸다(ScreenJeiLayerMixin)
					} else {
						drawContainer(s, g, mx, my);
					}
				});
				ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !clickContainer(s, e.x(), e.y(), e.button()));
				ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, ha, va) -> !(on() && scrollIn(mx, my, va)));
				ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> !keyContainer(s, e.key()));
			});
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulJei:init", t);
		}
	}

	public static boolean on() {
		return NeogulItemsModule.listOn() && NeogulData.onNeogul(Minecraft.getInstance());
	}

	// ==================== 서랍 묶기 ====================

	private static final String[][] GROUPS = {
		{"crop", "작물"}, {"gather", "채집물"}, {"ember", "잉걸불"}, {"coin", "채집 코인"}, {"tool", "채집 도구"},
		{"rod", "낚싯대"}, {"fish", "물고기"}, {"gem", "광물"}, {"dungeon", "던전 전리품"}, {"mat", "요리 재료"},
		{"food", "요리"}, {"food_gold", "황금 요리"},
		{"set_elder", "엘더우드 장비"}, {"set_lotus", "블랙로터스 장비"}, {"set_nord", "노르드 장비"},
		{"set_burgonet", "버거넷 장비"}, {"set_oakhark", "오크하크 장비"}, {"gear", "장비"}, {"etc", "기타"}};

	private static String groupOf(NeogulData.Entry e) {
		String k = e.key();
		String c = e.cat() == null ? "" : e.cat();
		if (k.startsWith("tool_")) {
			return "tool";
		}
		if (k.matches("rod\\d+")) {
			return "rod";
		}
		if (k.startsWith("coin_") || k.startsWith("fcoin")) {
			return "coin";
		}
		if (k.startsWith("ember_")) {
			return "ember";
		}
		for (String set : new String[]{"elder", "lotus", "nord", "burgonet", "oakhark"}) {
			if (k.startsWith(set + "_")) {
				return "set_" + set;
			}
		}
		if (k.startsWith("gold_") && "food".equals(c)) {
			return "food_gold";
		}
		if (k.startsWith("ex_") || k.equals("sugar") || k.equals("flour") || k.equals("rice") || k.equals("bread")) {
			return "mat";
		}
		for (String[] g : GROUPS) {
			if (g[0].equals(c)) {
				return c;
			}
		}
		return "etc";
	}

	private static List<Group> groups() {
		if (query.equals(groupsFor)) {
			return groups;
		}
		groupsFor = query;
		String q = NeogulData.normalize(query).toLowerCase(Locale.ROOT);
		Map<String, List<NeogulData.Entry>> by = new LinkedHashMap<>();
		for (String[] g : GROUPS) {
			by.put(g[0], new ArrayList<>());
		}
		for (NeogulData.Entry e : NeogulData.listItems()) {
			if (!q.isEmpty() && !NeogulData.normalize(e.name()).toLowerCase(Locale.ROOT).contains(q)) {
				continue;
			}
			by.get(groupOf(e)).add(e);
		}
		List<Group> out = new ArrayList<>();
		for (String[] g : GROUPS) {
			List<NeogulData.Entry> l = by.get(g[0]);
			if (!l.isEmpty()) {
				out.add(new Group(g[0], g[1], l));
			}
		}
		groups = out;
		scroll = 0;
		return out;
	}

	private static boolean isOpen(Group g) {
		return !query.isEmpty() || OPEN.contains(g.id());
	}

	private static List<Line> buildLines() {
		List<Line> out = new ArrayList<>();
		for (Group g : groups()) {
			out.add(new Line(g, -1));
			if (isOpen(g)) {
				int rows = (g.items().size() + cols - 1) / cols;
				for (int r = 0; r < rows; r++) {
					out.add(new Line(g, r));
				}
			}
		}
		return out;
	}

	// ==================== 자리 ====================

	/** x0 = 목록이 시작할 수 있는 왼쪽 끝. 화면 오른쪽 끝에 붙는다. */
	private static boolean layout(int x0, int w, int h) {
		shown = false;
		int x1 = w - 4;
		cols = Math.min(9, (x1 - x0) / SLOT);
		if (cols < 3) {
			return false;
		}
		gx = x1 - cols * SLOT;
		sy = h - SEARCH_H - 6;
		bottom = sy - 4;
		visLines = (bottom - TOP) / SLOT;
		if (visLines < 2) {
			return false;
		}
		sw = cols * SLOT;
		lines = buildLines();
		scroll = Math.max(0, Math.min(Math.max(0, lines.size() - visLines), scroll));
		shown = true;
		return true;
	}

	/** 마우스 아래 줄(없으면 null). */
	private static Line lineAt(double mx, double my) {
		if (!shown || mx < gx || mx >= gx + sw || my < TOP) {
			return null;
		}
		int i = (int) ((my - TOP) / SLOT);
		if (i >= visLines || scroll + i >= lines.size()) {
			return null;
		}
		return lines.get(scroll + i);
	}

	/** 마우스 아래 목록 아이템(없으면 null). 보기 창의 R/U도 쓴다. */
	public static NeogulData.Entry itemAt(double mx, double my) {
		Line l = lineAt(mx, my);
		if (l == null || l.header()) {
			return null;
		}
		int c = (int) ((mx - gx) / SLOT);
		int i = l.row() * cols + c;
		return c < cols && i < l.g().items().size() ? l.g().items().get(i) : null;
	}

	// ==================== 그리기 ====================

	/** 목록을 그린다(x0 = 쓸 수 있는 왼쪽 끝). 화면에 들어갈 자리가 없으면 false. */
	public static boolean drawIn(GuiGraphicsExtractor g, int mx, int my, int x0, int w, int h) {
		try {
			if (!layout(x0, w, h)) {
				return false;
			}
			Font font = Minecraft.getInstance().font;
			NeogulData.Entry hover = null;
			for (int i = 0; i < visLines && scroll + i < lines.size(); i++) {
				Line l = lines.get(scroll + i);
				int y = TOP + i * SLOT;
				if (l.header()) {
					// 49-397차(사용자: "여기는 마크 느낌을 없애 줘 봐, 그냥 글 가운데에다 해서 예쁘게"): 둥근 반투명 띠 + 가운데 이름, 열리면 아래에 강조 선
					boolean h2 = LunaDraw.in(mx, my, gx, y, sw, SLOT - 2);
					boolean open = isOpen(l.g());
					int bg = open ? 0xD0262B33 : (h2 ? 0xC0222630 : 0x9C111419);
					LunaDraw.roundRect(g, gx, y + 1, sw, SLOT - 3, 4, bg);
					if (open) {
						int lw = Math.min(sw - 16, font.width(l.g().name()) + 16);
						g.fill(gx + (sw - lw) / 2, y + SLOT - 3, gx + (sw + lw) / 2, y + SLOT - 2, LunaDraw.ACCENT | 0xFF000000);
					}
					String name = l.g().name();
					String cnt = String.valueOf(l.g().items().size());
					int nw = font.width(name);
					int cx = gx + (sw - nw) / 2;
					g.text(font, LunaCompat.textLiteral(name), cx, y + 5, open ? 0xFFFFFFFF : (h2 ? 0xFFF2F2F2 : 0xFFD8DCE2), true);
					g.text(font, LunaCompat.textLiteral(cnt), gx + sw - 6 - font.width(cnt), y + 5, 0xFF8A919C, false);
					String chev = open ? "-" : "+";
					g.text(font, LunaCompat.textLiteral(chev), gx + 6, y + 5, open ? (LunaDraw.ACCENT | 0xFF000000) : 0xFF8A919C, false);
					continue;
				}
				// 열린 서랍의 아이템 줄: 은은한 바탕
				g.fill(gx + 1, y, gx + sw - 1, y + SLOT, 0x50000000);
				List<NeogulData.Entry> items = l.g().items();
				for (int c = 0; c < cols; c++) {
					int idx = l.row() * cols + c;
					if (idx >= items.size()) {
						break;
					}
					NeogulData.Entry e = items.get(idx);
					int x = gx + c * SLOT;
					if (LunaDraw.in(mx, my, x, y, SLOT, SLOT)) {
						hover = e;
						g.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
					}
					ItemStack st = NeogulData.icon(e, 1);
					if (!st.isEmpty()) {
						g.item(st, x + 1, y + 1);
					}
				}
			}
			// 스크롤 막대(목록 왼쪽 바깥에 얇게)
			if (lines.size() > visLines) {
				int bh = visLines * SLOT;
				int th = Math.max(10, bh * visLines / lines.size());
				int ty = TOP + (bh - th) * scroll / Math.max(1, lines.size() - visLines);
				g.fill(gx - 4, TOP, gx - 2, TOP + bh, 0x40000000);
				g.fill(gx - 4, ty, gx - 2, ty + th, 0xC0FFFFFF);
			}
			// 검색 칸(바닐라 입력 칸)
			LunaDraw.roundRect(g, gx, sy, sw, SEARCH_H, 4, focused ? (LunaDraw.ACCENT | 0xFF000000) : 0xA0303640);
			LunaDraw.roundRect(g, gx + 1, sy + 1, sw - 2, SEARCH_H - 2, 3, 0xE0111419);
			String shownQ = query;
			while (!shownQ.isEmpty() && font.width(shownQ) > sw - 8) {
				shownQ = shownQ.substring(1);
			}
			if (shownQ.isEmpty() && !focused) {
				g.text(font, LunaCompat.textLiteral("검색"), gx + (sw - font.width("검색")) / 2, sy + 3, 0xFF6E7580, false);
			} else {
				g.text(font, LunaCompat.textLiteral(shownQ), gx + 4, sy + 3, 0xFFE0E0E0, true);
			}
			if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = gx + 4 + font.width(shownQ);
				g.fill(cx, sy + 2, cx + 1, sy + SEARCH_H - 2, 0xFFD0D0D0);
			}
			if (hover != null) {
				List<Component> tip = new ArrayList<>();
				tip.add(LunaCompat.textLiteral(hover.name()));
				tip.add(LunaCompat.textLiteral("§7좌클릭/R 얻는 법  우클릭/U 쓰임새"));
				g.setComponentTooltipForNextFrame(font, tip, mx, my);
			}
			return true;
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulJei:draw", t);
			return false;
		}
	}

	/** 클릭. 처리했으면 true. 아이템을 누르면 onItem(key, 쓰임새?). */
	public static boolean clickIn(Screen s, double mx, double my, int button, BiConsumer<String, Boolean> onItem) {
		if (!shown) {
			return false;
		}
		if (LunaDraw.in(mx, my, gx, sy, sw, SEARCH_H)) {
			focus(s, true);
			if (button == 1) {
				query = "";
			}
			return true;
		}
		focus(s, false);
		Line l = lineAt(mx, my);
		if (l != null && l.header()) {
			if (!OPEN.remove(l.g().id())) {
				OPEN.add(l.g().id());
			}
			LunaCompat.playUiSound(Minecraft.getInstance(), "UI_BUTTON_CLICK", 1.0f);
			return true;
		}
		NeogulData.Entry e = itemAt(mx, my);
		if (e != null) {
			LunaCompat.playUiSound(Minecraft.getInstance(), "UI_BUTTON_CLICK", 1.0f);
			onItem.accept(e.key(), button == 1);
			return true;
		}
		return mx >= gx - 4 && mx < gx + sw && my >= TOP && my < sy + SEARCH_H;   // 목록 빈자리 클릭은 아이템 던지기가 되지 않게
	}

	public static boolean scrollIn(double mx, double my, double v) {
		if (!shown || mx < gx - 4 || mx >= gx + sw || v == 0) {
			return false;
		}
		scroll = Math.max(0, Math.min(Math.max(0, lines.size() - visLines), scroll + (v > 0 ? -2 : 2)));
		return true;
	}

	// ==================== 인벤토리 창 ====================

	private static int containerX0(Screen s) {
		if (s instanceof ContainerScreenAccessorMixin acc) {
			return acc.lunaslight$leftPos() + acc.lunaslight$imageWidth() + 8;
		}
		return s.width;
	}

	/** 49-399차: 이번 프레임에 툴팁 전에 그렸다(afterExtract에서 또 그리지 않게). */
	private static boolean drawnEarly;

	/** ScreenJeiLayerMixin: 화면을 다 그리고 아이템 설명(툴팁)을 그리기 바로 전 - 설명이 목록 위에 뜬다. */
	public static void beforeTooltip(Screen s, GuiGraphicsExtractor g, int mx, int my) {
		if (!(s instanceof AbstractContainerScreen<?>)) {
			return;
		}
		try {
			drawContainer(s, g, mx, my);
			drawnEarly = true;
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulJei:layer", t);
		}
	}

	private static void drawContainer(Screen s, GuiGraphicsExtractor g, int mx, int my) {
		shown = false;
		if (on()) {
			drawIn(g, mx, my, containerX0(s), s.width, s.height);
		}
	}

	private static boolean clickContainer(Screen s, double mx, double my, int button) {
		try {
			return on() && clickIn(s, mx, my, button, (k, u) -> open(s, k, u));
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulJei:click", t);
			return false;
		}
	}

	private static boolean keyContainer(Screen s, int key) {
		try {
			if (!on()) {
				return false;
			}
			boolean recipe = key == NeogulItemsModule.recipeKey() && key > 0;
			boolean uses = key == NeogulItemsModule.usesKey() && key > 0;
			if (!recipe && !uses) {
				return false;
			}
			if (HangulInput.textFocused()) {
				return false;   // 크리에이티브 검색, 모루 이름 칸 등에서 글을 쓰는 중
			}
			Minecraft mc = Minecraft.getInstance();
			double mx = mc.mouseHandler.xpos() * s.width / Math.max(1, mc.getWindow().getScreenWidth());
			double my = mc.mouseHandler.ypos() * s.height / Math.max(1, mc.getWindow().getScreenHeight());
			String k = null;
			NeogulData.Entry e = itemAt(mx, my);
			if (e != null) {
				k = e.key();
			} else if (s instanceof ContainerScreenAccessorMixin acc) {
				Slot slot = acc.lunaslight$hoveredSlot();
				if (slot != null && slot.hasItem()) {
					k = NeogulData.keyOf(slot.getItem());
				}
			}
			if (k == null) {
				return false;
			}
			open(s, k, uses);
			return true;
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulJei:key", t);
			return false;
		}
	}

	private static void open(Screen parent, String key, boolean uses) {
		focus(parent, false);
		LunaCompat.setScreen(new NeogulItemsScreen(key, uses, parent));
	}

	// ==================== 검색 칸 글자(TextCapture) ====================

	private static final TextCapture.Target TARGET = new TextCapture.Target() {
		@Override
		public boolean wants() {
			return focused && owner != null && LunaCompat.screenOf(Minecraft.getInstance()) == owner && on();
		}

		@Override
		public void onChar(int codePoint) {
			if (codePoint >= ' ' && query.length() < 40) {
				query += new String(Character.toChars(codePoint));
			}
		}

		@Override
		public boolean onKey(int key, int action, int modifiers) {
			if (action == 0) {
				return true;
			}
			if (key == InputConstants.KEY_BACKSPACE) {
				if (!query.isEmpty()) {
					int cut = query.offsetByCodePoints(query.length(), -1);
					query = query.substring(0, cut);
				}
				return true;
			}
			if (key == InputConstants.KEY_ESCAPE || key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				focused = false;
				return true;
			}
			return true;   // E 같은 글자 키로 인벤토리가 닫히지 않게
		}
	};

	public static void focus(Screen s, boolean f) {
		focused = f;
		if (f) {
			owner = s;
			TextCapture.setTarget(TARGET);
		}
	}
}
