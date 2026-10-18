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
import java.util.List;
import java.util.Locale;

/**
 * 49-391차(사용자: "R 누르면 이렇게 뜨는 게 아니라 아이템에 R 누르거나 JEI처럼 인벤 열면 오른쪽에 리스트 뜨고 하게 해줘, JEI같이 만들어줘 완전"):
 * <b>너굴 아이템 목록</b> - 너굴마을에서 인벤토리(상자, 작업대 등 아이템 창 전부)를 열면 창 오른쪽 빈자리에 JEI처럼 아이템 목록이 뜬다.
 * <ul>
 *   <li>맨 위: ◀ 쪽/전체 ▶ (휠로도 넘김). 가운데: 아이템(글자 없이 그림만, 마우스를 올리면 이름). 맨 아래: 검색 칸(한글 됨).</li>
 *   <li>목록 아이템 좌클릭 = 얻는 법, 우클릭 = 쓰임새. 인벤토리 칸이나 목록 아이템에 마우스를 올리고 R = 얻는 법, U = 쓰임새.</li>
 *   <li>보기 창({@link NeogulItemsScreen})은 Esc로 닫으면 원래 인벤토리로 돌아온다.</li>
 * </ul>
 */
public final class NeogulJei {
	private NeogulJei() {
	}

	private static final int SLOT = 18;
	private static final int TOP = 20;
	private static final int SEARCH_H = 14;

	private static String query = "";
	private static boolean focused;
	private static Screen owner;
	private static int page;
	private static List<NeogulData.Entry> filtered = new ArrayList<>();
	private static String filteredFor;

	/** 이번 프레임의 자리. */
	private static int gx, gy, cols, rows, sx, sy, sw, x0;
	private static boolean shown;

