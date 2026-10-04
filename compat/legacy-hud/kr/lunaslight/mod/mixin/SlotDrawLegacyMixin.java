package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.SlotDrawHook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-216차(사용자: "버전별 안 되는 기능 살려내"): 1.16~1.19.4 칸 그리기 훅. SlotDrawMixin은 DrawContext 시대
 * (1.20+) 시그니처라 이 시대엔 붙을 곳이 없어 희귀도 테두리 같은 칸 기능이 조용히 안 됐다(믹스인 대상 실측표).
 * 이 시대는 HandledScreen#drawSlot(MatrixStack, Slot)이 1.16~1.19.4 그대로다(javap) - 행렬을 shim DrawContext로
 * 감싸 같은 훅으로 넘긴다. compat/legacy-hud는 legacy-era1(1.16~1.19.4) 판에만 얹힌다.
 */
@Mixin(HandledScreen.class)
public abstract class SlotDrawLegacyMixin {

	@Inject(method = "drawSlot(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/screen/slot/Slot;)V", at = @At("HEAD"), require = 0)
	private void lunaslight$beforeSlotLegacy(MatrixStack matrices, Slot slot, CallbackInfo ci) {
		SlotDrawHook.beforeSlot(new DrawContext(matrices), slot);
	}

	@Inject(method = "drawSlot(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/screen/slot/Slot;)V", at = @At("RETURN"), require = 0)
	private void lunaslight$afterSlotLegacy(MatrixStack matrices, Slot slot, CallbackInfo ci) {
		SlotDrawHook.afterSlot(new DrawContext(matrices), slot);
	}
}
