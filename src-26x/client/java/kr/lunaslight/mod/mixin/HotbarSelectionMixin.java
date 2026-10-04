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
// 26.2: 인게임 HUD가 Gui에서 Hud 클래스로 분리됨. 26.1.x용 쌍둥이(…Mixin261, Gui 대상)가 따로 있다.
@Mixin(targets = "net.minecraft.client.gui.Hud")
public abstract class HotbarSelectionMixin {



	@ModifyArg(method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1),
			index = 2, require = 0)
	private int lunaslight$hotbarSel1216(int x) {
		return HotbarSelectionHook.adjust(x);
	}

	/** 49-215차: 26.3은 RenderPipeline이 com.mojang.renderpearl.api.pipeline으로 옮겨가 설명자가 다르다(순서는 같음, javap 실측). */
	@ModifyArg(method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1),
			index = 2, require = 0)
	private int lunaslight$hotbarSel263(int x) {
		return HotbarSelectionHook.adjust(x);
	}
}