	public static void init() {
		try {
			ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
				if (!(screen instanceof AbstractContainerScreen<?>)) {
					return;
				}
				ScreenEvents.afterExtract(screen).register((s, g, mx, my, d) -> draw(s, g, mx, my));
				ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !click(s, e.x(), e.y(), e.button()));
				ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, ha, va) -> !scroll(s, mx, my, va));
				ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> !key(s, e.key()));
			});
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulJei:init", t);
		}
	}

	private static boolean on() {
		return NeogulItemsModule.listOn() && NeogulData.onNeogul(Minecraft.getInstance());
	}

	// ==================== 자리 ====================

	private static boolean layout(Screen s) {
		shown = false;
		if (!on() || !(s instanceof ContainerScreenAccessorMixin acc)) {
			return false;
		}
		x0 = acc.lunaslight$leftPos() + acc.lunaslight$imageWidth() + 6;
		int x1 = s.width - 4;
		cols = Math.min(9, (x1 - x0) / SLOT);
		if (cols < 2) {
			return false;
		}
		gx = x1 - cols * SLOT;
		gy = TOP;
		sy = s.height - SEARCH_H - 6;
		rows = (sy - 4 - gy) / SLOT;
		if (rows < 1) {
			return false;
		}
		sx = gx;
		sw = cols * SLOT;
		shown = true;
		return true;
	}

	private static List<NeogulData.Entry> list() {
		if (!query.equals(filteredFor)) {
			filteredFor = query;
			String q = NeogulData.normalize(query).toLowerCase(Locale.ROOT);
			List<NeogulData.Entry> out = new ArrayList<>();
			for (NeogulData.Entry e : NeogulData.items()) {
				if (q.isEmpty() || NeogulData.normalize(e.name()).toLowerCase(Locale.ROOT).contains(q)) {
					out.add(e);
				}
			}
			filtered = out;
			page = 0;
		}
		return filtered;
	}

	private static int pages() {
		int per = Math.max(1, cols * rows);
		return Math.max(1, (list().size() + per - 1) / per);
	}

	/** 마우스 아래 목록 아이템(없으면 null). */
	private static NeogulData.Entry itemAt(double mx, double my) {
		if (!shown || mx < gx || my < gy) {
			return null;
		}
		int c = (int) ((mx - gx) / SLOT);
		int r = (int) ((my - gy) / SLOT);
		if (c >= cols || r >= rows) {
			return null;
		}
		int i = page * cols * rows + r * cols + c;
		List<NeogulData.Entry> l = list();
		return i < l.size() ? l.get(i) : null;
	}

	private static boolean inArea(double mx, double my) {
		return shown && mx >= gx - 2 && mx < gx + cols * SLOT + 2;
	}

	// ==================== 그리기 ====================

	private static void draw(Screen s, GuiGraphicsExtractor g, int mx, int my) {
		try {
			if (!layout(s)) {
				return;
			}
			Font font = Minecraft.getInstance().font;
			int pn = pages();
			page = Math.max(0, Math.min(pn - 1, page));
			// 맨 위: ◀ 1/5 ▶ (JEI처럼)
			int cy = 6;
			drawButton(g, font, gx, cy, "<", LunaDraw.in(mx, my, gx, cy, 12, 12));
			drawButton(g, font, gx + sw - 12, cy, ">", LunaDraw.in(mx, my, gx + sw - 12, cy, 12, 12));
			String ps = (page + 1) + "/" + pn;
			g.text(font, LunaCompat.textLiteral(ps), gx + (sw - font.width(ps)) / 2, cy + 2, 0xFFFFFFFF, true);
			// 아이템
			List<NeogulData.Entry> l = list();
			int start = page * cols * rows;
			NeogulData.Entry hover = null;
			for (int i = 0; i < cols * rows && start + i < l.size(); i++) {
				NeogulData.Entry e = l.get(start + i);
				int x = gx + (i % cols) * SLOT;
				int y = gy + (i / cols) * SLOT;
				boolean h = LunaDraw.in(mx, my, x, y, SLOT, SLOT);
				if (h) {
					hover = e;
					g.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
				}
				ItemStack st = NeogulData.icon(e, 1);
				if (!st.isEmpty()) {
					g.item(st, x + 1, y + 1);
				}
			}
			// 검색 칸(바닐라 입력 칸)
			g.fill(sx, sy, sx + sw, sy + SEARCH_H, focused ? 0xFFFFFFFF : 0xFFA0A0A0);
			g.fill(sx + 1, sy + 1, sx + sw - 1, sy + SEARCH_H - 1, 0xFF000000);
			String shownQ = query;
			while (!shownQ.isEmpty() && font.width(shownQ) > sw - 8) {
				shownQ = shownQ.substring(1);
			}
			if (shownQ.isEmpty() && !focused) {
				g.text(font, LunaCompat.textLiteral("§8검색"), sx + 4, sy + 3, 0xFF808080, false);
			} else {
				g.text(font, LunaCompat.textLiteral(shownQ), sx + 4, sy + 3, 0xFFE0E0E0, true);
			}
			if (focused && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = sx + 4 + font.width(shownQ);
				g.fill(cx, sy + 2, cx + 1, sy + SEARCH_H - 2, 0xFFD0D0D0);
			}
			if (hover != null) {
				List<Component> tip = new ArrayList<>();
				tip.add(LunaCompat.textLiteral(hover.name()));
				tip.add(LunaCompat.textLiteral("§7좌클릭/R 얻는 법  우클릭/U 쓰임새"));
				g.setComponentTooltipForNextFrame(font, tip, mx, my);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulJei:draw", t);
		}
	}

	private static void drawButton(GuiGraphicsExtractor g, Font font, int x, int y, String label, boolean hover) {
		LunaDraw.mcPanel(g, x, y, 12, 12, hover ? 0xFFE0E0E0 : 0xFFC6C6C6);
		g.text(font, LunaCompat.textLiteral(label), x + (12 - font.width(label)) / 2 + 1, y + 2, 0xFF404040, false);
	}

	// ==================== 입력 ====================

	private static boolean click(Screen s, double mx, double my, int button) {
		try {
			if (!layout(s)) {
				return false;
			}
			if (LunaDraw.in(mx, my, sx, sy, sw, SEARCH_H)) {
				focus(s, true);
				if (button == 1) {
					query = "";
				}
				return true;
			}
			focus(s, false);
			if (LunaDraw.in(mx, my, gx, 6, 12, 12)) {
				turn(-1);
				return true;
			}
			if (LunaDraw.in(mx, my, gx + sw - 12, 6, 12, 12)) {
				turn(1);
				return true;
			}
			NeogulData.Entry e = itemAt(mx, my);
			if (e != null) {
				open(s, e.key(), button == 1);
				return true;
			}
			return inArea(mx, my) && my >= gy && my < gy + rows * SLOT;   // 목록 빈칸 클릭은 아이템 던지기가 되지 않게
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulJei:click", t);
			return false;
		}
	}

	private static boolean scroll(Screen s, double mx, double my, double v) {
		if (!layout(s) || !inArea(mx, my) || v == 0) {
			return false;
		}
		turn(v > 0 ? -1 : 1);
		return true;
	}

	private static void turn(int d) {
		int pn = pages();
		page = ((page + d) % pn + pn) % pn;
		LunaCompat.playUiSound(Minecraft.getInstance(), "UI_BUTTON_CLICK", 1.0f);
	}

	private static boolean key(Screen s, int key) {
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
			layout(s);
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
		LunaCompat.playUiSound(Minecraft.getInstance(), "UI_BUTTON_CLICK", 1.0f);
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

	private static void focus(Screen s, boolean f) {
		focused = f;
		if (f) {
			owner = s;
			TextCapture.setTarget(TARGET);
		}
	}
}
