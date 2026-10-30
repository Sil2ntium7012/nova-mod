package kr.lunaslight.mod.gui;

import com.mojang.blaze3d.platform.InputConstants;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaHangul;
import kr.lunaslight.mod.util.NeogulCosmetics;
import kr.lunaslight.mod.util.NeogulData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-431차(사용자: "치장 창도 따로 있으면 좋을 듯, 미리 입어보기랑, 위키에 다 있고"): <b>너굴마을 치장</b>.
 *
 * <p>위: 검색칸 + 등급 칩(전체, 레어, 에픽, 유니크, 전설) + 부위 칩(전체, 모자, 등, 왼손 …) + "획득 불가도 보기" 스위치(처음엔 꺼짐 =
 * 위키가 획득 가능으로 보여 주는 것만). 왼쪽: 치장 칸(서버 리소스팩의 nexo 모델로 그림, 팩이 꺼져 있으면 이름 첫 글자).
 * 오른쪽: <b>미리 입어보기</b> - 내 모습 복사본에 모자는 머리, 왼손은 왼손에 실제로 씌우고, 등이나 효과처럼 몸에 못 붙이는 건 옆 칸에 크게.
 * 칸을 누르면 입고, 한 번 더 누르면 벗는다(부위마다 하나). 미리보기는 끌어서 돌린다. 서버에 아무것도 보내지 않는다.
 */
public class NeogulCosmeticsScreen extends LunaScreenBase {

	private final LunaVScale lunaV = new LunaVScale(this, 480, 320);
	/** 이번 프레임 가상 좌표 1단위 = 실제 GUI 몇 분의 1인지(엔티티 그림은 행렬을 안 따라서 직접 바꾼다). */
	private float curK = 1f;

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		curK = (float) lunaV.k(e);
		try {
			lunaRender0(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
		} finally {
			lunaV.end(ctx, e);
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
	protected boolean lunaMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging) {
			boolean e = lunaV.enter();
			double k = lunaV.k(e);
			lunaV.exit(e);
			spin = (float) Math.max(-260, Math.min(260, spin + deltaX * k * 2));
			return true;
		}
		return false;
	}

	@Override
	protected boolean lunaMouseReleased(double mouseX, double mouseY, int button) {
		dragging = false;
		return false;
	}

