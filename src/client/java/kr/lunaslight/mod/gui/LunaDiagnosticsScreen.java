package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.LunaPerf;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-7차: 성능 진단서. LunaPerf가 백그라운드에서 모은 지표(FPS/메모리/스파이크/핑/서버 틱/모드 수)를
 * 신호등(초록/노랑/빨강)으로 보여주고, 문제 항목엔 원인 진단과 해결책을 붙여줌.
 * 정직성: 모드별 메모리 귀속은 JVM 구조상 불가능해서 흉내내지 않고 하단에 그대로 안내함.
 *
 * <p>49-166차(사용자: "진단 조금 더 화면 크고 제대로 멋있게"): 목록 한 장에서 <b>대시보드</b>로.
 * 위에 큰 숫자 타일 4개(FPS, 메모리, 핑, 서버 틱), 그 아래 최근 60초 FPS 막대 그래프와 메모리 사용률 막대,
 * 그 아래에 주의/문제 항목만 카드로(전부 정상이면 초록 한 줄). 창은 화면 폭의 대부분(최대 640)을 쓴다.
 * 큰 숫자는 GUI 변환(행렬 배율)으로 2배 그리고, 변환을 못 쓰는 버전은 굵은 글씨로 대신한다.
 */
public class LunaDiagnosticsScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 480, 320);

	@Override
	public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
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
	private static final int MAX_W = 640;
	private static final int PAD = 18;
	private static final int CLOSE_SZ = 22;
	private static final int GAP = 10;
	private static final int TILE_H = 64;
	private static final int CHART_H = 96;

	private static final int GOOD = 0xFF5AD86E;
	private static final int WARN = 0xFFF5C542;
	private static final int BAD = 0xFFFF6B5E;

	private final Screen parent;
	private int lastPx, lastPy, lastPw, lastPh; // 클릭 판정용(렌더에서 갱신)
	/** 49-217차: 화면이 낮아 다 안 들어갈 때 본문 스크롤(휠). */
	private double scroll;
	private int maxScroll;

	private final long openedNanos = System.nanoTime();

	public LunaDiagnosticsScreen(Screen parent) {
		super(kr.lunaslight.mod.util.LunaCompat.textLiteral("성능 진단서"));
		this.parent = parent;
	}

	/** 49-28차: 통계 화면과 같은 등장 애니메이션(칸마다 조금씩 늦게). */
	private float rowProgress(int index) {
		float elapsed = (System.nanoTime() - openedNanos) / 1_000_000_000f;
		float t = (elapsed - index * 0.05f) / 0.4f;
		if (t <= 0) {
			return 0f;
		}
		if (t >= 1) {
			return 1f;
		}
		float inv = 1f - t;
		return 1f - inv * inv * inv;
	}

	/** 공백 기준 단어 줄바꿈. */
	private List<String> wrap(String text, int maxWidth) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (LunaCompat.getTextWidth(textRenderer, candidate) > maxWidth && !line.isEmpty()) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(candidate);
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}

	private static int levelColor(int level) {
		return level >= 2 ? BAD : level == 1 ? WARN : GOOD;
	}

	private static String levelTag(int level) {
		return level >= 2 ? "문제" : level == 1 ? "주의" : "좋음";
	}

	/** 큰 숫자(2배). 변환을 못 쓰는 버전은 굵은 글씨 1배. 그린 폭(1배 기준 좌표계)을 돌려준다. */
	private int bigText(DrawContext ctx, String s, int x, int y, int color) {
		if (LunaCompat.guiTransformSupported(ctx)) {
			LunaCompat.guiPush(ctx);
			LunaCompat.guiTranslate(ctx, x, y);
			LunaCompat.guiScale(ctx, 2f, 2f);
			try {
				LunaDraw.textBold(ctx, textRenderer, s, 0, 0, color);
			} finally {
				LunaCompat.guiPop(ctx);
			}
			return LunaDraw.widthBold(textRenderer, s) * 2;
		}
		LunaDraw.textBold(ctx, textRenderer, s, x, y + 4, color);
		return LunaDraw.widthBold(textRenderer, s);
	}

	private void lunaRender0(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LunaClientScreen.applyScreenTheme();
		try {
			renderDiag(ctx, mouseX, mouseY, delta);
		} finally {
			LunaClientScreen.restoreScreenTheme();
		}
	}

	private void renderDiag(DrawContext ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, LunaDraw.applyAlpha(0x88000000));

		List<LunaPerf.Issue> issues = LunaPerf.diagnose(client);
		List<LunaPerf.Issue> alerts = new ArrayList<>();
		for (LunaPerf.Issue is : issues) {
			if (is.level() > 0) {
				alerts.add(is);
			}
		}

		int pw = Math.min(MAX_W, width - 40);
		int contentW = pw - PAD * 2;

		// 높이 선계산
		List<List<String>> adviceLines = new ArrayList<>();
		List<Integer> cardHs = new ArrayList<>();
		int alertsH = 0;
		for (LunaPerf.Issue is : alerts) {
			List<String> lines = wrap(is.advice(), contentW - 24);
			adviceLines.add(lines);
			int ch = 30 + lines.size() * 11 + 6;
			cardHs.add(ch);
			alertsH += ch + 6;
		}
		if (alerts.isEmpty()) {
			alertsH = 30 + 6;
		}
		int headH = PAD + CLOSE_SZ + 14;
		int sectionLabelH = 18;
		int footH = 26;
		int maxH = height - 20;
		// 49-217차(사용자 제보: 전체 화면에서 아래 글씨가 잘림): 다 안 들어가면 먼저 타일/그래프를 낮추고(compact),
		// 그래도 넘치면 본문을 잘라 휠로 내린다. 아래 안내 줄은 본문과 겹치지 않게 따로 둔 칸에 그린다.
		int tileH = TILE_H;
		int chartH = CHART_H;
		int bodyH = tileH + GAP + chartH + GAP + sectionLabelH + alertsH;
		boolean compact = headH + bodyH + footH > maxH;
		if (compact) {
			tileH = 46;
			chartH = 64;
			bodyH = tileH + GAP + chartH + GAP + sectionLabelH + alertsH;
		}
		int ph = headH + bodyH + footH;
		boolean cramped = ph > maxH;
		if (cramped) {
			ph = maxH;
		}
		maxScroll = cramped ? Math.max(0, bodyH - (ph - headH - footH)) : 0;
		scroll = Math.max(0, Math.min(maxScroll, scroll));
		int px = (width - pw) / 2;
		int py = Math.max(10, (height - ph) / 2);
		lastPx = px;
		lastPy = py;
		lastPw = pw;
		lastPh = ph;

		LunaDraw.panel3d(ctx, px, py, pw, ph, 8);   // 49-227차: 사진 시안 판

		// ---- 헤더
		int hy = py + PAD;
		int lx = px + PAD;
		LunaDraw.textBold(ctx, textRenderer, "성능 진단서", lx, LunaDraw.textY(hy, CLOSE_SZ), LunaDraw.TEXT);
		int tw = LunaDraw.widthBold(textRenderer, "성능 진단서");
		boolean measuring = issues.isEmpty();
		LunaDraw.circle(ctx, lx + tw + 10, hy + CLOSE_SZ / 2 - 3, 6, LunaDraw.withAlpha(measuring ? WARN : GOOD, 0xE0));
		LunaDraw.text(ctx, textRenderer, measuring ? "지표를 모으는 중" : "실시간 측정 중", lx + tw + 20, LunaDraw.textY(hy, CLOSE_SZ), LunaDraw.TEXT_DIM);
		int closeX = px + pw - PAD - CLOSE_SZ;
		boolean closeHover = LunaDraw.in(mouseX, mouseY, closeX, hy, CLOSE_SZ, CLOSE_SZ);
		LunaDraw.button3d(ctx, closeX, hy, CLOSE_SZ, CLOSE_SZ, 5, LunaDraw.B_NEUTRAL, closeHover ? 1f : 0f);   // 49-227차
		LunaIcons.draw(ctx, textRenderer, LunaIcons.CLOSE, closeX + (CLOSE_SZ - 10) / 2, LunaDraw.iconY(hy, CLOSE_SZ),
			closeHover ? LunaDraw.TEXT : LunaDraw.TEXT_SUB);

		if (cramped) {
			lunaV.scissor(ctx, px, py + headH, px + pw, py + ph - footH);
		}
		int y = py + headH - (int) Math.round(scroll);

		// ---- 큰 숫자 타일 4개
		int tiles = 4;
		int tileW = (contentW - GAP * (tiles - 1)) / tiles;
		int fps = LunaPerf.fps();
		int avgFps = LunaPerf.avgFps();
		long used = LunaPerf.usedMemMb();
		long max = LunaPerf.maxMemMb();
		int ping = LunaPerf.ping();
		float tick = LunaPerf.serverTickRatio();
		int fpsLv = avgFps < 0 ? 0 : avgFps < 45 ? 2 : avgFps < 75 ? 1 : 0;
		float memPct = max > 0 ? used / (float) max : 0f;
		int memLv = memPct > 0.85f ? 2 : memPct > 0.7f ? 1 : 0;
		int pingLv = ping < 0 ? 0 : ping > 200 ? 2 : ping > 100 ? 1 : 0;
		int tickLv = ping < 0 ? 0 : tick < 0.7f ? 2 : tick < 0.9f ? 1 : 0;
		String[][] tileText = {
			{"FPS", fps < 0 ? "-" : String.valueOf(fps), avgFps < 0 ? "" : "평균 " + avgFps},
			{"메모리", max > 0 ? Math.round(memPct * 100) + "%" : "-", max > 0 ? used + " / " + max + " MB" : ""},
			{"핑", ping < 0 ? "-" : ping + "ms", ping < 0 ? "서버에 없음" : "서버 지연"},
			{"서버 틱", ping < 0 ? "-" : Math.round(tick * 100) + "%", ping < 0 ? "서버에 없음" : "진행률"},
		};
		int[] tileLv = {fpsLv, memLv, pingLv, tickLv};
		for (int i = 0; i < tiles; i++) {
			float p = rowProgress(i);
			float base = LunaDraw.alpha();
			LunaDraw.setAlpha(base * p);
			int tx = lx + i * (tileW + GAP);
			int ty = y + Math.round((1f - p) * 6f);
			int color = levelColor(tileLv[i]);
			LunaDraw.card3d(ctx, tx, ty, tileW, tileH, 5, 0, 0);   // 49-227차
			LunaDraw.roundRect(ctx, tx, ty + 10, 3, tileH - 20, 1, color);
			LunaDraw.text(ctx, textRenderer, tileText[i][0], tx + 12, ty + (compact ? 7 : 9), LunaDraw.TEXT_DIM);
			bigText(ctx, tileText[i][1], tx + 12, ty + (compact ? 20 : 24), tileLv[i] == 0 ? LunaDraw.TEXT : color);
			if (!compact) {
				LunaDraw.text(ctx, textRenderer, tileText[i][2], tx + 12, ty + tileH - 16, LunaDraw.TEXT_SUB);
			}
			LunaDraw.circle(ctx, tx + tileW - 14, ty + 9, 6, color);
			LunaDraw.setAlpha(base);
		}
		y += tileH + GAP;

		// ---- 최근 60초 FPS 막대 + 메모리 사용률 막대
		{
			float p = rowProgress(4);
			float base = LunaDraw.alpha();
			LunaDraw.setAlpha(base * p);
			int cy = y + Math.round((1f - p) * 6f);
			int chartW = contentW * 2 / 3 - GAP / 2;
			LunaDraw.card3d(ctx, lx, cy, chartW, chartH, 5, 0, 0);   // 49-227차
			LunaDraw.text(ctx, textRenderer, "FPS 최근 60초", lx + 12, cy + 9, LunaDraw.TEXT_DIM);
			int[] hist = LunaPerf.fpsHistory();
			int gx = lx + 12;
			int gy = cy + 24;
			int gw = chartW - 24;
			int gh = chartH - 34;
			int peak = 60;
			for (int v : hist) {
				peak = Math.max(peak, v);
			}
			if (hist.length == 0) {
				LunaDraw.textCentered(ctx, textRenderer, "모으는 중…", lx + chartW / 2, cy + chartH / 2 - 2, LunaDraw.TEXT_DIM);
			} else {
				int n = 60;
				float barW = gw / (float) n;
				for (int i = 0; i < hist.length; i++) {
					int slot = n - hist.length + i;
					int v = hist[i];
					int bh = Math.max(1, Math.round(gh * Math.min(1f, v / (float) peak)));
					int bx = gx + Math.round(slot * barW);
					int bw = Math.max(1, Math.round((slot + 1) * barW) - Math.round(slot * barW) - 1);
					int c = v < 45 ? BAD : v < 75 ? WARN : LunaDraw.ACCENT;
					ctx.fill(bx, gy + gh - bh, bx + bw, gy + gh, LunaDraw.applyAlpha(LunaDraw.withAlpha(c, 0xC8)));
				}
				// 평균선
				if (avgFps > 0) {
					int ay = gy + gh - Math.round(gh * Math.min(1f, avgFps / (float) peak));
					ctx.fill(gx, ay, gx + gw, ay + 1, LunaDraw.applyAlpha(LunaDraw.withAlpha(0xFFFFFF, 0x50)));
					String lab = "평균 " + avgFps;
					// 49-217차: 평균선이 그래프 위쪽에 붙으면 글자가 막대를 덮었다 - 그때는 제목 줄 오른쪽에
					int labY = ay - 11 < gy ? cy + 9 : ay - 11;
					LunaDraw.text(ctx, textRenderer, lab, gx + gw - LunaDraw.width(textRenderer, lab), labY, LunaDraw.TEXT_SUB);
				}
			}
			ctx.fill(gx, gy + gh, gx + gw, gy + gh + 1, LunaDraw.applyAlpha(LunaClientScreen.ink(0x30)));

			int mx = lx + chartW + GAP;
			int mw = contentW - chartW - GAP;
			LunaDraw.card3d(ctx, mx, cy, mw, chartH, 5, 0, 0);   // 49-227차
			LunaDraw.text(ctx, textRenderer, "메모리", mx + 12, cy + 9, LunaDraw.TEXT_DIM);
			int mc = levelColor(memLv);
			String memPctText = max > 0 ? Math.round(memPct * 100) + "%" : "-";
			if (compact) {
				LunaDraw.text(ctx, textRenderer, memPctText, mx + mw - 12 - LunaDraw.width(textRenderer, memPctText), cy + 9, memLv == 0 ? LunaDraw.TEXT : mc);
			} else {
				bigText(ctx, memPctText, mx + 12, cy + 24, memLv == 0 ? LunaDraw.TEXT : mc);
			}
			int barY = cy + chartH - 26;
			int barW2 = mw - 24;
			LunaDraw.roundRect(ctx, mx + 12, barY, barW2, 6, 3, LunaClientScreen.ink(0x24));
			LunaDraw.roundRect(ctx, mx + 12, barY, Math.max(6, Math.round(barW2 * Math.min(1f, memPct))), 6, 3, mc);
			LunaDraw.text(ctx, textRenderer, max > 0 ? used + " / " + max + " MB" : "", mx + 12, barY + 10, LunaDraw.TEXT_SUB);
			LunaDraw.setAlpha(base);
		}
		y += chartH + GAP;

		// ---- 주의/문제 항목
		LunaDraw.text(ctx, textRenderer, alerts.isEmpty() ? "진단" : "진단 " + alerts.size() + "건", lx, y + 3, LunaDraw.TEXT_SUB);
		y += sectionLabelH;
		if (alerts.isEmpty()) {
			float p = rowProgress(5);
			float base = LunaDraw.alpha();
			LunaDraw.setAlpha(base * p);
			LunaDraw.roundRect(ctx, lx, y, contentW, 30, 6, LunaDraw.withAlpha(GOOD, 0x1E));
			LunaDraw.circle(ctx, lx + 12, y + 12, 6, GOOD);
			LunaDraw.text(ctx, textRenderer, measuring ? "몇 초 뒤 다시 열어 주세요. 지표를 모으고 있습니다" : "모든 지표가 정상입니다",
				lx + 26, LunaDraw.textY(y, 30), LunaDraw.TEXT);
			LunaDraw.setAlpha(base);
			y += 36;
		} else {
			for (int i = 0; i < alerts.size(); i++) {
				LunaPerf.Issue is = alerts.get(i);
				int ch = cardHs.get(i);
				int color = levelColor(is.level());
				float p = rowProgress(5 + i);
				float base = LunaDraw.alpha();
				LunaDraw.setAlpha(base * p);
				int rx = lx + Math.round((1f - p) * 5f);
				LunaDraw.card3d(ctx, rx, y, contentW, ch, 5, 0, 0);   // 49-227차
				LunaDraw.roundRect(ctx, rx, y + 8, 3, 14, 1, color);
				LunaDraw.text(ctx, textRenderer, is.title(), rx + 12, y + 10, LunaDraw.TEXT);
				String tag = levelTag(is.level());
				int tagW = LunaDraw.width(textRenderer, tag) + 14;
				int tagX = rx + contentW - 8 - tagW;
				LunaDraw.roundRect(ctx, tagX, y + 7, tagW, 16, 6, LunaDraw.withAlpha(color, 0x2A));
				LunaDraw.textCentered(ctx, textRenderer, tag, tagX + tagW / 2, LunaDraw.textY(y + 7, 16), color);
				String value = is.value() == null ? "" : is.value();
				LunaDraw.text(ctx, textRenderer, value, tagX - 8 - LunaDraw.width(textRenderer, value), y + 10, color);
				int ay = y + 30;
				for (String line : adviceLines.get(i)) {
					LunaDraw.text(ctx, textRenderer, line, rx + 12, ay, LunaDraw.TEXT_DIM);
					ay += 11;
				}
				LunaDraw.setAlpha(base);
				y += ch + 6;
			}
		}
		if (cramped) {
			ctx.disableScissor();
		}

		// ---- 하단 안내(본문 아래 따로 둔 칸 - 49-217차: 예전엔 본문 위에 겹쳐 그려졌다)
		String foot = maxScroll > 0 ? "휠로 내려 보기 | 모드별 메모리 사용량은 자바 구조상 정확히 잴 수 없습니다"
				: "모드별 메모리 사용량은 자바 구조상 정확히 잴 수 없습니다";
		LunaDraw.text(ctx, textRenderer, LunaDraw.ellipsize(textRenderer, foot, contentW), lx, py + ph - footH + (footH - 9) / 2, LunaDraw.TEXT_DIM);
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double verticalAmount) {
		if (maxScroll <= 0) {
			return false;
		}
		scroll = Math.max(0, Math.min(maxScroll, scroll - verticalAmount * 24));
		return true;
	}

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		if (LunaDraw.in(mouseX, mouseY, lastPx + lastPw - PAD - CLOSE_SZ, lastPy + PAD, CLOSE_SZ, CLOSE_SZ)
				|| !LunaDraw.in(mouseX, mouseY, lastPx, lastPy, lastPw, lastPh)) {
			goBack();
		}
		return true;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
			goBack();
			return true;
		}
		return false;
	}

	private void goBack() {
		kr.lunaslight.mod.util.LunaCompat.setScreen(parent);
	}

	@Override
	public void close() {
		goBack();
	}
}
