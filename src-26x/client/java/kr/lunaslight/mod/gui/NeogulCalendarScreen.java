package kr.lunaslight.mod.gui;

import com.mojang.blaze3d.platform.InputConstants;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NeogulCalendar;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * 49-518차(사용자: "너굴 달력 - 일퀘 보상 받음 / 보상 안 받음 / 일퀘 안 함, 추천이랑 핫타임도, 그날 접속한 시간도, 조작 불가능하게"):
 * 한 달 달력. 칸마다 날짜, 접속 시간, 일퀘 상태(색 띠), 추천과 핫타임 점. 보기만 한다 - 바꿀 수 있는 곳이 없다({@link NeogulCalendar}가 감지한 것만).
 */
public class NeogulCalendarScreen extends LunaScreenBase {

	private final LunaVScale lunaV = new LunaVScale(this, 440, 340);
	private final Screen parent;
	private YearMonth month = YearMonth.now();

	private static final String[] WEEK = {"월", "화", "수", "목", "금", "토", "일"};
	private static final int CLAIMED = 0xFF4CC38A, UNCLAIMED = 0xFFFFC857, NONE = 0xFF6B7280, MISS = 0xFFFF6B6B;
	private static final int REC = 0xFF5AB4FF, HOT = 0xFFFF8FB1;

