package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulData;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-133차 너굴 아이템 창 → 49-392차(사용자: "여기도 JEI같이 깔끔하게 보여주고 왜 전체를 또 보여주는 거야 ... 농장 던전 이런 것도
 * 꾸며 주던가 폰트로 아니면 좀 예쁘게"): <b>JEI 같은 보기 창</b>. 아이템 목록은 인벤토리 옆(NeogulJei)에만 있고, 여기는 고른 아이템의
 * 얻는 법/쓰임새만 보여 준다.
 * <ul>
 *   <li>머리: 아이템 칸 + 이름 + 분류, 오른쪽에 [얻는 법] [쓰임새] 탭.</li>
 *   <li>몸: 방법마다 카드 하나 - 색 띠 + 어디서(아이콘, 이름) + 덧붙임(회색), 아래 줄에 [재료…] → [결과]. 칸마다 마우스를 올리면 이름.</li>
 *   <li>재료/결과 좌클릭 = 그 아이템의 얻는 법, 우클릭 = 쓰임새(R/U도 같음). 백스페이스 = 뒤로, Esc = 인벤토리로.</li>
 * </ul>
 */
public class NeogulItemsScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 480, 320);

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		try {
			if (lunaV.needsInit()) {
				layout();
				lunaV.markInit();
			}
			render0(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
		} finally {
			lunaV.end(ctx, e);
		}
	}

	@Override
	protected void init() {
		boolean e = lunaV.enter();
		try {
			layout();
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
			return click0(mouseX * k, mouseY * k, button);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		boolean e = lunaV.enter();
		try {
			if (verticalAmount != 0) {
				turn(verticalAmount > 0 ? -1 : 1);
			}
			return true;
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		boolean e = lunaV.enter();
		try {
			return key0(keyCode);
		} finally {
			lunaV.exit(e);
		}
	}

	private static final int SLOT = 18;
	private static final int PW = 228;
	private static final int HEAD = 32;
	private static final int CARD = 46;
	private static final int FOOT = 22;
	private static final int TEXT = 0xFF404040;
	private static final int TEXT_SUB = 0xFF6E6E6E;

	private final Screen parent;
	private String selected;
	private boolean usesMode;
	private int page;
	private final List<Object[]> history = new ArrayList<>();   // {key, usesMode}

	private int px, py, ph, perPage;
	private int lastMouseX, lastMouseY;

	/** 이번 프레임에 마우스를 받는 칸들 - 툴팁과 클릭이 같이 쓴다. */
	private record Hot(int x, int y, int w, int h, List<Component> tip, String key, int action) {
	}

	private static final int ACT_NONE = 0, ACT_ITEM = 1, ACT_FROM = 2, ACT_USES = 3, ACT_PREV = 4, ACT_NEXT = 5, ACT_BACK = 6;
	private final List<Hot> hots = new ArrayList<>();

	public NeogulItemsScreen(String key, boolean uses, Screen parent) {
		super(LunaCompat.textLiteral("너굴 아이템"));
		this.parent = parent;
		this.selected = key;
		this.usesMode = uses;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		if (parent != null) {
			LunaCompat.setScreen(parent);
			return;
		}
		super.onClose();
	}

	private void layout() {
		int maxCards = Math.max(1, (height - 24 - HEAD - FOOT) / CARD);
		int need = Math.max(1, rows().size());
		perPage = Math.min(maxCards, need);
		ph = HEAD + perPage * CARD + FOOT;
		px = (width - PW) / 2;
		py = Math.max(4, (height - ph) / 2);
	}

	// ==================== 줄(방법) ====================

	/** {stationId, List<Ing> or null, outKey, outCount, note}. 얻는 법 = 기본 출처 + 만드는 법, 쓰임새 = 재료로 쓰는 제작법. */
	private List<Object[]> rows() {
		List<Object[]> out = new ArrayList<>();
		NeogulData.Entry sel = NeogulData.get(selected);
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

	/** 어디서 종류마다 카드 띠 색(농사 초록, 낚시 파랑, 던전 빨강 …). */
	private static int stationColor(String id) {
		if (id == null) {
			return 0xFF8B8B8B;
		}
		if (id.startsWith("dg")) {
			return 0xFFB04A3C;
		}
		return switch (id) {
			case "farm", "scarecrow" -> 0xFF5E9E3A;
			case "fish", "dodo" -> 0xFF3C7DB0;
			case "mine" -> 0xFF7A7A86;
			case "is_harvest", "is_ocean", "is_mud", "is_meat" -> 0xFF2F9A8C;
			case "shop", "smith" -> 0xFF8A5CB0;
			case "cook" -> 0xFFC9822E;
			default -> 0xFF8B8B8B;
		};
	}

	// ==================== 그리기 ====================

	private void render0(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		lastMouseX = mouseX;
		lastMouseY = mouseY;
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, 0xA6000000);
		hots.clear();
		layout();
		LunaDraw.mcPanel(ctx, px, py, PW, ph);
		drawHead(ctx, mouseX, mouseY);
		drawCards(ctx, mouseX, mouseY);
		drawFoot(ctx, mouseX, mouseY);

		Hot h = hotAt(mouseX, mouseY);
		if (h != null && h.tip() != null && !h.tip().isEmpty()) {
			ctx.setComponentTooltipForNextFrame(font, h.tip(), mouseX, mouseY);
		}
	}

	private static List<Component> tip(String... lines) {
		List<Component> out = new ArrayList<>();
		for (String l : lines) {
			if (l != null && !l.isEmpty()) {
				out.add(LunaCompat.textLiteral(l));
			}
		}
		return out;
	}

	private void drawHead(GuiGraphicsExtractor ctx, int mx, int my) {
		NeogulData.Entry sel = NeogulData.get(selected);
		int x = px + 8;
		int y = py + 7;
		slot(ctx, x, y, sel == null ? null : NeogulData.icon(sel, 1), false, mx, my);
		String name = sel == null ? "?" : sel.name();
		String cat = "";
		if (sel != null) {
			for (NeogulData.Category c : NeogulData.categories()) {
				if (c.id().equals(sel.cat())) {
					cat = c.name();
				}
			}
		}
		int tabW = 44;
		int ux = px + PW - 8 - tabW;
		int fx = ux - tabW - 2;
		ctx.text(font, LunaCompat.textLiteral(LunaDraw.ellipsize(font, name, fx - x - 26)), x + 22, y + 1, TEXT, false);
		ctx.text(font, LunaCompat.textLiteral(cat), x + 22, y + 11, TEXT_SUB, false);
		if (sel != null) {
			hots.add(new Hot(x + 1, y + 1, 16, 16, tip(sel.name()), null, ACT_NONE));
		}
		tab(ctx, fx, y + 1, tabW, "얻는 법", !usesMode, mx, my, ACT_FROM);
		tab(ctx, ux, y + 1, tabW, "쓰임새", usesMode, mx, my, ACT_USES);
		// 머리 아래 가는 홈(바닐라 판 구분선)
		int ly = py + HEAD - 3;
		ctx.fill(px + 6, ly, px + PW - 6, ly + 1, 0xFF8B8B8B);
		ctx.fill(px + 6, ly + 1, px + PW - 6, ly + 2, 0xFFFFFFFF);
	}

	private void tab(GuiGraphicsExtractor ctx, int x, int y, int w, String label, boolean on, int mx, int my, int action) {
		boolean hover = LunaDraw.in(mx, my, x, y, w, 14);
		int base = on ? 0xFFFFFFFF : (hover ? 0xFFDADADA : 0xFFB4B4B4);
		LunaDraw.mcPanel(ctx, x, y, w, 14, base);
		int tw = font.width(label);
		ctx.text(font, LunaCompat.textLiteral(label), x + (w - tw) / 2, y + 3, on ? 0xFF202020 : TEXT_SUB, false);
		hots.add(new Hot(x, y, w, 14, null, null, action));
	}

	private void drawCards(GuiGraphicsExtractor ctx, int mx, int my) {
		List<Object[]> rows = rows();
		int pages = Math.max(1, (rows.size() + perPage - 1) / perPage);
		page = Math.max(0, Math.min(pages - 1, page));
		int top = py + HEAD;
		if (rows.isEmpty()) {
			String msg = usesMode ? "이 아이템이 쓰이는 곳은 아직 없어요" : "얻는 법을 아직 몰라요";
			ctx.text(font, LunaCompat.textLiteral(msg), px + (PW - font.width(msg)) / 2, top + CARD / 2 - 4, TEXT_SUB, false);
			return;
		}
		for (int i = 0; i < perPage; i++) {
			int idx = page * perPage + i;
			if (idx >= rows.size()) {
				break;
			}
			drawCard(ctx, rows.get(idx), px + 6, top + i * CARD, PW - 12, mx, my);
		}
	}

	@SuppressWarnings("unchecked")
	private void drawCard(GuiGraphicsExtractor ctx, Object[] r, int x, int y, int w, int mx, int my) {
		String stId = (String) r[0];
		NeogulData.Station st = NeogulData.station(stId);
		String note = (String) r[4];
		int h = CARD - 4;
		// 카드 바탕(살짝 들어간 판) + 왼쪽 색 띠
		ctx.fill(x, y, x + w, y + h, 0xFFB9B9B9);
		ctx.fill(x, y, x + w, y + 1, 0xFF8B8B8B);
		ctx.fill(x, y, x + 1, y + h, 0xFF8B8B8B);
		ctx.fill(x, y + h - 1, x + w, y + h, 0xFFE4E4E4);
		ctx.fill(x + w - 1, y, x + w, y + h, 0xFFE4E4E4);
		ctx.fill(x + 1, y + 1, x + 4, y + h - 1, stationColor(stId));
		// 어디서: 아이콘 + 이름 + 덧붙임
		ItemStack sIcon = NeogulData.icon(st);
		if (!sIcon.isEmpty()) {
			ctx.item(sIcon, x + 7, y + 2);
		}
		String sName = st == null ? stId : st.name();
		ctx.text(font, LunaCompat.textLiteral(sName), x + 26, y + 3, TEXT, false);
		String sub = note != null && !note.isEmpty() ? note : (st == null ? "" : st.note());
		int nameW = font.width(sName);
		String subShown = LunaDraw.ellipsize(font, sub, w - 30);
		ctx.text(font, LunaCompat.textLiteral(subShown), x + 26, y + 12, TEXT_SUB, false);
		hots.add(new Hot(x + 5, y + 1, Math.max(nameW, font.width(subShown)) + 24, 20,
				tip("§e" + sName, st == null ? null : "§7" + st.note(), note == null || note.isEmpty() ? null : "§f" + note), null, ACT_NONE));
		// 재료 → 결과
		int ry = y + h - SLOT - 2;
		int outX = x + w - SLOT - 4;
		int arrowX = outX - 28;
		List<NeogulData.Ing> ins = (List<NeogulData.Ing>) r[1];
		int ix = x + 7;
		if (ins != null) {
			for (NeogulData.Ing in : ins) {
				if (ix + SLOT > arrowX - 2) {
					break;
				}
				NeogulData.Entry ie = NeogulData.get(in.key());
				slot(ctx, ix, ry, ie == null ? null : NeogulData.icon(ie, in.count()), true, mx, my);
				hots.add(new Hot(ix + 1, ry + 1, 16, 16,
						tip((ie == null ? in.key() : ie.name()) + (in.count() > 1 ? " §7x" + in.count() : "")), in.key(), ACT_ITEM));
				ix += SLOT;
			}
		} else if (st != null) {
			// 기본 출처(재료 없음): 어디서 칸을 재료 자리에
			slot(ctx, ix, ry, sIcon, false, mx, my);
			hots.add(new Hot(ix + 1, ry + 1, 16, 16, tip("§e" + sName, "§7" + st.note()), null, ACT_NONE));
		}
		arrow(ctx, arrowX, ry + 1);
		String outKey = (String) r[2];
		int outCount = (Integer) r[3];
		NeogulData.Entry oe = NeogulData.get(outKey);
		slot(ctx, outX, ry, oe == null ? null : NeogulData.icon(oe, outCount), true, mx, my);
		hots.add(new Hot(outX + 1, ry + 1, 16, 16,
				tip((oe == null ? outKey : oe.name()) + (outCount > 1 ? " §7x" + outCount : "")), outKey, ACT_ITEM));
	}

	private void drawFoot(GuiGraphicsExtractor ctx, int mx, int my) {
		int pages = Math.max(1, (rows().size() + perPage - 1) / perPage);
		int y = py + ph - FOOT + 3;
		if (pages > 1) {
			int cx = px + PW / 2;
			button(ctx, cx - 40, y, 14, "<", mx, my, ACT_PREV);
			button(ctx, cx + 26, y, 14, ">", mx, my, ACT_NEXT);
			String ps = (page + 1) + " / " + pages;
			ctx.text(font, LunaCompat.textLiteral(ps), cx - font.width(ps) / 2, y + 3, TEXT, false);
		}
		if (!history.isEmpty()) {
			button(ctx, px + 8, y, 30, "뒤로", mx, my, ACT_BACK);
		}
	}

	private void button(GuiGraphicsExtractor ctx, int x, int y, int w, String label, int mx, int my, int action) {
		boolean hover = LunaDraw.in(mx, my, x, y, w, 14);
		LunaDraw.mcPanel(ctx, x, y, w, 14, hover ? 0xFFE6E6E6 : 0xFFC6C6C6);
		ctx.text(font, LunaCompat.textLiteral(label), x + (w - font.width(label)) / 2 + 1, y + 3, TEXT, false);
		hots.add(new Hot(x, y, w, 14, null, null, action));
	}

	private void slot(GuiGraphicsExtractor ctx, int x, int y, ItemStack stack, boolean count, int mx, int my) {
		LunaDraw.mcSlot(ctx, x, y);
		if (stack != null && !stack.isEmpty()) {
			try {
				ctx.item(stack, x + 1, y + 1);
				if (count) {
					LunaCompat.drawItemOverlay(ctx, font, stack, x + 1, y + 1);
				}
			} catch (Throwable ignored) {
			}
		}
		if (LunaDraw.in(mx, my, x + 1, y + 1, 16, 16)) {
			ctx.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
		}
	}

	private void arrow(GuiGraphicsExtractor ctx, int x, int y) {
		int c = 0xFF8B8B8B;
		ctx.fill(x, y + 6, x + 15, y + 9, c);
		for (int i = 0; i < 7; i++) {
			ctx.fill(x + 15 + i, y + i, x + 16 + i, y + 15 - i, c);
		}
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
		page = 0;
	}

	private void back() {
		if (!history.isEmpty()) {
			Object[] prev = history.remove(history.size() - 1);
			selected = (String) prev[0];
			usesMode = (Boolean) prev[1];
			page = 0;
		}
	}

	private void turn(int d) {
		int pages = Math.max(1, (rows().size() + Math.max(1, perPage) - 1) / Math.max(1, perPage));
		page = ((page + d) % pages + pages) % pages;
	}

	private boolean click0(double mouseX, double mouseY, int button) {
		Hot h = hotAt(mouseX, mouseY);
		if (h == null) {
			return false;
		}
		switch (h.action()) {
			case ACT_ITEM -> open(h.key(), button == 1);
			case ACT_FROM -> {
				usesMode = false;
				page = 0;
			}
			case ACT_USES -> {
				usesMode = true;
				page = 0;
			}
			case ACT_PREV -> turn(-1);
			case ACT_NEXT -> turn(1);
			case ACT_BACK -> back();
			default -> {
				return true;
			}
		}
		LunaCompat.playUiSound(minecraft, "UI_BUTTON_CLICK", 1.0f);
		return true;
	}

	private boolean key0(int keyCode) {
		if (keyCode == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (keyCode == InputConstants.KEY_BACKSPACE) {
			back();
			return true;
		}
		int rk = kr.lunaslight.mod.module.impl.misc.NeogulItemsModule.recipeKey();
		int uk = kr.lunaslight.mod.module.impl.misc.NeogulItemsModule.usesKey();
		if ((keyCode == rk && rk > 0) || (keyCode == uk && uk > 0)) {
			Hot h = hotAt(lastMouseX, lastMouseY);
			if (h != null && h.action() == ACT_ITEM && h.key() != null) {
				open(h.key(), keyCode == uk);
			} else if (keyCode == rk) {
				onClose();
			}
			return true;
		}
		if (keyCode == InputConstants.KEY_LEFT) {
			turn(-1);
			return true;
		}
		if (keyCode == InputConstants.KEY_RIGHT) {
			turn(1);
			return true;
		}
		return false;
	}
}
