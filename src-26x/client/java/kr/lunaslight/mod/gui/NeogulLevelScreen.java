package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import kr.lunaslight.mod.util.NeogulLevels;
import java.util.List;
import java.util.Locale;

/**
 * 49-149차(사용자: "너굴마을 경험치 - 기능에 계산기, 따로 켜는 게 아니라 입력 > 결과 방식. 시작 레벨 끝 레벨로 필요한 경험치,
 * 돈 보여주고 위키 정보도 밑에 따로, 레벨별 구매 가능한 기능도"): <b>너굴마을 레벨 계산기</b>.
 *
 * <p>위: [시작 레벨] → [끝 레벨] 입력(숫자 입력, -/+, 휠, 위아래 화살표). 그 아래 결과 상자(생활 경험치, 토벌 경험치,
 * 레벨업 비용, 이 구간에 새로 배울 수 있는 기능 수와 합계 비용), 새로 배울 수 있는 기능 목록, 맨 아래 레벨표(구간마다
 * 생활/토벌 경험치, 레벨업 비용, 그 레벨에서 배울 수 있는 기능). 자료는 mcng.kr/wiki 레벨 & 스킬(사용자가 준 표 기준).
 * 레벨업에는 생활 경험치와 토벌 경험치가 둘 다 차야 한다.
 *
 * <p>49-431차(사용자: "레벨 시스템이 완전히 4가지로 개편돼서 이거 모두 적용해서"): 너굴마을 레벨이 농사, 토벌, 채광, 낚시 넷으로 나뉘었다.
 * 위에 분야 탭(+ 권한 스킬)을 두고, 고른 분야의 경험치, 레벨업 비용(넷 공통), 그 분야 스킬만 보여 준다. 자료는 {@link NeogulLevels}.
 */