	public NeogulCalendarScreen(Screen parent) {
		super(LunaCompat.textLiteral("너굴 달력"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

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
	protected boolean lunaMouseScrolled(double mouseX, double mouseY, double v) {
		month = v > 0 ? month.minusMonths(1) : month.plusMonths(1);
		return true;
	}

	@Override
	protected boolean lunaKeyPressed(int keyCode, int scanCode, int modifiers) {
		if (keyCode == InputConstants.KEY_ESCAPE) {
			onClose();
			return true;
		}
		if (keyCode == InputConstants.KEY_LEFT) {
			month = month.minusMonths(1);
			return true;
		}
		if (keyCode == InputConstants.KEY_RIGHT) {
			month = month.plusMonths(1);
			return true;
		}
		return false;
	}

	// ==================== 배치 ====================

	private int left, top, gw, cell, cellH;

	private void layout() {
		gw = Math.min(width - 32, 560);
		left = (width - gw) / 2;
		top = 54;
		cell = gw / 7;
		cellH = Math.max(32, Math.min(48, (height - top - 18 - 24) / 6));
	}

	private String hover;

	private void draw(GuiGraphicsExtractor ctx, int mx, int my, float delta) {
		LunaDraw.beginFrame();
		layout();
		hover = null;
		int txt = LunaClientScreen.themeText(), sub = LunaClientScreen.themeSub(), dim = LunaClientScreen.themeDim(), acc = LunaClientScreen.themeAccent();
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mx, my, delta, LunaDraw.OVERLAY);
		ctx.fill(0, 0, width, height, LunaDraw.applyAlpha((LunaClientScreen.themeBg() & 0x00FFFFFF)
			| (LunaClientScreen.themeLight() ? 0xF5000000 : 0xEE000000)));

		// 제목 + 달 넘기기 + 닫기
		String head = month.getYear() + "년 " + month.getMonthValue() + "월";
		LunaDraw.text(ctx, font, "너굴 달력", left, 14, txt);
		LunaDraw.text(ctx, font, "보기만 할 수 있어요 (감지한 기록)", left, 26, dim);
		int hw = LunaDraw.width(font, head);
		int cx = left + gw / 2;
		LunaDraw.text(ctx, font, head, cx - hw / 2, 18, txt);
		boolean lh = LunaDraw.in(mx, my, cx - hw / 2 - 22, 14, 16, 16), rh = LunaDraw.in(mx, my, cx + hw / 2 + 6, 14, 16, 16);
		LunaIcons.drawInBox(ctx, font, LunaIcons.LEFT, cx - hw / 2 - 22, 14, 16, lh ? acc : sub);
		LunaIcons.drawInBox(ctx, font, LunaIcons.RIGHT, cx + hw / 2 + 6, 14, 16, rh ? acc : sub);
		boolean ch = LunaDraw.in(mx, my, left + gw - 16, 14, 16, 16);
		LunaIcons.drawInBox(ctx, font, LunaIcons.CLOSE, left + gw - 16, 14, 16, ch ? acc : sub);

		// 요일 머리
		for (int i = 0; i < 7; i++) {
			int col = i == 5 ? 0xFF6EA8FF : i == 6 ? 0xFFFF7A7A : sub;
			LunaDraw.textCentered(ctx, font, WEEK[i], left + i * cell + cell / 2, top - 12, col);
		}

		LocalDate first = month.atDay(1);
		LocalDate today = LocalDate.now();
		int lead = first.getDayOfWeek().getValue() - 1;
		int days = month.lengthOfMonth();
		int claimed = 0, played = 0;
		for (int dm = 1; dm <= days; dm++) {
			int idx = lead + dm - 1;
			int x = left + (idx % 7) * cell, y = top + (idx / 7) * cellH;
			LocalDate date = month.atDay(dm);
			String key = date.toString();
			NeogulCalendar.Day d = NeogulCalendar.peek(key);
			long ms = NeogulCalendar.playMs(key);
			boolean future = date.isAfter(today);
			boolean isToday = date.equals(today);
			boolean hov = LunaDraw.in(mx, my, x + 1, y + 1, cell - 2, cellH - 2);
			LunaDraw.roundRect(ctx, x + 1, y + 1, cell - 2, cellH - 2, 4,
				LunaDraw.applyAlpha(future ? LunaDraw.withAlpha(LunaClientScreen.themeCard(), 0x60) : hov ? LunaClientScreen.themeLine() : LunaClientScreen.themeCard()));
			if (isToday) {
				LunaDraw.roundRectOutline(ctx, x + 1, y + 1, cell - 2, cellH - 2, 4, LunaDraw.applyAlpha(acc));
			}
			LunaDraw.text(ctx, font, String.valueOf(dm), x + 5, y + 4, future ? dim : txt);
			if (future) {
				continue;
			}
			// 49-527차(사용자: "달력 UI가 너무 안 깔끔해, 내부 블록이 이상해"): 칸 안 글자 띠와 추/핫 상자를 없애고 작은 표시만.
			// 왼쪽 위 날짜, 왼쪽 아래 접속 시간, 오른쪽 위 일퀘 동그라미(받음 = 꽉 찬 초록, 안 받음 = 노란 고리), 오른쪽 아래 추천(파랑), 핫타임(분홍) 점.
			if (ms > 0) {
				played++;
				String t = shortTime(ms);
				LunaDraw.text(ctx, font, t, x + 5, y + cellH - 12, sub);
			}
			boolean rec = d != null && d.rec, hot = d != null && d.hot;
			NeogulCalendar.Quest q = d == null ? NeogulCalendar.Quest.NONE : d.quest;
			int qx = x + cell - 12, qy = y + 5;
			if (q == NeogulCalendar.Quest.CLAIMED) {
				claimed++;
				LunaDraw.roundRect(ctx, x + 1, y + 1, cell - 2, cellH - 2, 4, LunaDraw.applyAlpha(LunaDraw.withAlpha(CLAIMED, 0x18)));
				LunaDraw.roundRect(ctx, qx, qy, 7, 7, 3, LunaDraw.applyAlpha(CLAIMED));
			} else if (q == NeogulCalendar.Quest.DONE) {
				LunaDraw.roundRect(ctx, qx, qy, 7, 7, 3, LunaDraw.applyAlpha(UNCLAIMED));
				LunaDraw.roundRect(ctx, qx + 2, qy + 2, 3, 3, 1, LunaDraw.applyAlpha(LunaClientScreen.themeCard()));
			}
			int px = x + cell - 8;
			if (hot) {
				LunaDraw.roundRect(ctx, px, y + cellH - 9, 4, 4, 2, LunaDraw.applyAlpha(HOT));
				px -= 6;
			}
			if (rec) {
				LunaDraw.roundRect(ctx, px, y + cellH - 9, 4, 4, 2, LunaDraw.applyAlpha(REC));
			}
			if (hov) {
				StringBuilder sb = new StringBuilder();
				sb.append(date.getMonthValue()).append("월 ").append(dm).append("일  ");
				sb.append("접속 ").append(ms > 0 ? longTime(ms) : "없음");
				sb.append("  |  일퀘 ").append(q == NeogulCalendar.Quest.CLAIMED ? "보상 받음" : q == NeogulCalendar.Quest.DONE ? "다 했지만 보상 안 받음" : "안 함");
				if (d != null && d.total > 0) {
					sb.append(" (").append(d.done).append("/").append(d.total).append(")");
				}
				sb.append("  |  추천 ").append(rec ? "함" : "안 함").append("  |  핫타임 ").append(hot ? "받음" : "안 받음");
				hover = sb.toString();
			}
		}

		// 아래: 범례 또는 마우스 올린 날
		int by = top + 6 * cellH + 8;
		if (hover != null) {
			LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, hover, gw), left, by, txt);
		} else {
			int x = left;
			x = legend(ctx, x, by, CLAIMED, "일퀘 보상 받음");
			x = legend(ctx, x, by, UNCLAIMED, "다 했지만 안 받음");
			x = legend(ctx, x, by, REC, "추천");
			x = legend(ctx, x, by, HOT, "핫타임");
			String sum = "이번 달 접속 " + played + "일, 일퀘 보상 " + claimed + "일";
			LunaDraw.text(ctx, font, sum, left + gw - LunaDraw.width(font, sum), by, dim);
		}
	}

