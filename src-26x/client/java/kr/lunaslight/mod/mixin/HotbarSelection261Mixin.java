package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.HotbarSelectionHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 49-41차: 핫바 선택 테두리를 부드럽게. renderHotbar 안에서 선택 테두리는 항상 **두 번째** 그리기 호출(ordinal 1):
 *  · 1.20.1: drawTexture(Identifier, x, y, u, v, w, h) → x = 1번 인자
 *  · 1.20.2~1.21.5: drawGuiTexture(Identifier, x, y, w, h) → x = 1번 인자
 *  · 1.21.6+: drawGuiTexture(RenderPipeline, Identifier, x, y, w, h) → x = 2번 인자
 * 시그니처가 없는 버전은 require = 0으로 조용히 건너뜀. renderHotbar는 버전마다 인자가 달라 이름만으로 잡는다.
 */
// 26.1.x: 인게임 HUD가 아직 Gui 안에 있다(메서드 이름은 26.2 Hud와 동일). 26.2에서는 대상 메서드가 없어 조용히 건너뜀(require=0).
@Mixin(targets = "net.minecraft.client.gui.Gui")
public abstract class HotbarSelection261Mixin {



	@ModifyArg(method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1),
			index = 2, require = 0)
	private int lunaslight$hotbarSel1216261(int x) {
		return HotbarSelectionHook.adjust(x);
	}
}
