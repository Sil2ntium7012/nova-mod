package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.HotbarSelectionHook;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 49-216차: 1.16~1.19.4 핫바 선택 테두리 부드럽게. renderHotbar(float, MatrixStack) 안 두 번째 drawTexture
 * (MatrixStack, x, y, u, v, w, h)가 선택 테두리다(첫째는 핫바 바탕 - javap 실측, 1.19.4만 정적 호출이지만
 * ModifyArg는 둘 다 같은 인자 번호). x = 1번 인자.
 */
@Mixin(InGameHud.class)
public abstract class HotbarSelectionLegacyMixin {

	@ModifyArg(method = "renderHotbar(FLnet/minecraft/client/util/math/MatrixStack;)V",
			at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;drawTexture(Lnet/minecraft/client/util/math/MatrixStack;IIIIII)V", ordinal = 1),
			index = 1, require = 0)
	private int lunaslight$hotbarSelLegacy(int x) {
		return HotbarSelectionHook.adjust(x);
	}
}