	@Override
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		scroll = Math.max(0, Math.min(maxScroll, scroll - (float) verticalAmount * 28f));
		return true;
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		boolean e = lunaV.enter();
		try {
			return lunaKeyPressed0(keyCode, modifiers);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaCharTyped(char chr) {
		if (chr >= ' ' && query.length() < 30) {
			query.append(chr);
			scroll = 0;
		}
		return true;
	}

	@Override
	protected boolean lunaWantsText() {
		return true;
	}

	// ==================== 상태 ====================

	private static final String ALL = "전체";

	private final Screen parent;
	private final StringBuilder query = new StringBuilder();
	private static String grade = ALL;
	private static String slot = ALL;
	private static boolean showLocked;
	/** 입어 본 것: 부위 → 치장. 창을 닫았다 열어도 남는다(게임을 끄면 사라짐). */
	private static final Map<String, NeogulCosmetics.Cosmetic> WORN = new LinkedHashMap<>();
	private float scroll;
	private int maxScroll;
	private float spin;
	private boolean dragging;
	private float lockAnim;
	private String tip;

	private RemotePlayer doll;
	private Object dollLevel;

	private static final int TOP_Y = 14;
	private static final int SEARCH_Y = 40;
	private static final int SEARCH_H = 20;
	private static final int CHIP_Y = 66;
	private static final int CHIP_H = 16;
	private static final int CHIP2_Y = CHIP_Y + CHIP_H + 5;
	private static final int CELL_W = 58;
	private static final int CELL_H = 66;
	private static final int PANEL_W = 176;
	private int left, contentW, gridX, gridW, panelX, contentTop;

	private record Hit(int x, int y, int w, int h, NeogulCosmetics.Cosmetic c) {
	}

	private record Chip(int x, int y, int w, String value, boolean slotRow) {
	}

	private final List<Hit> hits = new ArrayList<>();
	private final List<Chip> chips = new ArrayList<>();
	private int[] lockBox = new int[4];
	private int[] clearBox = new int[4];
	private int[] dollBox = new int[4];

	public NeogulCosmeticsScreen(Screen parent) {
		super(LunaCompat.textLiteral("너굴마을 치장"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void layout() {
		contentW = Math.min(width - 32, 760);
		left = (width - contentW) / 2;
		panelX = left + contentW - PANEL_W;
		gridX = left;
		gridW = contentW - PANEL_W - 10;
		contentTop = CHIP2_Y + CHIP_H + 10;
	}

	private boolean matches(NeogulCosmetics.Cosmetic c, String g, String s) {
		if (!showLocked && !c.obtainable()) {
			return false;
		}
		if (!ALL.equals(g) && !c.grade().equals(g)) {
			return false;
		}
		if (!ALL.equals(s) && !c.slot().equals(s)) {
			return false;
		}
		String q = query.toString().trim();
		return q.isEmpty() || LunaHangul.matches(c.name(), q) || LunaHangul.matches(c.id(), q);
	}

	private List<NeogulCosmetics.Cosmetic> visible() {
		List<NeogulCosmetics.Cosmetic> out = new ArrayList<>();
		for (NeogulCosmetics.Cosmetic c : NeogulCosmetics.ALL) {
			if (matches(c, grade, slot)) {
				out.add(c);
			}
		}
		return out;
	}

	private int count(String g, String s) {
		int n = 0;
		for (NeogulCosmetics.Cosmetic c : NeogulCosmetics.ALL) {
			if (matches(c, g, s)) {
				n++;
			}
		}
		return n;
	}

	private static int gradeColor(String g) {
		for (int i = 0; i < NeogulCosmetics.GRADES.length; i++) {
			if (NeogulCosmetics.GRADES[i].equals(g)) {
				return NeogulCosmetics.GRADE_COLORS[i];
			}
		}
		return LunaClientScreen.themeAccent();
	}

	private static ItemStack stackOf(NeogulCosmetics.Cosmetic c) {
		try {
			return NeogulData.modelStack(c.model());
		} catch (Throwable t) {
			return null;
		}
	}

	private void card(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		LunaDraw.card3d(ctx, x, y, w, h, 4, LunaClientScreen.themeCard(), LunaClientScreen.themeLine());
	}

	// ==================== 그리기 ====================

	private void lunaRender0(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaClientScreen.applyScreenTheme();
		try {
			draw(ctx, mouseX, mouseY, delta);
		} finally {
			LunaClientScreen.restoreScreenTheme();
		}
	}

	private void draw(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		layout();
		tip = null;
		hits.clear();
		chips.clear();
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		int dim = LunaClientScreen.themeDim();
		int acc = LunaClientScreen.themeAccent();
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.OVERLAY);
		ctx.fill(0, 0, width, height, LunaDraw.applyAlpha((LunaClientScreen.themeBg() & 0x00FFFFFF)
			| (LunaClientScreen.themeLight() ? 0xF5000000 : 0xEE000000)));

		// 제목 + 닫기
		big(ctx, "치장", left, TOP_Y, txt);
		String note = "너굴마을 위키 기준 | 누르면 입어 보고, 한 번 더 누르면 벗어요";
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, note, contentW - 90), left + 62, LunaDraw.textY(TOP_Y + 6, 14), dim);
		boolean ch = LunaDraw.in(mouseX, mouseY, left + contentW - 16, TOP_Y + 4, 16, 16);
		LunaIcons.drawInBox(ctx, font, LunaIcons.CLOSE, left + contentW - 16, TOP_Y + 4, 16, ch ? acc : sub);

		// 검색칸
		int sw = gridW;
		LunaDraw.roundRect(ctx, left, SEARCH_Y, sw, SEARCH_H, 6, acc);
		LunaDraw.roundRect(ctx, left + 1, SEARCH_Y + 1, sw - 2, SEARCH_H - 2, 5, LunaClientScreen.themeCard());
		LunaIcons.drawInBox(ctx, font, LunaIcons.SEARCH, left + 6, SEARCH_Y + 2, 16, dim);
		String q = query + (lunaPreedit == null ? "" : lunaPreedit);
		int ty = LunaDraw.textY(SEARCH_Y, SEARCH_H);
		if (q.isEmpty()) {
			LunaDraw.text(ctx, font, "치장 이름으로 찾기 (초성, 영타도 돼요)", left + 26, ty, dim);
		} else {
			LunaDraw.text(ctx, font, q, left + 26, ty, txt);
		}
		if ((System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = left + 26 + LunaDraw.width(font, q) + 1;
			ctx.fill(cx, SEARCH_Y + 5, cx + 1, SEARCH_Y + SEARCH_H - 5, LunaDraw.applyAlpha(txt));
		}
		if (!q.isEmpty()) {
			boolean xh = LunaDraw.in(mouseX, mouseY, left + sw - 20, SEARCH_Y + 2, 16, 16);
			LunaIcons.drawInBox(ctx, font, LunaIcons.CLOSE, left + sw - 20, SEARCH_Y + 2, 16, xh ? acc : dim);
		}

		// 획득 불가도 보기(검색칸 오른쪽 = 미리보기 칸 위)
		lockAnim += ((showLocked ? 1f : 0f) - lockAnim) * Math.min(1f, delta * 0.5f + 0.2f);
		int tx = panelX;
		String lockLabel = "획득 불가도 보기";
		LunaDraw.toggle(ctx, tx, SEARCH_Y + 4, 24, 12, lockAnim, true);
		boolean lh = LunaDraw.in(mouseX, mouseY, tx, SEARCH_Y, PANEL_W, SEARCH_H);
		LunaDraw.text(ctx, font, lockLabel, tx + 30, ty, lh ? txt : sub);
		lockBox = new int[]{tx, SEARCH_Y, PANEL_W, SEARCH_H};

		// 등급 칩
		List<String> grades = new ArrayList<>();
		grades.add(ALL);
		for (String g : NeogulCosmetics.GRADES) {
			grades.add(g);
		}
		int x = left;
		for (String g : grades) {
			x = chip(ctx, x, CHIP_Y, g, g, count(g, slot), grade.equals(g), ALL.equals(g) ? acc : gradeColor(g), false, mouseX, mouseY, txt, sub, dim);
		}
		// 부위 칩
		x = left;
		x = chip(ctx, x, CHIP2_Y, ALL, "모든 부위", count(grade, ALL), ALL.equals(slot), acc, true, mouseX, mouseY, txt, sub, dim);
		for (String[] s : NeogulCosmetics.SLOTS) {
			int n = count(grade, s[0]);
			if (n == 0 && !s[0].equals(slot)) {
				continue;
			}
			x = chip(ctx, x, CHIP2_Y, s[0], s[1], n, s[0].equals(slot), sub, true, mouseX, mouseY, txt, sub, dim);
		}

		drawGrid(ctx, mouseX, mouseY, txt, sub, dim, acc);
		drawPanel(ctx, mouseX, mouseY, txt, sub, dim, acc);

		if (tip != null) {
			ctx.setTooltipForNextFrame(font, LunaCompat.textLiteral(tip), mouseX, mouseY);
		}
	}

	private int chip(GuiGraphicsExtractor ctx, int x, int y, String value, String name, int n, boolean sel, int col, boolean slotRow,
			int mouseX, int mouseY, int txt, int sub, int dim) {
		String label = name + " " + n;
		boolean dot = !ALL.equals(value) && !slotRow;
		int w = LunaDraw.width(font, label) + (dot ? 20 : 14);
		if (x + w > gridX + gridW) {
			return x;
		}
		boolean hov = LunaDraw.in(mouseX, mouseY, x, y, w, CHIP_H);
		if (sel) {
			LunaDraw.roundRect(ctx, x, y, w, CHIP_H, CHIP_H / 2, LunaDraw.withAlpha(col, 0xE6));
		} else {
			LunaDraw.roundRect(ctx, x, y, w, CHIP_H, CHIP_H / 2, hov ? LunaClientScreen.ink(0x24) : LunaClientScreen.ink(0x12));
			if (dot) {
				LunaDraw.roundRect(ctx, x + 7, y + CHIP_H / 2 - 2, 4, 4, 2, col);
			}
		}
		int tc = sel ? (kr.lunaslight.mod.util.LunaTheme.luminance(col) > 0.6f ? 0xFF15181C : 0xFFFFFFFF) : (n == 0 ? dim : (hov ? txt : sub));
		LunaDraw.text(ctx, font, label, x + (dot && !sel ? 14 : 7), LunaDraw.textY(y, CHIP_H), tc);
		chips.add(new Chip(x, y, w, value, slotRow));
		return x + w + 4;
	}

	private void drawGrid(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int txt, int sub, int dim, int acc) {
		int bottom = height - 8;
		lunaV.scissor(ctx, gridX - 2, contentTop, gridX + gridW + 2, bottom);
		List<NeogulCosmetics.Cosmetic> list = visible();
		int gap = 6;
		int cols = Math.max(1, (gridW + gap) / (CELL_W + gap));
		int cw = (gridW - gap * (cols - 1)) / cols;
		int y0 = contentTop + 2 - Math.round(scroll);
		if (list.isEmpty()) {
			card(ctx, gridX, y0, gridW, 34);
			LunaDraw.textCentered(ctx, font, showLocked ? "찾는 치장이 없어요" : "찾는 치장이 없어요 | 획득 불가도 보기를 켜 보세요",
				gridX + gridW / 2, LunaDraw.textY(y0, 34), sub);
		}
		boolean packOn = false;
		for (int i = 0; i < list.size(); i++) {
			NeogulCosmetics.Cosmetic c = list.get(i);
			int cx = gridX + (i % cols) * (cw + gap);
			int cy = y0 + (i / cols) * (CELL_H + gap);
			if (cy + CELL_H < contentTop || cy > bottom) {
				hits.add(new Hit(cx, cy, cw, CELL_H, c));
				continue;
			}
			boolean worn = WORN.get(c.slot()) == c;
			boolean hov = LunaDraw.in(mouseX, mouseY, cx, cy, cw, CELL_H) && mouseY >= contentTop && mouseY < bottom;
			int gc = c.gradeColor();
			card(ctx, cx, cy, cw, CELL_H);
			// 등급 띠(위) + 입은 칸 테두리
			LunaDraw.roundRect(ctx, cx + 6, cy + 3, cw - 12, 2, 1, LunaDraw.withAlpha(gc, c.obtainable() ? 0xE0 : 0x70));
			if (worn) {
				LunaDraw.roundRect(ctx, cx, cy, cw, CELL_H, 4, LunaDraw.withAlpha(acc, 0x2E));
			} else if (hov) {
				LunaDraw.roundRect(ctx, cx, cy, cw, CELL_H, 4, LunaDraw.withAlpha(gc, 0x1E));
			}
			ItemStack st = stackOf(c);
			int ix = cx + (cw - 32) / 2;
			int iy = cy + 9;
			if (st != null) {
				packOn = true;
				bigItem(ctx, st, ix, iy, 2f);
			} else {
				LunaDraw.roundRect(ctx, ix + 4, iy + 4, 24, 24, 12, LunaDraw.withAlpha(gc, 0x40));
				String first = c.name().isEmpty() ? "?" : c.name().substring(0, 1);
				LunaDraw.textCentered(ctx, font, first, ix + 16, LunaDraw.textY(iy + 4, 24), txt);
			}
			String nm = LunaDraw.ellipsize(font, c.name(), cw - 6);
			LunaDraw.textCentered(ctx, font, nm, cx + cw / 2, cy + 45, c.obtainable() ? txt : sub);
			LunaDraw.textCentered(ctx, font, c.slotName(), cx + cw / 2, cy + 55, dim);
			if (worn) {
				LunaIcons.drawInBox(ctx, font, LunaIcons.CHECK, cx + cw - 15, cy + 6, 12, acc);
			}
			hits.add(new Hit(cx, cy, cw, CELL_H, c));
			if (hov) {
				tip = c.name() + " | " + c.grade() + " | " + c.slotName() + (c.obtainable() ? "" : " | 지금은 획득 불가")
					+ (worn ? " | 누르면 벗어요" : " | 누르면 입어 봐요");
			}
		}
		ctx.disableScissor();
		int rows = (list.size() + cols - 1) / cols;
		int total = rows * (CELL_H + gap);
		maxScroll = Math.max(0, total - (bottom - contentTop));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		if (maxScroll > 0) {
			int h = bottom - contentTop;
			int thumbH = Math.max(20, h * h / (h + maxScroll));
			int thumbY = contentTop + Math.round((h - thumbH) * (scroll / maxScroll));
			LunaDraw.roundRect(ctx, gridX + gridW + 3, thumbY, 2, thumbH, 1, LunaClientScreen.ink(0x40));
		}
		if (!list.isEmpty() && !packOn && stackOf(list.get(0)) == null) {
			String w = "너굴마을 리소스팩이 켜져 있어야 모양이 보여요";
			int tw = LunaDraw.width(font, w) + 16;
			card(ctx, gridX + (gridW - tw) / 2, bottom - 22, tw, 18);
			LunaDraw.textCentered(ctx, font, w, gridX + gridW / 2, LunaDraw.textY(bottom - 22, 18), sub);
		}
	}

	private void drawPanel(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int txt, int sub, int dim, int acc) {
		int px = panelX;
		int py = CHIP_Y;
		int ph = height - 8 - py;
		card(ctx, px, py, PANEL_W, ph);
		LunaDraw.roundRect(ctx, px + 8, py + 9, 3, 8, 1, acc);
		LunaDraw.text(ctx, font, "미리 입어보기", px + 15, py + 9, txt);

		// 인형 자리
		int extraW = 44;
		int dx = px + 8;
		int dy = py + 24;
		int dw = PANEL_W - 16 - extraW - 4;
		int dh = Math.max(90, Math.min(160, ph - 24 - 70));
		LunaDraw.roundRect(ctx, dx, dy, dw, dh, 6, LunaClientScreen.ink(0x0E));
		dollBox = new int[]{dx, dy, dw, dh};
		RemotePlayer d = doll();
		if (d != null) {
			dress(d);
			int size = Math.max(20, Math.round(dh * 0.36f));
			float k = curK <= 0f ? 1f : curK;
			float fx = dx + dw / 2f - spin;
			float fy = dy + dh * 0.3f;
			LunaCompat.guiPush(ctx);
			try {
				LunaCompat.guiScale(ctx, k, k);
				InventoryScreen.extractEntityInInventoryFollowsMouse(ctx, Math.round(dx / k), Math.round(dy / k), Math.round((dx + dw) / k),
					Math.round((dy + dh) / k), Math.round(size / k), 0.0625f, fx / k, fy / k, d);
			} catch (Throwable t) {
				LunaCompat.warnOnce("neogulCosmetics:doll", t);
			} finally {
				LunaCompat.guiPop(ctx);
			}
		} else {
			LunaDraw.textCentered(ctx, font, "게임에 들어가면 보여요", dx + dw / 2, LunaDraw.textY(dy, dh), dim);
		}
		if (LunaDraw.in(mouseX, mouseY, dx, dy, dw, dh)) {
			tip = "끌어서 돌려 보기";
		}

		// 몸에 못 붙이는 것(등, 상의 …, 효과): 오른쪽 세로 칸에 크게
		int ex = dx + dw + 4;
		int ey = dy;
		for (String[] s : NeogulCosmetics.SLOTS) {
			if ("HAT".equals(s[0]) || "OFFHAND".equals(s[0])) {
				continue;
			}
			NeogulCosmetics.Cosmetic c = WORN.get(s[0]);
			if (c == null) {
				continue;
			}
			if (ey + extraW > dy + dh) {
				break;
			}
			LunaDraw.roundRect(ctx, ex, ey, extraW, extraW, 6, LunaDraw.withAlpha(c.gradeColor(), 0x26));
			ItemStack st = stackOf(c);
			if (st != null) {
				float t = (System.currentTimeMillis() % 4000L) / 4000f;
				float bob = (float) Math.sin(t * Math.PI * 2) * 1.5f;
				bigItem(ctx, st, ex + 4, Math.round(ey + 4 + bob), 2.25f);
			} else {
				LunaDraw.textCentered(ctx, font, c.name().substring(0, 1), ex + extraW / 2, LunaDraw.textY(ey, extraW), txt);
			}
			LunaDraw.text(ctx, font, s[1], ex + 3, ey + extraW - 9, dim);
			if (LunaDraw.in(mouseX, mouseY, ex, ey, extraW, extraW)) {
				tip = c.name() + " (" + s[1] + ")";
			}
			ey += extraW + 4;
		}

		// 입은 목록
		int ly = dy + dh + 8;
		if (WORN.isEmpty()) {
			LunaDraw.text(ctx, font, "왼쪽에서 치장을 눌러 보세요", px + 10, ly, dim);
			clearBox = new int[4];
			return;
		}
		for (String[] s : NeogulCosmetics.SLOTS) {
			NeogulCosmetics.Cosmetic c = WORN.get(s[0]);
			if (c == null) {
				continue;
			}
			if (ly + 11 > py + ph - 22) {
				break;
			}
			LunaDraw.roundRect(ctx, px + 10, ly + 2, 4, 4, 2, c.gradeColor());
			LunaDraw.text(ctx, font, s[1], px + 18, ly, dim);
			LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, c.name(), PANEL_W - 60), px + 50, ly, txt);
			ly += 11;
		}
		int bw = 70;
		int bx = px + PANEL_W - bw - 8;
		int by = py + ph - 22;
		boolean bh = LunaDraw.in(mouseX, mouseY, bx, by, bw, 16);
		LunaDraw.roundRect(ctx, bx, by, bw, 16, 8, bh ? LunaClientScreen.ink(0x30) : LunaClientScreen.ink(0x18));
		LunaDraw.textCentered(ctx, font, "모두 벗기", bx + bw / 2, LunaDraw.textY(by, 16), bh ? txt : sub);
		clearBox = new int[]{bx, by, bw, 16};
	}

