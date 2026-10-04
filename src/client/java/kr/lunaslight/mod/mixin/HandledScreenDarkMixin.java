package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.DarkModeHook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-49차: 다크 모드 - 배경 텍스처를 그린 <b>직후</b>에 GUI 사각형만 어둡게 덮는다(DarkModeHook 주석 참고).
 * drawBackground의 시그니처가 시대마다 갈려(1.20+ DrawContext / 그 아래 MatrixStack) 둘 다 두고 require = 0.
 */
@Mixin(HandledScreen.class)
public abstract class HandledScreenDarkMixin {

	@Inject(method = "drawBackground(Lnet/minecraft/client/gui/DrawContext;FII)V", at = @At("RETURN"), require = 0)
	private void lunaslight$dark(DrawContext ctx, float delta, int mouseX, int mouseY, CallbackInfo ci) {
		DarkModeHook.afterBackground(this, ctx);
	}

	@Inject(method = "drawBackground(Lnet/minecraft/client/util/math/MatrixStack;FII)V", at = @At("RETURN"), require = 0)
	private void lunaslight$darkLegacy(net.minecraft.client.util.math.MatrixStack matrices, float delta,
			int mouseX, int mouseY, CallbackInfo ci) {
		DarkModeHook.afterBackground(this, kr.lunaslight.mod.util.LunaCompat.toDrawContext(matrices));
	}
}
