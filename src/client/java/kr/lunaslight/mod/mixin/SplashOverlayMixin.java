package kr.lunaslight.mod.mixin;

import kr.lunaslight.mod.util.LunaCompat;
import kr.lunaslight.mod.util.SplashHook;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 49-47차: 시작 로딩 화면을 Luna 로딩창 모양으로 덮는다(SplashHook 주석 참고 - 취소하지 않고 RETURN에 덮기만).
 * 클래스 이름이 1.17에 SplashScreen → SplashOverlay로 바뀌어 <b>이름 문자열</b>로 잡고, 두 시대의 render
 * 시그니처를 각각 둔다. 전부 require = 0이라 없는 버전에서는 조용히 빠진다.
 */
@Mixin(targets = "net.minecraft.client.gui.screen.SplashOverlay")
public abstract class SplashOverlayMixin {

	@Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIF)V", at = @At("RETURN"), require = 0)
	private void lunaslight$splash(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SplashHook.render(this, ctx);
	}

	@Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;IIF)V", at = @At("RETURN"), require = 0)
	private void lunaslight$splashLegacy(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		SplashHook.render(this, LunaCompat.toDrawContext(matrices));
	}
}
