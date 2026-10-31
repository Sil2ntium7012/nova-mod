package kr.lunaslight.mod.gui;

import com.mojang.blaze3d.platform.InputConstants;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaHangul;
import kr.lunaslight.mod.util.NeogulData;
import kr.lunaslight.mod.util.NeogulMobs;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 49-443차(사용자: "던전 몬스터들 정보도 보는 란도 하나 만들어줘"): <b>던전 몬스터</b>.
 *
 * <p>위: 검색칸(몬스터 이름, 전리품 이름 - 초성/영타) + 던전 칩(전체, 외딴 농장 Lv.1~10 …). 아래: 던전마다 머리(이름, 권장 레벨, 처치 보상)와
 * 몬스터 카드 2열 - 대표 전리품 아이콘, 이름, 레벨, 레벨별 체력/공격력/경험치 표, 전리품 아이콘 줄(마우스를 올리면 이름과 확률).
 * 자료는 {@link NeogulMobs}(위키 기준). 아이콘은 서버 리소스팩 모양(없으면 너굴 아이템 자료의 대체 아이콘).
 */
public class NeogulMobsScreen extends LunaScreenBase {

	private final LunaVScale lunaV = new LunaVScale(this, 480, 320);

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
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
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double verticalAmount) {
		scroll = Math.max(0, Math.min(maxScroll, scroll - (float) verticalAmount * 30f));
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

	/** 던전 색(칩 점, 머리 띠). 순서 = NeogulMobs.ALL. */
	private static final int[] DG_COLORS = {0xFF8BD45A, 0xFFE0B05A, 0xFF7FD3FF, 0xFF4CC38A, 0xFF9C8CFF, 0xFFFF8FB1, 0xFFC08A5A, 0xFFFF6B6B};

	private final Screen parent;
	private final StringBuilder query = new StringBuilder();
	/** 고른 던전(-1 = 전체). */
	private static int dungeon = -1;
	private float scroll;
	private int maxScroll;
	private String tip;

	private static final int TOP_Y = 14;
	private static final int SEARCH_Y = 40;
	private static final int SEARCH_H = 20;
	private static final int CHIP_Y = 66;
	private static final int CHIP_H = 16;
	private int left, contentW, contentTop, chipRows;

	private final List<int[]> chipBoxes = new ArrayList<>();   // x, y, w, 던전 번호
	private final Map<String, ItemStack> icons = new HashMap<>();
	private long iconsAt;

	public NeogulMobsScreen(Screen parent) {
		super(LunaCompat.textLiteral("던전 몬스터"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void layout() {
		contentW = Math.min(width - 32, 640);
		left = (width - contentW) / 2;
		contentTop = CHIP_Y + chipRows * (CHIP_H + 4) + 6;
	}

	private boolean matches(NeogulMobs.Mob m) {
		String q = query.toString().trim();
		if (q.isEmpty()) {
			return true;
		}
		if (LunaHangul.matches(m.name(), q)) {
			return true;
		}
		for (NeogulMobs.Drop d : m.drops()) {
			if (LunaHangul.matches(d.name(), q)) {
				return true;
			}
		}
		return false;
	}

	private int count(int dg) {
		int n = 0;
		for (int i = 0; i < NeogulMobs.ALL.size(); i++) {
			if (dg >= 0 && dg != i) {
				continue;
			}
			for (NeogulMobs.Mob m : NeogulMobs.ALL.get(i).mobs()) {
				if (matches(m)) {
					n++;
				}
			}
		}
		return n;
	}

	private ItemStack iconOf(NeogulMobs.Drop d) {
		long now = System.currentTimeMillis();
		if (now - iconsAt > 3000L) {
			icons.clear();   // 리소스팩이 켜지거나 꺼졌을 수 있어 가끔 다시 찾음
			iconsAt = now;
		}
		ItemStack cached = icons.get(d.icon() + "|" + d.name());
		if (cached != null) {
			return cached;
		}
		ItemStack st = null;
		try {
			st = NeogulData.modelStack(d.model());
			if (st == null) {
				NeogulData.Entry e = NeogulData.byName(d.name());
				if (e != null) {
					st = NeogulData.icon(e, 1);
				}
			}
		} catch (Throwable ignored) {
		}
		if (st == null || st.isEmpty()) {
			st = new ItemStack(Items.PAPER);
		}
		icons.put(d.icon() + "|" + d.name(), st);
		return st;
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
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		int dim = LunaClientScreen.themeDim();
		int acc = LunaClientScreen.themeAccent();
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.OVERLAY);
		ctx.fill(0, 0, width, height, LunaDraw.applyAlpha((LunaClientScreen.themeBg() & 0x00FFFFFF)
			| (LunaClientScreen.themeLight() ? 0xF5000000 : 0xEE000000)));

		big(ctx, "던전 몬스터", left, TOP_Y, txt);
		String note = "너굴마을 위키 기준 | 전리품에 마우스를 올리면 이름과 확률";
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, note, contentW - 140), left + 116, LunaDraw.textY(TOP_Y + 6, 14), dim);
		boolean ch = LunaDraw.in(mouseX, mouseY, left + contentW - 16, TOP_Y + 4, 16, 16);
		LunaIcons.drawInBox(ctx, font, LunaIcons.CLOSE, left + contentW - 16, TOP_Y + 4, 16, ch ? acc : sub);

		// 검색칸
		LunaDraw.roundRect(ctx, left, SEARCH_Y, contentW, SEARCH_H, 6, acc);
		LunaDraw.roundRect(ctx, left + 1, SEARCH_Y + 1, contentW - 2, SEARCH_H - 2, 5, LunaClientScreen.themeCard());
		LunaIcons.drawInBox(ctx, font, LunaIcons.SEARCH, left + 6, SEARCH_Y + 2, 16, dim);
		String q = query + (lunaPreedit == null ? "" : lunaPreedit);
		int ty = LunaDraw.textY(SEARCH_Y, SEARCH_H);
		LunaDraw.text(ctx, font, q.isEmpty() ? "몬스터나 전리품 이름으로 찾기 (초성, 영타도 돼요)" : q, left + 26, ty, q.isEmpty() ? dim : txt);
		if ((System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = left + 26 + LunaDraw.width(font, q) + 1;
			ctx.fill(cx, SEARCH_Y + 5, cx + 1, SEARCH_Y + SEARCH_H - 5, LunaDraw.applyAlpha(txt));
		}
		if (!q.isEmpty()) {
			boolean xh = LunaDraw.in(mouseX, mouseY, left + contentW - 20, SEARCH_Y + 2, 16, 16);
			LunaIcons.drawInBox(ctx, font, LunaIcons.CLOSE, left + contentW - 20, SEARCH_Y + 2, 16, xh ? acc : dim);
		}

		// 던전 칩(줄 넘김)
		chipBoxes.clear();
		int x = left;
		int cy = CHIP_Y;
		int rows = 1;
		for (int i = -1; i < NeogulMobs.ALL.size(); i++) {
			String label = i < 0 ? "전체 " + count(-1)
				: NeogulMobs.ALL.get(i).name() + "  Lv." + NeogulMobs.ALL.get(i).lvMin() + "~" + NeogulMobs.ALL.get(i).lvMax();
			int w = LunaDraw.width(font, label) + (i < 0 ? 14 : 20);
			if (x + w > left + contentW) {
				x = left;
				cy += CHIP_H + 4;
				rows++;
			}
			boolean sel = dungeon == i;
			boolean hov = LunaDraw.in(mouseX, mouseY, x, cy, w, CHIP_H);
			int col = i < 0 ? acc : DG_COLORS[i % DG_COLORS.length];
			int n = count(i);
			if (sel) {
				LunaDraw.roundRect(ctx, x, cy, w, CHIP_H, CHIP_H / 2, LunaDraw.withAlpha(col, 0xE6));
			} else {
				LunaDraw.roundRect(ctx, x, cy, w, CHIP_H, CHIP_H / 2, hov ? LunaClientScreen.ink(0x24) : LunaClientScreen.ink(0x12));
				if (i >= 0) {
					LunaDraw.roundRect(ctx, x + 7, cy + CHIP_H / 2 - 2, 4, 4, 2, col);
				}
			}
			int tc = sel ? (kr.lunaslight.mod.util.LunaTheme.luminance(col) > 0.6f ? 0xFF15181C : 0xFFFFFFFF) : (n == 0 ? dim : (hov ? txt : sub));
			LunaDraw.text(ctx, font, label, x + (sel || i < 0 ? 7 : 14), LunaDraw.textY(cy, CHIP_H), tc);
			chipBoxes.add(new int[]{x, cy, w, i});
			x += w + 4;
		}
		if (rows != chipRows) {
			chipRows = rows;
			layout();
		}

		// 목록
		int bottom = height - 8;
		lunaV.scissor(ctx, left - 2, contentTop, left + contentW + 2, bottom);
		int y = contentTop + 2 - Math.round(scroll);
		int y0 = y;
		int gap = 6;
		int cols = contentW >= 460 ? 2 : 1;
		int cw = (contentW - gap * (cols - 1)) / cols;
		boolean any = false;
		for (int di = 0; di < NeogulMobs.ALL.size(); di++) {
			if (dungeon >= 0 && dungeon != di) {
				continue;
			}
			NeogulMobs.Dungeon dg = NeogulMobs.ALL.get(di);
			List<NeogulMobs.Mob> mobs = new ArrayList<>();
			for (NeogulMobs.Mob m : dg.mobs()) {
				if (matches(m)) {
					mobs.add(m);
				}
			}
			if (mobs.isEmpty()) {
				continue;
			}
			any = true;
			int dc = DG_COLORS[di % DG_COLORS.length];
			// 던전 머리: 색 띠 + 이름 + 권장 레벨 + 처치 보상
			LunaDraw.roundRect(ctx, left, y + 3, 3, 10, 1, dc);
			LunaDraw.text(ctx, font, dg.name(), left + 8, y + 4, txt);
			int nx = left + 8 + LunaDraw.width(font, dg.name()) + 8;
			String lv = "권장 Lv." + dg.lvMin() + "~" + dg.lvMax();
			int lw = LunaDraw.width(font, lv) + 10;
			LunaDraw.roundRect(ctx, nx, y + 2, lw, 12, 6, LunaDraw.withAlpha(dc, 0x30));
			LunaDraw.text(ctx, font, lv, nx + 5, y + 4, dc);
			String rw = "처치 보상 평균 " + dg.reward() + "원 (±25%)";
			LunaDraw.text(ctx, font, rw, left + contentW - LunaDraw.width(font, rw), y + 4, dim);
			y += 18;
			int col = 0;
			int rowTop = y;
			int rowH = 0;
			for (NeogulMobs.Mob m : mobs) {
				int cx = left + col * (cw + gap);
				int h = drawMob(ctx, m, cx, y, cw, dc, mouseX, mouseY, bottom, txt, sub, dim);
				rowH = Math.max(rowH, h);
				col++;
				if (col >= cols) {
					col = 0;
					y = rowTop + rowH + gap;
					rowTop = y;
					rowH = 0;
				} else {
					y = rowTop;
				}
			}
			if (col != 0) {
				y = rowTop + rowH + gap;
			}
			y += 8;
		}
		if (!any) {
			card(ctx, left, y, contentW, 34);
			LunaDraw.textCentered(ctx, font, "찾는 몬스터가 없어요", left + contentW / 2, LunaDraw.textY(y, 34), sub);
			y += 40;
		}
		ctx.disableScissor();
		int total = y - y0;
		maxScroll = Math.max(0, total - (bottom - contentTop));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		if (maxScroll > 0) {
			int hh = bottom - contentTop;
			int thumbH = Math.max(20, hh * hh / (hh + maxScroll));
			int thumbY = contentTop + Math.round((hh - thumbH) * (scroll / maxScroll));
			LunaDraw.roundRect(ctx, left + contentW + 4, thumbY, 2, thumbH, 1, LunaClientScreen.ink(0x40));
		}
		if (tip != null) {
			ctx.setTooltipForNextFrame(font, LunaCompat.textLiteral(tip), mouseX, mouseY);
		}
	}

	/** 몬스터 카드 하나. 높이를 돌려준다. */
	private int drawMob(GuiGraphicsExtractor ctx, NeogulMobs.Mob m, int x, int y, int w, int dc, int mouseX, int mouseY, int bottom,
			int txt, int sub, int dim) {
		int rows = m.hp().length;
		int h = 30 + 11 + rows * 10 + 6 + 22;
		card(ctx, x, y, w, h);
		boolean inView = mouseY >= contentTop && mouseY < bottom;
		// 대표 아이콘(2배) + 이름 + 레벨 알약
		NeogulMobs.Drop sig = m.signature();
		LunaDraw.roundRect(ctx, x + 6, y + 5, 22, 22, 6, LunaDraw.withAlpha(dc, 0x26));
		if (sig != null) {
			bigItem(ctx, iconOf(sig), x + 9, y + 8, 1f);
		}
		LunaDraw.text(ctx, font, m.name(), x + 34, y + 6, txt);
		String lv = "Lv." + m.lvMin() + "~" + m.lvMax();
		int lx = x + 34 + LunaDraw.width(font, m.name()) + 6;
		int lw = LunaDraw.width(font, lv) + 8;
		LunaDraw.roundRect(ctx, lx, y + 4, lw, 12, 6, LunaDraw.withAlpha(dc, 0x30));
		LunaDraw.text(ctx, font, lv, lx + 4, y + 6, dc);
		String sub2 = m.passive() ? "공격하지 않아요" : "공격해요";
		LunaDraw.text(ctx, font, sub2, x + 34, y + 17, m.passive() ? 0xFF7CC08A : 0xFFE0866A);

		// 표: 레벨 | 체력 | 공격력 | 경험치
		int ty = y + 30;
		int c0 = x + 8;
		int cwid = (w - 16) / 4;
		String[] head = {"레벨", "체력", "공격력", "경험치"};
		for (int i = 0; i < 4; i++) {
			LunaDraw.text(ctx, font, head[i], c0 + i * cwid, ty, dim);
		}
		ctx.fill(x + 6, ty + 10, x + w - 6, ty + 11, LunaClientScreen.ink(0x18));
		for (int r = 0; r < rows; r++) {
			int ry = ty + 13 + r * 10;
			LunaDraw.text(ctx, font, "Lv." + (m.lvMin() + r), c0, ry, dc);
			LunaDraw.text(ctx, font, String.format(java.util.Locale.ROOT, "%,d", m.hp()[r]), c0 + cwid, ry, txt);
			String atk = r < m.atk().length ? m.atk()[r] : "-";
			LunaDraw.text(ctx, font, "-".equals(atk) ? "없음" : atk, c0 + cwid * 2, ry, "-".equals(atk) ? dim : txt);
			LunaDraw.text(ctx, font, r < m.exp().length ? m.exp()[r] : "", c0 + cwid * 3, ry, sub);
		}

		// 전리품 아이콘 줄
		int dy = ty + 13 + rows * 10 + 4;
		ctx.fill(x + 6, dy - 2, x + w - 6, dy - 1, LunaClientScreen.ink(0x12));
		int dx = x + 8;
		for (NeogulMobs.Drop d : m.drops()) {
			if (dx + 18 > x + w - 6) {
				break;
			}
			boolean hov = inView && LunaDraw.in(mouseX, mouseY, dx - 1, dy, 18, 18);
			LunaDraw.roundRect(ctx, dx - 1, dy, 18, 18, 4, hov ? LunaClientScreen.ink(0x30) : LunaClientScreen.ink(0x10));
			ctx.item(iconOf(d), dx, dy + 1);
			if (hov) {
				tip = d.name() + " | " + d.chance();
			}
			dx += 20;
		}
		return h;
	}

	private void bigItem(GuiGraphicsExtractor ctx, ItemStack st, int x, int y, float s) {
		if (s == 1f || !LunaCompat.guiTransformSupported(ctx)) {
			ctx.item(st, x, y);
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

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		layout();
		if (LunaDraw.in(mouseX, mouseY, left + contentW - 18, TOP_Y + 2, 20, 20)) {
			onClose();
			return true;
		}
		if (query.length() > 0 && LunaDraw.in(mouseX, mouseY, left + contentW - 20, SEARCH_Y + 2, 16, 16)) {
			query.setLength(0);
			scroll = 0;
			return true;
		}
		for (int[] b : chipBoxes) {
			if (LunaDraw.in(mouseX, mouseY, b[0], b[1], b[2], CHIP_H)) {
				dungeon = b[3];
				scroll = 0;
				return true;
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
		LunaCompat.setScreen(parent);
	}
}
