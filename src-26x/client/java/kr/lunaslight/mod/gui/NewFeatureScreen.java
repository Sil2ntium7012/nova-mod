package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.Module;
import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.NewFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * 49-552차: <b>새 기능 묻기 창</b>({@link NewFeatures}). 서버나 월드에 들어가면 한 번 뜬다.
 * <pre>
 *  ┌──────────── 새 기능이 나왔어요 ────────────┐
 *  │  너굴 던전 UI                       [켬 ●] │
 *  │  던전 핫바, 하트, 스킬 카드, 경험치 바      │
 *  │  ┌ 미리보기(기능이 그려 주면) ┐              │
 *  │  …                                        │
 *  │     설정(Right Shift)에서 언제든 바꿀 수 있어요  │
 *  │                 [ 확인 ]                   │
 *  └───────────────────────────────────────────┘
 * </pre>
 * 줄을 누르면 켬/끔이 바뀐다(처음엔 켬). 확인이나 Esc를 누르면 그대로 적용하고 다시 안 묻는다.
 */
public class NewFeatureScreen extends LunaScreenBase {

	private final List<Module> list;
	private final boolean[] on;
	private final LunaVScale lunaV = new LunaVScale(this, 420, 300);
	private int[] okBtn;
	private boolean applied;

	public NewFeatureScreen(List<Module> list) {
		super(Component.literal("새 기능"));
		this.list = list;
		this.on = new boolean[list.size()];
		java.util.Arrays.fill(on, true);
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
				draw(ctx, lunaV.mouse(mouseX), lunaV.mouse(mouseY));
			} finally {
				LunaClientScreen.restoreScreenTheme();
			}
		} finally {
			lunaV.end(ctx, e);
		}
	}

	private static final int PREV_H = 96;

	// 49-571차: 새 기능이 여러 개면 미리보기까지 한 창에 다 넣으면 화면 밖으로 넘쳤다 - 한 번에 하나씩(쪽 넘기기).
	private int page;
	private int[] prevBtn, nextBtn, rowBox;

	private void draw(GuiGraphicsExtractor ctx, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		ctx.fill(0, 0, width, height, 0xA0000000);
		int n = list.size();
		page = Math.max(0, Math.min(page, n - 1));
		Module m = list.get(page);
		boolean prev = m.hasNewFeaturePreview();
		int w = Math.min(width - 20, 360);
		int rh = 30 + (prev ? PREV_H + 6 : 0);
		int h = 40 + rh + 50;
		int x = (width - w) / 2, y = (height - h) / 2;
		LunaDraw.panel3d(ctx, x, y, w, h, 6);
		LunaDraw.textCentered(ctx, mc.font, "새 기능이 나왔어요", x + w / 2, y + 10, LunaDraw.TEXT);
		LunaDraw.textCentered(ctx, mc.font, n > 1 ? "켤 기능을 골라 주세요 (" + (page + 1) + " / " + n + ")" : "켤까요?", x + w / 2, y + 22, LunaDraw.TEXT_SUB);
		int ry = y + 38;
		rowBox = new int[]{x + 8, ry, w - 16, 30};
		boolean hov = mx >= x + 8 && mx < x + w - 8 && my >= ry && my < ry + 30;
		LunaDraw.card3d(ctx, x + 8, ry, w - 16, rh, hov ? 1f : 0f, on[page]);
		LunaDraw.text(ctx, mc.font, m.getDisplayName(), x + 16, ry + 5, LunaDraw.TEXT);
		String d = m.getDescription() == null ? "" : m.getDescription();
		LunaDraw.text(ctx, mc.font, LunaDraw.ellipsize(mc.font, d, w - 80), x + 16, ry + 17, LunaDraw.TEXT_SUB);
		LunaDraw.toggle(ctx, x + w - 44, ry + 8, 28, 14, on[page] ? 1f : 0f, true);
		if (prev) {
			int px = x + 14, py = ry + 32, pw = w - 28;
			ctx.fillGradient(px, py, px + pw, py + PREV_H, 0xFF3A4A5E, 0xFF1C2430);
			ctx.enableScissor(px, py, px + pw, py + PREV_H);
			try {
				m.drawNewFeaturePreview(ctx, px, py, pw, PREV_H);
			} catch (Throwable t) {
				LunaCompat.warnOnce("newFeature:preview", t);
			} finally {
				ctx.disableScissor();
			}
		}
		// 쪽 점
		if (n > 1) {
			int dotsW = n * 8 - 3;
			for (int i = 0; i < n; i++) {
				ctx.fill(x + (w - dotsW) / 2 + i * 8, y + h - 44, x + (w - dotsW) / 2 + i * 8 + 5, y + h - 41,
						i == page ? LunaDraw.ACCENT | 0xFF000000 : 0x60FFFFFF);
			}
		}
		LunaDraw.textCentered(ctx, mc.font, "설정(Right Shift)에서 언제든 바꿀 수 있어요", x + w / 2, y + h - 36, LunaDraw.TEXT_DIM);
		int bw = 90, bh = 18, by = y + h - bh - 8;
		boolean last = page >= n - 1;
		if (n > 1) {
			prevBtn = new int[]{x + w / 2 - bw - 4, by, bw, bh};
			nextBtn = new int[]{x + w / 2 + 4, by, bw, bh};
			boolean hp = page > 0 && in(prevBtn, mx, my);
			LunaDraw.button3d(ctx, mc.font, prevBtn[0], prevBtn[1], bw, bh, "이전", LunaDraw.B_NEUTRAL, hp ? 1f : 0f);
			if (page == 0) {
				ctx.fill(prevBtn[0], prevBtn[1], prevBtn[0] + bw, prevBtn[1] + bh, 0x80000000);
			}
		} else {
			prevBtn = null;
			nextBtn = new int[]{x + (w - bw) / 2, by, bw, bh};
		}
		LunaDraw.button3d(ctx, mc.font, nextBtn[0], nextBtn[1], bw, bh, last ? "확인" : "다음", LunaDraw.B_PRIMARY, in(nextBtn, mx, my) ? 1f : 0f);
		okBtn = last ? nextBtn : null;
	}

	private static boolean in(int[] r, double mx, double my) {
		return r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
	}

	@Override
	protected boolean lunaMouseClicked(double mouseX, double mouseY, int button) {
		boolean e = lunaV.enter();
		double k = lunaV.k(e);
		try {
			double mx = mouseX * k, my = mouseY * k;
			if (button != 0) {
				return true;
			}
			if (in(nextBtn, mx, my)) {
				LunaCompat.playUiSound(Minecraft.getInstance(), "UI_BUTTON_CLICK", 1.0f);
				if (page >= list.size() - 1) {
					onClose();
				} else {
					page++;
				}
				return true;
			}
			if (page > 0 && in(prevBtn, mx, my)) {
				LunaCompat.playUiSound(Minecraft.getInstance(), "UI_BUTTON_CLICK", 1.0f);
				page--;
				return true;
			}
			if (in(rowBox, mx, my)) {
				on[page] = !on[page];
				LunaCompat.playUiSound(Minecraft.getInstance(), "UI_BUTTON_CLICK", 1.2f);
				return true;
			}
			return true;
		} finally {
			lunaV.exit(e);
		}
	}

	@Override
	public void onClose() {
		apply();
		super.onClose();
	}

	@Override
	public void removed() {
		// 49-570차(사용자: "신규 기능은 항상 겜 들어갔을 때 물어보라고"): 너굴마을은 들어가자마자 서버를 옮겨(로비 → 섬) 이 창이 연결 화면에 덮여
		// 사라졌는데, 그때도 "답한 걸로" 적어서 사용자는 한 번도 못 보고 지나갔다. 확인이나 Esc로 닫을 때만 답한 걸로 치고, 다른 창이 덮으면 다시 묻는다.
		if (!applied) {
			NewFeatures.askAgain();
		}
		super.removed();
	}

	private void apply() {
		if (applied) {
			return;
		}
		applied = true;
		for (int i = 0; i < list.size(); i++) {
			Module m = list.get(i);
			try {
				m.setEnabled(on[i]);
				m.onNewFeatureAnswered(on[i]);
			} catch (Throwable t) {
				LunaCompat.warnOnce("newFeature:apply", t);
			}
			NewFeatures.markAsked(m);
		}
		try {
			kr.lunaslight.mod.config.LunaClientConfig.save();
		} catch (Throwable ignored) {
		}
	}
}
