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
	private int[][] rows;
	private int[] okBtn;
	private boolean applied;

	public NewFeatureScreen(List<Module> list) {
		super(Component.literal("새 기능"));
		this.list = list;
		this.on = new boolean[list.size()];
		java.util.Arrays.fill(on, true);
		this.rows = new int[list.size()][];
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

	private static final int PREV_H = 92;

	private void draw(GuiGraphicsExtractor ctx, int mx, int my) {
		Minecraft mc = Minecraft.getInstance();
		ctx.fill(0, 0, width, height, 0xA0000000);
		int w = Math.min(width - 20, 360);
		int h = 52 + 26;
		for (Module m : list) {
			h += 30 + (m.hasNewFeaturePreview() ? PREV_H + 4 : 0);
		}
		h = Math.min(h, height - 20);
		int x = (width - w) / 2, y = (height - h) / 2;
		LunaDraw.panel3d(ctx, x, y, w, h, 6);
		LunaDraw.textCentered(ctx, mc.font, "새 기능이 나왔어요", x + w / 2, y + 10, LunaDraw.TEXT);
		LunaDraw.textCentered(ctx, mc.font, "켤 기능을 골라 주세요", x + w / 2, y + 22, LunaDraw.TEXT_SUB);
		int ry = y + 38;
		for (int i = 0; i < list.size(); i++) {
			Module m = list.get(i);
			boolean prev = m.hasNewFeaturePreview();
			int rh = 26 + (prev ? PREV_H + 4 : 0);
			rows[i] = new int[]{x + 8, ry, w - 16, rh};
			boolean hov = mx >= x + 8 && mx < x + w - 8 && my >= ry && my < ry + 26;
			LunaDraw.card3d(ctx, x + 8, ry, w - 16, rh, hov ? 1f : 0f, on[i]);
			LunaDraw.text(ctx, mc.font, m.getDisplayName(), x + 16, ry + 4, LunaDraw.TEXT);
			String d = m.getDescription() == null ? "" : m.getDescription();
			LunaDraw.text(ctx, mc.font, LunaDraw.ellipsize(mc.font, d, w - 80), x + 16, ry + 15, LunaDraw.TEXT_SUB);
			LunaDraw.toggle(ctx, x + w - 44, ry + 6, 28, 14, on[i] ? 1f : 0f, true);
			if (prev) {
				int px = x + 14, py = ry + 28, pw = w - 28;
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
			ry += rh + 4;
		}
		LunaDraw.textCentered(ctx, mc.font, "설정(Right Shift)에서 언제든 바꿀 수 있어요", x + w / 2, y + h - 36, LunaDraw.TEXT_DIM);
		int bw = 90, bh = 18;
		okBtn = new int[]{x + (w - bw) / 2, y + h - bh - 8, bw, bh};
		boolean hovOk = mx >= okBtn[0] && mx < okBtn[0] + bw && my >= okBtn[1] && my < okBtn[1] + bh;
		LunaDraw.button3d(ctx, mc.font, okBtn[0], okBtn[1], bw, bh, "확인", LunaDraw.B_PRIMARY, hovOk ? 1f : 0f);
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
			if (okBtn != null && mx >= okBtn[0] && mx < okBtn[0] + okBtn[2] && my >= okBtn[1] && my < okBtn[1] + okBtn[3]) {
				LunaCompat.playUiSound(Minecraft.getInstance(), "UI_BUTTON_CLICK", 1.0f);
				onClose();
				return true;
			}
			for (int i = 0; i < rows.length; i++) {
				int[] r = rows[i];
				if (r != null && mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + 26) {
					on[i] = !on[i];
					LunaCompat.playUiSound(Minecraft.getInstance(), "UI_BUTTON_CLICK", 1.2f);
					return true;
				}
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
		apply();   // 다른 창이 덮어써도 답한 걸로
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
