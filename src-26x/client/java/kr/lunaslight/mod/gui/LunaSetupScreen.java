package kr.lunaslight.mod.gui;

import com.google.gson.JsonObject;
import kr.lunaslight.mod.util.FirstSetup;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * 49-213차: <b>처음 설정</b> 화면 - 세 단계로 나눠 고른다(한 번에 다 보여 주지 않는다).
 * <ol>
 *   <li>화면: 글꼴, GUI 크기</li>
 *   <li>목록과 HUD: 기능 보기, HUD 배경</li>
 *   <li>조작과 창: 마우스 감도(1~200% 게이지), 화면 모드</li>
 * </ol>
 * 누르는 즉시 적용되어 그 자리에서 확인한다. [완료]를 누르면 기록하고(런처 폴더에도 - 다른 프로필은 묻지 않음),
 * 서버로 켰던 경우 그 서버로 들어간다. 판단과 적용은 {@link FirstSetup}.
 */
public class LunaSetupScreen extends LunaScreenBase {

	// ==================== 49-247차: GUI 배율과 무관한 크기 + 창이 작으면 같이 작게(LunaVScale) ====================
	private final LunaVScale lunaV = new LunaVScale(this, 392, 252);

	@Override
	public void extractRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		boolean e = lunaV.begin(ctx);
		try {
			if (lunaV.needsInit()) {
				lunaInit0();
				lunaV.markInit();
			}
			lunaRender0(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY), delta);
		} finally {
			lunaV.end(ctx, e);
		}
	}

	@Override
	protected void init() {
		boolean e = lunaV.enter();
		try {
			lunaInit0();
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
			return lunaMouseClicked0(mouseX * k, mouseY * k, button);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseReleased(double mouseX, double mouseY, int button) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseReleased0(mouseX * k, mouseY * k, button);
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	protected boolean lunaMouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			return lunaMouseDragged0(mouseX * k, mouseY * k, button, deltaX * k, deltaY * k);
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

	private static final int PANEL_W = 372;
	private static final int PANEL_H = 236;
	private static final int PAD = 16;
	private static final int PILL_H = 18;

	private static final String[] PAGE_TITLES = {"화면", "목록과 HUD", "조작과 창"};
	private static final String[] FONTS = {"마크 폰트", "갈무리 폰트", "모던"};
	private static final String[] GUI = {"자동", "1", "2", "3", "4"};
	private static final String[] TILES = {"큰 박스", "작은 박스", "상세"};
	private static final String[] HUD = {"둥근", "네모난", "없음"};
	private static final String[] WINDOW = {"창 화면", "전체 화면", "테두리 없는 전체 화면"};

	private final Screen parent;
	private int px, py;
	private int page;

	private int fontIdx, gui, tile, hud, window;
	private double mouseValue = 0.5;
	/** 49-214차: 감도 게이지 자리(그릴 때 기록, 누르기/끌기에 씀)와 끄는 중인지. */
	private int gaugeX, gaugeY, gaugeW;
	private boolean gaugeShown, dragging;

	private record Hit(int x, int y, int w, int h, Runnable action) {
	}

	private final List<Hit> hits = new ArrayList<>();

	public LunaSetupScreen(Screen parent) {
		super(LunaCompat.textLiteral("처음 설정"));
		this.parent = parent;
		LunaDraw.resetAnim("setup");
		try {
			JsonObject cur = FirstSetup.current(Minecraft.getInstance());
			fontIdx = cur.get("font").getAsInt();
			gui = cur.get("guiScale").getAsInt();
			tile = cur.get("tileView").getAsInt();
			hud = cur.get("hudShape").getAsInt();
			mouseValue = cur.get("mouse").getAsDouble();
			window = cur.get("window").getAsInt();
		} catch (Throwable t) {
			LunaCompat.warnOnce("setup:current", t);
		}
	}

	private void lunaInit0() {
		px = (width - PANEL_W) / 2;
		py = Math.max(8, (height - PANEL_H) / 2);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ==================== 그리기 ====================

	private void lunaRender0(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
		LunaDraw.beginFrame();
		LunaGfx.drawScreenBackdrop(this, ctx, width, height, mouseX, mouseY, delta, 0xA6000000);
		float open = LunaDraw.animFrom("setup", 0f, 1f, 18f);
		LunaDraw.setAlpha(open);
		hits.clear();

		LunaDraw.panel3d(ctx, px, py, PANEL_W, PANEL_H, 8);   // 49-227차: 사진 시안 판

		// 머리: 제목 + 진행도
		LunaDraw.text(ctx, font, "처음 설정", px + PAD, py + 14, LunaDraw.TEXT);
		int pct = Math.round(100f * (page + 1) / PAGE_TITLES.length);
		String step = (page + 1) + " / " + PAGE_TITLES.length + " 단계  " + pct + "%";
		LunaDraw.text(ctx, font, step, px + PANEL_W - PAD - LunaDraw.width(font, step), py + 14, LunaDraw.TEXT_SUB);
		progress(ctx, px + PAD, py + 30, PANEL_W - PAD * 2);
		LunaDraw.text(ctx, font, "누르면 바로 적용됩니다. 나중에 설정에서 다시 바꿀 수 있습니다.", px + PAD, py + 60, LunaDraw.TEXT_DIM);

		int y = py + 80;
		gaugeShown = false;
		switch (page) {
			case 0 -> {
				y = row(ctx, "글꼴", FONTS, fontIdx, y, mouseX, mouseY, i -> {
					fontIdx = i;
					FirstSetup.setFont(i);
				});
				row(ctx, "GUI 크기", GUI, gui, y, mouseX, mouseY, i -> {
					gui = i;
					FirstSetup.setGuiScale(Minecraft.getInstance(), i);
				});
			}
			case 1 -> {
				y = row(ctx, "기능 보기", TILES, tile, y, mouseX, mouseY, i -> {
					tile = i;
					FirstSetup.setTileView(i);
				});
				int after = row(ctx, "HUD 배경", HUD, hud, y, mouseX, mouseY, i -> {
					hud = i;
					FirstSetup.setHudShape(i);
				});
				hudSample(ctx, px + PANEL_W - PAD - 124, y - 2, 124, 48);
				y = after;
			}
			default -> {
				y = gauge(ctx, y, mouseX, mouseY);
				row(ctx, "화면 모드", WINDOW, window, y, mouseX, mouseY, i -> {
					window = i;
					FirstSetup.setWindowMode(Minecraft.getInstance(), i);
				});
			}
		}

		// 아래 버튼
		int by = py + PANEL_H - PAD - 20;
		if (page > 0) {
			button(ctx, "이전", px + PAD, by, 64, false, mouseX, mouseY, () -> page--);
		}
		boolean last = page == PAGE_TITLES.length - 1;
		button(ctx, last ? "완료" : "다음", px + PANEL_W - PAD - 72, by, 72, true, mouseX, mouseY,
			last ? this::finish : () -> page++);
		LunaDraw.setAlpha(1f);
	}

	/** 이름 한 줄 + 고르기 알약 줄. 다음 줄의 y를 돌려준다. */
	private int row(GuiGraphicsExtractor ctx, String label, String[] options, int selected, int y, int mouseX, int mouseY,
			java.util.function.IntConsumer pick) {
		LunaDraw.text(ctx, font, label, px + PAD, y, LunaDraw.TEXT_SUB);
		int x = px + PAD;
		int oy = y + 12;
		for (int i = 0; i < options.length; i++) {
			int w = LunaDraw.width(font, options[i]) + 18;
			boolean hov = LunaDraw.in(mouseX, mouseY, x, oy, w, PILL_H);
			LunaDraw.pillButton(ctx, font, x, oy, w, PILL_H, options[i], hov, i == selected);
			final int idx = i;
			hits.add(new Hit(x, oy, w, PILL_H, () -> pick.accept(idx)));
			x += w + 4;
		}
		return oy + PILL_H + 16;
	}

	private void button(GuiGraphicsExtractor ctx, String label, int x, int y, int w, boolean accent, int mouseX, int mouseY, Runnable action) {
		boolean hov = LunaDraw.in(mouseX, mouseY, x, y, w, 20);
		LunaDraw.pillButton(ctx, font, x, y, w, 20, label, hov, accent);
		hits.add(new Hit(x, y, w, 20, action));
	}

	/**
	 * 49-214차(사용자: "진행도 제대로 표시"): 전체 막대(지금 단계까지 채움, 부드럽게 늘어남) + 단계 이름 세 칸.
	 * 지난 단계는 체크, 지금 단계는 강조색 번호, 남은 단계는 흐리게.
	 */
	private void progress(GuiGraphicsExtractor ctx, int x, int y, int w) {
		float target = (page + 1) / (float) PAGE_TITLES.length;
		float t = LunaDraw.anim("setup:progress", target, 10f);
		LunaDraw.roundRect(ctx, x, y, w, 4, 2, 0x26FFFFFF);
		LunaDraw.roundRect(ctx, x, y, Math.max(4, Math.round(w * t)), 4, 2, LunaDraw.ACCENT);
		int seg = w / PAGE_TITLES.length;
		for (int i = 0; i < PAGE_TITLES.length; i++) {
			int sx = x + seg * i;
			boolean done = i < page;
			boolean now = i == page;
			int dot = 11;
			int dy = y + 10;
			LunaDraw.circle(ctx, sx, dy, dot, done || now ? LunaDraw.ACCENT : 0x33FFFFFF);
			String mark = done ? "\u2713" : String.valueOf(i + 1);
			int mw = LunaDraw.width(font, mark);
			LunaDraw.text(ctx, font, mark, sx + (dot - mw) / 2 + 1, LunaDraw.textY(dy, dot),
				done || now ? kr.lunaslight.mod.util.LunaTheme.ON_ACCENT : LunaDraw.TEXT_DIM);
			LunaDraw.text(ctx, font, PAGE_TITLES[i], sx + dot + 4, LunaDraw.textY(dy, dot),
				now ? LunaDraw.TEXT : (done ? LunaDraw.TEXT_SUB : LunaDraw.TEXT_DIM));
		}
	}

	/**
	 * 49-214차(사용자: "감도는 게이지 형식으로 다양하게"): 1%~200% 게이지. 누르거나 끌어서, 휠로 1%씩.
	 * 마인크래프트 값(0~1)과 같은 눈금이다(100% = 0.5). 다음 줄의 y를 돌려준다.
	 */
	private int gauge(GuiGraphicsExtractor ctx, int y, int mouseX, int mouseY) {
		LunaDraw.text(ctx, font, "마우스 감도", px + PAD, y, LunaDraw.TEXT_SUB);
		String val = sensitivityPercent() + "%";
		int vw = LunaDraw.width(font, val);
		gaugeX = px + PAD;
		gaugeY = y + 13;
		gaugeW = PANEL_W - PAD * 2 - 44;
		gaugeShown = true;
		boolean hov = dragging || LunaDraw.in(mouseX, mouseY, gaugeX - 4, gaugeY - 2, gaugeW + 8, 18);
		LunaDraw.slider(ctx, gaugeX, gaugeY, gaugeW, (float) mouseValue, hov);
		LunaDraw.roundRect(ctx, px + PANEL_W - PAD - 38, gaugeY - 1, 38, 16, 4, 0x1FFFFFFF);
		LunaDraw.text(ctx, font, val, px + PANEL_W - PAD - 19 - vw / 2, LunaDraw.textY(gaugeY - 1, 16), LunaDraw.TEXT);
		// 눈금: 50% 100% 150%
		int[] marks = {50, 100, 150};
		for (int m : marks) {
			int mx = gaugeX + Math.round(gaugeW * (m / 200f));
			ctx.fill(mx, gaugeY + 11, mx + 1, gaugeY + 14, LunaDraw.applyAlpha(0x40FFFFFF));
			String ml = m + "%";
			LunaDraw.text(ctx, font, ml, mx - LunaDraw.width(font, ml) / 2, gaugeY + 16, LunaDraw.TEXT_DIM);
		}
		return gaugeY + 16 + 22;
	}

	private int sensitivityPercent() {
		return (int) Math.round(mouseValue * 200.0);
	}

	private void setGaugeFromMouse(double mouseX) {
		double r = (mouseX - gaugeX) / Math.max(1.0, gaugeW);
		r = Math.max(0.005, Math.min(1.0, r));   // 0%는 막는다(움직이지 않게 되니까) - 최소 1%
		mouseValue = Math.round(r * 200.0) / 200.0;
		FirstSetup.setMouse(Minecraft.getInstance(), mouseValue);
	}

	/**
	 * HUD 배경 미리보기. 49-214차(사용자: "FPS 미리보기가 배경이랑 색이 비슷해서 안 보임"): 설정 화면 미리보기와 같은
	 * 실제 게임 사진 위에 고른 모양의 정보 상자를 띄운다.
	 */
	private void hudSample(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		int regionH = Math.max(24, Math.min(512, Math.round(277f * h / Math.max(1, w))));
		int v = Math.max(0, Math.min(512 - regionH, 250 - regionH / 2));
		if (!LunaGfx.drawTex(ctx, LunaGfx.id("textures/gui/preview.png"), x, y, w, h, 0, v, 277, regionH, 512, 0xFFFFFFFF)) {
			int horizon = y + h * 45 / 100;
			ctx.fillGradient(x, y, x + w, horizon, 0xFF8FC2E8, 0xFFB7D9EF);
			ctx.fillGradient(x, horizon, x + w, y + h, 0xFF7FA04E, 0xFF5E7D3A);
		}
		LunaDraw.roundRectOutline(ctx, x, y, w, h, 3, 0x33FFFFFF);
		String s = "FPS 144";
		int bw = LunaDraw.width(font, s) + 12;
		int bh = 16;
		int bx = x + 6;
		int by = y + 6;
		if (hud == 0) {
			LunaDraw.roundRect(ctx, bx, by, bw, bh, 4, 0x8C090A0C);
		} else if (hud == 1) {
			ctx.fill(bx, by, bx + bw, by + bh, LunaDraw.applyAlpha(0x8C090A0C));
		}
		LunaDraw.text(ctx, font, s, bx + 6, LunaDraw.textY(by, bh), 0xFFFFFFFF);
	}

	private void finish() {
		JsonObject prefs = new JsonObject();
		prefs.addProperty("font", fontIdx);
		prefs.addProperty("guiScale", gui);
		prefs.addProperty("tileView", tile);
		prefs.addProperty("hudShape", hud);
		prefs.addProperty("mouse", mouseValue);
		prefs.addProperty("window", window);
		LunaCompat.setScreen(parent);
		FirstSetup.finish(Minecraft.getInstance(), prefs);
	}

	// ==================== 입력 ====================

	private boolean lunaMouseClicked0(double mouseX, double mouseY, int button) {
		if (gaugeShown && LunaDraw.in(mouseX, mouseY, gaugeX - 4, gaugeY - 2, gaugeW + 8, 18)) {
			dragging = true;
			setGaugeFromMouse(mouseX);
			return true;
		}
		for (Hit h : new ArrayList<>(hits)) {
			if (LunaDraw.in(mouseX, mouseY, h.x(), h.y(), h.w(), h.h())) {
				h.action().run();
				return true;
			}
		}
		return true;
	}

	private boolean lunaMouseDragged0(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
		if (dragging) {
			setGaugeFromMouse(mouseX);
			return true;
		}
		return false;
	}

	private boolean lunaMouseReleased0(double mouseX, double mouseY, int button) {
		if (dragging) {
			dragging = false;
			return true;
		}
		return false;
	}

	private boolean lunaMouseScrolled0(double mouseX, double mouseY, double verticalAmount) {
		if (gaugeShown && LunaDraw.in(mouseX, mouseY, px, gaugeY - 14, PANEL_W, 40) && verticalAmount != 0) {
			int pctNow = sensitivityPercent() + (verticalAmount > 0 ? 1 : -1);
			mouseValue = Math.max(1, Math.min(200, pctNow)) / 200.0;
			FirstSetup.setMouse(Minecraft.getInstance(), mouseValue);
			return true;
		}
		return false;
	}

	private boolean lunaKeyPressed0(int keyCode, int scanCode, int modifiers) {
		if (keyCode == InputConstants.KEY_ESCAPE) {
			return true;   // 처음 설정은 끝까지 - ESC로 닫지 않는다
		}
		if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
			if (page < PAGE_TITLES.length - 1) {
				page++;
			} else {
				finish();
			}
			return true;
		}
		return false;
	}
}
