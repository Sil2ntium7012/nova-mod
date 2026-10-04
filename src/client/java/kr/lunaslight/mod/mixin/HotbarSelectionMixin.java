package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.HotbarSelectionHook;
import net.minecraft.client.gui.hud.InGameHud;
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
@Mixin(InGameHud.class)
public abstract class HotbarSelectionMixin {

	@ModifyArg(method = "renderHotbar",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Lnet/minecraft/util/Identifier;IIIIII)V", ordinal = 1),
			index = 1, require = 0)
	private int lunaslight$hotbarSel1201(int x) {
		return HotbarSelectionHook.adjust(x);
	}

	@ModifyArg(method = "renderHotbar",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lnet/minecraft/util/Identifier;IIII)V", ordinal = 1),
			index = 1, require = 0)
	private int lunaslight$hotbarSel1202(int x) {
		return HotbarSelectionHook.adjust(x);
	}

	// 49-178차(전 버전 점검): 1.21.2~1.21.5는 drawGuiTexture(Function<Identifier,RenderLayer>, Identifier, x, y, w, h) -
	// 위 둘 다 안 맞아 이 네 버전만 부드러운 선택 테두리가 빠져 있었다. x = 2번 인자(ordinal 1 = 선택 테두리, javap 실측).
	@ModifyArg(method = "renderHotbar",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIII)V", ordinal = 1),
			index = 2, require = 0)
	private int lunaslight$hotbarSel1212(int x) {
		return HotbarSelectionHook.adjust(x);
	}

	@ModifyArg(method = "renderHotbar",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawGuiTexture(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/util/Identifier;IIII)V", ordinal = 1),
			index = 2, require = 0)
	private int lunaslight$hotbarSel1216(int x) {
		return HotbarSelectionHook.adjust(x);
	}
}
