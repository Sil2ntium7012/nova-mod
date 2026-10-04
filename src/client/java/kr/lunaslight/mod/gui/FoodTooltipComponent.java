package kr.lunaslight.mod.gui;

import kr.lunaslight.mod.module.impl.inventory.ItemTooltipInfoModule;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.TooltipComponent;

/**
 * 49-25차: 음식 툴팁의 배고픔/포만감을 글자 대신 **마인크래프트 고기 아이콘**으로("마크 배고픔 모양").
 *
 * 윗줄 = 채워 주는 배고픔(빈 고기 위에 가득/반 고기, 칸당 2포인트), 아랫줄 = 포만감(고기 윤곽을 금색으로,
 * 소수는 열 단위로 잘라 부드럽게 - 포만감 HUD와 같은 방식). 아이콘은 HUD와 같이 8px 간격으로 겹쳐 그린다.
 *
 * ShulkerTooltipComponent와 같은 이유로 두 시대의 TooltipComponent 메서드를 @Override 없이 모두 정의.
 * ItemStackTooltipDataMixin → TooltipData Proxy(FoodPayload) → Fabric TooltipComponentCallback → 이 클래스.
 */
public class FoodTooltipComponent implements TooltipComponent {
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

	public int getHeight(TextRenderer textRenderer) {
		return getHeight();
	}

	public int getWidth(TextRenderer textRenderer) {
		int icons = Math.max(hungerIcons, saturationIcons);
		return icons == 0 ? 0 : (icons - 1) * STEP + 9;
	}

	// ---- 그리기(두 시대 모두) ----
	public void drawItems(TextRenderer textRenderer, int x, int y, DrawContext ctx) {
		draw(ctx, x, y);
	}

	public void drawItems(TextRenderer textRenderer, int x, int y, int width, int height, DrawContext ctx) {
		draw(ctx, x, y);
	}

	private void draw(DrawContext ctx, int x, int y) {
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

	// ---- 49-216차: 1.17~1.19.4(MatrixStack 시대) 시그니처 - 그 시대 TooltipComponent는 이 모양의 default 메서드만 있어
	// 위 drawItems가 불리지 않았다(그래서 이 판들에선 이 부품을 컴파일에서 뺐었다). 행렬을 shim DrawContext로 감싸
	// 같은 그리기로 넘긴다. 시그니처 셋(javap): 1.17~1.18.2 (…, int z, TextureManager) / 1.19~1.19.3 (…, int z) /
	// 1.19.4 (…, ItemRenderer). 1.20+엔 이 셋이 인터페이스에 없어 그냥 안 불리는 메서드다.
	public void drawItems(TextRenderer textRenderer, int x, int y, net.minecraft.client.util.math.MatrixStack matrices,
			net.minecraft.client.render.item.ItemRenderer itemRenderer, int z, net.minecraft.client.texture.TextureManager textures) {
		drawLegacy(textRenderer, x, y, matrices);
	}

	public void drawItems(TextRenderer textRenderer, int x, int y, net.minecraft.client.util.math.MatrixStack matrices,
			net.minecraft.client.render.item.ItemRenderer itemRenderer, int z) {
		drawLegacy(textRenderer, x, y, matrices);
	}

	public void drawItems(TextRenderer textRenderer, int x, int y, net.minecraft.client.util.math.MatrixStack matrices,
			net.minecraft.client.render.item.ItemRenderer itemRenderer) {
		drawLegacy(textRenderer, x, y, matrices);
	}

	private void drawLegacy(TextRenderer textRenderer, int x, int y, net.minecraft.client.util.math.MatrixStack matrices) {
		try {
			// shim DrawContext(MatrixStack)는 옛 판에만 있다 - 1.20+ 컴파일을 깨지 않게 이름으로 만든다
			DrawContext ctx = DrawContext.class.getConstructor(net.minecraft.client.util.math.MatrixStack.class).newInstance(matrices);
			drawItems(textRenderer, x, y, ctx);
		} catch (Throwable t) {
			kr.lunaslight.mod.util.LunaCompat.warnOnce("tooltipLegacy", t);
		}
	}
}