	/** 내 모습 복사본(서버와 상관없는 가짜 플레이어). 세계가 바뀌면 다시 만든다. */
	private RemotePlayer doll() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null || mc.player == null) {
			doll = null;
			return null;
		}
		if (doll == null || dollLevel != mc.level) {
			try {
				doll = new RemotePlayer(mc.level, mc.player.getGameProfile());
				doll.setSilent(true);
				dollLevel = mc.level;
			} catch (Throwable t) {
				LunaCompat.warnOnce("neogulCosmetics:make", t);
				doll = null;
			}
		}
		return doll;
	}

	/** 내 갑옷을 그대로 입히고, 입어 본 모자는 머리, 왼손은 왼손에. */
	private void dress(RemotePlayer d) {
		Minecraft mc = Minecraft.getInstance();
		try {
			for (EquipmentSlot es : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack want = mc.player.getItemBySlot(es);
				if (es == EquipmentSlot.HEAD && WORN.get("HAT") != null) {
					ItemStack hat = stackOf(WORN.get("HAT"));
					if (hat != null) {
						want = hat;
					}
				}
				if (!ItemStack.matches(d.getItemBySlot(es), want)) {
					d.setItemSlot(es, want.copy());
				}
			}
			ItemStack off = ItemStack.EMPTY;
			if (WORN.get("OFFHAND") != null) {
				ItemStack o = stackOf(WORN.get("OFFHAND"));
				if (o != null) {
					off = o;
				}
			}
			if (!ItemStack.matches(d.getItemBySlot(EquipmentSlot.OFFHAND), off)) {
				d.setItemSlot(EquipmentSlot.OFFHAND, off.copy());
			}
			if (!d.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()) {
				d.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			}
		} catch (Throwable t) {
			LunaCompat.warnOnce("neogulCosmetics:dress", t);
		}
	}

	private void bigItem(GuiGraphicsExtractor ctx, ItemStack st, int x, int y, float s) {
		if (!LunaCompat.guiTransformSupported(ctx)) {
			ctx.item(st, x + 8, y + 8);
			return;
		}
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, x, y);
			LunaCompat.guiScale(ctx, s, s);
			ctx.item(st, 0, 0);
		} finally {
			LunaCompat.guiPop(ctx);
		}
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

	// ==================== 입력 ====================

	private static boolean inBox(double mx, double my, int[] b) {
		return b[2] > 0 && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
	}

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		layout();
		if (LunaDraw.in(mouseX, mouseY, left + contentW - 18, TOP_Y + 2, 20, 20)) {
			onClose();
			return true;
		}
		if (query.length() > 0 && LunaDraw.in(mouseX, mouseY, left + gridW - 20, SEARCH_Y + 2, 16, 16)) {
			query.setLength(0);
			scroll = 0;
			return true;
		}
		if (inBox(mouseX, mouseY, lockBox)) {
			showLocked = !showLocked;
			scroll = 0;
			return true;
		}
		if (inBox(mouseX, mouseY, clearBox)) {
			WORN.clear();
			return true;
		}
		if (inBox(mouseX, mouseY, dollBox)) {
			dragging = true;
			if (button == 1) {
				spin = 0;
			}
			return true;
		}
		for (Chip c : chips) {
			if (LunaDraw.in(mouseX, mouseY, c.x(), c.y(), c.w(), CHIP_H)) {
				if (c.slotRow()) {
					slot = c.value();
				} else {
					grade = c.value();
				}
				scroll = 0;
				return true;
			}
		}
		if (mouseY >= contentTop && mouseX < gridX + gridW + 2) {
			for (Hit h : hits) {
				if (LunaDraw.in(mouseX, mouseY, h.x(), h.y(), h.w(), h.h())) {
					NeogulCosmetics.Cosmetic c = h.c();
					if (WORN.get(c.slot()) == c) {
						WORN.remove(c.slot());
					} else {
						WORN.put(c.slot(), c);
					}
					return true;
				}
			}
		}
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode, int modifiers) {
		if (keyCode == InputConstants.KEY_ESCAPE) {
			if (query.length() > 0) {
				query.setLength(0);
				scroll = 0;
				return true;
			}
			onClose();
			return true;
		}
		if (keyCode == InputConstants.KEY_BACKSPACE) {
			if (query.length() > 0) {
				if ((modifiers & 0x2) != 0) {
					query.setLength(0);
				} else {
					query.setLength(query.length() - 1);
				}
				scroll = 0;
			}
			return true;
		}
		return true;
	}

	@Override
	public void onClose() {
		doll = null;
		LunaCompat.setScreen(parent);
	}
}
