package kr.lunaslight.mod.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 49-532차(사용자: 너굴마을 아이템 설명 상자가 검은 네모 - "예쁘게 리팩 입혀 주라"): 너굴마을에 있는 동안, 아이템이 자기 설명 모양(tooltip_style)을
 * 정하지 않은 설명 상자를 너굴마을 리소스팩의 왼쪽 위 갈색 판(ng:font/hud/hud_bg.png)과 같은 모양으로 그린다 - 같은 색을 그대로 썼다
 * (바깥 밝은 테두리 165,122,80 / 위 밝은 줄 199,154,110 / 안쪽 진한 테두리 117,78,41 / 바탕 142,105,69 / 아래 그림자).
 * 바탕만 글이 잘 읽히게 조금 어둡게. 판 그림을 늘리면 테두리가 뭉개져서 색으로 직접 그린다.
 */
public final class NeogulTooltipStyle {
	private NeogulTooltipStyle() {
	}

	private static final int RIM = 0xFFA57A50, TOP = 0xFFC79A6E, INNER = 0xFF754E29, FILL = 0xF5684628, SHADOW = 0xC8000000;

	/** 바닐라 설명 상자 자리(x, y, w, h = 글 영역) 둘레에 그린다. 그렸으면 true. */
	public static boolean draw(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
		Minecraft mc = Minecraft.getInstance();
		if (mc == null || mc.player == null || !NeogulData.onNeogul(mc)) {
			return false;
		}
		int x0 = x - 4, y0 = y - 4, x1 = x + w + 4, y1 = y + h + 4;
		// 아래 그림자
		ctx.fill(x0 + 1, y1, x1 - 1, y1 + 1, SHADOW);
		// 바깥 밝은 테두리(모서리 한 칸 깎음)
		ctx.fill(x0 + 1, y0, x1 - 1, y0 + 1, TOP);
		ctx.fill(x0 + 1, y1 - 1, x1 - 1, y1, RIM);
		ctx.fill(x0, y0 + 1, x0 + 1, y1 - 1, RIM);
		ctx.fill(x1 - 1, y0 + 1, x1, y1 - 1, RIM);
		// 안쪽 진한 테두리
		ctx.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, INNER);
		ctx.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, INNER);
		ctx.fill(x0 + 1, y0 + 2, x0 + 2, y1 - 2, INNER);
		ctx.fill(x1 - 2, y0 + 2, x1 - 1, y1 - 2, INNER);
		// 바탕
		ctx.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, FILL);
		return true;
	}
}
