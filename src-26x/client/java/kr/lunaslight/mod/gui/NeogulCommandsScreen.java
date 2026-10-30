package kr.lunaslight.mod.gui;

import com.mojang.blaze3d.platform.InputConstants;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaHangul;
import kr.lunaslight.mod.util.NeogulCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-431차(사용자: "명령어 목록도 볼 수 있으면 좋을 거 같고 명령어 검색 기능이랑, 물론 화면에는 보기 이쁘게"): <b>너굴마을 명령어</b>.
 *
 * <p>위: 검색칸(명령어, 설명 둘 다, 초성/영타 검색 - LunaHangul) + 분류 칩(전체, 기본, 정보 …, 개수 표시). 아래: 분류별 카드
 * (명령어는 분류 색, 인자는 흐리게, 설명). 카드를 누르면 채팅창이 그 명령어로 채워져 열린다(인자가 있으면 인자 앞까지 - 마저 쓰고 엔터).
 * 바로 보내지는 않는다(실수로 /섬 삭제 같은 걸 보내지 않게). 자료는 {@link NeogulCommands}(위키 기준).
 */
public class NeogulCommandsScreen extends LunaScreenBase {

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
		if (chr >= ' ' && query.length() < 40) {
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
	/** 분류 색(칩 점, 명령어 글자). 순서 = NeogulCommands.CATEGORIES. */
	private static final int[] CAT_COLORS = {0xFF9AA4B2, 0xFF5AB4FF, 0xFFB98CFF, 0xFFFFC857, 0xFF7CE08A, 0xFF4FD6C8, 0xFF8BD45A, 0xFFFF8FB1};

	private final Screen parent;
	private final StringBuilder query = new StringBuilder();
	private static String category = ALL;
	private float scroll;
	private int maxScroll;
	private String tip;
	private String toast;
	private long toastAt;

	private static final int TOP_Y = 14;
	private static final int SEARCH_Y = 40;
	private static final int SEARCH_H = 20;
	private static final int CHIP_Y = 66;
	private static final int CHIP_H = 16;
	private int left, contentW, contentTop;

	/** 이번 프레임 카드 자리(클릭 판정). */
	private record Hit(int x, int y, int w, int h, NeogulCommands.Command cmd) {
	}

	private final List<Hit> hits = new ArrayList<>();
	private final List<int[]> chipBoxes = new ArrayList<>();
	private final List<String> chipNames = new ArrayList<>();

	public NeogulCommandsScreen(Screen parent) {
		super(LunaCompat.textLiteral("너굴마을 명령어"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void layout() {
		contentW = Math.min(width - 32, 600);
		left = (width - contentW) / 2;
		contentTop = CHIP_Y + CHIP_H + 10;
	}

	private static int catColor(String cat) {
		int i = NeogulCommands.CATEGORIES.indexOf(cat);
		return i < 0 ? LunaClientScreen.themeAccent() : CAT_COLORS[i % CAT_COLORS.length];
	}

	private boolean matches(NeogulCommands.Command c) {
		String q = query.toString().trim();
		if (q.isEmpty()) {
			return true;
		}
		String qq = q.startsWith("/") ? q.substring(1) : q;
		return LunaHangul.matches(c.usage(), qq) || LunaHangul.matches(c.desc(), qq) || LunaHangul.matches(c.category(), qq);
	}

	private List<NeogulCommands.Command> visible() {
		List<NeogulCommands.Command> out = new ArrayList<>();
		for (NeogulCommands.Command c : NeogulCommands.ALL) {
			if ((ALL.equals(category) || c.category().equals(category)) && matches(c)) {
				out.add(c);
			}
		}
		return out;
	}

	private int countIn(String cat) {
		int n = 0;
		for (NeogulCommands.Command c : NeogulCommands.ALL) {
			if ((ALL.equals(cat) || c.category().equals(cat)) && matches(c)) {
				n++;
			}
		}
		return n;
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
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		int dim = LunaClientScreen.themeDim();
		int acc = LunaClientScreen.themeAccent();
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.OVERLAY);
		ctx.fill(0, 0, width, height, LunaDraw.applyAlpha((LunaClientScreen.themeBg() & 0x00FFFFFF)
			| (LunaClientScreen.themeLight() ? 0xF5000000 : 0xEE000000)));

		// 제목 + 닫기
		big(ctx, "명령어", left, TOP_Y, txt);
		String note = "너굴마을 위키 기준 | 누르면 채팅창에 넣어 드려요 (바로 보내지 않음)";
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, note, contentW - 120), left + 80, LunaDraw.textY(TOP_Y + 6, 14), dim);
		boolean ch = LunaDraw.in(mouseX, mouseY, left + contentW - 16, TOP_Y + 4, 16, 16);
		LunaIcons.drawInBox(ctx, font, LunaIcons.CLOSE, left + contentW - 16, TOP_Y + 4, 16, ch ? acc : sub);

		// 검색칸
		LunaDraw.roundRect(ctx, left, SEARCH_Y, contentW, SEARCH_H, 6, acc);
		LunaDraw.roundRect(ctx, left + 1, SEARCH_Y + 1, contentW - 2, SEARCH_H - 2, 5, LunaClientScreen.themeCard());
		LunaIcons.drawInBox(ctx, font, LunaIcons.SEARCH, left + 6, SEARCH_Y + 2, 16, dim);
		String q = query + (lunaPreedit == null ? "" : lunaPreedit);
		int ty = LunaDraw.textY(SEARCH_Y, SEARCH_H);
		if (q.isEmpty()) {
			LunaDraw.text(ctx, font, "명령어나 설명으로 찾기 (초성, 영타도 돼요)", left + 26, ty, dim);
		} else {
			LunaDraw.text(ctx, font, q, left + 26, ty, txt);
		}
		if ((System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = left + 26 + LunaDraw.width(font, q) + 1;
			ctx.fill(cx, SEARCH_Y + 5, cx + 1, SEARCH_Y + SEARCH_H - 5, LunaDraw.applyAlpha(txt));
		}
		if (!q.isEmpty()) {
			boolean xh = LunaDraw.in(mouseX, mouseY, left + contentW - 20, SEARCH_Y + 2, 16, 16);
			LunaIcons.drawInBox(ctx, font, LunaIcons.CLOSE, left + contentW - 20, SEARCH_Y + 2, 16, xh ? acc : dim);
		}

		// 분류 칩
		chipBoxes.clear();
		chipNames.clear();
		int x = left;
		List<String> cats = new ArrayList<>();
		cats.add(ALL);
		cats.addAll(NeogulCommands.CATEGORIES);
		for (String c : cats) {
			int n = countIn(c);
			String label = c + " " + n;
			int w = LunaDraw.width(font, label) + (ALL.equals(c) ? 14 : 20);
			if (x + w > left + contentW) {
				break;
			}
			boolean sel = category.equals(c);
			boolean hov = LunaDraw.in(mouseX, mouseY, x, CHIP_Y, w, CHIP_H);
			int col = ALL.equals(c) ? acc : catColor(c);
			if (sel) {
				LunaDraw.roundRect(ctx, x, CHIP_Y, w, CHIP_H, CHIP_H / 2, LunaDraw.withAlpha(col, 0xE6));
			} else {
				LunaDraw.roundRect(ctx, x, CHIP_Y, w, CHIP_H, CHIP_H / 2, hov ? LunaClientScreen.ink(0x24) : LunaClientScreen.ink(0x12));
				if (!ALL.equals(c)) {
					LunaDraw.roundRect(ctx, x + 7, CHIP_Y + CHIP_H / 2 - 2, 4, 4, 2, col);
				}
			}
			int tc = sel ? (kr.lunaslight.mod.util.LunaTheme.luminance(col) > 0.6f ? 0xFF15181C : 0xFFFFFFFF) : (n == 0 ? dim : (hov ? txt : sub));
			LunaDraw.text(ctx, font, label, x + (sel || ALL.equals(c) ? 7 : 14), LunaDraw.textY(CHIP_Y, CHIP_H), tc);
			chipBoxes.add(new int[]{x, w});
			chipNames.add(c);
			x += w + 4;
		}

		// 목록
		int bottom = height - 8;
		lunaV.scissor(ctx, left - 2, contentTop, left + contentW + 2, bottom);
		int y = contentTop + 2 - Math.round(scroll);
		int y0 = y;
		List<NeogulCommands.Command> list = visible();
		if (list.isEmpty()) {
			card(ctx, left, y, contentW, 34);
			LunaDraw.textCentered(ctx, font, "찾는 명령어가 없어요", left + contentW / 2, LunaDraw.textY(y, 34), sub);
			y += 34;
		}
		int cols = contentW >= 440 ? 2 : 1;
		int gap = 6;
		int cw = (contentW - gap * (cols - 1)) / cols;
		String lastCat = null;
		int col = 0;
		int rowTop = y;
		int rowH = 0;
		for (NeogulCommands.Command c : list) {
			if (!c.category().equals(lastCat)) {
				if (col != 0) {
					y = rowTop + rowH + gap;
					col = 0;
				}
				if (lastCat != null || col == 0) {
					y += lastCat == null ? 0 : 6;
				}
				lastCat = c.category();
				int cc = catColor(lastCat);
				LunaDraw.roundRect(ctx, left, y + 3, 3, 8, 1, cc);
				LunaDraw.text(ctx, font, lastCat, left + 8, y + 3, txt);
				y += 16;
				rowTop = y;
				rowH = 0;
			}
			int cx = left + col * (cw + gap);
			String desc = c.desc();
			List<String> lines = wrap(desc, cw - 16);
			int h = 18 + lines.size() * 10 + 4;
			boolean hov = LunaDraw.in(mouseX, mouseY, cx, y, cw, h) && mouseY >= contentTop && mouseY < bottom;
			card(ctx, cx, y, cw, h);
			if (hov) {
				LunaDraw.roundRect(ctx, cx, y, cw, h, 4, LunaDraw.withAlpha(catColor(c.category()), 0x22));
			}
			drawUsage(ctx, c, cx + 8, y + 6, catColor(c.category()), dim);
			for (int i = 0; i < lines.size(); i++) {
				LunaDraw.text(ctx, font, lines.get(i), cx + 8, y + 18 + i * 10, sub);
			}
			hits.add(new Hit(cx, y, cw, h, c));
			if (hov) {
				tip = c.hasArgs() ? "누르면 채팅창에 \"" + c.prefill().trim() + "\"까지 넣어요" : "누르면 채팅창에 넣어요";
			}
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
		ctx.disableScissor();
		int total = y - y0;
		maxScroll = Math.max(0, total - (bottom - contentTop));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		if (maxScroll > 0) {
			int h = bottom - contentTop;
			int thumbH = Math.max(20, h * h / (h + maxScroll));
			int thumbY = contentTop + Math.round((h - thumbH) * (scroll / maxScroll));
			LunaDraw.roundRect(ctx, left + contentW + 4, thumbY, 2, thumbH, 1, LunaClientScreen.ink(0x40));
		}
		if (toast != null && System.currentTimeMillis() - toastAt < 1500) {
			int tw = LunaDraw.width(font, toast) + 16;
			card(ctx, (width - tw) / 2, height - 30, tw, 18);
			LunaDraw.textCentered(ctx, font, toast, width / 2, LunaDraw.textY(height - 30, 18), txt);
		}
		if (tip != null) {
			ctx.setTooltipForNextFrame(font, LunaCompat.textLiteral(tip), mouseX, mouseY);
		}
	}

	/** "/섬 초대 <닉네임>": 명령어 부분은 색, 인자는 흐리게. */
	private void drawUsage(GuiGraphicsExtractor ctx, NeogulCommands.Command c, int x, int y, int color, int dim) {
		String u = c.usage();
		String head = c.hasArgs() ? c.prefill() : u;
		String rest = u.substring(Math.min(u.length(), head.length()));
		LunaDraw.text(ctx, font, head, x, y, color);
		if (!rest.isEmpty()) {
			LunaDraw.text(ctx, font, rest, x + LunaDraw.width(font, head), y, dim);
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

	private List<String> wrap(String s, int w) {
		List<String> out = new ArrayList<>();
		StringBuilder cur = new StringBuilder();
		for (String word : s.split(" ")) {
			String next = cur.length() == 0 ? word : cur + " " + word;
			if (cur.length() > 0 && LunaDraw.width(font, next) > w) {
				out.add(cur.toString());
				cur = new StringBuilder(word);
			} else {
				cur = new StringBuilder(next);
			}
		}
		if (cur.length() > 0) {
			out.add(cur.toString());
		}
		if (out.size() > 2) {
			String two = out.get(1);
			out = new ArrayList<>(out.subList(0, 2));
			out.set(1, LunaDraw.ellipsize(font, two + " …", w));
		}
		return out;
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
		if (mouseY >= CHIP_Y && mouseY < CHIP_Y + CHIP_H) {
			for (int i = 0; i < chipBoxes.size(); i++) {
				int[] b = chipBoxes.get(i);
				if (mouseX >= b[0] && mouseX < b[0] + b[1]) {
					category = chipNames.get(i);
					scroll = 0;
					return true;
				}
			}
		}
		if (mouseY >= contentTop) {
			for (Hit h : hits) {
				if (LunaDraw.in(mouseX, mouseY, h.x(), h.y(), h.w(), h.h())) {
					String text = h.cmd().hasArgs() ? h.cmd().prefill() : h.cmd().usage();
					if (!LunaCompat.openChatWith(Minecraft.getInstance(), text)) {
						toast = "채팅창을 열지 못했어요";
						toastAt = System.currentTimeMillis();
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
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			List<NeogulCommands.Command> list = visible();
			if (list.size() == 1) {
				NeogulCommands.Command c = list.get(0);
				LunaCompat.openChatWith(Minecraft.getInstance(), c.hasArgs() ? c.prefill() : c.usage());
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