	private void dot(GuiGraphicsExtractor ctx, int x, int y, int color, String label) {
		int c = color != 0 ? color : 0xFF3A3F4A;
		LunaDraw.roundRect(ctx, x, y, 15, 11, 3, LunaDraw.applyAlpha(color != 0 ? LunaDraw.withAlpha(c, 0xD0) : c));
		LunaDraw.textCentered(ctx, font, label, x + 8, y + 2, color != 0 ? 0xFFFFFFFF : 0xFF7A808C);
	}

	private int legend(GuiGraphicsExtractor ctx, int x, int y, int color, String label) {
		LunaDraw.roundRect(ctx, x, y + 1, 7, 7, 3, LunaDraw.applyAlpha(color));
		LunaDraw.text(ctx, font, label, x + 10, y, LunaClientScreen.themeSub());
		return x + 10 + LunaDraw.width(font, label) + 10;
	}

	private static String shortTime(long ms) {
		long m = ms / 60000;
		return m >= 60 ? (m / 60) + "시간" : m + "분";
	}

	private static String longTime(long ms) {
		long m = ms / 60000;
		return m >= 60 ? (m / 60) + "시간 " + (m % 60) + "분" : m + "분";
	}

	private boolean click(double mx, double my) {
		layout();
		String head = month.getYear() + "년 " + month.getMonthValue() + "월";
		int hw = LunaDraw.width(font, head);
		int cx = left + gw / 2;
		if (LunaDraw.in(mx, my, cx - hw / 2 - 22, 14, 16, 16)) {
			month = month.minusMonths(1);
			return true;
		}
		if (LunaDraw.in(mx, my, cx + hw / 2 + 6, 14, 16, 16)) {
			month = month.plusMonths(1);
			return true;
		}
		if (LunaDraw.in(mx, my, left + gw - 16, 14, 16, 16)) {
			onClose();
			return true;
		}
		return true;   // 칸은 눌러도 아무 일 없음(보기 전용)
	}

	@Override
	public void onClose() {
		LunaCompat.setScreen(parent);
	}
}
