package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.impl.inventory.ShulkerPeekModule;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;
import java.util.List;

/**
 * 49-23차: 셜커 상자 툴팁의 인벤토리 격자(9×3, 셜커 색 배경) - "내 인벤토리 보는 것처럼".
 *
 * TooltipComponent의 추상 메서드가 시대별로 다르다(≤1.21.1 getHeight() / 1.21.2+ getHeight(TextRenderer),
 * drawItems(TextRenderer,int,int,DrawContext) / drawItems(TextRenderer,int,int,int,int,DrawContext)).
 * @Override 없이 모든 변형을 다 정의해 두면 어느 버전에서든 그 버전의 추상 메서드가 구현되고 나머지는
 * 그냥 남는 메서드가 된다(공유 소스가 버전별 매핑으로 각각 컴파일되므로).
 */
public class ShulkerTooltipComponent implements ClientTooltipComponent {
	private static final int SLOT = 18;
	private static final int COLS = 9;
	private static final int PAD = 4;

	private final List<ItemStack> slots;
	private final int color;
	private final boolean tint;
	private final int rows;

	public ShulkerTooltipComponent(ShulkerPeekModule.GridPayload payload) {
		this.slots = payload.slots();
		this.color = payload.color();
		this.tint = payload.tint();
		this.rows = Math.max(1, (slots.size() + COLS - 1) / COLS);
	}

	// ---- 크기(두 시대 모두) ----
	public int getHeight() {
		return rows * SLOT + PAD * 2 + 2;
	}

	public int getHeight(Font textRenderer) {
		return getHeight();
	}

	public int getWidth(Font textRenderer) {
		return COLS * SLOT + PAD * 2;
	}

	// ---- 그리기(두 시대 모두) ----
	public void drawItems(Font textRenderer, int x, int y, GuiGraphicsExtractor ctx) {
		draw(textRenderer, x, y, ctx);
	}

	public void extractImage(Font textRenderer, int x, int y, int width, int height, GuiGraphicsExtractor ctx) {
		draw(textRenderer, x, y, ctx);
	}

	private static int mix(int a, int b, float t) {
		int aa = Math.round(((a >>> 24) & 0xFF) + (((b >>> 24) & 0xFF) - ((a >>> 24) & 0xFF)) * t);
		int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
		return (aa << 24) | (r << 16) | (g << 8) | bl;
	}

	private void draw(Font textRenderer, int x, int y, GuiGraphicsExtractor ctx) {
		int w = COLS * SLOT + PAD * 2;
		int h = rows * SLOT + PAD * 2;
		// 49-24차: "마크 인벤토리 느낌" - 바닐라 GUI 회색 판(베벨)을 셜커 색으로 틴트, 슬롯은 바닐라 그대로
		int base = tint ? mix(LunaDraw.MC_PANEL, color, 0.45f) : LunaDraw.MC_PANEL;
		LunaDraw.mcPanel(ctx, x, y, w, h, base);
		for (int i = 0; i < rows * COLS; i++) {
			int sx = x + PAD + (i % COLS) * SLOT;
			int sy = y + PAD + (i / COLS) * SLOT;
			LunaDraw.mcSlot(ctx, sx, sy);
		}
		for (int i = 0; i < slots.size() && i < rows * COLS; i++) {
			ItemStack stack = slots.get(i);
			if (stack == null || stack.isEmpty()) {
				continue;
			}
			int sx = x + PAD + (i % COLS) * SLOT + 1;
			int sy = y + PAD + (i / COLS) * SLOT + 1;
			try {
				ctx.item(stack, sx, sy);
				LunaCompat.drawItemOverlay(ctx, textRenderer, stack, sx, sy);
			} catch (Throwable t) {
				LunaCompat.warnOnce("shulkerGridItem", t);
			}
		}
	}
}