public class NeogulLevelScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
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

	// ==================== 자료(위키) - NeogulLevels ====================

	private static final int MAX_LV = NeogulLevels.MAX_LV;
	private static final int PERM_TAB = 4;
	private static final String[] TABS = {"농사", "토벌", "채광", "낚시", "권한 스킬"};

	/** 고른 분야(0~3) 또는 권한 스킬(4). 다시 열어도 그대로. */
	private static int track = 0;

	private static long req(int lv) {
		return NeogulLevels.REQ[Math.min(track, 3)][lv];
	}

	private static int trackColor() {
		return track < 4 ? NeogulLevels.TRACK_COLORS[track] : 0xFFFFC857;
	}

	// ==================== 상태 ====================

	private final Screen parent;
	private int startLv = 1;
	private int endLv = 10;
	private int focus = -1;          // 0 = 시작, 1 = 끝
	private String buf = "";
	private float scroll;
	private int maxScroll;
	private String tip;

	private static final int TOP_Y = 14;
	private static final int TAB_Y = 40;
	private static final int TAB_H = 16;
	private static final int INPUT_Y = 64;
	private static final int BOX_H = 18;
	private static final int FIELD_W = 34;
	private static final int BTN_W = 16;
	private int left, contentW, contentTop;

	public NeogulLevelScreen(Screen parent) {
		super(LunaCompat.textLiteral("너굴마을 레벨 계산기"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private void layout() {
		contentW = Math.min(width - 32, 560);
		left = (width - contentW) / 2;
		contentTop = track == PERM_TAB ? TAB_Y + TAB_H + 12 : INPUT_Y + BOX_H + 12;
	}

	// ==================== 숫자 모양 ====================

	private static String num(long v) {
		return String.format(Locale.ROOT, "%,d", v);
	}

	/**
	 * 123450000 → "1억 2,345만원", 5000 → "0.5만원", 12500 → "1.25만원", 300 → "300원".
	 * 49-167차(사용자: "천원단위는 0.5만원 이런식으로"): 만 미만 끝자리는 소수(최대 2자리, 끝 0 제거)로 만 단위에 붙인다.
	 */
	private static String won(long v) {
		if (v < 10_000L) {
			return v >= 1_000L ? manDecimal(v) + "만원" : num(v) + "원";
		}
		long eok = v / 100_000_000L;
		long manRest = v % 100_000_000L;
		StringBuilder sb = new StringBuilder();
		if (eok > 0) {
			sb.append(num(eok)).append("억");
		}
		if (manRest > 0) {
			if (sb.length() > 0) {
				sb.append(' ');
			}
			sb.append(manDecimal(manRest)).append("만");
		}
		return sb.append("원").toString();
	}

	/** 12500 → "1.25", 5000 → "0.5", 20000 → "2", 123450000 % 1억 = 23450000 → "2,345". */
	private static String manDecimal(long v) {
		long man = v / 10_000L;
		long rest = (v % 10_000L) / 100L; // 백 원 단위까지(소수 둘째 자리)
		if (rest == 0) {
			return num(man);
		}
		String d = String.format(Locale.ROOT, "%02d", rest);
		if (d.endsWith("0")) {
			d = d.substring(0, 1);
		}
		return num(man) + "." + d;
	}

	// ==================== 갈무리 글자 ====================

	private Component gObj(String s) {
		// 49-155차(사용자: "기능에 폰트 기본으로 다 되돌려"): 갈무리 고정을 풀고 글꼴 설정(기본 = 마크 글꼴)을 따른다.
		return LunaGfx.text(s);
	}

	private int gWidth(String s) {
		return LunaCompat.textWidth(font, gObj(s));
	}

	private void gText(GuiGraphicsExtractor ctx, String s, int x, int boxY, int boxH, int color) {
		ctx.text(font, gObj(s), x, boxY + Math.round(boxH / 2f - LunaCompat.textVisualCenter()), LunaDraw.applyAlpha(color), false);
	}

	private boolean gBig(GuiGraphicsExtractor ctx, String s, int x, int boxY, int boxH, int color, int maxW) {
		if (!LunaCompat.guiTransformSupported(ctx) || gWidth(s) * 2 > maxW) {
			gText(ctx, s, x, boxY, boxH, color);
			return false;
		}
		LunaCompat.guiPush(ctx);
		try {
			LunaCompat.guiTranslate(ctx, x, boxY + Math.round(boxH / 2f - LunaCompat.textVisualCenter() * 2f));
			LunaCompat.guiScale(ctx, 2f, 2f);
			ctx.text(font, gObj(s), 0, 0, LunaDraw.applyAlpha(color), false);
		} finally {
			LunaCompat.guiPop(ctx);
		}
		return true;
	}

	private void card(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		LunaDraw.card3d(ctx, x, y, w, h, 4, LunaClientScreen.themeCard(), LunaClientScreen.themeLine());   // 49-227차
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

		// 제목 + 닫기
		gBig(ctx, "레벨 계산기", left, TOP_Y, 24, txt, contentW);
		String note = "너굴마을 위키 기준 | 농사, 토벌, 채광, 낚시 레벨은 따로 오릅니다";
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, note, contentW - 170), left + 150,
			LunaDraw.textY(TOP_Y + 6, 14), dim);
		boolean ch = LunaDraw.in(mouseX, mouseY, left + contentW - 16, TOP_Y + 4, 16, 16);
		LunaIcons.drawInBox(ctx, font, LunaIcons.CLOSE, left + contentW - 16, TOP_Y + 4, 16, ch ? acc : sub);

		// 분야 탭
		drawTabs(ctx, mouseX, mouseY);
		// 입력 줄
		if (track != PERM_TAB) {
			int x = left;
			gText(ctx, "시작 레벨", x, INPUT_Y, BOX_H, sub);
			x += gWidth("시작 레벨") + 8;
			x = drawStepper(ctx, 0, x, mouseX, mouseY) + 14;
			gText(ctx, "→", x, INPUT_Y, BOX_H, dim);
			x += gWidth("→") + 14;
			gText(ctx, "끝 레벨", x, INPUT_Y, BOX_H, sub);
			x += gWidth("끝 레벨") + 8;
			drawStepper(ctx, 1, x, mouseX, mouseY);
		}
		ctx.fill(left, contentTop - 6, left + contentW, contentTop - 5, LunaDraw.applyAlpha(LunaClientScreen.themeLine()));

		// 본문(스크롤)
		int bottom = height - 8;
		lunaV.scissor(ctx, left - 2, contentTop, left + contentW + 2, bottom);
		int y = contentTop + 2 - Math.round(scroll);
		int y0 = y;
		if (track == PERM_TAB) {
			y = drawPerms(ctx, y, mouseX, mouseY) + 8;
		} else {
			y = drawResults(ctx, y, mouseX, mouseY) + 12;
			y = drawTable(ctx, y, mouseX, mouseY) + 8;
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
		if (tip != null) {
			ctx.setTooltipForNextFrame(font, LunaCompat.textLiteral(tip), mouseX, mouseY);
		}
	}

	/** 탭 칩 자리(클릭 판정). */
	private final int[] tabX = new int[TABS.length];
	private final int[] tabW = new int[TABS.length];

	/** 분야 탭: 고른 탭은 분야 색으로 채우고, 나머지는 옅은 칩 + 색 점. */
	private void drawTabs(GuiGraphicsExtractor ctx, int mouseX, int mouseY) {
		int x = left;
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		for (int i = 0; i < TABS.length; i++) {
			int col = i < 4 ? NeogulLevels.TRACK_COLORS[i] : 0xFFFFC857;
			int w = gWidth(TABS[i]) + 22;
			tabX[i] = x;
			tabW[i] = w;
			boolean sel = track == i;
			boolean hov = LunaDraw.in(mouseX, mouseY, x, TAB_Y, w, TAB_H);
			if (sel) {
				LunaDraw.roundRect(ctx, x, TAB_Y, w, TAB_H, TAB_H / 2, LunaDraw.withAlpha(col, 0xE6));
			} else {
				LunaDraw.roundRect(ctx, x, TAB_Y, w, TAB_H, TAB_H / 2, hov ? LunaClientScreen.ink(0x24) : LunaClientScreen.ink(0x12));
				LunaDraw.roundRect(ctx, x + 7, TAB_Y + TAB_H / 2 - 2, 4, 4, 2, col);
			}
			int tc = sel ? (kr.lunaslight.mod.util.LunaTheme.luminance(col) > 0.6f ? 0xFF15181C : 0xFFFFFFFF) : (hov ? txt : sub);
			gText(ctx, TABS[i], x + (sel ? 11 : 15), TAB_Y, TAB_H, tc);
			x += w + 5;
		}
	}

	/** 권한 스킬(누적 플레이타임 + 해금 비용, 레벨 조건 없음). */
	private int drawPerms(GuiGraphicsExtractor ctx, int y, int mouseX, int mouseY) {
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		int dim = LunaClientScreen.themeDim();
		int acc = trackColor();
		card(ctx, left, y, contentW, 26);
		gText(ctx, "레벨 조건 없이 누적 플레이타임과 해금 비용만 채우면 배울 수 있어요 (/스킬, 학당 루리)", left + 10, y, 26, sub);
		y += 34;
		int cCmd = left + 6, cDesc = left + 90, cTime = left + contentW - 150, cCost = left + contentW - 80;
		LunaDraw.text(ctx, font, "명령어", cCmd, y, dim);
		LunaDraw.text(ctx, font, "설명", cDesc, y, dim);
		LunaDraw.text(ctx, font, "플레이타임", cTime, y, dim);
		LunaDraw.text(ctx, font, "해금 비용", cCost, y, dim);
		y += 12;
		ctx.fill(left, y - 2, left + contentW, y - 1, LunaDraw.applyAlpha(LunaClientScreen.themeLine()));
		long sum = 0;
		int i = 0;
		for (NeogulLevels.Perm pm : NeogulLevels.PERMS) {
			int rh = 14;
			if (i++ % 2 == 0) {
				LunaDraw.roundRect(ctx, left, y - 3, contentW, rh, 2, LunaClientScreen.ink(0x08));
			}
			LunaDraw.text(ctx, font, pm.command(), cCmd, y, acc);
			LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, pm.desc(), cTime - cDesc - 8), cDesc, y, txt);
			LunaDraw.text(ctx, font, pm.playtime(), cTime, y, sub);
			LunaDraw.text(ctx, font, won(pm.cost()), cCost, y, sub);
			sum += pm.cost();
			y += rh;
		}
		y += 4;
		String total = "모두 배우면 " + won(sum);
		LunaDraw.text(ctx, font, total, left + contentW - LunaDraw.width(font, total) - 4, y, dim);
		return y + 12;
	}

	/** [-][ 숫자 ][+] - 반환 = 오른쪽 끝 x. 좌표는 클릭 판정용으로 기억해 둔다. */
	private final int[] stepX = new int[2];

	private int drawStepper(GuiGraphicsExtractor ctx, int which, int x, int mouseX, int mouseY) {
		stepX[which] = x;
		int val = which == 0 ? startLv : endLv;
		boolean foc = focus == which;
		int line = LunaClientScreen.themeLine();
		int txt = LunaClientScreen.themeText();
		int acc = LunaClientScreen.themeAccent();
		// -
		boolean mh = LunaDraw.in(mouseX, mouseY, x, INPUT_Y, BTN_W, BOX_H);
		card(ctx, x, INPUT_Y, BTN_W, BOX_H);
		LunaDraw.textCentered(ctx, font, "-", x + BTN_W / 2, LunaDraw.textY(INPUT_Y, BOX_H), mh ? acc : txt);
		// 칸
		int fx = x + BTN_W + 2;
		LunaDraw.roundRect(ctx, fx, INPUT_Y, FIELD_W, BOX_H, 4, foc ? acc : line);
		LunaDraw.roundRect(ctx, fx + 1, INPUT_Y + 1, FIELD_W - 2, BOX_H - 2, 3, LunaClientScreen.themeCard());
		String shown = foc ? buf : String.valueOf(val);
		int tw = gWidth(shown);
		gText(ctx, shown, fx + (FIELD_W - tw) / 2, INPUT_Y, BOX_H, txt);
		if (foc && (System.currentTimeMillis() / 500) % 2 == 0) {
			int cx = fx + (FIELD_W + tw) / 2 + 1;
			ctx.fill(cx, INPUT_Y + 4, cx + 1, INPUT_Y + BOX_H - 4, LunaDraw.applyAlpha(txt));
		}
		// +
		int px = fx + FIELD_W + 2;
		boolean ph = LunaDraw.in(mouseX, mouseY, px, INPUT_Y, BTN_W, BOX_H);
		card(ctx, px, INPUT_Y, BTN_W, BOX_H);
		LunaDraw.textCentered(ctx, font, "+", px + BTN_W / 2, LunaDraw.textY(INPUT_Y, BOX_H), ph ? acc : txt);
		return px + BTN_W;
	}

	/** 결과 상자 3개(분야 경험치, 레벨업 비용, 배울 수 있는 스킬) + 경험치 얻는 법 + 구간 안 스킬 목록. */
	private int drawResults(GuiGraphicsExtractor ctx, int y, int mouseX, int mouseY) {
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		int dim = LunaClientScreen.themeDim();
		int acc = trackColor();
		String tn = NeogulLevels.TRACKS[track];
		if (endLv <= startLv) {
			card(ctx, left, y, contentW, 30);
			gText(ctx, "입력 오류: 끝 레벨은 시작 레벨보다 커야 해요", left + 10, y, 30, sub);
			return y + 30;
		}
		long exp = 0;
		long cost = 0;
		for (int lv = startLv; lv < endLv; lv++) {
			exp += req(lv);
			cost += NeogulLevels.COST[lv];
		}
		List<NeogulLevels.Skill> fresh = NeogulLevels.skillsBetween(track, startLv, endLv);
		long skillCost = 0;
		for (NeogulLevels.Skill k : fresh) {
			skillCost += k.cost();
		}
		String range = tn + " Lv." + startLv + " → Lv." + endLv;
		String[][] items = {
			{tn + " 경험치", num(exp), range},
			{"레벨업 비용", won(cost), num(cost) + "원 (네 분야 같음)"},
			{"새로 배울 수 있는 스킬", fresh.size() + "개", "모두 배우면 " + won(skillCost)}};
		int gap = 6;
		int cols = contentW >= 420 ? 3 : 1;
		int tw = (contentW - gap * (cols - 1)) / cols;
		int th = 52;
		for (int i = 0; i < items.length; i++) {
			int tx = left + (i % cols) * (tw + gap);
			int ty = y + (i / cols) * (th + gap);
			card(ctx, tx, ty, tw, th);
			LunaDraw.roundRect(ctx, tx + 4, ty + 8, 2, th - 16, 1, i == 0 ? acc : LunaClientScreen.ink(0x30));
			LunaDraw.text(ctx, font, items[i][0], tx + 11, ty + 7, sub);
			gBig(ctx, items[i][1], tx + 11, ty + 17, 22, i == 0 ? acc : txt, tw - 18);
			LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, items[i][2], tw - 18), tx + 11, ty + th - 12, dim);
		}
		y += ((items.length + cols - 1) / cols) * (th + gap) - gap + 8;

		// 경험치 얻는 법
		String[] src = NeogulLevels.EXP_SOURCES[track];
		card(ctx, left, y, contentW, 30);
		LunaDraw.text(ctx, font, tn + " 경험치 얻는 법 | " + src[0], left + 10, y + 5, sub);
		LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, src[1], contentW - 20), left + 10, y + 17, txt);
		y += 40;

		// 새로 배울 수 있는 스킬
		gText(ctx, "구간 안에서 배울 수 있는 " + tn + " 스킬", left, y, 14, sub);
		y += 18;
		if (fresh.isEmpty()) {
			LunaDraw.text(ctx, font, "없음", left + 4, y, dim);
			return y + 12;
		}
		for (NeogulLevels.Skill k : fresh) {
			String lv = "Lv." + k.level();
			LunaDraw.text(ctx, font, lv, left + 4, y, acc);
			int nx = left + 40;
			String price = won(k.cost());
			int pw = LunaDraw.width(font, price);
			LunaDraw.text(ctx, font, k.name(), nx, y, txt);
			int dx = nx + Math.max(96, LunaDraw.width(font, k.name()) + 8);
			LunaDraw.text(ctx, font, LunaDraw.ellipsize(font, k.desc(), left + contentW - pw - 12 - dx), dx, y, sub);
			LunaDraw.text(ctx, font, price, left + contentW - pw - 4, y, sub);
			if (LunaDraw.in(mouseX, mouseY, left, y - 1, contentW, 12) && mouseY >= contentTop) {
				tip = k.name() + " | " + k.desc() + " | " + won(k.cost());
			}
			y += 12;
		}
		return y;
	}

	/** 위키 레벨표: 구간 | 필요 경험치 | 레벨업 비용 | 도착 레벨에 배울 수 있는 스킬. 고른 구간은 강조. */
	private int drawTable(GuiGraphicsExtractor ctx, int y, int mouseX, int mouseY) {
		int txt = LunaClientScreen.themeText();
		int sub = LunaClientScreen.themeSub();
		int dim = LunaClientScreen.themeDim();
		int acc = trackColor();
		gText(ctx, NeogulLevels.TRACKS[track] + " 레벨표 (위키)", left, y, 14, sub);
		y += 18;
		int cLv = left + 4;
		int cExp = left + 70;
		int cCost = left + 150;
		int cSkill = left + 236;
		LunaDraw.text(ctx, font, "구간", cLv, y, dim);
		LunaDraw.text(ctx, font, "필요 경험치", cExp, y, dim);
		LunaDraw.text(ctx, font, "레벨업 비용", cCost, y, dim);
		LunaDraw.text(ctx, font, "도착 레벨 스킬", cSkill, y, dim);
		y += 12;
		ctx.fill(left, y - 2, left + contentW, y - 1, LunaDraw.applyAlpha(LunaClientScreen.themeLine()));
		int skillW = left + contentW - cSkill - 4;
		for (int lv = 1; lv < MAX_LV; lv++) {
			List<NeogulLevels.Skill> ks = NeogulLevels.skillsAt(track, lv + 1);
			List<String> lines = new ArrayList<>();
			StringBuilder cur = new StringBuilder();
			for (NeogulLevels.Skill k : ks) {
				String part = k.name();
				String next = cur.length() == 0 ? part : cur + ", " + part;
				if (cur.length() > 0 && LunaDraw.width(font, next) > skillW) {
					lines.add(cur.toString());
					cur = new StringBuilder(part);
				} else {
					cur = new StringBuilder(next);
				}
			}
			if (cur.length() > 0) {
				lines.add(cur.toString());
			}
			int rows = Math.max(1, lines.size());
			int rh = rows * 10 + 3;
			boolean in = lv >= startLv && lv < endLv;
			if (in) {
				LunaDraw.roundRect(ctx, left, y - 2, contentW, rh, 2, LunaDraw.withAlpha(acc, 0x22));
			} else if (lv % 2 == 0) {
				LunaDraw.roundRect(ctx, left, y - 2, contentW, rh, 2, LunaClientScreen.ink(0x08));
			}
			int c = in ? txt : sub;
			LunaDraw.text(ctx, font, lv + " → " + (lv + 1), cLv, y, in ? acc : c);
			LunaDraw.text(ctx, font, num(req(lv)), cExp, y, c);
			LunaDraw.text(ctx, font, won(NeogulLevels.COST[lv]), cCost, y, c);
			for (int i = 0; i < lines.size(); i++) {
				LunaDraw.text(ctx, font, lines.get(i), cSkill, y + i * 10, in ? txt : dim);
			}
			if (!ks.isEmpty() && LunaDraw.in(mouseX, mouseY, cSkill, y - 2, skillW, rh) && mouseY >= contentTop) {
				StringBuilder t = new StringBuilder("Lv." + (lv + 1) + ": ");
				for (int i = 0; i < ks.size(); i++) {
					NeogulLevels.Skill k = ks.get(i);
					t.append(i == 0 ? "" : ",  ").append(k.name()).append(" (").append(k.desc()).append(", ").append(won(k.cost())).append(")");
				}
				tip = t.toString();
			}
			y += rh;
		}
		return y;
	}

	// ==================== 입력 ====================

	private void commit() {
		if (focus < 0) {
			return;
		}
		try {
			int v = Integer.parseInt(buf.trim());
			v = Math.max(1, Math.min(MAX_LV, v));
			if (focus == 0) {
				startLv = v;
			} else {
				endLv = v;
			}
		} catch (NumberFormatException ignored) {
		}
		focus = -1;
		buf = "";
	}

	private void bump(int which, int d) {
		if (focus == which) {
			commit();
		}
		if (which == 0) {
			startLv = Math.max(1, Math.min(MAX_LV, startLv + d));
		} else {
			endLv = Math.max(1, Math.min(MAX_LV, endLv + d));
		}
	}

	/** 0 = -, 1 = 칸, 2 = +, -1 = 없음. */
	private int partAt(int which, double mx, double my) {
		int x = stepX[which];
		if (my < INPUT_Y || my >= INPUT_Y + BOX_H) {
			return -1;
		}
		if (mx >= x && mx < x + BTN_W) {
			return 0;
		}
		int fx = x + BTN_W + 2;
		if (mx >= fx && mx < fx + FIELD_W) {
			return 1;
		}
		int px = fx + FIELD_W + 2;
		if (mx >= px && mx < px + BTN_W) {
			return 2;
		}
		return -1;
	}

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		layout();
		if (LunaDraw.in(mouseX, mouseY, left + contentW - 18, TOP_Y + 2, 20, 20)) {
			onClose();
			return true;
		}
		if (mouseY >= TAB_Y && mouseY < TAB_Y + TAB_H) {
			for (int i = 0; i < TABS.length; i++) {
				if (mouseX >= tabX[i] && mouseX < tabX[i] + tabW[i]) {
					commit();
					track = i;
					scroll = 0;
					return true;
				}
			}
		}
		for (int w = 0; w < 2 && track != PERM_TAB; w++) {
			int p = partAt(w, mouseX, mouseY);
			if (p == 0) {
				bump(w, -1);
				return true;
			}
			if (p == 2) {
				bump(w, 1);
				return true;
			}
			if (p == 1) {
				commit();
				focus = w;
				buf = "";
				return true;
			}
		}
		commit();
		return true;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double amount) {
		for (int w = 0; w < 2 && track != PERM_TAB; w++) {
			if (partAt(w, mouseX, mouseY) >= 0) {
				bump(w, amount > 0 ? 1 : -1);
				return true;
			}
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll - (float) amount * 24f));
		return true;
	}

	private boolean lunaCharTyped0(char chr) {
		if (focus >= 0 && chr >= '0' && chr <= '9' && buf.length() < 3) {
			buf += chr;
			return true;
		}
		return focus >= 0;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (keyCode == InputConstants.KEY_ESCAPE) {
			if (focus >= 0) {
				focus = -1;
				buf = "";
				return true;
			}
			onClose();
			return true;
		}
		if (focus >= 0) {
			switch (keyCode) {
				case InputConstants.KEY_BACKSPACE -> {
					if (!buf.isEmpty()) {
						buf = buf.substring(0, buf.length() - 1);
					}
				}
				case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> commit();
				case InputConstants.KEY_TAB -> {
					int next = focus == 0 ? 1 : 0;
					commit();
					focus = next;
				}
				case InputConstants.KEY_UP -> {
					int w = focus;
					commit();
					bump(w, 1);
				}
				case InputConstants.KEY_DOWN -> {
					int w = focus;
					commit();
					bump(w, -1);
				}
				default -> {
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		LunaCompat.setScreen(parent);
	}
}
