package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.impl.inventory.ItemTooltipInfoModule;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;

/**
 * 49-25차: 음식 툴팁의 배고픔/포만감을 글자 대신 **마인크래프트 고기 아이콘**으로("마크 배고픔 모양").
 *
 * 윗줄 = 채워 주는 배고픔(빈 고기 위에 가득/반 고기, 칸당 2포인트), 아랫줄 = 포만감(고기 윤곽을 금색으로,
 * 소수는 열 단위로 잘라 부드럽게 - 포만감 HUD와 같은 방식). 아이콘은 HUD와 같이 8px 간격으로 겹쳐 그린다.
 *
 * ShulkerTooltipComponent와 같은 이유로 두 시대의 TooltipComponent 메서드를 @Override 없이 모두 정의.
 * ItemStackTooltipDataMixin → TooltipData Proxy(FoodPayload) → Fabric TooltipComponentCallback → 이 클래스.
 */
public class FoodTooltipComponent implements ClientTooltipComponent {
	private static final int STEP = 8;
	private static final int ROW = 9;
	private static final int GAP = 2;

	private final float hunger;
	private final float saturation;
	private final int saturationColor;
	private final int hungerIcons;
	private final int saturationIcons;

	public FoodTooltipComponent(ItemTooltipInfoModule.FoodPayload payload) {
		this.hunger = Math.max(0f, Math.min(40f, payload.hunger()));
		this.saturation = Math.max(0f, Math.min(40f, payload.saturation()));
		this.saturationColor = payload.saturationColor();
		this.hungerIcons = (int) Math.ceil(hunger / 2f);
		this.saturationIcons = (int) Math.ceil(saturation / 2f);
	}

	private int rows() {
		return (hungerIcons > 0 ? 1 : 0) + (saturationIcons > 0 ? 1 : 0);
	}

	// ---- 크기(두 시대 모두) ----
	public int getHeight() {
		int r = rows();
		return r == 0 ? 0 : r * ROW + (r - 1) * GAP + 1;
	}

	public int getHeight(Font textRenderer) {
		return getHeight();
	}

	public int getWidth(Font textRenderer) {
		int icons = Math.max(hungerIcons, saturationIcons);
		return icons == 0 ? 0 : (icons - 1) * STEP + 9;
	}

	// ---- 그리기(두 시대 모두) ----
	public void drawItems(Font textRenderer, int x, int y, GuiGraphicsExtractor ctx) {
		draw(ctx, x, y);
	}

	public void extractImage(Font textRenderer, int x, int y, int width, int height, GuiGraphicsExtractor ctx) {
		draw(ctx, x, y);
	}

	private void draw(GuiGraphicsExtractor ctx, int x, int y) {
		int row = y;
		if (hungerIcons > 0) {
			for (int i = 0; i < hungerIcons; i++) {
				int ix = x + i * STEP;
				float pts = hunger - i * 2f;
				int kind = pts >= 2f ? 2 : 1;
				if (!LunaGfx.drawFoodIcon(ctx, ix, row, 0, 0xFFFFFFFF)) {
					ctx.fill(ix, row, ix + 9, row + 9, 0x50000000);
				}
				if (!LunaGfx.drawFoodIcon(ctx, ix, row, kind, 0xFFFFFFFF)) {
					ctx.fill(ix + 1, row + 1, ix + (kind == 2 ? 8 : 5), row + 8, 0xFFC07040);
				}
			}
			row += ROW + GAP;
		}
		if (saturationIcons > 0) {
			for (int i = 0; i < saturationIcons; i++) {
				int ix = x + i * STEP;
				float pts = saturation - i * 2f;
				int cols = pts >= 2f ? 9 : Math.max(1, Math.round(9f * pts / 2f));
				// 빈 고기 실루엣 위에 금색 윤곽 - HUD의 포만감 테두리와 같은 모양
				if (!LunaGfx.drawFoodIcon(ctx, ix, row, 0, 0xFFFFFFFF)) {
					ctx.fill(ix, row, ix + 9, row + 9, 0x50000000);
				}
				LunaGfx.drawFoodOutline(ctx, ix, row, cols, saturationColor);
			}
		}
	}
}
