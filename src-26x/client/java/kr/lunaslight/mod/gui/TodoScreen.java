package kr.lunaslight.mod.gui;

import com.mojang.blaze3d.platform.InputConstants;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.TodoList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-473차: <b>할 일 목록 화면</b>({@link TodoList}).
 * <pre>
 *  할 일                                         [x]
 *  ┌ [ ] 감자 300개 캐기   아이템 얻기  매일  120/300 ━━━   (x) ┐
 *  └ [v] 서버 추천하기     채팅 글      매일                (x) ┘
 *  ── 새 할 일 ──
 *  기본  [직접 체크] [블록 캐기] [몹 잡기] …
 *  너굴  [서버 추천] [핫타임] [접속보상] …
 *  [이름............] [대상......] [300] 개  [매일] [너굴] [추가]
 * </pre>
 * 위쪽 목록: 네모를 누르면 직접 체크/해제, 오른쪽 x는 지우기. 아래: 행동 칩을 고르면 이름, 대상, 수가 채워지고 고쳐서 [추가](엔터).
 */
public class TodoScreen extends LunaScreenBase {

	private final LunaVScale lunaV = new LunaVScale(this, 480, 320);

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		try {
			LunaClientScreen.applyScreenTheme();
			try {
				draw(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
			} finally {
				LunaClientScreen.restoreScreenTheme();
			}
		} finally {
			lunaV.end(ctx, e);
		}
	}

	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return click(mouseX * k, mouseY * k);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		scroll = Math.max(0, Math.min(maxScroll, scroll - (float) verticalAmount * 24f));
		return true;
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		return key(keyCode, modifiers);
	}

	@Override
	protected boolean lunaCharTyped(char chr) {
		if (chr < ' ') {
			return true;
		}
		StringBuilder b = field();
		if (focus == 2) {
			if (chr >= '0' && chr <= '9' && b.length() < 6) {
				b.append(chr);
			}
		} else if (b.length() < 40) {
			b.append(chr);
		}
		return true;
	}

	@Override
	protected boolean lunaWantsText() {
		return focus != 2;
	}

	// ==================== 상태 ====================

	private final Screen parent;
	private final StringBuilder name = new StringBuilder();
	private final StringBuilder target = new StringBuilder();
	private final StringBuilder goal = new StringBuilder("1");
	private String type = TodoList.MANUAL;
	private boolean daily;
	private boolean neogul;
	private int focus;   // 0 이름, 1 대상, 2 수
	private String chosen = "기본/직접 체크";
	private float scroll;
	private int maxScroll;
	private String tip;

	private static final int TOP_Y = 14;
	private static final int LIST_Y = 42;
	private static final int ROW_H = 24;
	private static final int CHIP_H = 16;
	private static final int FIELD_H = 18;
	private int left, contentW, panelY;

	private record Box(int x, int y, int w, int h, Runnable run) {
	}

	private final List<Box> boxes = new ArrayList<>();

	public TodoScreen(Screen parent) {
		super(LunaCompat.textLiteral("할 일"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private StringBuilder field() {
		return focus == 1 ? target : focus == 2 ? goal : name;
	}

	private void layout() {
		contentW = Math.min(width - 32, 560);
		left = (width - contentW) / 2;
		panelY = height - 8 - (CHIP_H * 2 + 6 + FIELD_H + 30);
	}

	private void pick(TodoList.Preset p) {
		chosen = p.group() + "/" + p.label();
		type = p.type();
		name.setLength(0);
		name.append(p.text());
		target.setLength(0);
		target.append(p.target());
		goal.setLength(0);
		goal.append(p.goal());
		daily = p.daily();
		neogul = p.neogul();
		focus = 0;
	}

	private void addTask() {
		String n = name.toString().trim();
		String tg = target.toString().trim();
		if (n.isEmpty() && TodoList.MANUAL.equals(type)) {
			return;   // 직접 체크는 이름이 있어야
		}
		if (n.isEmpty() && TodoList.CHAT.equals(type) && tg.isEmpty()) {
			return;
		}
		TodoList.Task t = new TodoList.Task();
		t.text = n.isEmpty() ? (tg.isEmpty() ? TodoList.typeName(type) : tg + " " + TodoList.typeName(type)) : n;
		t.type = type;
		t.target = TodoList.needsTarget(type) ? tg : "";
		int g = 1;
		try {
			g = Integer.parseInt(goal.toString());
		} catch (NumberFormatException ignored) {
		}
		t.goal = TodoList.needsGoal(type) ? Math.max(1, g) : 1;
		t.daily = daily;
		t.neogul = neogul;
		TodoList.add(t);
		name.setLength(0);
		focus = 0;
		LunaCompat.playUiSound(minecraft, "UI_BUTTON_CLICK", 1.2f);
	}

	// ==================== 그리기 ====================

	private void card(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		LunaDraw.card3d(ctx, x, y, w, h, 4, LunaClientScreen.themeCard(), LunaClientScreen.themeLine());
	}

	private void big(GuiGraphicsExtractor ctx, String s, int x, int y, int color) {
		if (!LunaCompat.guiTransformSupported(ctx)) {
			LunaDraw.text(ctx, font, s, x, y + 8, color);
			return;
		}
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, x, y + 4);
			LunaCompat.guiScale(ctx, 2f, 2f);
			LunaDraw.text(ctx, font, s, 0, 0, color);
		} finally {
			LunaCompat.guiPop(ctx);
		}
	}

	private void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		layout();
		boxes.clear();
		tip = null;
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		int dim = LunaClientScreen.themeDim();
		int acc = LunaClientScreen.themeAccent();
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.OVERLAY);
		ctx.fill(0, 0, width, height, LunaDraw.applyAlpha((LunaClientScreen.themeBg() & 0x00FFFFFF)
			| (LunaClientScreen.themeLight() ? 0xF5000000 : 0xEE000000)));

		big(ctx, "할 일", left, TOP_Y, txt);
		String note = "행동을 하면 저절로 체크돼요 | 네모를 눌러 직접 체크";
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, note, contentW - 120), left + 60, LunaDraw.textY(TOP_Y + 6, 14), dim);
		boolean ch = LunaDraw.in(mouseX, mouseY, left + contentW - 16, TOP_Y + 4, 16, 16);
		LunaIcons.drawInBox(ctx, font, LunaIcons.CLOSE, left + contentW - 16, TOP_Y + 4, 16, ch ? acc : sub);
		boxes.add(new Box(left + contentW - 18, TOP_Y + 2, 20, 20, this::onClose));

		// ---- 목록 ----
		int bottom = panelY - 8;
		List<TodoList.Task> list = new ArrayList<>(TodoList.all());
		lunaV.scissor(ctx, left - 2, LIST_Y, left + contentW + 2, bottom);
		int y = LIST_Y - Math.round(scroll);
		if (list.isEmpty()) {
			card(ctx, left, y, contentW, 34);
			LunaDraw.textCentered(ctx, font, "아직 할 일이 없어요 - 아래에서 골라 추가해 보세요", left + contentW / 2, LunaDraw.textY(y, 34), sub);
			y += 38;
		}
		for (TodoList.Task t : list) {
			boolean vis = y + ROW_H > LIST_Y && y < bottom;
			if (vis) {
				drawRow(ctx, t, y, mouseX, mouseY, mouseY >= LIST_Y && mouseY < bottom, txt, sub, dim, acc);
			}
			y += ROW_H + 4;
		}
		ctx.disableScissor();
		int total = y + Math.round(scroll) - LIST_Y;
		maxScroll = Math.max(0, total - (bottom - LIST_Y));
		scroll = Math.max(0, Math.min(scroll, maxScroll));

		// ---- 새 할 일 ----
		int py = panelY;
		LunaDraw.roundRect(ctx, left, py - 4, contentW, 1, 0, LunaClientScreen.themeLine());
		LunaDraw.text(ctx, font, "새 할 일", left, py + 2, sub);
		py += 14;
		for (String group : new String[]{"기본", "너굴"}) {
			LunaDraw.text(ctx, font, group, left, LunaDraw.textY(py, CHIP_H), dim);
			int x = left + 28;
			for (TodoList.Preset p : TodoList.PRESETS) {
				if (!p.group().equals(group)) {
					continue;
				}
				int w = LunaDraw.width(font, p.label()) + 14;
				if (x + w > left + contentW) {
					break;
				}
				boolean sel = chosen.equals(p.group() + "/" + p.label());
				boolean hov = LunaDraw.in(mouseX, mouseY, x, py, w, CHIP_H);
				LunaDraw.roundRect(ctx, x, py, w, CHIP_H, CHIP_H / 2, sel ? LunaDraw.withAlpha(acc, 0xE6) : LunaClientScreen.ink(hov ? 0x24 : 0x12));
				int tc = sel ? (kr.lunaslight.mod.util.LunaTheme.luminance(acc) > 0.6f ? 0xFF15181C : 0xFFFFFFFF) : (hov ? txt : sub);
				LunaDraw.text(ctx, font, p.label(), x + 7, LunaDraw.textY(py, CHIP_H), tc);
				boxes.add(new Box(x, py, w, CHIP_H, () -> pick(p)));
				if (hov) {
					tip = TodoList.typeName(p.type()) + (p.neogul() ? " | 너굴마을에서만 셈" : "") + (p.daily() ? " | 매일 다시" : "");
				}
				x += w + 4;
			}
			py += CHIP_H + 3;
		}
		py += 4;
		// 입력칸 줄
		boolean needT = TodoList.needsTarget(type);
		boolean needG = TodoList.needsGoal(type);
		int addW = 40;
		int togW = 34;
		int goalW = needG ? 60 : 0;
		int targW = needT ? 100 : 0;
		int nameW = contentW - addW - togW * 2 - goalW - targW - 4 * (2 + (needG ? 1 : 0) + (needT ? 1 : 0)) - 4;
		int x = left;
		fieldBox(ctx, x, py, nameW, 0, "이름 (예: 감자 300개 캐기)", name, mouseX, mouseY, txt, dim, acc);
		x += nameW + 4;
		if (needT) {
			String hint = TodoList.CHAT.equals(type) ? "채팅에 뜰 글" : TodoList.KILL.equals(type) ? "몹 이름 (비우면 아무거나)" : "이름에 든 글 (비우면 아무거나)";
			fieldBox(ctx, x, py, targW, 1, hint, target, mouseX, mouseY, txt, dim, acc);
			x += targW + 4;
		}
		if (needG) {
			fieldBox(ctx, x, py, goalW - 18, 2, "수", goal, mouseX, mouseY, txt, dim, acc);
			LunaDraw.text(ctx, font, TodoList.unit(type), x + goalW - 15, LunaDraw.textY(py, FIELD_H), sub);
			x += goalW + 4;
		}
		x = toggle(ctx, x, py, togW, "매일", daily, () -> daily = !daily, mouseX, mouseY, txt, sub, acc, "자정에 다시 0부터");
		x = toggle(ctx, x, py, togW, "너굴", neogul, () -> neogul = !neogul, mouseX, mouseY, txt, sub, acc, "너굴마을에 있을 때만 셈");
		boolean ah = LunaDraw.in(mouseX, mouseY, x, py, addW, FIELD_H);
		LunaDraw.roundRect(ctx, x, py, addW, FIELD_H, 5, LunaDraw.withAlpha(acc, ah ? 0xFF : 0xD8));
		int atc = kr.lunaslight.mod.util.LunaTheme.luminance(acc) > 0.6f ? 0xFF15181C : 0xFFFFFFFF;
		LunaDraw.textCentered(ctx, font, "추가", x + addW / 2, LunaDraw.textY(py, FIELD_H), atc);
		boxes.add(new Box(x, py, addW, FIELD_H, this::addTask));
		LunaDraw.text(ctx, font, "행동: " + TodoList.typeName(type) + "  |  엔터로 추가, Tab으로 칸 이동", left, py + FIELD_H + 5, dim);

		if (tip != null) {
			ctx.setTooltipForNextFrame(font, LunaCompat.textLiteral(tip), mouseX, mouseY);
		}
	}

	private void drawRow(GuiGraphicsExtractor ctx, TodoList.Task t, int y, int mouseX, int mouseY, boolean inList,
			int txt, int sub, int dim, int acc) {
		boolean hov = inList && LunaDraw.in(mouseX, mouseY, left, y, contentW, ROW_H);
		card(ctx, left, y, contentW, ROW_H);
		if (hov) {
			LunaDraw.roundRect(ctx, left, y, contentW, ROW_H, 4, LunaClientScreen.ink(0x0C));
		}
		// 체크 네모
		int bx = left + 6;
		int by = y + (ROW_H - 12) / 2;
		boolean bh = inList && LunaDraw.in(mouseX, mouseY, bx - 2, by - 2, 16, 16);
		if (t.done) {
			LunaDraw.roundRect(ctx, bx, by, 12, 12, 3, 0xFF4CC38A);
			LunaIcons.drawInBox(ctx, font, LunaIcons.CHECK, bx, by, 12, 0xFFFFFFFF);
		} else {
			LunaDraw.roundRect(ctx, bx, by, 12, 12, 3, bh ? acc : LunaClientScreen.ink(0x50));
			LunaDraw.roundRect(ctx, bx + 1, by + 1, 10, 10, 2, LunaClientScreen.themeCard());
		}
		if (inList) {
			boxes.add(new Box(bx - 2, by - 2, 16, 16, () -> TodoList.toggle(t)));
		}
		String nm = t.text == null || t.text.isBlank() ? TodoList.typeName(t.type) : t.text;
		// 오른쪽: 지우기, 진행
		int rx = left + contentW - 6;
		boolean xh = inList && LunaDraw.in(mouseX, mouseY, rx - 14, y + (ROW_H - 14) / 2, 14, 14);
		if (hov) {
			LunaIcons.drawInBox(ctx, font, LunaIcons.TRASH, rx - 14, y + (ROW_H - 14) / 2, 14, xh ? 0xFFFF6B6B : dim);
			if (inList) {
				boxes.add(new Box(rx - 14, y + (ROW_H - 14) / 2, 14, 14, () -> TodoList.remove(t)));
			}
		}
		rx -= 20;
		if (TodoList.needsGoal(t.type) && t.goal > 1) {
			String pr = t.progress + "/" + t.goal + (TodoList.PLAY.equals(t.type) ? "분" : "");
			int bw = 50;
			float f = Math.min(1f, t.progress / (float) Math.max(1, t.goal));
			LunaDraw.roundRect(ctx, rx - bw, y + ROW_H / 2 - 1, bw, 3, 1, LunaClientScreen.ink(0x20));
			LunaDraw.roundRect(ctx, rx - bw, y + ROW_H / 2 - 1, Math.max(2, Math.round(bw * f)), 3, 1, t.done ? 0xFF4CC38A : acc);
			rx -= bw + 6;
			int pw = LunaDraw.width(font, pr);
			LunaDraw.text(ctx, font, pr, rx - pw, LunaDraw.textY(y, ROW_H), sub);
			rx -= pw + 8;
		}
		// 꼬리표: 행동, 매일, 너굴
		List<String> tags = new ArrayList<>();
		if (t.neogul) {
			tags.add("너굴");
		}
		if (t.daily) {
			tags.add("매일");
		}
		tags.add(TodoList.typeName(t.type));
		for (String tg : tags) {
			int tw = LunaDraw.width(font, tg) + 10;
			rx -= tw;
			LunaDraw.roundRect(ctx, rx, y + (ROW_H - 14) / 2, tw, 14, 7, LunaClientScreen.ink(0x14));
			LunaDraw.text(ctx, font, tg, rx + 5, LunaDraw.textY(y + (ROW_H - 14) / 2, 14), dim);
			rx -= 4;
		}
		int nx = bx + 20;
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, nm, Math.max(20, rx - nx - 6)), nx, LunaDraw.textY(y, ROW_H), t.done ? dim : txt);
		if (t.done) {
			int lw = Math.min(LunaDraw.width(font, nm), rx - nx - 6);
			ctx.fill(nx, y + ROW_H / 2, nx + Math.max(0, lw), y + ROW_H / 2 + 1, LunaDraw.applyAlpha(dim));
		}
		if (hov && t.target != null && !t.target.isBlank()) {
			tip = "대상: " + t.target.replace("|", ", ");
		}
	}

	private void fieldBox(GuiGraphicsExtractor ctx, int x, int y, int w, int idx, String hint, StringBuilder b,
			int mouseX, int mouseY, int txt, int dim, int acc) {
		boolean f = focus == idx;
		LunaDraw.roundRect(ctx, x, y, w, FIELD_H, 5, f ? acc : LunaClientScreen.themeLine());
		LunaDraw.roundRect(ctx, x + 1, y + 1, w - 2, FIELD_H - 2, 4, LunaClientScreen.themeCard());
		String s = b + (f && idx != 2 && lunaPreedit != null ? lunaPreedit : "");
		int ty = LunaDraw.textY(y, FIELD_H);
		if (s.isEmpty() && !f) {
			LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, hint, w - 10), x + 5, ty, dim);
		} else {
			String shown = s;
			while (shown.length() > 0 && LunaDraw.width(font, shown) > w - 12) {
				shown = shown.substring(1);
			}
			LunaDraw.text(ctx, font, shown, x + 5, ty, txt);
			if (f && (System.currentTimeMillis() / 500) % 2 == 0) {
				int cx = x + 5 + LunaDraw.width(font, shown) + 1;
				ctx.fill(cx, y + 4, cx + 1, y + FIELD_H - 4, LunaDraw.applyAlpha(txt));
			}
		}
		boxes.add(new Box(x, y, w, FIELD_H, () -> focus = idx));
	}

	private int toggle(GuiGraphicsExtractor ctx, int x, int y, int w, String label, boolean on, Runnable run,
			int mouseX, int mouseY, int txt, int sub, int acc, String hint) {
		boolean hov = LunaDraw.in(mouseX, mouseY, x, y, w, FIELD_H);
		LunaDraw.roundRect(ctx, x, y, w, FIELD_H, 5, on ? LunaDraw.withAlpha(acc, 0x40) : LunaClientScreen.ink(hov ? 0x20 : 0x10));
		if (on) {
			LunaDraw.roundRect(ctx, x + 4, y + FIELD_H / 2 - 2, 4, 4, 2, acc);
		}
		LunaDraw.text(ctx, font, label, x + (on ? 11 : 7), LunaDraw.textY(y, FIELD_H), on ? txt : sub);
		boxes.add(new Box(x, y, w, FIELD_H, run));
		if (hov) {
			tip = hint;
		}
		return x + w + 4;
	}

	// ==================== 입력 ====================

	private boolean click(double mx, double my) {
		layout();
		for (int i = boxes.size() - 1; i >= 0; i--) {
			Box b = boxes.get(i);
			if (LunaDraw.in(mx, my, b.x(), b.y(), b.w(), b.h())) {
				b.run().run();
				return true;
			}
		}
		return true;
	}

	private boolean key(int keyCode, int modifiers) {
		if (keyCode == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (keyCode == InputConstants.KEY_TAB) {
			int n = 3;
			do {
				focus = (focus + ((modifiers & 0x1) != 0 ? n - 1 : 1)) % n;
			} while ((focus == 1 && !TodoList.needsTarget(type)) || (focus == 2 && !TodoList.needsGoal(type)));
			return true;
		}
		if (keyCode == InputConstants.KEY_BACKSPACE) {
			StringBuilder b = field();
			if (b.length() > 0) {
				if ((modifiers & 0x2) != 0) {
					b.setLength(0);
				} else {
					b.setLength(b.length() - 1);
				}
			}
			return true;
		}
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			addTask();
			return true;
		}
		return true;
	}

	@Override
	public void onClose() {
		TodoList.changed();
		LunaCompat.setScreen(parent);
	}
}
