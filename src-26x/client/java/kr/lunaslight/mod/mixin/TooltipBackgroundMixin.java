package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.gui.LunaTooltipFrame;
import kr.lunaslight.mod.util.LunaCompat;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-23차: 툴팁 꾸미기. 바닐라 툴팁 배경(TooltipBackgroundRenderer.render)을 취소하고 LunaTooltipFrame이
 * 등급 색 테두리 상자를 대신 그린다. render의 시그니처가 세 시대로 갈려서(1.20.1~1.21.1 z 포함 /
 * 1.21.2~1.21.5 z + 텍스처 / 1.21.6+ 텍스처만) 디스크립터별 핸들러 셋을 두고 require=0으로 맞는 것만 적용.
 * 아이템/리소스팩이 자체 툴팁 텍스처를 지정한 경우(texture != null)는 존중해서 손대지 않는다.
 * 49-36차: 클래스 리터럴 대신 이름 문자열(targets) - TooltipBackgroundRenderer가 없는 1.19.2 이하에서도 컴파일되게.
 */
@Mixin(targets = "net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil")
public abstract class TooltipBackgroundMixin {



	@Inject(method = "extractTooltipBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIILnet/minecraft/resources/Identifier;)V", at = @At("HEAD"), cancellable = true, require = 0)
	private static void lunaslight$frameTex(GuiGraphicsExtractor ctx, int x, int y, int w, int h, Identifier texture, CallbackInfo ci) {
		// 49-532차(사용자 사진: 너굴마을 아이템 설명 상자가 검은 네모 - "예쁘게 리팩 입혀 주라"): 너굴마을에서 자기 모양이 없는 설명 상자는
		// 너굴마을 리소스팩 왼쪽 위 갈색 판과 같은 모양으로 그린다(NeogulTooltipStyle).
		if (texture == null && kr.lunaslight.mod.util.NeogulTooltipStyle.draw(ctx, x, y, w, h)) {
			ci.cancel();
			return;
		}
		if (!LunaTooltipFrame.enabled || texture != null) {
			return;
		}
		if (LunaTooltipFrame.render(ctx, x, y, w, h)) {
			ci.cancel();
		}
	}
}
